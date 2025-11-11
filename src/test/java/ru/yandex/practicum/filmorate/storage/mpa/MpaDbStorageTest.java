package ru.yandex.practicum.filmorate.storage.mpa;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.MpaRating;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@AutoConfigureTestDatabase
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Import(MpaDbStorage.class)
class MpaDbStorageTest {

    private final MpaDbStorage mpaStorage;

    @Test
    void shouldFindAllRatings() {
        Collection<MpaRating> ratings = mpaStorage.findAll();

        assertThat(ratings)
                .hasSize(5)
                .extracting(MpaRating::getName)
                .contains("G", "PG", "PG-13", "R", "NC-17");
    }

    @Test
    void shouldFindRatingById() {
        MpaRating rating = mpaStorage.findById(1);

        assertThat(rating.getName()).isEqualTo("G");
    }

    @Test
    void shouldThrowWhenRatingNotFound() {
        assertThatThrownBy(() -> mpaStorage.findById(999))
                .isInstanceOf(NotFoundException.class);
    }
}
