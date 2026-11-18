package com.example.ticketmaster_mobile.adquisicion;

import static org.junit.Assert.assertEquals;

import com.example.ticketmaster_mobile.PaymentValidationUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;

/**
 * PU-03 · Validación del número de tarjeta en el formulario de pago.
 * Menú: Adquirir Boletos → Pago (PaymentActivity).
 * Unidad: {@link PaymentValidationUtils#errorNumeroTarjeta(String)}.
 */
@RunWith(Parameterized.class)
public class PU03_NumeroTarjetaTest {

    @Parameters(name = "{0}")
    public static Collection<Object[]> casos() {
        return Arrays.asList(new Object[][]{
                // caso, numeroIngresado, errorEsperado
                {"C1 16 dígitos válidos", "4111111111111111", null},
                {"C2 campo vacío", "", "Ingresa el número de tarjeta"},
                {"C3 menos de 16 dígitos (14)", "41111111111111", "El número de tarjeta debe tener 16 dígitos"},
                {"C4 más de 16 dígitos (17)", "41111111111111111", "El número de tarjeta debe tener 16 dígitos"},
                {"C5 contiene espacios", "4111 1111 1111 1111", "El número de tarjeta solo puede contener dígitos"},
                {"C6 contiene letras", "41111111111111AB", "El número de tarjeta solo puede contener dígitos"},
                {"C7 16 dígitos con ceros a la izquierda", "0000123456789012", null},
        });
    }

    private final String caso, numero, esperado;

    public PU03_NumeroTarjetaTest(String caso, String numero, String esperado) {
        this.caso = caso;
        this.numero = numero;
        this.esperado = esperado;
    }

    @Test
    public void errorNumeroTarjeta_validaLongitudYCaracteres() {
        String obtenido = PaymentValidationUtils.errorNumeroTarjeta(numero);
        EvidenciaPruebas.registrar("PU-03", caso, "numero=\"" + numero + "\"", esperado, obtenido);
        assertEquals(esperado, obtenido);
    }
}
