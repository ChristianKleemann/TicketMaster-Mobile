package com.example.ticketmaster_mobile.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "Establecimiento")
public class Establecimiento {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "ID_Establecimiento")
    public int idEstablecimiento;

    @NonNull
    @ColumnInfo(name = "Nombre")
    public String nombre = "";

    @NonNull
    @ColumnInfo(name = "Ubicacion")
    public String ubicacion = "";

    // Valores admitidos: "Cine", "Teatro", "Museo"
    @NonNull
    @ColumnInfo(name = "TipoEstablecimiento")
    public String tipoEstablecimiento = "";

    // Capacidad máxima del lugar (suma de la capacidad de sus zonas/secciones)
    @ColumnInfo(name = "Capacidad")
    public int capacidad;

    // Horario de servicio, formato HH:mm
    @NonNull
    @ColumnInfo(name = "HoraApertura")
    public String horaApertura = "00:00";

    @NonNull
    @ColumnInfo(name = "HoraCierre")
    public String horaCierre = "23:59";

    // Días laborales separados por coma, ISO: 1=lunes ... 7=domingo (ej. "2,3,4,5,6,7")
    @NonNull
    @ColumnInfo(name = "DiasLaborales")
    public String diasLaborales = "1,2,3,4,5,6,7";

    // Máximo de boletos que un usuario puede tener por función
    @ColumnInfo(name = "MaxBoletosPorUsuario")
    public int maxBoletosPorUsuario = 6;

    // Código de vestimenta (solo teatros; vacío si no aplica)
    @NonNull
    @ColumnInfo(name = "Vestimenta")
    public String vestimenta = "";

    // Restricciones de acceso (mascotas, armas, alimentos, cámaras, etc.)
    @NonNull
    @ColumnInfo(name = "Restricciones")
    public String restricciones = "";
}
