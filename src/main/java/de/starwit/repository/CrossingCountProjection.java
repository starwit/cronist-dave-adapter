package de.starwit.repository;

import java.time.Instant;

/**
 * Objects of a class that entered through one counting line and left through another.
 *
 * @param intervalStart start of the quarter hour the object left in
 * @param objectClassId class of the object as detected by the SAE
 * @param directionFrom direction of the line the object entered through, e.g. "N"
 * @param directionTo   direction of the line the object left through
 */
public record CrossingCountProjection(Instant intervalStart, long objectClassId, String directionFrom,
        String directionTo, long count) {
}
