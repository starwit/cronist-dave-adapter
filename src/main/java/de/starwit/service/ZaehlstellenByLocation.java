package de.starwit.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import de.starwit.dto.dave.SearchResultDto;

/**
 * The Zaehlstellen of DAVe by their location. A Zaehlstelle has no key the adapter can set, DAVe assigns it to a
 * location instead: saving a new Zaehlstelle within {@link #TOLERANCE_METERS} of an existing one updates that one.
 * The comparison is the same as in DAVe, so the adapter finds the Zaehlstelle DAVe would update.
 */
class ZaehlstellenByLocation {

    /** As {@code ZaehlstelleIndex.LOCATION_TOLERANCE_METERS} in DAVe. */
    static final double TOLERANCE_METERS = 5.0;

    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

    private record Location(String id, double latitude, double longitude) {
    }

    private final Supplier<List<SearchResultDto>> zaehlstellen;
    private List<Location> locations;

    /** @param zaehlstellen search results of DAVe, read once on first use, the ones without a location are ignored */
    ZaehlstellenByLocation(Supplier<List<SearchResultDto>> zaehlstellen) {
        this.zaehlstellen = zaehlstellen;
    }

    private List<Location> locations() {
        if (locations == null) {
            locations = new ArrayList<>();
            zaehlstellen.get().stream()
                    .filter(zaehlstelle -> zaehlstelle.latitude() != null && zaehlstelle.longitude() != null)
                    .forEach(zaehlstelle -> locations.add(
                            new Location(zaehlstelle.id(), zaehlstelle.latitude(), zaehlstelle.longitude())));
        }
        return locations;
    }

    /** @return id of the Zaehlstelle at the location, empty if there is none or the location is unknown */
    Optional<String> find(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return Optional.empty();
        }
        // a bounding box as in DAVe: a degree of longitude gets shorter towards the poles
        double latTolerance = TOLERANCE_METERS / METERS_PER_DEGREE_LAT;
        double lngTolerance = TOLERANCE_METERS / (METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(latitude)));
        return locations().stream()
                .filter(location -> Math.abs(location.latitude() - latitude) <= latTolerance
                        && Math.abs(location.longitude() - longitude) <= lngTolerance)
                .map(Location::id)
                .findFirst();
    }

    void add(String id, double latitude, double longitude) {
        locations().add(new Location(id, latitude, longitude));
    }
}
