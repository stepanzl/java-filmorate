package ru.yandex.practicum.filmorate.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.filmorate.model.User;
import ru.yandex.practicum.filmorate.storage.user.UserStorage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserService {
    private final UserStorage userStorage;

    @Autowired
    public UserService(@Qualifier("userDbStorage") UserStorage userStorage) {
        this.userStorage = userStorage;
    }

    @Transactional
    public User create(User user) {
        return userStorage.create(user);
    }

    @Transactional
    public User update(User user) {
        return userStorage.update(user);
    }

    public Collection<User> findAll() {
        return userStorage.findAll();
    }

    public User findById(Long id) {
        return userStorage.findById(id);
    }

    @Transactional
    public void addFriend(Long userId, Long friendId) {
        findById(userId);
        findById(friendId);
        userStorage.addFriend(userId, friendId);
    }

    @Transactional
    public void removeFriend(Long userId, Long friendId) {
        findById(userId);
        findById(friendId);
        userStorage.removeFriend(userId, friendId);
    }

    public List<User> getFriends(Long userId) {
        findById(userId);
        return new ArrayList<>(userStorage.getFriends(userId));
    }

    public List<User> getCommonFriends(Long userId, Long otherId) {
        findById(userId);
        findById(otherId);
        return new ArrayList<>(userStorage.getCommonFriends(userId, otherId));
    }
}
