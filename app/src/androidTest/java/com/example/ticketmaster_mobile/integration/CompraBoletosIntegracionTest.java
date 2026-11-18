package com.example.ticketmaster_mobile.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.ticketmaster_mobile.data.ReglasNegocio;
import com.example.ticketmaster_mobile.data.entity.Pago;
import com.example.ticketmaster_mobile.data.entity.WhatsappOutbox;
import com.example.ticketmaster_mobile.integration.soporte.Compras;
import com.example.ticketmaster_mobile.integration.soporte.Compras.Resultado;
import com.example.ticketmaster_mobile.integration.soporte.EntornoRule;
import com.example.ticketmaster_mobile.integration.soporte.Escenario;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integración de la compra de boletos: reglas de negocio (restricciones) y pagos, ejecutadas contra
 * el repositorio real y la base de datos Room. Las pruebas de pantalla están en CompraBoletosUiTest.
 */
@RunWith(AndroidJUnit4.class)
public class CompraBoletosIntegracionTest {

    @Rule
    public final EntornoRule entorno = new EntornoRule();

    private Escenario e() {
        return entorno.esc;
    }

    private Resultado comprarTeatro(int usuario, int n) {
        return Compras.comprar(entorno.contexto, usuario, e().teatroFuncion,
                Compras.porZona(e().teatroPlatea, 1000, n), Compras.tarjetaCredito(e()));
    }

    private void assertNadaPersistido() {
        assertEquals(0, e().contar("Orden"));
        assertEquals(0, e().contar("Boleto"));
        assertEquals(0, e().contar("Pago"));
        assertEquals(0, e().contar("WhatsappOutbox"));
    }

    // ------------------------------------------------------------------ compra correcta

    @Test
    public void compraValidaPersisteOrdenBoletosPagoYNotificacion() {
        Resultado r = comprarTeatro(e().usuario1, 2);

        assertTrue(r.error, r.exito());
        assertEquals(1, e().contar("Orden"));
        assertEquals(2, e().contar("Boleto"));
        assertEquals(1, e().contar("Pago"));
        assertEquals("Pagada", e().texto("SELECT EstadoOrden FROM Orden WHERE ID_Orden = " + r.idOrden));
        assertEquals(2000.0, e().numero("SELECT CAST(MontoTotal AS INTEGER) FROM Orden WHERE ID_Orden = " + r.idOrden), 0.001);
        assertEquals(e().usuario1, e().numero("SELECT ID_Usuario FROM Orden WHERE ID_Orden = " + r.idOrden));
        assertEquals(2, e().numero("SELECT COUNT(*) FROM Boleto WHERE ID_Orden = " + r.idOrden
                + " AND EstadoBoleto = 'Emitido' AND ID_Funcion = " + e().teatroFuncion));

        WhatsappOutbox cola = e().db.whatsappOutboxDao().buscarPorOrden(r.idOrden);
        assertNotNull("La compra debe encolar su notificación en la misma transacción", cola);
        assertEquals(WhatsappOutbox.PENDIENTE, cola.estado);
        assertEquals(0, cola.intentos);
    }

    @Test
    public void elMontoDeLaOrdenEsLaSumaDeLosPreciosDeSusBoletos() {
        Resultado r = Compras.comprar(entorno.contexto, e().usuario1, e().teatroFuncion,
                java.util.Arrays.asList(
                        new com.example.ticketmaster_mobile.data.repository.CompraRepository.ItemCompra(null, e().teatroPlatea, 1000),
                        new com.example.ticketmaster_mobile.data.repository.CompraRepository.ItemCompra(null, e().teatroPalco, 2500)),
                Compras.tarjetaCredito(e()));

        assertTrue(r.error, r.exito());
        assertEquals(3500, e().numero("SELECT CAST(MontoTotal AS INTEGER) FROM Orden"));
        assertEquals(3500, e().numero("SELECT CAST(Monto AS INTEGER) FROM Pago"));
        assertEquals(3500, e().numero("SELECT CAST(SUM(PrecioFinal) AS INTEGER) FROM Boleto"));
    }

