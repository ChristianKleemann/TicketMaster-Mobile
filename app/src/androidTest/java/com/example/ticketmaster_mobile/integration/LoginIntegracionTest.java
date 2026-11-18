package com.example.ticketmaster_mobile.integration;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.ticketmaster_mobile.LoginActivity;
import com.example.ticketmaster_mobile.MainActivity;
import com.example.ticketmaster_mobile.R;
import com.example.ticketmaster_mobile.integration.soporte.Coincidencias;
import com.example.ticketmaster_mobile.integration.soporte.EntornoRule;
import com.example.ticketmaster_mobile.integration.soporte.Escenario;
import com.example.ticketmaster_mobile.integration.soporte.Flujos;
import com.example.ticketmaster_mobile.session.SessionManager;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Integración Login ↔ base de datos ↔ sesión ↔ pantalla principal (Espresso).
 * Usa una base en memoria sembrada con dos usuarios conocidos.
 */
@RunWith(AndroidJUnit4.class)
public class LoginIntegracionTest {

    @Rule
    public final EntornoRule entorno = new EntornoRule();

    private SessionManager sesion() {
        Context ctx = entorno.contexto;
        return new SessionManager(ctx);
    }

    @Test
    public void usuarioExistenteConPasswordCorrectaEntraYVeSusDatos() {
        try (ActivityScenario<LoginActivity> s = ActivityScenario.launch(LoginActivity.class)) {
            Flujos.iniciarSesion(Escenario.CURP, Escenario.PASSWORD);

            onView(withId(R.id.bottomNavigation)).check(matches(isDisplayed()));
            Flujos.tocar(R.id.nav_profile);

            onView(withId(R.id.tvProfileNombre)).check(matches(withText("JUAN PEREZ LOPEZ")));
            onView(withId(R.id.tvProfileCurp)).check(matches(withText("CURP: " + Escenario.CURP)));
            onView(withId(R.id.tvProfileEstado)).check(matches(withText("Estado: JALISCO")));
            onView(withId(R.id.tvProfileSexo)).check(matches(withText("Sexo: Masculino")));
        }
        assertTrue("Debe quedar sesión activa", sesion().haySesionActiva());
        assertEquals(entorno.esc.usuario1, sesion().getIdUsuarioActivo());
    }

    @Test
    public void cadaUsuarioVeSoloSuPropiaCuenta() {
        try (ActivityScenario<LoginActivity> s = ActivityScenario.launch(LoginActivity.class)) {
            Flujos.iniciarSesion(Escenario.CURP_2, Escenario.PASSWORD);
            Flujos.tocar(R.id.nav_profile);

            onView(withId(R.id.tvProfileCurp)).check(matches(withText("CURP: " + Escenario.CURP_2)));
            onView(withId(R.id.tvProfileNombre)).check(matches(withText("MARIA GOMEZ ARIAS")));
        }
        assertEquals(entorno.esc.usuario2, sesion().getIdUsuarioActivo());
    }

    @Test
    public void passwordIncorrectaMuestraErrorYNoCreaSesion() {
        try (ActivityScenario<LoginActivity> s = ActivityScenario.launch(LoginActivity.class)) {
            Flujos.iniciarSesion(Escenario.CURP, "Incorrecta1#");

            Flujos.verDialogo("La contraseña es incorrecta. Intenta de nuevo.");
            Flujos.cerrarDialogo("Entendido");
            onView(withId(R.id.btnLogin)).check(matches(isDisplayed()));
        }
        assertFalse("Una contraseña incorrecta no debe crear sesión", sesion().haySesionActiva());
    }

    @Test
    public void curpConLongitudIncorrectaSeRechazaSinConsultarLaDb() {
        try (ActivityScenario<LoginActivity> s = ActivityScenario.launch(LoginActivity.class)) {
            Flujos.iniciarSesion("ZZZZ000101HJCXXX00", Escenario.PASSWORD);
            onView(withId(R.id.tilUsuario)).check(matches(Coincidencias.conError(
                    "El usuario debe tener exactamente 20 caracteres (actual: 18).")));
        }
        assertFalse(sesion().haySesionActiva());
    }

    @Test
    public void curpBienFormadaPeroInexistenteMuestraCuentaNoRegistrada() {
        try (ActivityScenario<LoginActivity> s = ActivityScenario.launch(LoginActivity.class)) {
            Flujos.iniciarSesion("ZZZZ000101HJCXXXZZ00", Escenario.PASSWORD);

            Flujos.verDialogo("No existe ninguna cuenta registrada con esa CURP. Regístrate primero.");
            Flujos.cerrarDialogo("Entendido");
        }
        assertFalse(sesion().haySesionActiva());
    }

