package ru.promo.shortener.config;

public class ApplicationConfig {

    public final long ttlSeconds;
    public final int defaultMaxClicks;

    public final int initialKeyLength;
    public final int maxKeyLength;
    public final int attemptsPerLength;

    public final int cleanupIntervalSeconds;

    public ApplicationConfig(long ttlSeconds,
                             int defaultMaxClicks,
                             int initialKeyLength,
                             int maxKeyLength,
                             int attemptsPerLength,
                             int cleanupIntervalSeconds) {
        this.ttlSeconds = ttlSeconds;
        this.defaultMaxClicks = defaultMaxClicks;
        this.initialKeyLength = initialKeyLength;
        this.maxKeyLength = maxKeyLength;
        this.attemptsPerLength = attemptsPerLength;
        this.cleanupIntervalSeconds = cleanupIntervalSeconds;
    }
}
