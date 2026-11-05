package de.starwit.dto.dave;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** To create a Zaehlstelle, {@code BearbeiteZaehlstelleDTO} in DAVe. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateZaehlstelleDto(String nummer, int stadtbezirkNummer, double lat, double lng, String kommentar,
        List<String> customSuchwoerter, boolean sichtbarDatenportal) {
}
