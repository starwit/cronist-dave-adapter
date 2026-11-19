package de.starwit.dto.cronist;

/**
 * An observation area as returned by Cronist.
 *
 * @param imageId of the image of the area, null if it has none
 */
public record ObservationAreaDto(Long id, String name, Double latitude, Double longitude, Long imageId) {
}
