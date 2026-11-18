package com.example.ticketmaster_mobile.integration.soporte;

import java.util.concurrent.Callable;

/** Espera activa (con tope de tiempo) para condiciones que dependen de procesos en segundo plano. */
public final class Esperar {

    private Esperar() {
    }

    /** true si la condición se cumplió antes de {@code timeoutMs}. */
    public static boolean hasta(long timeoutMs, Callable<Boolean> condicion) {
        long limite = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < limite) {
            try {
                if (Boolean.TRUE.equals(condicion.call())) return true;
            } catch (Exception ignorada) {
                // la condición aún no es evaluable (p. ej. la fila no existe todavía)
            }
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        try {
            return Boolean.TRUE.equals(condicion.call());
        } catch (Exception e) {
            return false;
        }
    }
}
