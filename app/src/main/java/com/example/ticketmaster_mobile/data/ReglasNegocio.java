package com.example.ticketmaster_mobile.data;

import java.util.Date;
import java.util.Set;

/**
 * Reglas de negocio de la venta de boletos, sin dependencias de Android.
 */
public final class ReglasNegocio {

    /**
     * Interruptor para pruebas: si es false, NO se bloquea la compra por ser hoy un día no
     * laborable / festivo del establecimiento (útil para demostrar el flujo completo en
     * cualquier día de la semana). La función elegida sí debe caer en día de servicio.
     * Déjalo en true para la entrega: es el comportamiento pedido en la especificación.
     */
    public static final boolean VALIDAR_DIA_DE_COMPRA = true;

    private ReglasNegocio() {
    }

    /**
     * Regla "no se pueden adquirir boletos en días no laborales".
     *
     * @param diasLaborales  días de servicio del establecimiento (CSV ISO, 1=lunes...7=domingo)
     * @param fechaIso       día a evaluar (yyyy-MM-dd)
     * @param festivo        descripción del festivo si ese día lo es; null si no
     * @param nombreLugar    nombre para el mensaje
     * @return null si el día es hábil; o el mensaje que se debe mostrar al usuario
     */
    public static String motivoDiaNoLaboral(String diasLaborales, String fechaIso, String festivo, String nombreLugar) {
        Date fecha = FechaUtils.parsearFecha(fechaIso);
        if (fecha == null) return "Fecha inválida";
        if (festivo != null) {
            return nombreLugar + " no tiene servicio el " + FechaUtils.fechaLegible(fechaIso)
                    + " por ser día festivo (" + festivo + ").";
        }
        Set<Integer> dias = FechaUtils.parsearDias(diasLaborales);
        int dia = FechaUtils.diaIso(fecha);
        if (!dias.contains(dia)) {
            return nombreLugar + " no tiene servicio los " + FechaUtils.nombreDia(dia)
                    + " (días de servicio: " + FechaUtils.describirDias(diasLaborales) + ").";
        }
        return null;
    }

    /** Mensaje cuando el usuario ya llegó (o excedería) el máximo de boletos por usuario. */
    public static String mensajeLimiteBoletos(int maximo, int yaTiene, int solicita) {
        if (yaTiene >= maximo) {
            return "Ya tienes " + yaTiene + " boletos para esta función. El máximo por usuario es " + maximo + ".";
        }
        return "Solo puedes comprar " + maximo + " boletos por usuario para esta función"
                + (yaTiene > 0 ? " (ya tienes " + yaTiene + ", te quedan " + (maximo - yaTiene) + ")" : "")
                + ". Intentaste comprar " + solicita + ".";
    }
}
