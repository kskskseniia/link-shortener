package ru.promo.shortener;

import ru.promo.shortener.cli.ConsoleCli;
import ru.promo.shortener.config.ApplicationConfig;
import ru.promo.shortener.config.ApplicationConfigLoader;
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

        UserIdentityProvider users = new FileUserIdentityProvider();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(
                new ExpiredLinkCleaner(repo),
                config.cleanupIntervalSeconds,
                config.cleanupIntervalSeconds,
                TimeUnit.SECONDS
        );

        try {
            new ConsoleCli(service, repo, users).run();
        } finally {
            scheduler.shutdownNow();
        }
    }
}
