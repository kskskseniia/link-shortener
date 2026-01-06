package ru.promo.shortener.config;

public class ApplicationConfig {
    public final int initialKeyLength;
    public final int maxKeyLength;
    public final int attemptsPerLength;

    public final long ttlSeconds;
    public final int defaultMaxClicks;

    public final int cleanupIntervalSeconds;

    public ApplicationConfig(int initialKeyLength,
                             int maxKeyLength,
                             int attemptsPerLength,
                             long ttlSeconds,
                             int defaultMaxClicks,
                             int cleanupIntervalSeconds) {
        this.initialKeyLength = initialKeyLength;
        this.maxKeyLength = maxKeyLength;
        this.attemptsPerLength = attemptsPerLength;
        this.ttlSeconds = ttlSeconds;
        this.defaultMaxClicks = defaultMaxClicks;
        this.cleanupIntervalSeconds = cleanupIntervalSeconds;
    }
}
