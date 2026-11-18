package com.example.ticketmaster_mobile.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "MetodoPago")
public class MetodoPago {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "ID_MetodoPago")
    public int idMetodoPago;

    @NonNull
    @ColumnInfo(name = "Nombre")
    public String nombre = "";
}
