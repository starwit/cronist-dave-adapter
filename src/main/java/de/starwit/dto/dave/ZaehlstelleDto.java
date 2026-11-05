package de.starwit.dto.dave;

import java.util.List;

/** An existing Zaehlstelle, {@code BearbeiteZaehlstelleDTO} in DAVe, reduced to its Zaehlungen. */
public record ZaehlstelleDto(String id, List<ZaehlungDto> zaehlungen) {
}
