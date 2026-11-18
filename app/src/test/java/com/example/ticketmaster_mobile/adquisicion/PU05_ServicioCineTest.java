package com.example.ticketmaster_mobile.adquisicion;

import static org.junit.Assert.assertEquals;

import com.example.ticketmaster_mobile.data.ServicioCine;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;

/**
 * PU-05 · Búsqueda del tipo de servicio de sala (tipo de asiento), su aforo y su precio base.
 * Menú: Adquirir Boletos → selección de asientos de cine (SeatSelectionActivity / SeatAdapter).
 * Unidad: {@link ServicioCine#porNombre(String)}, {@link ServicioCine#capacidad()} y precioBase.
 */
@RunWith(Parameterized.class)
public class PU05_ServicioCineTest {

    @Parameters(name = "{0}")
    public static Collection<Object[]> casos() {
        return Arrays.asList(new Object[][]{
                // caso, nombreBuscado, servicioEsperado, capacidadEsperada, precioEsperado
                {"C1 Tradicional (8x10)", "Tradicional", "Tradicional", 80, 90.0},
                {"C2 VIP sin distinguir mayúsculas (4x6)", "vip", "VIP", 24, 220.0},
                {"C3 IMAX (8x10)", "IMAX", "IMAX", 80, 190.0},
                {"C4 VR (3x4)", "VR", "VR", 12, 180.0},
                {"C5 nombre con espacio (6x10)", "macro xe", "Macro XE", 60, 160.0},
                {"C6 servicio inexistente → Tradicional", "Sala Inexistente", "Tradicional", 80, 90.0},
                {"C7 nombre nulo → Tradicional", null, "Tradicional", 80, 90.0},
        });
    }

    private final String caso, buscado, servicioEsperado;
    private final int capacidadEsperada;
    private final double precioEsperado;

    public PU05_ServicioCineTest(String caso, String buscado, String servicioEsperado,
                                 int capacidadEsperada, double precioEsperado) {
        this.caso = caso;
        this.buscado = buscado;
        this.servicioEsperado = servicioEsperado;
        this.capacidadEsperada = capacidadEsperada;
        this.precioEsperado = precioEsperado;
    }

    @Test
    public void porNombre_devuelveServicioConCapacidadYPrecioCorrectos() {
        ServicioCine s = ServicioCine.porNombre(buscado);
        String esperado = servicioEsperado + " | capacidad=" + capacidadEsperada + " | precio=" + precioEsperado;
        String obtenido = s.nombre + " | capacidad=" + s.capacidad() + " | precio=" + s.precioBase;
        EvidenciaPruebas.registrar("PU-05", caso, "nombre=" + (buscado == null ? "null" : "\"" + buscado + "\""),
                esperado, obtenido);
        assertEquals(servicioEsperado, s.nombre);
        assertEquals(capacidadEsperada, s.capacidad());
        assertEquals(precioEsperado, s.precioBase, 0.001);
    }
}
