package com.example.ticketmaster_mobile.whatsapp;

import java.util.Random;

/** Backoff exponencial con jitter (±20 %) para no sincronizar reintentos. Java puro. */
public final class Backoff {

    public final long baseMs;
    public final long maxMs;
    private final Random random;

    public Backoff(long baseMs, long maxMs, Random random) {
        this.baseMs = baseMs;
        this.maxMs = maxMs;
        this.random = random;
    }

    public static Backoff porDefecto() {
        return new Backoff(30_000L, 60 * 60_000L, new Random());
    }

    /** Espera antes del reintento tras el intento número {@code intento} (1 = primer fallo). */
    public long esperaMs(int intento) {
        int exp = Math.max(0, Math.min(intento - 1, 20));
        long base = Math.min(maxMs, baseMs * (1L << exp));
        double factor = 0.8 + random.nextDouble() * 0.4;
        return Math.min(maxMs, Math.max(1L, (long) (base * factor)));
    }
}
