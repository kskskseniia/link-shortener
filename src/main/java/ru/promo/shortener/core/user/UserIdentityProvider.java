package ru.promo.shortener.core.user;

public interface UserIdentityProvider {

    // Текущий пользователь (UUID строкой).
    String getCurrentUserUuid();

    // Создаёт нового пользователя (генерирует UUID),
    String createNewUser();

    // Переключает текущего пользователя на указанный UUID,
    void switchUser(String uuid);
}
