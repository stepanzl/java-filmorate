package ru.yandex.practicum.filmorate.storage.user;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.User;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@Qualifier("userDbStorage")
public class UserDbStorage implements UserStorage {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<User> userRowMapper = this::mapRowToUser;

    public UserDbStorage(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public User create(User user) {
        SimpleJdbcInsert insert = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("users")
                .usingGeneratedKeyColumns("user_id");

        Map<String, Object> values = new HashMap<>();
        values.put("email", user.getEmail());
        values.put("login", user.getLogin());
        values.put("name", user.getName());
        values.put("birthday", toSqlDate(user.getBirthday()));

        Number id = insert.executeAndReturnKey(values);
        user.setId(Objects.requireNonNull(id).longValue());

        updateFriendships(user);
        return findById(user.getId());
    }

    @Override
    @Transactional
    public User update(User user) {
        int updated = jdbcTemplate.update(
                "UPDATE users SET email = ?, login = ?, name = ?, birthday = ? WHERE user_id = ?",
                user.getEmail(),
                user.getLogin(),
                user.getName(),
                toSqlDate(user.getBirthday()),
                user.getId()
        );

        if (updated == 0) {
            throw new NotFoundException("User not found");
        }

        updateFriendships(user);
        return findById(user.getId());
    }

    @Override
    public Collection<User> findAll() {
        List<User> users = jdbcTemplate.query(
                "SELECT user_id, email, login, name, birthday FROM users",
                userRowMapper
        );

        populateFriendships(users);
        return users;
    }

    @Override
    public User findById(Long id) {
        List<User> users = jdbcTemplate.query(
                "SELECT user_id, email, login, name, birthday FROM users WHERE user_id = ?",
                userRowMapper,
                id
        );

        if (users.isEmpty()) {
            throw new NotFoundException("User not found");
        }

        User user = users.get(0);
        populateFriendships(List.of(user));
        return user;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", id);
    }

    private User mapRowToUser(ResultSet rs, int rowNum) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("user_id"));
        user.setEmail(rs.getString("email"));
        user.setLogin(rs.getString("login"));
        user.setName(rs.getString("name"));
        Date birthday = rs.getDate("birthday");
        if (birthday != null) {
            user.setBirthday(birthday.toLocalDate());
        }
        return user;
    }

    private void populateFriendships(Collection<User> users) {
        if (users.isEmpty()) {
            return;
        }

        Map<Long, User> usersById = users.stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        users.forEach(user -> user.getFriends().clear());

        List<Long> ids = usersById.keySet().stream().toList();

        String placeholders = ids.stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));

        jdbcTemplate.query(
                "SELECT user_id, friend_id FROM friendships WHERE user_id IN (" + placeholders + ")",
                ids.toArray(),
                rs -> {
                    long userId = rs.getLong("user_id");
                    long friendId = rs.getLong("friend_id");
                    User user = usersById.get(userId);
                    if (user != null) {
                        user.getFriends().add(friendId);
                    }
                }
        );
    }

    private void updateFriendships(User user) {
        jdbcTemplate.update("DELETE FROM friendships WHERE user_id = ?", user.getId());

        Set<Long> friends = user.getFriends();
        if (friends == null || friends.isEmpty()) {
            return;
        }

        List<Object[]> batchArgs = friends.stream()
                .map(friendId -> new Object[]{user.getId(), friendId})
                .toList();

        jdbcTemplate.batchUpdate(
                "INSERT INTO friendships (user_id, friend_id) VALUES (?, ?)",
                batchArgs
        );
    }

    private Date toSqlDate(LocalDate date) {
        return date == null ? null : Date.valueOf(date);
    }
}
