package de.starwit.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import de.starwit.config.AdapterProperties;
import de.starwit.config.BearerTokenConfigurer;
import de.starwit.dto.BackendIdDto;
import de.starwit.dto.CreateZaehlstelleDto;
import de.starwit.dto.CreateZaehlungDto;
import de.starwit.dto.DetectionDto;
import de.starwit.dto.SearchOptionsDto;
import de.starwit.dto.SearchResultDto;
import de.starwit.dto.UpdateStatusDto;
import de.starwit.dto.ZaehlstelleDto;

/** Calls the DAVe backend API. All its endpoints used here require the role FACHADMIN. */
@Service
public class DaveService {

    private static final String TYPE_ZAEHLSTELLE = "zaehlstelle";

    private final RestClient restClient;

    @Autowired
    DaveService(RestClient.Builder builder, AdapterProperties properties, BearerTokenConfigurer bearerTokens) {
        this(bearerTokens.apply(builder, properties.dave().clientRegistrationId())
                .baseUrl(properties.dave().url())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(HttpClientSettings.defaults()
                        .withTimeouts(properties.dave().connectTimeout(), properties.dave().readTimeout())))
                .build());
    }

    DaveService(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * An empty query finds all. DAVe has no lookup by nummer and its search is word based, so the exact comparison is
     * left to the caller.
     *
     * @return the ids of all Zaehlstellen by their nummer
     */
    public Map<String, String> findZaehlstellenIdsByNummer() {
        List<SearchResultDto> results = restClient.post().uri("/search?query=")
                .body(new SearchOptionsDto(false, true, List.of()))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return results.stream()
                .filter(result -> TYPE_ZAEHLSTELLE.equals(result.type()))
                .collect(Collectors.toMap(SearchResultDto::fachId, SearchResultDto::id, (first, second) -> first));
    }

    /** @return the Zaehlstelle with all its Zaehlungen, whatever their status */
    public ZaehlstelleDto getZaehlstelle(String id) {
        return restClient.get().uri("/zaehlstelle/editZaehlstelle?id={id}", id).retrieve()
                .body(ZaehlstelleDto.class);
    }

    /** @return id of the created Zaehlstelle, fails if one with this nummer exists */
    public String createZaehlstelle(CreateZaehlstelleDto zaehlstelle) {
        return restClient.post().uri("/zaehlstelle/save").body(zaehlstelle).retrieve().body(BackendIdDto.class).id();
    }

    /** @return id of the created Zaehlung */
    public String createZaehlung(CreateZaehlungDto zaehlung, String zaehlstelleId) {
        return restClient.post().uri("/zaehlung/save?zaehlstelle_id={id}", zaehlstelleId).body(zaehlung).retrieve()
                .body(BackendIdDto.class).id();
    }

    /**
     * Saves the detections of one interval. DAVe fills up missing Fahrbeziehungen with zeros for the interval of the
     * first detection only and skips intervals if the Zaehlung already has later data, so the intervals have to be sent
     * one by one, oldest first. Then sending an interval again does no harm.
     */
    public void saveDetections(List<DetectionDto> detections) {
        restClient.post().uri("/detector/save-latest-detections").body(detections).retrieve().toBodilessEntity();
    }

    public void updateStatus(String zaehlungId, String status) {
        restClient.post().uri("/zaehlung/updateStatus").body(new UpdateStatusDto(zaehlungId, status)).retrieve()
                .toBodilessEntity();
    }
}
