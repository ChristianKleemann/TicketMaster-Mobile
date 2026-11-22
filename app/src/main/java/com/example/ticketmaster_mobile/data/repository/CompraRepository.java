package com.example.ticketmaster_mobile.data.repository;

import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;

import com.example.ticketmaster_mobile.data.AppDatabase;
import com.example.ticketmaster_mobile.data.DatabaseExecutor;
import com.example.ticketmaster_mobile.data.FechaUtils;
import com.example.ticketmaster_mobile.data.ReglasNegocio;
import com.example.ticketmaster_mobile.data.entity.Boleto;
import com.example.ticketmaster_mobile.data.entity.DiaFestivo;
import com.example.ticketmaster_mobile.data.entity.Establecimiento;
import com.example.ticketmaster_mobile.data.entity.Funcion;
import com.example.ticketmaster_mobile.data.entity.Orden;
import com.example.ticketmaster_mobile.data.entity.Pago;
import com.example.ticketmaster_mobile.data.entity.SeccionZona;
import com.example.ticketmaster_mobile.data.entity.WhatsappOutbox;
import com.example.ticketmaster_mobile.whatsapp.WahaSyncScheduler;
import com.example.ticketmaster_mobile.whatsapp.GreenApiConfigProvider;

import com.example.ticketmaster_mobile.data.remote.ReservasFirestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import androidx.annotation.VisibleForTesting;
import java.util.ArrayList;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Lógica de negocio de "Adquirir Boletos".
 * {@link #realizarCompra} valida las reglas del establecimiento y, si todas se cumplen, crea la Orden,
 * un Boleto por cada lugar/asiento y el Pago dentro de UNA transacción de Room. Si algo falla
 * (regla de negocio, aforo agotado, asiento ya vendido) se revierte todo y no queda ningún registro
 * parcial. Reglas que se validan aquí (además de las que ya muestra la interfaz):
 *  - Hoy debe ser día laborable del establecimiento (no festivo) — ver {@link ReglasNegocio#VALIDAR_DIA_DE_COMPRA}.
 *  - La función no ha comenzado y cae en un día de servicio.
 *  - Máximo de boletos por usuario para esa función (10 teatro, 5 museo, 6 cine).
 *  - Capacidad de cada zona/sección para esa función.
 */
public class CompraRepository {

    public interface CompraCallback {
        void onExito(int idOrden);
        void onError(String mensaje);
    }

    /** Un boleto a comprar: un asiento numerado (cine) o un lugar en una zona de admisión general. */
    public static class ItemCompra {
        /** null cuando la zona no tiene asientos numerados (teatro, museo). */
        public final Integer idAsiento;
        public final int idSeccionZona;
        public final double precio;

        public ItemCompra(Integer idAsiento, int idSeccionZona, double precio) {
            this.idAsiento = idAsiento;
            this.idSeccionZona = idSeccionZona;
            this.precio = precio;
        }
    }

    /** Datos del pago capturados en pantalla. Nunca se guarda el número completo ni el CVV. */
    public static class DatosPago {
        public final int idMetodoPago;
        public final boolean esPaypal;
        public final String nombreTitular;
        /** Solo tarjeta: los últimos 4 dígitos. */
        public final String ultimosCuatroDigitos;
        /** Solo PayPal: correo de la cuenta. */
        public final String cuentaPaypal;
        /** Referencia única de la compra (TM-XXXXXXXX). Se asigna al iniciar la compra. */
        public String referencia;

        public DatosPago(int idMetodoPago, boolean esPaypal, String nombreTitular,
                         String ultimosCuatroDigitos, String cuentaPaypal) {
            this.idMetodoPago = idMetodoPago;
            this.esPaypal = esPaypal;
            this.nombreTitular = nombreTitular;
            this.ultimosCuatroDigitos = ultimosCuatroDigitos;
            this.cuentaPaypal = cuentaPaypal;
        }
    }

    /** Se lanza dentro de la transacción para revertirla con un mensaje legible para el usuario. */
    private static class ReglaIncumplidaException extends RuntimeException {
        ReglaIncumplidaException(String mensaje) {
            super(mensaje);
        }
    }

    private static class Resultado {
        final int idOrden;
        final String error;

        Resultado(int idOrden, String error) {
            this.idOrden = idOrden;
            this.error = error;
        }
    }

    private final AppDatabase db;
    private final Context appContext;

    /** Las pruebas con Room en memoria no tienen Firebase: se desactivan las reservas remotas. */
    private static volatile boolean reservasRemotasActivas = true;

    @VisibleForTesting
    public static void desactivarReservasRemotas(boolean desactivar) {
        reservasRemotasActivas = !desactivar;
    }

    public CompraRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.db = AppDatabase.getInstance(context);
    }

    public void realizarCompra(int idUsuario,
                               int idFuncion,
                               List<ItemCompra> boletos,
                               DatosPago datosPago,
                               CompraCallback callback) {

        if (boletos == null || boletos.isEmpty()) {
            callback.onError("Selecciona al menos un boleto");
            return;
        }

        DatabaseExecutor.runAsync(() -> {
            if (datosPago.referencia == null) {
                datosPago.referencia = "TM-" + UUID.randomUUID().toString()
                        .substring(0, 8).toUpperCase(Locale.ROOT);
            }
            ReservasFirestore.Reserva reserva = null;
            try {
                if (reservasRemotasActivas) {
                    FirebaseUser usuarioFb = FirebaseAuth.getInstance().getCurrentUser();
                    if (usuarioFb == null) {
                        return new Resultado(-1, "Tu sesión expiró. Vuelve a iniciar sesión.");
                    }
                    Funcion funcion = db.funcionDao().buscarPorId(idFuncion);
                    if (funcion == null) return new Resultado(-1, "La función seleccionada ya no existe.");
                    Establecimiento lugar = db.establecimientoDao().buscarPorId(funcion.idEstablecimiento);
                    if (lugar == null) return new Resultado(-1, "El establecimiento ya no existe.");

                    Map<Integer, Integer> capacidades = new HashMap<>();
                    for (ItemCompra it : boletos) {
                        if (!capacidades.containsKey(it.idSeccionZona)) {
                            SeccionZona z = db.seccionZonaDao().buscarPorId(it.idSeccionZona);
                            if (z == null) return new Resultado(-1, "La zona seleccionada ya no existe.");
                            capacidades.put(it.idSeccionZona, z.capacidad);
                        }
                    }

                    // 1) Firestore: reserva asientos y cupos (árbitro único entre teléfonos)
                    reserva = ReservasFirestore.reservar(usuarioFb.getUid(), idFuncion, boletos,
                            capacidades, lugar.maxBoletosPorUsuario,
                            armarOrdenNube(usuarioFb.getUid(), idFuncion, funcion, boletos, datosPago));
                }

                // 2) Room: las mismas reglas y registros de siempre
                int idOrden = db.runInTransaction((java.util.concurrent.Callable<Integer>) () ->
                        ejecutarCompra(idUsuario, idFuncion, boletos, datosPago));

                if (reserva != null) ReservasFirestore.vincularOrdenLocal(reserva, idOrden);
                notificarWhatsappSiAplica();
                return new Resultado(idOrden, null);
            } catch (ReglaIncumplidaException e) {
                ReservasFirestore.liberar(reserva);
                return new Resultado(-1, e.getMessage());
            } catch (SQLiteConstraintException e) {
                ReservasFirestore.liberar(reserva);
                return new Resultado(-1, "Uno o más asientos ya fueron comprados por otra persona. Elige otros asientos.");
            } catch (Exception e) {
                ReservasFirestore.liberar(reserva); // no hace nada si reserva es null
                String mensaje = ReservasFirestore.mensajeDe(e);
                return new Resultado(-1, mensaje != null ? mensaje
                        : "Ocurrió un error al procesar tu compra. Intenta de nuevo.");
            }
        }, resultado -> {
            if (resultado != null && resultado.error == null) {
                callback.onExito(resultado.idOrden);
            } else {
                callback.onError(resultado != null ? resultado.error : "Ocurrió un error al procesar tu compra.");
            }
        });
    }

    private int ejecutarCompra(int idUsuario, int idFuncion, List<ItemCompra> boletos, DatosPago datosPago) {
        Funcion funcion = db.funcionDao().buscarPorId(idFuncion);
        if (funcion == null) throw new ReglaIncumplidaException("La función seleccionada ya no existe.");
        Establecimiento lugar = db.establecimientoDao().buscarPorId(funcion.idEstablecimiento);
        if (lugar == null) throw new ReglaIncumplidaException("El establecimiento ya no existe.");

        Date ahora = new Date();
        String hoy = FechaUtils.fechaIso(ahora);

        // 1) Hoy debe ser día laborable (no festivo) para vender boletos
        if (ReglasNegocio.VALIDAR_DIA_DE_COMPRA) {
            String motivo = ReglasNegocio.motivoDiaNoLaboral(lugar.diasLaborales, hoy,
                    descripcionFestivo(lugar.idEstablecimiento, hoy), lugar.nombre);
            if (motivo != null) {
                throw new ReglaIncumplidaException("Hoy no se pueden adquirir boletos. " + motivo);
            }
        }

        // 2) La función no debe haber comenzado y debe caer en día de servicio
        if (funcion.fechaHora.compareTo(FechaUtils.fechaHoraIso(ahora)) < 0) {
            throw new ReglaIncumplidaException("Esta función ya comenzó o ya pasó.");
        }
        String diaFuncion = funcion.fechaHora.substring(0, 10);
        String motivoFuncion = ReglasNegocio.motivoDiaNoLaboral(lugar.diasLaborales, diaFuncion,
                descripcionFestivo(lugar.idEstablecimiento, diaFuncion), lugar.nombre);
        if (motivoFuncion != null) throw new ReglaIncumplidaException(motivoFuncion);

        // 3) Máximo de boletos por usuario para esta función
        int yaTiene = db.boletoDao().contarActivosDeUsuarioEnFuncion(idUsuario, idFuncion);
        if (yaTiene + boletos.size() > lugar.maxBoletosPorUsuario) {
            throw new ReglaIncumplidaException(
                    ReglasNegocio.mensajeLimiteBoletos(lugar.maxBoletosPorUsuario, yaTiene, boletos.size()));
        }

        // 4) Capacidad de cada zona (se cuenta dentro de la transacción: sin sobreventa)
        Map<Integer, Integer> solicitadosPorZona = new HashMap<>();
        for (ItemCompra item : boletos) {
            Integer previo = solicitadosPorZona.get(item.idSeccionZona);
            solicitadosPorZona.put(item.idSeccionZona, previo == null ? 1 : previo + 1);
        }
        for (Map.Entry<Integer, Integer> entrada : solicitadosPorZona.entrySet()) {
            SeccionZona zona = db.seccionZonaDao().buscarPorId(entrada.getKey());
            if (zona == null || zona.idEstablecimiento != lugar.idEstablecimiento) {
                throw new ReglaIncumplidaException("La zona seleccionada no pertenece a este establecimiento.");
            }
            int vendidos = db.boletoDao().contarActivosEnZona(idFuncion, zona.idSeccionZona);
            int disponibles = zona.capacidad - vendidos;
            if (entrada.getValue() > disponibles) {
                throw new ReglaIncumplidaException(disponibles <= 0
                        ? "La zona \"" + zona.nombreZona + "\" está agotada para esta función."
                        : "Solo quedan " + disponibles + " lugares en \"" + zona.nombreZona + "\".");
            }
        }

        double montoTotal = 0.0;
        for (ItemCompra item : boletos) montoTotal += item.precio;

        String fechaHoraActual = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(ahora);

        // 5) Orden
        Orden orden = new Orden();
        orden.idUsuario = idUsuario;
        orden.fechaCompra = fechaHoraActual;
        orden.montoTotal = montoTotal;
        orden.estadoOrden = "Pagada";
        int idOrden = (int) db.ordenDao().insertar(orden);

        // 6) Un Boleto por lugar. UNIQUE(ID_Funcion, ID_Asiento) evita vender dos veces un asiento.
        for (ItemCompra item : boletos) {
            Boleto boleto = new Boleto();
            boleto.idOrden = idOrden;
            boleto.idFuncion = idFuncion;
            boleto.idAsiento = item.idAsiento;
            boleto.idSeccionZona = item.idSeccionZona;
            boleto.precioFinal = item.precio;
            boleto.estadoBoleto = "Emitido";
            db.boletoDao().insertar(boleto);
        }

        // 7) Pago (solo últimos 4 dígitos / correo PayPal; jamás número completo ni CVV)
        Pago pago = new Pago();
        pago.idOrden = idOrden;
        pago.idMetodoPago = datosPago.idMetodoPago;
        pago.monto = montoTotal;
        pago.fechaPago = fechaHoraActual;
        pago.estadoPago = "Aprobado";
        pago.referenciaExterna = datosPago.referencia != null
                ? datosPago.referencia
                : "TM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        pago.nombreTitular = datosPago.nombreTitular;
        pago.ultimosCuatroDigitos = datosPago.esPaypal ? null : datosPago.ultimosCuatroDigitos;
        pago.cuentaPaypal = datosPago.esPaypal ? datosPago.cuentaPaypal : null;
        db.pagoDao().insertar(pago);

        // 8) Notificación de WhatsApp (outbox) en la MISMA transacción: o se guardan la compra y su
        //    notificación pendiente, o no se guarda nada. El envío real ocurre después, en segundo
        //    plano, leyendo los datos de pago de la DB (ver paquete waha).
        if (GreenApiConfigProvider.actual().servidorConfigurado()) {
            WhatsappOutbox cola = new WhatsappOutbox();
            cola.idOrden = idOrden;
            cola.estado = WhatsappOutbox.PENDIENTE;
            cola.proximoIntento = System.currentTimeMillis();
            cola.creadoEn = cola.proximoIntento;
            db.whatsappOutboxDao().insertar(cola);
        }

        return idOrden;
    }

    /** Orden tal como se guarda en la nube. Jamás incluye número completo de tarjeta ni CVV. */
    private Map<String, Object> armarOrdenNube(String uid, int idFuncion, Funcion funcion,
                                               List<ItemCompra> boletos, DatosPago datosPago) {
        double monto = 0.0;
        List<Map<String, Object>> items = new ArrayList<>();
        for (ItemCompra it : boletos) {
            monto += it.precio;
            Map<String, Object> m = new HashMap<>();
            m.put("idAsiento", it.idAsiento);
            m.put("idSeccionZona", it.idSeccionZona);
            m.put("precio", it.precio);
            items.add(m);
        }
        Map<String, Object> pago = new HashMap<>();
        pago.put("idMetodoPago", datosPago.idMetodoPago);
        pago.put("monto", monto);
        pago.put("referenciaExterna", datosPago.referencia);
        pago.put("nombreTitular", datosPago.nombreTitular);
        pago.put("ultimosCuatroDigitos", datosPago.esPaypal ? null : datosPago.ultimosCuatroDigitos);
        pago.put("cuentaPaypal", datosPago.esPaypal ? datosPago.cuentaPaypal : null);

        Map<String, Object> orden = new HashMap<>();
        orden.put("uid", uid);
        orden.put("idFuncion", idFuncion);
        orden.put("idEstablecimiento", funcion.idEstablecimiento);
        orden.put("nombreEvento", funcion.nombreEvento);
        orden.put("fechaCompra", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date()));
        orden.put("montoTotal", monto);
        orden.put("estadoOrden", "Pagada");
        orden.put("boletos", items);
        orden.put("pago", pago);
        return orden;
    }

    private void notificarWhatsappSiAplica() {
        try {
            if (GreenApiConfigProvider.actual().servidorConfigurado()) {
                WahaSyncScheduler.programarAhora(appContext);
            }
        } catch (RuntimeException ignorada) {
            // Nunca debe afectar al resultado de una compra ya confirmada
        }
    }

    private String descripcionFestivo(int idEstablecimiento, String fechaIso) {
        DiaFestivo festivo = db.diaFestivoDao().buscarPorFecha(idEstablecimiento, fechaIso);
        return festivo != null ? festivo.descripcion : null;
    }
}
