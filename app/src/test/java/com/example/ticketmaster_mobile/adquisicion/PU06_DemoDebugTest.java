package com.example.ticketmaster_mobile.adquisicion;

import static org.junit.Assert.assertEquals;

import com.example.ticketmaster_mobile.PaymentValidationUtils;
import com.example.ticketmaster_mobile.data.ReglasNegocio;

import org.junit.Ignore;
import org.junit.Test;

/**
 * PU-06 · Demostración de estados de una prueba unitaria y uso del debug.
 * NO forma parte de las 5 pruebas del plan: existe para ver en Android Studio
 *   - una prueba que PASA (verde),
 *   - una prueba que FALLA a propósito (rojo, "error"),
 *   - una prueba OMITIDA (amarillo, "warning").
 * Para ejecutar solo las 5 pruebas reales:  gradlew testDebugUnitTest --tests "*PU0[1-5]*"
 */
public class PU06_DemoDebugTest {

    /** Verde: el resultado obtenido coincide con el esperado. */
    @Test
    public void demo_pasa() {
        String obtenido = ReglasNegocio.motivoDiaNoLaboral("1,2,3,4,5,6,7", "2026-02-30", null, "Cinemex");
        assertEquals("Fecha inválida", obtenido);
    }

    /**
     * Rojo: el valor ESPERADO está mal escrito a propósito ("15" en lugar de "16").
     * La app funciona bien; el error está en la prueba. Se encuentra depurando:
     * breakpoint en PaymentValidationUtils.errorNumeroTarjeta (línea del if de longitud),
     * Debug Tests, y se observa LONGITUD_TARJETA = 16. Corrige "15" por "16" y vuelve a correr.
     */
    @Test
    public void demo_falla_a_proposito() {
        String obtenido = PaymentValidationUtils.errorNumeroTarjeta("411111111111111"); // 15 dígitos
        assertEquals("El número de tarjeta debe tener 15 dígitos", obtenido);
    }

    /** Amarillo: JUnit la marca como omitida (ignored) y no la ejecuta. */
    @Ignore("Warning de demostración: prueba omitida a propósito")
    @Test
    public void demo_omitida() {
        assertEquals("nunca se ejecuta", "nunca se ejecuta");
    }
}
