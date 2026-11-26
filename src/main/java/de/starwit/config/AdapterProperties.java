package de.starwit.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param zone               time zone DAVe runs in
 * @param correctBicycle     subtract bicycles from pedestrians, as a cyclist is also tracked as a person
 * @param intersectionMapping direction of a counting line to number of the Knotenarm in DAVe
 */
@ConfigurationProperties("adapter")
public record AdapterProperties(ZoneId zone, boolean correctBicycle, Map<String, Integer> intersectionMapping,
        Cronist cronist, Dave dave, Geoserver geoserver) {

    /**
     * @param testUrl              called periodically to check reachability and credentials, the check is disabled if
     *                             empty
     * @param clientRegistrationId OAuth2 client to authenticate with, requests are sent without a token if null
     */
    public record Cronist(String url, String testUrl, String clientRegistrationId,
            @DefaultValue("2s") Duration connectTimeout, @DefaultValue("30s") Duration readTimeout) {
    }

    /**
     * @param testUrl              called periodically to check reachability and credentials, the check is disabled if
     *                             empty
     * @param clientRegistrationId OAuth2 client to authenticate with, requests are sent without a token if null
     * @param stadtbezirkNummer    Stadtbezirk of the created Zaehlstellen whose Stadtteil is unknown or not mapped
     * @param stadtbezirkMapping   name of the Stadtteil found by the GeoServer to number of the Stadtbezirk in DAVe
     * @param kategorien           vehicle classes the Zaehlungen are created with
     * @param finalStatus          status of a Zaehlung once all its data is transferred
     */
    public record Dave(String url, String testUrl, String clientRegistrationId,
            @DefaultValue("2s") Duration connectTimeout, @DefaultValue("30s") Duration readTimeout,
            int stadtbezirkNummer, Map<String, Integer> stadtbezirkMapping, List<String> kategorien,
            FinalStatus finalStatus) {
    }

    /**
     * WFS of a GeoServer to find the Stadtteil of an observation area by its coordinates.
     *
     * @param url      of the WFS, the lookup is disabled if empty
     * @param typeName layer of the Stadtteile, with a polygon {@code geo} and the name of the Stadtteil {@code name}
     */
    public record Geoserver(String url, String typeName,
            @DefaultValue("2s") Duration connectTimeout, @DefaultValue("30s") Duration readTimeout) {
    }

    public enum FinalStatus {
        ACCOMPLISHED,
        ACTIVE
    }
}
