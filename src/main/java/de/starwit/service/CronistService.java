package de.starwit.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import de.starwit.config.AdapterProperties;
import de.starwit.config.BearerTokenConfigurer;
import de.starwit.dto.CountingDto;

/** Calls the Cronist API. */
@Service
public class CronistService {

    private final RestClient restClient;

    @Autowired
    CronistService(RestClient.Builder builder, AdapterProperties properties, BearerTokenConfigurer bearerTokens) {
        this(bearerTokens.apply(builder, properties.cronist().clientRegistrationId())
                .baseUrl(properties.cronist().url())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(HttpClientSettings.defaults()
                        .withTimeouts(properties.cronist().connectTimeout(), properties.cronist().readTimeout())))
                .build());
    }

    CronistService(RestClient restClient) {
        this.restClient = restClient;
    }

    /** Only completed countings are reviewed by a user, their analytics data doesn't change anymore. */
    public List<CountingDto> findCompletedCountings() {
        List<CountingDto> countings = restClient.get().uri("/api/counting").retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return countings.stream()
                .filter(counting -> CountingDto.STATE_COMPLETED.equals(counting.state()))
                .toList();
    }
}
