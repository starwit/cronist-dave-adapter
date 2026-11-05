package de.starwit.mapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.starwit.config.AdapterProperties;
import de.starwit.dto.dave.DetectionDto;
import de.starwit.dto.dave.FahrbeziehungDto;
import de.starwit.dto.TransferPlanDto;
import de.starwit.repository.CrossingCountProjection;

/** Maps the line crossings of Observatory to the detections of the DAVe detector API. */
@Component
public class DetectionMapper {

    private static final Logger LOG = LoggerFactory.getLogger(DetectionMapper.class);

    private static final long CLASS_PERSON = 0;
    private static final long CLASS_BICYCLE = 1;
    private static final long CLASS_CAR = 2;
    private static final long CLASS_MOTORCYCLE = 3;
    private static final long CLASS_BUS = 5;
    private static final long CLASS_TRUCK = 7;

    private final AdapterProperties properties;

    public DetectionMapper(AdapterProperties properties) {
        this.properties = properties;
    }

    /**
     * @return for each interval, oldest first, the detections of all Fahrbeziehungen of the Zaehlung. Fahrbeziehungen
     *         without crossings are included, as DAVe would otherwise show no data instead of zero
     */
    public List<List<DetectionDto>> convertToDtos(UUID zaehlungId, TransferPlanDto plan) {
        Map<Instant, Map<FahrbeziehungDto, Counts>> countsByInterval = new HashMap<>();
        for (CrossingCountProjection count : plan.counts()) {
            Integer von = properties.intersectionMapping().get(count.directionFrom());
            Integer nach = properties.intersectionMapping().get(count.directionTo());
            if (von == null || nach == null) {
                LOG.warn("Skipping crossings with unmapped direction: {}", count);
                continue;
            }
            countsByInterval.computeIfAbsent(count.intervalStart(), start -> new HashMap<>())
                    .computeIfAbsent(new FahrbeziehungDto(true, von, nach), fahrbeziehung -> new Counts())
                    .add(count.objectClassId(), Math.toIntExact(count.count()));
        }
        List<FahrbeziehungDto> fahrbeziehungen = plan.fahrbeziehungen();
        return plan.intervals().stream()
                .map(start -> fahrbeziehungen.stream()
                        .map(fahrbeziehung -> countsByInterval.getOrDefault(start, Map.of())
                                .getOrDefault(fahrbeziehung, new Counts())
                                .toDetection(zaehlungId, start, fahrbeziehung, properties.correctBicycle()))
                        .toList())
                .toList();
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

        DetectionDto toDetection(UUID zaehlungId, Instant start, FahrbeziehungDto fahrbeziehung,
                boolean correctBicycle) {
            int persons = correctBicycle ? Math.max(0, fussgaenger - fahrradfahrer) : fussgaenger;
            return new DetectionDto(zaehlungId, start, start.plus(TransferPlanDto.INTERVAL), fahrbeziehung.von(),
                    fahrbeziehung.nach(), pkw, lkw, 0, busse, kraftraeder, fahrradfahrer, persons);
        }
    }
}
