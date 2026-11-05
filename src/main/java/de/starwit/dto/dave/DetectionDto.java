package de.starwit.dto.dave;

import java.time.Instant;
import java.util.UUID;

/** Counts of one Fahrbeziehung in one interval, {@code DetectionDTO} in DAVe. */
public record DetectionDto(UUID zaehlungId, Instant startUhrzeit, Instant endeUhrzeit, int von, int nach, int pkw,
        int lkw, int lastzuege, int busse, int kraftraeder, int fahrradfahrer, int fussgaenger) {
}
