package com.example.ticketmaster_mobile.integration.cajanegra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.UiObject2;

import com.example.ticketmaster_mobile.data.entity.WhatsappOutbox;
import com.example.ticketmaster_mobile.integration.soporte.EntornoRule;
import com.example.ticketmaster_mobile.integration.soporte.Escenario;
import com.example.ticketmaster_mobile.integration.soporte.Esperar;
import com.example.ticketmaster_mobile.integration.soporte.Fechas;
import com.example.ticketmaster_mobile.integration.soporte.Json;
import com.example.ticketmaster_mobile.integration.uiautomator.Ua;
import com.example.ticketmaster_mobile.session.SessionManager;
import com.example.ticketmaster_mobile.integration.soporte.FakeGreenApiServer;
import com.example.ticketmaster_mobile.integration.soporte.FakeGreenApiServer.Peticion;
import com.example.ticketmaster_mobile.integration.soporte.FakeGreenApiServer.Respuesta;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;

/**
 * Las cinco pruebas de CAJA NEGRA diseñadas en el reporte (BB-01 a BB-05), automatizadas con
 * UI Automator: la app se lanza desde el launcher del sistema y se maneja solo por lo que se ve
 * en pantalla (resource-id y textos), sin usar clases internas de la app para operar.
 *
 * <pre>
 *  BB-01  Partición de equivalencia  Formato del usuario (CURP) en el login
 *  BB-02  Unitaria                   Validación de los campos del pago con tarjeta
 *  BB-03  Partición de equivalencia  Cantidad de boletos por usuario (máximo del teatro de prueba: 3)
 *  BB-04  Integración                Restricción «Día no laborable» del establecimiento
 *  BB-05  Integración                Compra completa y envío del ticket a WhatsApp
 * </pre>
 *
 * El entorno (base en memoria, catálogo de prueba y WAHA simulado) es el mismo de la suite de
 * integración, por lo que los resultados no dependen del día de la semana ni de WAHA real. El
 * Worker real de WorkManager procesa la cola en BB-05, por lo que el dispositivo necesita red.
 */
@RunWith(AndroidJUnit4.class)
public class PruebasCajaNegraTest {

    private static final long ESPERA_ENVIO_MS = 90_000L;

    @Rule
    public final EntornoRule entorno = new EntornoRule(true);

    @After
    public void cerrar() {
        Ua.cerrarApp();
    }

    // ------------------------------------------------------------------ utilidades

    private boolean haySesion() {
        return new SessionManager(entorno.contexto).haySesionActiva();
    }

    private void entrar() {
        Ua.lanzarDesdeElLauncher(true);
        Ua.iniciarSesion(Escenario.CURP, Escenario.PASSWORD);
        assertTrue("Debe mostrarse la navegación principal", Ua.hay("bottomNavigation", Ua.ESPERA_MS));
    }

    /** Del catálogo a la pantalla de selección de zonas del teatro de prueba. */
    private void abrirSeleccionDeZonasDelTeatro() {
        Ua.tocar("nav_tickets");
        Ua.tocar("chipTeatro");
        Ua.tocarTexto(Escenario.TEATRO);
        Ua.tocarTexto(Escenario.EVENTO_TEATRO);
        assertTrue(Ua.hay("rvZonas", Ua.ESPERA_MS));
    }

    private void esperarTotal(String inicio) {
        assertTrue("El resumen debe empezar con \"" + inicio + "\" (actual: " + Ua.texto("tvZonaTotal") + ")",
                Esperar.hasta(Ua.ESPERA_MS, () -> Ua.texto("tvZonaTotal").startsWith(inicio)));
    }

    private void llegarAlPagoConUnBoleto() {
        abrirSeleccionDeZonasDelTeatro();
        Ua.tocarBotonDeFila(Escenario.PLATEA, "btnMas");
        esperarTotal("1 boleto");
        assertTrue(Esperar.hasta(Ua.ESPERA_MS, () -> Ua.esperar("btnZonaContinuar").isEnabled()));
        Ua.tocar("btnZonaContinuar");
        assertTrue(Ua.hay("btnConfirmarPago", Ua.ESPERA_MS));
        Ua.tocarTexto("Tarjeta de Crédito");
    }

