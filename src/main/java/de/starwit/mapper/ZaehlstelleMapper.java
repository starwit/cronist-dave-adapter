package de.starwit.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import de.starwit.config.AdapterProperties;
import de.starwit.dto.dave.CreateZaehlstelleDto;
import de.starwit.dto.cronist.ObservationAreaDto;

/** Maps an observation area of Cronist to a Zaehlstelle of DAVe. */
@Component
public class ZaehlstelleMapper {

    private final AdapterProperties properties;

    public ZaehlstelleMapper(AdapterProperties properties) {
        this.properties = properties;
    }

    /** DAVe has no name for a Zaehlstelle, its nummer is the only human readable key and unique. */
    public CreateZaehlstelleDto convertToDto(ObservationAreaDto area) {
        return new CreateZaehlstelleDto(area.name(), properties.dave().stadtbezirkNummer(), area.latitude(),
                area.longitude(), "Created by cronist-dave-adapter", List.of(area.name()), false);
    }
}
