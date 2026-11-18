package com.example.ticketmaster_mobile.adquisicion;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;

/**
 * Utilidad solo de pruebas: registra en un CSV cada caso ejecutado (valores ingresados, resultado
 * esperado y resultado obtenido) para documentarlos después en el reporte.
 * Archivo: app/build/reports/pruebas-unitarias/resultados.csv
 */
final class EvidenciaPruebas {

    private static final File ARCHIVO = new File("build/reports/pruebas-unitarias/resultados.csv");
    private static boolean iniciado = false;

    private EvidenciaPruebas() {
    }

    static synchronized void registrar(String idPrueba, String caso, String ingresados,
                                       String esperado, String obtenido) {
        try {
            File dir = ARCHIVO.getParentFile();
            if (!dir.exists() && !dir.mkdirs()) return;
            boolean append = iniciado;
            iniciado = true;
            try (PrintWriter w = new PrintWriter(new FileWriter(ARCHIVO, append))) {
                if (!append) w.println("id,caso,ingresados,esperado,obtenido,estado");
                String estado = Objects.equals(esperado, obtenido) ? "PASO" : "FALLO";
                w.println(csv(idPrueba) + "," + csv(caso) + "," + csv(ingresados) + ","
                        + csv(esperado) + "," + csv(obtenido) + "," + estado);
            }
        } catch (IOException ignorado) {
            // La evidencia es auxiliar: nunca debe hacer fallar una prueba.
        }
    }

    private static String csv(String s) {
        String v = s == null ? "null" : s;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
