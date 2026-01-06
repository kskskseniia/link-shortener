package ru.promo.shortener.core.model;

import java.time.Instant;
import java.util.Objects;

public final class ShortLink {
    private final String shortKey;      // уникальная короткая часть ссылки (например AbC123)
    private final String originalUrl;   // исходный URL
    private final String ownerUuid;     // UUID пользователя
    private final Instant createdAt;    // момент создания
    private final Instant expiresAt;    // createdAt + TTL

    private int maxClicks;              // лимит переходов
    private int clicks;                 // сколько уже переходов
    private LinkStatus status;

    public ShortLink(String shortKey,
                     String originalUrl,
                     String ownerUuid,
                     Instant createdAt,
                     Instant expiresAt,
                     int maxClicks) {
        this.shortKey = Objects.requireNonNull(shortKey, "shortKey");
        this.originalUrl = Objects.requireNonNull(originalUrl, "originalUrl");
        this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.maxClicks = maxClicks;
        this.clicks = 0;
        this.status = LinkStatus.ACTIVE;
    }

    public String getShortKey() {
        return shortKey;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getOwnerUuid() {
        return ownerUuid;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getMaxClicks() {
        return maxClicks;
    }

    public int getClicks() {
        return clicks;
    }

    public LinkStatus getStatus() {
        return status;
    }

    public boolean isExpiredByTtl(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean isActive() {
        return status == LinkStatus.ACTIVE;
    }

    public void markExpiredByTtl() {
        if (status == LinkStatus.ACTIVE) {
            status = LinkStatus.EXPIRED_BY_TTL;
        }
    }

    public void markExpiredByClicks() {
        if (status == LinkStatus.ACTIVE) {
            status = LinkStatus.EXPIRED_BY_CLICKS;
        }
    }

    public void markDeleted() {
        status = LinkStatus.DELETED;
    }

    public void setMaxClicks(int maxClicks) {
        this.maxClicks = maxClicks;
    }

    public void registerClick() {
        this.clicks++;
        if (clicks >= maxClicks) {
            markExpiredByClicks();
        }
    }
}
