package de.starwit.repository;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads the line crossings from the analytics database of Observatory. */
@Repository
public class LineCrossingRepository {

    /**
     * Same logic as in dave-adapter: an object is counted when it leaves through another line than the one it first
     * entered through. Different from dave-adapter, the crossings are found by the job names and not the observation
     * area, as an area has several countings in Cronist, and all quarter hours are read at once as a counting is
     * transferred after it is completed.
     */
    private static final String QUERY = """
            select
                date_bin('15 minutes', r.crossing_time, timestamptz '1970-01-01 00:00:00+00') as interval_start,
                r.object_class_id,
                r.direction_from,
                r.direction_to,
                count(*) as count
            from (
                select
                    l.crossing_time,
                    l.object_class_id,
                    first_value(m.direction) over w as direction_from,
                    m.direction as direction_to,
                    first_value(l.direction) over w as crossing_from,
                    l.direction as crossing_to
                from linecrossing l
                    join metadata m on l.metadata_id = m.id
                where m.name in (:jobNames)
                window w as (partition by l.object_id order by l.crossing_time asc)
            ) r
            where r.direction_from <> r.direction_to
                and r.crossing_from = 'in' and r.crossing_to = 'out'
            group by 1, 2, 3, 4
            order by 1
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public LineCrossingRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @param jobNames names of the Observatory jobs of a counting, one per counting line */
    public List<CrossingCountProjection> countCrossings(List<String> jobNames) {
        return jdbcTemplate.query(QUERY, new MapSqlParameterSource("jobNames", jobNames),
                (rs, rowNum) -> new CrossingCountProjection(rs.getTimestamp("interval_start").toInstant(),
                        rs.getLong("object_class_id"), rs.getString("direction_from"), rs.getString("direction_to"),
                        rs.getLong("count")));
    }
}
