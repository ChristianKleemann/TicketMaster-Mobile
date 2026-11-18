package com.example.ticketmaster_mobile.integration.uiautomator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.ticketmaster_mobile.integration.soporte.Compras;
import com.example.ticketmaster_mobile.integration.soporte.EntornoRule;
import com.example.ticketmaster_mobile.integration.soporte.Escenario;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Restricciones de compra vistas por el usuario (UI Automator, caja negra). */
@RunWith(AndroidJUnit4.class)
public class UiAutomatorRestriccionesTest {

    @Rule
    public final EntornoRule entorno = new EntornoRule();

    @Before
    public void entrar() {
        Ua.lanzarDesdeElLauncher(true);
        Ua.iniciarSesion(Escenario.CURP, Escenario.PASSWORD);
        assertTrue(Ua.hay("bottomNavigation", Ua.ESPERA_MS));
        Ua.tocar("nav_tickets");
    }

    @After
    public void cerrar() {
        Ua.cerrarApp();
    }

    private void abrir(String chip, String establecimiento) {
        Ua.tocar(chip);
        Ua.tocarTexto(establecimiento);
        assertTrue(Ua.hay("rvFunciones", Ua.ESPERA_MS));
    }

    @Test
    public void unEstablecimientoCerradoHoyAvisaYNoDejaAvanzar() {
        abrir("chipTeatro", Escenario.TEATRO_CERRADO);

        assertTrue(Ua.hayTextoQueContiene("Hoy no hay venta de boletos.", Ua.ESPERA_MS));
        Ua.tocarTexto(Escenario.EVENTO_CERRADO);

        assertTrue(Ua.hayTexto("Hoy no hay venta de boletos", Ua.ESPERA_MS));
        Ua.tocarTexto("Entendido");
        assertFalse("No debe abrirse la selección de boletos", Ua.hay("rvZonas", 1_000));
        assertEquals(0, entorno.esc.contar("Orden"));
    }

    @Test
    public void unDiaFestivoSeExplicaAlUsuario() {
        abrir("chipTeatro", Escenario.TEATRO_FESTIVO);

        assertTrue(Ua.hayTextoQueContiene(Escenario.DESCRIPCION_FESTIVO, Ua.ESPERA_MS));
        Ua.tocarTexto(Escenario.EVENTO_FESTIVO);
        assertTrue(Ua.hayTexto("Hoy no hay venta de boletos", Ua.ESPERA_MS));
        Ua.tocarTexto("Entendido");
        assertFalse(Ua.hay("rvZonas", 1_000));
    }

    @Test
    public void elCineAvisaCuandoElServicioElegidoNoExiste() {
        abrir("chipCine", Escenario.CINE);
        Ua.tocarTexto(Escenario.EVENTO_CINE);
        assertTrue(Ua.hay("chipGroupServicios", Ua.ESPERA_MS));

        Ua.tocarTexto("VIP");

        assertTrue(Ua.hayTexto("Servicio no disponible", Ua.ESPERA_MS));
        assertTrue(Ua.hayTextoQueContiene(Escenario.CINE + " no cuenta con el servicio VIP.", Ua.ESPERA_MS));
        Ua.tocarTexto("Entendido");
    }

    @Test
    public void elMaximoDeBoletosPorUsuarioSeRespetaAlSeleccionar() {
        abrir("chipTeatro", Escenario.TEATRO);
        Ua.tocarTexto(Escenario.EVENTO_TEATRO);
        assertTrue(Ua.hay("rvZonas", Ua.ESPERA_MS));

        for (int i = 0; i < Escenario.MAX_BOLETOS_TEATRO + 2; i++) {
            Ua.tocarBotonDeFila(Escenario.PLATEA, "btnMas");
        }

        String esperado = Escenario.MAX_BOLETOS_TEATRO + " boletos";
        assertTrue("El total no debe pasar de " + esperado + " (actual: " + Ua.texto("tvZonaTotal") + ")",
                Ua.texto("tvZonaTotal").startsWith(esperado));
    }

    @Test
    public void unaZonaAgotadaApareceComoAgotada() {
        assertTrue(Compras.comprar(entorno.contexto, entorno.esc.usuario2, entorno.esc.teatroFuncion,
                Compras.porZona(entorno.esc.teatroPalco, 2500, 2), Compras.tarjetaCredito(entorno.esc)).exito());

        abrir("chipTeatro", Escenario.TEATRO);
        Ua.tocarTexto(Escenario.EVENTO_TEATRO);

        assertTrue(Ua.hayTexto("Agotado", Ua.ESPERA_MS));
    }

    @Test
    public void unaFuncionPasadaNoSeOfreceAlUsuario() {
        abrir("chipTeatro", Escenario.TEATRO);

        assertTrue(Ua.hayTexto(Escenario.EVENTO_TEATRO, Ua.ESPERA_MS));
        assertFalse(Ua.hayTexto("Obra Pasada", 1_000));
    }
}
