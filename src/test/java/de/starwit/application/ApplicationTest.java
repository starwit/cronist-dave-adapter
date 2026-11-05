package de.starwit.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import de.starwit.config.AdapterProperties;
import de.starwit.config.AdapterProperties.FinalStatus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Starts the whole application with its default configuration and OAuth2 clients, no other service is running. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "adapter.cronist.url=http://localhost:1",
        "adapter.dave.url=http://localhost:1",
        "adapter.cronist.client-registration-id=cronist",
        "adapter.dave.client-registration-id=dave",
        "spring.security.oauth2.client.registration.cronist.provider=idp",
        "spring.security.oauth2.client.registration.cronist.client-id=id",
        "spring.security.oauth2.client.registration.cronist.client-secret=secret",
        "spring.security.oauth2.client.registration.cronist.authorization-grant-type=client_credentials",
        "spring.security.oauth2.client.registration.dave.provider=idp",
        "spring.security.oauth2.client.registration.dave.client-id=id",
        "spring.security.oauth2.client.registration.dave.client-secret=secret",
        "spring.security.oauth2.client.registration.dave.authorization-grant-type=client_credentials",
        "spring.security.oauth2.client.provider.idp.token-uri=http://localhost:1/token",
        "management.health.db.enabled=false"
})
class ApplicationTest {

    @Autowired
    private AdapterProperties properties;

    @LocalServerPort
    private int port;

    private int status(String path) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }

    @Test
    void bindsDefaultConfiguration() {
        assertThat(properties.intersectionMapping()).hasSize(8).containsEntry("NE", 5).containsEntry("NW", 8);
        assertThat(properties.zone().getId()).isEqualTo("Europe/Berlin");
        assertThat(properties.dave().finalStatus()).isEqualTo(FinalStatus.ACCOMPLISHED);
        assertThat(properties.dave().kategorien()).containsExactly("PKW");
    }

    @Test
    void onlyOffersHealthCheck() throws Exception {
        assertThat(status("/monitoring/health")).isEqualTo(200);
        assertThat(status("/monitoring")).isEqualTo(403);
        assertThat(status("/anything")).isEqualTo(403);
    }
}
