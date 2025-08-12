package ru.yandex.practicum.filmorate;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.filmorate.model.Film;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FilmTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void validFilm_shouldHaveNoViolations() {
        Film film = makeValidFilm();
        Set<ConstraintViolation<Film>> violations = validator.validate(film);
        assertTrue(violations.isEmpty(), "Ожидалось отсутствие ошибок валидации");
    }

    @Test
    void emptyName_shouldFailValidation() {
        Film film = makeValidFilm();
        film.setName("");
        Set<ConstraintViolation<Film>> violations = validator.validate(film);
        assertFalse(violations.isEmpty(), "Ожидалась ошибка для пустого названия фильма");
    }

    @Test
    void tooLongDescription_shouldFailValidation() {
        Film film = makeValidFilm();
        film.setDescription("A".repeat(201));
        Set<ConstraintViolation<Film>> violations = validator.validate(film);
        assertFalse(violations.isEmpty(), "Ожидалась ошибка для слишком длинного описания");
    }

    @Test
    void futureReleaseDate_shouldFailValidation() {
        Film film = makeValidFilm();
        film.setReleaseDate(LocalDate.now().plusDays(1));
        Set<ConstraintViolation<Film>> violations = validator.validate(film);
        assertFalse(violations.isEmpty(), "Ожидалась ошибка для даты релиза в будущем");
    }

    @Test
    void zeroDuration_shouldFailValidation() {
        Film film = makeValidFilm();
        film.setDuration(0);
        Set<ConstraintViolation<Film>> violations = validator.validate(film);
        assertFalse(violations.isEmpty(), "Ожидалась ошибка для нулевой длительности");
    }

    @Test
    void negativeDuration_shouldFailValidation() {
        Film film = makeValidFilm();
        film.setDuration(-100);
        Set<ConstraintViolation<Film>> violations = validator.validate(film);
        assertFalse(violations.isEmpty(), "Ожидалась ошибка для отрицательной длительности");
    }

    private Film makeValidFilm() {
        Film film = new Film();
        film.setName("Test Film");
        film.setDescription("Short description");
        film.setReleaseDate(LocalDate.of(2000, 1, 1));
        film.setDuration(120);
        return film;
    }
}
