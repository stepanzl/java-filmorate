package ru.yandex.practicum.filmorate.storage.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.filmorate.exception.NotFoundException;
import ru.yandex.practicum.filmorate.model.User;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Repository
@Qualifier("userDbStorage")
public class UserDbStorage implements UserStorage {
    private static final String SELECT_ALL_USERS = "SELECT user_id, email, login, name, birthday FROM users";
    private static final String SELECT_USER_BY_ID = SELECT_ALL_USERS + " WHERE user_id = ?";
    private static final String UPDATE_USER = "UPDATE users SET email = ?, login = ?, name = ?, birthday = ? WHERE user_id = ?";
    private static final String DELETE_USER = "DELETE FROM users WHERE user_id = ?";
    private static final String SELECT_FRIENDSHIPS_BY_USER_IDS = "SELECT user_id, friend_id FROM friendships WHERE user_id IN (%s)";
    private static final String DELETE_FRIENDSHIPS_BY_USER_ID = "DELETE FROM friendships WHERE user_id = ?";
    private static final String MERGE_FRIENDSHIP = "MERGE INTO friendships (user_id, friend_id) KEY (user_id, friend_id) VALUES (?, ?)";
    private static final String DELETE_FRIENDSHIP = "DELETE FROM friendships WHERE user_id = ? AND friend_id = ?";
    private static final String SELECT_FRIENDS_BY_USER_ID = "SELECT u.user_id, u.email, u.login, u.name, u.birthday FROM friendships f " +
            "JOIN users u ON f.friend_id = u.user_id WHERE f.user_id = ?";
    private static final String SELECT_COMMON_FRIENDS = "SELECT DISTINCT u.user_id, u.email, u.login, u.name, u.birthday FROM friendships f1 " +
            "JOIN friendships f2 ON f1.friend_id = f2.friend_id " +
            "JOIN users u ON u.user_id = f1.friend_id " +
            "WHERE f1.user_id = ? AND f2.user_id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert userInsert;
    private final RowMapper<User> userRowMapper = new UserRowMapper();

    public UserDbStorage(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.userInsert = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("users")
                .usingGeneratedKeyColumns("user_id");
    }

    @Override
    public User create(User user) {
        try {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("email", user.getEmail());
            parameters.put("login", user.getLogin());
            parameters.put("name", user.getName());
            parameters.put("birthday", toDate(user.getBirthday()));

            Number generatedId = userInsert.executeAndReturnKey(parameters);
            user.setId(generatedId.longValue());

            updateFriendships(user);
            return findById(user.getId());
        } catch (DataAccessException e) {
            log.error("Failed to create user {}", user, e);
            throw new RuntimeException("Failed to create user", e);
        }
    }

    @Override
    public User update(User user) {
        Objects.requireNonNull(user.getId(), "User id must not be null for update");
        try {
            int updated = jdbcTemplate.update(UPDATE_USER,
                    user.getEmail(),
                    user.getLogin(),
                    user.getName(),
                    toDate(user.getBirthday()),
                    user.getId());
            if (updated == 0) {
                throw new NotFoundException("User with id " + user.getId() + " not found");
            }
            updateFriendships(user);
            return findById(user.getId());
        } catch (DataAccessException e) {
            log.error("Failed to update user {}", user, e);
            throw new RuntimeException("Failed to update user", e);
        }
    }

    @Override
    public Collection<User> findAll() {
        try {
            List<User> users = jdbcTemplate.query(SELECT_ALL_USERS, userRowMapper);
            loadFriendships(users);
            return users;
        } catch (DataAccessException e) {
            log.error("Failed to fetch users", e);
            throw new RuntimeException("Failed to fetch users", e);
        }
    }

    @Override
    public User findById(Long id) {
        try {
            List<User> users = jdbcTemplate.query(SELECT_USER_BY_ID, userRowMapper, id);
            if (users.isEmpty()) {
                throw new NotFoundException("User with id " + id + " not found");
            }
            User user = users.get(0);
            loadFriendships(Collections.singletonList(user));
            return user;
        } catch (DataAccessException e) {
            log.error("Failed to fetch user with id {}", id, e);
            throw new RuntimeException("Failed to fetch user", e);
        }
    }

