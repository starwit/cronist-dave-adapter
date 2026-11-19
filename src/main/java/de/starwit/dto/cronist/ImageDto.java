package de.starwit.dto.cronist;

import org.springframework.http.MediaType;

/** An image of an observation area as returned by Cronist. */
public record ImageDto(byte[] data, MediaType contentType) {
}
