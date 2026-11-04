package de.starwit.adapter.transfer;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.starwit.adapter.analytics.CrossingCount;
import de.starwit.adapter.config.AdapterProperties;
import de.starwit.adapter.cronist.CronistCounting;
import de.starwit.adapter.cronist.CronistCounting.Line;
import de.starwit.adapter.cronist.CronistCounting.ObservationArea;
import de.starwit.adapter.dave.DaveDtos.Detection;
import de.starwit.adapter.dave.DaveDtos.Fahrbeziehung;
import de.starwit.adapter.dave.DaveDtos.Knotenarm;
import de.starwit.adapter.dave.DaveDtos.NewZaehlstelle;
import de.starwit.adapter.dave.DaveDtos.NewZaehlung;

/** Maps from the Cronist and Observatory model to the DAVe model. */
@Component
public class CountingMapper {

    private static final Logger LOG = LoggerFactory.getLogger(CountingMapper.class);

    private static final Duration INTERVAL = Duration.ofMinutes(15);
    private static final int INTERVALS_PER_DAY = 96;

    private static final long CLASS_PERSON = 0;
    private static final long CLASS_BICYCLE = 1;
    private static final long CLASS_CAR = 2;
    private static final long CLASS_MOTORCYCLE = 3;
    private static final long CLASS_BUS = 5;
    private static final long CLASS_TRUCK = 7;

    private final AdapterProperties properties;

    public CountingMapper(AdapterProperties properties) {
        this.properties = properties;
    }

    /**
     * What is transferred of a counting.
     *
     * @param knotenarme number of the Knotenarm to its street name, one per direction of the counting lines
     * @param intervals  start of all quarter hours of the counting, oldest first
     */
    public record TransferPlan(SortedMap<Integer, String> knotenarme, List<Instant> intervals,
            List<CrossingCount> counts) {

        public Instant firstInterval() {
            return intervals.getFirst();
        }
    }

    /** @return empty if the counting has no data to transfer, the reason is logged */
    public Optional<TransferPlan> plan(CronistCounting counting, List<CrossingCount> counts) {
        SortedMap<Integer, String> knotenarme = knotenarme(counting);
        if (knotenarme.size() < 2) {
            LOG.warn("Counting {} has {} usable directions, at least 2 are needed", counting.name(),
                    knotenarme.size());
            return Optional.empty();
        }
        List<Instant> intervals = intervals(counting, counts);
        if (intervals.isEmpty()) {
            LOG.warn("Counting {} has neither a time range nor line crossings", counting.name());
            return Optional.empty();
        }
        return Optional.of(new TransferPlan(knotenarme, intervals, counts));
    }

    /** DAVe has no name for a Zaehlstelle, its nummer is the only human readable key and unique. */
    public NewZaehlstelle toZaehlstelle(ObservationArea area) {
        return new NewZaehlstelle(area.name(), properties.dave().stadtbezirkNummer(), area.latitude(),
                area.longitude(), "Created by cronist-dave-adapter", List.of(area.name()), false);
    }

    public NewZaehlung toZaehlung(CronistCounting counting, TransferPlan plan) {
        ObservationArea area = counting.observationArea();
        List<Knotenarm> knotenarme = plan.knotenarme().entrySet().stream()
                .map(entry -> new Knotenarm(entry.getKey(), entry.getValue()))
                .toList();
        List<Fahrbeziehung> fahrbeziehungen = fahrbeziehungen(plan).stream()
                .map(route -> new Fahrbeziehung(true, route.von(), route.nach()))
                .toList();
        return new NewZaehlung(date(plan.firstInterval()), "N", area.latitude(), area.longitude(),
                projektNummer(counting), counting.name(), area.name(), false, false, false,
                properties.dave().kategorien(), 15, "NO_INFORMATION", "CREATED", "VIDEO", zaehldauer(plan), knotenarme,
                fahrbeziehungen);
    }

    /** Identifies the Zaehlung of a counting in DAVe. */
    public static String projektNummer(CronistCounting counting) {
        return "cronist-" + counting.id();
    }

    public boolean spansSeveralDays(TransferPlan plan) {
        return !date(plan.firstInterval()).equals(date(plan.intervals().getLast()));
    }

