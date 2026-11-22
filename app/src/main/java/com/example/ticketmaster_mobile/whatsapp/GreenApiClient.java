package com.example.ticketmaster_mobile.whatsapp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLException;

/**
 * Cliente mínimo de GREEN-API (WhatsApp). Solo java.net, sin dependencias.
 *
 *   POST {apiUrl}/waInstance{idInstance}/sendMessage/{apiToken}
 *   {"chatId":"5213312345678@c.us","message":"..."}   →   {"idMessage":"..."}
 *
 * Seguridad: el token viaja en la URL (así lo define GREEN-API), por lo que NUNCA se escribe en
 * errores, logs ni en la base de datos. Exige HTTPS, no sigue redirecciones y limita el tamaño
 * de la respuesta.
 */
public class GreenApiClient implements TextSender {

    private static final int MAX_RESPUESTA_BYTES = 64 * 1024;
    private static final int MAX_MENSAJE = 20_000; // límite de GREEN-API

    private static final Pattern ID_MENSAJE =
            Pattern.compile("\"idMessage\"\\s*:\\s*\"([^\"]{1,200})\"");

    private final GreenApiConfig config;

    public GreenApiClient(GreenApiConfig config) {
        this.config = config;
    }

    /** Envía al destino por defecto de la configuración. */
    @Override
    public EnvioResultado enviarTexto(String texto) {
        return enviarTexto(config.chatId, texto);
    }

    /** Envía a un chat concreto ("5213312345678", "+52…" o "…@c.us"); vacío = destino por defecto. */
    public EnvioResultado enviarTexto(String chatDestino, String texto) {
        if (!config.servidorConfigurado()) {
            return EnvioResultado.sinPenalizar(0, "Falta configurar GREEN-API (idInstance o token)", 0);
        }
        if (!config.esHttps() && !config.allowInsecureHttp) {
            return EnvioResultado.sinPenalizar(0, "La URL de GREEN-API debe usar HTTPS", 0);
        }
        String chat = GreenApiConfig.normalizarChatId(chatDestino);
        if (chat.isEmpty()) {
            if (chatDestino != null && !chatDestino.trim().isEmpty()) {
                return EnvioResultado.permanente(0, "Número de destino inválido");
            }
            chat = config.chatId;
        }
        if (chat.isEmpty()) {
            return EnvioResultado.permanente(0, "No hay número de WhatsApp de destino");
        }
        if (texto == null || texto.isEmpty()) {
            return EnvioResultado.permanente(0, "Mensaje vacío");
        }
        if (texto.length() > MAX_MENSAJE) texto = texto.substring(0, MAX_MENSAJE);

        String cuerpo = "{\"chatId\":" + json(chat) + ",\"message\":" + json(texto) + "}";
        String url = config.apiUrl + "/waInstance" + config.idInstance + "/sendMessage/" + config.apiToken;

        Respuesta r;
        try {
            r = post(url, cuerpo);
        } catch (SSLException e) {
            return EnvioResultado.sinPenalizar(0, "Error TLS con GREEN-API: " + e.getClass().getSimpleName(), 0);
        } catch (SocketTimeoutException e) {
            return EnvioResultado.reintentar(0, "GREEN-API no respondió a tiempo (la entrega pudo ocurrir)", 0);
        } catch (IOException e) {
            // Solo el nombre de la clase: el mensaje de la excepción puede contener la URL (con el token)
            return EnvioResultado.reintentar(0, "Error de red: " + e.getClass().getSimpleName(), 0);
        } catch (RuntimeException e) {
            return EnvioResultado.permanente(0, "Petición inválida: " + e.getClass().getSimpleName());
        }
        return clasificar(r);
    }

    // ------------------------------------------------------------------ clasificación

    private EnvioResultado clasificar(Respuesta r) {
        int s = r.status;
        if (s >= 200 && s < 300) return EnvioResultado.exito(s, extraerIdMensaje(r.cuerpo));

        String cuerpo = r.cuerpo == null ? "" : r.cuerpo.toLowerCase(Locale.ROOT);

        if (s == 401 || s == 403) {
            return EnvioResultado.sinPenalizar(s,
                    "GREEN-API rechazó las credenciales (revisa idInstance y token en local.properties)", 0);
        }
        if (s == 400) {
            // Instancia sin QR escaneado, arrancando, vencida o borrada: es del servicio, no del mensaje
            if (cuerpo.contains("not authorized") || cuerpo.contains("starting")) {
                return EnvioResultado.sinPenalizar(s,
                        "La instancia de GREEN-API no está lista: escanea el QR en la consola o espera", 0);
            }
            if (cuerpo.contains("expired") || cuerpo.contains("deleted")) {
                return EnvioResultado.sinPenalizar(s, "La instancia de GREEN-API está vencida o fue eliminada", 0);
            }
            return EnvioResultado.permanente(s, "GREEN-API rechazó el mensaje (datos inválidos)");
        }
        if (s == 466) {
            return EnvioResultado.permanente(s,
                    "GREEN-API: se alcanzó un límite del plan (el plan gratuito permite 3 chats)");
        }
        if (s == 429) {
            return EnvioResultado.reintentar(s, "GREEN-API limita la tasa de envío", r.retryAfterMs);
        }
        if (s >= 500 || s == 408 || s == 499) {
            return EnvioResultado.reintentar(s, "GREEN-API respondió HTTP " + s, r.retryAfterMs);
        }
        if (s >= 300 && s < 400) {
            return EnvioResultado.sinPenalizar(s, "GREEN-API respondió con redirección; no se sigue por seguridad", 0);
        }
        return EnvioResultado.permanente(s, "GREEN-API rechazó la petición (HTTP " + s + ")");
    }

    static String extraerIdMensaje(String cuerpo) {
        if (cuerpo == null) return null;
        Matcher m = ID_MENSAJE.matcher(cuerpo);
        return m.find() ? m.group(1) : null;
    }

    // ------------------------------------------------------------------ HTTP

    private static final class Respuesta {
        final int status;
        final String cuerpo;
        final long retryAfterMs;

        Respuesta(int status, String cuerpo, long retryAfterMs) {
            this.status = status;
            this.cuerpo = cuerpo;
            this.retryAfterMs = retryAfterMs;
        }
    }

    private Respuesta post(String url, String cuerpoJson) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setRequestMethod("POST");
            con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(config.connectTimeoutMs);
            con.setReadTimeout(config.readTimeoutMs);
            con.setUseCaches(false);
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("User-Agent", "TicketMaster-Mobile/1.0");
            byte[] bytes = cuerpoJson.getBytes(StandardCharsets.UTF_8);
            con.setDoOutput(true);
            con.setFixedLengthStreamingMode(bytes.length);
            con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream os = con.getOutputStream()) {
                os.write(bytes);
            }
            int status = con.getResponseCode();
            InputStream in = status >= 400 ? con.getErrorStream() : con.getInputStream();
            return new Respuesta(status, in == null ? "" : leerAcotado(in),
                    parsearRetryAfter(con.getHeaderField("Retry-After")));
        } finally {
            con.disconnect();
        }
    }

    private static long parsearRetryAfter(String valor) {
        if (valor == null) return 0;
        try {
            long seg = Long.parseLong(valor.trim());
            return seg < 0 ? 0 : Math.min(seg, 3600) * 1000L;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String leerAcotado(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int leidos;
            while ((leidos = is.read(buf)) != -1) {
                if (out.size() + leidos > MAX_RESPUESTA_BYTES) {
                    out.write(buf, 0, MAX_RESPUESTA_BYTES - out.size());
                    break;
                }
                out.write(buf, 0, leidos);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String json(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
