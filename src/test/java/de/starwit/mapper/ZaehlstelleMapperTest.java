package de.starwit.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.starwit.dto.dave.CreateZaehlstelleDto;
import de.starwit.service.TestData;

class ZaehlstelleMapperTest {

    private final ZaehlstelleMapper mapper = new ZaehlstelleMapper(TestData.properties());

    @Test
    void zaehlstelleIsAtLocationOfArea() {
        CreateZaehlstelleDto zaehlstelle = mapper.convertToDto(TestData.AREA, Optional.empty());

        assertThat(zaehlstelle.lat()).isEqualTo(48.1);
        assertThat(zaehlstelle.lng()).isEqualTo(11.5);
    }

    @Test
    void kommentarContainsAreaMarkedAsCronist() {
        CreateZaehlstelleDto zaehlstelle = mapper.convertToDto(TestData.AREA, Optional.empty());

        assertThat(zaehlstelle.kommentar()).isEqualTo("Marienplatz - Cronist");
        assertThat(zaehlstelle.customSuchwoerter()).containsExactly("Marienplatz");
    }

    @Test
    void zaehlstelleIsVisibleInDatenportal() {
        assertThat(mapper.convertToDto(TestData.AREA, Optional.empty()).sichtbarDatenportal()).isTrue();
    }

    @Test
    void stadtbezirkIsMappedFromStadtteil() {
        assertThat(mapper.convertToDto(TestData.AREA, Optional.of("Stadtmitte")).stadtbezirkNummer()).isEqualTo(5);
    }

    @Test
    void stadtbezirkIsDefaultIfStadtteilIsUnknownOrNotMapped() {
        assertThat(mapper.convertToDto(TestData.AREA, Optional.empty()).stadtbezirkNummer()).isEqualTo(3);
        assertThat(mapper.convertToDto(TestData.AREA, Optional.of("Vorsfelde")).stadtbezirkNummer()).isEqualTo(3);
    }
}
