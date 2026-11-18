package com.example.ticketmaster_mobile.integration.soporte;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.RecyclerViewActions.actionOnItem;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import com.example.ticketmaster_mobile.R;

/** Pasos de interfaz reutilizables (Espresso) para recorrer la app como lo haría un usuario. */
public final class Flujos {

    private Flujos() {
    }

    // ------------------------------------------------------------------ genéricos

    /** Escribe en un campo visible (pantallas sin scroll, como el login). */
    public static void escribir(int idCampo, String texto) {
        onView(withId(idCampo)).perform(replaceText(texto));
    }

    /** Escribe en un campo que puede estar fuera de pantalla (formularios con scroll). */
    public static void escribirConScroll(int idCampo, String texto) {
        onView(withId(idCampo)).perform(scrollTo(), replaceText(texto));
    }

    public static void tocar(int id) {
        onView(withId(id)).perform(click());
    }

    public static void tocarConScroll(int id) {
        onView(withId(id)).perform(scrollTo(), click());
    }

    public static void verDialogo(String mensaje) {
        onView(withText(mensaje)).inRoot(isDialog()).check(matches(isDisplayed()));
    }

    public static void cerrarDialogo(String boton) {
        onView(withText(boton)).inRoot(isDialog()).perform(click());
    }

    // ------------------------------------------------------------------ login

    public static void iniciarSesion(String curp, String password) {
        escribir(R.id.etUsuario, curp);
        escribir(R.id.etPassword, password);
        tocar(R.id.btnLogin);
    }

    // ------------------------------------------------------------------ catálogo y funciones

    public static void irATickets() {
        tocar(R.id.nav_tickets);
    }

    /** Filtra por tipo (chip) y abre el establecimiento. {@code idChip} 0 = sin filtrar. */
    public static void abrirEstablecimiento(int idChip, String nombre) {
        if (idChip != 0) tocar(idChip);
        onView(withId(R.id.rvEstablecimientos))
                .perform(actionOnItem(hasDescendant(withText(nombre)), click()));
    }

    public static void abrirFuncion(String evento) {
        onView(withId(R.id.rvFunciones))
                .perform(actionOnItem(hasDescendant(withText(evento)), click()));
    }

    // ------------------------------------------------------------------ teatro / museo (zonas)

    public static void masEnZona(String zona, int veces) {
        for (int i = 0; i < veces; i++) {
            onView(withId(R.id.rvZonas)).perform(
                    actionOnItem(hasDescendant(withText(zona)), Acciones.clicEnHijo(R.id.btnMas)));
        }
    }

    public static void menosEnZona(String zona, int veces) {
        for (int i = 0; i < veces; i++) {
            onView(withId(R.id.rvZonas)).perform(
                    actionOnItem(hasDescendant(withText(zona)), Acciones.clicEnHijo(R.id.btnMenos)));
        }
    }

    // ------------------------------------------------------------------ cine (asientos)

    public static void elegirAsiento(String codigo) {
        onView(org.hamcrest.Matchers.allOf(withId(R.id.tvAsientoCodigo), withText(codigo))).perform(click());
    }

    // ------------------------------------------------------------------ pago

    public static void elegirMetodo(String nombre) {
        onView(withText(nombre)).perform(scrollTo(), click());
    }

    public static void llenarTarjeta(String numero, String titular, String vence, String cvv) {
        escribirConScroll(R.id.etNumeroTarjeta, numero);
        escribirConScroll(R.id.etNombreTarjeta, titular);
        escribirConScroll(R.id.etVencimiento, vence);
        escribirConScroll(R.id.etCvv, cvv);
    }

    public static void llenarPaypal(String correo, String titular) {
        escribirConScroll(R.id.etCorreoPaypal, correo);
        escribirConScroll(R.id.etNombrePaypal, titular);
    }

    public static void confirmarPago() {
        tocar(R.id.btnConfirmarPago);
    }
}