    /**
     * @return for each interval, oldest first, the detections of all Fahrbeziehungen of the Zaehlung. Fahrbeziehungen
     *         without crossings are included, as DAVe would otherwise show no data instead of zero
     */
    public List<List<Detection>> toDetections(UUID zaehlungId, TransferPlan plan) {
        Map<Instant, Map<Route, Counts>> countsByInterval = new HashMap<>();
        for (CrossingCount count : plan.counts()) {
            Integer von = properties.intersectionMapping().get(count.directionFrom());
            Integer nach = properties.intersectionMapping().get(count.directionTo());
            if (von == null || nach == null) {
                LOG.warn("Skipping crossings with unmapped direction: {}", count);
                continue;
            }
            countsByInterval.computeIfAbsent(count.intervalStart(), start -> new HashMap<>())
                    .computeIfAbsent(new Route(von, nach), route -> new Counts())
                    .add(count.objectClassId(), Math.toIntExact(count.count()));
        }
        List<Route> routes = fahrbeziehungen(plan);
        return plan.intervals().stream()
                .map(start -> routes.stream()
                        .map(route -> countsByInterval.getOrDefault(start, Map.of())
                                .getOrDefault(route, new Counts())
                                .toDetection(zaehlungId, start, route, properties.correctBicycle()))
                        .toList())
                .toList();
    }

    private SortedMap<Integer, String> knotenarme(CronistCounting counting) {
        SortedMap<Integer, String> knotenarme = new TreeMap<>();
        for (Line line : Objects.requireNonNullElse(counting.polygon(), List.<Line>of())) {
            if (!line.isCountingLine()) {
                continue;
            }
            Integer nummer = properties.intersectionMapping().get(line.direction());
            if (nummer == null) {
                LOG.warn("Counting {}: line {} has no mapped direction ({}), its crossings are not transferred",
                        counting.name(), line.name(), line.direction());
                continue;
            }
            knotenarme.merge(nummer, line.name(), (first, second) -> first + " / " + second);
        }
        return knotenarme;
    }

    /** DAVe knows no time range of a counting, the crossings are included as videos can exceed the counting's. */
    private List<Instant> intervals(CronistCounting counting, List<CrossingCount> counts) {
        // the end of the range is exclusive, of a crossing's interval inclusive
        Instant declaredEnd = counting.end() == null ? null : intervalStart(counting.end().minusNanos(1));
        Stream<Instant> bounds = Stream.concat(
                Stream.of(counting.start() == null ? null : intervalStart(counting.start()), declaredEnd),
                counts.stream().map(CrossingCount::intervalStart));
        List<Instant> sorted = bounds.filter(Objects::nonNull).sorted().toList();
        if (sorted.isEmpty()) {
            return List.of();
        }
        List<Instant> intervals = new ArrayList<>();
        for (Instant start = sorted.getFirst(); !start.isAfter(sorted.getLast()); start = start.plus(INTERVAL)) {
            intervals.add(start);
        }
        return intervals;
    }

    private static Instant intervalStart(Instant instant) {
        return Instant.ofEpochSecond(Math.floorDiv(instant.getEpochSecond(), INTERVAL.toSeconds())
                * INTERVAL.toSeconds());
    }

    /** All movements from one Knotenarm to another, an object leaving where it came from is not counted. */
    private static List<Route> fahrbeziehungen(TransferPlan plan) {
        List<Route> routes = new ArrayList<>();
        for (int von : plan.knotenarme().keySet()) {
            for (int nach : plan.knotenarme().keySet()) {
                if (von != nach) {
                    routes.add(new Route(von, nach));
                }
            }
        }
        return routes;
    }

    private LocalDate date(Instant instant) {
        return instant.atZone(properties.zone()).toLocalDate();
    }

    /**
     * DAVe checks on completion that a Zaehlung with a fixed duration has exactly its intervals, so anything but a
     * whole day is SONSTIGE.
     */
    private String zaehldauer(TransferPlan plan) {
        boolean startsAtMidnight = plan.firstInterval().atZone(properties.zone()).toLocalTime().equals(LocalTime.MIDNIGHT);
        return startsAtMidnight && plan.intervals().size() == INTERVALS_PER_DAY && !spansSeveralDays(plan)
                ? "DAUER_24_STUNDEN"
                : "SONSTIGE";
    }

    private record Route(int von, int nach) {
    }

    private static final class Counts {
        private int pkw;
        private int lkw;
        private int busse;
        private int kraftraeder;
        private int fahrradfahrer;
        private int fussgaenger;

        void add(long objectClassId, int count) {
            if (objectClassId == CLASS_CAR) {
                pkw += count;
            } else if (objectClassId == CLASS_TRUCK) {
                lkw += count;
            } else if (objectClassId == CLASS_BUS) {
                busse += count;
            } else if (objectClassId == CLASS_MOTORCYCLE) {
                kraftraeder += count;
            } else if (objectClassId == CLASS_BICYCLE) {
                fahrradfahrer += count;
            } else if (objectClassId == CLASS_PERSON) {
                fussgaenger += count;
            }
        }

        Detection toDetection(UUID zaehlungId, Instant start, Route route, boolean correctBicycle) {
            int persons = correctBicycle ? Math.max(0, fussgaenger - fahrradfahrer) : fussgaenger;
            return new Detection(zaehlungId, start, start.plus(INTERVAL), route.von(), route.nach(), pkw, lkw, 0,
                    busse, kraftraeder, fahrradfahrer, persons);
        }
    }
}
