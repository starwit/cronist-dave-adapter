package de.starwit.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.starwit.dto.cronist.CountingDto;
import de.starwit.dto.cronist.PolygonDto;
import de.starwit.dto.TransferPlanDto;
import de.starwit.repository.CrossingCountProjection;
import de.starwit.service.TestData;

class TransferPlanMapperTest {

    private static final Instant T1000 = Instant.parse("2026-10-05T10:00:00Z");
    private static final Instant T1015 = Instant.parse("2026-10-05T10:15:00Z");

    private final TransferPlanMapper mapper = new TransferPlanMapper(TestData.properties());

    @Test
    void linesOfSameDirectionShareOneKnotenarm() {
        CountingDto counting = TestData.counting(1, "2026-10-05T10:00:00", "2026-10-05T10:30:00",
                List.of(TestData.line("Nord 1", "N"), TestData.line("Nord 2", "N"), TestData.line("Ost", "E")));

        TransferPlanDto plan = mapper.convertToDto(counting, List.of()).orElseThrow();

        assertThat(plan.knotenarme()).containsEntry(1, "Nord 1 / Nord 2").containsEntry(2, "Ost").hasSize(2);
    }

    @Test
    void noPlanWithoutTwoUsableDirections() {
        CountingDto noDirections = TestData.counting(1, "2026-10-05T10:00:00", "2026-10-05T10:30:00",
                List.of(TestData.line("a", null), TestData.line("b", "N"),
                        new PolygonDto("area", "E", List.of("p1", "p2", "p3"))));

        assertThat(mapper.convertToDto(noDirections, List.of())).isEmpty();
    }

    @Test
    void noPlanWithoutTimeRangeAndCrossings() {
        assertThat(mapper.convertToDto(TestData.counting(1, null, null), List.of())).isEmpty();
    }

    @Test
    void intervalsAreQuarterHoursOfRangeWithExclusiveEnd() {
        TransferPlanDto plan = mapper
                .convertToDto(TestData.counting(1, "2026-10-05T10:07:00", "2026-10-05T10:30:00"), List.of())
                .orElseThrow();

        assertThat(plan.intervals()).containsExactly(T1000, T1015);
    }

    @Test
    void intervalsAcceptTimestampsWithOffset() {
        TransferPlanDto plan = mapper.convertToDto(TestData.counting(1, "2026-10-05T12:00:00+02:00", null), List.of())
                .orElseThrow();

        assertThat(plan.intervals()).containsExactly(T1000);
    }

    @Test
    void intervalsIncludeCrossingsOutsideOfRange() {
        Instant later = Instant.parse("2026-10-05T10:45:00Z");

        TransferPlanDto plan = mapper
                .convertToDto(TestData.counting(1), List.of(new CrossingCountProjection(later, 2, "N", "E", 1)))
                .orElseThrow();

        assertThat(plan.intervals()).hasSize(4).endsWith(later);
    }

    @Test
    void wholeLocalDayIsOneDay() {
        // midnight to midnight in Berlin
        TransferPlanDto plan = mapper
                .convertToDto(TestData.counting(1, "2026-10-04T22:00:00", "2026-10-05T22:00:00"), List.of())
                .orElseThrow();

        assertThat(plan.spansSeveralDays(TestData.properties().zone())).isFalse();
    }

    @Test
    void detectsCountingOverSeveralDays() {
        TransferPlanDto plan = mapper
                .convertToDto(TestData.counting(1, "2026-10-05T21:45:00", "2026-10-05T22:15:00"), List.of())
                .orElseThrow();

        assertThat(plan.spansSeveralDays(TestData.properties().zone())).isTrue();
    }
}
