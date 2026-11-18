package com.example.ticketmaster_mobile.adquisicion;

import static org.junit.Assert.assertEquals;

import com.example.ticketmaster_mobile.PaymentValidationUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;

/**
 * PU-04 · Validación de la fecha de vencimiento (MM/AA) de la tarjeta.
 * Menú: Adquirir Boletos → Pago (PaymentActivity).
 * Unidad: {@link PaymentValidationUtils#errorVencimiento(String, Calendar)}.
 * La fecha actual se fija en 8-oct-2026 para que la prueba sea determinista.
 */
@RunWith(Parameterized.class)
public class PU04_VencimientoTarjetaTest {

    @Parameters(name = "{0}")
    public static Collection<Object[]> casos() {
        return Arrays.asList(new Object[][]{
                // caso, vencimientoIngresado, errorEsperado
                {"C1 mes en curso (aún válido)", "10/26", null},
                {"C2 año futuro", "01/30", null},
                {"C3 mes anterior (vencida)", "09/26", "La tarjeta está vencida"},
                {"C4 año anterior (vencida)", "12/25", "La tarjeta está vencida"},
                {"C5 mes fuera de rango (13)", "13/27", "El mes debe estar entre 01 y 12"},
                {"C6 formato incorrecto", "1026", "Usa el formato MM/AA"},
                {"C7 campo vacío", "", "Ingresa la fecha de vencimiento"},
        });
    }

    private final String caso, vencimiento, esperado;

    public PU04_VencimientoTarjetaTest(String caso, String vencimiento, String esperado) {
        this.caso = caso;
        this.vencimiento = vencimiento;
        this.esperado = esperado;
    }

    @Test
    public void errorVencimiento_detectaTarjetasVencidasYFormatoInvalido() {
        Calendar hoy = Calendar.getInstance();
        hoy.clear();
        hoy.set(2026, Calendar.OCTOBER, 8);

        String obtenido = PaymentValidationUtils.errorVencimiento(vencimiento, hoy);
        EvidenciaPruebas.registrar("PU-04", caso,
                "vencimiento=\"" + vencimiento + "\", hoy=2026-10-08", esperado, obtenido);
        assertEquals(esperado, obtenido);
    }
}
