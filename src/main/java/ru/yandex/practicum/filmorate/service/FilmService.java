package ru.yandex.practicum.filmorate.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.storage.film.FilmStorage;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FilmService {
    private final FilmStorage filmStorage;
    private final UserStorage userStorage;

    @Autowired
    public FilmService(@Qualifier("inMemoryFilmStorage") FilmStorage filmStorage,
                       @Qualifier("inMemoryUserStorage") UserStorage userStorage) {
        this.filmStorage = filmStorage;
        this.userStorage = userStorage;
    }

    public Film create(Film film) {
        return filmStorage.create(film);
    }

    public Film update(Film film) {
        return filmStorage.update(film);
    }

    public List<Film> findAll() {
        return filmStorage.findAll().stream().collect(Collectors.toList());
    }

    public Film findById(Long id) {
        return filmStorage.findById(id);
    }

    public void addLike(Long filmId, Long userId) {
        Film film = findById(filmId);
        // verify user exists
        userStorage.findById(userId);
        film.getLikes().add(userId);
        filmStorage.update(film);
    }

    public void removeLike(Long filmId, Long userId) {
        Film film = findById(filmId);
        if (!film.getLikes().contains(userId)) {
            throw new NotFoundException("Like from user " + userId + " not found for film " + filmId);
        }
        film.getLikes().remove(userId);
        filmStorage.update(film);
    }

    public List<Film> getPopular(int count) {
        return filmStorage.findAll().stream()
                .sorted(Comparator.comparingInt((Film f) -> f.getLikes().size()).reversed())
                .limit(count)
                .collect(Collectors.toList());
    }
}