    @Test
    public void compraDeCineAsignaLosAsientosElegidos() {
        Resultado r = Compras.comprar(entorno.contexto, e().usuario1, e().cineFuncion,
                Compras.asientos(e(), 90, "A1", "A2"), Compras.paypal(e(), "cliente@correo.com"));

        assertTrue(r.error, r.exito());
        assertEquals(2, e().numero("SELECT COUNT(*) FROM Boleto WHERE ID_Orden = " + r.idOrden));
        assertEquals(2, e().numero("SELECT COUNT(*) FROM Boleto b JOIN Asiento a ON a.ID_Asiento = b.ID_Asiento "
                + "WHERE b.ID_Orden = " + r.idOrden + " AND a.CodigoAsiento IN ('A1','A2')"));
    }

    // ------------------------------------------------------------------ restricción: máximo por usuario

    @Test
    public void permiteComprarExactamenteElMaximoPorUsuario() {
        Resultado r = comprarTeatro(e().usuario1, Escenario.MAX_BOLETOS_TEATRO);
        assertTrue(r.error, r.exito());
        assertEquals(Escenario.MAX_BOLETOS_TEATRO, e().contar("Boleto"));
    }

    @Test
    public void rechazaMasBoletosQueElMaximoEnUnaSolaCompra() {
        Resultado r = comprarTeatro(e().usuario1, Escenario.MAX_BOLETOS_TEATRO + 1);

        assertFalse(r.exito());
        assertEquals(ReglasNegocio.mensajeLimiteBoletos(Escenario.MAX_BOLETOS_TEATRO, 0,
                Escenario.MAX_BOLETOS_TEATRO + 1), r.error);
        assertNadaPersistido();
    }

    @Test
    public void elLimiteIncluyeLosBoletosDeComprasAnteriores() {
        assertTrue(comprarTeatro(e().usuario1, 2).exito());

        Resultado r = comprarTeatro(e().usuario1, 2);

        assertFalse(r.exito());
        assertEquals(ReglasNegocio.mensajeLimiteBoletos(Escenario.MAX_BOLETOS_TEATRO, 2, 2), r.error);
        assertEquals("La segunda compra rechazada no debe dejar rastro", 2, e().contar("Boleto"));
        assertEquals(1, e().contar("Orden"));
        assertEquals(1, e().contar("WhatsappOutbox"));
    }

    @Test
    public void conElMaximoAlcanzadoCualquierCompraNuevaSeRechaza() {
        assertTrue(comprarTeatro(e().usuario1, Escenario.MAX_BOLETOS_TEATRO).exito());

        Resultado r = comprarTeatro(e().usuario1, 1);

        assertFalse(r.exito());
        assertEquals(ReglasNegocio.mensajeLimiteBoletos(Escenario.MAX_BOLETOS_TEATRO,
                Escenario.MAX_BOLETOS_TEATRO, 1), r.error);
    }

    @Test
    public void elLimiteEsPorUsuarioYNoGlobal() {
        assertTrue(comprarTeatro(e().usuario1, Escenario.MAX_BOLETOS_TEATRO).exito());

        Resultado r = comprarTeatro(e().usuario2, Escenario.MAX_BOLETOS_TEATRO);

        assertTrue("Otro usuario conserva su propio cupo: " + r.error, r.exito());
        assertEquals(2 * Escenario.MAX_BOLETOS_TEATRO, e().contar("Boleto"));
    }

    @Test
    public void elLimiteEsPorFuncionYNoPorEstablecimiento() {
        assertTrue(comprarTeatro(e().usuario1, Escenario.MAX_BOLETOS_TEATRO).exito());

        Resultado r = Compras.comprar(entorno.contexto, e().usuario1, e().teatroFuncionRara,
                Compras.porZona(e().teatroPlatea, 1000, 1), Compras.tarjetaCredito(e()));

        assertTrue("Otra función del mismo teatro cuenta aparte: " + r.error, r.exito());
    }

    // ------------------------------------------------------------------ restricción: capacidad

