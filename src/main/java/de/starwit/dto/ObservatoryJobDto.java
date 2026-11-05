package de.starwit.dto;

/** @param name name of the job in Observatory, its analytics data is found by it */
public record ObservatoryJobDto(Long jobId, String name) {
}
