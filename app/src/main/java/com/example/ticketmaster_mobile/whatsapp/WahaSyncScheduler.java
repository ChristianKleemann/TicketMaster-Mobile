package com.example.ticketmaster_mobile.whatsapp;

import android.content.Context;

import androidx.annotation.VisibleForTesting;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.ticketmaster_mobile.data.AppDatabase;
import com.example.ticketmaster_mobile.data.DatabaseExecutor;

import java.util.concurrent.TimeUnit;

/** Punto único para pedir que se envíen las notificaciones pendientes. Nunca lanza excepciones. */
public final class WahaSyncScheduler {

    private static final String TRABAJO_INMEDIATO = "waha-outbox-drain";
    // Nombre distinto: el worker en ejecución se reprograma a sí mismo sin cancelarse (REPLACE)
    private static final String TRABAJO_DIFERIDO = "waha-outbox-next";
    private static final String TRABAJO_PERIODICO = "waha-outbox-periodic";

    private static volatile boolean pausadoParaPruebas = false;

    private WahaSyncScheduler() {
    }

    /**
     * SOLO PRUEBAS: mientras esté en true no se programa ni se ejecuta el envío en segundo plano,
     * para que una prueba controle cuándo se procesa la cola (sin carreras con WorkManager).
     */
    @VisibleForTesting
    public static void pausarParaPruebas(boolean pausar) {
        pausadoParaPruebas = pausar;
    }

    static boolean estaPausadoParaPruebas() {
        return pausadoParaPruebas;
    }

    private static Constraints conRed() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    /** Intenta enviar ya (con red). Si ya hay una ejecución en cola, no se duplica. */
    public static void programarAhora(Context context) {
        programar(context, 0, ExistingWorkPolicy.KEEP, TRABAJO_INMEDIATO);
    }

    /** Despertar futuro para el siguiente reintento; reemplaza a otro despertar en espera. */
    static void programarEn(Context context, long retrasoMs) {
        programar(context, retrasoMs, ExistingWorkPolicy.REPLACE, TRABAJO_DIFERIDO);
    }

    private static void programar(Context context, long retrasoMs, ExistingWorkPolicy politica,
                                  String nombre) {
        if (pausadoParaPruebas) return;
        try {
            OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(WahaSyncWorker.class)
                    .setConstraints(conRed())
                    .setInitialDelay(retrasoMs, TimeUnit.MILLISECONDS)
                    .build();
            WorkManager.getInstance(context.getApplicationContext())
                    .enqueueUniqueWork(nombre, politica, req);
        } catch (RuntimeException ignorada) {
            // Una falla al programar jamás debe afectar la compra; el periódico lo recuperará
        }
    }

    /** Red de seguridad: cada 15 min (mínimo de Android) revisa la cola. Se llama al iniciar la app. */
    public static void asegurarRevisionPeriodica(Context context) {
        try {
            PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(
                    WahaSyncWorker.class, 15, TimeUnit.MINUTES)
                    .setConstraints(conRed())
                    .build();
            WorkManager.getInstance(context.getApplicationContext())
                    .enqueueUniquePeriodicWork(TRABAJO_PERIODICO, ExistingPeriodicWorkPolicy.KEEP, req);
        } catch (RuntimeException ignorada) {
        }
    }

    /** Reabre las notificaciones que agotaron sus intentos y las vuelve a enviar. */
    public static void reintentarFallidas(Context context) {
        Context app = context.getApplicationContext();
        DatabaseExecutor.IO_EXECUTOR.execute(() -> {
            AppDatabase.getInstance(app).whatsappOutboxDao().reabrirFallidas(System.currentTimeMillis());
            programarAhora(app);
        });
    }

    /** Tras vincular/cambiar el número de WhatsApp: reintenta ya las notificaciones del usuario. */
    public static void reintentarDeUsuario(Context context, int idUsuario) {
        Context app = context.getApplicationContext();
        DatabaseExecutor.IO_EXECUTOR.execute(() -> {
            AppDatabase.getInstance(app).whatsappOutboxDao()
                    .reabrirDeUsuario(idUsuario, System.currentTimeMillis());
            programarAhora(app);
        });
    }

    /** Botón "Reintentar" de la pantalla de confirmación. */
    public static void reintentarOrden(Context context, int idOrden) {
        Context app = context.getApplicationContext();
        DatabaseExecutor.IO_EXECUTOR.execute(() -> {
            AppDatabase.getInstance(app).whatsappOutboxDao()
                    .reabrirOrden(idOrden, System.currentTimeMillis());
            programarAhora(app);
        });
    }
}
