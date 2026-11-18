package com.example.ticketmaster_mobile.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tipo de servicio de una sala de cine. En la app funciona como "tipo de asiento": cada servicio es
 * una Seccion_Zona del cine con su propia cuadrícula, precio y apariencia en la pantalla de
 * selección de asientos. Un cine solo ofrece algunos de estos servicios; si el usuario elige uno
 * que el cine no tiene, la app le muestra un mensaje.
 */
public final class ServicioCine {

    public final String nombre;
    public final String descripcion;
    public final int filas;
    public final int columnas;
    public final double precioBase;
    /** Color ARGB de acento (borde de las butacas y banner de pantalla). */
    public final int colorAcento;
    /** Radio de las esquinas de la butaca en dp (0 = cuadrada, 24 = circular). */
    public final int radioDp;
    /** Texto del banner de pantalla. */
    public final String textoPantalla;
    /** Proporción (0.5–1.0) del ancho que ocupa el banner de pantalla. */
    public final float anchoPantalla;

    private ServicioCine(String nombre, String descripcion, int filas, int columnas, double precioBase,
                         int colorAcento, int radioDp, String textoPantalla, float anchoPantalla) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.filas = filas;
        this.columnas = columnas;
        this.precioBase = precioBase;
        this.colorAcento = colorAcento;
        this.radioDp = radioDp;
        this.textoPantalla = textoPantalla;
        this.anchoPantalla = anchoPantalla;
    }

    public int capacidad() {
        return filas * columnas;
    }

    public static final ServicioCine TRADICIONAL = new ServicioCine("Tradicional",
            "Butacas estándar en sala tradicional", 8, 10, 90, 0xFF90A4AE, 8, "PANTALLA", 0.8f);
    public static final ServicioCine PLUUS = new ServicioCine("PLUUS",
            "Butacas con más espacio, sonido y proyección mejorados", 5, 8, 140, 0xFF26A69A, 10, "PANTALLA PLUUS", 0.85f);
    public static final ServicioCine VIP = new ServicioCine("VIP",
            "Butacas reclinables de lujo con servicio a tu asiento", 4, 6, 220, 0xFFFFC107, 18, "PANTALLA VIP", 0.7f);
    public static final ServicioCine MACRO_XE = new ServicioCine("Macro XE",
            "Pantalla extra grande y sonido envolvente", 6, 10, 160, 0xFF29B6F6, 6, "PANTALLA MACRO XE", 1.0f);
    public static final ServicioCine JUNIOR = new ServicioCine("Cinépolis Junior",
            "Sala infantil con butacas y decoración para niños", 4, 8, 80, 0xFFFF8A65, 14, "PANTALLA JUNIOR", 0.75f);
    public static final ServicioCine CUATRO_DX = new ServicioCine("4DX",
            "Butacas con movimiento y efectos (viento, agua, aromas)", 4, 8, 210, 0xFFEF5350, 2, "PANTALLA 4DX · BUTACAS MÓVILES", 0.85f);
    public static final ServicioCine IMAX = new ServicioCine("IMAX",
            "Pantalla gigante y sonido de alta precisión", 8, 10, 190, 0xFF1E88E5, 4, "PANTALLA IMAX", 1.0f);
    public static final ServicioCine VR = new ServicioCine("VR",
            "Experiencia de realidad virtual: un visor por butaca", 3, 4, 180, 0xFFAB47BC, 24, "SALA VR · VISORES", 0.6f);
    public static final ServicioCine SCREEN_X = new ServicioCine("Screen X",
            "Proyección panorámica de 270° con pantallas laterales", 6, 10, 170, 0xFF9CCC65, 6, "PANTALLA CENTRAL + LATERALES", 1.0f);

    public static final List<ServicioCine> TODOS = Collections.unmodifiableList(Arrays.asList(
            TRADICIONAL, PLUUS, VIP, MACRO_XE, JUNIOR, CUATRO_DX, IMAX, VR, SCREEN_X));

    /** Busca el servicio por el nombre de la zona; si no coincide devuelve Tradicional. */
    public static ServicioCine porNombre(String nombre) {
        for (ServicioCine s : TODOS) {
            if (s.nombre.equalsIgnoreCase(nombre)) return s;
        }
        return TRADICIONAL;
    }
}
