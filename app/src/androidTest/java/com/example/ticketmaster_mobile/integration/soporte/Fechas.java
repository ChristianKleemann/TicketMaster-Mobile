package com.example.ticketmaster_mobile.integration.soporte;

import com.example.ticketmaster_mobile.data.FechaUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Fechas relativas a "hoy" para que las pruebas no dependan del día en que se ejecutan. */
public final class Fechas {

    private Fechas() {
    }

    public static String hoyIso() {
        return FechaUtils.fechaIso(new Date());
    }

    /** "yyyy-MM-dd HH:mm" de mañana a la hora dada. */
    public static String manana(int hora, int minuto) {
        return enDias(1, hora, minuto);
    }

    public static String ayer(int hora, int minuto) {
        return enDias(-1, hora, minuto);
    }

    private static String enDias(int dias, int hora, int minuto) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, dias);
        c.set(Calendar.HOUR_OF_DAY, hora);
        c.set(Calendar.MINUTE, minuto);
        return FechaUtils.fechaHoraIso(c.getTime());
    }

    /** Día ISO de hoy: 1 = lunes ... 7 = domingo. */
    public static int diaIsoDeHoy() {
        return FechaUtils.diaIso(new Date());
    }

    /** Todos los días ISO salvo {@code excluido}, p. ej. "1,2,3,4,5,7". */
    public static String todosExcepto(int excluido) {
        StringBuilder sb = new StringBuilder();
        for (int d = 1; d <= 7; d++) {
            if (d == excluido) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(d);
        }
        return sb.toString();
    }

    /** Vencimiento de tarjeta válido (diciembre del año próximo) en formato MM/AA. */
    public static String vencimientoValido() {
        int anio = Calendar.getInstance().get(Calendar.YEAR) + 1;
        return String.format(Locale.ROOT, "12/%02d", anio % 100);
    }
}
