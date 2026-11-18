package com.example.ticketmaster_mobile.data.remote;

import android.content.Context;
import android.util.Log;

import com.example.ticketmaster_mobile.data.AppDatabase;
import com.example.ticketmaster_mobile.data.DatabaseExecutor;
import com.example.ticketmaster_mobile.data.entity.Boleto;
import com.example.ticketmaster_mobile.data.entity.Orden;
import com.example.ticketmaster_mobile.data.entity.Pago;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.Map;

/** Copia a Room las órdenes del usuario que están en Firestore y aún no existen en este teléfono. */
public final class OrdenesFirestore {

    private static final String TAG = "OrdenesFirestore";

    private OrdenesFirestore() {}

    public static void sincronizar(Context contexto, int idUsuarioLocal) {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) return;
        Context app = contexto.getApplicationContext();

        FirebaseFirestore.getInstance().collection("ordenes")
                .whereEqualTo("uid", usuario.getUid())
                .get()
                .addOnSuccessListener(snap -> DatabaseExecutor.IO_EXECUTOR.execute(() -> {
                    AppDatabase db = AppDatabase.getInstance(app);
                    int importadas = 0;
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        try {
                            if (importar(db, doc, idUsuarioLocal)) importadas++;
                        } catch (Exception e) {
                            // Ej.: el catálogo aún no se descargó (llave foránea). Se reintenta al reabrir Perfil.
                            Log.w(TAG, "No se pudo importar la orden " + doc.getId(), e);
                        }
                    }
                    Log.i(TAG, "Órdenes importadas desde Firestore: " + importadas);
                }))
                .addOnFailureListener(e -> Log.w(TAG, "Sin conexión: se usa el historial local.", e));
    }

    @SuppressWarnings("unchecked")
    private static boolean importar(AppDatabase db, DocumentSnapshot doc, int idUsuarioLocal) {
        if (!"Pagada".equals(doc.getString("estadoOrden"))) return false;

        Map<String, Object> pagoMapa = (Map<String, Object>) doc.get("pago");
        List<Map<String, Object>> items = (List<Map<String, Object>>) doc.get("boletos");
        Long idFuncion = doc.getLong("idFuncion");
        if (pagoMapa == null || items == null || items.isEmpty() || idFuncion == null) return false;

        // Sin referencia (órdenes anteriores al Paso 5) no se puede saber si ya existen: se omiten
        String referencia = (String) pagoMapa.get("referenciaExterna");
        if (referencia == null || referencia.isEmpty()) return false;
        if (db.pagoDao().contarPorReferencia(referencia) > 0) return false;

        String fecha = doc.getString("fechaCompra") == null ? "" : doc.getString("fechaCompra");
        Double monto = doc.getDouble("montoTotal");

        db.runInTransaction(() -> {
            Orden orden = new Orden();
            orden.idUsuario = idUsuarioLocal;
            orden.fechaCompra = fecha;
            orden.montoTotal = monto == null ? 0.0 : monto;
            orden.estadoOrden = "Pagada";
            int idOrden = (int) db.ordenDao().insertar(orden);

            for (Map<String, Object> m : items) {
                Boleto b = new Boleto();
                b.idOrden = idOrden;
                b.idFuncion = idFuncion.intValue();
                Number asiento = (Number) m.get("idAsiento");
                b.idAsiento = asiento == null ? null : asiento.intValue();
                b.idSeccionZona = ((Number) m.get("idSeccionZona")).intValue();
                b.precioFinal = ((Number) m.get("precio")).doubleValue();
                b.estadoBoleto = "Emitido";
                db.boletoDao().insertar(b);
            }

            Pago pago = new Pago();
            pago.idOrden = idOrden;
            pago.idMetodoPago = ((Number) pagoMapa.get("idMetodoPago")).intValue();
            pago.monto = orden.montoTotal;
            pago.fechaPago = fecha;
            pago.estadoPago = "Aprobado";
            pago.referenciaExterna = referencia;
            pago.nombreTitular = (String) pagoMapa.get("nombreTitular");
            pago.ultimosCuatroDigitos = (String) pagoMapa.get("ultimosCuatroDigitos");
            pago.cuentaPaypal = (String) pagoMapa.get("cuentaPaypal");
            db.pagoDao().insertar(pago);
        });
        return true;
    }
}
