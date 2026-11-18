package com.example.ticketmaster_mobile.waha;

/**
 * Validación y normalización del número de WhatsApp que captura el usuario. Java puro.
 *
 * Acepta "33 2218 0006", "(33) 2218-0006", "+52 33 2218 0006", "523322180006", "5213322180006"...
 * y devuelve siempre el chatId de WAHA. Para México WhatsApp exige 52 + 1 + 10 dígitos
 * ("5213322180006"); sin ese "1" WAHA responde "No LID for user".
 */
public final class TelefonoWhatsapp {

    public static final class Resultado {
        /** Solo dígitos en formato internacional, p. ej. "5213322180006". null si hay error. */
        public final String digitos;
        /** chatId de WAHA, p. ej. "5213322180006@c.us". null si hay error. */
        public final String chatId;
        /** Mensaje para el usuario; null si el número es válido. */
        public final String error;

        private Resultado(String digitos, String error) {
            this.digitos = digitos;
            this.chatId = digitos == null ? null : digitos + "@c.us";
            this.error = error;
        }

        public boolean esValido() {
            return error == null;
        }
    }

    private TelefonoWhatsapp() {
    }

    public static Resultado validar(String entrada) {
        if (entrada == null || entrada.trim().isEmpty()) {
            return error("Escribe tu número de WhatsApp.");
        }
        String t = entrada.trim();
        StringBuilder d = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= '0' && c <= '9') d.append(c);
            else if (c == '+' && i == 0) continue;
            else if (c == ' ' || c == '-' || c == '(' || c == ')') continue;
            else return error("Usa solo dígitos (puedes incluir +, espacios o guiones).");
        }
        String n = d.toString();
        if (n.length() < 10) {
            return error("El número es muy corto: escribe los 10 dígitos de tu celular (lada + número).");
        }
        if (n.length() > 15) {
            return error("El número es muy largo (máximo 15 dígitos en formato internacional).");
        }

        if (n.length() == 10) {                       // celular de México sin lada de país
            if (n.charAt(0) == '0' || n.charAt(0) == '1') {
                return error("Un celular de 10 dígitos no puede empezar con 0 ni con 1.");
            }
            n = "521" + n;
        } else if (n.length() == 12 && n.startsWith("52")) {   // 52 + 10 dígitos: falta el "1"
            n = "521" + n.substring(2);
        }
        // 13 dígitos "521..." ya es correcto; otros países (11 a 15 dígitos) se aceptan tal cual
        return new Resultado(n, null);
    }

    private static Resultado error(String mensaje) {
        return new Resultado(null, mensaje);
    }

    /** "5213322180006@c.us" -> "+52 1 33 2218 0006" (solo para mostrar). */
    public static String formatear(String chatId) {
        if (chatId == null || chatId.isEmpty()) return "";
        String d = chatId.replace("@c.us", "");
        if (d.length() == 13 && d.startsWith("521")) {
            return "+52 1 " + d.substring(3, 5) + " " + d.substring(5, 9) + " " + d.substring(9);
        }
        return "+" + d;
    }
}