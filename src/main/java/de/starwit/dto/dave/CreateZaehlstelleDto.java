package de.starwit.dto.dave;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * To create a Zaehlstelle, {@code BearbeiteZaehlstelleDTO} in DAVe. Has no nummer: it follows a numbering scheme of
 * DAVe the adapter can't fill, DAVe finds the Zaehlstelle by its location instead.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateZaehlstelleDto(int stadtbezirkNummer, double lat, double lng, String kommentar,
        List<String> customSuchwoerter, boolean sichtbarDatenportal) {
}
