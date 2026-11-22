package com.example.ticketmaster_mobile.whatsapp;

import static org.junit.Assert.*;

import org.junit.Test;

public class GreenApiConfigProviderTest {

    @Test
    public void normalizaChatId() {
        assertEquals("5213312345678@c.us", GreenApiConfigProvider.normalizarChatId("+52 1 33 1234-5678"));
        assertEquals("5213312345678@c.us", GreenApiConfigProvider.normalizarChatId("5213312345678@c.us"));
        assertEquals("120363012345@g.us", GreenApiConfigProvider.normalizarChatId("120363012345@g.us"));
        assertEquals("", GreenApiConfigProvider.normalizarChatId("abc"));
        assertEquals("", GreenApiConfigProvider.normalizarChatId("123"));
        assertEquals("", GreenApiConfigProvider.normalizarChatId("1234567890123456"));
        assertEquals("", GreenApiConfigProvider.normalizarChatId(null));
        assertEquals("", GreenApiConfigProvider.normalizarChatId("  "));
    }

    @Test
    public void normalizaYValida() {
        GreenApiConfig c = new GreenApiConfig(" https://api.green-api.com// ", " 7103-123 ", " tok ",
                "5213312345678", 1, 1, false);
        assertEquals("https://api.green-api.com", c.apiUrl);
        assertEquals("7103123", c.idInstance);
        assertEquals("tok", c.apiToken);
        assertTrue(c.estaConfigurado());
        assertEquals(GreenApiConfigProvider.API_URL_POR_DEFECTO, new GreenApiConfig("", "1", "t", "5213312345678", 1, 1, false).apiUrl);
        assertFalse(new GreenApiConfig("", "", "t", "5213312345678", 1, 1, false).estaConfigurado());
        assertFalse(new GreenApiConfig("", "1", "", "5213312345678", 1, 1, false).estaConfigurado());
        assertFalse(new GreenApiConfig("", "1", "t", "zzz", 1, 1, false).estaConfigurado());
        assertTrue(new GreenApiConfig("", "1", "t", "zzz", 1, 1, false).servidorConfigurado());
    }
}
