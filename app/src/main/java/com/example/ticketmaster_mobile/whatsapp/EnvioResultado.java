package com.example.ticketmaster_mobile.whatsapp;

/** Resultado clasificado de un intento de envío a WAHA. */
public final class EnvioResultado {

    public enum Tipo {
        /** WAHA aceptó el mensaje (2xx). */
        EXITO,
        /** Fallo transitorio (timeout, 5xx, 429...). Reintentar con backoff; cuenta como intento. */
        REINTENTAR,
        /**
         * Problema de infraestructura/configuración (sesión de WhatsApp no lista, API key inválida,
         * certificado, circuito abierto). Reintentar más tarde SIN consumir intentos: no es culpa
         * del mensaje, y no debe agotarse por una caída larga de WAHA.
         */
        REINTENTAR_SIN_PENALIZAR,
        /** El mensaje nunca será aceptado tal cual (4xx de validación). No reintentar. */
        PERMANENTE
    }

    public final Tipo tipo;
    public final int httpStatus;
    public final String mensajeId;
    public final String detalle;
    /** Espera mínima pedida por el servidor (Retry-After) en ms; 0 si no aplica. */
    public final long reintentarDespuesMs;

    private EnvioResultado(Tipo tipo, int httpStatus, String mensajeId, String detalle, long reintentarDespuesMs) {
        this.tipo = tipo;
        this.httpStatus = httpStatus;
        this.mensajeId = mensajeId;
        this.detalle = detalle;
        this.reintentarDespuesMs = reintentarDespuesMs;
    }

    public static EnvioResultado exito(int http, String mensajeId) {
        return new EnvioResultado(Tipo.EXITO, http, mensajeId, null, 0);
    }

    public static EnvioResultado reintentar(int http, String detalle, long retryAfterMs) {
        return new EnvioResultado(Tipo.REINTENTAR, http, null, recortar(detalle), retryAfterMs);
    }

    public static EnvioResultado sinPenalizar(int http, String detalle, long retryAfterMs) {
        return new EnvioResultado(Tipo.REINTENTAR_SIN_PENALIZAR, http, null, recortar(detalle), retryAfterMs);
    }

    public static EnvioResultado permanente(int http, String detalle) {
        return new EnvioResultado(Tipo.PERMANENTE, http, null, recortar(detalle), 0);
    }

    /** Los errores se guardan en la DB y pueden mostrarse: se acotan y nunca incluyen secretos. */
    private static String recortar(String s) {
        if (s == null) return null;
        String limpio = s.replace('\n', ' ').replace('\r', ' ').trim();
        return limpio.length() > 240 ? limpio.substring(0, 240) + "…" : limpio;
    }

    @Override
    public String toString() {
        return tipo + (httpStatus > 0 ? " HTTP " + httpStatus : "") + (detalle != null ? " · " + detalle : "");
    }
}
