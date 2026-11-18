package com.example.ticketmaster_mobile.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Día festivo (sin servicio ni venta de boletos) de un establecimiento. */
@Entity(
        tableName = "Dia_Festivo",
        foreignKeys = @ForeignKey(
                entity = Establecimiento.class,
                parentColumns = "ID_Establecimiento",
                childColumns = "ID_Establecimiento",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index("ID_Establecimiento"),
                @Index(value = {"ID_Establecimiento", "Fecha"}, unique = true)
        }
)
public class DiaFestivo {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "ID_DiaFestivo")
    public int idDiaFestivo;

    @ColumnInfo(name = "ID_Establecimiento")
    public int idEstablecimiento;

    @NonNull
    @ColumnInfo(name = "Fecha") // formato ISO: yyyy-MM-dd
    public String fecha = "";

    @NonNull
    @ColumnInfo(name = "Descripcion")
    public String descripcion = "";
}
