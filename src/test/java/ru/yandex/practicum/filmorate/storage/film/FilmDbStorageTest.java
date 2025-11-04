package ru.yandex.practicum.filmorate.storage.film;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.Film;
import ru.yandex.practicum.filmorate.model.Genre;
import ru.yandex.practicum.filmorate.model.MpaRating;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.user.UserDbStorage;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@AutoConfigureTestDatabase
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Import({FilmDbStorage.class, UserDbStorage.class})
class FilmDbStorageTest {

    private final FilmDbStorage filmStorage;
    private final UserDbStorage userStorage;

    @Test
    void shouldCreateFilmWithGenresAndLikes() {
        User user = userStorage.create(buildUser("film-create-user@example.com", "filmCreateUser"));
        Film film = buildFilm("Film to Create", 1);
        film.getGenres().add(new Genre(1, null));
        film.getLikes().add(user.getId());

        Film created = filmStorage.create(film);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getGenres())
                .extracting(Genre::getId)
                .containsExactly(1);
        assertThat(created.getLikes())
                .containsExactly(user.getId());
    }

    @Test
    void shouldUpdateFilmWithNewData() {
        User firstUser = userStorage.create(buildUser("film-update-user1@example.com", "filmUpdateUser1"));
        User secondUser = userStorage.create(buildUser("film-update-user2@example.com", "filmUpdateUser2"));
        Film film = filmStorage.create(buildFilm("Original Film", 1));
        film.setName("Updated Film");
        film.setDescription("Updated description");
        film.setReleaseDate(LocalDate.of(2005, 5, 5));
        film.setDuration(180);
        film.setMpa(new MpaRating(2, null));
        film.setGenres(Set.of(new Genre(2, null), new Genre(3, null)));
        film.setLikes(Set.of(firstUser.getId(), secondUser.getId()));

        Film updated = filmStorage.update(film);

        assertThat(updated.getName()).isEqualTo("Updated Film");
        assertThat(updated.getDescription()).isEqualTo("Updated description");
        assertThat(updated.getReleaseDate()).isEqualTo(LocalDate.of(2005, 5, 5));
        assertThat(updated.getDuration()).isEqualTo(180);
        assertThat(updated.getMpa().getId()).isEqualTo(2);
        assertThat(updated.getGenres())
                .extracting(Genre::getId)
                .containsExactlyInAnyOrder(2, 3);
        assertThat(updated.getLikes())
                .containsExactlyInAnyOrder(firstUser.getId(), secondUser.getId());
    }

    @Test
    void shouldFindAllFilms() {
        Film first = filmStorage.create(buildFilm("First Film", 1));
        Film second = filmStorage.create(buildFilm("Second Film", 2));

        Collection<Film> films = filmStorage.findAll();

        assertThat(films)
                .extracting(Film::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    void shouldFindFilmByIdWithAssociations() {
        User user = userStorage.create(buildUser("film-find-user@example.com", "filmFindUser"));
        Film film = buildFilm("Film With Associations", 3);
        film.setGenres(Set.of(new Genre(4, null), new Genre(5, null)));
        film.getLikes().add(user.getId());
        Film created = filmStorage.create(film);

        Film found = filmStorage.findById(created.getId());

        assertThat(found.getGenres())
                .extracting(Genre::getId)
                .containsExactlyInAnyOrder(4, 5);
        assertThat(found.getLikes()).containsExactly(user.getId());
    }

    @Test
    void shouldDeleteFilm() {
        Film film = filmStorage.create(buildFilm("Film To Delete", 1));

        filmStorage.delete(film.getId());

        assertThatThrownBy(() -> filmStorage.findById(film.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldAddAndRemoveLike() {
        User user = userStorage.create(buildUser("film-like-user@example.com", "filmLikeUser"));
        Film film = filmStorage.create(buildFilm("Film Like", 1));

        filmStorage.addLike(film.getId(), user.getId());
        Film withLike = filmStorage.findById(film.getId());
        assertThat(withLike.getLikes()).containsExactly(user.getId());

        filmStorage.removeLike(film.getId(), user.getId());
        Film withoutLike = filmStorage.findById(film.getId());
        assertThat(withoutLike.getLikes()).isEmpty();
    }

    @Test
    void shouldReturnMostPopularFilmsByLikes() {
        User userOne = userStorage.create(buildUser("popular-user1@example.com", "popularUser1"));
        User userTwo = userStorage.create(buildUser("popular-user2@example.com", "popularUser2"));
        User userThree = userStorage.create(buildUser("popular-user3@example.com", "popularUser3"));

        Film filmOne = filmStorage.create(buildFilm("Popular Film One", 1));
        Film filmTwo = filmStorage.create(buildFilm("Popular Film Two", 2));
        Film filmThree = filmStorage.create(buildFilm("Popular Film Three", 3));

        filmStorage.addLike(filmOne.getId(), userOne.getId());
        filmStorage.addLike(filmOne.getId(), userTwo.getId());
        filmStorage.addLike(filmTwo.getId(), userThree.getId());

        Collection<Film> popular = filmStorage.getMostPopular(2);

        assertThat(popular)
                .extracting(Film::getId)
                .containsExactly(filmOne.getId(), filmTwo.getId())
                .doesNotContain(filmThree.getId());
    }

    @Test
    void shouldReturnEmptyWhenCountNonPositive() {
        Collection<Film> popular = filmStorage.getMostPopular(0);

        assertThat(popular).isEmpty();
    }

    private Film buildFilm(String name, int mpaId) {
        Film film = new Film();
        film.setName(name);
        film.setDescription(name + " description");
        film.setReleaseDate(LocalDate.of(2000, 1, 1));
        film.setDuration(120);
        film.setMpa(new MpaRating(mpaId, null));
        return film;
    }

    private User buildUser(String email, String login) {
        User user = new User();
        user.setEmail(email);
        user.setLogin(login);
        user.setName("User " + login);
        user.setBirthday(LocalDate.of(1990, 1, 1));
        return user;
    }
}
