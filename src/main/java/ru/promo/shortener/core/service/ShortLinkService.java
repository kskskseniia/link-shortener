package ru.promo.shortener.core.service;

import ru.promo.shortener.config.ApplicationConfig;
import ru.promo.shortener.core.model.ShortLink;
import ru.promo.shortener.core.service.exceptions.AccessDeniedException;
import ru.promo.shortener.core.service.exceptions.NotFoundException;
import ru.promo.shortener.core.service.exceptions.ValidationException;

import java.time.Instant;
import java.time.Clock;
import java.util.Objects;
import java.net.URI;

public class ShortLinkService {

    private final ShortLinkRepository repository;
    private final ShortKeyGenerator generator;

    private final long ttlSeconds;
    private final int defaultMaxClicks;

    private final int initialKeyLength;
    private final int maxKeyLength;
    private final int attemptsPerLength;

    private static final int MAX_URL_LENGTH = 2048;

    private final Clock clock;

    public ShortLinkService(ShortLinkRepository repository,
                            ShortKeyGenerator generator,
                            ApplicationConfig config) {
        this(repository, generator, config, Clock.systemUTC());
    }

    public ShortLinkService(ShortLinkRepository repository,
                            ShortKeyGenerator generator,
                            ApplicationConfig config,
                            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.generator = Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");

        if (config.ttlSeconds <= 0) throw new ValidationException("link.ttl.seconds must be positive");
        if (config.defaultMaxClicks <= 0) throw new ValidationException("link.default.max-clicks must be positive");
        if (config.initialKeyLength <= 0) throw new ValidationException("shortkey.length.initial must be positive");
        if (config.maxKeyLength < config.initialKeyLength) {
            throw new ValidationException("shortkey.length.max must be >= shortkey.length.initial");
        }
        if (config.attemptsPerLength <= 0) throw new ValidationException("shortkey.attempts.per.length must be positive");

        this.ttlSeconds = config.ttlSeconds;
        this.defaultMaxClicks = config.defaultMaxClicks;
        this.initialKeyLength = config.initialKeyLength;
        this.maxKeyLength = config.maxKeyLength;
        this.attemptsPerLength = config.attemptsPerLength;
    }

    // create URL (без лимита) -> берём default
    public ShortLink create(String originalUrl, String ownerUuid) {
        return create(originalUrl, ownerUuid, defaultMaxClicks);
    }

    // create URL + лимит кликов (например: create https://google.com 50)
    public ShortLink create(String originalUrl, String ownerUuid, int maxClicks) {
        validateUrl(originalUrl);
        validateOwner(ownerUuid);
        validateMaxClicks(maxClicks);

        Instant now = Instant.now(clock);
        Instant expiresAt = now.plusSeconds(ttlSeconds);

        String shortKey = generateUniqueShortKey();

        ShortLink link = new ShortLink(shortKey, originalUrl, ownerUuid, now, expiresAt, maxClicks);
        repository.save(link);
        return link;
    }

    // resolve (переход): проверка TTL/лимита + регистрация клика + вернуть originalUrl
    public String resolve(String shortKey) {
        if (shortKey == null || shortKey.isBlank()) {
            throw new ValidationException("shortKey must not be empty");
        }
        if (shortKey.contains(" ")) {
            throw new ValidationException("shortKey must not contain spaces");
        }

        ShortLink link = repository.findByShortKey(shortKey)
                .orElseThrow(() -> new NotFoundException("Short link not found: " + shortKey));

        Instant now = Instant.now(clock);

        // TTL
        if (link.isExpiredByTtl(now)) {
            link.markExpiredByTtl();
            repository.save(link);
            throw new ValidationException("Link expired by TTL");
        }

        // Если уже не активна (удалена/протухла)
        if (!link.isActive()) {
            throw new ValidationException("Link is not active. Status: " + link.getStatus());
        }

        // КЛЮЧЕВАЯ ПРОВЕРКА: если лимит уже исчерпан — блокируем
        if (link.getClicks() >= link.getMaxClicks()) {
            link.markExpiredByClicks();
            repository.save(link);
            throw new ValidationException("Link expired by clicks limit");
        }

        // Разрешаем переход и учитываем клик
        link.registerClick();

        // Если это был последний разрешённый клик — пометим, чтобы следующий уже блокировался
        if (link.getClicks() >= link.getMaxClicks()) {
            link.markExpiredByClicks();

            System.out.println("Link " + link.getShortKey()
                    + " expired: click limit reached (" + link.getMaxClicks() + ")");
        }

        repository.save(link);
        return link.getOriginalUrl();
    }

