package de.starwit.mapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.starwit.config.AdapterProperties;
import de.starwit.dto.CountingDto;
import de.starwit.dto.PolygonDto;
import de.starwit.dto.TransferPlanDto;
import de.starwit.repository.CrossingCountProjection;

/** Maps a counting of Cronist and its line crossings to what is transferred to DAVe. */
@Component
public class TransferPlanMapper {

    private static final Logger LOG = LoggerFactory.getLogger(TransferPlanMapper.class);

    private final AdapterProperties properties;

    public TransferPlanMapper(AdapterProperties properties) {
        this.properties = properties;
    }

    /** @return empty if the counting has no data to transfer, the reason is logged */
    public Optional<TransferPlanDto> convertToDto(CountingDto counting, List<CrossingCountProjection> counts) {
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
        return Optional.of(new TransferPlanDto(knotenarme, intervals, counts));
    }

    private SortedMap<Integer, String> knotenarme(CountingDto counting) {
        SortedMap<Integer, String> knotenarme = new TreeMap<>();
        for (PolygonDto line : Objects.requireNonNullElse(counting.polygon(), List.<PolygonDto>of())) {
            if (!line.isCountingLine()) {
                continue;
            }
            Integer nummer = line.direction() == null ? null : properties.intersectionMapping().get(line.direction());
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
    private List<Instant> intervals(CountingDto counting, List<CrossingCountProjection> counts) {
        // the end of the range is exclusive, of a crossing's interval inclusive
        Instant declaredEnd = counting.end() == null ? null : intervalStart(counting.end().minusNanos(1));
        Stream<Instant> bounds = Stream.concat(
                Stream.of(counting.start() == null ? null : intervalStart(counting.start()), declaredEnd),
                counts.stream().map(CrossingCountProjection::intervalStart));
        List<Instant> sorted = bounds.filter(Objects::nonNull).sorted().toList();
        if (sorted.isEmpty()) {
            return List.of();
        }
        List<Instant> intervals = new ArrayList<>();
        for (Instant start = sorted.getFirst(); !start.isAfter(sorted.getLast()); start = start
                .plus(TransferPlanDto.INTERVAL)) {
            intervals.add(start);
        }
        return intervals;
    }

    private static Instant intervalStart(Instant instant) {
        long seconds = TransferPlanDto.INTERVAL.toSeconds();
        return Instant.ofEpochSecond(Math.floorDiv(instant.getEpochSecond(), seconds) * seconds);
    }
}
