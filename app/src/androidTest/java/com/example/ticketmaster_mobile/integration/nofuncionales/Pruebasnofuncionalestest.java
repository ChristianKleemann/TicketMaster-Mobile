package com.example.ticketmaster_mobile.integration.nofuncionales;

import static org.junit.Assert.assertTrue;

import android.os.Build;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.example.ticketmaster_mobile.integration.soporte.EntornoRule;
import com.example.ticketmaster_mobile.integration.soporte.Escenario;
import com.example.ticketmaster_mobile.integration.uiautomator.Ua;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * RNF-01 · Tiempo de respuesta del menú y la navegación.
 *
 * Mide, con UI Automator y sin conocer las clases internas, cuánto tarda la app en mostrar la
 * pantalla destino desde el instante del toque. Cada operación se repite {@value #REPETICIONES}
 * veces (más una pasada de calentamiento que no cuenta) y se informa mínimo, promedio y máximo.
 * Criterio de aceptación: promedio <= {@value #CRITERIO_MS} ms por operación.
 *
 * Cómo ver los resultados (la prueba los escribe en Logcat con la etiqueta NFR):
 * <pre>  adb logcat -d -s NFR</pre>
 *
 * Nota: la base es la de la suite de pruebas (en memoria y con pocos datos), por lo que estas
 * cifras son reproducibles pero optimistas frente a la app real con el catálogo completo; el
 * reporte las complementa con mediciones sobre la app real (am start -W y grabación de pantalla).
 * El tiempo incluye la pequeña sobrecarga de UI Automator (decenas de ms), de modo que el
 * resultado es conservador.
 */
@RunWith(AndroidJUnit4.class)
public class Pruebasnofuncionalestest {

    private static final String ETIQUETA = "NFR";
    private static final int REPETICIONES = 5;
    private static final long CRITERIO_MS = 2_000L;

    @Rule
    public final EntornoRule entorno = new EntornoRule();

    @After
    public void cerrar() {
        Ua.cerrarApp();
    }

    private void entrar() {
        Ua.lanzarDesdeElLauncher(true);
        Ua.iniciarSesion(Escenario.CURP, Escenario.PASSWORD);
        assertTrue("Debe mostrarse la navegación principal", Ua.hay("bottomNavigation", Ua.ESPERA_MS));
    }

    /** Milisegundos desde el toque hasta que {@code destino} es visible. */
    private long medirToque(UiObject2 elemento, BySelector destino, String descripcion) {
        UiDevice d = Ua.dispositivo();
        long inicio = System.nanoTime();
        elemento.click();
        boolean visible = d.wait(Until.hasObject(destino), Ua.ESPERA_MS);
        long ms = (System.nanoTime() - inicio) / 1_000_000L;
        assertTrue("No apareció el destino de: " + descripcion, visible);
        return ms;
    }

    private UiObject2 textoVisible(String texto) {
        Ua.desplazarHastaTexto(texto);
        UiObject2 o = Ua.dispositivo().wait(Until.findObject(By.text(texto)), Ua.ESPERA_MS);
        if (o == null) throw new AssertionError("No apareció el texto \"" + texto + "\"");
        return o;
    }

    /** Escribe la línea de resultados; devuelve un mensaje de fallo si el promedio supera el criterio. */
    private String informar(String codigo, String operacion, long[] muestras) {
        long min = Long.MAX_VALUE;
        long max = 0;
        long suma = 0;
        for (long m : muestras) {
            min = Math.min(min, m);
            max = Math.max(max, m);
            suma += m;
        }
        long promedio = suma / muestras.length;
        String linea = "NFR|" + codigo + "|" + operacion + "|n=" + muestras.length
                + "|min=" + min + "ms|prom=" + promedio + "ms|max=" + max + "ms"
                + "|criterio<=" + CRITERIO_MS + "ms|" + (promedio <= CRITERIO_MS ? "CUMPLE" : "NO CUMPLE")
                + "|" + Build.MANUFACTURER + " " + Build.MODEL + "|Android " + Build.VERSION.RELEASE;
        Log.i(ETIQUETA, linea);
        System.out.println(linea);
        return promedio <= CRITERIO_MS
                ? null
                : operacion + ": promedio " + promedio + " ms > " + CRITERIO_MS + " ms";
    }

    @Test
    public void nf01_tiempoDeRespuestaDelMenuYLaNavegacion() {
        entrar();

        BySelector nombreEnPerfil = By.res(Ua.PAQUETE, "tvProfileNombre").text(Pattern.compile(".+"));
        BySelector primerEstablecimiento = By.res(Ua.PAQUETE, "tvEstablecimientoNombre");
        BySelector primeraFuncion = By.res(Ua.PAQUETE, "tvFuncionNombreEvento");
        BySelector listaDeZonas = By.res(Ua.PAQUETE, "rvZonas");
        UiDevice d = Ua.dispositivo();

        long[] perfil = new long[REPETICIONES];
        long[] catalogo = new long[REPETICIONES];
        long[] funciones = new long[REPETICIONES];
        long[] zonas = new long[REPETICIONES];

        // i == -1 es el calentamiento: carga de clases y cachés; no se registra
        for (int i = -1; i < REPETICIONES; i++) {
            // Menú: Perfil
            long tPerfil = medirToque(Ua.esperar("nav_profile"), nombreEnPerfil, "menú Perfil");
            // Menú: Tickets (catálogo)
            long tCatalogo = medirToque(Ua.esperar("nav_tickets"), primerEstablecimiento, "menú Tickets");

            // Catálogo -> Funciones del teatro
            Ua.tocar("chipTeatro");
            assertTrue(Ua.hayTexto(Escenario.TEATRO, Ua.ESPERA_MS));
            long tFunciones = medirToque(textoVisible(Escenario.TEATRO), primeraFuncion, "abrir establecimiento");

            // Funciones -> selección de zonas
            long tZonas = medirToque(textoVisible(Escenario.EVENTO_TEATRO), listaDeZonas, "abrir evento");

            if (i >= 0) {
                perfil[i] = tPerfil;
                catalogo[i] = tCatalogo;
                funciones[i] = tFunciones;
                zonas[i] = tZonas;
            }

            // Regreso al catálogo para la siguiente vuelta
            d.pressBack();
            assertTrue(Ua.hay("rvFunciones", Ua.ESPERA_MS));
            d.pressBack();
            assertTrue(Ua.hay("rvEstablecimientos", Ua.ESPERA_MS));
        }

        List<String> fallos = new ArrayList<>();
        String f;
        if ((f = informar("RT-01", "Menu Perfil", perfil)) != null) fallos.add(f);
        if ((f = informar("RT-02", "Menu Tickets (catalogo)", catalogo)) != null) fallos.add(f);
        if ((f = informar("RT-03", "Abrir establecimiento", funciones)) != null) fallos.add(f);
        if ((f = informar("RT-04", "Abrir evento (zonas)", zonas)) != null) fallos.add(f);

        assertTrue("Operaciones que superan el criterio: " + fallos, fallos.isEmpty());
    }
}
