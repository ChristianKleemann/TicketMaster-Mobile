package com.example.ticketmaster_mobile.integration.nofuncionales;

import static org.junit.Assert.assertTrue;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.ticketmaster_mobile.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

/** RNF accesibilidad: contraste mínimo WCAG 2.1 AA de la paleta de la app. */
@RunWith(AndroidJUnit4.class)
public class ContrasteColoresTest {

    private static final double TEXTO = 4.5;       // texto normal
    private static final double COMPONENTE = 3.0;  // bordes e iconos necesarios

    private static int color(int res) {
        return ContextCompat.getColor(ApplicationProvider.getApplicationContext(), res);
    }

    private static void exige(String nombre, int frente, int fondo, double minimo) {
        double ratio = ColorUtils.calculateContrast(frente, fondo);
        assertTrue(String.format(Locale.US, "%s: %.2f:1 (mínimo %.1f:1)", nombre, ratio, minimo),
                ratio >= minimo);
    }

    @Test
    public void textosSobreFondoYTarjeta() {
        int[] fondos = {color(R.color.tm_background), color(R.color.tm_surface)};
        String[] nombres = {"fondo", "tarjeta"};
        for (int i = 0; i < fondos.length; i++) {
            exige("texto principal / " + nombres[i], color(R.color.tm_text_primary), fondos[i], TEXTO);
            exige("texto secundario / " + nombres[i], color(R.color.tm_text_secondary), fondos[i], TEXTO);
            exige("hint / " + nombres[i], color(R.color.tm_text_hint), fondos[i], TEXTO);
            exige("azul de texto / " + nombres[i], color(R.color.tm_blue_text), fondos[i], TEXTO);
            exige("éxito / " + nombres[i], color(R.color.tm_success), fondos[i], TEXTO);
            exige("error / " + nombres[i], color(R.color.tm_error), fondos[i], TEXTO);
        }
    }

    @Test
    public void textoSobreBotonesYAsientos() {
        int blanco = color(R.color.tm_text_primary);
        exige("texto del botón / azul de marca", blanco, color(R.color.tm_blue), TEXTO);
        exige("asiento libre", blanco, color(R.color.seat_disponible_bg), TEXTO);
        exige("asiento ocupado", blanco, color(R.color.seat_ocupado_bg), TEXTO);
        exige("asiento elegido", blanco, color(R.color.seat_seleccionado), TEXTO);
    }

    @Test
    public void bordesYComponentes() {
        int fondo = color(R.color.tm_background);
        exige("borde del campo (azul)", color(R.color.tm_blue), fondo, COMPONENTE);
        exige("borde asiento libre", color(R.color.seat_disponible), fondo, COMPONENTE);
        exige("borde asiento ocupado", color(R.color.seat_ocupado), fondo, COMPONENTE);
        exige("borde asiento elegido", color(R.color.seat_seleccionado), fondo, COMPONENTE);
    }
}