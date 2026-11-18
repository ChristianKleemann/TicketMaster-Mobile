package com.example.ticketmaster_mobile.waha;

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
 * Cliente mínimo y robusto de WAHA. Solo java.net (sin dependencias), por lo que es testeable en JVM.
 *
 *  - POST {base}/api/sendText  {session, chatId, text}  con cabecera X-Api-Key.
 *  - GET  {base}/api/sessions/{session}                  para distinguir "sesión de WhatsApp no
 *    lista" de un fallo propio del mensaje.
 *
 * Garantías de seguridad: exige HTTPS (salvo allowInsecureHttp en debug), no sigue redirecciones
 * (no reenviar la API key a otro host), limita el tamaño de la respuesta, y nunca escribe la API key
 * ni el texto del mensaje en errores/logs.
 */
public class WahaClient implements TextSender {

    private static final int MAX_RESPUESTA_BYTES = 64 * 1024;
    private static final long MAX_RETRY_AFTER_MS = 60 * 60 * 1000L;
    private static final Pattern ID_STRING = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]{1,200})\"");
    private static final Pattern ID_SERIALIZED = Pattern.compile("\"_serialized\"\\s*:\\s*\"([^\"]{1,200})\"");
    private static final Pattern MENSAJE_ERROR =
            Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.){1,300})\"");
    private static final Pattern STATUS = Pattern.compile("\"status\"\\s*:\\s*\"([A-Z_]{1,40})\"");
    private static final Pattern NUMERO_EXISTE = Pattern.compile("\"numberExists\"\\s*:\\s*(true|false)");
    private static final Pattern CHAT_ID = Pattern.compile("\"chatId\"\\s*:\\s*\"([^\"]{1,100})\"");

    private final WahaConfig config;

    public WahaClient(WahaConfig config) {
        this.config = config;
    }

    @Override
    public EnvioResultado enviarTexto(String texto) {
        return enviarTexto(null, texto);
    }

    @Override
    public EnvioResultado enviarTexto(String chatId, String texto) {
        String destino = destinoEfectivo(chatId);
        EnvioResultado invalido = validarEnvio(destino);
        if (invalido != null) return invalido;
        String cuerpo = "{\"session\":" + jsonString(config.session)
                + ",\"chatId\":" + jsonString(destino)
                + ",\"text\":" + jsonString(texto) + "}";
        return post("/api/sendText", cuerpo);
    }

    private String destinoEfectivo(String chatId) {
        return (chatId == null || chatId.trim().isEmpty()) ? config.chatId : chatId.trim();
    }

    /** Validaciones previas al envío; null si todo está en orden. */
    private EnvioResultado validarEnvio(String destino) {
        if (!config.servidorConfigurado()) {
            return EnvioResultado.sinPenalizar(0, "WAHA no está configurado (URL vacía)", 0);
        }
        if (config.apiKey.isEmpty()) {
            return EnvioResultado.sinPenalizar(0, "Falta la API key de WAHA", 0);
        }
        if (!config.esHttps() && !config.allowInsecureHttp) {
            return EnvioResultado.sinPenalizar(0, "La URL de WAHA debe usar HTTPS", 0);
        }
        if (destino == null || destino.isEmpty()) {
            return EnvioResultado.permanente(0, MensajesWhatsapp.SIN_NUMERO);
        }
        return null;
    }

    private EnvioResultado post(String ruta, String cuerpo) {
        Respuesta r;
        try {
            r = llamar("POST", config.baseUrl + ruta, cuerpo);
        } catch (SSLException e) {
            return EnvioResultado.sinPenalizar(0, "Error TLS con WAHA: " + e.getClass().getSimpleName(), 0);
        } catch (TimeoutDeLectura e) {
            // La petición YA se envió y no llegó respuesta: pudo llegar al servidor, así que
            // reintentar puede duplicar el mensaje (WAHA no ofrece idempotency-key).
            return EnvioResultado.reintentar(0,
                    "WAHA no respondió a tiempo tras recibir la petición (la entrega pudo ocurrir)", 0);
        } catch (SocketTimeoutException e) {
            // Timeout al CONECTAR: la petición nunca salió; no hay riesgo de duplicado. Suele ser
            // firewall/aislamiento de red o IP incorrecta (los paquetes se pierden en silencio).
            return EnvioResultado.reintentar(0,
                    "No se pudo conectar con WAHA: tiempo de conexión agotado. Revisa IP/URL, firewall y red", 0);
        } catch (IOException e) {
            return EnvioResultado.reintentar(0, "Error de red: " + e.getClass().getSimpleName(), 0);
        } catch (RuntimeException e) {
            return EnvioResultado.permanente(0, "Petición inválida: " + e.getClass().getSimpleName());
        }
        return clasificar(r);
    }

    private EnvioResultado clasificar(Respuesta r) {
        int s = r.status;
        if (s >= 200 && s < 300) return EnvioResultado.exito(s, extraerIdMensaje(r.cuerpo));
        String motivo = extraerMotivoError(r.cuerpo);

        // El destino no tiene identificador de WhatsApp (LID): es un problema del número configurado
        // (p. ej. falta el "1" de 521 en México). Reintentar nunca lo arreglará.
        if (motivo != null && motivo.contains("No LID for user")) {
            return EnvioResultado.permanente(s, "WAHA: " + motivo + ". Revisa el número de destino "
                    + "(para México suele ser 521 + 10 dígitos) o usa el @lid de la sesión");
        }

        if (s == 401 || s == 403) {
            return EnvioResultado.sinPenalizar(s, "WAHA rechazó la API key (HTTP " + s + ")", 0);
        }
        if (s >= 300 && s < 400) {
            return EnvioResultado.sinPenalizar(s, "WAHA respondió con redirección; no se sigue por seguridad", 0);
        }
        if (s == 429) {
            return EnvioResultado.reintentar(s, "WAHA limita la tasa (429)", r.retryAfterMs);
        }
        if (s == 408 || s == 425 || s == 404 || s == 422 || s >= 500) {
            // 404/422/5xx son típicos de "sesión no iniciada / sin escanear QR": lo comprobamos.
            String estado = consultarEstadoSesion();
            if (estado != null && !"WORKING".equals(estado)) {
                return EnvioResultado.sinPenalizar(s,
                        "Sesión de WhatsApp no lista (estado " + estado + ")", r.retryAfterMs);
            }
            if (s == 404 && estado == null) {
                return EnvioResultado.reintentar(s, "Recurso no encontrado en WAHA (404)", r.retryAfterMs);
            }
            return EnvioResultado.reintentar(s, "WAHA respondió HTTP " + s + conMotivo(motivo), r.retryAfterMs);
        }
        // 400, 413, 415... : el mensaje o el destino son inválidos; reintentar no ayuda
        return EnvioResultado.permanente(s, "WAHA rechazó la petición (HTTP " + s + ")" + conMotivo(motivo));
    }

    private static String conMotivo(String motivo) {
        return motivo == null ? "" : ": " + motivo;
    }

    /**
     * Extrae SOLO el mensaje de la excepción de WAHA (p. ej. "No LID for user"). El cuerpo de error
     * de WAHA repite la petición completa, incluido el texto del ticket, así que nunca se guarda
     * entero; el mensaje se recorta y se limpia antes de persistirlo.
     */
    static String extraerMotivoError(String cuerpo) {
        if (cuerpo == null) return null;
        Matcher m = MENSAJE_ERROR.matcher(cuerpo);
        if (!m.find()) return null;
        String msg = m.group(1).replace("\\n", " ").replace("\\\"", "\"");
        int url = msg.indexOf("(http");
        if (url > 0) msg = msg.substring(0, url);
        msg = msg.trim();
        if (msg.length() > 100) msg = msg.substring(0, 100) + "…";
        return msg.isEmpty() ? null : msg;
    }

    /** Resultado de comprobar WAHA (y, opcionalmente, un número) con un mensaje listo para el usuario. */
    public static final class Diagnostico {
        public enum Tipo {
            OK, NO_CONFIGURADO, SIN_API_KEY, SIN_CONEXION, API_KEY_INVALIDA,
            SESION_NO_LISTA, NUMERO_NO_EXISTE, ERROR
        }

        public final Tipo tipo;
        /** chatId canónico que devuelve WAHA para el número (solo si tipo == OK y se verificó un número). */
        public final String chatId;
        public final String mensaje;

        Diagnostico(Tipo tipo, String chatId, String mensaje) {
            this.tipo = tipo;
            this.chatId = chatId;
            this.mensaje = mensaje;
        }

        public boolean esOk() {
            return tipo == Tipo.OK;
        }
    }

    /**
     * Comprueba, en orden: configuración, conexión, API key, estado de la sesión de WhatsApp y, si se
     * pasa un número (solo dígitos, p. ej. "5213322180006"), que ese número exista en WhatsApp.
     * Debe llamarse fuera del hilo principal. Nunca lanza excepciones.
     */
    public Diagnostico diagnosticar(String telefonoDigitos) {
        if (!config.servidorConfigurado()) {
            return new Diagnostico(Diagnostico.Tipo.NO_CONFIGURADO, null,
                    "La conexión con WAHA no está configurada (falta waha.baseUrl al compilar la app).");
        }
        if (config.apiKey.isEmpty()) {
            return new Diagnostico(Diagnostico.Tipo.SIN_API_KEY, null,
                    "Falta la clave de WAHA (waha.apiKey) al compilar la app.");
        }
        try {
            Respuesta s = llamar("GET", config.baseUrl + "/api/sessions/" + urlSeguro(config.session), null);
            if (s.status == 401 || s.status == 403) {
                return new Diagnostico(Diagnostico.Tipo.API_KEY_INVALIDA, null,
                        "WAHA rechazó la clave (waha.apiKey). Debe ser igual a WAHA_API_KEY.");
            }
            if (s.status < 200 || s.status >= 300) {
                return new Diagnostico(Diagnostico.Tipo.ERROR, null,
                        "WAHA respondió HTTP " + s.status + " al consultar la sesión '" + config.session + "'.");
            }
            Matcher m = STATUS.matcher(s.cuerpo);
            String estado = m.find() ? m.group(1) : "DESCONOCIDO";
            if (!"WORKING".equals(estado)) {
                return new Diagnostico(Diagnostico.Tipo.SESION_NO_LISTA, null,
                        "La sesión de WhatsApp en WAHA no está activa (estado " + estado
                                + "). Ábrela en el dashboard y pulsa Start o escanea el QR.");
            }
            if (telefonoDigitos == null) {
                return new Diagnostico(Diagnostico.Tipo.OK, null, "Conexión con WAHA correcta.");
            }
            Respuesta c = llamar("GET", config.baseUrl + "/api/contacts/check-exists?phone="
                    + urlSeguro(telefonoDigitos) + "&session=" + urlSeguro(config.session), null);
            if (c.status < 200 || c.status >= 300) {
                // No se pudo comprobar el número, pero WAHA y la sesión sí funcionan
                return new Diagnostico(Diagnostico.Tipo.OK, null,
                        "WAHA está activo, pero no pudo verificar el número (HTTP " + c.status + ").");
            }
            Matcher e = NUMERO_EXISTE.matcher(c.cuerpo);
            if (e.find() && "false".equals(e.group(1))) {
                return new Diagnostico(Diagnostico.Tipo.NUMERO_NO_EXISTE, null,
                        "Ese número no tiene cuenta de WhatsApp. Revísalo e inténtalo de nuevo.");
            }
            Matcher id = CHAT_ID.matcher(c.cuerpo);
            return new Diagnostico(Diagnostico.Tipo.OK, id.find() ? id.group(1) : null,
                    "Número verificado en WhatsApp.");
        } catch (SSLException e) {
            return new Diagnostico(Diagnostico.Tipo.SIN_CONEXION, null,
                    "Falló la conexión segura con WAHA (" + e.getClass().getSimpleName() + ").");
        } catch (IOException e) {
            return new Diagnostico(Diagnostico.Tipo.SIN_CONEXION, null,
                    "No se pudo conectar con WAHA en " + hostLegible() + " (" + e.getClass().getSimpleName()
                            + "). Revisa que Docker esté activo, la URL/IP, el túnel USB y el firewall.");
        } catch (RuntimeException e) {
            return new Diagnostico(Diagnostico.Tipo.ERROR, null,
                    "Error inesperado al consultar WAHA: " + e.getClass().getSimpleName());
        }
    }

    private String hostLegible() {
        try {
            URL u = new URL(config.baseUrl);
            return u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
        } catch (IOException e) {
            return "la URL configurada";
        }
    }

    /** Devuelve el estado de la sesión (WORKING, STOPPED, SCAN_QR_CODE...) o null si no se pudo saber. */
    public String consultarEstadoSesion() {
        try {
            Respuesta r = llamar("GET", config.baseUrl + "/api/sessions/" + urlSeguro(config.session), null);
            if (r.status < 200 || r.status >= 300) return null;
            Matcher m = STATUS.matcher(r.cuerpo);
            return m.find() ? m.group(1) : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private Respuesta llamar(String metodo, String url, String cuerpoJson) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setRequestMethod(metodo);
            con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(config.connectTimeoutMs);
            con.setReadTimeout(config.readTimeoutMs);
            con.setUseCaches(false);
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("X-Api-Key", config.apiKey);
            con.setRequestProperty("User-Agent", "TicketMaster-Mobile/1.0");
            if (cuerpoJson != null) {
                byte[] bytes = cuerpoJson.getBytes(StandardCharsets.UTF_8);
                con.setDoOutput(true);
                con.setFixedLengthStreamingMode(bytes.length);
                con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream os = con.getOutputStream()) {
                    os.write(bytes);
                }
            }
            try {
                int status = con.getResponseCode();
                InputStream in = status >= 400 ? con.getErrorStream() : con.getInputStream();
                String cuerpo = in == null ? "" : leerAcotado(in);
                return new Respuesta(status, cuerpo, parsearRetryAfter(con.getHeaderField("Retry-After")));
            } catch (SocketTimeoutException e) {
                // Ya se escribió la petición: distinguirlo de un timeout de conexión
                throw new TimeoutDeLectura(e.getMessage());
            }
        } finally {
            con.disconnect();
        }
    }

    private static String leerAcotado(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                if (out.size() + n > MAX_RESPUESTA_BYTES) {
                    out.write(buf, 0, MAX_RESPUESTA_BYTES - out.size());
                    break;
                }
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Retry-After en segundos (la forma fecha-HTTP se ignora: se usa el backoff propio). */
    static long parsearRetryAfter(String valor) {
        if (valor == null) return 0;
        try {
            long seg = Long.parseLong(valor.trim());
            if (seg <= 0) return 0;
            return Math.min(seg * 1000L, MAX_RETRY_AFTER_MS);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String extraerIdMensaje(String cuerpo) {
        if (cuerpo == null) return null;
        Matcher m = ID_SERIALIZED.matcher(cuerpo);
        if (m.find()) return m.group(1);
        m = ID_STRING.matcher(cuerpo);
        return m.find() ? m.group(1) : null;
    }

    /** Escapa una cadena como literal JSON (RFC 8259), incluyendo caracteres de control. */
    static String jsonString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static String urlSeguro(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.') sb.append(c);
            else sb.append(String.format(Locale.ROOT, "%%%02X", (int) c));
        }
        return sb.toString();
    }

    /** Timeout ocurrido DESPUÉS de enviar la petición (esperando la respuesta). */
    private static final class TimeoutDeLectura extends SocketTimeoutException {
        TimeoutDeLectura(String msg) {
            super(msg);
        }
    }


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
}
