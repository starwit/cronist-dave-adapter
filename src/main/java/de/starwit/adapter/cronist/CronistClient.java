package de.starwit.adapter.cronist;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import de.starwit.adapter.config.AdapterProperties;
import de.starwit.adapter.config.BearerTokens;

/** Client for the Cronist API. */
@Service
public class CronistClient {

    private final RestClient restClient;

    @Autowired
    CronistClient(RestClient.Builder builder, AdapterProperties properties, BearerTokens bearerTokens) {
        this(bearerTokens.apply(builder, properties.cronist().clientRegistrationId())
                .baseUrl(properties.cronist().url())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(HttpClientSettings.defaults()
                        .withTimeouts(properties.cronist().connectTimeout(), properties.cronist().readTimeout())))
                .build());
    }

    CronistClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** Only completed countings are reviewed by a user, their analytics data doesn't change anymore. */
    public List<CronistCounting> findCompletedCountings() {
        List<CronistCounting> countings = restClient.get().uri("/api/counting").retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return countings.stream()
                .filter(counting -> CronistCounting.STATE_COMPLETED.equals(counting.state()))
                .toList();
    }
}
