package de.starwit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class BearerTokenConfigurerTest {

    private HttpServer server;
    private String baseUrl;
    private final List<String> authorizationHeaders = new CopyOnWriteArrayList<>();
    private final List<String> tokenRequests = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/token", exchange -> {
            tokenRequests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, """
                    {"access_token": "the-token", "token_type": "Bearer", "expires_in": 300}
                    """);
        });
        server.createContext("/api", exchange -> {
            authorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, "[]");
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private BearerTokenConfigurer bearerTokens() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("dave")
                .clientId("client")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri(baseUrl + "/token")
                .build();
        var beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("registrations", new InMemoryClientRegistrationRepository(registration));
        return new BearerTokenConfigurer(beanFactory.getBeanProvider(ClientRegistrationRepository.class));
    }

    private void callApi(RestClient.Builder builder) {
        builder.baseUrl(baseUrl).build().get().uri("/api").retrieve().toBodilessEntity();
    }

    @Test
    void addsTokenAndReusesIt() {
        RestClient.Builder builder = bearerTokens().apply(RestClient.builder(), "dave");

        callApi(builder);
        callApi(builder);

        assertThat(authorizationHeaders).containsExactly("Bearer the-token", "Bearer the-token");
        assertThat(tokenRequests).hasSize(1);
        assertThat(tokenRequests.getFirst()).contains("grant_type=client_credentials");
    }

    @Test
    void addsNoTokenWithoutClientRegistration() {
        callApi(new BearerTokenConfigurer(new StaticListableBeanFactory().getBeanProvider(ClientRegistrationRepository.class))
                .apply(RestClient.builder(), null));

        assertThat(authorizationHeaders).hasSize(1).containsOnlyNulls();
    }

    @Test
    void failsOnUnknownClientRegistration() {
        assertThatThrownBy(() -> bearerTokens().apply(RestClient.builder(), "unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
