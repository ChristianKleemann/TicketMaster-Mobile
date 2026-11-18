package com.example.ticketmaster_mobile.waha;

import java.util.Locale;

/**
 * Configuración inmutable de la conexión con WAHA (WhatsApp HTTP API). Java puro, sin Android.
 *
 * El valor real se inyecta en tiempo de compilación desde local.properties / variables de entorno
 * (ver app/build.gradle.kts); nunca se versiona la API key.
 */
public final class WahaConfig {

    public final String baseUrl;
    public final String apiKey;
    public final String session;
    /** ChatId de WhatsApp ya normalizado, p. ej. "5213312345678@c.us". */
    public final String chatId;
    public final int connectTimeoutMs;
    public final int readTimeoutMs;
    /** Solo para desarrollo (emulador / LAN). En release debe ser false: se exige HTTPS. */
    public final boolean allowInsecureHttp;

    public WahaConfig(String baseUrl, String apiKey, String session, String chatDestino,
                      int connectTimeoutMs, int readTimeoutMs, boolean allowInsecureHttp) {
        this.baseUrl = normalizarBaseUrl(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.session = (session == null || session.trim().isEmpty()) ? "default" : session.trim();
        this.chatId = normalizarChatId(chatDestino);
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.allowInsecureHttp = allowInsecureHttp;
    }

    /** true si hay URL y destino; sin ellos la integración queda desactivada sin romper la app. */
    public boolean estaConfigurado() {
        return !baseUrl.isEmpty() && !chatId.isEmpty();
    }

    /**
     * true si hay URL de WAHA. El destino ya no es obligatorio: cada usuario vincula su propio número
     * y {@link #chatId} (waha.chatDestino) queda solo como número de respaldo para desarrollo.
     */
    public boolean servidorConfigurado() {
        return !baseUrl.isEmpty();
    }

    public boolean esHttps() {
        return baseUrl.toLowerCase(Locale.ROOT).startsWith("https://");
    }

    static String normalizarBaseUrl(String url) {
        if (url == null) return "";
        String u = url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    /**
     * Acepta "+52 33 1234 5678", "5213312345678", "5213312345678@c.us" o un grupo "...@g.us" y
     * devuelve el chatId de WAHA. Devuelve "" si no es válido (entre 8 y 15 dígitos, E.164).
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
