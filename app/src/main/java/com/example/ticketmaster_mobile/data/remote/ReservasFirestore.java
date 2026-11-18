package com.example.ticketmaster_mobile.data.remote;

import com.example.ticketmaster_mobile.data.repository.CompraRepository.ItemCompra;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Reserva de asientos y cupos en Firestore. Los métodos bloquean: llamarlos SIEMPRE desde un hilo
 * de fondo (CompraRepository ya lo hace).
 */
public final class ReservasFirestore {

    private static final long ESPERA_SEG = 20;

    private ReservasFirestore() {}

    /** Lo necesario para deshacer una reserva si la compra local falla. */
    public static final class Reserva {
        final DocumentReference ordenRef;
        final List<DocumentReference> ventas = new ArrayList<>();
        final DocumentReference limiteRef;
        final int nBoletos;
        final Map<DocumentReference, Integer> pedidosPorZona = new HashMap<>();

        Reserva(DocumentReference ordenRef, DocumentReference limiteRef, int nBoletos) {
            this.ordenRef = ordenRef;
            this.limiteRef = limiteRef;
            this.nBoletos = nBoletos;
        }
    }

    private static long valor(DocumentSnapshot doc, String campo) {
        Long v = doc.getLong(campo);
        return v == null ? 0L : v;
    }

