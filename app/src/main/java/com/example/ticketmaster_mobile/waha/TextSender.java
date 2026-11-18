package com.example.ticketmaster_mobile.waha;

/** Abstracción del envío, para poder probar el procesador sin red. */
public interface TextSender {

    /** Envía texto al destino de la configuración. */
    EnvioResultado enviarTexto(String texto);

    /** Envía texto a un chat concreto (el del usuario). Por defecto usa el de la configuración. */
    default EnvioResultado enviarTexto(String chatId, String texto) {
        return enviarTexto(texto);
    }
}