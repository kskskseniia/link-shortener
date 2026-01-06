package ru.promo.shortener;

import ru.promo.shortener.config.ApplicationConfig;
import ru.promo.shortener.config.ApplicationConfigLoader;
import ru.promo.shortener.core.model.ShortLink;
import ru.promo.shortener.core.service.ShortKeyGenerator;
import ru.promo.shortener.core.service.ShortLinkRepository;
import ru.promo.shortener.core.service.ShortLinkService;
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

        // 1) Текущий пользователь (из файла или созданный автоматически)
        String userA = userIdentity.getCurrentUserUuid();
        System.out.println("Current user (A): " + userA);

        // 2) Создаём ссылку как пользователь A
        ShortLink linkA = service.create("https://google.com", userA, 5);
        System.out.println("User A created shortKey: " + linkA.getShortKey());

        // 3) Создаём нового пользователя (B) — это ПЕРЕЗАПИШЕТ user.uuid
        String userB = userIdentity.createNewUser();
        System.out.println("Created NEW user (B): " + userB);

        // 4) Убеждаемся, что текущий пользователь теперь B
        System.out.println("Current user now: " + userIdentity.getCurrentUserUuid());

        // 5) Создаём ссылку как пользователь B
        ShortLink linkB = service.create("https://example.com", userB, 3);
        System.out.println("User B created shortKey: " + linkB.getShortKey());

        // 6) Переключаемся обратно на пользователя A
        userIdentity.switchUser(userA);
        System.out.println("Switched back to user (A): " + userIdentity.getCurrentUserUuid());

        // 7) Проверим, что list по репозиторию у A показывает только ссылки A (по ownerUuid)
        System.out.println("Links for A: " + repo.findByOwnerUuid(userA).size());
        System.out.println("Links for B: " + repo.findByOwnerUuid(userB).size());
    }
}
