package ru.promo.shortener.infra.user;

import ru.promo.shortener.core.user.UserIdentityProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class FileUserIdentityProvider implements UserIdentityProvider {

    // Захардкоженные имена файлов
    private static final Path CURRENT_USER_FILE = Path.of("user.uuid");
    private static final Path USERS_FILE = Path.of("users.txt");

    private String currentUserUuid;
    private final Set<String> knownUsers = new HashSet<>();

    public FileUserIdentityProvider() {
        loadKnownUsers();
        this.currentUserUuid = loadOrCreateCurrentUser();
        registerUser(this.currentUserUuid);
    }

    @Override
    public String getCurrentUserUuid() {
        return currentUserUuid;
    }

    @Override
    public String createNewUser() {
        String uuid = UUID.randomUUID().toString();
        registerUser(uuid);
        writeCurrentUser(uuid);
        this.currentUserUuid = uuid;
        return uuid;
    }

    @Override
    public void switchUser(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            throw new IllegalArgumentException("uuid must not be blank");
        }

        // Проверка формата UUID
        UUID.fromString(uuid);

        if (!knownUsers.contains(uuid)) {
            throw new IllegalArgumentException(
                    "Unknown user UUID. Use 'new-user' or check users.txt"
            );
        }

        writeCurrentUser(uuid);
        this.currentUserUuid = uuid;
    }

    // ---------------- internal ----------------

    private void loadKnownUsers() {
        if (!Files.exists(USERS_FILE)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(USERS_FILE, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (!line.isBlank()) {
                    knownUsers.add(line.trim());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read users file: " + USERS_FILE, e);
        }
    }

    private void registerUser(String uuid) {
        if (knownUsers.add(uuid)) {
            persistKnownUsers();
        }
    }

    private void persistKnownUsers() {
        try {
            Files.write(USERS_FILE, knownUsers, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write users file: " + USERS_FILE, e);
        }
    }

    private String loadOrCreateCurrentUser() {
        if (Files.exists(CURRENT_USER_FILE)) {
            try {
                String uuid = Files.readString(CURRENT_USER_FILE, StandardCharsets.UTF_8).trim();
                if (!uuid.isEmpty()) {
                    UUID.fromString(uuid);
                    return uuid;
                }
            } catch (Exception ignored) {
                // если файл битый — создадим нового
            }
        }

        String newUuid = UUID.randomUUID().toString();
        writeCurrentUser(newUuid);
        return newUuid;
    }

    private void writeCurrentUser(String uuid) {
        try {
            Files.writeString(
                    CURRENT_USER_FILE,
                    uuid + System.lineSeparator(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to write current user file: " + CURRENT_USER_FILE, e
            );
        }
    }
}
