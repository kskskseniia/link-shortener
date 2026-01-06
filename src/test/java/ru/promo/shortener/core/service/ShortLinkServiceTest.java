package ru.promo.shortener.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.promo.shortener.config.ApplicationConfig;
import ru.promo.shortener.core.model.LinkStatus;
import ru.promo.shortener.core.model.ShortLink;
import ru.promo.shortener.core.service.exceptions.AccessDeniedException;
import ru.promo.shortener.core.service.exceptions.NotFoundException;
import ru.promo.shortener.core.service.exceptions.ValidationException;
import ru.promo.shortener.infra.InMemoryShortLinkRepository;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.*;

class ShortLinkServiceTest {

    private InMemoryShortLinkRepository repo;

    @BeforeEach
    void setUp() {
        repo = new InMemoryShortLinkRepository();
    }

    private static ApplicationConfig cfg(long ttlSeconds, int defaultMaxClicks,
                                         int initialLen, int maxLen, int attemptsPerLen,
                                         int cleanupIntervalSeconds) {
        return new ApplicationConfig(
                initialLen,
                maxLen,
                attemptsPerLen,
                ttlSeconds,
                defaultMaxClicks,
                cleanupIntervalSeconds
        );
    }

    /**
     * Генератор, который возвращает ключи из очереди по порядку.
     * Удобно, чтобы детерминированно тестировать коллизии.
     */
    private static ShortKeyGenerator seqGenerator(String... keys) {
        Deque<String> q = new ArrayDeque<>();
        for (String k : keys) q.addLast(k);

        return length -> {
            if (q.isEmpty()) return "ZZZZZZ";
            return q.removeFirst();
        };
    }

    // проверка отклонения некорректной конфигурации сервиса
    @Test
    void constructor_invalidConfig_rejected() {
        assertThrows(ValidationException.class, () ->
                new ShortLinkService(repo, seqGenerator("A"), cfg(0, 3, 6, 10, 10, 60)));

        assertThrows(ValidationException.class, () ->
                new ShortLinkService(repo, seqGenerator("A"), cfg(3600, 0, 6, 10, 10, 60)));

        assertThrows(ValidationException.class, () ->
                new ShortLinkService(repo, seqGenerator("A"), cfg(3600, 3, 0, 10, 10, 60)));

        assertThrows(ValidationException.class, () ->
                new ShortLinkService(repo, seqGenerator("A"), cfg(3600, 3, 8, 6, 10, 60)));

        assertThrows(ValidationException.class, () ->
                new ShortLinkService(repo, seqGenerator("A"), cfg(3600, 3, 6, 10, 0, 60)));
    }

    // создание ссылки с валидным URL: сохраняется и возвращается корректный ShortLink
    @Test
    void create_validUrl_savesAndReturnsLink() {
        var service = new ShortLinkService(repo, seqGenerator("ABC123"),
                cfg(3600, 3, 6, 10, 10, 60));

        ShortLink link = service.create("https://google.com", "owner-A");

        assertEquals("ABC123", link.getShortKey());
        assertEquals("https://google.com", link.getOriginalUrl());
        assertEquals("owner-A", link.getOwnerUuid());
        assertEquals(3, link.getMaxClicks());
        assertEquals(0, link.getClicks());
        assertEquals(LinkStatus.ACTIVE, link.getStatus());

        assertTrue(repo.findByShortKey("ABC123").isPresent());
    }

    // проверка валидации URL при создании ссылки
    @Test
    void create_invalidUrl_rejected() {
        var service = new ShortLinkService(repo, seqGenerator("A"),
                cfg(3600, 3, 6, 10, 10, 60));

        assertThrows(ValidationException.class, () -> service.create("   ", "owner-A"));
        assertThrows(ValidationException.class, () -> service.create("ftp://example.com", "owner-A"));
        assertThrows(ValidationException.class, () -> service.create("http://1", "owner-A"));
    }

    // проверка валидации владельца (ownerUuid) при создании ссылки
    @Test
    void create_invalidOwner_rejected() {
        var service = new ShortLinkService(repo, seqGenerator("A"),
                cfg(3600, 3, 6, 10, 10, 60));

        assertThrows(ValidationException.class, () -> service.create("https://example.com", " "));
        assertThrows(ValidationException.class, () -> service.create("https://example.com", null));
    }

    // создание ссылки с пользовательским лимитом кликов
    @Test
    void create_customMaxClicks_isSaved() {
        var service = new ShortLinkService(repo, seqGenerator("K1"),
                cfg(3600, 3, 6, 10, 10, 60));

        ShortLink link = service.create("https://example.com", "owner-A", 50);
        assertEquals(50, link.getMaxClicks());
    }

