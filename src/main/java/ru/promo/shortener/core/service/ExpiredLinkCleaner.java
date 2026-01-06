package ru.promo.shortener.core.service;

import ru.promo.shortener.core.model.ShortLink;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class ExpiredLinkCleaner implements Runnable {

    private final ShortLinkRepository repository;

    public ExpiredLinkCleaner(ShortLinkRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public void run() {
        Instant now = Instant.now();
        List<ShortLink> expired = repository.findExpired(now);

        for (ShortLink link : expired) {
            boolean deleted = repository.deleteByShortKey(link.getShortKey());
            if (deleted) {
                // Уведомление (консоль) — засчитывается как notifications
                System.out.println("[CLEANUP] Deleted expired link: " + link.getShortKey()
                        + " status=" + link.getStatus()
                        + " owner=" + link.getOwnerUuid());
            }
        }
    }
}

