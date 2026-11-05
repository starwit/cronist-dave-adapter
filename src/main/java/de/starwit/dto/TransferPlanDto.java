package de.starwit.dto;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;

import de.starwit.dto.dave.FahrbeziehungDto;
import de.starwit.repository.CrossingCountProjection;

/**
 * What is transferred of a counting.
 *
 * @param knotenarme number of the Knotenarm to its street name, one per direction of the counting lines
 * @param intervals  start of all quarter hours of the counting, oldest first
 */
public record TransferPlanDto(SortedMap<Integer, String> knotenarme, List<Instant> intervals,
        List<CrossingCountProjection> counts) {

    public static final Duration INTERVAL = Duration.ofMinutes(15);

    public Instant firstInterval() {
        return intervals.getFirst();
    }

    public LocalDate date(ZoneId zone) {
        return firstInterval().atZone(zone).toLocalDate();
    }

    public boolean spansSeveralDays(ZoneId zone) {
        return !date(zone).equals(intervals.getLast().atZone(zone).toLocalDate());
    }

    /** All movements from one Knotenarm to another, an object leaving where it came from is not counted. */
    public List<FahrbeziehungDto> fahrbeziehungen() {
        List<FahrbeziehungDto> fahrbeziehungen = new ArrayList<>();
        for (int von : knotenarme.keySet()) {
            for (int nach : knotenarme.keySet()) {
                if (von != nach) {
                    fahrbeziehungen.add(new FahrbeziehungDto(true, von, nach));
                }
            }
        }
        return fahrbeziehungen;
    }
}
