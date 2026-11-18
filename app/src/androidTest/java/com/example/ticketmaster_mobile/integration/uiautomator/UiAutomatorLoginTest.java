package com.example.ticketmaster_mobile.integration.uiautomator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;

import com.example.ticketmaster_mobile.MainActivity;
import com.example.ticketmaster_mobile.integration.soporte.EntornoRule;
import com.example.ticketmaster_mobile.integration.soporte.Escenario;
import com.example.ticketmaster_mobile.session.SessionManager;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Login y sesión vistos como los ve un usuario (UI Automator, caja negra): app lanzada desde el
 * launcher del sistema, interacción solo por lo visible en pantalla.
 */
@RunWith(AndroidJUnit4.class)
public class UiAutomatorLoginTest {

    @Rule
    public final EntornoRule entorno = new EntornoRule();

    @After
    public void cerrar() {
        Ua.cerrarApp();
    }

    private boolean haySesion() {
        return new SessionManager(entorno.contexto).haySesionActiva();
    }

    @Test
    public void usuarioExistenteIniciaSesionDesdeElLauncherYVeSuPerfil() {
        Ua.lanzarDesdeElLauncher(true);

        Ua.iniciarSesion(Escenario.CURP, Escenario.PASSWORD);

        assertTrue("Debe mostrarse la navegación principal", Ua.hay("bottomNavigation", Ua.ESPERA_MS));
        Ua.tocar("nav_profile");
        assertEquals("CURP: " + Escenario.CURP, Ua.texto("tvProfileCurp"));
        assertEquals("JUAN PEREZ LOPEZ", Ua.texto("tvProfileNombre"));
        assertTrue(haySesion());
    }

    @Test
    public void passwordIncorrectaMuestraUnDialogoYNoEntra() {
        Ua.lanzarDesdeElLauncher(true);

        Ua.iniciarSesion(Escenario.CURP, "Incorrecta1#");

        assertTrue(Ua.hayTexto("La contraseña es incorrecta. Intenta de nuevo.", Ua.ESPERA_MS));
        Ua.tocarTexto("Entendido");
        assertTrue("Sigue en el login", Ua.hay("btnLogin", Ua.ESPERA_MS));
        assertFalse(Ua.hay("bottomNavigation", 500));
        assertFalse(haySesion());
    }

    @Test
    public void curpNoRegistradaMuestraUnDialogoYNoEntra() {
        Ua.lanzarDesdeElLauncher(true);

        Ua.iniciarSesion("ZZZZ000101HJCXXXZZ00", Escenario.PASSWORD);

        assertTrue(Ua.hayTexto("No existe ninguna cuenta registrada con esa CURP. Regístrate primero.", Ua.ESPERA_MS));
        Ua.tocarTexto("Entendido");
        assertFalse(haySesion());
    }

    @Test
    public void camposVaciosMuestranSusErrores() {
        Ua.lanzarDesdeElLauncher(true);

        Ua.tocar("btnLogin");
        assertTrue(Ua.hayTexto("El campo usuario no puede estar vacío.", Ua.ESPERA_MS));
        assertFalse(haySesion());

        // Con usuario válido pero sin contraseña, se pide la contraseña
        Ua.escribir("etUsuario", Escenario.CURP);
        Ua.tocar("btnLogin");
        assertTrue(Ua.hayTexto("Ingresa tu contraseña", Ua.ESPERA_MS));
        assertFalse(haySesion());
    }

    @Test
    public void segundoUsuarioVeSusPropiosDatos() {
        Ua.lanzarDesdeElLauncher(true);

        Ua.iniciarSesion(Escenario.CURP_2, Escenario.PASSWORD);

        assertTrue(Ua.hay("bottomNavigation", Ua.ESPERA_MS));
        assertEquals("CURP: " + Escenario.CURP_2, Ua.texto("tvProfileCurp"));
        assertEquals(entorno.esc.usuario2, new SessionManager(entorno.contexto).getIdUsuarioActivo());
    }

    @Test
    public void sinSesionLaPantallaPrincipalRedirigeAlLogin() {
        Context ctx = entorno.contexto;
        Intent intent = new Intent(ctx, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ctx.startActivity(intent);
        Ua.esperarApp();

        assertTrue("Sin sesión se debe pedir iniciar sesión", Ua.hay("btnLogin", Ua.ESPERA_MS));
        assertFalse(Ua.hay("bottomNavigation", 500));
    }

    @Test
    public void laSesionSobreviveAIrAlInicioYVolverALaApp() {
        Ua.lanzarDesdeElLauncher(true);
        Ua.iniciarSesion(Escenario.CURP, Escenario.PASSWORD);
        assertTrue(Ua.hay("bottomNavigation", Ua.ESPERA_MS));

        Ua.dispositivo().pressHome();
        assertTrue(Ua.dispositivo().wait(Until.gone(By.pkg(Ua.PAQUETE).depth(0)), Ua.ESPERA_MS));
        Ua.lanzarDesdeElLauncher(false);

        assertTrue("Al volver sigue dentro de la cuenta", Ua.hay("bottomNavigation", Ua.ESPERA_MS));
        assertTrue(haySesion());
    }

    @Test
    public void cerrarSesionRegresaAlLoginYAlRelanzarPideCredenciales() {
        Ua.lanzarDesdeElLauncher(true);
        Ua.iniciarSesion(Escenario.CURP, Escenario.PASSWORD);
        assertTrue(Ua.hay("bottomNavigation", Ua.ESPERA_MS));

        Ua.tocar("btnLogout");

        assertTrue(Ua.hay("btnLogin", Ua.ESPERA_MS));
        assertFalse(haySesion());

        Ua.dispositivo().pressBack();
        Ua.lanzarDesdeElLauncher(true);
        assertTrue("Tras cerrar sesión la app abre en el login", Ua.hay("btnLogin", Ua.ESPERA_MS));
        assertFalse(Ua.hay("bottomNavigation", 500));
    }

    @Test
    public void atrasDesdeElPerfilSaleDeLaAppSinVolverAlLogin() {
        Ua.lanzarDesdeElLauncher(true);
        Ua.iniciarSesion(Escenario.CURP, Escenario.PASSWORD);
        assertTrue(Ua.hay("bottomNavigation", Ua.ESPERA_MS));

        Ua.dispositivo().pressBack();

        assertTrue("La app debe cerrarse",
                Ua.dispositivo().wait(Until.gone(By.pkg(Ua.PAQUETE).depth(0)), Ua.ESPERA_MS));
        assertTrue("Y la sesión sigue guardada", haySesion());
    }

    @Test
    public void elBotonDeRegistroAbreElFormularioYAtrasRegresaAlLogin() {
        Ua.lanzarDesdeElLauncher(true);

        Ua.tocar("btnGoToSignUp");
        // El formulario es largo: el botón puede quedar fuera de pantalla hasta desplazarse
        assertTrue("Debe salir del login", Ua.noHay("btnLogin", Ua.ESPERA_MS));
        Ua.desplazarHasta("btnRegister");
        assertTrue(Ua.hay("btnRegister", Ua.ESPERA_MS));

        Ua.dispositivo().pressBack();
        assertTrue(Ua.hay("btnLogin", Ua.ESPERA_MS));
    }
}