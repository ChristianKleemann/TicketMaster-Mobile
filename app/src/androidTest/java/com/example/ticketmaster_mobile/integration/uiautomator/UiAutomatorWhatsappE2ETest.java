package com.example.ticketmaster_mobile.integration.uiautomator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.ticketmaster_mobile.data.entity.WhatsappOutbox;
import com.example.ticketmaster_mobile.integration.soporte.EntornoRule;
import com.example.ticketmaster_mobile.integration.soporte.Escenario;
import com.example.ticketmaster_mobile.integration.soporte.Esperar;
import com.example.ticketmaster_mobile.integration.soporte.Fechas;
import com.example.ticketmaster_mobile.integration.soporte.FakeWahaServer.Peticion;
import com.example.ticketmaster_mobile.integration.soporte.Json;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Extremo a extremo: el usuario compra tocando la pantalla (UI Automator) y el envío a WhatsApp lo
 * hace el Worker real de WorkManager contra un WAHA simulado. Requiere que el dispositivo tenga
 * red (el trabajo en segundo plano la exige como restricción); no requiere WAHA real.
 */
@RunWith(AndroidJUnit4.class)
public class UiAutomatorWhatsappE2ETest {

    private static final long ESPERA_ENVIO_MS = 90_000L;

    /** WorkManager real: el Worker de la app procesa la cola. */
    @Rule
    public final EntornoRule entorno = new EntornoRule(true);

    @After
    public void cerrar() {
        Ua.cerrarApp();
    }

    private void comprarDosBoletosDeTeatroConTarjeta() {
        Ua.lanzarDesdeElLauncher(true);
        Ua.iniciarSesion(Escenario.CURP, Escenario.PASSWORD);
        assertTrue(Ua.hay("bottomNavigation", Ua.ESPERA_MS));

        Ua.tocar("nav_tickets");
        Ua.tocar("chipTeatro");
        Ua.tocarTexto(Escenario.TEATRO);
        Ua.tocarTexto(Escenario.EVENTO_TEATRO);
        assertTrue(Ua.hay("rvZonas", Ua.ESPERA_MS));
        Ua.tocarBotonDeFila(Escenario.PLATEA, "btnMas");
        Ua.tocarBotonDeFila(Escenario.PLATEA, "btnMas");
        Ua.tocar("btnZonaContinuar");

        assertTrue(Ua.hay("btnConfirmarPago", Ua.ESPERA_MS));
        Ua.tocarTexto("Tarjeta de Crédito");
        Ua.escribir("etNumeroTarjeta", "4111111111111111");
        Ua.escribir("etNombreTarjeta", "Juan Perez");
        Ua.escribir("etVencimiento", Fechas.vencimientoValido());
        Ua.escribir("etCvv", "987");
        Ua.tocar("btnConfirmarPago");

        assertTrue("Debe mostrarse la confirmación de compra", Ua.hay("tvConfOrden", Ua.ESPERA_MS));
    }

    @Test
    public void laCompraHechaDesdeLaPantallaLlegaPorWhatsappUnaSolaVezYSinErrores() {
        comprarDosBoletosDeTeatroConTarjeta();

        assertTrue(Ua.texto("tvConfOrden").startsWith("Orden #"));
        assertEquals("Cantidad de boletos: 2", Ua.texto("tvConfCantidad"));

        boolean llego = Esperar.hasta(ESPERA_ENVIO_MS, () -> entorno.waha.enviosAceptados().size() >= 1);
        assertTrue("El Worker debe enviar el ticket a WAHA (¿el dispositivo tiene red?)", llego);

        List<Peticion> aceptadas = entorno.waha.enviosAceptados();
        assertEquals("Un solo mensaje por compra", 1, aceptadas.size());
        Peticion p = aceptadas.get(0);
        assertEquals("/api/sendText", p.ruta);
        assertEquals(EntornoRule.CHAT_ID_ESPERADO, Json.objeto(p.cuerpo).optString("chatId"));
        assertEquals(EntornoRule.SESION, Json.objeto(p.cuerpo).optString("session"));

        String texto = Json.texto(p);
        int orden = entorno.esc.ultimaOrden();
        assertTrue(texto, texto.contains("Orden #" + orden));
        assertTrue(texto, texto.contains(Escenario.EVENTO_TEATRO));
        assertTrue(texto, texto.contains("Platea ×2 — $2,000.00"));
        assertTrue(texto, texto.contains("Total: $2,000.00 MXN"));
        assertTrue(texto, texto.contains("•••• 1111"));
        assertFalse("No debe viajar el número completo", texto.contains("4111111111111111"));

        assertTrue("La fila de la cola debe quedar ENVIADO",
                Esperar.hasta(15_000L, () -> WhatsappOutbox.ENVIADO.equals(
                        entorno.esc.texto("SELECT Estado FROM WhatsappOutbox WHERE ID_Orden = " + orden))));
        assertNotNull(entorno.esc.texto("SELECT MensajeIdWaha FROM WhatsappOutbox WHERE ID_Orden = " + orden));

        // Esperar un poco más: no debe haber un segundo envío
        Esperar.hasta(5_000L, () -> false);
        assertEquals(1, entorno.waha.enviosAceptados().size());
    }

    @Test
    public void conWahaCaidoLaCompraDesdeLaPantallaSeCompletaYQuedaPendiente() {
        entorno.waha.close();

        comprarDosBoletosDeTeatroConTarjeta();

        int orden = entorno.esc.ultimaOrden();
        assertEquals("Pagada", entorno.esc.texto("SELECT EstadoOrden FROM Orden WHERE ID_Orden = " + orden));
        assertEquals(2, entorno.esc.contar("Boleto"));
        String estado = entorno.esc.texto("SELECT Estado FROM WhatsappOutbox WHERE ID_Orden = " + orden);
        assertNotNull(estado);
        assertFalse("Sin WAHA no puede figurar como enviado", WhatsappOutbox.ENVIADO.equals(estado));
    }
}
