package ru.promo.shortener.core.service;

import ru.promo.shortener.core.model.ShortLink;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShortLinkRepository {

    void save(ShortLink link);

    Optional<ShortLink> findByShortKey(String shortKey);

    List<ShortLink> findByOwnerUuid(String ownerUuid);

    boolean deleteByShortKey(String shortKey);

    List<ShortLink> findExpired(Instant now);

    List<ShortLink> findAll();
}
