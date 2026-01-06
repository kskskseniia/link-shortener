package ru.promo.shortener.infra;

import org.junit.jupiter.api.Test;
import ru.promo.shortener.core.model.ShortLink;
import ru.promo.shortener.core.service.ShortLinkRepository;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryShortLinkRepositoryTest {

    // проверка save/find/delete
    @Test
    void save_find_delete_works() {
        ShortLinkRepository repo = new InMemoryShortLinkRepository();

        ShortLink link = new ShortLink(
                "ABC123",
                "https://example.com",
                "owner-A",
                Instant.parse("2026-01-06T10:00:00Z"),
                Instant.parse("2026-01-06T11:00:00Z"),
                3
        );

        repo.save(link);

        assertTrue(repo.findByShortKey("ABC123").isPresent());
        assertEquals(1, repo.findByOwnerUuid("owner-A").size());

        assertTrue(repo.deleteByShortKey("ABC123"));
        assertTrue(repo.findByShortKey("ABC123").isEmpty());
    }

    // проверка поиска протухших ссылок по TTL
    @Test
    void findExpired_returnsTtlExpired() {
        ShortLinkRepository repo = new InMemoryShortLinkRepository();
        Instant now = Instant.parse("2026-01-06T10:00:00Z");

        ShortLink ttlExpired = new ShortLink(
                "TTL001",
                "https://a.com",
                "A",
                now.minusSeconds(100),
                now.minusSeconds(1),
                10
        );

        repo.save(ttlExpired);

        var expired = repo.findExpired(now);
        assertEquals(1, expired.size());
        assertEquals("TTL001", expired.get(0).getShortKey());
    }
}
