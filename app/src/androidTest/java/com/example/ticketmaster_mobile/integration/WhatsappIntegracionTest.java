package com.example.ticketmaster_mobile.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.ticketmaster_mobile.data.entity.WhatsappOutbox;
import com.example.ticketmaster_mobile.integration.soporte.Compras;
import com.example.ticketmaster_mobile.integration.soporte.EntornoRule;
import com.example.ticketmaster_mobile.integration.soporte.Escenario;
import com.example.ticketmaster_mobile.integration.soporte.FakeWahaServer;
import com.example.ticketmaster_mobile.integration.soporte.FakeWahaServer.Peticion;
import com.example.ticketmaster_mobile.integration.soporte.FakeWahaServer.Respuesta;
import com.example.ticketmaster_mobile.integration.soporte.Json;
import com.example.ticketmaster_mobile.waha.Backoff;
import com.example.ticketmaster_mobile.waha.CircuitBreaker;
import com.example.ticketmaster_mobile.waha.MensajeTicketFormatter;
import com.example.ticketmaster_mobile.waha.OutboxProcessor;
import com.example.ticketmaster_mobile.waha.RoomOutboxStore;
import com.example.ticketmaster_mobile.waha.RoomTicketSource;
import com.example.ticketmaster_mobile.waha.WahaClient;
import com.example.ticketmaster_mobile.waha.WahaConfig;
import com.example.ticketmaster_mobile.waha.WahaConfigProvider;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Integración del envío por WhatsApp: compra real → cola (outbox) en Room → OutboxProcessor real →
 * WahaClient real (HTTP) → servidor WAHA simulado. Comprueba que el mensaje es el correcto, que
 * llega una sola vez y que los fallos se tratan como se espera. No requiere WAHA ni red.
 */
@RunWith(AndroidJUnit4.class)
public class WhatsappIntegracionTest {

    @Rule
    public final EntornoRule entorno = new EntornoRule();

    /** Reloj controlable: las esperas de reintento se simulan sin dormir. */
    private final AtomicLong reloj = new AtomicLong(System.currentTimeMillis());

    private Escenario e() {
        return entorno.esc;
    }

    private OutboxProcessor procesador() {
        return new OutboxProcessor(
                new RoomOutboxStore(e().db.whatsappOutboxDao()),
                new RoomTicketSource(e().db),
                new WahaClient(WahaConfigProvider.actual()),
                new Backoff(1_000L, 5_000L, new Random(7)),
                breaker,
                reloj::get,
                OutboxProcessor.MAX_INTENTOS);
    }

    private final CircuitBreaker breaker = new CircuitBreaker(5, 60_000L, reloj::get);

    private OutboxProcessor.Resumen procesar() {
        reloj.set(Math.max(reloj.get(), System.currentTimeMillis()));
        return procesador().procesar();
    }

    private void avanzar(long ms) {
        reloj.addAndGet(ms);
    }

    private int comprarTeatro(int usuario, int funcion, int zona, double precio, int n) {
        Compras.Resultado r = Compras.comprar(entorno.contexto, usuario, funcion,
                Compras.porZona(zona, precio, n), Compras.tarjetaCredito(e()));
        assertTrue(r.error, r.exito());
        return r.idOrden;
    }

    private int comprarTeatro(int n) {
        return comprarTeatro(e().usuario1, e().teatroFuncion, e().teatroPlatea, 1000, n);
    }

    private WhatsappOutbox fila(int idOrden) {
        WhatsappOutbox f = e().db.whatsappOutboxDao().buscarPorOrden(idOrden);
        assertNotNull("La orden " + idOrden + " debe tener fila en la cola", f);
        return f;
    }

    // ------------------------------------------------------------------ mensaje correcto, sin errores

