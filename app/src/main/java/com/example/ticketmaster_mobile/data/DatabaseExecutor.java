package com.example.ticketmaster_mobile.data;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pool de hilos único para toda la app, usado para ejecutar operaciones de Room
 * (inserts, updates y transacciones) fuera del hilo principal, tal como exige Room.
 * Las lecturas (SELECT) se hacen preferentemente con LiveData, que Room ya ejecuta
 * en segundo plano automáticamente.
 */
public class DatabaseExecutor {

    private static final int NUM_THREADS = 4;
    public static final ExecutorService IO_EXECUTOR = Executors.newFixedThreadPool(NUM_THREADS);
    private static final Handler MAIN_THREAD_HANDLER = new Handler(Looper.getMainLooper());

    private DatabaseExecutor() {
    }

    /** Ejecuta `task` en un hilo de fondo y entrega el resultado de vuelta en el hilo principal. */
    public static <T> void runAsync(BackgroundTask<T> task, ResultCallback<T> callback) {
        IO_EXECUTOR.execute(() -> {
            T result = task.run();
            MAIN_THREAD_HANDLER.post(() -> callback.onResult(result));
        });
    }

    public interface BackgroundTask<T> {
        T run();
    }

    public interface ResultCallback<T> {
        void onResult(T result);
    }
}
