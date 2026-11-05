package de.starwit.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.starwit.dto.dave.CreateZaehlstelleDto;
import de.starwit.service.TestData;

class ZaehlstelleMapperTest {

    @Test
    void zaehlstelleIsNamedAfterArea() {
        CreateZaehlstelleDto zaehlstelle = new ZaehlstelleMapper(TestData.properties()).convertToDto(TestData.AREA);

        assertThat(zaehlstelle.nummer()).isEqualTo("Marienplatz");
        assertThat(zaehlstelle.lat()).isEqualTo(48.1);
        assertThat(zaehlstelle.lng()).isEqualTo(11.5);
        assertThat(zaehlstelle.stadtbezirkNummer()).isEqualTo(3);
    }
}
