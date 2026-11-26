package de.starwit.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import de.starwit.config.AdapterProperties;
import de.starwit.config.BearerTokenConfigurer;
import de.starwit.dto.cronist.CountingDto;
import de.starwit.dto.cronist.ImageDto;

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

    /** @param imageId of an observation area */
    public ImageDto getImage(Long imageId) {
        ResponseEntity<byte[]> response = restClient.get().uri("/api/observationarea/image/{id}", imageId).retrieve()
                .toEntity(byte[].class);
        return new ImageDto(response.getBody(), response.getHeaders().getContentType());
    }

    /**
     * Calls a URL with the same client as all other requests, so the credentials are checked too.
     *
     * @param testUrl absolute, or relative to the base URL
     */
    public void checkConnection(String testUrl) {
        restClient.get().uri(testUrl).retrieve().toBodilessEntity();
    }
}
