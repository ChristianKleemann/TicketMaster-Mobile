package com.example.ticketmaster_mobile.integration.soporte;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.IdlingRegistry;
import androidx.work.WorkManager;

import com.example.ticketmaster_mobile.whatsapp.GreenApiConfig;
import com.example.ticketmaster_mobile.whatsapp.GreenApiConfigProvider;
import com.example.ticketmaster_mobile.whatsapp.WahaSyncScheduler;
import com.example.ticketmaster_mobile.data.repository.CompraRepository;

import org.junit.rules.ExternalResource;

import java.io.IOException;

/**
 * Prepara (y limpia) todo lo que una prueba de integración necesita:
 *  - base de datos en memoria con el {@link Escenario} sembrado (reemplaza a la real);
 *  - un WAHA simulado y la configuración de la app apuntando a él;
 *  - Espresso esperando al executor de base de datos;
 *  - sesión cerrada y WorkManager en pausa (salvo que la prueba pida el envío real en segundo plano).
 */
public class EntornoRule extends ExternalResource {

    public static final String SESION = "default";
    public static final String DESTINO = "+52 1 33 2218 0006";
    public static final String CHAT_ID_ESPERADO = "5213322180006@c.us";

    private final boolean workManagerReal;

    public Context contexto;
    public Escenario esc;
    public FakeGreenApiServer green;
    private DbIdlingResource idling;

    /** Envío en segundo plano en pausa: la prueba controla cuándo se procesa la cola. */
    public EntornoRule() {
        this(false);
    }

    /** @param workManagerReal true para que el Worker real procese la cola (pruebas extremo a extremo). */
    public EntornoRule(boolean workManagerReal) {
        this.workManagerReal = workManagerReal;
    }

    @Override
    protected void before() throws IOException {
        contexto = ApplicationProvider.getApplicationContext();
        WahaSyncScheduler.pausarParaPruebas(!workManagerReal);
        CompraRepository.desactivarReservasRemotas(true);
        cancelarTrabajos();

        esc = Escenario.crear(contexto);
        esc.cerrarSesion();
        green = FakeGreenApiServer.iniciar();
        configurarGreenApi(configuracionNormal());

        idling = new DbIdlingResource();
        IdlingRegistry.getInstance().register(idling);

    }

    @Override
    protected void after() {
        if (idling != null) IdlingRegistry.getInstance().unregister(idling);
        GreenApiConfigProvider.setOverrideForTesting(null);
        WahaSyncScheduler.pausarParaPruebas(false);
        CompraRepository.desactivarReservasRemotas(false);
        cancelarTrabajos();
        if (green != null) green.close();
        if (esc != null) {
            esc.cerrarSesion();
            esc.cerrar();
        }
    }

    private void cancelarTrabajos() {
        try {
            WorkManager.getInstance(contexto).cancelAllWork();
        } catch (RuntimeException ignorada) {
        }
    }

    /** Configuración válida apuntando al WAHA simulado (timeouts cortos para pruebas rápidas). */
    public GreenApiConfig configuracionNormal() {
        return new GreenApiConfig(green.baseUrl(),
                FakeGreenApiServer.ID_INSTANCE,
                FakeGreenApiServer.API_KEY_POR_DEFECTO,
                DESTINO,
                2000, 2000, true);
    }

    public void configurarGreenApi(GreenApiConfig config) {
        GreenApiConfigProvider.setOverrideForTesting(config);
    }

    /** GREEN API sin configurar (URL y destino vacíos): la integración queda desactivada. */
    public void desactivarGreenApi() {
        configurarGreenApi(new GreenApiConfig("", "", "", "", 2000, 2000, true));
    }


}
