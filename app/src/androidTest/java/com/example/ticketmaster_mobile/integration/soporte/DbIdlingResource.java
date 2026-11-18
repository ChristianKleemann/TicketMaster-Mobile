package com.example.ticketmaster_mobile.integration.soporte;

import android.os.Handler;
import android.os.Looper;

import androidx.test.espresso.IdlingResource;

import com.example.ticketmaster_mobile.data.DatabaseExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Hace que Espresso espere a que termine el trabajo de base de datos de la app.
 *
 * La app ejecuta Room y las tareas en {@link DatabaseExecutor#IO_EXECUTOR}, un pool propio que
 * Espresso no conoce; sin este recurso las aserciones se adelantan a los datos. El escenario de
 * pruebas también usa ese pool como executor de consultas de Room (LiveData incluido).
 * Un trabajo publica su resultado al hilo principal ANTES de terminar, así que "pool ocioso" implica
 * que ya no queda nada por entregar sin que el looper principal lo vea.
 */
public final class DbIdlingResource implements IdlingResource {

    private final ThreadPoolExecutor pool;
    private final Handler principal = new Handler(Looper.getMainLooper());
    private volatile ResourceCallback callback;
    private volatile boolean sondeando;

    public DbIdlingResource() {
        if (!(DatabaseExecutor.IO_EXECUTOR instanceof ThreadPoolExecutor)) {
            throw new IllegalStateException("IO_EXECUTOR ya no es un ThreadPoolExecutor: "
                    + "actualiza DbIdlingResource");
        }
        this.pool = (ThreadPoolExecutor) DatabaseExecutor.IO_EXECUTOR;
    }

    @Override
    public String getName() {
        return "DatabaseExecutor.IO_EXECUTOR";
    }

    @Override
    public boolean isIdleNow() {
        boolean ocioso = pool.getActiveCount() == 0 && pool.getQueue().isEmpty();
        if (ocioso) {
            ResourceCallback cb = callback;
            if (cb != null) cb.onTransitionToIdle();
        } else if (!sondeando) {
            sondeando = true;
            principal.postDelayed(this::sondear, 15);
        }
        return ocioso;
    }

    private void sondear() {
        sondeando = false;
        isIdleNow();
    }

    @Override
    public void registerIdleTransitionCallback(ResourceCallback callback) {
        this.callback = callback;
    }
}
