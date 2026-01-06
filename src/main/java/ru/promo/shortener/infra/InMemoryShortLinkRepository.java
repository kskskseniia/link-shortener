package ru.promo.shortener.infra;

import ru.promo.shortener.core.model.LinkStatus;
import ru.promo.shortener.core.model.ShortLink;
import ru.promo.shortener.core.service.ShortLinkRepository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryShortLinkRepository implements ShortLinkRepository {

    // Храним по shortKey: shortKey -> ShortLink (объект)
    private final Map<String, ShortLink> byShortKey = new ConcurrentHashMap<>();

    // Дополнительный индекс: UUID пользователя -> набор shortKey его ссылок
    private final Map<String, Set<String>> shortKeysByOwner = new ConcurrentHashMap<>();

    @Override
    public void save(ShortLink link) {
        Objects.requireNonNull(link, "link");

        byShortKey.put(link.getShortKey(), link);

        // Обновляем индекс владельца
        shortKeysByOwner
                .computeIfAbsent(link.getOwnerUuid(), k -> ConcurrentHashMap.newKeySet())
                .add(link.getShortKey());
    }

    @Override
    public Optional<ShortLink> findByShortKey(String shortKey) {
        if (shortKey == null || shortKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byShortKey.get(shortKey));
    }

    @Override
    public List<ShortLink> findByOwnerUuid(String ownerUuid) {
        if (ownerUuid == null || ownerUuid.isBlank()) {
            return List.of();
        }

        Set<String> shortKeys = shortKeysByOwner.getOrDefault(ownerUuid, Set.of());
        if (shortKeys.isEmpty()) {
            return List.of();
        }

        List<ShortLink> result = new ArrayList<>(shortKeys.size());
        for (String shortKey : shortKeys) {
            ShortLink link = byShortKey.get(shortKey);
            if (link != null) {
                result.add(link);
            }
        }
        return result;
    }

    @Override
    public boolean deleteByShortKey(String shortKey) {
        if (shortKey == null || shortKey.isBlank()) {
            return false;
        }

        ShortLink removed = byShortKey.remove(shortKey);
        if (removed == null) {
            return false;
        }

        // Чистим индекс владельца
        Set<String> shortKeys = shortKeysByOwner.get(removed.getOwnerUuid());
        if (shortKeys != null) {
            shortKeys.remove(shortKey);
            if (shortKeys.isEmpty()) {
                shortKeysByOwner.remove(removed.getOwnerUuid());
            }
        }

        // (не обязательно) помечаем объект как удалённый
        removed.markDeleted();
        return true;
    }

    @Override
    public List<ShortLink> findExpired(Instant now) {
        Objects.requireNonNull(now, "now");

        List<ShortLink> expired = new ArrayList<>();
        for (ShortLink link : byShortKey.values()) {
            // 1) TTL истёк
            if (link.isExpiredByTtl(now)) {
                link.markExpiredByTtl();
                expired.add(link);
                continue;
            }

            // 2) Уже не активна (например, исчерпан лимит кликов)
            if (link.getStatus() == LinkStatus.EXPIRED_BY_CLICKS) {
                expired.add(link);
            }
        }
        return expired;
    }

    @Override
    public List<ShortLink> findAll() {
        return new ArrayList<>(byShortKey.values());
    }
}
