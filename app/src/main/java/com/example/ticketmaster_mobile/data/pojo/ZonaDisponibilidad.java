package com.example.ticketmaster_mobile.data.pojo;

/** Zona/sección de un establecimiento con su disponibilidad para una función concreta. */
public class ZonaDisponibilidad {
    public int idSeccionZona;
    public String nombreZona;
    public String tipoZona;
    public double precioBase;
    public int capacidad;
    public String descripcion;
    public int vendidos;

    public int disponibles() {
        return Math.max(0, capacidad - vendidos);
    }
}