    @Override
    public void delete(Long id) {
        try {
            int updated = jdbcTemplate.update(DELETE_USER, id);
            if (updated == 0) {
                throw new NotFoundException("User with id " + id + " not found");
            }
        } catch (DataAccessException e) {
            log.error("Failed to delete user with id {}", id, e);
            throw new RuntimeException("Failed to delete user", e);
        }
    }

    @Override
    public void addFriend(Long userId, Long friendId) {
        try {
            jdbcTemplate.update(MERGE_FRIENDSHIP, userId, friendId);
        } catch (DataAccessException e) {
            log.error("Failed to add friend {} for user {}", friendId, userId, e);
            throw new RuntimeException("Failed to add friend", e);
        }
    }

    @Override
    public void removeFriend(Long userId, Long friendId) {
        try {
            int updated = jdbcTemplate.update(DELETE_FRIENDSHIP, userId, friendId);
        } catch (DataAccessException e) {
            log.error("Failed to remove friend {} for user {}", friendId, userId, e);
            throw new RuntimeException("Failed to remove friend", e);
        }
    }

    @Override
    public Collection<User> getFriends(Long userId) {
        try {
            List<User> friends = jdbcTemplate.query(SELECT_FRIENDS_BY_USER_ID, userRowMapper, userId);
            loadFriendships(friends);
            return friends;
        } catch (DataAccessException e) {
            log.error("Failed to fetch friends for user {}", userId, e);
            throw new RuntimeException("Failed to fetch friends", e);
        }
    }

    @Override
    public Collection<User> getCommonFriends(Long userId, Long otherId) {
        try {
            List<User> friends = jdbcTemplate.query(SELECT_COMMON_FRIENDS, userRowMapper, userId, otherId);
            loadFriendships(friends);
            return friends;
        } catch (DataAccessException e) {
            log.error("Failed to fetch common friends for users {} and {}", userId, otherId, e);
            throw new RuntimeException("Failed to fetch common friends", e);
        }
    }

    private void updateFriendships(User user) {
        if (user.getId() == null) {
            return;
        }
        try {
            jdbcTemplate.update(DELETE_FRIENDSHIPS_BY_USER_ID, user.getId());
            Set<Long> friends = user.getFriends();
            if (friends == null || friends.isEmpty()) {
                return;
            }
            List<Object[]> batchArgs = new ArrayList<>();
            for (Long friendId : friends) {
                batchArgs.add(new Object[]{user.getId(), friendId});
            }
            jdbcTemplate.batchUpdate(MERGE_FRIENDSHIP, batchArgs);
        } catch (DataAccessException e) {
            log.error("Failed to update friendships for user {}", user.getId(), e);
            throw new RuntimeException("Failed to update friendships", e);
        }
    }

    private void loadFriendships(Collection<User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        Map<Long, User> usersById = users.stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        if (usersById.isEmpty()) {
            return;
        }
        String placeholders = usersById.keySet().stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));
        String query = String.format(SELECT_FRIENDSHIPS_BY_USER_IDS, placeholders);
        Object[] args = usersById.keySet().toArray();
        try {
            jdbcTemplate.query(query, args, rs -> {
                long userId = rs.getLong("user_id");
                long friendId = rs.getLong("friend_id");
                User user = usersById.get(userId);
                if (user != null) {
                    user.getFriends().add(friendId);
                }
            });
        } catch (DataAccessException e) {
            log.error("Failed to load friendships for users {}", usersById.keySet(), e);
            throw new RuntimeException("Failed to load friendships", e);
        }
    }

    private Date toDate(LocalDate localDate) {
        return localDate == null ? null : Date.valueOf(localDate);
    }

    private static class UserRowMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
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
    }
}