    /**
     * Transacción: verifica asientos libres, aforo por zona y máximo por usuario; si todo está bien,
     * marca los asientos, actualiza contadores y guarda la orden en la nube.
     * Lanza una excepción (ver {@link #mensajeDe}) si no se pudo reservar.
     */
    public static Reserva reservar(String uid, int idFuncion, List<ItemCompra> items,
                                   Map<Integer, Integer> capacidadPorZona, int maxPorUsuario,
                                   Map<String, Object> datosOrden) throws Exception {
        FirebaseFirestore fs = FirebaseFirestore.getInstance();
        int n = items.size();

        Reserva r = new Reserva(fs.collection("ordenes").document(),
                fs.collection("limites").document(uid + "_" + idFuncion), n);

        Map<Integer, Integer> pedidosPorIdZona = new HashMap<>();
        Map<Integer, DocumentReference> refZona = new HashMap<>();
        for (ItemCompra it : items) {
            if (it.idAsiento != null) {
                r.ventas.add(fs.collection("ventas").document(idFuncion + "_" + it.idAsiento));
            }
            Integer previo = pedidosPorIdZona.get(it.idSeccionZona);
            pedidosPorIdZona.put(it.idSeccionZona, previo == null ? 1 : previo + 1);
            if (!refZona.containsKey(it.idSeccionZona)) {
                refZona.put(it.idSeccionZona,
                        fs.collection("ocupacion").document(idFuncion + "_" + it.idSeccionZona));
            }
        }
        for (Map.Entry<Integer, DocumentReference> e : refZona.entrySet()) {
            r.pedidosPorZona.put(e.getValue(), pedidosPorIdZona.get(e.getKey()));
        }

        Tasks.await(fs.runTransaction(tx -> {
            // ---- LECTURAS (Firestore exige leer todo antes de escribir)
            for (DocumentReference v : r.ventas) {
                if (tx.get(v).exists()) {
                    throw new FirebaseFirestoreException(
                            "Uno o más asientos ya fueron comprados por otra persona. Elige otros asientos.",
                            FirebaseFirestoreException.Code.FAILED_PRECONDITION);
                }
            }
            long yaTiene = valor(tx.get(r.limiteRef), "boletos");
            if (yaTiene + n > maxPorUsuario) {
                throw new FirebaseFirestoreException(
                        "Superas el máximo de " + maxPorUsuario + " boletos por usuario para esta función.",
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }
            Map<Integer, Long> vendidosPrevios = new HashMap<>();
            for (Map.Entry<Integer, DocumentReference> e : refZona.entrySet()) {
                long vendidos = valor(tx.get(e.getValue()), "vendidos");
                int pedidos = pedidosPorIdZona.get(e.getKey());
                Integer capacidad = capacidadPorZona.get(e.getKey());
                if (capacidad == null || vendidos + pedidos > capacidad) {
                    long libres = capacidad == null ? 0 : Math.max(0, capacidad - vendidos);
                    throw new FirebaseFirestoreException(
                            libres == 0 ? "La zona seleccionada está agotada para esta función."
                                    : "Solo quedan " + libres + " lugares en la zona seleccionada.",
                            FirebaseFirestoreException.Code.FAILED_PRECONDITION);
                }
                vendidosPrevios.put(e.getKey(), vendidos);
            }

            // ---- ESCRITURAS
            for (ItemCompra it : items) {
                if (it.idAsiento == null) continue;
                Map<String, Object> venta = new HashMap<>();
                venta.put("uid", uid);
                venta.put("idFuncion", idFuncion);
                venta.put("idAsiento", it.idAsiento);
                venta.put("idOrdenFs", r.ordenRef.getId());
                tx.set(fs.collection("ventas").document(idFuncion + "_" + it.idAsiento), venta);
            }
            Map<String, Object> limite = new HashMap<>();
            limite.put("uid", uid);
            limite.put("idFuncion", idFuncion);
            limite.put("boletos", yaTiene + n);
            tx.set(r.limiteRef, limite);

            for (Map.Entry<Integer, DocumentReference> e : refZona.entrySet()) {
                Map<String, Object> ocup = new HashMap<>();
                ocup.put("idFuncion", idFuncion);
                ocup.put("idSeccionZona", e.getKey());
                ocup.put("vendidos", vendidosPrevios.get(e.getKey()) + pedidosPorIdZona.get(e.getKey()));
                tx.set(e.getValue(), ocup);
            }
            tx.set(r.ordenRef, datosOrden);
            return null;
        }), ESPERA_SEG, TimeUnit.SECONDS);

        return r;
    }

    /** Anota en la orden de la nube el ID de la orden local (mejor esfuerzo, no bloquea). */
    public static void vincularOrdenLocal(Reserva r, int idOrdenLocal) {
        r.ordenRef.update("idOrdenLocal", idOrdenLocal);
    }

    /** Compensación: libera asientos y cupos si la compra local falló. Nunca lanza excepciones. */
    public static void liberar(Reserva r) {
        if (r == null) return;
        try {
            WriteBatch lote = FirebaseFirestore.getInstance().batch();
            for (DocumentReference v : r.ventas) lote.delete(v);
            lote.update(r.limiteRef, "boletos", FieldValue.increment(-r.nBoletos));
            for (Map.Entry<DocumentReference, Integer> e : r.pedidosPorZona.entrySet()) {
                lote.update(e.getKey(), "vendidos", FieldValue.increment(-e.getValue()));
            }
            lote.update(r.ordenRef, "estadoOrden", "Cancelada");
            Tasks.await(lote.commit(), ESPERA_SEG, TimeUnit.SECONDS);
        } catch (Exception ignorada) {
            // Si no hay red, queda una reserva huérfana; se limpia a mano o con una función programada.
        }
    }

    /** Mensaje legible para el usuario, o null si el error no es de reglas/red (error inesperado). */
    public static String mensajeDe(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof FirebaseFirestoreException) {
                FirebaseFirestoreException f = (FirebaseFirestoreException) t;
                switch (f.getCode()) {
                    case FAILED_PRECONDITION:
                        return f.getMessage();
                    case UNAVAILABLE:
                    case DEADLINE_EXCEEDED:
                        return "Sin conexión a internet. Para comprar necesitas estar en línea.";
                    case PERMISSION_DENIED:
                        return "No tienes permiso para realizar esta compra. Vuelve a iniciar sesión.";
                    default:
                        break;
                }
            }
            t = t.getCause();
        }
        if (e instanceof TimeoutException) {
            return "Sin conexión a internet. Para comprar necesitas estar en línea.";
        }
        return null;
    }
}
