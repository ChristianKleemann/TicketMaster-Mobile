package com.example.ticketmaster_mobile.data.pojo;

import androidx.annotation.Nullable;

/** Fila Orden + Pago + MetodoPago usada para armar el mensaje de WhatsApp. */
public class PagoTicketRow {
    public int idOrden;
    public double montoTotal;
    public String estadoOrden;
    public String referencia;
    public String fechaPago;
    public String estadoPago;
    public String metodoPago;
    @Nullable
    public String ultimosCuatro;
    @Nullable
    public String cuentaPaypal;
}