    @Test
    public void laCompraSeEnviaUnaVezConElMensajeCorrectoYQuedaEnviado() {
        int orden = comprarTeatro(2);
        assertEquals(WhatsappOutbox.PENDIENTE, fila(orden).estado);

        OutboxProcessor.Resumen r = procesar();

        assertEquals(1, r.enviados);
        assertEquals(0, r.fallidos);
        assertEquals(0, r.reprogramados);

        List<Peticion> aceptadas = entorno.waha.enviosAceptados();
        assertEquals(1, aceptadas.size());
        Peticion p = aceptadas.get(0);
        assertEquals("POST", p.metodo);
        assertEquals("/api/sendText", p.ruta);
        assertEquals(FakeWahaServer.API_KEY_POR_DEFECTO, p.apiKey);
        assertEquals(EntornoRule.SESION, Json.objeto(p.cuerpo).optString("session"));
        assertEquals(EntornoRule.CHAT_ID_ESPERADO, Json.objeto(p.cuerpo).optString("chatId"));

        WhatsappOutbox f = fila(orden);
        assertEquals(WhatsappOutbox.ENVIADO, f.estado);
        assertNotNull(f.mensajeIdWaha);
        assertTrue(f.mensajeIdWaha, f.mensajeIdWaha.startsWith("true_5213322180006@c.us_"));
        assertNotNull(f.enviadoEn);
    }

    @Test
    public void elTextoEnviadoCoincideConLosDatosGuardadosEnLaBase() {
        int orden = comprarTeatro(2);
        String referencia = e().texto("SELECT ReferenciaExterna FROM Pago WHERE ID_Orden = " + orden);

        procesar();

        String texto = Json.texto(entorno.waha.enviosAceptados().get(0));
        assertTrue(texto, texto.contains("Compra confirmada"));
        assertTrue(texto, texto.contains("Orden #" + orden));
        assertTrue(texto, texto.contains("Ref. " + referencia));
        assertTrue(texto, texto.contains(Escenario.EVENTO_TEATRO));
        assertTrue(texto, texto.contains(Escenario.TEATRO));
        assertTrue(texto, texto.contains("Boletos (2)"));
        assertTrue(texto, texto.contains("Platea ×2 — $2,000.00"));
        assertTrue(texto, texto.contains("Total: $2,000.00 MXN"));
        assertTrue(texto, texto.contains("Tarjeta de Crédito •••• 1111"));
        assertTrue(texto, texto.contains("Pago aprobado"));
        // Y es exactamente lo que produce el formateador a partir de la base
        assertEquals(MensajeTicketFormatter.formatear(new RoomTicketSource(e().db).cargar(orden)), texto);
    }

    @Test
    public void unaCompraDeCineIncluyeLosAsientosYEnmascaraElCorreoDePaypal() {
        Compras.Resultado r = Compras.comprar(entorno.contexto, e().usuario1, e().cineFuncion,
                Compras.asientos(e(), 90, "A1", "A2"), Compras.paypal(e(), "cliente@correo.com"));
        assertTrue(r.error, r.exito());

        procesar();

        String texto = Json.texto(entorno.waha.enviosAceptados().get(0));
        assertTrue(texto, texto.contains("Tradicional — asientos A1, A2 — $180.00"));
        assertTrue(texto, texto.contains("Total: $180.00 MXN"));
        assertTrue(texto, texto.contains("PayPal"));
        assertTrue(texto, texto.contains("cl***@correo.com"));
        assertFalse("El correo completo no debe salir en el chat", texto.contains("cliente@correo.com"));
    }

    @Test
    public void elMensajeNoFiltraLaApiKeyNiDatosSensibles() {
        comprarTeatro(1);

        procesar();

        String texto = Json.texto(entorno.waha.enviosAceptados().get(0));
        assertFalse(texto.contains(FakeWahaServer.API_KEY_POR_DEFECTO));
        assertFalse(texto.contains(Escenario.CURP));
        assertFalse(texto.contains(e().texto("SELECT PasswordHash FROM Usuario WHERE ID_Usuario = " + e().usuario1)));
    }

    @Test
    public void losCaracteresEspecialesViajanBienEnElJson() {
        int orden = comprarTeatro(e().usuario1, e().teatroFuncionRara, e().teatroPlatea, 1000, 1);

        procesar();

        assertEquals(1, entorno.waha.enviosAceptados().size());
        String texto = Json.texto(entorno.waha.enviosAceptados().get(0)); // falla si el JSON no es válido
        assertTrue(texto, texto.contains(Escenario.EVENTO_RARO));
        assertTrue(texto, texto.contains("Orden #" + orden));
    }

