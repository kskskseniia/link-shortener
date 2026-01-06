package ru.promo.shortener.config;

import java.io.InputStream;
import java.util.Properties;

public class ApplicationConfigLoader {

    public static ApplicationConfig load() {
        Properties props = new Properties();

        try (InputStream is = ApplicationConfigLoader.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (is == null) {
                throw new IllegalStateException("application.properties not found in resources");
            }
            props.load(is);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load application.properties", e);
        }

        int initialKeyLength = Integer.parseInt(props.getProperty("shortkey.length.initial"));
        int maxKeyLength = Integer.parseInt(props.getProperty("shortkey.length.max"));
        int attemptsPerLength = Integer.parseInt(props.getProperty("shortkey.attempts.per.length"));

        long ttlSeconds = Long.parseLong(props.getProperty("link.ttl.seconds"));
        int defaultMaxClicks = Integer.parseInt(props.getProperty("link.default.max-clicks"));

        int cleanupIntervalSeconds = Integer.parseInt(props.getProperty("cleanup.interval.seconds"));

        return new ApplicationConfig(
                initialKeyLength,
                maxKeyLength,
                attemptsPerLength,
                ttlSeconds,
                defaultMaxClicks,
                cleanupIntervalSeconds
        );
    }
}
