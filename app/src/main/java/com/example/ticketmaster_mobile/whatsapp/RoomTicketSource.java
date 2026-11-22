package com.example.ticketmaster_mobile.whatsapp;

import com.example.ticketmaster_mobile.data.AppDatabase;
import com.example.ticketmaster_mobile.data.FechaUtils;
import com.example.ticketmaster_mobile.data.pojo.BoletoDetalle;
import com.example.ticketmaster_mobile.data.pojo.PagoTicketRow;
import com.example.ticketmaster_mobile.data.entity.Orden;
import com.example.ticketmaster_mobile.data.entity.Usuario;

import java.util.List;


/** Lee de la DB (Orden + Pago + Boletos) los datos que se envían a WhatsApp. */
public class RoomTicketSource implements TicketSource {

    private final AppDatabase db;

    public RoomTicketSource(AppDatabase db) {
        this.db = db;
    }

    @Override
    public TicketResumen cargar(int idOrden) {
        PagoTicketRow pago = db.whatsappOutboxDao().cargarPagoDeOrden(idOrden);
        if (pago == null) return null;
        List<BoletoDetalle> detalle = db.boletoDao().listarDetallePorOrden(idOrden);
        if (detalle == null || detalle.isEmpty()) return null;

        BoletoDetalle primero = detalle.get(0);
        TicketResumen t = new TicketResumen();
        Orden orden = db.ordenDao().buscarPorId(idOrden);
        Usuario usuario = orden == null ? null : db.usuarioDao().buscarPorId(orden.idUsuario);
        t.chatId = usuario == null ? null : usuario.whatsappChatId;
        t.idOrden = pago.idOrden;
        t.referencia = pago.referencia;
        t.fechaPago = pago.fechaPago;
        t.estadoPago = pago.estadoPago;
        t.metodoPago = pago.metodoPago;
        t.ultimosCuatro = pago.ultimosCuatro;
        t.cuentaPaypal = pago.cuentaPaypal;
        t.montoTotal = pago.montoTotal;
        t.evento = primero.nombreEvento;
        t.lugar = primero.nombreEstablecimiento;
        t.tipoLugar = primero.tipoEstablecimiento;
        t.fechaFuncion = FechaUtils.fechaHoraLegible(primero.fechaHora);
        for (BoletoDetalle b : detalle) {
            t.boletos.add(new TicketResumen.Boleto(b.nombreZona, b.codigoAsiento, b.precioFinal));
        }
        return t;
    }
}
