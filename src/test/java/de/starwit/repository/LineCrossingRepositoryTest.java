package de.starwit.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Runs the query on the schema Observatory's migrations create. */
@Testcontainers(disabledWithoutDocker = true)
class LineCrossingRepositoryTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private static JdbcTemplate jdbc;
    private static LineCrossingRepository repository;

    @BeforeAll
    static void createSchema() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        repository = new LineCrossingRepository(new NamedParameterJdbcTemplate(dataSource));
        jdbc.execute("""
                create table linecrossing (object_id varchar(255), direction varchar(255),
                    crossing_time timestamp with time zone, object_class_id bigint, metadata_id bigint)
                """);
        jdbc.execute("""
                create table metadata (id bigint not null, name varchar(255) not null, classification varchar(255),
                    geo_referenced boolean, center_longitude decimal(22,19), center_latitude decimal(22,19),
                    observation_area_id bigint, direction varchar(255))
                """);
    }

    @BeforeEach
    void clear() {
        jdbc.execute("truncate linecrossing");
        jdbc.execute("truncate metadata");
        // same observation area for two countings
        metadata(1, "cronist-counting-1-north", "N");
        metadata(2, "cronist-counting-1-east", "E");
        metadata(3, "cronist-counting-2-north", "N");
        metadata(4, "cronist-counting-2-east", "E");
    }

    private void metadata(long id, String name, String direction) {
        jdbc.update("insert into metadata(id, name, observation_area_id, direction) values (?, ?, 7, ?)", id, name,
                direction);
    }

    private void crossing(String objectId, long metadataId, String direction, String time, long classId) {
        jdbc.update("""
                insert into linecrossing(object_id, direction, crossing_time, object_class_id, metadata_id)
                values (?, ?, ?, ?, ?)
                """, objectId, direction, Timestamp.from(Instant.parse(time)), classId, metadataId);
    }

    private static final List<String> JOBS = List.of("cronist-counting-1-north", "cronist-counting-1-east");

    @Test
    void countsObjectInIntervalItLeaves() {
        crossing("a", 1, "in", "2026-10-05T10:14:50Z", 2);
        crossing("a", 2, "out", "2026-10-05T10:15:10Z", 2);

        assertThat(repository.countCrossings(JOBS)).containsExactly(
                new CrossingCountProjection(Instant.parse("2026-10-05T10:15:00Z"), 2, "N", "E", 1));
    }

    @Test
    void groupsByIntervalClassAndRoute() {
        crossing("a", 1, "in", "2026-10-05T10:01:00Z", 2);
        crossing("a", 2, "out", "2026-10-05T10:02:00Z", 2);
        crossing("b", 1, "in", "2026-10-05T10:03:00Z", 2);
        crossing("b", 2, "out", "2026-10-05T10:04:00Z", 2);
        crossing("c", 1, "in", "2026-10-05T10:03:00Z", 7);
        crossing("c", 2, "out", "2026-10-05T10:04:00Z", 7);
        crossing("d", 2, "in", "2026-10-05T10:03:00Z", 2);
        crossing("d", 1, "out", "2026-10-05T10:04:00Z", 2);
        crossing("e", 1, "in", "2026-10-05T10:16:00Z", 2);
        crossing("e", 2, "out", "2026-10-05T10:17:00Z", 2);

        assertThat(repository.countCrossings(JOBS)).containsExactlyInAnyOrder(
                new CrossingCountProjection(Instant.parse("2026-10-05T10:00:00Z"), 2, "N", "E", 2),
                new CrossingCountProjection(Instant.parse("2026-10-05T10:00:00Z"), 7, "N", "E", 1),
                new CrossingCountProjection(Instant.parse("2026-10-05T10:00:00Z"), 2, "E", "N", 1),
                new CrossingCountProjection(Instant.parse("2026-10-05T10:15:00Z"), 2, "N", "E", 1));
    }

    @Test
    void ignoresObjectsNotPassingThrough() {
        // same line in and out
        crossing("a", 1, "in", "2026-10-05T10:01:00Z", 2);
        crossing("a", 1, "out", "2026-10-05T10:02:00Z", 2);
        // only entered
        crossing("b", 1, "in", "2026-10-05T10:01:00Z", 2);
        // first crossing was leaving
        crossing("c", 1, "out", "2026-10-05T10:01:00Z", 2);
        crossing("c", 2, "out", "2026-10-05T10:02:00Z", 2);

        assertThat(repository.countCrossings(JOBS)).isEmpty();
    }

    @Test
    void ignoresOtherCountingsOfSameArea() {
        crossing("a", 3, "in", "2026-10-05T10:01:00Z", 2);
        crossing("a", 4, "out", "2026-10-05T10:02:00Z", 2);

        assertThat(repository.countCrossings(JOBS)).isEmpty();
    }
}
