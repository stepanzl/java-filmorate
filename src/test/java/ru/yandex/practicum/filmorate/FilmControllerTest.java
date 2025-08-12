package ru.yandex.practicum.filmorate;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.filmorate.controller.FilmController;
import ru.yandex.practicum.filmorate.exception.ValidationException;
import ru.yandex.practicum.filmorate.model.Film;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FilmControllerTest {

    private FilmController filmController;
    private Validator validator;

    @BeforeEach
    void setUp() {
        filmController = new FilmController();
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void shouldFailWhenNameIsBlank() {
        Film film = getValidFilm();
        film.setName(" ");

        assertFalse(validator.validate(film).isEmpty());
    }

    @Test
    void shouldFailWhenDescriptionTooLong() {
        Film film = getValidFilm();
        film.setDescription("a".repeat(201));

        assertFalse(validator.validate(film).isEmpty());
    }

    @Test
    void shouldFailWhenReleaseDateTooEarly() {
        Film film = getValidFilm();
        film.setReleaseDate(LocalDate.of(1800, 1, 1));

        assertTrue(validator.validate(film).isEmpty());
        assertThrows(ValidationException.class, () -> filmController.createFilm(film));
    }

    @Test
    void shouldFailWhenDurationNegative() {
        Film film = getValidFilm();
        film.setDuration(-10);

        assertFalse(validator.validate(film).isEmpty());
    }

    @Test
    void shouldCreateFilmWhenValid() {
        Film film = getValidFilm();

        assertTrue(validator.validate(film).isEmpty());
        Film created = filmController.createFilm(film);
        assertNotNull(created.getId());
    }

    private Film getValidFilm() {
        Film film = new Film();
        film.setName("Valid");
        film.setDescription("Good film");
        film.setReleaseDate(LocalDate.of(2000, 1, 1));
        film.setDuration(100);
        return film;
    }
}


