package com.example.ticketmaster_mobile.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "Funcion",
        foreignKeys = @ForeignKey(
                entity = Establecimiento.class,
                parentColumns = "ID_Establecimiento",
                childColumns = "ID_Establecimiento",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("ID_Establecimiento")}
)
public class Funcion {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "ID_Funcion")
    public int idFuncion;

    @ColumnInfo(name = "ID_Establecimiento")
    public int idEstablecimiento;

    @NonNull
    @ColumnInfo(name = "NombreEvento")
    public String nombreEvento = "";

    @NonNull
    @ColumnInfo(name = "FechaHora") // formato ISO: yyyy-MM-dd HH:mm
    public String fechaHora = "";

    // Clasificación (cine: A, B, B15, C...). Vacío si no aplica.
    @NonNull
    @ColumnInfo(name = "Clasificacion")
    public String clasificacion = "";

    // Detalle libre: género y duración, compositor, tipo de visita, etc.
    @NonNull
    @ColumnInfo(name = "Detalle")
    public String detalle = "";
}
