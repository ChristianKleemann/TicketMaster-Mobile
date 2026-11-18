package com.example.ticketmaster_mobile.data.pojo;

import androidx.room.Ignore;

/**
 * Resultado de la consulta que combina Asiento + Seccion_Zona + Boleto
 * para saber, dentro de una Función específica, si un asiento está ocupado.
 * No es una @Entity: es un POJO de solo lectura para pintar la cuadrícula de asientos.
 */
public class AsientoEstado {
    public int idAsiento;
    public int idSeccionZona;
    public String fila;
    public String numero;
    public String codigoAsiento;
    public String nombreZona;
    public double precioBase;

    // 1 si ya existe un Boleto activo (Reservado/Emitido/Usado) para ese asiento en la función; 0 si está libre
    public int ocupado;

    // Campo que NO viene de la consulta SQL: true mientras el usuario lo tiene seleccionado en pantalla.
    // @Ignore le indica a Room que no intente mapearlo a ninguna columna.
    @Ignore
    public boolean seleccionado = false;
}
