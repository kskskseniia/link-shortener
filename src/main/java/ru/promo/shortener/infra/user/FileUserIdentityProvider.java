package ru.promo.shortener.infra.user;

import ru.promo.shortener.core.user.UserIdentityProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

public class FileUserIdentityProvider implements UserIdentityProvider {

    private final Path filePath;
    private String currentUserUuid;

    public FileUserIdentityProvider(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.currentUserUuid = loadOrCreate();
    }

    @Override
    public String getCurrentUserUuid() {
        return currentUserUuid;
    }

    @Override
    public String createNewUser() {
        String uuid = UUID.randomUUID().toString();
        write(uuid);
        this.currentUserUuid = uuid;
        return uuid;
    }

    @Override
    public void switchUser(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            throw new IllegalArgumentException("uuid must not be blank");
        }
        // простая валидация формата UUID
        UUID.fromString(uuid);

        write(uuid);
        this.currentUserUuid = uuid;
    }

    private String loadOrCreate() {
        if (Files.exists(filePath)) {
            try {
                String uuid = Files.readString(filePath, StandardCharsets.UTF_8).trim();
                if (!uuid.isEmpty()) {
                    // проверим, что это реально UUID
                    UUID.fromString(uuid);
                    return uuid;
                }
            } catch (Exception ignored) {
            }
        }

        String newUuid = UUID.randomUUID().toString();
        write(newUuid);
        return newUuid;
    }

    private void write(String uuid) {
        try {
            Files.writeString(filePath, uuid + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write user uuid file: " + filePath, e);
        }
    }
}
