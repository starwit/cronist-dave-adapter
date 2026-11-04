package de.starwit.adapter.dave;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import de.starwit.adapter.config.AdapterProperties;
import de.starwit.adapter.config.BearerTokens;
import de.starwit.adapter.dave.DaveDtos.BackendId;
import de.starwit.adapter.dave.DaveDtos.Detection;
import de.starwit.adapter.dave.DaveDtos.ExistingZaehlstelle;
import de.starwit.adapter.dave.DaveDtos.NewZaehlstelle;
import de.starwit.adapter.dave.DaveDtos.NewZaehlung;
import de.starwit.adapter.dave.DaveDtos.SearchOptions;
import de.starwit.adapter.dave.DaveDtos.SearchResult;
import de.starwit.adapter.dave.DaveDtos.UpdateStatus;

/** Client for the DAVe backend API. All its endpoints used here require the role FACHADMIN. */
@Service
public class DaveClient {

    private static final String TYPE_ZAEHLSTELLE = "zaehlstelle";

    private final RestClient restClient;

    @Autowired
    DaveClient(RestClient.Builder builder, AdapterProperties properties, BearerTokens bearerTokens) {
        this(bearerTokens.apply(builder, properties.dave().clientRegistrationId())
                .baseUrl(properties.dave().url())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(HttpClientSettings.defaults()
                        .withTimeouts(properties.dave().connectTimeout(), properties.dave().readTimeout())))
                .build());
    }

    DaveClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * An empty query finds all. DAVe has no lookup by nummer and its search is word based, so the exact comparison is
     * left to the caller.
     *
     * @return the ids of all Zaehlstellen by their nummer
     */
    public Map<String, String> findZaehlstellenIdsByNummer() {
        List<SearchResult> results = restClient.post().uri("/search?query=")
                .body(new SearchOptions(false, true, List.of()))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return results.stream()
                .filter(result -> TYPE_ZAEHLSTELLE.equals(result.type()))
                .collect(Collectors.toMap(SearchResult::fachId, SearchResult::id, (first, second) -> first));
    }

    /** @return the Zaehlstelle with all its Zaehlungen, whatever their status */
    public ExistingZaehlstelle getZaehlstelle(String id) {
        return restClient.get().uri("/zaehlstelle/editZaehlstelle?id={id}", id).retrieve()
                .body(ExistingZaehlstelle.class);
    }

    /** @return id of the created Zaehlstelle, fails if one with this nummer exists */
    public String createZaehlstelle(NewZaehlstelle zaehlstelle) {
        return restClient.post().uri("/zaehlstelle/save").body(zaehlstelle).retrieve().body(BackendId.class).id();
    }

    /** @return id of the created Zaehlung */
    public String createZaehlung(NewZaehlung zaehlung, String zaehlstelleId) {
        return restClient.post().uri("/zaehlung/save?zaehlstelle_id={id}", zaehlstelleId).body(zaehlung).retrieve()
                .body(BackendId.class).id();
    }

    /**
     * Saves the detections of one interval. DAVe fills up missing Fahrbeziehungen with zeros for the interval of the
     * first detection only and skips intervals if the Zaehlung already has later data, so the intervals have to be sent
     * one by one, oldest first. Then sending an interval again does no harm.
     */
    public void saveDetections(List<Detection> detections) {
        restClient.post().uri("/detector/save-latest-detections").body(detections).retrieve().toBodilessEntity();
    }

    public void updateStatus(String zaehlungId, String status) {
        restClient.post().uri("/zaehlung/updateStatus").body(new UpdateStatus(zaehlungId, status)).retrieve()
                .toBodilessEntity();
    }
}
