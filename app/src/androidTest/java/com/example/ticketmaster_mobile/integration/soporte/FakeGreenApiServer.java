package com.example.ticketmaster_mobile.integration.soporte;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GREEN-API simulado para las pruebas de integración: un servidor HTTP/1.1 mínimo sobre
 * ServerSocket (sin dependencias) que escucha en 127.0.0.1, registra cada petición y responde
 * con un guion.
 *
 *  - POST /waInstance{id}/sendMessage/{token} → respuesta guionada (por defecto 200 con idMessage).
 *    En {@code Peticion.ruta} queda solo "/sendMessage" y el token va en {@code Peticion.apiKey}.
 *  - Token distinto del esperado → 401.
 *  - estadoSesion distinto de "WORKING" → 400 "instance is starting or not authorized".
 */
public final class FakeGreenApiServer implements Closeable {

    /** Petición recibida. {@code status} se rellena cuando el servidor ya respondió. */
    public static final class Peticion {
        public final String metodo;
        public final String ruta;
        public final String apiKey;
        public final String cuerpo;
        public volatile int status;

        Peticion(String metodo, String ruta, String apiKey, String cuerpo) {
            this.metodo = metodo;
            this.ruta = ruta;
            this.apiKey = apiKey;
            this.cuerpo = cuerpo;
        }
    }

    /** Respuesta que el servidor dará a un POST sendMessage. */
    public static final class Respuesta {
        final int status;
        final String cuerpo;
        final long retardoMs;
        final String retryAfter;

        private Respuesta(int status, String cuerpo, long retardoMs, String retryAfter) {
            this.status = status;
            this.cuerpo = cuerpo;
            this.retardoMs = retardoMs;
            this.retryAfter = retryAfter;
        }

        public static Respuesta http(int status, String cuerpoJson) {
            return new Respuesta(status, cuerpoJson, 0, null);
        }

        /** Error de GREEN-API (el cuerpo lleva el mensaje; el cliente lo clasifica por palabras clave). */
        public static Respuesta errorGreen(int status, String mensaje) {
            return http(status, "{\"message\":\"" + mensaje + "\"}");
        }

        public Respuesta conRetardo(long ms) {
            return new Respuesta(status, cuerpo, ms, retryAfter);
        }

        public Respuesta conRetryAfter(String segundos) {
            return new Respuesta(status, cuerpo, retardoMs, segundos);
        }
    }

    /** Ruta normalizada de los envíos (sin /waInstance{id} ni el token). */
    public static final String RUTA_ENVIO = "/sendMessage";
    public static final String ID_INSTANCE = "7103000001";
    /** Token de la instancia simulada (en GREEN-API va dentro de la URL). */
    public static final String API_KEY_POR_DEFECTO = "token-de-prueba";