    @Test
    public void unNombreDeEventoNoPuedeInyectarFormatoNiSaltosDeLinea() {
        e().db.getOpenHelper().getWritableDatabase().execSQL(
                "UPDATE Funcion SET NombreEvento = '*Evento_ ~malo~\n*Inyectado*' WHERE ID_Funcion = " + e().teatroFuncion);
        comprarTeatro(1);

        procesar();

        String texto = Json.texto(entorno.waha.enviosAceptados().get(0));
        assertTrue(texto, texto.contains("*Evento malo Inyectado*"));
        assertFalse(texto, texto.contains("~malo~"));
    }

    @Test
    public void variasComprasSeEnvianUnaVezCadaUnaYEnMensajesDistintos() {
        int a = comprarTeatro(1);
        int b = comprarTeatro(e().usuario2, e().teatroFuncion, e().teatroPlatea, 1000, 2);
        int c = comprarTeatro(e().usuario2, e().teatroFuncionRara, e().teatroPalco, 2500, 1);

        OutboxProcessor.Resumen r = procesar();

        assertEquals(3, r.enviados);
        List<Peticion> envios = entorno.waha.enviosAceptados();
        assertEquals(3, envios.size());
        Set<String> textos = new HashSet<>();
        for (Peticion p : envios) textos.add(Json.texto(p));
        assertEquals(3, textos.size());
        for (int orden : new int[]{a, b, c}) {
            assertEquals(WhatsappOutbox.ENVIADO, fila(orden).estado);
            int n = 0;
            for (String t : textos) if (t.contains("Orden #" + orden + " ")) n++;
            assertEquals("Cada orden en exactamente un mensaje", 1, n);
        }
    }

    @Test
    public void unaOrdenYaEnviadaNoSeReenvia() {
        comprarTeatro(1);
        assertEquals(1, procesar().enviados);

        avanzar(10 * 60_000L);
        OutboxProcessor.Resumen segunda = procesar();

        assertEquals(0, segunda.enviados);
        assertEquals(1, entorno.waha.envios().size());
    }

    // ------------------------------------------------------------------ fallos transitorios y recuperación

    @Test
    public void unErrorDelServidorSeReintentaYSeEntregaUnaSolaVez() {
        int orden = comprarTeatro(1);
        entorno.waha.encolar(Respuesta.errorWaha(503, "Service Unavailable"));

        OutboxProcessor.Resumen primera = procesar();

        assertEquals(0, primera.enviados);
        assertEquals(1, primera.reprogramados);
        WhatsappOutbox f = fila(orden);
        assertEquals(WhatsappOutbox.PENDIENTE, f.estado);
        assertEquals(1, f.intentos);
        assertNotNull(f.ultimoError);
        assertTrue(f.ultimoError, f.ultimoError.contains("503"));
        assertTrue("Debe esperar antes de reintentar", f.proximoIntento > reloj.get());
        assertEquals("El error no debe contar como mensaje entregado", 0, entorno.waha.enviosAceptados().size());

        avanzar(60_000L);
        OutboxProcessor.Resumen segunda = procesar();

        assertEquals(1, segunda.enviados);
        assertEquals(2, entorno.waha.envios().size());
        assertEquals(1, entorno.waha.enviosAceptados().size());
        assertEquals(WhatsappOutbox.ENVIADO, fila(orden).estado);
    }

    @Test
    public void unReintentoNoSeAdelantaAntesDeSuHora() {
        comprarTeatro(1);
        entorno.waha.encolar(Respuesta.errorWaha(503, "Service Unavailable"));
        procesar();
        int enviosAntes = entorno.waha.envios().size();

        OutboxProcessor.Resumen r = procesar(); // sin avanzar el reloj

        assertEquals(0, r.enviados);
        assertEquals(enviosAntes, entorno.waha.envios().size());
    }

