package de.starwit.dto.dave;

import java.util.List;

/** {@code SearchAndFilterOptionsDTO} in DAVe. */
public record SearchOptionsDto(boolean searchInMessstellen, boolean searchInZaehlstellen,
        List<String> messstelleVerkehrsart) {
}
