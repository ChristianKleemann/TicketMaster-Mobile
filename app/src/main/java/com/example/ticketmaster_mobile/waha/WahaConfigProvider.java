package com.example.ticketmaster_mobile.waha;

import androidx.annotation.VisibleForTesting;

import com.example.ticketmaster_mobile.BuildConfig;

/** Construye la configuración a partir de los valores inyectados en BuildConfig. */
public final class WahaConfigProvider {

    private static volatile WahaConfig sobrescrita;

    private WahaConfigProvider() {
    }

    /** SOLO PRUEBAS: apunta la integración a un WAHA simulado. null restablece BuildConfig. */
    @VisibleForTesting
    public static void setOverrideForTesting(WahaConfig config) {
        sobrescrita = config;
    }

    public static WahaConfig actual() {
        WahaConfig o = sobrescrita;
        if (o != null) return o;
        return new WahaConfig(
                BuildConfig.WAHA_BASE_URL,
                BuildConfig.WAHA_API_KEY,
                BuildConfig.WAHA_SESSION,
                BuildConfig.WAHA_CHAT_DESTINO,
                10_000,
                20_000,
                BuildConfig.WAHA_ALLOW_HTTP);
    }
}
