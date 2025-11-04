package ru.yandex.practicum.filmorate.storage.user;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.User;

import java.time.LocalDate;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@AutoConfigureTestDatabase
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Import(UserDbStorage.class)
class UserDbStorageTest {

    private final UserDbStorage userStorage;

    @Test
    void shouldCreateUserWithGeneratedId() {
        User user = buildUser("create-user@example.com", "createUser");

        User created = userStorage.create(user);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getEmail()).isEqualTo("create-user@example.com");
        assertThat(created.getLogin()).isEqualTo("createUser");
    }

    @Test
    void shouldUpdateUserAndReplaceFriends() {
        User user = userStorage.create(buildUser("update-user@example.com", "updateUser"));
        User friendOne = userStorage.create(buildUser("friend-one@example.com", "friendOne"));
        User friendTwo = userStorage.create(buildUser("friend-two@example.com", "friendTwo"));
        User friendThree = userStorage.create(buildUser("friend-three@example.com", "friendThree"));

        user.setName("Updated Name");
        user.setBirthday(LocalDate.of(1999, 12, 31));
        user.getFriends().add(friendOne.getId());
        user.getFriends().add(friendTwo.getId());

        User updated = userStorage.update(user);
        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(updated.getFriends())
                .containsExactlyInAnyOrder(friendOne.getId(), friendTwo.getId());

        updated.getFriends().remove(friendOne.getId());
        updated.getFriends().add(friendThree.getId());
        User updatedAgain = userStorage.update(updated);

        assertThat(updatedAgain.getFriends())
                .containsExactlyInAnyOrder(friendThree.getId(), friendTwo.getId());
    }

    @Test
    void shouldFindAllUsers() {
        User first = userStorage.create(buildUser("all-first@example.com", "allFirst"));
        User second = userStorage.create(buildUser("all-second@example.com", "allSecond"));

        Collection<User> users = userStorage.findAll();

        assertThat(users)
                .extracting(User::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    void shouldFindUserByIdWithFriends() {
        User owner = userStorage.create(buildUser("owner@example.com", "owner"));
        User friend = userStorage.create(buildUser("friend@example.com", "friend"));
        userStorage.addFriend(owner.getId(), friend.getId());

        User found = userStorage.findById(owner.getId());

        assertThat(found.getFriends()).containsExactly(friend.getId());
    }

    @Test
    void shouldDeleteUser() {
        User user = userStorage.create(buildUser("delete@example.com", "toDelete"));

        userStorage.delete(user.getId());

        assertThatThrownBy(() -> userStorage.findById(user.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldAddAndRemoveFriend() {
        User user = userStorage.create(buildUser("friend-owner@example.com", "friendOwner"));
        User friend = userStorage.create(buildUser("friend-target@example.com", "friendTarget"));

        userStorage.addFriend(user.getId(), friend.getId());
        Collection<User> friends = userStorage.getFriends(user.getId());
        assertThat(friends)
                .singleElement()
                .extracting(User::getId)
                .isEqualTo(friend.getId());

        userStorage.removeFriend(user.getId(), friend.getId());
        Collection<User> afterRemoval = userStorage.getFriends(user.getId());
        assertThat(afterRemoval).isEmpty();
    }

    @Test
    void shouldReturnCommonFriends() {
        User user = userStorage.create(buildUser("common-owner@example.com", "commonOwner"));
        User other = userStorage.create(buildUser("common-other@example.com", "commonOther"));
        User commonFriend = userStorage.create(buildUser("common-friend@example.com", "commonFriend"));
        User uniqueFriend = userStorage.create(buildUser("unique-friend@example.com", "uniqueFriend"));

        userStorage.addFriend(user.getId(), commonFriend.getId());
        userStorage.addFriend(other.getId(), commonFriend.getId());
        userStorage.addFriend(user.getId(), uniqueFriend.getId());

        Collection<User> common = userStorage.getCommonFriends(user.getId(), other.getId());

        assertThat(common)
                .extracting(User::getId)
                .containsExactly(commonFriend.getId());
    }

    private User buildUser(String email, String login) {
        User user = new User();
        user.setEmail(email);
        user.setLogin(login);
        user.setName("Name of " + login);
        user.setBirthday(LocalDate.of(1990, 1, 1));
        return user;
    }
}
