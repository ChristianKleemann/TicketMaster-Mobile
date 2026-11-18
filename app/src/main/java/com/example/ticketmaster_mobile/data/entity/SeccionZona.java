package com.example.ticketmaster_mobile.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "Seccion_Zona",
        foreignKeys = @ForeignKey(
                entity = Establecimiento.class,
                parentColumns = "ID_Establecimiento",
                childColumns = "ID_Establecimiento",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("ID_Establecimiento")}
)
public class SeccionZona {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "ID_SeccionZona")
    public int idSeccionZona;

    @ColumnInfo(name = "ID_Establecimiento")
    public int idEstablecimiento;

    @NonNull
    @ColumnInfo(name = "NombreZona")
    public String nombreZona = "";

    // "Numerado" | "General"
    @NonNull
    @ColumnInfo(name = "TipoZona")
    public String tipoZona = "Numerado";

    @ColumnInfo(name = "PrecioBase")
    public double precioBase;

    // Boletos que se pueden vender por función en esta zona (asientos o aforo)
    @ColumnInfo(name = "Capacidad")
    public int capacidad;

    @NonNull
    @ColumnInfo(name = "Descripcion")
    public String descripcion = "";
}
