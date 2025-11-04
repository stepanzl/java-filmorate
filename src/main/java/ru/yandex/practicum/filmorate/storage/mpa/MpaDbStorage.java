package ru.yandex.practicum.filmorate.storage.mpa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.MpaRating;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

@Slf4j
@Repository
@Qualifier("mpaDbStorage")
public class MpaDbStorage implements MpaStorage {
    private static final String SELECT_ALL = "SELECT mpa_rating_id, name FROM mpa_ratings ORDER BY mpa_rating_id";
    private static final String SELECT_BY_ID = "SELECT mpa_rating_id, name FROM mpa_ratings WHERE mpa_rating_id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<MpaRating> mpaRowMapper = new MpaRowMapper();

    public MpaDbStorage(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Collection<MpaRating> findAll() {
        try {
            return jdbcTemplate.query(SELECT_ALL, mpaRowMapper);
        } catch (DataAccessException e) {
            log.error("Failed to fetch MPA ratings", e);
            throw new RuntimeException("Failed to fetch MPA ratings", e);
        }
    }

    @Override
    public MpaRating findById(int id) {
        try {
            List<MpaRating> ratings = jdbcTemplate.query(SELECT_BY_ID, mpaRowMapper, id);
            if (ratings.isEmpty()) {
                throw new NotFoundException("MPA rating with id " + id + " not found");
            }
            return ratings.get(0);
        } catch (DataAccessException e) {
            log.error("Failed to fetch MPA rating with id {}", id, e);
            throw new RuntimeException("Failed to fetch MPA rating", e);
        }
    }

    private static class MpaRowMapper implements RowMapper<MpaRating> {
        @Override
        public MpaRating mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new MpaRating(rs.getInt("mpa_rating_id"), rs.getString("name"));
        }
    }
}