    // проверка отклонения некорректного лимита кликов при создании
    @Test
    void create_invalidMaxClicks_rejected() {
        var service = new ShortLinkService(repo, seqGenerator("K1"),
                cfg(3600, 3, 6, 10, 10, 60));

        assertThrows(ValidationException.class, () -> service.create("https://example.com", "owner-A", 0));
        assertThrows(ValidationException.class, () -> service.create("https://example.com", "owner-A", -5));
    }

    // один и тот же URL у разных пользователей -> разные shortKey
    @Test
    void create_sameUrlDifferentOwners_getDifferentShortKeys() {
        var service = new ShortLinkService(repo, seqGenerator("KEY111", "KEY222"),
                cfg(3600, 3, 6, 10, 10, 60));

        ShortLink a = service.create("https://example.com", "owner-A");
        ShortLink b = service.create("https://example.com", "owner-B");

        assertNotEquals(a.getShortKey(), b.getShortKey());
    }

    // обработка коллизий генератора shortKey
    @Test
    void create_collision_generatesAnotherKey() {
        var service = new ShortLinkService(repo, seqGenerator("DUPLIC", "DUPLIC", "UNIQ01"),
                cfg(3600, 3, 6, 10, 10, 60));

        ShortLink first = service.create("https://a.com", "owner-A");
        ShortLink second = service.create("https://b.com", "owner-A");

        assertEquals("DUPLIC", first.getShortKey());
        assertEquals("UNIQ01", second.getShortKey());
    }

    // проверка валидации shortKey при переходе по ссылке
    @Test
    void resolve_invalidShortKey_rejected() {
        var service = new ShortLinkService(repo, seqGenerator("X"),
                cfg(3600, 3, 6, 10, 10, 60));

        assertThrows(ValidationException.class, () -> service.resolve("   "));
        assertThrows(ValidationException.class, () -> service.resolve("A B"));
        assertThrows(ValidationException.class, () -> service.resolve(null));
    }

    // переход по несуществующему shortKey -> NotFoundException
    @Test
    void resolve_unknownKey_notFound() {
        var service = new ShortLinkService(repo, seqGenerator("X"),
                cfg(3600, 3, 6, 10, 10, 60));

        assertThrows(NotFoundException.class, () -> service.resolve("NOPE"));
    }

    // учёт кликов: разрешено ровно maxClicks переходов, затем блокировка
    @Test
    void resolve_allowsExactlyMaxClicks_thenBlocks() {
        var service = new ShortLinkService(repo, seqGenerator("CLICK3"),
                cfg(3600, 3, 6, 10, 10, 60));

        service.create("https://example.com", "owner-A"); // maxClicks=3

        assertEquals("https://example.com", service.resolve("CLICK3"));
        assertEquals("https://example.com", service.resolve("CLICK3"));
        assertEquals("https://example.com", service.resolve("CLICK3"));

        ValidationException ex = assertThrows(ValidationException.class, () -> service.resolve("CLICK3"));
        assertTrue(ex.getMessage().toLowerCase().contains("click"));

        ShortLink saved = repo.findByShortKey("CLICK3").orElseThrow();
        assertEquals(3, saved.getClicks());
        assertEquals(LinkStatus.EXPIRED_BY_CLICKS, saved.getStatus());
    }

    // блокировка ссылки при истечении времени жизни (TTL)
    @Test
    void resolve_afterTtl_marksExpiredAndBlocks() {
        var service = new ShortLinkService(repo, seqGenerator("TTL001"),
                cfg(3600, 3, 6, 10, 10, 60));

        ShortLink link = service.create("https://example.com", "owner-A");

        // делаем TTL истёкшим вручную: пересоздадим объект со старым shortKey, но expiresAt в прошлом
        repo.deleteByShortKey(link.getShortKey());

        ShortLink expired = new ShortLink(
                link.getShortKey(),
                link.getOriginalUrl(),
                link.getOwnerUuid(),
                Instant.now().minusSeconds(5000),
                Instant.now().minusSeconds(1),
                link.getMaxClicks()
        );
        repo.save(expired);

        ValidationException ex = assertThrows(ValidationException.class, () -> service.resolve("TTL001"));
        assertTrue(ex.getMessage().toLowerCase().contains("ttl"));

        ShortLink saved = repo.findByShortKey("TTL001").orElseThrow();
        assertEquals(LinkStatus.EXPIRED_BY_TTL, saved.getStatus());
    }

    // изменение лимита кликов доступно только владельцу ссылки
    @Test
    void updateMaxClicks_onlyOwnerCanUpdate() {
        var service = new ShortLinkService(repo, seqGenerator("UPD001"),
                cfg(3600, 3, 6, 10, 10, 60));

        service.create("https://example.com", "owner-A");

        assertThrows(AccessDeniedException.class,
                () -> service.updateMaxClicks("UPD001", "owner-B", 10));
    }

