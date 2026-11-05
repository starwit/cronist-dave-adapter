package de.starwit.dto;

import java.util.List;

/**
 * Polygon of a counting, only the ones with two points are counting lines.
 *
 * @param direction compass direction of the line, e.g. "NE", null if not set
 */
public record PolygonDto(String name, String direction, List<Object> point) {

    public boolean isCountingLine() {
        return point != null && point.size() == 2;
    }
}