    @Test
    public void rechazaCuandoLaZonaYaEstaAgotada() {
        assertTrue(Compras.comprar(entorno.contexto, e().usuario1, e().teatroFuncion,
                Compras.porZona(e().teatroPalco, 2500, 2), Compras.tarjetaCredito(e())).exito());

        Resultado r = Compras.comprar(entorno.contexto, e().usuario2, e().teatroFuncion,
                Compras.porZona(e().teatroPalco, 2500, 1), Compras.tarjetaCredito(e()));

        assertFalse(r.exito());
        assertEquals("La zona \"Palco\" está agotada para esta función.", r.error);
        assertEquals(2, e().contar("Boleto"));
    }

    @Test
    public void rechazaCuandoPidenMasDeLoQueQuedaYDiceCuantosQuedan() {
        assertTrue(Compras.comprar(entorno.contexto, e().usuario1, e().teatroFuncion,
                Compras.porZona(e().teatroPalco, 2500, 1), Compras.tarjetaCredito(e())).exito());

        Resultado r = Compras.comprar(entorno.contexto, e().usuario2, e().teatroFuncion,
                Compras.porZona(e().teatroPalco, 2500, 2), Compras.tarjetaCredito(e()));

        assertFalse(r.exito());
        assertEquals("Solo quedan 1 lugares en \"Palco\".", r.error);
        assertEquals(1, e().contar("Boleto"));
    }

    @Test
    public void laCapacidadSeCuentaPorFuncion() {
        assertTrue(Compras.comprar(entorno.contexto, e().usuario1, e().teatroFuncion,
                Compras.porZona(e().teatroPalco, 2500, 2), Compras.tarjetaCredito(e())).exito());

        Resultado r = Compras.comprar(entorno.contexto, e().usuario2, e().teatroFuncionRara,
                Compras.porZona(e().teatroPalco, 2500, 2), Compras.tarjetaCredito(e()));

        assertTrue("El Palco de otra función sigue libre: " + r.error, r.exito());
    }

    @Test
    public void rechazaUnaZonaDeOtroEstablecimiento() {
        Resultado r = Compras.comprar(entorno.contexto, e().usuario1, e().teatroFuncion,
                Compras.porZona(e().cineZona, 90, 1), Compras.tarjetaCredito(e()));

        assertFalse(r.exito());
        assertEquals("La zona seleccionada no pertenece a este establecimiento.", r.error);
        assertNadaPersistido();
    }

    // ------------------------------------------------------------------ restricción: asiento único

    @Test
    public void elMismoAsientoNoSePuedeVenderDosVeces() {
        assertTrue(Compras.comprar(entorno.contexto, e().usuario1, e().cineFuncion,
                Compras.asientos(e(), 90, "B3"), Compras.tarjetaCredito(e())).exito());

        Resultado r = Compras.comprar(entorno.contexto, e().usuario2, e().cineFuncion,
                Compras.asientos(e(), 90, "B3"), Compras.tarjetaCredito(e()));

        assertFalse(r.exito());
        assertEquals("Uno o más asientos ya fueron comprados por otra persona. Elige otros asientos.", r.error);
        assertEquals(1, e().contar("Orden"));
        assertEquals(1, e().contar("Boleto"));
        assertEquals(1, e().contar("Pago"));
        assertEquals(1, e().contar("WhatsappOutbox"));
    }

    @Test
    public void unaCompraConUnAsientoLibreYUnoOcupadoSeRevierteCompleta() {
        assertTrue(Compras.comprar(entorno.contexto, e().usuario1, e().cineFuncion,
                Compras.asientos(e(), 90, "A5"), Compras.tarjetaCredito(e())).exito());

        Resultado r = Compras.comprar(entorno.contexto, e().usuario2, e().cineFuncion,
                Compras.asientos(e(), 90, "A4", "A5"), Compras.tarjetaCredito(e()));

        assertFalse(r.exito());
        assertEquals("El asiento libre A4 tampoco debe quedar vendido", 0, e().numero(
                "SELECT COUNT(*) FROM Boleto b JOIN Asiento a ON a.ID_Asiento = b.ID_Asiento "
                        + "WHERE a.CodigoAsiento = 'A4'"));
        assertEquals(1, e().contar("Boleto"));
    }

