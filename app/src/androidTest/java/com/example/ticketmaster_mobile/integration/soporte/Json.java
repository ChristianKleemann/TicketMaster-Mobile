package com.example.ticketmaster_mobile.integration.soporte;

import org.json.JSONException;
import org.json.JSONObject;

/** Lectura del cuerpo JSON que la app envía a WAHA. */
public final class Json {

    private Json() {
    }

    public static JSONObject objeto(String cuerpo) {
        try {
            return new JSONObject(cuerpo);
        } catch (JSONException e) {
            throw new AssertionError("El cuerpo enviado a WAHA no es JSON válido: " + cuerpo, e);
        }
    }

    public static String texto(FakeGreenApiServer.Peticion p) {
        try {
            return objeto(p.cuerpo).getString("text");
        } catch (JSONException e) {
            throw new AssertionError("El JSON enviado a WAHA no trae 'text'", e);
        }
    }
}
