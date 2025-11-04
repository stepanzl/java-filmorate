package ru.yandex.practicum.filmorate.storage.genre;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.Genre;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

@Slf4j
@Repository
@Qualifier("genreDbStorage")
public class GenreDbStorage implements GenreStorage {
    private static final String SELECT_ALL = "SELECT genre_id, name FROM genres ORDER BY genre_id";
    private static final String SELECT_BY_ID = "SELECT genre_id, name FROM genres WHERE genre_id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<Genre> genreRowMapper = new GenreRowMapper();

    public GenreDbStorage(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Collection<Genre> findAll() {
        try {
            return jdbcTemplate.query(SELECT_ALL, genreRowMapper);
        } catch (DataAccessException e) {
            log.error("Failed to fetch genres", e);
            throw new RuntimeException("Failed to fetch genres", e);
        }
    }

    @Override
    public Genre findById(int id) {
        try {
            List<Genre> genres = jdbcTemplate.query(SELECT_BY_ID, genreRowMapper, id);
            if (genres.isEmpty()) {
                throw new NotFoundException("Genre with id " + id + " not found");
            }
            return genres.get(0);
        } catch (DataAccessException e) {
            log.error("Failed to fetch genre with id {}", id, e);
            throw new RuntimeException("Failed to fetch genre", e);
        }
    }

    private static class GenreRowMapper implements RowMapper<Genre> {
        @Override
        public Genre mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Genre(rs.getInt("genre_id"), rs.getString("name"));
        }
    }
}
