package com.example.ticketmaster_mobile.data.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Cola transaccional ("outbox") de notificaciones de WhatsApp por compra. La fila se inserta en la
 * MISMA transacción que Orden + Boletos + Pago, de modo que jamás existe una compra sin su
 * notificación pendiente (ni una notificación sin compra). UNIQUE(ID_Orden) garantiza una sola
 * notificación por orden aunque algo intente encolarla dos veces.
 *
 * No guarda el texto del mensaje: se reconstruye desde la DB al enviar, así siempre refleja los
 * datos reales de pago. No contiene secretos.
 */
@Entity(
        tableName = "WhatsappOutbox",
        foreignKeys = @ForeignKey(entity = Orden.class, parentColumns = "ID_Orden",
                childColumns = "ID_Orden", onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(value = "ID_Orden", unique = true),
                @Index(value = {"Estado", "ProximoIntento"})
        }
)
public class WhatsappOutbox {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String ENVIANDO = "ENVIANDO";
    public static final String ENVIADO = "ENVIADO";
    public static final String FALLIDO = "FALLIDO";

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "ID_Outbox")
    public long idOutbox;

    @ColumnInfo(name = "ID_Orden")
    public int idOrden;

    // PENDIENTE | ENVIANDO | ENVIADO | FALLIDO
    @NonNull
    @ColumnInfo(name = "Estado")
    public String estado = PENDIENTE;

    @ColumnInfo(name = "Intentos")
    public int intentos;

    // epoch ms a partir del cual se puede (re)intentar
    @ColumnInfo(name = "ProximoIntento")
    public long proximoIntento;

    // epoch ms hasta el que una entrada ENVIANDO se considera en curso (recuperación tras crash)
    @ColumnInfo(name = "LeaseHasta")
    public long leaseHasta;

    @Nullable
    @ColumnInfo(name = "UltimoError")
    public String ultimoError;

    @Nullable
    @ColumnInfo(name = "MensajeIdWaha")
    public String mensajeIdWaha;

    @ColumnInfo(name = "CreadoEn")
    public long creadoEn;

    @Nullable
    @ColumnInfo(name = "EnviadoEn")
    public Long enviadoEn;
}
