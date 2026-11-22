package com.example.ticketmaster_mobile.whatsapp;

/** Traduce los errores técnicos guardados en el outbox a mensajes claros para el usuario. */
public final class MensajesWhatsapp {

    /** Marca que usa el procesador cuando la orden no tiene a dónde enviarse. */
    public static final String SIN_NUMERO = "Sin número de WhatsApp vinculado";

    private MensajesWhatsapp() {
    }

    public static boolean esSinNumero(String ultimoError) {
        return ultimoError != null && ultimoError.contains(SIN_NUMERO);
    }

    public static String amigable(String e) {
        if (e == null || e.isEmpty()) return "";
        if (e.contains(SIN_NUMERO)) {
            return "Aún no has vinculado tu número de WhatsApp.";
        }
        if (e.contains("ConnectException") || e.contains("Error de red")
                || e.contains("tiempo de conexión") || e.contains("UnknownHost")) {
            return "No se pudo conectar con el servidor de WhatsApp (WAHA). "
                    + "Revisa que esté encendido y que el teléfono tenga acceso a su IP.";
        }
        if (e.contains("API key")) {
            return "La clave de acceso a WAHA es incorrecta.";
        }
        if (e.contains("Sesión de WhatsApp no lista")) {
            return "La sesión de WhatsApp en WAHA no está activa. Ábrela en el dashboard y pulsa Start.";
        }
        if (e.contains("No LID for user")) {
            return "Ese número no tiene WhatsApp o está mal escrito.";
        }
        if (e.contains("no está configurado")) {
            return "La integración con WAHA no está configurada en esta compilación.";
        }
        if (e.contains("no respondió a tiempo")) {
            return "WAHA tardó demasiado en responder.";
        }
        return e;
    }
}
