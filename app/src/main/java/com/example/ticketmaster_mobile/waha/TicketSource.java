package com.example.ticketmaster_mobile.waha;

/** Origen de los datos del ticket (la DB de la app). */
public interface TicketSource {
    /** Datos de la compra o null si la orden ya no existe o no tiene un pago aprobado. */
    TicketResumen cargar(int idOrden);
}
