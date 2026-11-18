package com.example.ticketmaster_mobile.integration;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.ticketmaster_mobile.MainActivity;
import com.example.ticketmaster_mobile.R;
import com.example.ticketmaster_mobile.data.ReglasNegocio;
import com.example.ticketmaster_mobile.data.entity.Pago;
import com.example.ticketmaster_mobile.integration.soporte.Coincidencias;
import com.example.ticketmaster_mobile.integration.soporte.Compras;
import com.example.ticketmaster_mobile.integration.soporte.EntornoRule;
import com.example.ticketmaster_mobile.integration.soporte.Escenario;
import com.example.ticketmaster_mobile.integration.soporte.Fechas;
import com.example.ticketmaster_mobile.integration.soporte.Flujos;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

/**
 * Integración de punta a punta dentro de la app (Espresso): catálogo → función → boletos → pago →
 * confirmación → historial, con la base de datos real en memoria. Verifica pantalla y datos guardados.
 */
@RunWith(AndroidJUnit4.class)
public class CompraBoletosUiTest {

    @Rule
    public final EntornoRule entorno = new EntornoRule();

    @Before
    public void iniciarSesion() {
        entorno.esc.iniciarSesionComo(entorno.esc.usuario1);
    }

    private Escenario e() {
        return entorno.esc;
    }

    private static String dinero(double monto) {
        return String.format(Locale.getDefault(), "$%,.2f", monto);
    }

