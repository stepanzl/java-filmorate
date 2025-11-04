package ru.yandex.practicum.filmorate.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.filmorate.exception.ValidationException;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.MpaRating;
import ru.yandex.practicum.filmorate.storage.film.FilmStorage;
import ru.yandex.practicum.filmorate.storage.genre.GenreStorage;
import ru.yandex.practicum.filmorate.storage.mpa.MpaStorage;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FilmService {
    private final FilmStorage filmStorage;
    private final UserStorage userStorage;
    private final GenreStorage genreStorage;
    private final MpaStorage mpaStorage;

    @Autowired
    public FilmService(@Qualifier("filmDbStorage") FilmStorage filmStorage,
                       @Qualifier("userDbStorage") UserStorage userStorage,
                       @Qualifier("genreDbStorage") GenreStorage genreStorage,
                       @Qualifier("mpaDbStorage") MpaStorage mpaStorage) {
        this.filmStorage = filmStorage;
        this.userStorage = userStorage;
        this.genreStorage = genreStorage;
        this.mpaStorage = mpaStorage;
    }

    // временный адаптер для тестов InMemory имплементации
    @Deprecated
    public FilmService(FilmStorage filmStorage, UserStorage userStorage) {
        this(filmStorage, userStorage, new NoopGenreStorage(), new NoopMpaStorage());
    }

    static final class NoopGenreStorage implements GenreStorage {
        @Override
        public List<Genre> findAll() {
            return List.of(new Genre(1, "TestGenre")); // Пример
        }

        @Override
        public Genre findById(int id) {
            if (id == 1) {
                return new Genre(1, "TestGenre");
            }
            return new Genre(id, "TestGenre" + id);
        }
    }

    static final class NoopMpaStorage implements MpaStorage {
        @Override
        public List<MpaRating> findAll() {
            return List.of(new MpaRating(1, "G"));
        }

        @Override
        public MpaRating findById(int id) {
            if (id == 1) {
                return new MpaRating(1, "G");
            }
            return new MpaRating(id, "TestMPA" + id);
        }
    }

    public Film create(Film film) {
        enrichFilmMetadata(film);
        return filmStorage.create(film);
    }

    public Film update(Film film) {
        enrichFilmMetadata(film);
        return filmStorage.update(film);
    }

    public List<Film> findAll() {
        return filmStorage.findAll().stream().collect(Collectors.toList());
    }

    public Film findById(Long id) {
        return filmStorage.findById(id);
    }

    public void addLike(Long filmId, Long userId) {
        findById(filmId);
        userStorage.findById(userId);
        filmStorage.addLike(filmId, userId);
    }

    public void removeLike(Long filmId, Long userId) {
        findById(filmId);
        userStorage.findById(userId);
        filmStorage.removeLike(filmId, userId);
    }

    public List<Film> getPopular(int count) {
        return filmStorage.getMostPopular(count).stream()
                .collect(Collectors.toList());
    }

    private void enrichFilmMetadata(Film film) {
        film.setMpa(resolveMpa(film.getMpa()));
        film.setGenres(resolveGenres(film.getGenres()));
    }

    private MpaRating resolveMpa(MpaRating mpa) {
        if (mpa == null || mpa.getId() == 0) {
            throw new ValidationException("Film must have a valid MPA rating");
        }
        return mpaStorage.findById(mpa.getId());
    }

    private LinkedHashSet<Genre> resolveGenres(Set<Genre> genres) {
        if (genres == null || genres.isEmpty()) {
            return new LinkedHashSet<>();
        }

        return genres.stream()
                .filter(Objects::nonNull)
                .map(Genre::getId)
                .distinct()
                .sorted()
                .map(genreStorage::findById)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