    private void llenarTarjeta(String numero, String titular, String vencimiento, String cvv) {
        Ua.escribir("etNumeroTarjeta", numero);
        Ua.escribir("etNombreTarjeta", titular);
        Ua.escribir("etVencimiento", vencimiento);
        Ua.escribir("etCvv", cvv);
    }

    /** ¿Se ve el mensaje de error? Se desplaza la pantalla hasta él, como lo haría una persona. */
    private boolean verError(String texto) {
        Ua.desplazarHastaTexto(texto);
        return Ua.hayTexto(texto, Ua.ESPERA_MS);
    }

    /** Escribe "con el teclado" (eventos de tecla), de modo que actúen los filtros del campo. */
    private void teclear(String idCampo, String texto) throws IOException {
        Ua.desplazarHasta(idCampo);
        UiObject2 campo = Ua.esperar(idCampo);
        campo.setText("");
        campo.click();
        Ua.dispositivo().executeShellCommand("input text " + texto);
        Ua.dispositivo().waitForIdle(1_000);
    }

    // ------------------------------------------------------------------ BB-01

    @Test
    public void bb01_formatoDelUsuarioEnElLogin_particionDeEquivalencia() {
        Ua.lanzarDesdeElLauncher(true);

        // 1b · inválida: longitud distinta de 20
        Ua.iniciarSesion(Escenario.CURP.substring(0, 19), Escenario.PASSWORD);
        assertTrue(Ua.hayTexto("El usuario debe tener exactamente 20 caracteres (actual: 19).", Ua.ESPERA_MS));
        assertFalse(haySesion());

        // 1c · inválida: minúsculas
        Ua.iniciarSesion(Escenario.CURP.toLowerCase(), Escenario.PASSWORD);
        assertTrue(Ua.hayTexto("El usuario debe estar completamente en MAYÚSCULAS.", Ua.ESPERA_MS));
        assertFalse(haySesion());

        // 1d · inválida: 20 caracteres en mayúsculas, pero el sexo no es H ni M
        Ua.iniciarSesion("ABCD123456ZJCXXXZZ00", Escenario.PASSWORD);
        assertTrue(Ua.hayTextoQueContiene("Estructura inválida", Ua.ESPERA_MS));
        assertFalse(haySesion());

        // 1e · válida en formato pero no registrada
        Ua.iniciarSesion("ZZZZ000101HJCXXXZZ00", Escenario.PASSWORD);
        assertTrue(Ua.hayTexto("No existe ninguna cuenta registrada con esa CURP. Regístrate primero.", Ua.ESPERA_MS));
        Ua.tocarTexto("Entendido");
        assertFalse(haySesion());

        // 1a · válida y registrada
        Ua.iniciarSesion(Escenario.CURP, Escenario.PASSWORD);
        assertTrue(Ua.hay("bottomNavigation", Ua.ESPERA_MS));
        assertTrue(haySesion());
    }

    // ------------------------------------------------------------------ BB-02

    @Test
    public void bb02_validacionDeLosCamposDelPagoConTarjeta_unitaria() throws IOException {
        entrar();
        llegarAlPagoConUnBoleto();

        // 2a · cuatro campos inválidos a la vez
        llenarTarjeta("12345678", "Al", "01/20", "12");
        Ua.tocar("btnConfirmarPago");
        assertTrue(verError("El número de tarjeta debe tener 16 dígitos"));
        assertTrue(verError("El nombre es demasiado corto"));
        assertTrue(verError("La tarjeta está vencida"));
        assertTrue(verError("El CVV son 3 dígitos"));
        assertEquals("No se registra la compra", 0, entorno.esc.contar("Orden"));

        // 2b · solo el vencimiento es inválido: los demás campos no muestran error
        llenarTarjeta("4111111111111111", "JUAN PEREZ", "13/30", "123");
        Ua.tocar("btnConfirmarPago");
        assertTrue(verError("El mes debe estar entre 01 y 12"));
        assertFalse(Ua.hayTexto("El número de tarjeta debe tener 16 dígitos", 1_000));
        assertFalse(Ua.hayTexto("El nombre es demasiado corto", 1_000));
        assertFalse(Ua.hayTexto("El CVV son 3 dígitos", 1_000));
        assertEquals(0, entorno.esc.contar("Orden"));

        // 2c · los campos no aceptan caracteres no permitidos cuando se escribe con el teclado
        teclear("etNumeroTarjeta", "41ab11");
        assertEquals("El número de tarjeta solo admite dígitos", "4111", Ua.texto("etNumeroTarjeta"));
        teclear("etNombreTarjeta", "J0SE");
        assertEquals("El titular no admite números", "JSE", Ua.texto("etNombreTarjeta"));
        assertEquals(0, entorno.esc.contar("Orden"));
    }

