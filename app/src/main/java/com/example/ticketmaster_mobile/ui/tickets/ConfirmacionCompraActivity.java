package com.example.ticketmaster_mobile.ui.tickets;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.snackbar.Snackbar;

import com.example.ticketmaster_mobile.MainActivity;
import com.example.ticketmaster_mobile.R;
import com.example.ticketmaster_mobile.data.AppDatabase;
import com.example.ticketmaster_mobile.data.ClasificacionCine;
import com.example.ticketmaster_mobile.data.DatabaseExecutor;
import com.example.ticketmaster_mobile.data.FechaUtils;
import com.example.ticketmaster_mobile.data.entity.MetodoPago;
import com.example.ticketmaster_mobile.data.entity.Orden;
import com.example.ticketmaster_mobile.data.entity.Pago;
import com.example.ticketmaster_mobile.data.pojo.BoletoDetalle;
import com.example.ticketmaster_mobile.VincularWhatsappDialog;
import com.example.ticketmaster_mobile.data.entity.Usuario;
import com.example.ticketmaster_mobile.data.entity.WhatsappOutbox;
import com.example.ticketmaster_mobile.waha.MensajesWhatsapp;
import com.example.ticketmaster_mobile.waha.WahaSyncScheduler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Comprobante de compra: monto pagado, cantidad de boletos, asientos/servicio o sección, y la
 * información de acceso según el tipo de lugar (vestimenta, clasificación y restricciones,
 * horario de entrada).
 */
public class ConfirmacionCompraActivity extends AppCompatActivity {

    public static final String EXTRA_ID_ORDEN = "EXTRA_ID_ORDEN";

    private static class Datos {
        Orden orden;
        Pago pago;
        MetodoPago metodo;
        List<BoletoDetalle> boletos;
    }
    private int idOrden = -1;

