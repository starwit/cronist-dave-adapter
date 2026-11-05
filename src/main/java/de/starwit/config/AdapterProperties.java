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
        Cronist cronist, Dave dave) {

    /** @param clientRegistrationId OAuth2 client to authenticate with, requests are sent without a token if null */
    public record Cronist(String url, String clientRegistrationId,
            @DefaultValue("2s") Duration connectTimeout, @DefaultValue("30s") Duration readTimeout) {
    }

    /**
     * @param clientRegistrationId OAuth2 client to authenticate with, requests are sent without a token if null
     * @param stadtbezirkNummer    Stadtbezirk of the created Zaehlstellen
     * @param kategorien           vehicle classes the Zaehlungen are created with
     * @param finalStatus          status of a Zaehlung once all its data is transferred
     */
    public record Dave(String url, String clientRegistrationId,
            @DefaultValue("2s") Duration connectTimeout, @DefaultValue("30s") Duration readTimeout,
            int stadtbezirkNummer, List<String> kategorien, FinalStatus finalStatus) {
    }

    public enum FinalStatus {
        ACCOMPLISHED,
        ACTIVE
    }
}
