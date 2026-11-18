package com.example.ticketmaster_mobile.adquisicion;

import static org.junit.Assert.assertEquals;

import com.example.ticketmaster_mobile.data.ReglasNegocio;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;

/**
 * PU-02 · Límite de boletos por usuario y función (teatro 10, museo 5, cine 6).
 * Menú: Adquirir Boletos → cantidad/asientos (ZonaCantidadActivity, SeatSelectionActivity).
 * Unidad: {@link ReglasNegocio#mensajeLimiteBoletos(int, int, int)}.
 */
@RunWith(Parameterized.class)
public class PU02_LimiteBoletosTest {

    @Parameters(name = "{0}")
    public static Collection<Object[]> casos() {
        return Arrays.asList(new Object[][]{
                // caso, maximo, yaTiene, solicita, mensajeEsperado
                {"C1 cine: ya alcanzó el máximo", 6, 6, 1,
                        "Ya tienes 6 boletos para esta función. El máximo por usuario es 6."},
                {"C2 cine: primera compra excede el máximo", 6, 0, 7,
                        "Solo puedes comprar 6 boletos por usuario para esta función. Intentaste comprar 7."},
                {"C3 cine: compra parcial excede el cupo restante", 6, 4, 3,
                        "Solo puedes comprar 6 boletos por usuario para esta función (ya tienes 4, te quedan 2). Intentaste comprar 3."},
                {"C4 teatro: máximo 10 alcanzado", 10, 10, 1,
                        "Ya tienes 10 boletos para esta función. El máximo por usuario es 10."},
                {"C5 museo: ya tiene más que el máximo", 5, 7, 1,
                        "Ya tienes 7 boletos para esta función. El máximo por usuario es 5."},
        });
    }

    private final String caso, esperado;
    private final int maximo, yaTiene, solicita;

    public PU02_LimiteBoletosTest(String caso, int maximo, int yaTiene, int solicita, String esperado) {
        this.caso = caso;
        this.maximo = maximo;
        this.yaTiene = yaTiene;
        this.solicita = solicita;
        this.esperado = esperado;
    }

    @Test
    public void mensajeLimiteBoletos_describeElExcesoCorrectamente() {
        String obtenido = ReglasNegocio.mensajeLimiteBoletos(maximo, yaTiene, solicita);
        EvidenciaPruebas.registrar("PU-02", caso,
                "maximo=" + maximo + ", yaTiene=" + yaTiene + ", solicita=" + solicita, esperado, obtenido);
        assertEquals(esperado, obtenido);
    }
}
