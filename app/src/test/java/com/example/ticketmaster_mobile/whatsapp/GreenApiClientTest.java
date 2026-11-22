package com.example.ticketmaster_mobile.whatsapp;

import static org.junit.Assert.*;

import com.sun.net.httpserver.HttpServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/** Pruebas del cliente contra un GREEN-API simulado (HttpServer local). */
public class GreenApiClientTest {

    private static final String TOKEN = "token-secreto-123";

    private HttpServer server;
    private String base;
    private final AtomicReference<String> ultimaRuta = new AtomicReference<>();
    private final AtomicReference<String> ultimoCuerpo = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String respuesta = "{\"idMessage\":\"ABC123\"}";
    private volatile String retryAfter = null;

    @Before
    public void iniciar() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            ultimaRuta.set(ex.getRequestMethod() + " " + ex.getRequestURI().getPath());
            ultimoCuerpo.set(leer(ex.getRequestBody()));
            if (retryAfter != null) ex.getResponseHeaders().add("Retry-After", retryAfter);
            byte[] b = respuesta.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After
    public void detener() {
        server.stop(0);
    }

    private static String leer(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private GreenApiClient cliente() {
        return new GreenApiClient(new GreenApiConfig(base + "/", "7103123456", " " + TOKEN + " ",
                "+52 33 1234 5678", 2000, 2000, true));
    }

    @Test
    public void exito_armaUrlYCuerpoCorrectos() {
        EnvioResultado r = cliente().enviarTexto("Hola\n\"ticket\" ñ");
        assertEquals(EnvioResultado.Tipo.EXITO, r.tipo);
        assertEquals("ABC123", r.mensajeId);
        assertEquals("POST /waInstance7103123456/sendMessage/" + TOKEN, ultimaRuta.get());
        assertTrue(ultimoCuerpo.get().contains("\"chatId\":\"523312345678@c.us\""));
        assertTrue(ultimoCuerpo.get().contains("Hola\\n\\\"ticket\\\" ñ"));
    }

    @Test
    public void destinoExplicito_reemplazaAlPorDefecto() {
        assertEquals(EnvioResultado.Tipo.EXITO, cliente().enviarTexto("+52 33 9999 0000", "hola").tipo);
        assertTrue(ultimoCuerpo.get().contains("523399990000@c.us"));
    }

    @Test
    public void destinoInvalido_esPermanente() {
        assertEquals(EnvioResultado.Tipo.PERMANENTE, cliente().enviarTexto("abc", "hola").tipo);
    }

    @Test
    public void credencialesInvalidas_noConsumenIntentos() {
        status = 401;
        assertEquals(EnvioResultado.Tipo.REINTENTAR_SIN_PENALIZAR, cliente().enviarTexto("x").tipo);
        status = 403;
        assertEquals(EnvioResultado.Tipo.REINTENTAR_SIN_PENALIZAR, cliente().enviarTexto("x").tipo);
    }

    @Test
    public void instanciaSinQr_noConsumeIntentos() {
        status = 400;
        respuesta = "{\"error\":\"instance is starting or not authorized\"}";
        assertEquals(EnvioResultado.Tipo.REINTENTAR_SIN_PENALIZAR, cliente().enviarTexto("x").tipo);
    }

    @Test
    public void validacion400_esPermanente() {
        status = 400;
        respuesta = "{\"message\":\"Validation failed\"}";
        assertEquals(EnvioResultado.Tipo.PERMANENTE, cliente().enviarTexto("x").tipo);
    }

    @Test
    public void limiteDelPlan466_esPermanente() {
        status = 466;
        respuesta = "{}";
        assertEquals(EnvioResultado.Tipo.PERMANENTE, cliente().enviarTexto("x").tipo);
    }

    @Test
    public void errores429y5xx_seReintentan() {
        respuesta = "{}";
        retryAfter = "7";
        status = 429;
        EnvioResultado r = cliente().enviarTexto("x");
        assertEquals(EnvioResultado.Tipo.REINTENTAR, r.tipo);
        assertEquals(7000, r.reintentarDespuesMs);
        retryAfter = null;
        for (int s : new int[]{499, 500, 502, 503}) {
            status = s;
            assertEquals("HTTP " + s, EnvioResultado.Tipo.REINTENTAR, cliente().enviarTexto("x").tipo);
        }
    }

    @Test
    public void elTokenNuncaApareceEnLosDetalles() {
        respuesta = "{}";
        for (int s : new int[]{400, 401, 403, 404, 429, 466, 500, 502}) {
            status = s;
            String d = cliente().enviarTexto("x").detalle;
            assertTrue("HTTP " + s, d == null || !d.contains(TOKEN));
        }
        server.stop(0); // conexión rechazada: el mensaje de la excepción contiene la URL
        EnvioResultado r = cliente().enviarTexto("x");
        assertEquals(EnvioResultado.Tipo.REINTENTAR, r.tipo);
        assertFalse(r.detalle.contains(TOKEN));
    }

    @Test
    public void sinRedirecciones_noSeReenviaElToken() {
        status = 302;
        respuesta = "";
        assertEquals(EnvioResultado.Tipo.REINTENTAR_SIN_PENALIZAR, cliente().enviarTexto("x").tipo);
    }

    @Test
    public void sinConfigurarOHttp_noSeEnviaNada() {
        assertEquals(EnvioResultado.Tipo.REINTENTAR_SIN_PENALIZAR,
                new GreenApiClient(new GreenApiConfig("", "", "", "", 1, 1, false)).enviarTexto("x").tipo);
        assertEquals(EnvioResultado.Tipo.REINTENTAR_SIN_PENALIZAR,
                new GreenApiClient(new GreenApiConfig("http://x.com", "1", "t", "5213312345678", 1, 1, false))
                        .enviarTexto("x").tipo);
    }
}
