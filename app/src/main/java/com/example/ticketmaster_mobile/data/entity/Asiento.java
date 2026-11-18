package com.example.ticketmaster_mobile.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "Asiento",
        foreignKeys = @ForeignKey(
                entity = SeccionZona.class,
                parentColumns = "ID_SeccionZona",
                childColumns = "ID_SeccionZona",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("ID_SeccionZona")}
)
public class Asiento {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "ID_Asiento")
    public int idAsiento;

    @ColumnInfo(name = "ID_SeccionZona")
    public int idSeccionZona;

    @ColumnInfo(name = "Fila")
    public String fila;

    @ColumnInfo(name = "Numero")
    public String numero;

    @NonNull
    @ColumnInfo(name = "CodigoAsiento")
    public String codigoAsiento = "";
}
