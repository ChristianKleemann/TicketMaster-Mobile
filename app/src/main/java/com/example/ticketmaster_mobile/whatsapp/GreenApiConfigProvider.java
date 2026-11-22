package com.example.ticketmaster_mobile.whatsapp;

import androidx.annotation.VisibleForTesting;

import com.example.ticketmaster_mobile.BuildConfig;

/** Punto único de acceso a la configuración de GREEN-API (BuildConfig o una de pruebas). */
public final class GreenApiConfigProvider {

    private static volatile GreenApiConfig sobrescrita;

    private GreenApiConfigProvider() {}

    /** SOLO PRUEBAS: apunta la integración a un GREEN-API simulado. null restablece BuildConfig. */
    @VisibleForTesting
    public static void setOverrideForTesting(GreenApiConfig config) {
        sobrescrita = config;
    }

    public static GreenApiConfig actual() {
        GreenApiConfig o = sobrescrita;
        if (o != null) return o;
        return new GreenApiConfig(
                BuildConfig.GREEN_API_URL,
                BuildConfig.GREEN_ID_INSTANCE,
                BuildConfig.GREEN_API_TOKEN,
                BuildConfig.GREEN_CHAT_DESTINO,
                10_000,
                20_000,
                false);
    }
}