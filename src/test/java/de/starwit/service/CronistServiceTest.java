package de.starwit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import de.starwit.dto.CountingDto;

class CronistServiceTest {

    /** Shaped like the serialized CountingEntity, including fields the adapter doesn't read. */
    private static final String RESPONSE = """
            [
              {"id": 1, "name": "done", "startDateTime": "2026-10-05T10:00:00", "endDateTime": "2026-10-05T12:00:00",
               "state": "COMPLETED", "assignedSaeId": null,
               "observatoryJobs": [{"jobId": 11, "name": "cronist-counting-1-north"}],
               "observationArea": {"id": 7, "name": "Marienplatz", "imageId": null, "latitude": 48.1, "longitude": 11.5},
               "image": null,
               "polygon": [
                 {"id": 3, "name": "north", "open": true, "direction": "N",
                  "point": [{"id": 1, "xvalue": 0.1, "yvalue": 0.2}, {"id": 2, "xvalue": 0.3, "yvalue": 0.4}]},
                 {"id": 4, "name": "area", "open": false, "direction": null,
                  "point": [{"id": 5, "xvalue": 0.1, "yvalue": 0.2}, {"id": 6, "xvalue": 0.3, "yvalue": 0.4},
                            {"id": 7, "xvalue": 0.5, "yvalue": 0.6}]}
               ]},
              {"id": 2, "name": "running", "state": "PROCESSING", "observatoryJobs": [],
               "observationArea": {"id": 7, "name": "Marienplatz", "latitude": 48.1, "longitude": 11.5}}
            ]
            """;

    @Test
    void readsCompletedCountings() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://cronist/cronist");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://cronist/cronist/api/counting"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        List<CountingDto> countings = new CronistService(builder.build()).findCompletedCountings();

        assertThat(countings).hasSize(1);
        CountingDto counting = countings.getFirst();
        assertThat(counting.id()).isEqualTo(1L);
        assertThat(counting.start()).isEqualTo(Instant.parse("2026-10-05T10:00:00Z"));
        assertThat(counting.end()).isEqualTo(Instant.parse("2026-10-05T12:00:00Z"));
        assertThat(counting.observationArea().name()).isEqualTo("Marienplatz");
        assertThat(counting.observatoryJobs().getFirst().name()).isEqualTo("cronist-counting-1-north");
        assertThat(counting.polygon().get(0).isCountingLine()).isTrue();
        assertThat(counting.polygon().get(1).isCountingLine()).isFalse();
    }
}
