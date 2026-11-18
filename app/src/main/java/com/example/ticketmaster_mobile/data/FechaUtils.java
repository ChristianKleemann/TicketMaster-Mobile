package com.example.ticketmaster_mobile.data;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Utilidades de fecha sin dependencias de Android (java.util únicamente, compatible con minSdk 24).
 * Todas las fechas se manejan como texto ISO: "yyyy-MM-dd" y "yyyy-MM-dd HH:mm".
 */
public final class FechaUtils {

    private static final String[] DIAS = {"lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"};
    private static final String[] DIAS_CORTOS = {"lun", "mar", "mié", "jue", "vie", "sáb", "dom"};
    private static final String[] MESES = {"ene", "feb", "mar", "abr", "may", "jun",
            "jul", "ago", "sep", "oct", "nov", "dic"};

    private FechaUtils() {
    }

    public static String fechaIso(Date fecha) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(fecha);
    }

    public static String fechaHoraIso(Date fecha) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(fecha);
    }

    public static Date parsearFecha(String fechaIso) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
            sdf.setLenient(false);
            return sdf.parse(fechaIso.substring(0, 10));
        } catch (ParseException | RuntimeException e) {
            return null;
        }
    }

    /** Día de la semana ISO: 1 = lunes ... 7 = domingo. */
    public static int diaIso(Date fecha) {
        Calendar c = Calendar.getInstance();
        c.setTime(fecha);
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1;
    }

    public static Date sumarDias(Date fecha, int dias) {
        Calendar c = Calendar.getInstance();
        c.setTime(fecha);
        c.add(Calendar.DAY_OF_YEAR, dias);
        return c.getTime();
    }

    public static Date conHora(Date dia, String horaHHmm) {
        Calendar c = Calendar.getInstance();
        c.setTime(dia);
        String[] p = horaHHmm.split(":");
        c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(p[0]));
        c.set(Calendar.MINUTE, Integer.parseInt(p[1]));
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    public static Set<Integer> parsearDias(String diasCsv) {
        Set<Integer> dias = new TreeSet<>();
        if (diasCsv == null) return dias;
        for (String parte : diasCsv.split(",")) {
            String t = parte.trim();
            if (!t.isEmpty()) dias.add(Integer.parseInt(t));
        }
        return dias;
    }

    public static String nombreDia(int diaIso) {
        return DIAS[diaIso - 1];
    }

    /** "Todos los días", "Martes a Domingo" (si son consecutivos) o "lun, mié, vie". */
    public static String describirDias(String diasCsv) {
        List<Integer> dias = new ArrayList<>(parsearDias(diasCsv));
        if (dias.isEmpty()) return "Sin servicio";
        if (dias.size() == 7) return "Todos los días";
        boolean consecutivos = dias.get(dias.size() - 1) - dias.get(0) == dias.size() - 1;
        if (consecutivos && dias.size() > 2) {
            return capitalizar(DIAS[dias.get(0) - 1]) + " a " + capitalizar(DIAS[dias.get(dias.size() - 1) - 1]);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dias.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(DIAS_CORTOS[dias.get(i) - 1]);
        }
        return sb.toString();
    }

    /** "mié 7 oct 2026" a partir de "2026-10-07" o "2026-10-07 20:00". */
    public static String fechaLegible(String fechaIso) {
        Date d = parsearFecha(fechaIso);
        if (d == null) return fechaIso;
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        return DIAS_CORTOS[diaIso(d) - 1] + " " + c.get(Calendar.DAY_OF_MONTH) + " "
                + MESES[c.get(Calendar.MONTH)] + " " + c.get(Calendar.YEAR);
    }

    /** "mié 7 oct 2026, 20:00 hrs" a partir de "yyyy-MM-dd HH:mm". */
    public static String fechaHoraLegible(String fechaHoraIso) {
        if (fechaHoraIso == null || fechaHoraIso.length() < 16) return fechaHoraIso;
        return fechaLegible(fechaHoraIso) + ", " + fechaHoraIso.substring(11, 16) + " hrs";
    }

    /** Chip corto: "mié 7 oct". */
    public static String fechaCorta(String fechaIso) {
        String larga = fechaLegible(fechaIso);
        int ultimoEspacio = larga.lastIndexOf(' ');
        return ultimoEspacio > 0 ? larga.substring(0, ultimoEspacio) : larga;
    }

    private static String capitalizar(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
