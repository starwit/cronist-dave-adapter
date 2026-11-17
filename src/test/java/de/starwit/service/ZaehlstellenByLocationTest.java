package de.starwit.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.starwit.dto.dave.SearchResultDto;

class ZaehlstellenByLocationTest {

    private static SearchResultDto zaehlstelle(String id, Double latitude, Double longitude) {
        return new SearchResultDto(id, "1", "zaehlstelle", latitude, longitude);
    }

    @Test
    void findsZaehlstelleWithinFiveMeters() {
        // in Wolfsburg a degree of longitude is about 68 km, so 0.00006 are about 4 m
        var zaehlstellen = new ZaehlstellenByLocation(List.of(zaehlstelle("a", 52.42274, 10.78656)));

        assertThat(zaehlstellen.find(52.4227, 10.7865)).contains("a");
    }

    @Test
    void findsNoZaehlstelleFurtherAway() {
        // 0.0001 are about 11 m to the north, and about 7 m to the east
        var zaehlstellen = new ZaehlstellenByLocation(List.of(
                zaehlstelle("north", 52.4228, 10.7865), zaehlstelle("east", 52.4227, 10.7866)));

        assertThat(zaehlstellen.find(52.4227, 10.7865)).isEmpty();
    }

    @Test
    void ignoresZaehlstellenAndAreasWithoutLocation() {
        var zaehlstellen = new ZaehlstellenByLocation(List.of(zaehlstelle("a", null, null)));

        assertThat(zaehlstellen.find(52.4227, 10.7865)).isEmpty();
        assertThat(zaehlstellen.find(null, null)).isEmpty();
    }

    @Test
    void findsAddedZaehlstelle() {
        var zaehlstellen = new ZaehlstellenByLocation(List.of());
        zaehlstellen.add("new", 52.4227, 10.7865);

        assertThat(zaehlstellen.find(52.4227, 10.7865)).contains("new");
    }
}