    @Test
    public void conLaSesionDeWhatsappSinEscanearNoSeConsumenIntentosYSeEsperaAlMenosCincoMinutos() {
        int orden = comprarTeatro(1);
        entorno.waha.estadoSesion("SCAN_QR_CODE");
        entorno.waha.responderSiempre(Respuesta.errorWaha(422, "Session status is not as expected"));

        OutboxProcessor.Resumen r = procesar();

        assertEquals(1, r.reprogramados);
        WhatsappOutbox f = fila(orden);
        assertEquals(WhatsappOutbox.PENDIENTE, f.estado);
        assertEquals("Un problema de sesión no gasta intentos", 0, f.intentos);
        assertTrue(f.ultimoError, f.ultimoError.contains("SCAN_QR_CODE"));
        assertTrue(f.proximoIntento >= reloj.get() + OutboxProcessor.ESPERA_MIN_INFRA_MS - 1_000L);

        // Se escanea el QR: al llegar la hora, el mensaje sale
        entorno.waha.estadoSesion("WORKING");
        entorno.waha.restablecerRespuestas();
        avanzar(OutboxProcessor.ESPERA_MIN_INFRA_MS + 1_000L);
        assertEquals(1, procesar().enviados);
        assertEquals(WhatsappOutbox.ENVIADO, fila(orden).estado);
    }

    @Test
    public void unaApiKeyRechazadaNoPenalizaYSeRecuperaAlCorregirla() {
        int orden = comprarTeatro(1);
        entorno.waha.exigirApiKey("otra-clave");

        OutboxProcessor.Resumen r = procesar();

        assertEquals(1, r.reprogramados);
        WhatsappOutbox f = fila(orden);
        assertEquals(WhatsappOutbox.PENDIENTE, f.estado);
        assertEquals(0, f.intentos);
        assertTrue(f.ultimoError, f.ultimoError.contains("API key"));
        assertEquals(0, entorno.waha.enviosAceptados().size());

        entorno.waha.exigirApiKey(FakeWahaServer.API_KEY_POR_DEFECTO);
        avanzar(OutboxProcessor.ESPERA_MIN_INFRA_MS + 1_000L);
        assertEquals(1, procesar().enviados);
    }

    @Test
    public void siWahaTardaMasDelTimeoutSeReintentaYSeAvisaQueLaEntregaPudoOcurrir() {
        int orden = comprarTeatro(1);
        entorno.waha.encolar(Respuesta.http(201, "{\"id\":\"lento\"}").conRetardo(5_000));

        OutboxProcessor.Resumen r = procesar();

        assertEquals(1, r.reprogramados);
        WhatsappOutbox f = fila(orden);
        assertEquals(WhatsappOutbox.PENDIENTE, f.estado);
        assertTrue(f.ultimoError, f.ultimoError.contains("la entrega pudo ocurrir"));
    }

    @Test
    public void conWahaApagadoLaOrdenQuedaPendienteYLaCompraIntacta() {
        int orden = comprarTeatro(1);
        entorno.waha.close();

        OutboxProcessor.Resumen r = procesar();

        assertEquals(0, r.enviados);
        assertEquals(1, r.reprogramados);
        WhatsappOutbox f = fila(orden);
        assertEquals(WhatsappOutbox.PENDIENTE, f.estado);
        assertTrue(f.ultimoError, f.ultimoError.contains("Error de red") || f.ultimoError.contains("conectar"));
        assertEquals("La compra sigue pagada", "Pagada",
                e().texto("SELECT EstadoOrden FROM Orden WHERE ID_Orden = " + orden));
        assertEquals(1, e().contar("Boleto"));
    }

    @Test
    public void cuandoWahaVuelveEnOtraDireccionLaOrdenPendienteSeEntrega() throws Exception {
        int orden = comprarTeatro(1);
        entorno.waha.close();
        assertEquals(1, procesar().reprogramados);

        try (FakeWahaServer nuevo = FakeWahaServer.iniciar()) {
            entorno.configurarWaha(new WahaConfig(nuevo.baseUrl(), FakeWahaServer.API_KEY_POR_DEFECTO,
                    EntornoRule.SESION, EntornoRule.DESTINO, 2000, 2000, true));
            avanzar(60_000L);

            assertEquals(1, procesar().enviados);
            assertEquals(1, nuevo.enviosAceptados().size());
            assertEquals(WhatsappOutbox.ENVIADO, fila(orden).estado);
        }
    }

