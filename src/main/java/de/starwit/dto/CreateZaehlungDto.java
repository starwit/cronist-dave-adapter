package de.starwit.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** To create a Zaehlung, {@code BearbeiteZaehlungDTO} in DAVe. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateZaehlungDto(LocalDate datum, String zaehlart, double lat, double lng, String projektNummer,
        String projektName, String kreuzungsname, boolean sonderzaehlung, boolean dauerzaehlung,
        boolean kreisverkehr, List<String> kategorien, int zaehlIntervall, String wetter, String status,
        String quelle, String zaehldauer, List<KnotenarmDto> knotenarme, List<FahrbeziehungDto> fahrbeziehungen) {
}
