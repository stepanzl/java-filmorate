package ru.yandex.practicum.filmorate;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.yandex.practicum.filmorate.controller.UserController;
import ru.yandex.practicum.filmorate.exception.ValidationException;
import ru.yandex.practicum.filmorate.model.User;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UserControllerTest {

    private UserController userController;
    private Validator validator;

    @BeforeEach
    void setUp() {
        userController = new UserController();
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void createUser_withValidData_shouldSucceed() {
        User user = makeValidUser();
        Set<ConstraintViolation<User>> violations = validator.validate(user);
        assertTrue(violations.isEmpty(), "Валидация не должна выдавать ошибок");

        User created = userController.createUser(user);
        assertNotNull(created.getId(), "ID должен быть присвоен");
        assertEquals(user.getLogin(), created.getName(), "Имя должно совпадать с логином, если не задано явно");
    }

    @Test
    void createUser_withEmptyEmail_shouldFailValidation() {
        User user = makeValidUser();
        user.setEmail("");

        Set<ConstraintViolation<User>> violations = validator.validate(user);
        assertFalse(violations.isEmpty(), "Ожидалась ошибка валидации по пустому email");
    }

    @Test
    void createUser_withInvalidLoginContainingSpaces_shouldFailValidation() {
        User user = makeValidUser();
        user.setLogin("bad login");

        Set<ConstraintViolation<User>> violations = validator.validate(user);
        assertFalse(violations.isEmpty(), "Ожидалась ошибка валидации по логину с пробелами");
    }

    @Test
    void createUser_withFutureBirthday_shouldFailValidation() {
        User user = makeValidUser();
        user.setBirthday(LocalDate.now().plusDays(1));

        Set<ConstraintViolation<User>> violations = validator.validate(user);
        assertFalse(violations.isEmpty(), "Ожидалась ошибка валидации по дате рождения в будущем");
    }

    @Test
    void updateUser_whenNotExists_shouldThrowException() {
        User user = makeValidUser();
        user.setId(999L);

        assertThrows(ValidationException.class, () -> userController.updateUser(user));
    }

    private User makeValidUser() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setLogin("testlogin");
        user.setName(""); // пустое имя — должно замениться на логин
        user.setBirthday(LocalDate.of(1990, 1, 1));
        return user;
    }
}

