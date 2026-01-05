package ru.promo.shortener.core.model;

public enum LinkStatus {
    ACTIVE,
    EXPIRED_BY_TTL,
    EXPIRED_BY_CLICKS,
    DELETED
}