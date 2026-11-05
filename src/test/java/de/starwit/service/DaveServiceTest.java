package de.starwit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import de.starwit.dto.CreateZaehlstelleDto;
import de.starwit.dto.CreateZaehlungDto;
import de.starwit.dto.DetectionDto;
import de.starwit.dto.FahrbeziehungDto;
import de.starwit.dto.KnotenarmDto;
import de.starwit.dto.ZaehlstelleDto;

/** Field names and formats are the ones of the DTOs of dave-backend. */
class DaveServiceTest {

    private MockRestServiceServer server;
    private DaveService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://dave");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new DaveService(builder.build());
    }

    @Test
    void findsAllZaehlstellenButNoMessstellen() {
        server.expect(requestTo("http://dave/search?query="))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"searchInMessstellen": false, "searchInZaehlstellen": true, "messstelleVerkehrsart": []}
                        """))
                .andRespond(withSuccess("""
                        [{"id": "a", "fachId": "Marienplatz", "type": "zaehlstelle", "latitude": 1.0,
                          "tooltip": {"zaehlstellennnummer": "Marienplatz"}, "zaehlartenKarte": []},
                         {"id": "b", "fachId": "Marienplatz", "type": "messstelle"}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(service.findZaehlstellenIdsByNummer()).containsOnlyKeys("Marienplatz")
                .containsEntry("Marienplatz", "a");
    }

    @Test
    void readsZaehlungenOfZaehlstelle() {
        server.expect(requestTo("http://dave/zaehlstelle/editZaehlstelle?id=a"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id": "a", "nummer": "Marienplatz", "zaehlungen": [
                          {"id": "z", "projektNummer": "cronist-1", "status": "CREATED", "datum": "05.10.2026"}]}
                        """, MediaType.APPLICATION_JSON));

        ZaehlstelleDto zaehlstelle = service.getZaehlstelle("a");

        assertThat(zaehlstelle.zaehlungen().getFirst().status()).isEqualTo("CREATED");
        assertThat(zaehlstelle.zaehlungen().getFirst().projektNummer()).isEqualTo("cronist-1");
    }

    @Test
    void createsZaehlstelle() {
        server.expect(requestTo("http://dave/zaehlstelle/save"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"nummer": "Marienplatz", "stadtbezirkNummer": 3, "lat": 48.1, "lng": 11.5, "kommentar": "c",
                         "customSuchwoerter": ["Marienplatz"], "sichtbarDatenportal": false}
                        """, org.springframework.test.json.JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"id\": \"a\"}", MediaType.APPLICATION_JSON));

        assertThat(service.createZaehlstelle(new CreateZaehlstelleDto("Marienplatz", 3, 48.1, 11.5, "c",
                List.of("Marienplatz"), false))).isEqualTo("a");
    }

    @Test
    void createsZaehlung() {
        server.expect(requestTo("http://dave/zaehlung/save?zaehlstelle_id=a"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"datum": "2026-10-05", "zaehlart": "N", "lat": 48.1, "lng": 11.5, "projektNummer": "cronist-1",
                         "projektName": "Counting", "kreuzungsname": "Marienplatz", "sonderzaehlung": false,
                         "dauerzaehlung": false, "kreisverkehr": false, "kategorien": ["PKW"], "zaehlIntervall": 15,
                         "wetter": "NO_INFORMATION", "status": "CREATED", "quelle": "VIDEO", "zaehldauer": "SONSTIGE",
                         "knotenarme": [{"nummer": 1, "strassenname": "Nordstr"}],
                         "fahrbeziehungen": [{"isKreuzung": true, "von": 1, "nach": 2}]}
                        """, org.springframework.test.json.JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"id\": \"z\"}", MediaType.APPLICATION_JSON));

        assertThat(service.createZaehlung(new CreateZaehlungDto(LocalDate.of(2026, 10, 5), "N", 48.1, 11.5, "cronist-1",
                "Counting", "Marienplatz", false, false, false, List.of("PKW"), 15, "NO_INFORMATION", "CREATED",
                "VIDEO", "SONSTIGE", List.of(new KnotenarmDto(1, "Nordstr")), List.of(new FahrbeziehungDto(true, 1, 2))),
                "a")).isEqualTo("z");
    }

    @Test
    void savesDetections() {
        UUID id = UUID.fromString("339f992e-0925-4f6d-9e75-099bc520ad2c");
        server.expect(requestTo("http://dave/detector/save-latest-detections"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        [{"zaehlungId": "339f992e-0925-4f6d-9e75-099bc520ad2c",
                          "startUhrzeit": "2026-10-05T10:00:00Z", "endeUhrzeit": "2026-10-05T10:15:00Z",
                          "von": 1, "nach": 2, "pkw": 1, "lkw": 2, "lastzuege": 3, "busse": 4, "kraftraeder": 5,
                          "fahrradfahrer": 6, "fussgaenger": 7}]
                        """, org.springframework.test.json.JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"id\": \"z\"}", MediaType.APPLICATION_JSON));

        service.saveDetections(List.of(new DetectionDto(id, Instant.parse("2026-10-05T10:00:00Z"),
                Instant.parse("2026-10-05T10:15:00Z"), 1, 2, 1, 2, 3, 4, 5, 6, 7)));

        server.verify();
    }

    @Test
    void updatesStatus() {
        server.expect(requestTo("http://dave/zaehlung/updateStatus"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"zaehlungId\": \"z\", \"status\": \"ACCOMPLISHED\"}"))
                .andRespond(withSuccess("{\"id\": \"z\"}", MediaType.APPLICATION_JSON));

        service.updateStatus("z", "ACCOMPLISHED");

        server.verify();
    }
}
