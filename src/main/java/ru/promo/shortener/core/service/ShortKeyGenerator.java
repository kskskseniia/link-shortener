package ru.promo.shortener.core.service;

public interface ShortKeyGenerator {

    String generate(int length);

    default String generate() {
        return generate(6);
    }
}