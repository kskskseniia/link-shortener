package ru.promo.shortener.cli;

import ru.promo.shortener.core.model.ShortLink;
import ru.promo.shortener.core.model.LinkStatus;
import ru.promo.shortener.core.service.ShortLinkRepository;
import ru.promo.shortener.core.service.ShortLinkService;
import ru.promo.shortener.core.service.exceptions.AccessDeniedException;
import ru.promo.shortener.core.service.exceptions.NotFoundException;
import ru.promo.shortener.core.service.exceptions.ValidationException;
import ru.promo.shortener.core.user.UserIdentityProvider;

import java.awt.Desktop;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ConsoleCli {

    private final ShortLinkService service;
    private final ShortLinkRepository repository;
    private final UserIdentityProvider users;

    public ConsoleCli(ShortLinkService service,
                      ShortLinkRepository repository,
                      UserIdentityProvider users) {
        this.service = service;
        this.repository = repository;
        this.users = users;
    }

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private String shortStatus(LinkStatus status) {
        return switch (status) {
            case ACTIVE -> "ACTIVE";
            case EXPIRED_BY_CLICKS -> "CLICKS";
            case EXPIRED_BY_TTL -> "TTL";
            case DELETED -> "DELETED";
        };
    }

    public void run() {
        System.out.println("=== Link Shortener CLI ===");
        System.out.println("Current user: " + users.getCurrentUserUuid());
        System.out.println("Type 'help' for commands.");

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\s+");
                String cmd = parts[0].toLowerCase();

                if (cmd.equals("exit")) {
                    System.out.println("Bye!");
                    return;
                }

                if (cmd.equals("help")) {
                    printHelp();
                    continue;
                }

                try {
                    dispatch(cmd, parts);
                } catch (ValidationException e) {
                    System.out.println("Input error: " + e.getMessage());
                } catch (NotFoundException e) {
                    System.out.println("Not found: " + e.getMessage());
                } catch (AccessDeniedException e) {
                    System.out.println("Access denied: " + e.getMessage());
                } catch (NumberFormatException e) {
                    System.out.println("Input error: number expected");
                } catch (Exception e) {
                    System.out.println("Unexpected error: " + e.getMessage());
                }
            }
        }
    }

    private void dispatch(String cmd, String[] parts) throws Exception {
        switch (cmd) {
            case "whoami" -> handleWhoAmI();
            case "new-user" -> handleNewUser();
            case "switch-user" -> handleSwitchUser(parts);

            case "create" -> handleCreate(parts);
            case "open" -> handleOpen(parts);
            case "list" -> handleList();
            case "list-all" -> handleListAll();
            case "set-limit" -> handleSetLimit(parts);
            case "delete" -> handleDelete(parts);

            default -> System.out.println("Unknown command. Type 'help'.");
        }
    }

    private String currentUser() {
        return users.getCurrentUserUuid();
    }

    // -------- users --------

    private void handleWhoAmI() {
        System.out.println("Current user: " + currentUser());
    }

    private void handleNewUser() {
        String uuid = users.createNewUser();
        System.out.println("Created and switched to new user: " + uuid);
    }

    private void handleSwitchUser(String[] parts) {
        if (parts.length != 2) {
            System.out.println("Usage: switch-user <uuid>");
            return;
        }
        users.switchUser(parts[1]);
        System.out.println("Switched to user: " + currentUser());
    }

    // -------- links --------

    private void handleCreate(String[] parts) {
        if (parts.length < 2) {
            System.out.println("Usage: create <url> [maxClicks]");
            return;
        }

        String url = parts[1];

        ShortLink link;
        if (parts.length >= 3) {
            int maxClicks = Integer.parseInt(parts[2]);
            link = service.create(url, currentUser(), maxClicks);
        } else {
            link = service.create(url, currentUser());
        }

        System.out.println("Created short link:");
        System.out.println("  shortKey: " + link.getShortKey());
        System.out.println("  original: " + link.getOriginalUrl());
        System.out.println("  maxClicks: " + link.getMaxClicks());
        System.out.println("  expiresAt: " + TIME_FORMAT.format(link.getExpiresAt()));
    }

    private void handleOpen(String[] parts) throws Exception {
        if (parts.length != 2) {
            System.out.println("Usage: open <shortKey>");
            return;
        }

        String shortKey = parts[1];
        String url = service.resolve(shortKey);

        System.out.println("Open: " + url);

        // На сервере Desktop может быть недоступен — тогда просто печатаем URL.
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(new URI(url));
        } else {
            System.out.println("(Desktop is not supported, URL printed only)");
        }
    }

    private void handleList() {
        List<ShortLink> links = repository.findByOwnerUuid(currentUser());
        if (links.isEmpty()) {
            System.out.println("No links for current user.");
            return;
        }

        for (ShortLink l : links) {
            System.out.println("- " + l.getShortKey()
                    + " -> " + l.getOriginalUrl()
                    + " | clicks: " + l.getClicks() + "/" + l.getMaxClicks()
                    + " | status: " + l.getStatus());
        }
    }

    private void handleListAll() {
        List<ShortLink> links = repository.findAll();

        if (links.isEmpty()) {
            System.out.println("No links in system.");
            return;
        }

        // Чтобы вывод был стабильный и красивый
        links = links.stream()
                .sorted(Comparator
                        .comparing(ShortLink::getOwnerUuid)
                        .thenComparing(ShortLink::getShortKey))
                .toList();

        // Заголовок
        System.out.printf(
                "%-36s  %-10s  %-7s  %-12s  %-19s  %s%n",
                "OWNER UUID", "SHORTKEY", "STATUS", "CLICKS", "EXPIRES AT", "ORIGINAL"
        );
        System.out.println("-".repeat(36 + 2 + 10 + 2 + 7 + 2 + 12 + 2 + 19 + 2 + 30));

        for (ShortLink l : links) {
            String clicks = l.getClicks() + "/" + l.getMaxClicks();
            String expires = TIME_FORMAT.format(l.getExpiresAt());

            // Чтобы длинные URL не ломали консоль
            String original = l.getOriginalUrl();
            if (original.length() > 60) {
                original = original.substring(0, 57) + "...";
            }

            System.out.printf(
                    "%-36s  %-10s  %-7s  %-12s  %-19s  %s%n",
                    l.getOwnerUuid(),
                    l.getShortKey(),
                    shortStatus(l.getStatus()),
                    clicks,
                    expires,
                    original
            );
        }
    }


    private void handleSetLimit(String[] parts) {
        if (parts.length != 3) {
            System.out.println("Usage: set-limit <shortKey> <newMaxClicks>");
            return;
        }

        String shortKey = parts[1];
        int newMaxClicks = Integer.parseInt(parts[2]);

        ShortLink updated = service.updateMaxClicks(shortKey, currentUser(), newMaxClicks);
        System.out.println("Updated: " + updated.getShortKey() + " maxClicks=" + updated.getMaxClicks());

        if (updated.getStatus() == LinkStatus.EXPIRED_BY_CLICKS) {
            System.out.println("Notice: link is now unavailable because clicks (" +
                    updated.getClicks() + ") already reached/exceeded the new limit.");
        }
    }

    private void handleDelete(String[] parts) {
        if (parts.length != 2) {
            System.out.println("Usage: delete <shortKey>");
            return;
        }

        boolean deleted = service.deleteByOwner(parts[1], currentUser());
        System.out.println(deleted ? "Deleted " + parts[1] + '.': "Nothing deleted.");
    }

    private void printHelp() {
        System.out.println("""
                User:
                  whoami
                  new-user
                  switch-user <uuid>

                Links:
                  create <url> [maxClicks]
                  open <shortKey>
                  list         (debug) mini
                  list-all
                  set-limit <shortKey> <newMaxClicks>
                  delete <shortKey>

                Other:
                  help
                  exit
                """);
    }
}