    // после 2 переходов уменьшаем лимит до 1 -> ссылка сразу истекает и дальнейший open блокируется
    @Test
    void updateMaxClicks_afterTwoResolves_setToOne_blocksLink() {
        var service = new ShortLinkService(repo, seqGenerator("CUT001"),
                cfg(3600, 10, 6, 10, 10, 60));

        service.create("https://example.com", "owner-A", 10);

        assertEquals("https://example.com", service.resolve("CUT001"));
        assertEquals("https://example.com", service.resolve("CUT001"));

        ShortLink updated = service.updateMaxClicks("CUT001", "owner-A", 1);
        assertEquals(LinkStatus.EXPIRED_BY_CLICKS, updated.getStatus());
        assertEquals(2, updated.getClicks());
        assertEquals(1, updated.getMaxClicks());

        ValidationException ex = assertThrows(ValidationException.class, () -> service.resolve("CUT001"));
        assertTrue(ex.getMessage().toLowerCase().contains("not active")
                || ex.getMessage().toLowerCase().contains("click"));
    }

    // проверка валидации нового лимита кликов
    @Test
    void updateMaxClicks_invalidValue_rejected() {
        var service = new ShortLinkService(repo, seqGenerator("UPD002"),
                cfg(3600, 3, 6, 10, 10, 60));

        service.create("https://example.com", "owner-A");

        assertThrows(ValidationException.class,
                () -> service.updateMaxClicks("UPD002", "owner-A", 0));
        assertThrows(ValidationException.class,
                () -> service.updateMaxClicks("UPD002", "owner-A", -5));
    }

    // при уменьшении лимита ниже текущих кликов ссылка сразу истекает
    @Test
    void updateMaxClicks_lowerThanCurrentClicks_marksExpired() {
        var service = new ShortLinkService(repo, seqGenerator("LOW001"),
                cfg(3600, 10, 6, 10, 10, 60));

        service.create("https://example.com", "owner-A", 10);

        service.resolve("LOW001");
        service.resolve("LOW001");
        service.resolve("LOW001"); // clicks=3

        ShortLink updated = service.updateMaxClicks("LOW001", "owner-A", 2);
        assertEquals(LinkStatus.EXPIRED_BY_CLICKS, updated.getStatus());
    }

    // удаление ссылки доступно только владельцу
    @Test
    void delete_onlyOwnerCanDelete() {
        var service = new ShortLinkService(repo, seqGenerator("DEL001"),
                cfg(3600, 3, 6, 10, 10, 60));

        service.create("https://example.com", "owner-A");

        assertThrows(AccessDeniedException.class,
                () -> service.deleteByOwner("DEL001", "owner-B"));

        assertTrue(service.deleteByOwner("DEL001", "owner-A"));
        assertTrue(repo.findByShortKey("DEL001").isEmpty());
    }

    // list: возвращаются только ссылки текущего владельца
    @Test
    void listByOwner_returnsOnlyOwnersLinks() {
        var service = new ShortLinkService(repo, seqGenerator("A1", "B1", "A2"),
                cfg(3600, 3, 6, 10, 10, 60));

        service.create("https://a.com", "owner-A");
        service.create("https://b.com", "owner-B");
        service.create("https://c.com", "owner-A");

        var ownerALinks = repo.findByOwnerUuid("owner-A");
        var ownerBLinks = repo.findByOwnerUuid("owner-B");

        assertEquals(2, ownerALinks.size());
        assertEquals(1, ownerBLinks.size());

        assertTrue(ownerALinks.stream().allMatch(l -> l.getOwnerUuid().equals("owner-A")));
        assertTrue(ownerBLinks.stream().allMatch(l -> l.getOwnerUuid().equals("owner-B")));
    }

    // list: после удаления ссылка исчезает из списка владельца
    @Test
    void listByOwner_afterDelete_linkIsRemoved() {
        var service = new ShortLinkService(repo, seqGenerator("DEL1", "DEL2"),
                cfg(3600, 3, 6, 10, 10, 60));

        service.create("https://a.com", "owner-A");
        service.create("https://b.com", "owner-A");

        assertEquals(2, repo.findByOwnerUuid("owner-A").size());

        service.deleteByOwner("DEL1", "owner-A");

        var links = repo.findByOwnerUuid("owner-A");
        assertEquals(1, links.size());
        assertEquals("DEL2", links.get(0).getShortKey());
    }

    // list-all: возвращает все ссылки всех пользователей
    @Test
    void listAll_returnsAllLinks() {
        var service = new ShortLinkService(repo, seqGenerator("A1", "B1", "A2"),
                cfg(3600, 3, 6, 10, 10, 60));

        service.create("https://a.com", "owner-A");
        service.create("https://b.com", "owner-B");
        service.create("https://c.com", "owner-A");

        var allLinks = repo.findAll();
        assertEquals(3, allLinks.size());
    }
}
