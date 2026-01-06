package ru.promo.shortener;

import ru.promo.shortener.config.ApplicationConfig;
import ru.promo.shortener.config.ApplicationConfigLoader;
import ru.promo.shortener.core.model.ShortLink;
import ru.promo.shortener.core.service.ShortKeyGenerator;
import ru.promo.shortener.core.service.ShortLinkRepository;
import ru.promo.shortener.core.service.ShortLinkService;
import ru.promo.shortener.core.service.exceptions.AccessDeniedException;
import ru.promo.shortener.core.service.exceptions.ValidationException;
import ru.promo.shortener.core.user.UserIdentityProvider;
import ru.promo.shortener.infra.InMemoryShortLinkRepository;
import ru.promo.shortener.infra.RandomShortKeyGenerator;
import ru.promo.shortener.infra.user.FileUserIdentityProvider;

import java.nio.file.Path;

public class Application {

    public static void main(String[] args) {
        ApplicationConfig config = ApplicationConfigLoader.load();

        ShortLinkRepository repo = new InMemoryShortLinkRepository();
        ShortKeyGenerator generator = new RandomShortKeyGenerator();
        ShortLinkService service = new ShortLinkService(repo, generator, config);

        UserIdentityProvider userIdentity = new FileUserIdentityProvider(Path.of("user.uuid"));

        // A: текущий/первый пользователь
        String userA = userIdentity.getCurrentUserUuid();
        System.out.println("Current user (A): " + userA);

        ShortLink linkA = service.create("https://google.com", userA, 5);
        System.out.println("User A created shortKey: " + linkA.getShortKey());

        // B: создаём нового пользователя и делаем его текущим
        String userB = userIdentity.createNewUser();
        System.out.println("Created NEW user (B): " + userB);

        ShortLink linkB = service.create("https://example.com", userB, 3);
        System.out.println("User B created shortKey: " + linkB.getShortKey());

        System.out.println();
        System.out.println("=== Access control checks ===");

        // 1) B пытается удалить ссылку A
        try {
            System.out.println("B tries to delete A's link...");
            service.deleteByOwner(linkA.getShortKey(), userB);
            System.out.println("ERROR: B deleted A's link (should not happen)");
        } catch (AccessDeniedException e) {
            System.out.println("OK: Access denied (delete чужой ссылки запрещён)");
        }

        // 2) B пытается изменить лимит у ссылки A
        try {
            System.out.println("B tries to update maxClicks for A's link...");
            service.updateMaxClicks(linkA.getShortKey(), userB, 999);
            System.out.println("ERROR: B updated A's link (should not happen)");
        } catch (AccessDeniedException e) {
            System.out.println("OK: Access denied (edit чужой ссылки запрещён)");
        } catch (ValidationException e) {
            // если у тебя в сервисе есть проверки статуса/значений — тоже нормально
            System.out.println("OK: Validation blocked operation: " + e.getMessage());
        }

        // 3) A успешно меняет лимит у своей ссылки
        System.out.println();
        System.out.println("A updates OWN link maxClicks to 10...");
        service.updateMaxClicks(linkA.getShortKey(), userA, 10);
        System.out.println("OK: A updated own link");

        // 4) A успешно удаляет свою ссылку
        System.out.println("A deletes OWN link...");
        boolean deleted = service.deleteByOwner(linkA.getShortKey(), userA);
        System.out.println("Deleted: " + deleted);

        System.out.println();
        System.out.println("Links for A: " + repo.findByOwnerUuid(userA).size());
        System.out.println("Links for B: " + repo.findByOwnerUuid(userB).size());
    }
}
