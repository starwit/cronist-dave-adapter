package de.starwit.service;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import de.starwit.config.AdapterProperties;
import de.starwit.config.AdapterProperties.FinalStatus;
import de.starwit.dto.cronist.CountingDto;
import de.starwit.dto.cronist.ObservationAreaDto;
import de.starwit.dto.cronist.ObservatoryJobDto;
import de.starwit.dto.cronist.PolygonDto;

public final class TestData {

    public static final ObservationAreaDto AREA = new ObservationAreaDto(7L, "Marienplatz", 48.1, 11.5);

    private TestData() {
    }

    public static AdapterProperties properties(FinalStatus finalStatus, boolean correctBicycle) {
        return new AdapterProperties(ZoneId.of("Europe/Berlin"), correctBicycle,
                Map.of("N", 1, "E", 2, "S", 3, "W", 4),
                new AdapterProperties.Cronist("http://cronist", null, null, null),
                new AdapterProperties.Dave("http://dave", null, null, null, 3, List.of("PKW"), finalStatus));
    }

    public static AdapterProperties properties() {
        return properties(FinalStatus.ACCOMPLISHED, true);
    }

    public static PolygonDto line(String name, String direction) {
        return new PolygonDto(name, direction, List.of("p1", "p2"));
    }

    /** Lines to the north and east, from 2026-10-05 10:00 to 10:30 UTC. */
    public static CountingDto counting(long id) {
        return counting(id, "2026-10-05T10:00:00", "2026-10-05T10:30:00");
    }

    public static CountingDto counting(long id, String start, String end) {
        return counting(id, start, end, List.of(line("Nordstr", "N"), line("Ostweg", "E")));
    }

    public static CountingDto counting(long id, String start, String end, List<PolygonDto> lines) {
        return new CountingDto(id, "Counting " + id, start, end, "COMPLETED", AREA,
                List.of(new ObservatoryJobDto(1L, "job-n"), new ObservatoryJobDto(2L, "job-e")), lines);
    }
}