    @Test
    public void elMismoAsientoEnOtraFuncionSiSePuedeComprar() {
        int otraFuncion = e().funcionAdicional(e().cine, "Película Dos",
                com.example.ticketmaster_mobile.integration.soporte.Fechas.manana(23, 0), "A");
        assertTrue(Compras.comprar(entorno.contexto, e().usuario1, e().cineFuncion,
                Compras.asientos(e(), 90, "B1"), Compras.tarjetaCredito(e())).exito());

        Resultado r = Compras.comprar(entorno.contexto, e().usuario2, otraFuncion,
                Compras.asientos(e(), 90, "B1"), Compras.tarjetaCredito(e()));

        assertTrue(r.error, r.exito());
    }

    @Test
    public void dosCompradoresSimultaneosDelMismoAsientoSoloUnoGana() throws Exception {
        CountDownLatch salida = new CountDownLatch(1);
        CountDownLatch terminan = new CountDownLatch(2);
        AtomicInteger exitos = new AtomicInteger();
        AtomicInteger rechazos = new AtomicInteger();

        for (int usuario : new int[]{e().usuario1, e().usuario2}) {
            new Thread(() -> {
                try {
                    salida.await();
                    Resultado r = Compras.comprar(entorno.contexto, usuario, e().cineFuncion,
                            Compras.asientos(e(), 90, "B5"), Compras.tarjetaCredito(e()));
                    (r.exito() ? exitos : rechazos).incrementAndGet();
                } catch (InterruptedException ignorada) {
                } finally {
                    terminan.countDown();
                }
            }).start();
        }
        salida.countDown();
        assertTrue(terminan.await(30, java.util.concurrent.TimeUnit.SECONDS));

        assertEquals(1, exitos.get());
        assertEquals(1, rechazos.get());
        assertEquals(1, e().contar("Boleto"));
        assertEquals(1, e().contar("Orden"));
    }

    // ------------------------------------------------------------------ restricción: fecha y día de venta

    @Test
    public void rechazaUnaFuncionQueYaPaso() {
        Resultado r = Compras.comprar(entorno.contexto, e().usuario1, e().teatroFuncionPasada,
                Compras.porZona(e().teatroPlatea, 1000, 1), Compras.tarjetaCredito(e()));

        assertFalse(r.exito());
        assertEquals("Esta función ya comenzó o ya pasó.", r.error);
        assertNadaPersistido();
    }

    @Test
    public void rechazaLaVentaEnUnDiaNoLaboralDelEstablecimiento() {
        Resultado r = Compras.comprar(entorno.contexto, e().usuario1, e().teatroCerradoFuncion,
                Compras.porZona(zonaDe(e().teatroCerrado), 500, 1), Compras.tarjetaCredito(e()));

        assertFalse(r.exito());
        assertTrue(r.error, r.error.startsWith("Hoy no se pueden adquirir boletos."));
        assertTrue(r.error, r.error.contains(Escenario.TEATRO_CERRADO));
        assertNadaPersistido();
    }

    @Test
    public void rechazaLaVentaEnUnDiaFestivo() {
        Resultado r = Compras.comprar(entorno.contexto, e().usuario1, e().teatroFestivoFuncion,
                Compras.porZona(zonaDe(e().teatroFestivo), 500, 1), Compras.tarjetaCredito(e()));

        assertFalse(r.exito());
        assertTrue(r.error, r.error.contains("día festivo (" + Escenario.DESCRIPCION_FESTIVO + ")"));
        assertNadaPersistido();
    }

    @Test
    public void rechazaUnaFuncionInexistente() {
        Resultado r = Compras.comprar(entorno.contexto, e().usuario1, 987654,
                Compras.porZona(e().teatroPlatea, 1000, 1), Compras.tarjetaCredito(e()));

        assertFalse(r.exito());
        assertEquals("La función seleccionada ya no existe.", r.error);
    }

    @Test
    public void rechazaUnaCompraSinBoletos() {
        Resultado r = Compras.comprar(entorno.contexto, e().usuario1, e().teatroFuncion,
                java.util.Collections.emptyList(), Compras.tarjetaCredito(e()));

        assertFalse(r.exito());
        assertEquals("Selecciona al menos un boleto", r.error);
        assertNadaPersistido();
    }

    // ------------------------------------------------------------------ pagos