    // ------------------------------------------------------------------ BB-03

    @Test
    public void bb03_cantidadDeBoletosPorUsuario_particionDeEquivalenciaYValoresLimite() {
        entrar();
        abrirSeleccionDeZonasDelTeatro();
        int max = Escenario.MAX_BOLETOS_TEATRO;

        // 3a · inválida por debajo del mínimo: 0 boletos
        esperarTotal("0 boletos");
        assertFalse("Con 0 boletos no se puede continuar", Ua.esperar("btnZonaContinuar").isEnabled());

        // 3b · valor límite inferior: 1 boleto
        Ua.tocarBotonDeFila(Escenario.PLATEA, "btnMas");
        esperarTotal("1 boleto");
        assertTrue(Ua.esperar("btnZonaContinuar").isEnabled());

        // 3c · valor intermedio: 2 boletos
        Ua.tocarBotonDeFila(Escenario.PLATEA, "btnMas");
        esperarTotal("2 boletos");
        assertTrue(Ua.esperar("btnZonaContinuar").isEnabled());

        // 3d · valor límite superior: el máximo del teatro de prueba
        for (int i = 2; i < max; i++) Ua.tocarBotonDeFila(Escenario.PLATEA, "btnMas");
        esperarTotal(max + " boletos");
        assertTrue(Ua.esperar("btnZonaContinuar").isEnabled());
        assertTrue(Ua.hayTextoQueContiene("Máximo " + max + " boletos por usuario", Ua.ESPERA_MS));

        // 3e · inválida por encima del máximo: un «+» más no cambia el total
        Ua.tocarBotonDeFila(Escenario.PLATEA, "btnMas");
        Esperar.hasta(1_500L, () -> false);
        assertTrue("No se puede pasar del máximo (actual: " + Ua.texto("tvZonaTotal") + ")",
                Ua.texto("tvZonaTotal").startsWith(max + " boletos"));
        assertEquals(0, entorno.esc.contar("Orden"));
    }

    // ------------------------------------------------------------------ BB-04

    @Test
    public void bb04_restriccionDiaNoLaborable_integracion() {
        entrar();
        Ua.tocar("nav_tickets");
        Ua.tocar("chipTeatro");

        // 4a · establecimiento cerrado hoy: aviso, diálogo y no se abre la selección de boletos
        Ua.tocarTexto(Escenario.TEATRO_CERRADO);
        assertTrue(Ua.hay("rvFunciones", Ua.ESPERA_MS));
        assertTrue(Ua.hayTextoQueContiene("Hoy no hay venta de boletos.", Ua.ESPERA_MS));
        Ua.tocarTexto(Escenario.EVENTO_CERRADO);
        assertTrue(Ua.hayTexto("Hoy no hay venta de boletos", Ua.ESPERA_MS));
        Ua.tocarTexto("Entendido");
        assertFalse("No debe abrirse la selección de boletos", Ua.hay("rvZonas", 1_000));
        assertEquals("No se crea ninguna orden", 0, entorno.esc.contar("Orden"));

        // 4b · control: un establecimiento abierto hoy sí deja elegir boletos
        Ua.dispositivo().pressBack();
        assertTrue(Ua.hayTexto(Escenario.TEATRO, Ua.ESPERA_MS));
        Ua.tocarTexto(Escenario.TEATRO);
        assertTrue(Ua.hay("rvFunciones", Ua.ESPERA_MS));
        assertFalse("El establecimiento abierto no muestra el aviso",
                Ua.hayTextoQueContiene("Hoy no hay venta de boletos.", 1_000));
        Ua.tocarTexto(Escenario.EVENTO_TEATRO);
        assertTrue("Debe abrirse la selección de boletos", Ua.hay("rvZonas", Ua.ESPERA_MS));
    }

