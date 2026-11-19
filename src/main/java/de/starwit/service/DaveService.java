package de.starwit.service;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import de.starwit.config.AdapterProperties;
import de.starwit.config.BearerTokenConfigurer;
import de.starwit.dto.cronist.ImageDto;
import de.starwit.dto.dave.BackendIdDto;
import de.starwit.dto.dave.CreateZaehlstelleDto;
import de.starwit.dto.dave.CreateZaehlungDto;
import de.starwit.dto.dave.DetectionDto;
import de.starwit.dto.dave.SearchOptionsDto;
import de.starwit.dto.dave.SearchResultDto;
import de.starwit.dto.dave.UpdateStatusDto;
import de.starwit.dto.dave.ZaehlungDto;

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
     * An empty query finds all. DAVe has no lookup by location, so the comparison is left to the caller.
     *
     * @return all Zaehlstellen with their location
     */
    public List<SearchResultDto> findZaehlstellen() {
        List<SearchResultDto> results = restClient.post().uri("/search?query=")
                .body(new SearchOptionsDto(false, true, List.of()))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return results.stream()
                .filter(result -> TYPE_ZAEHLSTELLE.equals(result.type()))
                .toList();
    }

    /**
     * Requires the endpoint of branch {@code feature/ab#2325-get-values-from-video-upload} of dave-backend.
     *
     * @return all Zaehlungen whose projektNummer starts with the prefix, whatever their status and Zaehlstelle
     */
    public List<ZaehlungDto> findZaehlungenByProjektNummerPrefix(String prefix) {
        return restClient.get().uri("/zaehlung/byProjektNummerPrefix?prefix={prefix}", prefix).retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    /** @return id of the created Zaehlstelle, or of the existing one at its location, which DAVe then overwrites */
    public String createZaehlstelle(CreateZaehlstelleDto zaehlstelle) {
        return restClient.post().uri("/zaehlstelle/save").body(zaehlstelle).retrieve().body(BackendIdDto.class).id();
    }

    /** Replaces the image of the Zaehlstelle, if it has one. */
    public void saveZaehlstelleImage(String zaehlstelleId, ImageDto image) {
        // DAVe names the image after the Zaehlstelle, but a part needs a filename to be taken as a file
        Resource file = new ByteArrayResource(image.data()) {
            @Override
            public String getFilename() {
                return "zaehlstelle";
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(Objects.requireNonNullElse(image.contentType(), MediaType.APPLICATION_OCTET_STREAM));
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new HttpEntity<>(file, headers));
        restClient.post().uri("/zaehlstelleimage/{id}", zaehlstelleId).contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body).retrieve().toBodilessEntity();
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
