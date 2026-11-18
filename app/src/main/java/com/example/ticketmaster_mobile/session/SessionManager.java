package com.example.ticketmaster_mobile.session;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Administra la sesión del usuario autenticado. Solo guarda el ID_Usuario
 * (nunca credenciales) para poder consultar sus datos y su historial de compras.
 */
public class SessionManager {

    private static final String PREFS_NAME = "TicketMasterSession";
    private static final String KEY_ID_USUARIO = "id_usuario_activo";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void iniciarSesion(int idUsuario) {
        prefs.edit().putInt(KEY_ID_USUARIO, idUsuario).apply();
    }

    public int getIdUsuarioActivo() {
        return prefs.getInt(KEY_ID_USUARIO, -1);
    }

    public boolean haySesionActiva() {
        return getIdUsuarioActivo() != -1;
    }

    public void cerrarSesion() {
        prefs.edit().remove(KEY_ID_USUARIO).apply();
    }
}