    // ------------------------------------------------------------------ BB-05

    @Test
    public void bb05_compraCompletaYTicketPorWhatsapp_integracion() {
        entrar();
        abrirSeleccionDeZonasDelTeatro();
        Ua.tocarBotonDeFila(Escenario.PLATEA, "btnMas");
        Ua.tocarBotonDeFila(Escenario.PLATEA, "btnMas");
        esperarTotal("2 boletos");
        Ua.tocar("btnZonaContinuar");

        // 5a · pago con tarjeta válida y confirmación
        assertTrue(Ua.hay("btnConfirmarPago", Ua.ESPERA_MS));
        Ua.tocarTexto("Tarjeta de Crédito");
        llenarTarjeta("4111111111111111", "Juan Perez", Fechas.vencimientoValido(), "987");
        Ua.tocar("btnConfirmarPago");
        assertTrue("Debe mostrarse la confirmación de compra", Ua.hay("tvConfOrden", Ua.ESPERA_MS));
        assertTrue(Ua.texto("tvConfOrden").startsWith("Orden #"));
        assertEquals("Cantidad de boletos: 2", Ua.texto("tvConfCantidad"));

        // 5b · la orden aparece en el historial del perfil como «Pagada»
        Ua.tocar("btnConfPerfil");
        Ua.tocar("nav_profile");
        assertFalse("Ya hay compras en el historial", Ua.hay("tvSinCompras", 1_000));
        assertTrue(Ua.hayTexto("Pagada", Ua.ESPERA_MS));

        // 5c · llega un solo mensaje a WhatsApp, sin datos sensibles
        assertTrue("El Worker debe enviar el ticket a WAHA (¿el dispositivo tiene red?)",
                Esperar.hasta(ESPERA_ENVIO_MS, () -> entorno.green.enviosAceptados().size() >= 1));
        List<Peticion> aceptadas = entorno.green.enviosAceptados();
        assertEquals("Un solo mensaje por compra", 1, aceptadas.size());
        Peticion p = aceptadas.get(0);
        assertEquals("/sendMessage", p.ruta);
        assertEquals(EntornoRule.CHAT_ID_ESPERADO, Json.objeto(p.cuerpo).optString("chatId"));

        int orden = entorno.esc.ultimaOrden();
        String texto = Json.texto(p);
        assertTrue(texto, texto.contains("TicketMaster · Compra confirmada"));
        assertTrue(texto, texto.contains("Orden #" + orden));
        assertTrue(texto, texto.contains(Escenario.EVENTO_TEATRO));
        assertTrue(texto, texto.contains("Platea ×2 — $2,000.00"));
        assertTrue(texto, texto.contains("Total: $2,000.00 MXN"));
        assertTrue(texto, texto.contains("•••• 1111"));
        assertFalse("No debe viajar el número completo", texto.contains("4111111111111111"));
        assertFalse("No debe viajar el CVV", texto.contains("987"));

        // 5d · la fila de la cola queda ENVIADO y no se reenvía
        assertTrue("La fila de la cola debe quedar ENVIADO",
                Esperar.hasta(15_000L, () -> WhatsappOutbox.ENVIADO.equals(
                        entorno.esc.texto("SELECT Estado FROM WhatsappOutbox WHERE ID_Orden = " + orden))));
        assertEquals("Una sola fila por orden", 1,
                entorno.esc.numero("SELECT COUNT(*) FROM WhatsappOutbox WHERE ID_Orden = " + orden));
        Esperar.hasta(5_000L, () -> false);
        assertEquals(1, entorno.green.enviosAceptados().size());
    }
}