package com.example.ticketmaster_mobile.whatsapp;

/**
 * Cola persistente de notificaciones (outbox). La implementación real vive en Room
 * ({@link RoomOutboxStore}); en pruebas se usa una versión en memoria.
 */
public interface OutboxStore {

    final class Entrada {
        public final long id;
        public final int idOrden;
        /** Número de intento ya incluido el actual (se incrementa al reclamar). */
        public final int intentos;

        public Entrada(long id, int idOrden, int intentos) {
            this.id = id;
            this.idOrden = idOrden;
            this.intentos = intentos;
        }
    }

    /**
     * Reclama de forma atómica la siguiente entrada vencida (PENDIENTE lista, o ENVIANDO con lease
     * expirado por un cierre inesperado), la pasa a ENVIANDO con un lease e incrementa Intentos.
     * Devuelve null si no hay nada que hacer.
     */
    Entrada reclamarSiguiente(long ahoraMs, long leaseMs);

    void marcarEnviado(long id, String mensajeId, long ahoraMs);

    /** Vuelve a PENDIENTE para {@code proximoIntentoMs}, fijando el contador de intentos. */
    void reprogramar(long id, long proximoIntentoMs, String error, int intentos);

    void marcarFallido(long id, String error);

    /** Momento más cercano en que habrá trabajo pendiente, o -1 si la cola está vacía. */
    long proximoTrabajoMs();
}
