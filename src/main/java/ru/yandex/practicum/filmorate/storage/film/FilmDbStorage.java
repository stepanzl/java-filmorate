package ru.yandex.practicum.filmorate.storage.film;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.MpaRating;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Repository
@Qualifier("filmDbStorage")
public class FilmDbStorage implements FilmStorage {
    private static final String BASE_SELECT = "SELECT f.film_id, f.name, f.description, f.release_date, f.duration, " +
            "f.mpa_rating_id, m.name AS mpa_name FROM films f JOIN mpa_ratings m ON f.mpa_rating_id = m.mpa_rating_id";
    private static final String SELECT_ALL_FILMS = BASE_SELECT;
    private static final String SELECT_FILM_BY_ID = BASE_SELECT + " WHERE f.film_id = ?";
    private static final String UPDATE_FILM = "UPDATE films SET name = ?, description = ?, release_date = ?, duration = ?, " +
            "mpa_rating_id = ? WHERE film_id = ?";
    private static final String DELETE_FILM = "DELETE FROM films WHERE film_id = ?";
    private static final String SELECT_GENRES_BY_FILM_IDS = "SELECT fg.film_id, g.genre_id, g.name FROM film_genres fg " +
            "JOIN genres g ON fg.genre_id = g.genre_id WHERE fg.film_id IN (%s) ORDER BY g.genre_id";
    private static final String DELETE_GENRES_BY_FILM_ID = "DELETE FROM film_genres WHERE film_id = ?";
    private static final String INSERT_GENRE = "INSERT INTO film_genres (film_id, genre_id) VALUES (?, ?)";
    private static final String SELECT_LIKES_BY_FILM_IDS = "SELECT film_id, user_id FROM film_likes WHERE film_id IN (%s)";
    private static final String DELETE_LIKES_BY_FILM_ID = "DELETE FROM film_likes WHERE film_id = ?";
    private static final String MERGE_LIKE = "MERGE INTO film_likes (film_id, user_id) KEY (film_id, user_id) VALUES (?, ?)";
    private static final String DELETE_LIKE = "DELETE FROM film_likes WHERE film_id = ? AND user_id = ?";
    private static final String SELECT_POPULAR_FILMS = "SELECT f.film_id, f.name, f.description, f.release_date, f.duration, " +
            "f.mpa_rating_id, m.name AS mpa_name FROM films f " +
            "JOIN mpa_ratings m ON f.mpa_rating_id = m.mpa_rating_id " +
            "LEFT JOIN film_likes fl ON f.film_id = fl.film_id " +
            "GROUP BY f.film_id, f.name, f.description, f.release_date, f.duration, f.mpa_rating_id, m.name " +
            "ORDER BY COUNT(fl.user_id) DESC, f.film_id " +
            "LIMIT ?";

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert filmInsert;
    private final RowMapper<Film> filmRowMapper = new FilmRowMapper();

    public FilmDbStorage(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.filmInsert = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("films")
                .usingGeneratedKeyColumns("film_id");
    }

    @Override
    public Film create(Film film) {
        try {
            Objects.requireNonNull(film.getMpa(), "Film MPA rating must not be null");
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("name", film.getName());
            parameters.put("description", film.getDescription());
            parameters.put("release_date", toDate(film.getReleaseDate()));
            parameters.put("duration", film.getDuration());
            parameters.put("mpa_rating_id", film.getMpa().getId());

            Number generatedId = filmInsert.executeAndReturnKey(parameters);
            film.setId(generatedId.longValue());

            updateGenres(film);
            updateLikes(film);
            return findById(film.getId());
        } catch (DataAccessException e) {
            log.error("Failed to create film {}", film, e);
            throw new RuntimeException("Failed to create film", e);
        }
    }

    @Override
    public Film update(Film film) {
        Objects.requireNonNull(film.getId(), "Film id must not be null for update");
        try {
            Objects.requireNonNull(film.getMpa(), "Film MPA rating must not be null");
            int updated = jdbcTemplate.update(UPDATE_FILM,
                    film.getName(),
                    film.getDescription(),
                    toDate(film.getReleaseDate()),
                    film.getDuration(),
                    film.getMpa().getId(),
                    film.getId());
            if (updated == 0) {
                throw new NotFoundException("Film with id " + film.getId() + " not found");
            }
            updateGenres(film);
            updateLikes(film);
            return findById(film.getId());
        } catch (DataAccessException e) {
            log.error("Failed to update film {}", film, e);
            throw new RuntimeException("Failed to update film", e);
        }
    }

    @Override
    public Collection<Film> findAll() {
        try {
            List<Film> films = jdbcTemplate.query(SELECT_ALL_FILMS, filmRowMapper);
            loadGenres(films);
            loadLikes(films);
            return films;
        } catch (DataAccessException e) {
            log.error("Failed to fetch films", e);
            throw new RuntimeException("Failed to fetch films", e);
        }
    }

    @Override
    public Film findById(Long id) {
        try {
            List<Film> films = jdbcTemplate.query(SELECT_FILM_BY_ID, filmRowMapper, id);
            if (films.isEmpty()) {
                throw new NotFoundException("Film with id " + id + " not found");
            }
            Film film = films.get(0);
            loadGenres(Collections.singletonList(film));
            loadLikes(Collections.singletonList(film));
            return film;
        } catch (DataAccessException e) {
            log.error("Failed to fetch film with id {}", id, e);
            throw new RuntimeException("Failed to fetch film", e);
        }
    }

