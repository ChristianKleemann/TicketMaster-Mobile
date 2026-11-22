package com.example.ticketmaster_mobile.whatsapp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Construye el texto de WhatsApp de una compra. Determinista y sin dependencias de Android. */
public final class MensajeTicketFormatter {

    private MensajeTicketFormatter() {
    }

    public static String formatear(TicketResumen t) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎟️ *TicketMaster · Compra confirmada*\n");
        sb.append("Orden #").append(t.idOrden);
        if (notBlank(t.referencia)) sb.append(" · Ref. ").append(limpiar(t.referencia));
        sb.append("\n\n");

        sb.append("*").append(limpiar(t.evento)).append("*\n");
        sb.append(etiquetaLugar(t.tipoLugar)).append(limpiar(t.lugar)).append("\n");
        sb.append("🗓️ ").append(limpiar(t.fechaFuncion)).append("\n\n");

        sb.append("Boletos (").append(t.boletos.size()).append("):\n");
        sb.append(detalleBoletos(t.boletos)).append("\n");

        sb.append("💰 *Total: ").append(dinero(BigDecimal.valueOf(t.montoTotal))).append(" MXN*\n");
        sb.append("💳 ").append(metodo(t)).append("\n");
        sb.append("Pago ").append(notBlank(t.estadoPago) ? t.estadoPago.toLowerCase(Locale.ROOT) : "registrado");
        if (notBlank(t.fechaPago)) sb.append(" el ").append(limpiar(t.fechaPago));
        sb.append("\n\nConserva este mensaje como comprobante.");
        return sb.toString();
    }

    private static String detalleBoletos(List<TicketResumen.Boleto> boletos) {
        Map<String, List<TicketResumen.Boleto>> porZona = new LinkedHashMap<>();
        for (TicketResumen.Boleto b : boletos) {
            List<TicketResumen.Boleto> l = porZona.get(b.zona);
            if (l == null) {
                l = new java.util.ArrayList<>();
                porZona.put(b.zona, l);
            }
            l.add(b);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<TicketResumen.Boleto>> e : porZona.entrySet()) {
            BigDecimal subtotal = BigDecimal.ZERO;
            StringBuilder asientos = new StringBuilder();
            for (TicketResumen.Boleto b : e.getValue()) {
                subtotal = subtotal.add(BigDecimal.valueOf(b.precio));
                if (b.codigoAsiento != null) {
                    if (asientos.length() > 0) asientos.append(", ");
                    asientos.append(limpiar(b.codigoAsiento));
                }
            }
            sb.append("• ").append(limpiar(e.getKey()));
            if (asientos.length() > 0) sb.append(" — asientos ").append(asientos);
            else sb.append(" ×").append(e.getValue().size());
            sb.append(" — ").append(dinero(subtotal)).append("\n");
        }
        return sb.toString();
    }

    private static String metodo(TicketResumen t) {
        StringBuilder sb = new StringBuilder(notBlank(t.metodoPago) ? limpiar(t.metodoPago) : "Pago");
        if (notBlank(t.ultimosCuatro)) sb.append(" •••• ").append(limpiar(t.ultimosCuatro));
        if (notBlank(t.cuentaPaypal)) sb.append(" (").append(enmascararCorreo(t.cuentaPaypal)).append(")");
        return sb.toString();
    }

    /** "juan.perez@gmail.com" -> "ju***@gmail.com": el chat no debe exponer el correo completo. */
    static String enmascararCorreo(String correo) {
        String c = correo.trim();
        int at = c.indexOf('@');
        if (at <= 0) return "***";
        String local = c.substring(0, at);
        return local.substring(0, Math.min(2, local.length())) + "***" + c.substring(at);
    }

    private static String etiquetaLugar(String tipo) {
        if ("Cine".equals(tipo)) return "🎬 ";
        if ("Teatro".equals(tipo)) return "🎭 ";
        if ("Museo".equals(tipo)) return "🏛️ ";
        return "📍 ";
    }

    static String dinero(BigDecimal v) {
        return String.format(Locale.US, "$%,.2f", v.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Quita los caracteres que WhatsApp interpreta como formato (* _ ~ `) para que un nombre de
     * evento no rompa el mensaje, y los saltos de línea para que no se pueda inyectar estructura.
     */
    static String limpiar(String s) {
        if (s == null) return "";
        return s.replaceAll("[*_~`]", "").replaceAll("[\\r\\n]+", " ").trim();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
