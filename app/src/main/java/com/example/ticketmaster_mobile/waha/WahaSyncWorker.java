package com.example.ticketmaster_mobile.waha;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.ticketmaster_mobile.data.AppDatabase;

import java.util.concurrent.TimeUnit;

/**
 * Vacía la cola de notificaciones de WhatsApp. WorkManager garantiza que se ejecute con red, que
 * sobreviva al cierre de la app o al reinicio del teléfono, y que no corran dos copias a la vez
 * (trabajo único); además se serializa dentro del proceso con un candado.
 */
public class WahaSyncWorker extends Worker {

    private static final Object LOCK = new Object();
    private static final long RETENCION_ENVIADAS_MS = TimeUnit.DAYS.toMillis(30);

    /** Compartido entre ejecuciones para que el circuito recuerde fallos previos en el proceso. */
    private static final CircuitBreaker BREAKER =
            new CircuitBreaker(5, 60_000L, System::currentTimeMillis);

    public WahaSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (WahaSyncScheduler.estaPausadoParaPruebas()) return Result.success();
        WahaConfig config = WahaConfigProvider.actual();
        if (!config.servidorConfigurado()) return Result.success();   // integración desactivada

        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        RoomOutboxStore store = new RoomOutboxStore(db.whatsappOutboxDao());

        synchronized (LOCK) {
            try {
                db.whatsappOutboxDao().purgarEnviadasAntesDe(
                        System.currentTimeMillis() - RETENCION_ENVIADAS_MS);

                OutboxProcessor procesador = new OutboxProcessor(
                        store,
                        new RoomTicketSource(db),
                        new WahaClient(config),
                        Backoff.porDefecto(),
                        BREAKER,
                        System::currentTimeMillis,
                        OutboxProcessor.MAX_INTENTOS);
                procesador.procesar();
            } catch (RuntimeException e) {
                // Fallo inesperado (p. ej. DB cerrada): WorkManager reintenta con su backoff
                return Result.retry();
            }
            // Reprogramar un único despertar para la próxima entrada pendiente
            long proximo = store.proximoTrabajoMs();
            if (proximo >= 0) {
                long espera = Math.max(BREAKER.msHastaReintento(), proximo - System.currentTimeMillis());
                WahaSyncScheduler.programarEn(getApplicationContext(), Math.max(espera, 5_000L));
            }
        }
        return Result.success();
    }
}
