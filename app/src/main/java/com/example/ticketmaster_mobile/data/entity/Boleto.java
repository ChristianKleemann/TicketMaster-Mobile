package com.example.ticketmaster_mobile.data.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "Boleto",
        foreignKeys = {
                @ForeignKey(entity = Orden.class, parentColumns = "ID_Orden",
                        childColumns = "ID_Orden", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Funcion.class, parentColumns = "ID_Funcion",
                        childColumns = "ID_Funcion", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Asiento.class, parentColumns = "ID_Asiento",
                        childColumns = "ID_Asiento", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = SeccionZona.class, parentColumns = "ID_SeccionZona",
                        childColumns = "ID_SeccionZona", onDelete = ForeignKey.CASCADE)
        },
        indices = {
                @Index("ID_Orden"),
                @Index("ID_Funcion"),
                @Index("ID_Asiento"),
                @Index("ID_SeccionZona"),
                // Evita vender dos veces el mismo asiento en la misma función (overbooking).
                // Los boletos sin asiento (ID_Asiento NULL: teatro y museo) no se ven afectados:
                // SQLite permite varios NULL en un índice UNIQUE.
                @Index(value = {"ID_Funcion", "ID_Asiento"}, unique = true)
        }
)
public class Boleto {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "ID_Boleto")
    public int idBoleto;

    @ColumnInfo(name = "ID_Orden")
    public int idOrden;

    @ColumnInfo(name = "ID_Funcion")
    public int idFuncion;

    // NULL cuando la zona es de admisión general (teatro por sección, museo)
    @Nullable
    @ColumnInfo(name = "ID_Asiento")
    public Integer idAsiento;

    // Zona/sección/servicio al que pertenece el boleto (permite controlar el aforo)
    @ColumnInfo(name = "ID_SeccionZona")
    public int idSeccionZona;

    @ColumnInfo(name = "PrecioFinal")
    public double precioFinal;

    // "Reservado" | "Emitido" | "Cancelado" | "Usado"
    @NonNull
    @ColumnInfo(name = "EstadoBoleto")
    public String estadoBoleto = "Emitido";
}
