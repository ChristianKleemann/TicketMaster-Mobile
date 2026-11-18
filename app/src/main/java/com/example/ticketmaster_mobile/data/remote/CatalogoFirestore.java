package com.example.ticketmaster_mobile.data.remote;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.ticketmaster_mobile.data.AppDatabase;
import com.example.ticketmaster_mobile.data.CatalogoDemo;
import com.example.ticketmaster_mobile.data.DatabaseExecutor;
import com.example.ticketmaster_mobile.data.entity.Asiento;
import com.example.ticketmaster_mobile.data.entity.DiaFestivo;
import com.example.ticketmaster_mobile.data.entity.Establecimiento;
import com.example.ticketmaster_mobile.data.entity.Funcion;
import com.example.ticketmaster_mobile.data.entity.MetodoPago;
import com.example.ticketmaster_mobile.data.entity.SeccionZona;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Catálogo en Firestore con caché en Room.
 *  - {@link #subir}: SOLO el teléfono "maestro". Sube (o actualiza) todo el catálogo.
 *  - {@link #sincronizar}: cualquier teléfono. Descarga el catálogo si la versión cambió.
 */
public final class CatalogoFirestore {

    private static final String TAG = "CatalogoFirestore";
    private static final String PREFS = "TicketMasterCatalogo";
    private static final String KEY_VERSION = "version_catalogo";
    private static final int TAMANO_LOTE = 400; // Firestore admite máximo 500 operaciones por lote

    private CatalogoFirestore() {}

    private static final class Op {
        final DocumentReference ref;
        final Object datos;

        Op(DocumentReference ref, Object datos) {
            this.ref = ref;
            this.datos = datos;
        }
    }

    // ------------------------------------------------------------------ SUBIR (teléfono maestro)

    /** Genera las funciones que falten (próximos días) y sube todo el catálogo. Idempotente. */
    public static void subir(Context contexto, Runnable alTerminar, Consumer<Exception> alFallar) {
        AppDatabase db = AppDatabase.getInstance(contexto);
        FirebaseFirestore fs = FirebaseFirestore.getInstance();

        DatabaseExecutor.IO_EXECUTOR.execute(() -> {
            CatalogoDemo.asegurar(db);

            List<Op> ops = new ArrayList<>();
            for (Establecimiento e : db.establecimientoDao().listarTodos()) {
                ops.add(new Op(fs.collection("establecimientos").document(String.valueOf(e.idEstablecimiento)), e));
            }
            for (SeccionZona z : db.seccionZonaDao().listarTodas()) {
                ops.add(new Op(fs.collection("zonas").document(String.valueOf(z.idSeccionZona)), z));
            }
            for (Asiento a : db.asientoDao().listarTodos()) {
                ops.add(new Op(fs.collection("asientos").document(String.valueOf(a.idAsiento)), a));
            }
            for (Funcion f : db.funcionDao().listarTodas()) {
                ops.add(new Op(fs.collection("funciones").document(String.valueOf(f.idFuncion)), f));
            }
            for (DiaFestivo d : db.diaFestivoDao().listarTodos()) {
                ops.add(new Op(fs.collection("festivos").document(String.valueOf(d.idDiaFestivo)), d));
            }
            for (MetodoPago m : db.metodoPagoDao().listarTodos()) {
                ops.add(new Op(fs.collection("metodosPago").document(String.valueOf(m.idMetodoPago)), m));
            }
            Log.i(TAG, "Documentos a subir: " + ops.size());
            enviarLotes(fs, ops, 0, alTerminar, alFallar);
        });
    }

    private static void enviarLotes(FirebaseFirestore fs, List<Op> ops, int desde,
                                    Runnable alTerminar, Consumer<Exception> alFallar) {
        if (desde >= ops.size()) {
            // Al final se publica la nueva versión: los demás teléfonos sabrán que deben descargar
            Map<String, Object> meta = new HashMap<>();
            meta.put("version", System.currentTimeMillis());
            fs.collection("meta").document("catalogo").set(meta)
                    .addOnSuccessListener(v -> alTerminar.run())
                    .addOnFailureListener(e -> alFallar.accept(e));
            return;
        }
        int hasta = Math.min(desde + TAMANO_LOTE, ops.size());
        WriteBatch lote = fs.batch();
        for (int i = desde; i < hasta; i++) {
            lote.set(ops.get(i).ref, ops.get(i).datos);
        }
        lote.commit()
                .addOnSuccessListener(v -> enviarLotes(fs, ops, hasta, alTerminar, alFallar))
                .addOnFailureListener(e -> alFallar.accept(e));
    }

    // ------------------------------------------------------------------ SINCRONIZAR (todos)

    /** Descarga el catálogo a Room si hay una versión nueva (o si Room está vacío). Sin conexión no hace nada. */
    public static void sincronizar(Context contexto) {
        Context app = contexto.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        FirebaseFirestore fs = FirebaseFirestore.getInstance();

        fs.collection("meta").document("catalogo").get()
                .addOnSuccessListener(meta -> {
                    Long remota = meta.getLong("version");
                    if (remota == null) {
                        Log.i(TAG, "El catálogo aún no se ha subido a Firestore.");
                        return;
                    }
                    DatabaseExecutor.runAsync(
                            () -> AppDatabase.getInstance(app).establecimientoDao().contar(),
                            cantidadLocal -> {
                                boolean actualizado = remota == prefs.getLong(KEY_VERSION, 0L);
                                if (actualizado && cantidadLocal > 0) return;
                                descargarTodo(app, fs, prefs, remota);
                            });
                })
                .addOnFailureListener(e -> Log.w(TAG, "Sin conexión: se usa el catálogo local.", e));
    }

    private static void descargarTodo(Context app, FirebaseFirestore fs, SharedPreferences prefs, long version) {
        Task<QuerySnapshot> tEst = fs.collection("establecimientos").get();
        Task<QuerySnapshot> tZon = fs.collection("zonas").get();
        Task<QuerySnapshot> tAsi = fs.collection("asientos").get();
        Task<QuerySnapshot> tFun = fs.collection("funciones").get();
        Task<QuerySnapshot> tFes = fs.collection("festivos").get();
        Task<QuerySnapshot> tMet = fs.collection("metodosPago").get();

        Tasks.whenAllSuccess(tEst, tZon, tAsi, tFun, tFes, tMet)
                .addOnSuccessListener(r -> DatabaseExecutor.IO_EXECUTOR.execute(() -> {
                    AppDatabase db = AppDatabase.getInstance(app);
                    List<Establecimiento> est = ((QuerySnapshot) r.get(0)).toObjects(Establecimiento.class);
                    List<SeccionZona> zon = ((QuerySnapshot) r.get(1)).toObjects(SeccionZona.class);
                    List<Asiento> asi = ((QuerySnapshot) r.get(2)).toObjects(Asiento.class);
                    List<Funcion> fun = ((QuerySnapshot) r.get(3)).toObjects(Funcion.class);
                    List<DiaFestivo> fes = ((QuerySnapshot) r.get(4)).toObjects(DiaFestivo.class);
                    List<MetodoPago> met = ((QuerySnapshot) r.get(5)).toObjects(MetodoPago.class);

                    // El orden importa por las llaves foráneas: primero los "padres"
                    db.runInTransaction(() -> {
                        db.establecimientoDao().upsertar(est);
                        db.metodoPagoDao().upsertar(met);
                        db.seccionZonaDao().upsertar(zon);
                        db.funcionDao().upsertar(fun);
                        db.diaFestivoDao().upsertar(fes);
                        db.asientoDao().upsertar(asi);
                    });
                    prefs.edit().putLong(KEY_VERSION, version).apply();
                    Log.i(TAG, "Catálogo sincronizado: " + est.size() + " lugares, "
                            + fun.size() + " funciones, " + asi.size() + " asientos.");
                }))
                .addOnFailureListener(e -> Log.w(TAG, "No se pudo descargar el catálogo.", e));
    }
}
