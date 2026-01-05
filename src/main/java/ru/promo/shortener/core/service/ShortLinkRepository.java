package ru.promo.shortener.core.service;

import ru.promo.shortener.core.model.ShortLink;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShortLinkRepository {

    void save(ShortLink link);

    Optional<ShortLink> findByCode(String code);

    List<ShortLink> findByOwnerUuid(String ownerUuid);

    boolean deleteByCode(String code);

    List<ShortLink> findExpired(Instant now);
}