    @Test
    public void despuesDeCincoFallosSeguidosElCircuitoSeAbreYNoSeInsisteContraWaha() {
        // Seis compras de un boleto (3 por usuario, que es el máximo del teatro de prueba)
        for (int i = 0; i < 3; i++) {
            comprarTeatro(e().usuario1, e().teatroFuncion, e().teatroPlatea, 1000, 1);
            comprarTeatro(e().usuario2, e().teatroFuncion, e().teatroPlatea, 1000, 1);
        }
        entorno.waha.responderSiempre(Respuesta.errorWaha(503, "Service Unavailable"));

        OutboxProcessor.Resumen r = procesar();

        assertTrue("El circuito debe abrirse", r.circuitoAbierto);
        assertEquals("Solo 5 intentos contra el servidor caído", 5, entorno.waha.envios().size());
        assertEquals(0, r.enviados);
        assertEquals(6, e().numero("SELECT COUNT(*) FROM WhatsappOutbox WHERE Estado IN ('PENDIENTE','ENVIANDO')"));
    }

    // ------------------------------------------------------------------ fallos permanentes

    @Test
    public void unaPeticionInvalidaFallaSinReintentosYSePuedeReabrir() {
        int orden = comprarTeatro(1);
        entorno.waha.responderSiempre(Respuesta.errorWaha(400, "chatId is invalid"));

        OutboxProcessor.Resumen r = procesar();

        assertEquals(1, r.fallidos);
        WhatsappOutbox f = fila(orden);
        assertEquals(WhatsappOutbox.FALLIDO, f.estado);
        assertTrue(f.ultimoError, f.ultimoError.contains("HTTP 400"));
        assertFalse("El error guardado no debe incluir el texto del ticket", f.ultimoError.contains("Compra confirmada"));

        avanzar(60 * 60_000L);
        assertEquals("Un fallo permanente no se reintenta solo", 0, procesar().enviados);
        assertEquals(1, entorno.waha.envios().size());

        // El usuario corrige la configuración y reabre las fallidas
        entorno.waha.restablecerRespuestas();
        assertEquals(1, e().db.whatsappOutboxDao().reabrirFallidas(reloj.get()));
        assertEquals(1, procesar().enviados);
        assertEquals(WhatsappOutbox.ENVIADO, fila(orden).estado);
    }

    @Test
    public void unDestinoSinLidSeMarcaComoFallidoConUnMensajeUtil() {
        int orden = comprarTeatro(1);
        entorno.waha.responderSiempre(Respuesta.errorWaha(500, "No LID for user"));

        OutboxProcessor.Resumen r = procesar();

        assertEquals(1, r.fallidos);
        WhatsappOutbox f = fila(orden);
        assertEquals(WhatsappOutbox.FALLIDO, f.estado);
        assertTrue(f.ultimoError, f.ultimoError.contains("No LID for user"));
        assertTrue(f.ultimoError, f.ultimoError.contains("521"));
        assertEquals(1, entorno.waha.envios().size());
    }

    @Test
    public void unaOrdenSinPagoAprobadoNoSeEnvia() {
        int orden = comprarTeatro(1);
        e().db.getOpenHelper().getWritableDatabase().execSQL(
                "UPDATE Pago SET EstadoPago = 'Rechazado' WHERE ID_Orden = " + orden);

        OutboxProcessor.Resumen r = procesar();

        assertEquals(1, r.fallidos);
        assertEquals(0, entorno.waha.envios().size());
        assertEquals(WhatsappOutbox.FALLIDO, fila(orden).estado);
    }

