package de.starwit.dto;

/** An observation area as returned by Cronist. */
public record ObservationAreaDto(Long id, String name, Double latitude, Double longitude) {
}
