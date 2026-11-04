package de.starwit.adapter.cronist;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;

/** A counting as returned by Cronist, reduced to what is transferred. */
public record CronistCounting(Long id, String name, String startDateTime, String endDateTime, String state,
        ObservationArea observationArea, List<ObservatoryJob> observatoryJobs, List<Line> polygon) {

    public static final String STATE_COMPLETED = "COMPLETED";

    public record ObservationArea(Long id, String name, Double latitude, Double longitude) {
    }

    /** @param name name of the job in Observatory, its analytics data is found by it */
    public record ObservatoryJob(Long jobId, String name) {
    }

    /**
     * Polygon of a counting, only the ones with two points are counting lines.
     *
     * @param direction compass direction of the line, e.g. "NE", null if not set
     */
    public record Line(String name, String direction, List<Object> point) {

        public boolean isCountingLine() {
            return point != null && point.size() == 2;
        }
    }

    public Instant start() {
        return toInstant(startDateTime);
    }

    public Instant end() {
        return toInstant(endDateTime);
    }

    private static Instant toInstant(String dateTime) {
        if (dateTime == null) {
            return null;
        }
        TemporalAccessor parsed = DateTimeFormatter.ISO_DATE_TIME.parseBest(dateTime, OffsetDateTime::from,
                LocalDateTime::from);
        // Cronist stores its date times without time zone and assumes them to be UTC
        return parsed instanceof OffsetDateTime offsetDateTime
                ? offsetDateTime.toInstant()
                : ((LocalDateTime) parsed).toInstant(ZoneOffset.UTC);
    }
}
