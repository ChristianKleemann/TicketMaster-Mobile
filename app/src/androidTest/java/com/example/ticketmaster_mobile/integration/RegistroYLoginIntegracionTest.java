package com.example.ticketmaster_mobile.integration;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.ticketmaster_mobile.PasswordUtils;
import com.example.ticketmaster_mobile.R;
import com.example.ticketmaster_mobile.RegisterActivity;
import com.example.ticketmaster_mobile.ValidationsUtils;
import com.example.ticketmaster_mobile.integration.soporte.Coincidencias;
import com.example.ticketmaster_mobile.integration.soporte.EntornoRule;
import com.example.ticketmaster_mobile.integration.soporte.Escenario;
import com.example.ticketmaster_mobile.integration.soporte.Flujos;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Integración Registro ↔ validaciones ↔ DB ↔ Login (Espresso). */
@RunWith(AndroidJUnit4.class)
public class RegistroYLoginIntegracionTest {

    private static final String PASSWORD_NUEVA = "Nuev0#Ab";

    @Rule
    public final EntornoRule entorno = new EntornoRule();

    /** CURP que la propia app genera para los datos de {@link #llenarDatosPersonales()}. */
    private static String curpEsperada() {
        return ValidationsUtils.generarCurpExtendido(
                "SANCHEZ", "RAMOS", "LUIS", "2001", "3", "7", "H", "JALISCO");
    }

    private void llenarDatosPersonales() {
        Flujos.escribirConScroll(R.id.etNombre1, "LUIS");
        Flujos.escribirConScroll(R.id.etNombre2, "ENRIQUE");
        Flujos.escribirConScroll(R.id.etApellido1, "SANCHEZ");
        Flujos.escribirConScroll(R.id.etApellido2, "RAMOS");
        Flujos.escribirConScroll(R.id.etAnio, "2001");
        Flujos.escribirConScroll(R.id.etMes, "3");
        Flujos.escribirConScroll(R.id.etDia, "7");
        Flujos.escribirConScroll(R.id.etSexo, "H");
        Flujos.escribirConScroll(R.id.etEntidad, "JALISCO");
    }

    @Test
    public void registroCompletoGuardaUsuarioConPasswordHasheadaYAbreLoginConLaCurp() {
        String curp = curpEsperada();
        assertEquals(20, curp.length());
        int antes = entorno.esc.contar("Usuario");

        try (ActivityScenario<RegisterActivity> s = ActivityScenario.launch(RegisterActivity.class)) {
            llenarDatosPersonales();
            Flujos.tocarConScroll(R.id.btnGenerarCurp);
            onView(withId(R.id.etUsuario)).check(matches(withText(curp)));
            Flujos.escribirConScroll(R.id.etPassword, PASSWORD_NUEVA);
            Flujos.tocarConScroll(R.id.btnRegister);

            // Tras registrarse, la app abre el login con la CURP ya escrita
            onView(withId(R.id.btnLogin)).check(matches(isDisplayed()));
            onView(withId(R.id.etUsuario)).check(matches(withText(curp)));
        }

        assertEquals(antes + 1, entorno.esc.contar("Usuario"));
        String filtro = " FROM Usuario WHERE CURP = '" + curp + "'";
        assertEquals("LUIS ENRIQUE", entorno.esc.texto("SELECT Nombre" + filtro));
        assertEquals("SANCHEZ", entorno.esc.texto("SELECT ApellidoPaterno" + filtro));
        assertEquals("RAMOS", entorno.esc.texto("SELECT ApellidoMaterno" + filtro));
        assertEquals("2001-03-07", entorno.esc.texto("SELECT FechaNacimiento" + filtro));
        assertEquals("JALISCO", entorno.esc.texto("SELECT Estado" + filtro));
        assertEquals("Masculino", entorno.esc.texto("SELECT Sexo" + filtro));

        String hash = entorno.esc.texto("SELECT PasswordHash" + filtro);
        assertNotEquals("La contraseña no puede guardarse en claro", PASSWORD_NUEVA, hash);
        assertEquals(PasswordUtils.hashPassword(PASSWORD_NUEVA), hash);
        assertEquals(64, hash.length());
        assertFalse("La contraseña en claro no debe aparecer en ninguna tabla",
                String.join("\n", entorno.esc.volcarTodoComoTexto()).contains(PASSWORD_NUEVA));
    }

