package ru.promo.shortener;

import ru.promo.shortener.config.ApplicationConfig;
import ru.promo.shortener.config.ApplicationConfigLoader;
import ru.promo.shortener.core.model.ShortLink;
import ru.promo.shortener.core.service.ExpiredLinkCleaner;
import ru.promo.shortener.core.service.ShortKeyGenerator;
import ru.promo.shortener.core.service.ShortLinkRepository;
import ru.promo.shortener.core.service.ShortLinkService;
import ru.promo.shortener.core.user.UserIdentityProvider;
import ru.promo.shortener.infra.InMemoryShortLinkRepository;
import ru.promo.shortener.infra.RandomShortKeyGenerator;
import ru.promo.shortener.infra.user.FileUserIdentityProvider;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Application {

    public static void main(String[] args) {
        ApplicationConfig config = ApplicationConfigLoader.load();

        ShortLinkRepository repo = new InMemoryShortLinkRepository();
        ShortKeyGenerator generator = new RandomShortKeyGenerator();
        ShortLinkService service = new ShortLinkService(repo, generator, config);

        UserIdentityProvider userIdentity = new FileUserIdentityProvider(Path.of("user.uuid"));
        String user = userIdentity.getCurrentUserUuid();

        System.out.println("Current user: " + user);
        System.out.println("TTL seconds: " + config.ttlSeconds);
        System.out.println("Cleanup interval seconds: " + config.cleanupIntervalSeconds);

        // 1) Запускаем планировщик очистки
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(
                new ExpiredLinkCleaner(repo),
                config.cleanupIntervalSeconds,
                config.cleanupIntervalSeconds,
                TimeUnit.SECONDS
        );

        try {
            // 2) Создаём тестовую ссылку
            ShortLink link = service.create("https://google.com", user, 5);
            System.out.println("Created test link shortKey: " + link.getShortKey());
            System.out.println("Expires at: " + link.getExpiresAt());

            // 3) Ждём, чтобы TTL истёк и cleaner успел удалить
            // Рекомендуется ждать: TTL + 2 * cleanupInterval
            long waitSeconds = config.ttlSeconds + (config.cleanupIntervalSeconds * 2L);
            System.out.println("Waiting ~" + waitSeconds + " seconds to see cleanup...");
            Thread.sleep(waitSeconds * 1000L);

            // 4) Проверим, что ссылка пропала
            boolean exists = repo.findByShortKey(link.getShortKey()).isPresent();
            System.out.println("Link exists after waiting: " + exists);
            System.out.println("Links for user: " + repo.findByOwnerUuid(user).size());

        } catch (InterruptedException e) {
            System.out.println("Interrupted.");
            Thread.currentThread().interrupt();
        } finally {
            scheduler.shutdownNow();
            System.out.println("Scheduler stopped.");
        }
    }
}
