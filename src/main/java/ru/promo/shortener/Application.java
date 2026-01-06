package ru.promo.shortener;

import ru.promo.shortener.config.ApplicationConfig;
import ru.promo.shortener.config.ApplicationConfigLoader;
import ru.promo.shortener.core.model.ShortLink;
import ru.promo.shortener.core.service.ShortKeyGenerator;
import ru.promo.shortener.core.service.ShortLinkRepository;
import ru.promo.shortener.core.service.ShortLinkService;
import ru.promo.shortener.infra.InMemoryShortLinkRepository;
import ru.promo.shortener.infra.RandomShortKeyGenerator;

public class Application {

    public static void main(String[] args) {
        ApplicationConfig config = ApplicationConfigLoader.load();

        ShortLinkRepository repo = new InMemoryShortLinkRepository();
        ShortKeyGenerator generator = new RandomShortKeyGenerator();
        ShortLinkService service = new ShortLinkService(repo, generator, config);

        String owner1 = "user-uuid-1";
        String owner2 = "user-uuid-2";
        String url = "https://google.com";

        ShortLink a = service.create(url, owner1, 50);
        ShortLink b = service.create(url, owner2, 50);

        System.out.println("Owner1 shortKey: " + a.getShortKey());
        System.out.println("Owner2 shortKey: " + b.getShortKey());
        System.out.println("Different keys: " + !a.getShortKey().equals(b.getShortKey()));
    }
}
