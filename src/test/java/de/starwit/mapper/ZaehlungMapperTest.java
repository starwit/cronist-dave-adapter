package de.starwit.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.starwit.dto.CreateZaehlungDto;
import de.starwit.dto.FahrbeziehungDto;
import de.starwit.dto.KnotenarmDto;
import de.starwit.dto.TransferPlanDto;
import de.starwit.service.TestData;

class ZaehlungMapperTest {

    private final TransferPlanMapper planMapper = new TransferPlanMapper(TestData.properties());
    private final ZaehlungMapper mapper = new ZaehlungMapper(TestData.properties());

    @Test
    void zaehlungHasKnotenarmePerDirectionAndAllFahrbeziehungen() {
        TransferPlanDto plan = planMapper.convertToDto(TestData.counting(1), List.of()).orElseThrow();

        CreateZaehlungDto zaehlung = mapper.convertToDto(TestData.counting(1), plan);

        assertThat(zaehlung.projektNummer()).isEqualTo("cronist-1");
        assertThat(zaehlung.projektName()).isEqualTo("Counting 1");
        assertThat(zaehlung.kreuzungsname()).isEqualTo("Marienplatz");
        assertThat(zaehlung.datum()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(zaehlung.status()).isEqualTo("CREATED");
        assertThat(zaehlung.quelle()).isEqualTo("VIDEO");
        assertThat(zaehlung.zaehldauer()).isEqualTo("SONSTIGE");
        assertThat(zaehlung.knotenarme()).containsExactly(new KnotenarmDto(1, "Nordstr"),
                new KnotenarmDto(2, "Ostweg"));
        assertThat(zaehlung.fahrbeziehungen()).containsExactly(new FahrbeziehungDto(true, 1, 2),
                new FahrbeziehungDto(true, 2, 1));
    }

    @Test
    void wholeLocalDayIs24Hours() {
        // midnight in Berlin
        TransferPlanDto plan = planMapper
                .convertToDto(TestData.counting(1, "2026-10-04T22:00:00", "2026-10-05T22:00:00"), List.of())
                .orElseThrow();

        CreateZaehlungDto zaehlung = mapper.convertToDto(TestData.counting(1), plan);

        assertThat(plan.intervals()).hasSize(96);
        assertThat(zaehlung.zaehldauer()).isEqualTo("DAUER_24_STUNDEN");
        assertThat(zaehlung.datum()).isEqualTo(LocalDate.of(2026, 10, 5));
    }
}
