package com.example.ticketmaster_mobile.integration.uiautomator;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import java.util.regex.Pattern;

/**
 * Utilidades de UI Automator: prueba de "caja negra" que maneja la app como un usuario, solo por
 * lo que se ve en pantalla (resource-id y textos), sin conocer las clases internas.
 */
public final class Ua {

    public static final String PAQUETE = "com.example.ticketmaster_mobile";
    public static final long ESPERA_MS = 10_000L;

    private Ua() {
    }

    public static UiDevice dispositivo() {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    // ------------------------------------------------------------------ ciclo de vida de la app

    /** Abre la app como lo hace el launcher, en una tarea nueva. */
    public static void lanzarDesdeElLauncher(boolean tareaNueva) {
        Context ctx = ApplicationProvider.getApplicationContext();
        Intent intent = ctx.getPackageManager().getLaunchIntentForPackage(PAQUETE);
        if (intent == null) throw new AssertionError("La app no tiene actividad de lanzamiento");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | (tareaNueva ? Intent.FLAG_ACTIVITY_CLEAR_TASK : Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        ctx.startActivity(intent);
        esperarApp();
    }

    public static void esperarApp() {
        if (!dispositivo().wait(Until.hasObject(By.pkg(PAQUETE).depth(0)), ESPERA_MS)) {
            throw new AssertionError("La app no llegó a primer plano");
        }
    }

    /** Cierra las pantallas de la app con "atrás" para no dejar actividades vivas entre pruebas. */
    public static void cerrarApp() {
        UiDevice d = dispositivo();
        for (int i = 0; i < 8 && d.hasObject(By.pkg(PAQUETE).depth(0)); i++) {
            d.pressBack();
            d.waitForIdle(500);
        }
        d.pressHome();
    }

    // ------------------------------------------------------------------ búsqueda de elementos

    public static UiObject2 esperar(String idRecurso) {
        UiObject2 o = dispositivo().wait(Until.findObject(By.res(PAQUETE, idRecurso)), ESPERA_MS);
        if (o == null) throw new AssertionError("No apareció el elemento id=" + idRecurso);
        return o;
    }

    public static boolean hay(String idRecurso, long timeoutMs) {
        return dispositivo().wait(Until.hasObject(By.res(PAQUETE, idRecurso)), timeoutMs);
    }

    public static boolean hayTexto(String texto, long timeoutMs) {
        return dispositivo().wait(Until.hasObject(By.text(texto)), timeoutMs);
    }

    public static boolean hayTextoQueContiene(String fragmento, long timeoutMs) {
        return dispositivo().wait(Until.hasObject(By.text(Pattern.compile(".*" + Pattern.quote(fragmento) + ".*",
                Pattern.DOTALL))), timeoutMs);
    }

    public static boolean noHay(String idRecurso, long timeoutMs) {
        return dispositivo().wait(Until.gone(By.res(PAQUETE, idRecurso)), timeoutMs);
    }

    // ------------------------------------------------------------------ acciones

    /** Desplaza el contenedor hasta que el elemento sea visible (si ya lo es, no hace nada). */
    public static void desplazarHasta(String idRecurso) {
        if (dispositivo().hasObject(By.res(PAQUETE, idRecurso))) return;
        try {
            new UiScrollable(new UiSelector().scrollable(true))
                    .scrollIntoView(new UiSelector().resourceId(PAQUETE + ":id/" + idRecurso));
        } catch (UiObjectNotFoundException ignorada) {
            // No hay contenedor desplazable: la búsqueda posterior dará el error útil
        }
    }

    public static void desplazarHastaTexto(String texto) {
        if (dispositivo().hasObject(By.text(texto))) return;
        try {
            new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().text(texto));
        } catch (UiObjectNotFoundException ignorada) {
        }
    }

    public static void escribir(String idRecurso, String texto) {
        desplazarHasta(idRecurso);
        esperar(idRecurso).setText(texto);
    }

    public static void tocar(String idRecurso) {
        desplazarHasta(idRecurso);
        esperar(idRecurso).click();
    }

    public static void tocarTexto(String texto) {
        desplazarHastaTexto(texto);
        UiObject2 o = dispositivo().wait(Until.findObject(By.text(texto)), ESPERA_MS);
        if (o == null) throw new AssertionError("No apareció el texto \"" + texto + "\"");
        o.click();
    }

    public static String texto(String idRecurso) {
        desplazarHasta(idRecurso);
        return esperar(idRecurso).getText();
    }

    /**
     * Toca el botón {@code idBoton} de la fila (de una lista) que contiene el texto dado,
     * p. ej. el "+" de la zona "Platea".
     */
    public static void tocarBotonDeFila(String textoDeLaFila, String idBoton) {
        desplazarHastaTexto(textoDeLaFila);
        UiObject2 etiqueta = dispositivo().wait(Until.findObject(By.text(textoDeLaFila)), ESPERA_MS);
        if (etiqueta == null) throw new AssertionError("No apareció la fila \"" + textoDeLaFila + "\"");
        UiObject2 actual = etiqueta;
        for (int i = 0; i < 6 && actual != null; i++) {
            UiObject2 boton = actual.findObject(By.res(PAQUETE, idBoton));
            if (boton != null) {
                boton.click();
                return;
            }
            actual = actual.getParent();
        }
        throw new AssertionError("La fila \"" + textoDeLaFila + "\" no tiene el botón " + idBoton);
    }

    // ------------------------------------------------------------------ flujos comunes

    public static void iniciarSesion(String curp, String password) {
        escribir("etUsuario", curp);
        escribir("etPassword", password);
        tocar("btnLogin");
    }
}
