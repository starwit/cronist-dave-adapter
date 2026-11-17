package de.starwit.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import de.starwit.config.AdapterProperties;
import de.starwit.dto.geoserver.FeatureCollectionDto;
import de.starwit.dto.geoserver.FeatureCollectionDto.FeatureDto;
import de.starwit.dto.geoserver.FeatureCollectionDto.StadtteilDto;

/** Finds the Stadtteil of a location with the WFS of a GeoServer, e.g. the one of the city of Wolfsburg. */
@Service
public class StadtteilService {

    private final RestClient restClient;
    private final String typeName;

    @Autowired
    StadtteilService(RestClient.Builder builder, AdapterProperties properties) {
        this(build(builder, properties.geoserver()), properties.geoserver() == null ? null
                : properties.geoserver().typeName());
    }

    StadtteilService(RestClient restClient, String typeName) {
        this.restClient = restClient;
        this.typeName = typeName;
    }

    private static RestClient build(RestClient.Builder builder, AdapterProperties.Geoserver geoserver) {
        if (geoserver == null || geoserver.url() == null || geoserver.url().isBlank()) {
            return null;
        }
        return builder.baseUrl(geoserver.url())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(HttpClientSettings.defaults()
                        .withTimeouts(geoserver.connectTimeout(), geoserver.readTimeout())))
                .build();
    }

    /**
     * The filter is evaluated by the GeoServer on its own, precise geometries: the ones it returns in WGS84 are rounded.
     *
     * @return name of the Stadtteil containing the location, empty if there is none, the location is unknown or no
     *         GeoServer is configured
     */
    public Optional<String> findStadtteil(Double latitude, Double longitude) {
        if (restClient == null || latitude == null || longitude == null) {
            return Optional.empty();
        }
        // with the SRID, the point is longitude first, whatever the axis order of the layer
        String filter = "INTERSECTS(geo,SRID=4326;POINT(" + longitude + " " + latitude + "))";
        FeatureCollectionDto result = restClient.get()
                .uri(uri -> uri.queryParam("SERVICE", "WFS")
                        .queryParam("VERSION", "2.0.0")
                        .queryParam("REQUEST", "GetFeature")
                        .queryParam("typeNames", "{typeName}")
                        .queryParam("outputFormat", "application/json")
                        .queryParam("propertyName", "name")
                        .queryParam("CQL_FILTER", "{filter}")
                        .build(Map.of("typeName", typeName, "filter", filter)))
                .retrieve()
                .body(FeatureCollectionDto.class);
        return Objects.requireNonNullElse(result == null ? null : result.features(), List.<FeatureDto>of()).stream()
                .map(FeatureDto::properties)
                .filter(Objects::nonNull)
                .map(StadtteilDto::name)
                .filter(name -> name != null && !name.isBlank())
                .findFirst();
    }
}
