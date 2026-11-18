package com.example.ticketmaster_mobile;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.ticketmaster_mobile.data.AppDatabase;
import com.example.ticketmaster_mobile.data.DatabaseExecutor;
import com.example.ticketmaster_mobile.waha.TelefonoWhatsapp;
import com.example.ticketmaster_mobile.waha.WahaClient;
import com.example.ticketmaster_mobile.waha.WahaConfigProvider;
import com.example.ticketmaster_mobile.waha.WahaSyncScheduler;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Diálogo para vincular (o cambiar/quitar) el número de WhatsApp del usuario. Valida el formato,
 * comprueba con WAHA que el número exista en WhatsApp y, si WAHA no está disponible, deja guardarlo
 * igualmente tras avisar. Al guardar reintenta de inmediato las notificaciones pendientes.
 */
public final class VincularWhatsappDialog {

    private VincularWhatsappDialog() {
    }

    /**
     * @param chatIdActual chatId ya vinculado (para precargarlo y ofrecer "Desvincular"); puede ser null
     * @param alGuardar    se ejecuta en el hilo principal tras guardar o desvincular
     */
    public static void mostrar(Context context, int idUsuario, String chatIdActual, Runnable alGuardar) {
        View vista = LayoutInflater.from(context).inflate(R.layout.dialog_vincular_whatsapp, null);
        TextInputLayout til = vista.findViewById(R.id.tilTelefonoWhatsapp);
        TextInputEditText et = vista.findViewById(R.id.etTelefonoWhatsapp);
        ProgressBar pb = vista.findViewById(R.id.pbWhatsapp);
        TextView tvEstado = vista.findViewById(R.id.tvWhatsappEstadoDialogo);

        if (chatIdActual != null && !chatIdActual.isEmpty()) {
            et.setText(chatIdActual.replace("@c.us", ""));
        }

        AlertDialog.Builder b = new AlertDialog.Builder(context)
                .setTitle(R.string.whatsapp_dialogo_titulo)
                .setView(vista)
                .setPositiveButton(R.string.whatsapp_guardar, null)   // se asigna abajo (no cerrar solo)
                .setNegativeButton(R.string.whatsapp_cancelar, null);
        if (chatIdActual != null && !chatIdActual.isEmpty()) {
            b.setNeutralButton(R.string.whatsapp_desvincular, null);
        }
        AlertDialog dialogo = b.create();
        dialogo.show();

        Button btnGuardar = dialogo.getButton(AlertDialog.BUTTON_POSITIVE);
        Button btnDesvincular = dialogo.getButton(AlertDialog.BUTTON_NEUTRAL);
        final boolean[] guardarSinVerificar = {false};

        et.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int r, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                // Si el usuario edita el número, se vuelve a verificar
                til.setError(null);
                guardarSinVerificar[0] = false;
                btnGuardar.setText(R.string.whatsapp_guardar);
                tvEstado.setVisibility(View.GONE);
            }
        });

        btnGuardar.setOnClickListener(v -> {
            // 1) Formato del número (sin red)
            TelefonoWhatsapp.Resultado num = TelefonoWhatsapp.validar(
                    et.getText() == null ? "" : et.getText().toString());
            if (!num.esValido()) {
                til.setError(num.error);
                return;
            }
            til.setError(null);

            if (guardarSinVerificar[0]) {                       // ya se le avisó que no se pudo verificar
                guardar(context, dialogo, idUsuario, num.chatId, false, alGuardar);
                return;
            }

            // 2) Comprobación con WAHA (conexión, clave, sesión y existencia del número)
            btnGuardar.setEnabled(false);
            pb.setVisibility(View.VISIBLE);
            tvEstado.setVisibility(View.VISIBLE);
            tvEstado.setText("Verificando el número con WhatsApp…");

            DatabaseExecutor.runAsync(
                    () -> new WahaClient(WahaConfigProvider.actual()).diagnosticar(num.digitos),
                    dg -> {
                        btnGuardar.setEnabled(true);
                        pb.setVisibility(View.GONE);
                        if (dg.esOk()) {
                            // WAHA devuelve el chatId canónico del número (resuelve 52 vs 521)
                            String chatId = dg.chatId != null ? dg.chatId : num.chatId;
                            guardar(context, dialogo, idUsuario, chatId, true, alGuardar);
                        } else if (dg.tipo == WahaClient.Diagnostico.Tipo.NUMERO_NO_EXISTE) {
                            tvEstado.setVisibility(View.GONE);
                            til.setError(dg.mensaje);        // el número no es de WhatsApp: no se guarda
                        } else {
                            // No se pudo verificar (WAHA apagado, sin red, clave...): se avisa y se
                            // permite guardarlo con una segunda pulsación
                            guardarSinVerificar[0] = true;
                            btnGuardar.setText(R.string.whatsapp_guardar_igual);
                            tvEstado.setText("⚠️ " + dg.mensaje
                                    + "\nPuedes guardarlo igualmente; se enviará cuando WAHA esté disponible.");
                        }
                    });
        });

        if (btnDesvincular != null) {
            btnDesvincular.setOnClickListener(v -> DatabaseExecutor.runAsync(
                    () -> AppDatabase.getInstance(context).usuarioDao().quitarWhatsapp(idUsuario),
                    n -> {
                        Toast.makeText(context, "WhatsApp desvinculado", Toast.LENGTH_SHORT).show();
                        dialogo.dismiss();
                        if (alGuardar != null) alGuardar.run();
                    }));
        }
    }

    private static void guardar(Context context, AlertDialog dialogo, int idUsuario, String chatId,
                                boolean verificado, Runnable alGuardar) {
        DatabaseExecutor.runAsync(
                () -> AppDatabase.getInstance(context).usuarioDao().guardarWhatsapp(idUsuario, chatId),
                n -> {
                    if (n == null || n < 1) {
                        Toast.makeText(context, "No se pudo guardar el número. Inténtalo de nuevo.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    WahaSyncScheduler.reintentarDeUsuario(context, idUsuario);   // envía lo pendiente
                    Toast.makeText(context, (verificado ? "✅ WhatsApp vinculado: " : "WhatsApp guardado: ")
                            + TelefonoWhatsapp.formatear(chatId), Toast.LENGTH_LONG).show();
                    dialogo.dismiss();
                    if (alGuardar != null) alGuardar.run();
                });
    }
}
