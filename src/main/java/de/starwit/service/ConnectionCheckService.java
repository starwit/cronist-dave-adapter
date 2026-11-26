package de.starwit.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import de.starwit.config.AdapterProperties;

/**
 * Periodically calls a test URL of Cronist and DAVe with the clients of the transfer, to notice early if a service is
 * not reachable or the credentials don't work anymore. The last results are reported by the health check.
 */
@Service
public class ConnectionCheckService {

    public static final String CRONIST = "cronist";
    public static final String DAVE = "dave";

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionCheckService.class);

    /** @param error null if the check succeeded */
    record Result(Instant checkedAt, String error) {

        boolean ok() {
            return error == null;
        }
    }

    private record Target(String testUrl, Consumer<String> call) {
    }

    private final Map<String, Target> targets = new LinkedHashMap<>();
    private final Map<String, Result> results = new ConcurrentHashMap<>();
    private final ClientRegistrationRepository clientRegistrations;
    private final Clock clock;

    @Autowired
    public ConnectionCheckService(CronistService cronist, DaveService dave, AdapterProperties properties,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations) {
        this(cronist, dave, properties, clientRegistrations.getIfAvailable(), Clock.systemUTC());
    }

    /** @param clientRegistrations null if no OAuth2 client is configured */
    ConnectionCheckService(CronistService cronist, DaveService dave, AdapterProperties properties,
            ClientRegistrationRepository clientRegistrations, Clock clock) {
        this.clientRegistrations = clientRegistrations;
        this.clock = clock;
        targets.put(CRONIST, new Target(properties.cronist().testUrl(), cronist::checkConnection));
        targets.put(DAVE, new Target(properties.dave().testUrl(), dave::checkConnection));
    }

    @Scheduled(fixedDelayString = "${adapter.credential-check-interval:5m}")
    public void checkAll() {
        targets.forEach(this::check);
    }

    private void check(String name, Target target) {
        if (!StringUtils.hasText(target.testUrl())) {
            return;
        }
        Result previous = results.get(name);
        Result result;
        try {
            target.call().accept(target.testUrl());
            result = new Result(clock.instant(), null);
        } catch (RuntimeException e) {
            result = new Result(clock.instant(), describe(e));
            LOG.debug("Connection check of {} failed: {}", name, causeChain(e));
        }
        results.put(name, result);

        if (!result.ok()) {
            LOG.warn("Connection check of {} ({}) failed: {}", name, target.testUrl(), result.error());
        } else if (previous != null && !previous.ok()) {
            LOG.info("Connection check of {} succeeded again", name);
        } else {
            LOG.debug("Connection check of {} succeeded", name);
        }
    }

    /** UNKNOWN if not checked yet or the check is disabled, which doesn't affect the overall status. */
    public Health health(String name) {
        if (!StringUtils.hasText(targets.get(name).testUrl())) {
            return Health.unknown().withDetail("reason", "no test URL configured").build();
        }
        Result result = results.get(name);
        if (result == null) {
            return Health.unknown().withDetail("reason", "not checked yet").build();
        }
        Health.Builder builder = result.ok() ? Health.up() : Health.down().withDetail("error", result.error());
        return builder.withDetail("checkedAt", result.checkedAt().toString()).build();
    }

    /**
     * A short, readable reason. The cause chain of a failed token request is long and repeats the same message several
     * times, it is only logged at debug level.
     */
    String describe(RuntimeException e) {
        OAuth2AuthorizationException tokenError = find(e, OAuth2AuthorizationException.class);
        return tokenError != null ? describeTokenError(tokenError) : describeServiceError(e);
    }

    private String describeTokenError(OAuth2AuthorizationException e) {
        String reason;
        HttpStatusCodeException response = find(e, HttpStatusCodeException.class);
        ResourceAccessException noConnection = find(e, ResourceAccessException.class);
        if (response != null) {
            String errorCode = jsonField(response.getResponseBodyAsString(), "error");
            String description = jsonField(response.getResponseBodyAsString(), "error_description");
            if ("invalid_client".equals(errorCode)) {
                reason = "identity provider rejected the client credentials";
            } else if ("unauthorized_client".equals(errorCode)) {
                reason = "client is not allowed to request a token with client credentials";
            } else if (response.getStatusCode().value() == 404) {
                reason = "token endpoint not found, check the token-uri";
            } else {
                reason = "token request rejected";
            }
            reason += " (" + response.getStatusCode().value()
                    + (errorCode == null ? "" : " " + errorCode)
                    + (description == null ? "" : ": " + description) + ")";
        } else if (noConnection != null) {
            reason = "identity provider not reachable (" + rootMessage(noConnection) + ")";
        } else {
            reason = "token request failed (" + e.getMessage() + ")";
        }
        return reason + clientInfo(e);
    }

    /** Client id and token URI, as they are what is wrong in most cases. */
    private String clientInfo(OAuth2AuthorizationException e) {
        if (!(e instanceof ClientAuthorizationException clientError) || clientRegistrations == null) {
            return "";
        }
        ClientRegistration registration = clientRegistrations
                .findByRegistrationId(clientError.getClientRegistrationId());
        if (registration == null) {
            return " [client registration " + clientError.getClientRegistrationId() + "]";
        }
        return " [client-id " + registration.getClientId() + ", token-uri "
                + registration.getProviderDetails().getTokenUri() + "]";
    }

    private static String describeServiceError(RuntimeException e) {
        if (e instanceof HttpClientErrorException.Unauthorized) {
            return "service rejected the access token (401)";
        }
        if (e instanceof HttpClientErrorException.Forbidden) {
            return "access denied (403), client is missing a role";
        }
        if (e instanceof HttpClientErrorException.NotFound) {
            return "test URL not found (404)";
        }
        if (e instanceof RestClientResponseException response) {
            return "unexpected response (" + response.getStatusCode().value() + ")";
        }
        if (e instanceof ResourceAccessException) {
            return "not reachable (" + rootMessage(e) + ")";
        }
        return e.toString();
    }

    private static <T extends Throwable> T find(Throwable e, Class<T> type) {
        return Stream.iterate(e, Objects::nonNull, Throwable::getCause)
                .limit(10) // causes can be circular
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElse(null);
    }

    /** e.g. a ConnectException without message, caused by a ClosedChannelException */
    private static String rootMessage(Throwable e) {
        return Stream.iterate(e, Objects::nonNull, Throwable::getCause)
                .limit(10)
                .reduce((first, second) -> second)
                .map(root -> root.getMessage() == null ? root.getClass().getSimpleName()
                        : root.getClass().getSimpleName() + ": " + root.getMessage())
                .orElseThrow();
    }

    /** The error response of a token endpoint (RFC 6749, 5.2) is flat JSON, no need for a JSON parser. */
    private static String jsonField(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    static String causeChain(Throwable e) {
        return Stream.iterate(e, Objects::nonNull, Throwable::getCause)
                .limit(10)
                .map(Throwable::toString)
                .collect(Collectors.joining(", caused by "));
    }
}
