package ru.promo.shortener.infra;

import ru.promo.shortener.core.service.ShortKeyGenerator;

import java.security.SecureRandom;

public class RandomShortKeyGenerator implements ShortKeyGenerator {

    private static final String SYMBOLS =
            "abcdefghijklmnopqrstuvwxyz" +
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                    "0123456789";

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate(int length) {
        if (length <= 0) throw new IllegalArgumentException("length must be positive");

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(SYMBOLS.length());
            sb.append(SYMBOLS.charAt(index));
        }
        return sb.toString();
    }
}