    /** Último estado visto del envío; permite avisar solo cuando CAMBIA a ENVIADO mientras se mira la pantalla. */
    private String estadoWhatsappAnterior;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirmacion);

        Button btnPerfil = findViewById(R.id.btnConfPerfil);
        btnPerfil.setOnClickListener(v -> irAlInicio());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                irAlInicio();
            }
        });

        idOrden = getIntent().getIntExtra(EXTRA_ID_ORDEN, -1);
        AppDatabase db = AppDatabase.getInstance(this);
        observarEnvioWhatsapp(db);
        DatabaseExecutor.runAsync(() -> {
            Datos d = new Datos();
            d.orden = db.ordenDao().buscarPorId(idOrden);
            d.pago = db.pagoDao().buscarPorOrden(idOrden);
            if (d.pago != null) d.metodo = db.metodoPagoDao().buscarPorId(d.pago.idMetodoPago);
            d.boletos = db.boletoDao().listarDetallePorOrden(idOrden);
            return d;
        }, this::pintar);
    }

    /** Muestra en vivo si el ticket ya salió por WhatsApp (la fila del outbox cambia de estado). */
    private void observarEnvioWhatsapp(AppDatabase db) {
        db.whatsappOutboxDao().observarPorOrden(idOrden).observe(this, fila -> {
            String estado = fila == null ? null : fila.estado;
            pintarEnvioWhatsapp(fila);
            if (WhatsappOutbox.ENVIADO.equals(estado) && estadoWhatsappAnterior != null
                    && !WhatsappOutbox.ENVIADO.equals(estadoWhatsappAnterior)) {
                Snackbar.make(findViewById(android.R.id.content),
                        "Ticket enviado a tu WhatsApp", Snackbar.LENGTH_LONG).show();
            }
            estadoWhatsappAnterior = estado;
        });
    }

    private void pintarEnvioWhatsapp(WhatsappOutbox fila) {
        View card = findViewById(R.id.cardConfWhatsapp);
        TextView titulo = findViewById(R.id.tvConfWhatsappEstado);
        TextView detalle = findViewById(R.id.tvConfWhatsappDetalle);
        Button accion = findViewById(R.id.btnConfWhatsappAccion);

        if (fila == null) {
            // integración con WAHA desactivada: no hay nada que mostrar
            card.setVisibility(View.GONE);
            return;
        }
        card.setVisibility(View.VISIBLE);
        accion.setVisibility(View.GONE);
        String error = MensajesWhatsapp.amigable(fila.ultimoError);

        switch (fila.estado) {
            case WhatsappOutbox.ENVIADO:
                titulo.setText("Ticket enviado a tu WhatsApp");
                detalle.setText("Revisa tu chat: ahí tienes el comprobante de la compra.");
                break;

            case WhatsappOutbox.FALLIDO:
                titulo.setText("No se pudo enviar el ticket por WhatsApp");
                detalle.setText(error);
                accion.setVisibility(View.VISIBLE);
                if (MensajesWhatsapp.esSinNumero(fila.ultimoError)) {
                    accion.setText(R.string.whatsapp_vincular);
                    accion.setOnClickListener(v -> abrirVinculacion());
                } else {
                    accion.setText("Reintentar ahora");
                    accion.setOnClickListener(v -> WahaSyncScheduler.reintentarOrden(this, idOrden));
                }
                break;

            default:                   // PENDIENTE / ENVIANDO
                if (fila.ultimoError == null || fila.ultimoError.isEmpty()) {
                    titulo.setText("Enviando tu ticket por WhatsApp…");
                    detalle.setText("");
                } else {
                    titulo.setText("Aún no se pudo enviar (intento " + fila.intentos + ")");
                    detalle.setText(error + "\nSe reintentará automáticamente.");
                    accion.setVisibility(View.VISIBLE);
                    accion.setText("Reintentar ahora");
                    accion.setOnClickListener(v -> WahaSyncScheduler.reintentarOrden(this, idOrden));
                }
        }
    }

    /** Abre el diálogo de vinculación con el número actual del dueño de la orden. */
    private void abrirVinculacion() {
        AppDatabase db = AppDatabase.getInstance(this);
        DatabaseExecutor.runAsync(() -> {
            Orden o = db.ordenDao().buscarPorId(idOrden);
            return o == null ? null : db.usuarioDao().buscarPorId(o.idUsuario);
        }, (Usuario u) -> {
            if (u == null) return;
            VincularWhatsappDialog.mostrar(this, u.idUsuario, u.whatsappChatId, null);
        });
    }

    private void irAlInicio() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void pintar(Datos d) {
        if (d == null || d.orden == null || d.boletos == null || d.boletos.isEmpty()) return;
        BoletoDetalle primero = d.boletos.get(0);
        String tipo = primero.tipoEstablecimiento;

        ((TextView) findViewById(R.id.tvConfOrden)).setText(
                "Orden #" + d.orden.idOrden + (d.pago != null ? " · Ref. " + d.pago.referenciaExterna : ""));
        ((TextView) findViewById(R.id.tvConfEvento)).setText(primero.nombreEvento);
        ((TextView) findViewById(R.id.tvConfLugar)).setText(primero.nombreEstablecimiento);

        String fecha = FechaUtils.fechaHoraLegible(primero.fechaHora);
        ((TextView) findViewById(R.id.tvConfFecha)).setText(
                ("Museo".equals(tipo) ? "Horario de entrada: " : "Fecha y hora: ") + fecha);

        ((TextView) findViewById(R.id.tvConfCantidad)).setText("Cantidad de boletos: " + d.boletos.size());
        ((TextView) findViewById(R.id.tvConfDetalle)).setText(armarDetalle(d.boletos));
        ((TextView) findViewById(R.id.tvConfMonto)).setText(
                String.format(Locale.getDefault(), "Monto pagado: $%,.2f", d.orden.montoTotal));

        if (d.pago != null && d.metodo != null) {
            String metodo = d.metodo.nombre;
            if (d.pago.ultimosCuatroDigitos != null) metodo += " •••• " + d.pago.ultimosCuatroDigitos;
            if (d.pago.cuentaPaypal != null) metodo += " (" + d.pago.cuentaPaypal + ")";
            if (d.pago.nombreTitular != null) metodo += " · " + d.pago.nombreTitular;
            ((TextView) findViewById(R.id.tvConfMetodo)).setText(metodo);
        }

        ((TextView) findViewById(R.id.tvConfAcceso)).setText(armarAcceso(primero, tipo));
    }

    /** Cine: asientos por servicio. Teatro/Museo: sección × cantidad con su subtotal. */
    private String armarDetalle(List<BoletoDetalle> boletos) {
        Map<String, StringBuilder> asientosPorZona = new LinkedHashMap<>();
        Map<String, Integer> cantidadPorZona = new LinkedHashMap<>();
        Map<String, Double> montoPorZona = new LinkedHashMap<>();

        for (BoletoDetalle b : boletos) {
            Integer c = cantidadPorZona.get(b.nombreZona);
            cantidadPorZona.put(b.nombreZona, c == null ? 1 : c + 1);
            Double m = montoPorZona.get(b.nombreZona);
            montoPorZona.put(b.nombreZona, (m == null ? 0 : m) + b.precioFinal);
            if (b.codigoAsiento != null) {
                StringBuilder sb = asientosPorZona.get(b.nombreZona);
                if (sb == null) {
                    sb = new StringBuilder();
                    asientosPorZona.put(b.nombreZona, sb);
                }
                if (sb.length() > 0) sb.append(", ");
                sb.append(b.codigoAsiento);
            }
        }

        StringBuilder out = new StringBuilder();
        for (String zona : cantidadPorZona.keySet()) {
            if (out.length() > 0) out.append("\n");
            StringBuilder asientos = asientosPorZona.get(zona);
            if (asientos != null) {
                out.append("• Servicio ").append(zona).append(" — asientos: ").append(asientos);
            } else {
                out.append("• ").append(zona).append(" ×").append(cantidadPorZona.get(zona));
            }
            out.append(String.format(Locale.getDefault(), " — $%,.2f", montoPorZona.get(zona)));
        }
        return out.toString();
    }

    private String armarAcceso(BoletoDetalle b, String tipo) {
        StringBuilder sb = new StringBuilder();
        if ("Teatro".equals(tipo)) {
            sb.append("Código de vestimenta:\n").append(b.vestimenta);
        } else if ("Cine".equals(tipo)) {
            sb.append("Clasificación: ").append(ClasificacionCine.descripcion(b.clasificacion));
        } else if ("Museo".equals(tipo)) {
            sb.append("Horario de entrada: ").append(FechaUtils.fechaHoraLegible(b.fechaHora));
        }
        if (b.restricciones != null && !b.restricciones.isEmpty()) {
            sb.append("\n\nRestricciones de acceso:\n").append(b.restricciones);
        }
        return sb.toString();
    }
}