    @Test
    public void usuarioRecienRegistradoPuedeIniciarSesion() {
        String curp = curpEsperada();
        try (ActivityScenario<RegisterActivity> s = ActivityScenario.launch(RegisterActivity.class)) {
            llenarDatosPersonales();
            Flujos.tocarConScroll(R.id.btnGenerarCurp);
            Flujos.escribirConScroll(R.id.etPassword, PASSWORD_NUEVA);
            Flujos.tocarConScroll(R.id.btnRegister);

            onView(withId(R.id.btnLogin)).check(matches(isDisplayed()));
            Flujos.escribir(R.id.etPassword, PASSWORD_NUEVA);
            Flujos.tocar(R.id.btnLogin);

            onView(withId(R.id.bottomNavigation)).check(matches(isDisplayed()));
            Flujos.tocar(R.id.nav_profile);
            onView(withId(R.id.tvProfileCurp)).check(matches(withText("CURP: " + curp)));
            onView(withId(R.id.tvProfileNombre)).check(matches(withText("LUIS ENRIQUE SANCHEZ RAMOS")));
        }
    }

    @Test
    public void curpDuplicadaSeRechazaYNoCreaOtroUsuario() {
        int antes = entorno.esc.contar("Usuario");
        try (ActivityScenario<RegisterActivity> s = ActivityScenario.launch(RegisterActivity.class)) {
            llenarDatosPersonales();
            // CURP ya sembrada en la base de datos
            Flujos.escribirConScroll(R.id.etUsuario, Escenario.CURP);
            Flujos.escribirConScroll(R.id.etPassword, PASSWORD_NUEVA);
            Flujos.tocarConScroll(R.id.btnRegister);

            onView(withId(R.id.tilUsuario)).check(matches(
                    Coincidencias.conError("Ya existe una cuenta registrada con esa CURP")));
        }
        assertEquals(antes, entorno.esc.contar("Usuario"));
    }

    @Test
    public void passwordDebilSeRechazaYNoGuardaNada() {
        int antes = entorno.esc.contar("Usuario");
        try (ActivityScenario<RegisterActivity> s = ActivityScenario.launch(RegisterActivity.class)) {
            llenarDatosPersonales();
            Flujos.tocarConScroll(R.id.btnGenerarCurp);
            Flujos.escribirConScroll(R.id.etPassword, "abc");
            Flujos.tocarConScroll(R.id.btnRegister);

            String esperado = ValidationsUtils.obtenerErrorPassword("abc");
            onView(withId(R.id.tilPassword)).check(matches(Coincidencias.conError(esperado)));
        }
        assertEquals(antes, entorno.esc.contar("Usuario"));
    }

    @Test
    public void curpInvalidaSeRechazaYNoGuardaNada() {
        int antes = entorno.esc.contar("Usuario");
        try (ActivityScenario<RegisterActivity> s = ActivityScenario.launch(RegisterActivity.class)) {
            llenarDatosPersonales();
            Flujos.escribirConScroll(R.id.etUsuario, "CORTA");
            Flujos.escribirConScroll(R.id.etPassword, PASSWORD_NUEVA);
            Flujos.tocarConScroll(R.id.btnRegister);

            onView(withId(R.id.tilUsuario)).check(matches(Coincidencias.conError(
                    "El usuario debe tener exactamente 20 caracteres (actual: 5).")));
        }
        assertEquals(antes, entorno.esc.contar("Usuario"));
    }

    @Test
    public void elRegistroNoCreaSesionAutomaticamente() {
        try (ActivityScenario<RegisterActivity> s = ActivityScenario.launch(RegisterActivity.class)) {
            llenarDatosPersonales();
            Flujos.tocarConScroll(R.id.btnGenerarCurp);
            Flujos.escribirConScroll(R.id.etPassword, PASSWORD_NUEVA);
            Flujos.tocarConScroll(R.id.btnRegister);
            onView(withId(R.id.btnLogin)).check(matches(isDisplayed()));
        }
        assertFalse(new com.example.ticketmaster_mobile.session.SessionManager(entorno.contexto).haySesionActiva());
        assertTrue(entorno.esc.contar("Usuario") >= 3);
    }
}
