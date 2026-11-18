package com.example.ticketmaster_mobile;

import android.app.Application;

import com.example.ticketmaster_mobile.waha.WahaConfigProvider;
import com.example.ticketmaster_mobile.waha.WahaSyncScheduler;
import com.example.ticketmaster_mobile.data.remote.CatalogoFirestore;

/**
 * Al iniciar el proceso se asegura la revisión periódica de la cola de WhatsApp y se intenta
 * enviar lo pendiente (p. ej. compras hechas sin conexión o tras un cierre inesperado).
 */
public class TicketMasterApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        if (WahaConfigProvider.actual().servidorConfigurado()) {
            WahaSyncScheduler.asegurarRevisionPeriodica(this);
            WahaSyncScheduler.programarAhora(this);
        }
        CatalogoFirestore.sincronizar(this);
    }
}
