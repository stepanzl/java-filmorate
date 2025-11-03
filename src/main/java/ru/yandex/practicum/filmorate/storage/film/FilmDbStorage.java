package ru.yandex.practicum.filmorate.storage.film;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@Qualifier("filmDbStorage")
public class FilmDbStorage implements FilmStorage {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<Film> filmRowMapper = this::mapRowToFilm;

    public FilmDbStorage(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public Film create(Film film) {
        SimpleJdbcInsert insert = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("films")
                .usingGeneratedKeyColumns("film_id");

        Map<String, Object> values = new HashMap<>();
        values.put("name", film.getName());
        values.put("description", film.getDescription());
        values.put("release_date", toSqlDate(film.getReleaseDate()));
        values.put("duration", film.getDuration());
        values.put("mpa_rating_id", film.getMpa() != null ? film.getMpa().getId() : null);

        Number id = insert.executeAndReturnKey(values);
        film.setId(Objects.requireNonNull(id).longValue());

        updateGenres(film);
        updateLikes(film);
        return findById(film.getId());
    }

    @Override
    @Transactional
    public Film update(Film film) {
        int updated = jdbcTemplate.update(
                "UPDATE films SET name = ?, description = ?, release_date = ?, duration = ?, mpa_rating_id = ? WHERE film_id = ?",
                film.getName(),
                film.getDescription(),
                toSqlDate(film.getReleaseDate()),
                film.getDuration(),
                film.getMpa() != null ? film.getMpa().getId() : null,
                film.getId()
        );

        if (updated == 0) {
            throw new NotFoundException("Film not found");
        }

        updateGenres(film);
        updateLikes(film);
        return findById(film.getId());
    }

    @Override
    public Collection<Film> findAll() {
        List<Film> films = jdbcTemplate.query(
                "SELECT f.film_id, f.name, f.description, f.release_date, f.duration, f.mpa_rating_id, m.name AS mpa_name " +
                        "FROM films f JOIN mpa_ratings m ON f.mpa_rating_id = m.mpa_rating_id",
                filmRowMapper
        );

        populateGenresAndLikes(films);
        return films;
    }

    @Override
    public Film findById(Long id) {
        List<Film> films = jdbcTemplate.query(
                "SELECT f.film_id, f.name, f.description, f.release_date, f.duration, f.mpa_rating_id, m.name AS mpa_name " +
                        "FROM films f JOIN mpa_ratings m ON f.mpa_rating_id = m.mpa_rating_id WHERE f.film_id = ?",
                filmRowMapper,
                id
        );

        if (films.isEmpty()) {
            throw new NotFoundException("Film not found");
        }

        Film film = films.get(0);
        populateGenresAndLikes(List.of(film));
        return film;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM films WHERE film_id = ?", id);
    }

    private Film mapRowToFilm(ResultSet rs, int rowNum) throws SQLException {
        Film film = new Film();
        film.setId(rs.getLong("film_id"));
        film.setName(rs.getString("name"));
        film.setDescription(rs.getString("description"));
        Date releaseDate = rs.getDate("release_date");
        if (releaseDate != null) {
            film.setReleaseDate(releaseDate.toLocalDate());
        }
        film.setDuration(rs.getInt("duration"));
        int mpaId = rs.getInt("mpa_rating_id");
        String mpaName = rs.getString("mpa_name");
        film.setMpa(new MpaRating(mpaId, mpaName));
        return film;
    }

    private void populateGenresAndLikes(Collection<Film> films) {
        if (films.isEmpty()) {
            return;
        }

        Map<Long, Film> filmsById = films.stream()
                .collect(Collectors.toMap(Film::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        films.forEach(film -> {
            film.getGenres().clear();
            film.getLikes().clear();
        });

        List<Long> ids = new ArrayList<>(filmsById.keySet());
        String placeholders = ids.stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));

        jdbcTemplate.query(
                "SELECT fg.film_id, g.genre_id, g.name FROM film_genres fg " +
                        "JOIN genres g ON fg.genre_id = g.genre_id WHERE fg.film_id IN (" + placeholders + ") " +
                        "ORDER BY fg.film_id, g.genre_id",
                ids.toArray(),
                rs -> {
                    long filmId = rs.getLong("film_id");
                    Film film = filmsById.get(filmId);
                    if (film != null) {
                        film.getGenres().add(new Genre(rs.getInt("genre_id"), rs.getString("name")));
                    }
                }
        );

        jdbcTemplate.query(
                "SELECT film_id, user_id FROM film_likes WHERE film_id IN (" + placeholders + ")",
                ids.toArray(),
                rs -> {
                    long filmId = rs.getLong("film_id");
                    Film film = filmsById.get(filmId);
                    if (film != null) {
                        film.getLikes().add(rs.getLong("user_id"));
                    }
                }
        );
    }

    private void updateGenres(Film film) {
        jdbcTemplate.update("DELETE FROM film_genres WHERE film_id = ?", film.getId());

        Set<Genre> genres = film.getGenres();
        if (genres == null || genres.isEmpty()) {
            return;
        }

        List<Object[]> batchArgs = genres.stream()
                .map(genre -> new Object[]{film.getId(), genre.getId()})
                .toList();

        jdbcTemplate.batchUpdate(
                "INSERT INTO film_genres (film_id, genre_id) VALUES (?, ?)",
                batchArgs
        );
    }

    private void updateLikes(Film film) {
        jdbcTemplate.update("DELETE FROM film_likes WHERE film_id = ?", film.getId());

        Set<Long> likes = film.getLikes();
        if (likes == null || likes.isEmpty()) {
            return;
        }

        List<Object[]> batchArgs = likes.stream()
                .map(userId -> new Object[]{film.getId(), userId})
                .toList();

        jdbcTemplate.batchUpdate(
                "INSERT INTO film_likes (film_id, user_id) VALUES (?, ?)",
                batchArgs
        );
    }

    private Date toSqlDate(LocalDate date) {
        return date == null ? null : Date.valueOf(date);
    }
}
