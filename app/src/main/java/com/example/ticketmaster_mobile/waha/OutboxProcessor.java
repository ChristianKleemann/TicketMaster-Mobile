package com.example.ticketmaster_mobile.waha;

/**
 * Vacía la cola de notificaciones: por cada entrada vencida lee el ticket de la DB, arma el mensaje
 * y lo envía a WAHA, aplicando reintentos con backoff, un circuit breaker y el límite de intentos.
 * Java puro (todas las dependencias son interfaces) para poder probarlo sin Android ni red.
 *
 * Semántica de entrega: AL MENOS UNA VEZ. Si WAHA acepta el mensaje pero la respuesta se pierde, el
 * reintento puede duplicarlo; el texto incluye la referencia de la orden para que sea reconocible.
 * A cambio nunca se pierde una notificación.
 */
public final class OutboxProcessor {

    public static final int MAX_INTENTOS = 10;
    public static final long LEASE_MS = 2 * 60_000L;
    public static final long ESPERA_MIN_INFRA_MS = 5 * 60_000L;

    public static final class Resumen {
        public int enviados;
        public int reprogramados;
        public int fallidos;
        public boolean circuitoAbierto;
    }

    public interface Reloj {
        long ahoraMs();
    }

    private final OutboxStore store;
    private final TicketSource tickets;
    private final TextSender sender;
    private final Backoff backoff;
    private final CircuitBreaker breaker;
    private final Reloj reloj;
    private final int maxIntentos;

    public OutboxProcessor(OutboxStore store, TicketSource tickets, TextSender sender,
                           Backoff backoff, CircuitBreaker breaker, Reloj reloj, int maxIntentos) {
        this.store = store;
        this.tickets = tickets;
        this.sender = sender;
        this.backoff = backoff;
        this.breaker = breaker;
        this.reloj = reloj;
        this.maxIntentos = maxIntentos;
    }

    public Resumen procesar() {
        Resumen res = new Resumen();
        while (true) {
            if (!breaker.permiteIntento()) {
                res.circuitoAbierto = true;
                return res;
            }
            long ahora = reloj.ahoraMs();
            OutboxStore.Entrada e = store.reclamarSiguiente(ahora, LEASE_MS);
            if (e == null) return res;
            procesarEntrada(e, res);
        }
    }

    private void procesarEntrada(OutboxStore.Entrada e, Resumen res) {
        EnvioResultado r;
        try {
            TicketResumen t = tickets.cargar(e.idOrden);
            if (t == null) {
                store.marcarFallido(e.id, "La orden no existe o no tiene un pago aprobado");
                res.fallidos++;
                return;
            }
            r = sender.enviarTexto(t.chatId, MensajeTicketFormatter.formatear(t));
        } catch (RuntimeException ex) {
            // Un error inesperado (p. ej. de lectura de la DB) no debe dejar la entrada colgada
            r = EnvioResultado.reintentar(0, "Error interno: " + ex.getClass().getSimpleName(), 0);
        }

        long ahora = reloj.ahoraMs();
        switch (r.tipo) {
            case EXITO:
                breaker.registrarExito();
                store.marcarEnviado(e.id, r.mensajeId, ahora);
                res.enviados++;
                break;

            case PERMANENTE:
                // El servidor respondió con sentido: la infraestructura está bien
                breaker.registrarExito();
                store.marcarFallido(e.id, r.toString());
                res.fallidos++;
                break;

            case REINTENTAR_SIN_PENALIZAR:
                breaker.registrarFallo();
                // WAHA/sesión caída: espera mínima larga para no sondear en bucle y no gastar batería
                store.reprogramar(e.id, ahora + Math.max(ESPERA_MIN_INFRA_MS, esperar(e.intentos, r)),
                        r.toString(), Math.max(0, e.intentos - 1));
                res.reprogramados++;
                break;

            case REINTENTAR:
            default:
                breaker.registrarFallo();
                if (e.intentos >= maxIntentos) {
                    store.marcarFallido(e.id, "Se agotaron los " + maxIntentos + " intentos. Último: " + r);
                    res.fallidos++;
                } else {
                    store.reprogramar(e.id, ahora + esperar(e.intentos, r), r.toString(), e.intentos);
                    res.reprogramados++;
                }
                break;
        }
    }

    private long esperar(int intentos, EnvioResultado r) {
        return Math.max(backoff.esperaMs(Math.max(1, intentos)), r.reintentarDespuesMs);
    }
}
