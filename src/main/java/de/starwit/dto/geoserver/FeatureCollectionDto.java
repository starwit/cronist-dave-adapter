package de.starwit.dto.geoserver;

import java.util.List;

/** GeoJSON returned by a WFS GetFeature request, only with the properties the adapter reads. */
public record FeatureCollectionDto(List<FeatureDto> features) {

    public record FeatureDto(StadtteilDto properties) {
    }

    /** A feature of the layer of Stadtteile. */
    public record StadtteilDto(String name) {
    }
}
