package com.example.ticketmaster_mobile.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "Pago",
        foreignKeys = {
                @ForeignKey(entity = Orden.class, parentColumns = "ID_Orden",
                        childColumns = "ID_Orden", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = MetodoPago.class, parentColumns = "ID_MetodoPago",
                        childColumns = "ID_MetodoPago")
        },
        indices = {
                @Index("ID_Orden"),
                @Index("ID_MetodoPago")
        }
)
public class Pago {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "ID_Pago")
    public int idPago;

    @ColumnInfo(name = "ID_Orden")
    public int idOrden;

    @ColumnInfo(name = "ID_MetodoPago")
    public int idMetodoPago;

    @ColumnInfo(name = "Monto")
    public double monto;

    @NonNull
    @ColumnInfo(name = "FechaPago")
    public String fechaPago = "";

    // "Aprobado" | "Rechazado" | "Procesando"
    @NonNull
    @ColumnInfo(name = "EstadoPago")
    public String estadoPago = "";

    @ColumnInfo(name = "ReferenciaExterna")
    public String referenciaExterna;

    @ColumnInfo(name = "UltimosCuatroDigitos")
    public String ultimosCuatroDigitos;

    // Nombre del titular de la tarjeta / cuenta PayPal (solo texto)
    @ColumnInfo(name = "NombreTitular")
    public String nombreTitular;

    // Correo de la cuenta PayPal (null si el pago fue con tarjeta)
    @ColumnInfo(name = "CuentaPaypal")
    public String cuentaPaypal;
}
