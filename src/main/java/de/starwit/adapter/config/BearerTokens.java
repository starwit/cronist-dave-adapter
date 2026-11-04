package de.starwit.adapter.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** Adds the access token of a client registration (client credentials) to the requests of a REST client. */
@Component
public class BearerTokens {

    private static final String PRINCIPAL = "cronist-dave-adapter";

    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    public BearerTokens(ObjectProvider<ClientRegistrationRepository> clientRegistrations) {
        this.clientRegistrations = clientRegistrations;
    }

    /** Does nothing if no client registration is given, fails if the given one is not configured. */
    public RestClient.Builder apply(RestClient.Builder builder, String clientRegistrationId) {
        if (!StringUtils.hasText(clientRegistrationId)) {
            return builder;
        }
        ClientRegistrationRepository registrations = clientRegistrations.getObject();
        if (registrations.findByRegistrationId(clientRegistrationId) == null) {
            throw new IllegalArgumentException("OAuth2 client registration " + clientRegistrationId + " not found");
        }
        // only supports client credentials by default, gets a new token once the current one expired
        OAuth2AuthorizedClientManager manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations,
                new InMemoryOAuth2AuthorizedClientService(registrations));
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(clientRegistrationId)
                .principal(PRINCIPAL)
                .build();
        return builder.requestInterceptor((request, body, execution) -> {
            OAuth2AuthorizedClient client = manager.authorize(authorizeRequest);
            request.getHeaders().set(HttpHeaders.AUTHORIZATION,
                    "Bearer " + client.getAccessToken().getTokenValue());
            return execution.execute(request, body);
        });
    }
}
