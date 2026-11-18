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
 * PU-01 · Regla "no se pueden adquirir boletos en días no laborales".
 * Menú: Adquirir Boletos → selección de establecimiento/función (FuncionesActivity).
 * Unidad: {@link ReglasNegocio#motivoDiaNoLaboral(String, String, String, String)}.
 */
@RunWith(Parameterized.class)
public class PU01_DiaNoLaboralTest {

    @Parameters(name = "{0}")
    public static Collection<Object[]> casos() {
        return Arrays.asList(new Object[][]{
                // caso, diasLaborales, fechaIso, festivo, lugar, mensajeEsperado
                {"C1 día hábil (miércoles)", "2,3,4,5,6,7", "2026-10-07", null, "Teatro Colón", null},
                {"C2 día de descanso (lunes)", "2,3,4,5,6,7", "2026-10-05", null, "Teatro Colón",
                        "Teatro Colón no tiene servicio los lunes (días de servicio: Martes a Domingo)."},
                {"C3 día festivo en día hábil", "1,2,3,4,5,6,7", "2026-12-25", "Navidad", "Cinemex",
                        "Cinemex no tiene servicio el vie 25 dic 2026 por ser día festivo (Navidad)."},
                {"C4 días no consecutivos", "1,3,5", "2026-10-06", null, "Museo Demo",
                        "Museo Demo no tiene servicio los martes (días de servicio: lun, mié, vie)."},
                {"C5 fecha inexistente", "1,2,3,4,5,6,7", "2026-02-30", null, "Cinemex", "Fecha inválida"},
                {"C6 día hábil en fin de semana (domingo)", "2,3,4,5,6,7", "2026-10-11", null, "Teatro Colón", null},
        });
    }

    private final String caso, dias, fecha, festivo, lugar, esperado;

    public PU01_DiaNoLaboralTest(String caso, String dias, String fecha, String festivo,
                                 String lugar, String esperado) {
        this.caso = caso;
        this.dias = dias;
        this.fecha = fecha;
        this.festivo = festivo;
        this.lugar = lugar;
        this.esperado = esperado;
    }

    @Test
    public void motivoDiaNoLaboral_devuelveMensajeCorrecto() {
        String obtenido = ReglasNegocio.motivoDiaNoLaboral(dias, fecha, festivo, lugar);
        EvidenciaPruebas.registrar("PU-01", caso,
                "dias=\"" + dias + "\", fecha=" + fecha + ", festivo=" + festivo + ", lugar=" + lugar,
                esperado, obtenido);
        assertEquals(esperado, obtenido);
    }
}
