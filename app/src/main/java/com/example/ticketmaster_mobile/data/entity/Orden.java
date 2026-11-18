package com.example.ticketmaster_mobile.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "Orden",
        foreignKeys = @ForeignKey(
                entity = Usuario.class,
                parentColumns = "ID_Usuario",
                childColumns = "ID_Usuario",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("ID_Usuario")}
)
public class Orden {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "ID_Orden")
    public int idOrden;

    @ColumnInfo(name = "ID_Usuario")
    public int idUsuario;

    @NonNull
    @ColumnInfo(name = "FechaCompra") // formato ISO: yyyy-MM-dd HH:mm:ss
    public String fechaCompra = "";

    @ColumnInfo(name = "MontoTotal")
    public double montoTotal;

    // "Pendiente" | "Pagada" | "Cancelada"
    @NonNull
    @ColumnInfo(name = "EstadoOrden")
    public String estadoOrden = "Pendiente";
}
