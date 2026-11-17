package de.starwit.dto.dave;

/** An existing Zaehlung, {@code ZaehlungByProjektNummerDTO} in DAVe, without the Zaehlstelle the adapter doesn't need. */
public record ZaehlungDto(String id, String projektNummer, String status) {
}