    @Override
    public void delete(Long id) {
        try {
            int updated = jdbcTemplate.update(DELETE_FILM, id);
            if (updated == 0) {
                throw new NotFoundException("Film with id " + id + " not found");
            }
        } catch (DataAccessException e) {
            log.error("Failed to delete film with id {}", id, e);
            throw new RuntimeException("Failed to delete film", e);
        }
    }

    @Override
    public void addLike(Long filmId, Long userId) {
        try {
            jdbcTemplate.update(MERGE_LIKE, filmId, userId);
        } catch (DataAccessException e) {
            log.error("Failed to add like for film {} by user {}", filmId, userId, e);
            throw new RuntimeException("Failed to add like", e);
        }
    }

    @Override
    public void removeLike(Long filmId, Long userId) {
        try {
            int updated = jdbcTemplate.update(DELETE_LIKE, filmId, userId);
            if (updated == 0) {
                throw new NotFoundException("Like from user " + userId + " not found for film " + filmId);
            }
        } catch (DataAccessException e) {
            log.error("Failed to remove like for film {} by user {}", filmId, userId, e);
            throw new RuntimeException("Failed to remove like", e);
        }
    }

    @Override
    public Collection<Film> getMostPopular(int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }
        try {
            List<Film> films = jdbcTemplate.query(SELECT_POPULAR_FILMS, filmRowMapper, count);
            loadGenres(films);
            loadLikes(films);
            return films;
        } catch (DataAccessException e) {
            log.error("Failed to fetch popular films", e);
            throw new RuntimeException("Failed to fetch popular films", e);
        }
    }

    private void updateGenres(Film film) {
        if (film.getId() == null) {
            return;
        }
        try {
            jdbcTemplate.update(DELETE_GENRES_BY_FILM_ID, film.getId());
            Set<Genre> genres = film.getGenres();
            if (genres == null || genres.isEmpty()) {
                return;
            }
            List<Object[]> batchArgs = new ArrayList<>();
            for (Genre genre : genres) {
                batchArgs.add(new Object[]{film.getId(), genre.getId()});
            }
            jdbcTemplate.batchUpdate(INSERT_GENRE, batchArgs);
        } catch (DataAccessException e) {
            log.error("Failed to update genres for film {}", film.getId(), e);
            throw new RuntimeException("Failed to update film genres", e);
        }
    }

    private void updateLikes(Film film) {
        if (film.getId() == null) {
            return;
        }
        try {
            jdbcTemplate.update(DELETE_LIKES_BY_FILM_ID, film.getId());
            Set<Long> likes = film.getLikes();
            if (likes == null || likes.isEmpty()) {
                return;
            }
            List<Object[]> batchArgs = likes.stream()
                    .map(userId -> new Object[]{film.getId(), userId})
                    .collect(Collectors.toList());
            jdbcTemplate.batchUpdate(MERGE_LIKE, batchArgs);
        } catch (DataAccessException e) {
            log.error("Failed to update likes for film {}", film.getId(), e);
            throw new RuntimeException("Failed to update film likes", e);
        }
    }

    private void loadGenres(Collection<Film> films) {
        if (films == null || films.isEmpty()) {
            return;
        }
        Map<Long, Film> filmById = films.stream()
                .collect(Collectors.toMap(Film::getId, film -> film));
        if (filmById.isEmpty()) {
            return;
        }
        String placeholders = filmById.keySet().stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));
        String query = String.format(SELECT_GENRES_BY_FILM_IDS, placeholders);
        Object[] args = filmById.keySet().toArray();
        try {
            jdbcTemplate.query(query, args, rs -> {
                long filmId = rs.getLong("film_id");
                Genre genre = new Genre(rs.getInt("genre_id"), rs.getString("name"));
                Film film = filmById.get(filmId);
                if (film != null) {
                    if (film.getGenres() == null) {
                        film.setGenres(new LinkedHashSet<>());
                    }
                    film.getGenres().add(genre);
                }
            });
        } catch (DataAccessException e) {
            log.error("Failed to load genres for films {}", filmById.keySet(), e);
            throw new RuntimeException("Failed to load film genres", e);
        }
    }

    private void loadLikes(Collection<Film> films) {
        if (films == null || films.isEmpty()) {
            return;
        }
        Map<Long, Film> filmById = films.stream()
                .collect(Collectors.toMap(Film::getId, film -> film));
        if (filmById.isEmpty()) {
            return;
        }
        String placeholders = filmById.keySet().stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));
        String query = String.format(SELECT_LIKES_BY_FILM_IDS, placeholders);
        Object[] args = filmById.keySet().toArray();
        try {
            jdbcTemplate.query(query, args, rs -> {
                long filmId = rs.getLong("film_id");
                long userId = rs.getLong("user_id");
                Film film = filmById.get(filmId);
                if (film != null) {
                    film.getLikes().add(userId);
                }
            });
        } catch (DataAccessException e) {
            log.error("Failed to load likes for films {}", filmById.keySet(), e);
            throw new RuntimeException("Failed to load film likes", e);
        }
    }

    private Date toDate(LocalDate date) {
        return date == null ? null : Date.valueOf(date);
    }

    private static class FilmRowMapper implements RowMapper<Film> {
        @Override
        public Film mapRow(ResultSet rs, int rowNum) throws SQLException {
            Film film = new Film();
            film.setId(rs.getLong("film_id"));
            film.setName(rs.getString("name"));
            film.setDescription(rs.getString("description"));
            Date releaseDate = rs.getDate("release_date");
            if (releaseDate != null) {
                film.setReleaseDate(releaseDate.toLocalDate());
            }
            film.setDuration(rs.getInt("duration"));
            film.setMpa(new MpaRating(rs.getInt("mpa_rating_id"), rs.getString("mpa_name")));
            return film;
        }
    }
}
