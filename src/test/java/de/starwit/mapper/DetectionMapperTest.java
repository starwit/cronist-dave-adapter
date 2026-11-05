package de.starwit.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import de.starwit.config.AdapterProperties.FinalStatus;
import de.starwit.dto.dave.DetectionDto;
import de.starwit.dto.TransferPlanDto;
import de.starwit.repository.CrossingCountProjection;
import de.starwit.service.TestData;

class DetectionMapperTest {

    private static final UUID ZAEHLUNG_ID = UUID.randomUUID();
    private static final Instant T1000 = Instant.parse("2026-10-05T10:00:00Z");
    private static final Instant T1015 = Instant.parse("2026-10-05T10:15:00Z");

    private final TransferPlanMapper planMapper = new TransferPlanMapper(TestData.properties());
    private final DetectionMapper mapper = new DetectionMapper(TestData.properties());

    private TransferPlanDto plan(CrossingCountProjection... counts) {
        return planMapper.convertToDto(TestData.counting(1), List.of(counts)).orElseThrow();
    }

    @Test
    void detectionsHaveAllFahrbeziehungenOfAllIntervalsInOrder() {
        TransferPlanDto plan = plan(
                new CrossingCountProjection(T1015, 2, "N", "E", 3),
                new CrossingCountProjection(T1015, 7, "N", "E", 2),
                new CrossingCountProjection(T1015, 5, "N", "E", 4),
                new CrossingCountProjection(T1015, 3, "N", "E", 5));

        List<List<DetectionDto>> detections = mapper.convertToDtos(ZAEHLUNG_ID, plan);

        assertThat(detections).hasSize(2);
        assertThat(detections.get(0)).containsExactly(
                new DetectionDto(ZAEHLUNG_ID, T1000, T1015, 1, 2, 0, 0, 0, 0, 0, 0, 0),
                new DetectionDto(ZAEHLUNG_ID, T1000, T1015, 2, 1, 0, 0, 0, 0, 0, 0, 0));
        Instant end = T1015.plusSeconds(900);
        assertThat(detections.get(1)).containsExactly(
                new DetectionDto(ZAEHLUNG_ID, T1015, end, 1, 2, 3, 2, 0, 4, 5, 0, 0),
                new DetectionDto(ZAEHLUNG_ID, T1015, end, 2, 1, 0, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void bicyclesAreSubtractedFromPedestrians() {
        TransferPlanDto plan = plan(
                new CrossingCountProjection(T1000, 0, "N", "E", 5),
                new CrossingCountProjection(T1000, 1, "N", "E", 2),
                new CrossingCountProjection(T1000, 0, "E", "N", 1),
                new CrossingCountProjection(T1000, 1, "E", "N", 2));

        List<DetectionDto> detections = mapper.convertToDtos(ZAEHLUNG_ID, plan).getFirst();

        assertThat(detections.get(0).fahrradfahrer()).isEqualTo(2);
        assertThat(detections.get(0).fussgaenger()).isEqualTo(3);
        assertThat(detections.get(1).fussgaenger()).isZero();
    }

    @Test
    void bicycleCorrectionCanBeDisabled() {
        DetectionMapper uncorrected = new DetectionMapper(TestData.properties(FinalStatus.ACCOMPLISHED, false));
        TransferPlanDto plan = plan(
                new CrossingCountProjection(T1000, 0, "N", "E", 5),
                new CrossingCountProjection(T1000, 1, "N", "E", 2));

        assertThat(uncorrected.convertToDtos(ZAEHLUNG_ID, plan).getFirst().getFirst().fussgaenger()).isEqualTo(5);
    }

    @Test
    void crossingsOfUnmappedDirectionsAreSkipped() {
        TransferPlanDto plan = plan(new CrossingCountProjection(T1000, 2, "NE", "E", 5));

        assertThat(mapper.convertToDtos(ZAEHLUNG_ID, plan).getFirst()).allMatch(detection -> detection.pkw() == 0);
    }
}
