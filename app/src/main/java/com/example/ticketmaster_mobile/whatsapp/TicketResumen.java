package com.example.ticketmaster_mobile.whatsapp;

import java.util.ArrayList;
import java.util.List;

/** Datos de una compra, extraídos de la DB, listos para formatearse como mensaje. Java puro. */
public final class TicketResumen {

    public static final class Boleto {
        public final String zona;
        /** null si no hay asiento numerado (teatro / museo). */
        public final String codigoAsiento;
        public final double precio;

        public Boleto(String zona, String codigoAsiento, double precio) {
            this.zona = zona;
            this.codigoAsiento = codigoAsiento;
            this.precio = precio;
        }
    }

    public int idOrden;
    /** chatId de WhatsApp del usuario dueño de la orden; null/vacío si no ha vinculado su número. */
    public String chatId;
    public String referencia;
    public String fechaPago;
    public String estadoPago;
    public String metodoPago;
    public String ultimosCuatro;
    public String cuentaPaypal;
    public double montoTotal;
    public String evento;
    public String lugar;
    public String tipoLugar;
    /** Ya en formato legible (p. ej. "vie 16 oct 2026, 20:00"). */
    public String fechaFuncion;
    public final List<Boleto> boletos = new ArrayList<>();
}
