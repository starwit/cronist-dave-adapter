package de.starwit.mapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.starwit.config.AdapterProperties;
import de.starwit.dto.dave.CreateZaehlstelleDto;
import de.starwit.dto.cronist.ObservationAreaDto;

/** Maps an observation area of Cronist to a Zaehlstelle of DAVe. */
@Component
public class ZaehlstelleMapper {

    private static final Logger LOG = LoggerFactory.getLogger(ZaehlstelleMapper.class);

    private final AdapterProperties properties;

    public ZaehlstelleMapper(AdapterProperties properties) {
        this.properties = properties;
    }

    /**
     * DAVe has no name for a Zaehlstelle, so kommentar and Suchwoerter carry the area name, the kommentar marked as
     * coming from Cronist.
     *
     * @param area      with a location, DAVe identifies the Zaehlstelle by it
     * @param stadtteil the area is in, empty if unknown
     */
    public CreateZaehlstelleDto convertToDto(ObservationAreaDto area, Optional<String> stadtteil) {
        String kommentar = area.name() + " - Cronist";
        return new CreateZaehlstelleDto(toStadtbezirkNummer(area, stadtteil), area.latitude(), area.longitude(),
                kommentar, List.of(area.name()), false);
    }

    private int toStadtbezirkNummer(ObservationAreaDto area, Optional<String> stadtteil) {
        Map<String, Integer> mapping = Objects.requireNonNullElse(properties.dave().stadtbezirkMapping(), Map.of());
        Integer nummer = stadtteil.map(mapping::get).orElse(null);
        if (nummer != null) {
            return nummer;
        }
        if (stadtteil.isPresent()) {
            LOG.warn("No Stadtbezirk mapped for Stadtteil {} of observation area {}, using the default",
                    stadtteil.get(), area.name());
        }
        return properties.dave().stadtbezirkNummer();
    }
}
