package ru.promo.shortener.config;

import java.io.InputStream;
import java.util.Properties;

public class ApplicationConfigLoader {

    public static ApplicationConfig load() {
        Properties props = new Properties();

        try (InputStream is = ApplicationConfigLoader.class
                .getClassLoader()
                .getResourceAsStream("application.properties")) {

            if (is == null) {
                throw new IllegalStateException("application.properties not found");
            }

            props.load(is);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load application.properties", e);
        }

        return new ApplicationConfig(
                Long.parseLong(props.getProperty("link.ttl.seconds")),
                Integer.parseInt(props.getProperty("link.default.max-clicks")),
                Integer.parseInt(props.getProperty("shortkey.length.initial")),
                Integer.parseInt(props.getProperty("shortkey.length.max")),
                Integer.parseInt(props.getProperty("shortkey.attempts.per.length")),
                Integer.parseInt(props.getProperty("cleanup.interval.seconds"))
        );
    }
}