    @Test
    public void alAgotarLosIntentosLaOrdenPasaAFallida() {
        int orden = comprarTeatro(1);
        e().db.getOpenHelper().getWritableDatabase().execSQL(
                "UPDATE WhatsappOutbox SET Intentos = " + (OutboxProcessor.MAX_INTENTOS - 1)
                        + " WHERE ID_Orden = " + orden);
        entorno.waha.responderSiempre(Respuesta.errorWaha(503, "Service Unavailable"));

        OutboxProcessor.Resumen r = procesar();

        assertEquals(1, r.fallidos);
        WhatsappOutbox f = fila(orden);
        assertEquals(WhatsappOutbox.FALLIDO, f.estado);
        assertTrue(f.ultimoError, f.ultimoError.contains("Se agotaron los " + OutboxProcessor.MAX_INTENTOS));
    }

    @Test
    public void conWahaSinConfigurarNoSeEnviaNadaYNoSeConsumenIntentos() {
        int orden = comprarTeatro(1);
        entorno.desactivarWaha();

        OutboxProcessor.Resumen r = procesar();

        assertEquals(0, r.enviados);
        assertEquals(0, entorno.waha.envios().size());
        assertEquals(0, fila(orden).intentos);
        assertEquals(WhatsappOutbox.PENDIENTE, fila(orden).estado);
    }

    // ------------------------------------------------------------------ concurrencia y arrendamiento (lease)

    @Test
    public void dosProcesadoresSimultaneosEnvianLaOrdenUnaSolaVez() throws Exception {
        int orden = comprarTeatro(1);
        entorno.waha.responderSiempre(Respuesta.http(201, "{\"id\":\"true_x@c.us_UNICO\"}").conRetardo(400));
        reloj.set(Math.max(reloj.get(), System.currentTimeMillis()));

        CountDownLatch salida = new CountDownLatch(1);
        CountDownLatch terminan = new CountDownLatch(2);
        List<OutboxProcessor.Resumen> resumenes = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            OutboxProcessor p = procesador();
            new Thread(() -> {
                try {
                    salida.await();
                    OutboxProcessor.Resumen r = p.procesar();
                    synchronized (resumenes) {
                        resumenes.add(r);
                    }
                } catch (InterruptedException ignorada) {
                } finally {
                    terminan.countDown();
                }
            }).start();
        }
        salida.countDown();
        assertTrue(terminan.await(30, TimeUnit.SECONDS));

        assertEquals("Solo un procesador debe reclamar la orden", 1, entorno.waha.envios().size());
        int enviados = 0;
        for (OutboxProcessor.Resumen r : resumenes) enviados += r.enviados;
        assertEquals(1, enviados);
        assertEquals(WhatsappOutbox.ENVIADO, fila(orden).estado);
    }

    @Test
    public void unaOrdenConArrendamientoVigenteNoSeReclama() {
        int orden = comprarTeatro(1);
        reloj.set(Math.max(reloj.get(), System.currentTimeMillis()));
        e().db.getOpenHelper().getWritableDatabase().execSQL(
                "UPDATE WhatsappOutbox SET Estado = 'ENVIANDO', Intentos = 1, LeaseHasta = "
                        + (reloj.get() + 60_000L) + " WHERE ID_Orden = " + orden);

        OutboxProcessor.Resumen r = procesar();

        assertEquals(0, r.enviados);
        assertEquals(0, entorno.waha.envios().size());
        assertEquals(WhatsappOutbox.ENVIANDO, fila(orden).estado);
    }

    @Test
    public void unaOrdenConArrendamientoVencidoPorUnCierreDeLaAppSeRecupera() {
        int orden = comprarTeatro(1);
        reloj.set(Math.max(reloj.get(), System.currentTimeMillis()));
        e().db.getOpenHelper().getWritableDatabase().execSQL(
                "UPDATE WhatsappOutbox SET Estado = 'ENVIANDO', Intentos = 1, LeaseHasta = "
                        + (reloj.get() - 1_000L) + " WHERE ID_Orden = " + orden);

        OutboxProcessor.Resumen r = procesar();

        assertEquals(1, r.enviados);
        assertEquals(WhatsappOutbox.ENVIADO, fila(orden).estado);
        assertEquals(1, entorno.waha.enviosAceptados().size());
    }
}
