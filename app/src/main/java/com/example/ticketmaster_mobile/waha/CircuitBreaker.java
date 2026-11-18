package com.example.ticketmaster_mobile.waha;

/**
 * Interruptor de circuito: tras {@code umbral} fallos consecutivos de infraestructura se "abre" y
 * deja de golpear a WAHA durante {@code aperturaMs}; luego permite una sola petición de prueba
 * (half-open). Un éxito lo cierra. Seguro para varios hilos.
 */
public final class CircuitBreaker {

    public interface Reloj {
        long ahoraMs();
    }

    private final int umbral;
    private final long aperturaMs;
    private final Reloj reloj;
    private int fallosConsecutivos = 0;
    private long abiertoHasta = 0;

    public CircuitBreaker(int umbral, long aperturaMs, Reloj reloj) {
        this.umbral = umbral;
        this.aperturaMs = aperturaMs;
        this.reloj = reloj;
    }

    /** true si se puede intentar una petición ahora (cerrado, o half-open tras la pausa). */
    public synchronized boolean permiteIntento() {
        if (fallosConsecutivos < umbral) return true;
        long ahora = reloj.ahoraMs();
        if (ahora >= abiertoHasta) {
            // half-open: una sola prueba; si falla se reabre completo
            abiertoHasta = ahora + aperturaMs;
            return true;
        }
        return false;
    }

    /** Milisegundos restantes con el circuito abierto (0 si permite intentar). */
    public synchronized long msHastaReintento() {
        if (fallosConsecutivos < umbral) return 0;
        return Math.max(0, abiertoHasta - reloj.ahoraMs());
    }

    public synchronized void registrarExito() {
        fallosConsecutivos = 0;
        abiertoHasta = 0;
    }

    public synchronized void registrarFallo() {
        fallosConsecutivos++;
        if (fallosConsecutivos >= umbral) abiertoHasta = reloj.ahoraMs() + aperturaMs;
    }

    public synchronized boolean estaAbierto() {
        return fallosConsecutivos >= umbral && reloj.ahoraMs() < abiertoHasta;
    }
}