    @Test
    public void elPagoConTarjetaGuardaSoloLosUltimosCuatroDigitos() {
        Resultado r = comprarTeatro(e().usuario1, 1);
        assertTrue(r.error, r.exito());

        Pago pago = e().db.pagoDao().buscarPorOrden(r.idOrden);
        assertNotNull(pago);
        assertEquals("Aprobado", pago.estadoPago);
        assertEquals("1111", pago.ultimosCuatroDigitos);
        assertNull(pago.cuentaPaypal);
        assertEquals(e().metodoCredito, pago.idMetodoPago);
        assertEquals(1000.0, pago.monto, 0.001);
        assertNotNull(pago.referenciaExterna);
        assertTrue(pago.referenciaExterna, pago.referenciaExterna.startsWith("TM-"));

        for (String celda : e().volcarTodoComoTexto()) {
            if (celda.startsWith("WhatsappOutbox.")) continue; // marcas de tiempo en milisegundos
            assertFalse("No debe guardarse un número de tarjeta completo: " + celda,
                    celda.matches("^[A-Za-z]+\\.[A-Za-z_]+=\\d{13,19}$"));
        }
    }

    @Test
    public void elPagoConPaypalGuardaElCorreoYNoDigitosDeTarjeta() {
        Resultado r = Compras.comprar(entorno.contexto, e().usuario1, e().cineFuncion,
                Compras.asientos(e(), 90, "A1"), Compras.paypal(e(), "cliente@correo.com"));
        assertTrue(r.error, r.exito());

        Pago pago = e().db.pagoDao().buscarPorOrden(r.idOrden);
        assertEquals("cliente@correo.com", pago.cuentaPaypal);
        assertNull(pago.ultimosCuatroDigitos);
        assertEquals(e().metodoPaypal, pago.idMetodoPago);
    }

    @Test
    public void elPagoConTarjetaDeDebitoUsaSuMetodo() {
        Resultado r = Compras.comprar(entorno.contexto, e().usuario1, e().teatroFuncion,
                Compras.porZona(e().teatroPlatea, 1000, 1), Compras.tarjetaDebito(e()));
        assertTrue(r.error, r.exito());

        Pago pago = e().db.pagoDao().buscarPorOrden(r.idOrden);
        assertEquals(e().metodoDebito, pago.idMetodoPago);
        assertEquals("4242", pago.ultimosCuatroDigitos);
    }

    @Test
    public void unaCompraRechazadaNoGeneraPagoNiCobro() {
        comprarTeatro(e().usuario1, Escenario.MAX_BOLETOS_TEATRO + 1);
        assertEquals(0, e().contar("Pago"));
        assertEquals(0, e().numero("SELECT COUNT(*) FROM Pago WHERE Monto > 0"));
    }

    // ------------------------------------------------------------------ integración con WAHA

    @Test
    public void conWahaDesactivadoLaCompraSigueFuncionandoYNoEncola() {
        entorno.desactivarWaha();

        Resultado r = comprarTeatro(e().usuario1, 1);

        assertTrue(r.error, r.exito());
        assertEquals(1, e().contar("Orden"));
        assertEquals("Sin WAHA configurado no hay nada que enviar", 0, e().contar("WhatsappOutbox"));
    }

    @Test
    public void laCompraNoDependeDeQueWahaEsteDisponible() {
        entorno.waha.close();

        Resultado r = comprarTeatro(e().usuario1, 1);

        assertTrue("La compra no hace llamadas de red: " + r.error, r.exito());
        assertEquals(1, e().contar("WhatsappOutbox"));
    }

    @Test
    public void cadaOrdenGeneraUnaSolaNotificacion() {
        Resultado a = comprarTeatro(e().usuario1, 1);
        Resultado b = comprarTeatro(e().usuario2, 1);

        assertTrue(a.exito() && b.exito());
        assertEquals(2, e().contar("WhatsappOutbox"));
        assertEquals(2, e().numero("SELECT COUNT(DISTINCT ID_Orden) FROM WhatsappOutbox"));
    }

    private int zonaDe(int idEstablecimiento) {
        return (int) e().numero("SELECT ID_SeccionZona FROM Seccion_Zona WHERE ID_Establecimiento = "
                + idEstablecimiento + " LIMIT 1");
    }
}