    // редактирование лимита владельцем
    public ShortLink updateMaxClicks(String shortKey, String ownerUuid, int newMaxClicks) {
        if (shortKey == null || shortKey.isBlank()) {
            throw new ValidationException("shortKey must not be empty");
        }
        if (shortKey.contains(" ")) {
            throw new ValidationException("shortKey must not contain spaces");
        }
        validateOwner(ownerUuid);
        validateMaxClicks(newMaxClicks);

        ShortLink link = repository.findByShortKey(shortKey)
                .orElseThrow(() -> new NotFoundException("Short link not found: " + shortKey));

        if (!Objects.equals(link.getOwnerUuid(), ownerUuid)) {
            throw new AccessDeniedException("Only owner can update the link");
        }

        if (!link.isActive()) {
            throw new ValidationException("Link is not active. Status: " + link.getStatus());
        }

        link.setMaxClicks(newMaxClicks);

        if (link.getClicks() >= link.getMaxClicks()) {
            link.markExpiredByClicks();
            repository.save(link);
            return link;
        }

        repository.save(link);
        return link;
    }

    // удаление владельцем
    public boolean deleteByOwner(String shortKey, String ownerUuid) {
        if (shortKey == null || shortKey.isBlank()) {
            throw new ValidationException("shortKey must not be empty");
        }
        if (shortKey.contains(" ")) {
            throw new ValidationException("shortKey must not contain spaces");
        }
        validateOwner(ownerUuid);

        ShortLink link = repository.findByShortKey(shortKey)
                .orElseThrow(() -> new NotFoundException("Short link not found: " + shortKey));

        if (!Objects.equals(link.getOwnerUuid(), ownerUuid)) {
            throw new AccessDeniedException("Only owner can delete the link");
        }

        return repository.deleteByShortKey(shortKey);
    }

    private String generateUniqueShortKey() {
        int length = initialKeyLength;

        while (length <= maxKeyLength) {
            for (int i = 0; i < attemptsPerLength; i++) {
                String candidate = generator.generate(length);
                if (repository.findByShortKey(candidate).isEmpty()) {
                    return candidate;
                }
            }
            length++;
        }

        throw new IllegalStateException("Unable to generate unique shortKey");
    }

    private void validateOwner(String ownerUuid) {
        if (ownerUuid == null || ownerUuid.isBlank()) throw new ValidationException("ownerUuid must not be blank");
    }

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new ValidationException("URL must not be empty");
        }

        String normalizedUrl = url.trim();

        if (normalizedUrl.length() > MAX_URL_LENGTH) {
            throw new ValidationException("URL is too long (max " + MAX_URL_LENGTH + " characters)");
        }

        final URI uri;
        try {
            uri = new URI(normalizedUrl);
        } catch (Exception e) {
            throw new ValidationException("Invalid URL format");
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new ValidationException("URL must start with http:// or https://");
        }

        String host = uri.getHost();
        if (host == null) {
            throw new ValidationException("URL must contain a host");
        }

        boolean isLocalhost = host.equalsIgnoreCase("localhost");
        boolean isIpv4 = host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
        boolean hasDot = host.contains(".");

        if (!isLocalhost && !isIpv4 && !hasDot) {
            throw new ValidationException(
                    "Host must be a valid domain, localhost, or IPv4 address"
            );
        }
    }

    private void validateMaxClicks(int maxClicks) {
        if (maxClicks <= 0) {
            throw new ValidationException("maxClicks must be greater than 0");
        }
    }
}
