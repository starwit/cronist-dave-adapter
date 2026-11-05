package de.starwit.dto.cronist;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;

/** A counting as returned by Cronist, reduced to what is transferred. */
public record CountingDto(Long id, String name, String startDateTime, String endDateTime, String state,
        ObservationAreaDto observationArea, List<ObservatoryJobDto> observatoryJobs, List<PolygonDto> polygon) {

    public static final String STATE_COMPLETED = "COMPLETED";

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
