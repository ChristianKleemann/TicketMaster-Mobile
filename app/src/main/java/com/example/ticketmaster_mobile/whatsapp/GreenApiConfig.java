package com.example.ticketmaster_mobile.whatsapp;

import java.util.Locale;

/**
 * Configuración inmutable de la conexión con GREEN-API (WhatsApp). Java puro, sin Android.
 */
public final class GreenApiConfig {

    public static final String API_URL_POR_DEFECTO = "https://api.green-api.com";

    public final String apiUrl;
    public final String idInstance;
    public final String apiToken;
    /** ChatId de WhatsApp ya normalizado, p. ej. "5213312345678@c.us". */
    public final String chatId;
    public final int connectTimeoutMs;
    public final int readTimeoutMs;
    /** Solo para pruebas con un servidor simulado. En producción se exige HTTPS. */
    public final boolean allowInsecureHttp;

    public GreenApiConfig(String apiUrl, String idInstance, String apiToken, String chatDestino,
                          int connectTimeoutMs, int readTimeoutMs, boolean allowInsecureHttp) {
        this.apiUrl = normalizarApiUrl(apiUrl);
        this.idInstance = soloDigitos(idInstance);
        this.apiToken = apiToken == null ? "" : apiToken.trim();
        this.chatId = normalizarChatId(chatDestino);
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.allowInsecureHttp = allowInsecureHttp;
    }

    /** Hay credenciales de la instancia (aunque no haya destino por defecto). */
    public boolean servidorConfigurado() {
        return !apiUrl.isEmpty() && !idInstance.isEmpty() && !apiToken.isEmpty();
    }

    /** Credenciales y destino; sin ellos la integración queda desactivada sin romper la app. */
    public boolean estaConfigurado() {
        return servidorConfigurado() && !chatId.isEmpty();
    }

    public boolean esHttps() {
        return apiUrl.toLowerCase(Locale.ROOT).startsWith("https://");
    }

    static String normalizarApiUrl(String url) {
        String u = (url == null || url.trim().isEmpty()) ? API_URL_POR_DEFECTO : url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private static String soloDigitos(String s) {
        if (s == null) return "";
        StringBuilder d = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') d.append(c);
        }
        return d.toString();
    }

    /**
     * Acepta "+52 33 1234 5678", "5213312345678", "5213312345678@c.us" o un grupo "...@g.us" y
     * devuelve el chatId. Devuelve "" si no es válido (entre 8 y 15 dígitos, E.164).
     */
    public static String normalizarChatId(String destino) {
        if (destino == null) return "";
        String d = destino.trim();
        if (d.isEmpty()) return "";
        if (d.endsWith("@g.us")) return d;
        if (d.endsWith("@c.us")) d = d.substring(0, d.length() - 5);
        StringBuilder digitos = new StringBuilder();
        for (int i = 0; i < d.length(); i++) {
            char c = d.charAt(i);
            if (c >= '0' && c <= '9') digitos.append(c);
            else if (c == '+' || c == ' ' || c == '-' || c == '(' || c == ')') continue;
            else return "";
        }
        int n = digitos.length();
        if (n < 8 || n > 15) return "";
        return digitos + "@c.us";
    }
}