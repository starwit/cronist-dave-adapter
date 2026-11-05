package de.starwit.mapper;

import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Component;

import de.starwit.config.AdapterProperties;
import de.starwit.dto.CountingDto;
import de.starwit.dto.CreateZaehlungDto;
import de.starwit.dto.KnotenarmDto;
import de.starwit.dto.ObservationAreaDto;
import de.starwit.dto.TransferPlanDto;

/** Maps a counting of Cronist to a Zaehlung of DAVe. */
@Component
public class ZaehlungMapper {

    private static final int INTERVALS_PER_DAY = 96;

    private final AdapterProperties properties;

    public ZaehlungMapper(AdapterProperties properties) {
        this.properties = properties;
    }

    public CreateZaehlungDto convertToDto(CountingDto counting, TransferPlanDto plan) {
        ObservationAreaDto area = counting.observationArea();
        List<KnotenarmDto> knotenarme = plan.knotenarme().entrySet().stream()
                .map(entry -> new KnotenarmDto(entry.getKey(), entry.getValue()))
                .toList();
        return new CreateZaehlungDto(plan.date(properties.zone()), "N", area.latitude(), area.longitude(),
                toProjektNummer(counting), counting.name(), area.name(), false, false, false,
                properties.dave().kategorien(), 15, "NO_INFORMATION", "CREATED", "VIDEO", zaehldauer(plan), knotenarme,
                plan.fahrbeziehungen());
    }

    /** Identifies the Zaehlung of a counting in DAVe. */
    public static String toProjektNummer(CountingDto counting) {
        return "cronist-" + counting.id();
    }

    /**
     * DAVe checks on completion that a Zaehlung with a fixed duration has exactly its intervals, so anything but a
     * whole day is SONSTIGE.
     */
    private String zaehldauer(TransferPlanDto plan) {
        boolean startsAtMidnight = plan.firstInterval().atZone(properties.zone()).toLocalTime()
                .equals(LocalTime.MIDNIGHT);
        return startsAtMidnight && plan.intervals().size() == INTERVALS_PER_DAY
                && !plan.spansSeveralDays(properties.zone())
                        ? "DAUER_24_STUNDEN"
                        : "SONSTIGE";
    }
}
