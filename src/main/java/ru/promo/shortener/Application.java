package ru.promo.shortener;

import ru.promo.shortener.core.model.ShortLink;
import ru.promo.shortener.core.service.ShortLinkRepository;
import ru.promo.shortener.infra.InMemoryShortLinkRepository;

import java.time.Instant;

public class Application {

    public static void main(String[] args) {
        ShortLinkRepository repo = new InMemoryShortLinkRepository();
        String ownerUuid = "user-uuid-1";
        String shortKey = "AbC123";

        System.out.println("=== Initial state ===");
        System.out.println("Links by owner: " + repo.findByOwnerUuid(ownerUuid).size());

        ShortLink link = new ShortLink(
                shortKey,
                "https://www.baeldung.com/java-9-http-client",
                ownerUuid,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                3
        );

        repo.save(link);

        System.out.println("\n=== After save ===");
        System.out.println("Links by owner: " + repo.findByOwnerUuid(ownerUuid).size());

        boolean deleted = repo.deleteByShortKey(shortKey);

        System.out.println("\n=== After delete ===");
        System.out.println("Deleted: " + deleted);
        System.out.println("Links by owner: " + repo.findByOwnerUuid(ownerUuid).size());
    }
}
