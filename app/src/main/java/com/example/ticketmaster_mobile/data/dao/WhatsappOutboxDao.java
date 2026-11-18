package com.example.ticketmaster_mobile.data.dao;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.lifecycle.LiveData;

import com.example.ticketmaster_mobile.data.entity.WhatsappOutbox;
import com.example.ticketmaster_mobile.data.pojo.PagoTicketRow;

@Dao
public abstract class WhatsappOutboxDao {

    /** IGNORE: una orden solo se encola una vez (índice UNIQUE en ID_Orden). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public abstract long insertar(WhatsappOutbox entrada);

    @Query("SELECT * FROM WhatsappOutbox WHERE ID_Outbox = :id")
    public abstract WhatsappOutbox buscar(long id);

    @Query("SELECT * FROM WhatsappOutbox WHERE ID_Orden = :idOrden")
    public abstract WhatsappOutbox buscarPorOrden(int idOrden);

    @Query("SELECT * FROM WhatsappOutbox " +
            "WHERE (Estado = 'PENDIENTE' AND ProximoIntento <= :ahora) " +
            "   OR (Estado = 'ENVIANDO' AND LeaseHasta < :ahora) " +
            "ORDER BY ID_Outbox LIMIT 1")
    protected abstract WhatsappOutbox siguienteVencida(long ahora);

    /** UPDATE condicionado: si otro hilo la reclamó antes, devuelve 0 filas afectadas. */
    @Query("UPDATE WhatsappOutbox SET Estado = 'ENVIANDO', Intentos = Intentos + 1, LeaseHasta = :lease " +
            "WHERE ID_Outbox = :id " +
            "AND ((Estado = 'PENDIENTE' AND ProximoIntento <= :ahora) " +
            "  OR (Estado = 'ENVIANDO' AND LeaseHasta < :ahora))")
    protected abstract int reclamar(long id, long ahora, long lease);

    /** Reclama atómicamente la siguiente entrada vencida; null si no hay. */
    @Transaction
    @Nullable
    public WhatsappOutbox reclamarSiguiente(long ahora, long leaseMs) {
        WhatsappOutbox e = siguienteVencida(ahora);
        if (e == null) return null;
        if (reclamar(e.idOutbox, ahora, ahora + leaseMs) != 1) return null;
        return buscar(e.idOutbox);
    }

    @Query("UPDATE WhatsappOutbox SET Estado = 'ENVIADO', MensajeIdWaha = :mensajeId, EnviadoEn = :ahora, " +
            "UltimoError = NULL, LeaseHasta = 0 WHERE ID_Outbox = :id AND Estado = 'ENVIANDO'")
    public abstract int marcarEnviado(long id, String mensajeId, long ahora);

    @Query("UPDATE WhatsappOutbox SET Estado = 'PENDIENTE', ProximoIntento = :proximo, UltimoError = :error, " +
            "Intentos = :intentos, LeaseHasta = 0 WHERE ID_Outbox = :id AND Estado = 'ENVIANDO'")
    public abstract int reprogramar(long id, long proximo, String error, int intentos);

    @Query("UPDATE WhatsappOutbox SET Estado = 'FALLIDO', UltimoError = :error, LeaseHasta = 0 " +
            "WHERE ID_Outbox = :id AND Estado = 'ENVIANDO'")
    public abstract int marcarFallido(long id, String error);

    @Query("SELECT MIN(ProximoIntento) FROM WhatsappOutbox WHERE Estado = 'PENDIENTE'")
    public abstract Long minProximoPendiente();

    @Query("SELECT MIN(LeaseHasta) FROM WhatsappOutbox WHERE Estado = 'ENVIANDO'")
    public abstract Long minLeaseEnCurso();

    @Query("SELECT COUNT(*) FROM WhatsappOutbox WHERE Estado IN ('PENDIENTE','ENVIANDO')")
    public abstract int contarPendientes();

    /** Reabre las notificaciones FALLIDO (p. ej. tras corregir la configuración de WAHA). */
    @Query("UPDATE WhatsappOutbox SET Estado = 'PENDIENTE', Intentos = 0, ProximoIntento = :ahora, " +
            "LeaseHasta = 0 WHERE Estado = 'FALLIDO'")
    public abstract int reabrirFallidas(long ahora);

    /** Limpieza: las ya enviadas no hacen falta pasado un tiempo. */
    @Query("DELETE FROM WhatsappOutbox WHERE Estado = 'ENVIADO' AND EnviadoEn < :limite")
    public abstract int purgarEnviadasAntesDe(long limite);

    /** Datos de pago de la orden (solo si el pago está aprobado), de donde sale el mensaje. */
    @Query("SELECT o.ID_Orden AS idOrden, o.MontoTotal AS montoTotal, o.EstadoOrden AS estadoOrden, " +
            "       p.ReferenciaExterna AS referencia, p.FechaPago AS fechaPago, p.EstadoPago AS estadoPago, " +
            "       mp.Nombre AS metodoPago, p.UltimosCuatroDigitos AS ultimosCuatro, " +
            "       p.CuentaPaypal AS cuentaPaypal " +
            "FROM Orden o " +
            "INNER JOIN Pago p ON p.ID_Orden = o.ID_Orden " +
            "INNER JOIN MetodoPago mp ON mp.ID_MetodoPago = p.ID_MetodoPago " +
            "WHERE o.ID_Orden = :idOrden AND p.EstadoPago = 'Aprobado' " +
            "ORDER BY p.ID_Pago LIMIT 1")
    public abstract PagoTicketRow cargarPagoDeOrden(int idOrden);

    /** Estado de la notificación de una orden, en vivo (null si la orden no tiene entrada). */
    @Query("SELECT * FROM WhatsappOutbox WHERE ID_Orden = :idOrden")
    public abstract LiveData<WhatsappOutbox> observarPorOrden(int idOrden);

    /** Reintenta ya las notificaciones PENDIENTE/FALLIDO de una orden (botón "Reintentar"). */
    @Query("UPDATE WhatsappOutbox SET Estado = 'PENDIENTE', Intentos = 0, ProximoIntento = :ahora, " +
            "LeaseHasta = 0 WHERE ID_Orden = :idOrden AND Estado IN ('PENDIENTE','FALLIDO')")
    public abstract int reabrirOrden(int idOrden, long ahora);

    /** Reintenta ya las de un usuario (al vincular o cambiar su número de WhatsApp). */
    @Query("UPDATE WhatsappOutbox SET Estado = 'PENDIENTE', Intentos = 0, ProximoIntento = :ahora, " +
            "LeaseHasta = 0 WHERE Estado IN ('PENDIENTE','FALLIDO') " +
            "AND ID_Orden IN (SELECT ID_Orden FROM Orden WHERE ID_Usuario = :idUsuario)")
    public abstract int reabrirDeUsuario(int idUsuario, long ahora);
}