    private final ServerSocket servidor;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "fake-greenapi");
        t.setDaemon(true);
        return t;
    });
    private final List<Peticion> peticiones = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedDeque<Respuesta> guion = new ConcurrentLinkedDeque<>();
    private final AtomicInteger contadorIds = new AtomicInteger();
    private volatile Respuesta respuestaFija;
    private volatile String estadoSesion = "WORKING";
    private volatile String apiKeyEsperada = API_KEY_POR_DEFECTO;
    private volatile boolean cerrado;

    private FakeGreenApiServer(ServerSocket servidor) {
        this.servidor = servidor;
    }

    public static FakeGreenApiServer iniciar() throws IOException {
        ServerSocket ss = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        FakeGreenApiServer s = new FakeGreenApiServer(ss);
        s.pool.execute(s::aceptar);
        return s;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + servidor.getLocalPort();
    }

    // ------------------------------------------------------------------ guion

    /** Todos los envíos siguientes responden igual (salvo los encolados). */
    public void responderSiempre(Respuesta r) {
        respuestaFija = r;
    }

    /** Respuestas que se consumen una por una, en orden, antes de la respuesta fija. */
    public void encolar(Respuesta... respuestas) {
        for (Respuesta r : respuestas) guion.addLast(r);
    }

    public void restablecerRespuestas() {
        guion.clear();
        respuestaFija = null;
    }

    public void estadoSesion(String estado) {
        estadoSesion = estado;
    }

    public void exigirApiKey(String clave) {
        apiKeyEsperada = clave;
    }

    // ------------------------------------------------------------------ lo recibido

    public List<Peticion> todas() {
        return new ArrayList<>(peticiones);
    }

    /** Solo los POST sendMessage. */
    public List<Peticion> envios() {
        List<Peticion> r = new ArrayList<>();
        for (Peticion p : peticiones) {
            if ("POST".equals(p.metodo) && RUTA_ENVIO.equals(p.ruta)) r.add(p);
        }
        return r;
    }

    /** Envíos que GREEN-API aceptó (2xx): los mensajes que de verdad "llegaron" a WhatsApp. */
    public List<Peticion> enviosAceptados() {
        List<Peticion> r = new ArrayList<>();
        for (Peticion p : envios()) {
            if (p.status >= 200 && p.status < 300) r.add(p);
        }
        return r;
    }

    public void limpiarRegistro() {
        peticiones.clear();
    }

    // ------------------------------------------------------------------ servidor

    private void aceptar() {
        while (!cerrado) {
            try {
                final Socket s = servidor.accept();
                pool.execute(() -> atender(s));
            } catch (IOException e) {
                if (cerrado) return;
            }
        }
    }

    private void atender(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(15_000);
            InputStream in = s.getInputStream();
            String lineaInicial = leerLinea(in);
            if (lineaInicial == null || lineaInicial.isEmpty()) return;
            String[] partes = lineaInicial.split(" ");
            String metodo = partes[0];
            String ruta = partes.length > 1 ? partes[1] : "/";

            Map<String, String> cabeceras = new HashMap<>();
            String linea;
            while ((linea = leerLinea(in)) != null && !linea.isEmpty()) {
                int i = linea.indexOf(':');
                if (i > 0) {
                    cabeceras.put(linea.substring(0, i).trim().toLowerCase(Locale.ROOT), linea.substring(i + 1).trim());
                }
            }
            int largo = 0;
            try {
                String cl = cabeceras.get("content-length");
                if (cl != null) largo = Integer.parseInt(cl);
            } catch (NumberFormatException ignorada) {
            }
            byte[] cuerpoBytes = new byte[largo];
            int leidos = 0;
            while (leidos < largo) {
                int n = in.read(cuerpoBytes, leidos, largo - leidos);
                if (n < 0) break;
                leidos += n;
            }
            String token = null;
            String rutaNormal = ruta;
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("^/waInstance(\\d+)/([A-Za-z]+)/([^/?]+)").matcher(ruta);
            if (m.find()) {
                rutaNormal = "/" + m.group(2);
                token = m.group(3);
            }
            Peticion p = new Peticion(metodo, rutaNormal, token,
                    new String(cuerpoBytes, 0, leidos, StandardCharsets.UTF_8));
            peticiones.add(p);

            Respuesta r = decidir(p);
            if (r.retardoMs > 0) {
                try {
                    Thread.sleep(r.retardoMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            p.status = r.status;
            escribir(s.getOutputStream(), r);
        } catch (IOException ignorada) {
            // el cliente cortó la conexión (p. ej. por timeout): es parte de lo que se prueba
        }
    }

    private Respuesta decidir(Peticion p) {
        String esperada = apiKeyEsperada;
        if (esperada != null && !esperada.equals(p.apiKey)) {
            return Respuesta.http(401, "{\"message\":\"Unauthorized\"}");
        }
        if ("POST".equals(p.metodo) && RUTA_ENVIO.equals(p.ruta)) {
            Respuesta r = guion.pollFirst();
            if (r != null) return r;
            if (respuestaFija != null) return respuestaFija;
            if (!"WORKING".equals(estadoSesion)) {
                return Respuesta.errorGreen(400, "instance is starting or not authorized");
            }
            int n = contadorIds.incrementAndGet();
            return Respuesta.http(200, "{\"idMessage\":\"FAKE" + n + "\"}");
        }
        return Respuesta.http(404, "{\"message\":\"Not Found\"}");
    }

    private static void escribir(OutputStream out, Respuesta r) throws IOException {
        byte[] cuerpo = r.cuerpo.getBytes(StandardCharsets.UTF_8);
        StringBuilder cab = new StringBuilder();
        cab.append("HTTP/1.1 ").append(r.status).append(' ').append(texto(r.status)).append("\r\n");
        cab.append("Content-Type: application/json; charset=utf-8\r\n");
        cab.append("Content-Length: ").append(cuerpo.length).append("\r\n");
        if (r.retryAfter != null) cab.append("Retry-After: ").append(r.retryAfter).append("\r\n");
        cab.append("Connection: close\r\n\r\n");
        out.write(cab.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(cuerpo);
        out.flush();
    }

    private static String texto(int status) {
        switch (status) {
            case 200: return "OK";
            case 201: return "Created";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 404: return "Not Found";
            case 422: return "Unprocessable Entity";
            case 403: return "Forbidden";
            case 429: return "Too Many Requests";
            case 466: return "Limit";
            case 500: return "Internal Server Error";
            case 503: return "Service Unavailable";
            default: return "Status";
        }
    }

    private static String leerLinea(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
        }
        if (c == -1 && b.size() == 0) return null;
        return new String(b.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    @Override
    public void close() {
        cerrado = true;
        try {
            servidor.close();
        } catch (IOException ignorada) {
        }
        pool.shutdownNow();
    }
}
