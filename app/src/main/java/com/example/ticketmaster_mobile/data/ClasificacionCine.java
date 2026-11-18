package com.example.ticketmaster_mobile.data;

/** Clasificaciones de películas (sistema mexicano) con su descripción para el usuario. */
public final class ClasificacionCine {

    private ClasificacionCine() {
    }

    public static String descripcion(String codigo) {
        if (codigo == null) return "";
        switch (codigo) {
            case "AA": return "AA · Comprensible para menores de 7 años";
            case "A":  return "A · Todo público";
            case "B":  return "B · Adolescentes de 12 años en adelante";
            case "B15": return "B15 · Mayores de 15 años";
            case "C":  return "C · Mayores de 18 años";
            case "D":  return "D · Solo adultos (contenido explícito)";
            default:   return codigo;
        }
    }
}