    @Test
    public void curpEnMinusculasSeRechazaConMensajeDeFormato() {
        try (ActivityScenario<LoginActivity> s = ActivityScenario.launch(LoginActivity.class)) {
            Flujos.iniciarSesion(Escenario.CURP.toLowerCase(), Escenario.PASSWORD);

            onView(withId(R.id.tilUsuario)).check(matches(Coincidencias.conError(
                    "El usuario debe estar completamente en MAYÚSCULAS.")));
        }
        assertFalse(sesion().haySesionActiva());
    }

    @Test
    public void usuarioVacioSeRechaza() {
        try (ActivityScenario<LoginActivity> s = ActivityScenario.launch(LoginActivity.class)) {
            Flujos.iniciarSesion("", Escenario.PASSWORD);

            onView(withId(R.id.tilUsuario)).check(matches(Coincidencias.conError(
                    "El campo usuario no puede estar vacío.")));
        }
        assertFalse(sesion().haySesionActiva());
    }

    @Test
    public void passwordVaciaSeRechaza() {
        try (ActivityScenario<LoginActivity> s = ActivityScenario.launch(LoginActivity.class)) {
            Flujos.iniciarSesion(Escenario.CURP, "");

            onView(withId(R.id.tilPassword)).check(matches(Coincidencias.conError("Ingresa tu contraseña")));
        }
        assertFalse(sesion().haySesionActiva());
    }

    @Test
    public void passwordDistingueMayusculasYMinusculas() {
        try (ActivityScenario<LoginActivity> s = ActivityScenario.launch(LoginActivity.class)) {
            Flujos.iniciarSesion(Escenario.CURP, Escenario.PASSWORD.toLowerCase());

            Flujos.verDialogo("La contraseña es incorrecta. Intenta de nuevo.");
            Flujos.cerrarDialogo("Entendido");
        }
        assertFalse(sesion().haySesionActiva());
    }

    @Test
    public void despuesDeUnErrorSePuedeCorregirYEntrar() {
        try (ActivityScenario<LoginActivity> s = ActivityScenario.launch(LoginActivity.class)) {
            Flujos.iniciarSesion(Escenario.CURP, "Incorrecta1#");
            Flujos.verDialogo("La contraseña es incorrecta. Intenta de nuevo.");
            Flujos.cerrarDialogo("Entendido");

            Flujos.escribir(R.id.etPassword, Escenario.PASSWORD);
            Flujos.tocar(R.id.btnLogin);

            onView(withId(R.id.bottomNavigation)).check(matches(isDisplayed()));
        }
        assertTrue(sesion().haySesionActiva());
    }

    @Test
    public void cerrarSesionRegresaAlLoginYLimpiaLaSesion() {
        try (ActivityScenario<LoginActivity> s = ActivityScenario.launch(LoginActivity.class)) {
            Flujos.iniciarSesion(Escenario.CURP, Escenario.PASSWORD);
            Flujos.tocar(R.id.nav_profile);
            Flujos.tocarConScroll(R.id.btnLogout);

            onView(withId(R.id.btnLogin)).check(matches(isDisplayed()));
        }
        assertFalse(sesion().haySesionActiva());
    }

    @Test
    public void sinSesionLaPantallaPrincipalRedirigeAlLogin() {
        assertFalse(sesion().haySesionActiva());
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.btnLogin)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void conSesionPreviaLaPantallaPrincipalSeAbreSinPedirLogin() {
        entorno.esc.iniciarSesionComo(entorno.esc.usuario1);
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.bottomNavigation)).check(matches(isDisplayed()));
            Flujos.tocar(R.id.nav_profile);
            onView(withId(R.id.tvProfileCurp)).check(matches(withText("CURP: " + Escenario.CURP)));
        }
    }

    @Test
    public void elLoginNoModificaLaBaseDeDatos() {
        int usuariosAntes = entorno.esc.contar("Usuario");
        String hashAntes = entorno.esc.texto("SELECT PasswordHash FROM Usuario WHERE CURP = '" + Escenario.CURP + "'");
        try (ActivityScenario<LoginActivity> s = ActivityScenario.launch(LoginActivity.class)) {
            Flujos.iniciarSesion(Escenario.CURP, Escenario.PASSWORD);
            onView(withId(R.id.bottomNavigation)).check(matches(isDisplayed()));
        }
        assertEquals(usuariosAntes, entorno.esc.contar("Usuario"));
        assertEquals(hashAntes, entorno.esc.texto(
                "SELECT PasswordHash FROM Usuario WHERE CURP = '" + Escenario.CURP + "'"));
    }
}