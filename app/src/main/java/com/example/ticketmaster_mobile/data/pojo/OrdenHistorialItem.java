package com.example.ticketmaster_mobile.data.pojo;

import com.example.ticketmaster_mobile.data.entity.Orden;

import java.util.List;

/** Agrupa una Orden con el detalle de sus Boletos, listo para pintarse en el historial de Perfil. */
public class OrdenHistorialItem {
    public Orden orden;
    public List<BoletoDetalle> boletos;

    public OrdenHistorialItem(Orden orden, List<BoletoDetalle> boletos) {
        this.orden = orden;
        this.boletos = boletos;
    }
}