    private ActivityScenario<MainActivity> abrirApp() {
        ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class);
        Flujos.irATickets();
        return s;
    }

    private void llegarAZonasDelTeatro() {
        Flujos.abrirEstablecimiento(R.id.chipTeatro, Escenario.TEATRO);
        Flujos.abrirFuncion(Escenario.EVENTO_TEATRO);
        onView(withId(R.id.rvZonas)).check(matches(isDisplayed()));
    }

    private void llegarAlPagoDelTeatro(String zona, int cantidad) {
        llegarAZonasDelTeatro();
        Flujos.masEnZona(zona, cantidad);
        Flujos.tocar(R.id.btnZonaContinuar);
        onView(withId(R.id.btnConfirmarPago)).check(matches(isDisplayed()));
    }

    // ------------------------------------------------------------------ catálogo (lectura de la DB)

    @Test
    public void elCatalogoMuestraLosEstablecimientosDeLaBase() {
        try (ActivityScenario<MainActivity> s = abrirApp()) {
            for (String nombre : new String[]{Escenario.TEATRO, Escenario.CINE,
                    Escenario.TEATRO_CERRADO, Escenario.TEATRO_FESTIVO}) {
                onView(withId(R.id.rvEstablecimientos)).perform(
                        androidx.test.espresso.contrib.RecyclerViewActions.scrollTo(hasDescendant(withText(nombre))));
                onView(withText(nombre)).check(matches(isDisplayed()));
            }
        }
    }

    @Test
    public void elFiltroPorTipoMuestraSoloEseTipo() {
        try (ActivityScenario<MainActivity> s = abrirApp()) {
            Flujos.tocar(R.id.chipCine);
            onView(withText(Escenario.CINE)).check(matches(isDisplayed()));
            onView(withText(Escenario.TEATRO)).check(doesNotExist());

            Flujos.tocar(R.id.chipTeatro);
            onView(withText(Escenario.TEATRO)).check(matches(isDisplayed()));
            onView(withText(Escenario.CINE)).check(doesNotExist());

            Flujos.tocar(R.id.chipMuseo);
            onView(withText(Escenario.TEATRO)).check(doesNotExist());
            onView(withText(Escenario.CINE)).check(doesNotExist());

            Flujos.tocar(R.id.chipTodos);
            onView(withText(Escenario.CINE)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void laListaDeFuncionesMuestraSoloLasFuturas() {
        try (ActivityScenario<MainActivity> s = abrirApp()) {
            Flujos.abrirEstablecimiento(R.id.chipTeatro, Escenario.TEATRO);

            onView(withText(Escenario.EVENTO_TEATRO)).check(matches(isDisplayed()));
            onView(withText("Obra Pasada")).check(doesNotExist());
        }
    }

    @Test
    public void unEventoConCaracteresEspecialesSeMuestraCompleto() {
        try (ActivityScenario<MainActivity> s = abrirApp()) {
            Flujos.abrirEstablecimiento(R.id.chipTeatro, Escenario.TEATRO);
            onView(withText(Escenario.EVENTO_RARO)).check(matches(isDisplayed()));
        }
    }

    // ------------------------------------------------------------------ compra completa de teatro

    @Test
    public void compraDeTeatroConTarjetaDeCreditoDeExtremoAExtremo() {
        try (ActivityScenario<MainActivity> s = abrirApp()) {
            llegarAZonasDelTeatro();
            Flujos.masEnZona(Escenario.PLATEA, 2);
            onView(withId(R.id.tvZonaTotal)).check(matches(withText(
                    String.format(Locale.getDefault(), "%d boleto%s · Total: $%,.2f", 2, "s", 2000.0))));
            Flujos.tocar(R.id.btnZonaContinuar);

            Flujos.elegirMetodo("Tarjeta de Crédito");
            Flujos.llenarTarjeta("4111111111111111", "Juan Perez", Fechas.vencimientoValido(), "987");
            Flujos.confirmarPago();

            onView(withId(R.id.tvConfCantidad)).check(matches(withText("Cantidad de boletos: 2")));
            onView(withId(R.id.tvConfMonto)).check(matches(withText("Monto pagado: " + dinero(2000))));
            onView(withId(R.id.tvConfDetalle)).check(matches(withText(containsString(Escenario.PLATEA + " ×2"))));
            onView(withId(R.id.tvConfMetodo)).check(matches(withText(containsString("•••• 1111"))));
            onView(withId(R.id.tvConfOrden)).check(matches(withText(containsString("Ref. TM-"))));
        }

        assertEquals(1, e().contar("Orden"));
        assertEquals(2, e().contar("Boleto"));
        assertEquals(1, e().contar("Pago"));
        assertEquals(1, e().contar("WhatsappOutbox"));
        assertEquals(e().usuario1, e().numero("SELECT ID_Usuario FROM Orden"));
        assertEquals(2000, e().numero("SELECT CAST(MontoTotal AS INTEGER) FROM Orden"));

        Pago pago = e().db.pagoDao().buscarPorOrden(e().ultimaOrden());
        assertEquals("1111", pago.ultimosCuatroDigitos);
        assertEquals("Juan Perez", pago.nombreTitular);
        assertEquals("Aprobado", pago.estadoPago);
        for (String celda : e().volcarTodoComoTexto()) {
            assertTrue("El número completo de la tarjeta no debe guardarse: " + celda,
                    !celda.contains("4111111111111111"));
            assertTrue("El CVV no debe guardarse: " + celda, !celda.endsWith("=987"));
        }
    }

    @Test
    public void despuesDeComprarElHistorialDelPerfilMuestraLaOrden() {
        try (ActivityScenario<MainActivity> s = abrirApp()) {
            llegarAlPagoDelTeatro(Escenario.PLATEA, 1);
            Flujos.elegirMetodo("Tarjeta de Débito");
            Flujos.llenarTarjeta("5555555555554444", "Juan Perez", Fechas.vencimientoValido(), "321");
            Flujos.confirmarPago();

            Flujos.tocar(R.id.btnConfPerfil);
            Flujos.tocar(R.id.nav_profile);

            onView(withId(R.id.tvSinCompras)).check(matches(not(isDisplayed())));
            onView(allOf(withText("Pagada"), isDescendantOfA(withId(R.id.rvHistorialOrdenes))))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void sinComprasElPerfilIndicaQueNoHayHistorial() {
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            Flujos.tocar(R.id.nav_profile);
            onView(withId(R.id.tvSinCompras)).check(matches(isDisplayed()));
        }
    }

    // ------------------------------------------------------------------ compra completa de cine con PayPal

    @Test
    public void compraDeCineConPaypalDeExtremoAExtremo() {
        try (ActivityScenario<MainActivity> s = abrirApp()) {
            Flujos.abrirEstablecimiento(R.id.chipCine, Escenario.CINE);
            Flujos.abrirFuncion(Escenario.EVENTO_CINE);
            Flujos.elegirAsiento("A1");
            Flujos.elegirAsiento("A2");
            onView(withId(R.id.btnContinuarPago)).check(matches(isEnabled()));
            Flujos.tocar(R.id.btnContinuarPago);

            Flujos.elegirMetodo("PayPal");
            Flujos.llenarPaypal("cliente@correo.com", "Juan Perez");
            Flujos.confirmarPago();

            onView(withId(R.id.tvConfCantidad)).check(matches(withText("Cantidad de boletos: 2")));
            onView(withId(R.id.tvConfMonto)).check(matches(withText("Monto pagado: " + dinero(180))));
            onView(withId(R.id.tvConfDetalle)).check(matches(withText(containsString("asientos: A1, A2"))));
            onView(withId(R.id.tvConfMetodo)).check(matches(withText(containsString("cliente@correo.com"))));
        }

        Pago pago = e().db.pagoDao().buscarPorOrden(e().ultimaOrden());
        assertEquals("cliente@correo.com", pago.cuentaPaypal);
        assertNull(pago.ultimosCuatroDigitos);
        assertEquals(e().metodoPaypal, pago.idMetodoPago);
        assertEquals(2, e().numero("SELECT COUNT(*) FROM Boleto b JOIN Asiento a ON a.ID_Asiento = b.ID_Asiento "
                + "WHERE a.CodigoAsiento IN ('A1','A2')"));
    }

    @Test
    public void unAsientoYaVendidoApareceOcupadoParaOtroUsuario() {
        assertTrue(Compras.comprar(entorno.contexto, e().usuario2, e().cineFuncion,
                Compras.asientos(e(), 90, "A1"), Compras.tarjetaCredito(e())).exito());

        try (ActivityScenario<MainActivity> s = abrirApp()) {
            Flujos.abrirEstablecimiento(R.id.chipCine, Escenario.CINE);
            Flujos.abrirFuncion(Escenario.EVENTO_CINE);

            // Tocar el asiento ocupado no lo selecciona: el total sigue en cero
            onView(allOf(withId(R.id.tvAsientoCodigo), withText("A1"))).perform(click());
            onView(withId(R.id.btnContinuarPago)).check(matches(not(isEnabled())));

            // Uno libre sí
            Flujos.elegirAsiento("A2");
            onView(withId(R.id.btnContinuarPago)).check(matches(isEnabled()));
        }
    }

    @Test
    public void unServicioQueElCineNoOfreceMuestraAviso() {
        try (ActivityScenario<MainActivity> s = abrirApp()) {
            Flujos.abrirEstablecimiento(R.id.chipCine, Escenario.CINE);
            Flujos.abrirFuncion(Escenario.EVENTO_CINE);

            onView(allOf(withText("VIP"), isDescendantOfA(withId(R.id.chipGroupServicios))))
                    .perform(scrollTo(), click());

            onView(withText("Servicio no disponible")).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(containsString(Escenario.CINE + " no cuenta con el servicio VIP.")))
                    .inRoot(isDialog()).check(matches(isDisplayed()));
            Flujos.cerrarDialogo("Entendido");
            onView(withId(R.id.btnContinuarPago)).check(matches(not(isEnabled())));
        }
    }

    // ------------------------------------------------------------------ restricciones en pantalla

    @Test
    public void elBotonMasNoPermiteSuperarElMaximoPorUsuario() {
        try (ActivityScenario<MainActivity> s = abrirApp()) {
            llegarAZonasDelTeatro();
            onView(withId(R.id.tvZonaLimite)).check(matches(withText(
                    "Máximo " + Escenario.MAX_BOLETOS_TEATRO + " boletos por usuario")));

            Flujos.masEnZona(Escenario.PLATEA, Escenario.MAX_BOLETOS_TEATRO + 2);

            onView(withId(R.id.tvZonaTotal)).check(matches(withText(
                    String.format(Locale.getDefault(), "%d boletos · Total: $%,.2f",
                            Escenario.MAX_BOLETOS_TEATRO, 1000.0 * Escenario.MAX_BOLETOS_TEATRO))));
        }
    }

    @Test
    public void lasComprasPreviasReducenElCupoMostradoEnPantalla() {
        assertTrue(Compras.comprar(entorno.contexto, e().usuario1, e().teatroFuncion,
                Compras.porZona(e().teatroPlatea, 1000, 2), Compras.tarjetaCredito(e())).exito());

        try (ActivityScenario<MainActivity> s = abrirApp()) {
            llegarAZonasDelTeatro();
            onView(withId(R.id.tvZonaLimite)).check(matches(withText(
                    "Máximo 3 boletos por usuario (ya tienes 2, puedes comprar 1 más)")));

            Flujos.masEnZona(Escenario.PLATEA, 3);

            onView(withId(R.id.tvZonaTotal)).check(matches(withText(
                    String.format(Locale.getDefault(), "%d boleto%s · Total: $%,.2f", 1, "", 1000.0))));
        }
    }

    @Test
    public void conElMaximoAlcanzadoLaPantallaLoIndica() {
        assertTrue(Compras.comprar(entorno.contexto, e().usuario1, e().teatroFuncion,
                Compras.porZona(e().teatroPlatea, 1000, Escenario.MAX_BOLETOS_TEATRO),
                Compras.tarjetaCredito(e())).exito());

        try (ActivityScenario<MainActivity> s = abrirApp()) {
            llegarAZonasDelTeatro();
            onView(withId(R.id.tvZonaLimite)).check(matches(withText(
                    "Ya tienes el máximo de " + Escenario.MAX_BOLETOS_TEATRO + " boletos para esta función.")));
            Flujos.masEnZona(Escenario.PLATEA, 1);
            onView(withId(R.id.btnZonaContinuar)).check(matches(not(isEnabled())));
        }
    }

    @Test
    public void unaZonaAgotadaSeMarcaYNoSePuedeAumentar() {
        assertTrue(Compras.comprar(entorno.contexto, e().usuario2, e().teatroFuncion,
                Compras.porZona(e().teatroPalco, 2500, 2), Compras.tarjetaCredito(e())).exito());

        try (ActivityScenario<MainActivity> s = abrirApp()) {
            llegarAZonasDelTeatro();

            onView(withText("Agotado")).check(matches(isDisplayed()));
            onView(Coincidencias.hijoDeFila(R.id.btnMas, Escenario.PALCO)).check(matches(not(isEnabled())));
            onView(Coincidencias.hijoDeFila(R.id.btnMas, Escenario.PLATEA)).check(matches(isEnabled()));
        }
    }

    @Test
    public void sinSeleccionarBoletosNoSePuedeContinuar() {
        try (ActivityScenario<MainActivity> s = abrirApp()) {
            llegarAZonasDelTeatro();
            onView(withId(R.id.btnZonaContinuar)).check(matches(not(isEnabled())));

            Flujos.masEnZona(Escenario.PLATEA, 1);
            onView(withId(R.id.btnZonaContinuar)).check(matches(isEnabled()));
            Flujos.menosEnZona(Escenario.PLATEA, 1);
            onView(withId(R.id.btnZonaContinuar)).check(matches(not(isEnabled())));
        }
    }

    @Test
    public void unEstablecimientoCerradoHoyMuestraAvisoYNoDejaComprar() {
        try (ActivityScenario<MainActivity> s = abrirApp()) {
            Flujos.abrirEstablecimiento(R.id.chipTeatro, Escenario.TEATRO_CERRADO);

            onView(withId(R.id.tvAvisoDiaNoLaboral)).check(matches(isDisplayed()));
            onView(withId(R.id.tvAvisoDiaNoLaboral)).check(matches(withText(containsString("Hoy no hay venta de boletos."))));

            Flujos.abrirFuncion(Escenario.EVENTO_CERRADO);

            onView(withText("Hoy no hay venta de boletos")).inRoot(isDialog()).check(matches(isDisplayed()));
            Flujos.cerrarDialogo("Entendido");
            onView(withId(R.id.rvZonas)).check(doesNotExist());
        }
        assertEquals(0, e().contar("Orden"));
    }

    @Test
    public void unDiaFestivoMuestraSuMotivoYNoDejaComprar() {
        try (ActivityScenario<MainActivity> s = abrirApp()) {
            Flujos.abrirEstablecimiento(R.id.chipTeatro, Escenario.TEATRO_FESTIVO);

            onView(withId(R.id.tvAvisoDiaNoLaboral)).check(matches(isDisplayed()));
            onView(withId(R.id.tvAvisoDiaNoLaboral)).check(matches(
                    withText(containsString(Escenario.DESCRIPCION_FESTIVO))));

            Flujos.abrirFuncion(Escenario.EVENTO_FESTIVO);
            onView(withText("Hoy no hay venta de boletos")).inRoot(isDialog()).check(matches(isDisplayed()));
            Flujos.cerrarDialogo("Entendido");
        }
        assertEquals(0, e().contar("Orden"));
    }

    @Test
    public void unEstablecimientoConServicioHoyNoMuestraAviso() {
        try (ActivityScenario<MainActivity> s = abrirApp()) {
            Flujos.abrirEstablecimiento(R.id.chipTeatro, Escenario.TEATRO);
            onView(withId(R.id.tvAvisoDiaNoLaboral)).check(matches(not(isDisplayed())));
        }
    }

    // ------------------------------------------------------------------ validación del pago

    @Test
    public void elFormularioVacioMuestraErroresYNoCompra() {
        try (ActivityScenario<MainActivity> s = abrirApp()) {
            llegarAlPagoDelTeatro(Escenario.PLATEA, 1);
            Flujos.elegirMetodo("Tarjeta de Crédito");

            Flujos.confirmarPago();

            onView(withId(R.id.tilNumeroTarjeta)).perform(scrollTo()).check(matches(
                    Coincidencias.conError("Ingresa el número de tarjeta")));
            onView(withId(R.id.tilNombreTarjeta)).check(matches(
                    Coincidencias.conError("Ingresa el nombre del titular")));
            onView(withId(R.id.tilVencimiento)).check(matches(
                    Coincidencias.conError("Ingresa la fecha de vencimiento")));
            onView(withId(R.id.tilCvv)).check(matches(Coincidencias.conError("Ingresa el CVV")));
            onView(withId(R.id.btnConfirmarPago)).check(matches(isDisplayed()));
        }
        assertEquals(0, e().contar("Orden"));
        assertEquals(0, e().contar("Pago"));
    }

    @Test
    public void numeroDeTarjetaIncompletoCvvCortoYTarjetaVencidaSeRechazan() {
        try (ActivityScenario<MainActivity> s = abrirApp()) {
            llegarAlPagoDelTeatro(Escenario.PLATEA, 1);
            Flujos.elegirMetodo("Tarjeta de Crédito");
            Flujos.llenarTarjeta("4111", "Juan Perez", "01/20", "12");

            Flujos.confirmarPago();

            onView(withId(R.id.tilNumeroTarjeta)).perform(scrollTo()).check(matches(
                    Coincidencias.conError("El número de tarjeta debe tener 16 dígitos")));
            onView(withId(R.id.tilVencimiento)).check(matches(Coincidencias.conError("La tarjeta está vencida")));
            onView(withId(R.id.tilCvv)).check(matches(Coincidencias.conError("El CVV son 3 dígitos")));
            onView(withId(R.id.tilNombreTarjeta)).check(matches(Coincidencias.sinError()));
        }
        assertEquals(0, e().contar("Orden"));
    }

    @Test
    public void correoDePaypalInvalidoSeRechaza() {
        try (ActivityScenario<MainActivity> s = abrirApp()) {
            llegarAlPagoDelTeatro(Escenario.PLATEA, 1);
            Flujos.elegirMetodo("PayPal");
            Flujos.llenarPaypal("no-es-correo", "Juan Perez");

            Flujos.confirmarPago();

            onView(withId(R.id.tilCorreoPaypal)).perform(scrollTo()).check(matches(
                    Coincidencias.conError("Correo electrónico inválido")));
        }
        assertEquals(0, e().contar("Orden"));
    }

    @Test
    public void corregirLosDatosPermiteCompletarElPago() {
        try (ActivityScenario<MainActivity> s = abrirApp()) {
            llegarAlPagoDelTeatro(Escenario.PLATEA, 1);
            Flujos.elegirMetodo("Tarjeta de Crédito");
            Flujos.llenarTarjeta("4111", "Juan Perez", Fechas.vencimientoValido(), "123");
            Flujos.confirmarPago();
            onView(withId(R.id.tilNumeroTarjeta)).perform(scrollTo()).check(matches(
                    Coincidencias.conError("El número de tarjeta debe tener 16 dígitos")));

            Flujos.escribirConScroll(R.id.etNumeroTarjeta, "4111111111111111");
            Flujos.confirmarPago();

            onView(withId(R.id.tvConfMonto)).check(matches(withText("Monto pagado: " + dinero(1000))));
        }
        assertEquals(1, e().contar("Orden"));
    }

    // ------------------------------------------------------------------ reglas evaluadas al pagar

    @Test
    public void siEnElMomentoDePagarYaSeSuperoElLimiteSeMuestraElMotivoYNoSeCobra() {
        try (ActivityScenario<MainActivity> s = abrirApp()) {
            llegarAZonasDelTeatro();
            Flujos.masEnZona(Escenario.PLATEA, 2);
            Flujos.tocar(R.id.btnZonaContinuar);
            Flujos.elegirMetodo("Tarjeta de Crédito");
            Flujos.llenarTarjeta("4111111111111111", "Juan Perez", Fechas.vencimientoValido(), "123");

            // Mientras el usuario capturaba su tarjeta compró 2 boletos desde otro dispositivo
            assertTrue(Compras.comprar(entorno.contexto, e().usuario1, e().teatroFuncion,
                    Compras.porZona(e().teatroPlatea, 1000, 2), Compras.tarjetaDebito(e())).exito());

            Flujos.confirmarPago();

            onView(withText("No se pudo completar el pago")).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(ReglasNegocio.mensajeLimiteBoletos(Escenario.MAX_BOLETOS_TEATRO, 2, 2)))
                    .inRoot(isDialog()).check(matches(isDisplayed()));
            Flujos.cerrarDialogo("Entendido");
            onView(withId(R.id.btnConfirmarPago)).check(matches(isEnabled()));
        }
        assertEquals("Solo debe existir la compra previa", 1, e().contar("Orden"));
        assertEquals(2, e().contar("Boleto"));
    }

    @Test
    public void sinSesionActivaNoSePuedePagar() {
        try (ActivityScenario<MainActivity> s = abrirApp()) {
            llegarAlPagoDelTeatro(Escenario.PLATEA, 1);
            Flujos.elegirMetodo("Tarjeta de Crédito");
            Flujos.llenarTarjeta("4111111111111111", "Juan Perez", Fechas.vencimientoValido(), "123");
            e().cerrarSesion();

            Flujos.confirmarPago();

            onView(withId(R.id.btnConfirmarPago)).check(matches(isDisplayed()));
        }
        assertEquals(0, e().contar("Orden"));
    }
}
