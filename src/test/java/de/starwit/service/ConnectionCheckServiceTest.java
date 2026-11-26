package de.starwit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import de.starwit.config.AdapterProperties;

class ConnectionCheckServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");
    private static final String CRONIST_TEST = "http://cronist/cronist/api/observationarea";
    private static final String DAVE_TEST = "http://dave/api/cityinformation/all";

    private static final String TOKEN_URI = "http://localhost:8180/realms/dave/protocol/openid-connect/token";
    private static final ClientRegistrationRepository CLIENT_REGISTRATIONS = new InMemoryClientRegistrationRepository(
            ClientRegistration.withRegistrationId("dave")
                    .clientId("dave_api")
                    .clientSecret("wrong")
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .tokenUri(TOKEN_URI)
                    .build());

    private final CronistService cronist = mock(CronistService.class);
    private final DaveService dave = mock(DaveService.class);

    private ConnectionCheckService service(String cronistTestUrl, String daveTestUrl) {
        AdapterProperties properties = new AdapterProperties(null, false, Map.of(),
                new AdapterProperties.Cronist("http://cronist/cronist", cronistTestUrl, null, null, null),
                new AdapterProperties.Dave("http://dave", daveTestUrl, null, null, null, 0, Map.of(), List.of(), null),
                null);
        return new ConnectionCheckService(cronist, dave, properties, CLIENT_REGISTRATIONS, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void unknownBeforeFirstCheck() {
        ConnectionCheckService service = service(CRONIST_TEST, DAVE_TEST);

        assertThat(service.health(ConnectionCheckService.CRONIST).getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(service.health(ConnectionCheckService.DAVE).getStatus()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    void upIfTestUrlsRespond() {
        ConnectionCheckService service = service(CRONIST_TEST, DAVE_TEST);

        service.checkAll();

        verify(cronist).checkConnection(CRONIST_TEST);
        verify(dave).checkConnection(DAVE_TEST);
        Health health = service.health(ConnectionCheckService.CRONIST);
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("checkedAt", NOW.toString());
        assertThat(service.health(ConnectionCheckService.DAVE).getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void oneFailingServiceDoesNotSkipTheOther() {
        doThrow(new ResourceAccessException("I/O error", new ConnectException("Connection refused")))
                .when(cronist).checkConnection(CRONIST_TEST);
        ConnectionCheckService service = service(CRONIST_TEST, DAVE_TEST);

        service.checkAll();

        Health health = service.health(ConnectionCheckService.CRONIST);
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat((String) health.getDetails().get("error")).isEqualTo("not reachable (ConnectException: Connection refused)");
        verify(dave).checkConnection(DAVE_TEST);
        assertThat(service.health(ConnectionCheckService.DAVE).getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void recoversOnNextSuccessfulCheck() {
        doThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", null, null, null))
                .doNothing()
                .when(dave).checkConnection(DAVE_TEST);
        ConnectionCheckService service = service(CRONIST_TEST, DAVE_TEST);

        service.checkAll();
        assertThat((String) service.health(ConnectionCheckService.DAVE).getDetails().get("error"))
                .isEqualTo("access denied (403), client is missing a role");

        service.checkAll();
        assertThat(service.health(ConnectionCheckService.DAVE).getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void disabledWithoutTestUrl() {
        ConnectionCheckService service = service("", null);

        service.checkAll();

        verify(cronist, never()).checkConnection(any());
        verify(dave, never()).checkConnection(any());
        assertThat(service.health(ConnectionCheckService.CRONIST).getStatus()).isEqualTo(Status.UNKNOWN);
    }

    /** The exception chain of the client credentials token request of Spring Security, when Keycloak answers 401. */
    private static RuntimeException tokenRequestFailure(RuntimeException cause) {
        OAuth2Error error = new OAuth2Error("invalid_token_response",
                "An error occurred while attempting to retrieve the OAuth 2.0 Access Token Response: "
                        + cause.getMessage(),
                null);
        return new ClientAuthorizationException(error, "dave",
                new OAuth2AuthorizationException(error, cause));
    }

    @Test
    void describesRejectedClientCredentials() {
        RuntimeException e = tokenRequestFailure(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED,
                "Unauthorized", null,
                """
                {"error":"invalid_client","error_description":"Invalid client or Invalid client credentials"}"""
                        .getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));

        assertThat(service(CRONIST_TEST, DAVE_TEST).describe(e)).isEqualTo(
                "identity provider rejected the client credentials (401 invalid_client: Invalid client or Invalid "
                        + "client credentials) [client-id dave_api, token-uri " + TOKEN_URI + "]");
    }

    @Test
    void describesUnreachableIdentityProvider() {
        RuntimeException e = tokenRequestFailure(
                new ResourceAccessException("I/O error", new ConnectException("Connection refused")));

        assertThat(service(CRONIST_TEST, DAVE_TEST).describe(e)).isEqualTo(
                "identity provider not reachable (ConnectException: Connection refused) [client-id dave_api, "
                        + "token-uri " + TOKEN_URI + "]");
    }

    @Test
    void describesRejectedToken() {
        RuntimeException e = HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", null, null,
                null);

        assertThat(service(CRONIST_TEST, DAVE_TEST).describe(e)).isEqualTo("service rejected the access token (401)");
    }

    @Test
    void servicesCallAbsoluteTestUrl() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://dave");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://dave:18080/api/dave-backend-service/cityinformation/all"))
                .andRespond(withSuccess());
        server.expect(requestTo("http://dave:18080/api/dave-backend-service/cityinformation/all"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        DaveService service = new DaveService(builder.build());

        service.checkConnection("http://dave:18080/api/dave-backend-service/cityinformation/all");
        assertThatThrownBy(
                () -> service.checkConnection("http://dave:18080/api/dave-backend-service/cityinformation/all"))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        server.verify();
    }
}
