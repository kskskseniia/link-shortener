package ru.promo.shortener.core.model;

import java.time.Instant;
import java.util.Objects;

public final class ShortLink {
    private final String code;
    private final String originalUrl;    // исходный URL
    private final String ownerUuid;      // UUID пользователя
    private final Instant createdAt;     // момент создания
    private final Instant expiresAt;     // createdAt + TTL

    private int maxClicks;              // лимит переходов
    private int clicks;                 // сколько уже переходов
    private LinkStatus status;

    public ShortLink(String code,
                     String originalUrl,
                     String ownerUuid,
                     Instant createdAt,
                     Instant expiresAt,
                     int maxClicks) {
        this.code = Objects.requireNonNull(code);
        this.originalUrl = Objects.requireNonNull(originalUrl);
        this.ownerUuid = Objects.requireNonNull(ownerUuid);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.maxClicks = maxClicks;
        this.clicks = 0;
        this.status = LinkStatus.ACTIVE;
    }

}
