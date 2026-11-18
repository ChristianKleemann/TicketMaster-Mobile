package com.example.ticketmaster_mobile;

import java.util.Calendar;
import java.util.regex.Pattern;

/**
 * Validaciones del formulario de pago (sin dependencias de Android).
 * Cada método devuelve null si el dato es válido, o el mensaje de error a mostrar.
 */
public final class PaymentValidationUtils {

    private static final Pattern SOLO_DIGITOS = Pattern.compile("^[0-9]+$");
    private static final Pattern SOLO_TEXTO = Pattern.compile("^\\p{L}+(?: \\p{L}+)*$");
    private static final Pattern CORREO = Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");

    public static final int LONGITUD_TARJETA = 16;
    public static final int LONGITUD_CVV = 3;

    private PaymentValidationUtils() {
    }

    /** Número de tarjeta: únicamente dígitos, exactamente 16. */
    public static String errorNumeroTarjeta(String numero) {
        if (numero == null || numero.isEmpty()) return "Ingresa el número de tarjeta";
        if (!SOLO_DIGITOS.matcher(numero).matches()) return "El número de tarjeta solo puede contener dígitos";
        if (numero.length() != LONGITUD_TARJETA) return "El número de tarjeta debe tener " + LONGITUD_TARJETA + " dígitos";
        return null;
    }

    /** Nombre del titular: solo texto (letras con acentos/ñ y espacios), sin números ni símbolos. */
    public static String errorNombre(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) return "Ingresa el nombre del titular";
        String n = nombre.trim();
        if (n.length() < 3) return "El nombre es demasiado corto";
        if (n.length() > 50) return "El nombre no puede exceder 50 caracteres";
        if (!SOLO_TEXTO.matcher(n).matches()) return "El nombre solo puede contener letras y espacios";
        return null;
    }

    /** Vencimiento en formato MM/AA que no esté vencido (el mes en curso aún es válido). */
    public static String errorVencimiento(String mmAa, Calendar hoy) {
        if (mmAa == null || mmAa.isEmpty()) return "Ingresa la fecha de vencimiento";
        if (!mmAa.matches("^\\d{2}/\\d{2}$")) return "Usa el formato MM/AA";
        int mes = Integer.parseInt(mmAa.substring(0, 2));
        int anio = 2000 + Integer.parseInt(mmAa.substring(3, 5));
        if (mes < 1 || mes > 12) return "El mes debe estar entre 01 y 12";
        int anioActual = hoy.get(Calendar.YEAR);
        int mesActual = hoy.get(Calendar.MONTH) + 1;
        if (anio < anioActual || (anio == anioActual && mes < mesActual)) return "La tarjeta está vencida";
        return null;
    }

    public static String errorCvv(String cvv) {
        if (cvv == null || cvv.isEmpty()) return "Ingresa el CVV";
        if (!SOLO_DIGITOS.matcher(cvv).matches() || cvv.length() != LONGITUD_CVV) {
            return "El CVV son " + LONGITUD_CVV + " dígitos";
        }
        return null;
    }

    public static String errorCorreo(String correo) {
        if (correo == null || correo.trim().isEmpty()) return "Ingresa el correo de tu cuenta PayPal";
        if (!CORREO.matcher(correo.trim()).matches()) return "Correo electrónico inválido";
        return null;
    }
}
