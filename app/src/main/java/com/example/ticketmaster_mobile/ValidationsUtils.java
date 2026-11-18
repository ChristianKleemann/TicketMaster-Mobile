package com.example.ticketmaster_mobile;

import java.util.regex.Pattern;

// Author: Christian Francisco Kleemann Toribio
public class ValidationsUtils {
    // RegEx para CURP Extendido (20 caracteres exactos):
    // [A-Z]{4}       -> 4 letras (primeros apellidos y nombre)
    // [0-9]{6}       -> 6 números (AAMMDD)
    // [HM]           -> 1 letra (Hombre / Mujer)
    // [A-Z]{2}       -> 2 letras (Entidad estándar CURP)
    // [A-Z0-9]{3}    -> 3 consonantes internas / homoclave
    // [A-Z]{2}       -> 2 letras adicionales del Estado de Nacimiento
    // [0-9]          -> 1 número final
    private static final String REGEX_CURP = "^[A-Z]{4}\\d{6}[HM][A-Z]{2}[A-Z0-9]{3}[A-Z]{2}\\d{2}$";

    /**
     * Valida detalladamente el usuario e indica qué regla está fallando.
     */
    public static String obtenerErrorUsuario(String usuario) {
        if (usuario == null || usuario.isEmpty()) {
            return "El campo usuario no puede estar vacío.";
        }
        if (usuario.length() != 20) {
            return "El usuario debe tener exactamente 20 caracteres (actual: " + usuario.length() + ").";
        }
        if (usuario.contains(" ")) {
            return "No se permiten espacios en ningún lugar del usuario.";
        }
        if (!usuario.equals(usuario.toUpperCase())) {
            return "El usuario debe estar completamente en MAYÚSCULAS.";
        }
        char primero = usuario.charAt(0);
        char ultimo = usuario.charAt(usuario.length() - 1);
        if (!Character.isLetterOrDigit(primero) || !Character.isLetterOrDigit(ultimo)) {
            return "El usuario no puede iniciar ni terminar con caracteres especiales.";
        }
        if (!Pattern.matches(REGEX_CURP, usuario)) {
            return "Estructura inválida. Debe ser: [4 Letras][6 Números][1 Sexo][2 Entidad][3 Homoclave][2 Estado Ext][1 Número].";
        }
        return null; // Válido
    }

    /**
     * Valida detalladamente la contraseña e indica qué regla está fallando.
     */
    public static String obtenerErrorPassword(String pass) {
        if (pass == null || pass.isEmpty()) {
            return "La contraseña no puede estar vacía.";
        }
        if (pass.length() != 8) {
            return "La contraseña debe tener exactamente 8 caracteres (actual: " + pass.length() + ").";
        }
        if (!Pattern.compile("[A-Z]").matcher(pass).find()) {
            return "La contraseña debe incluir al menos una letra mayúscula.";
        }
        if (!Pattern.compile("[a-z]").matcher(pass).find()) {
            return "La contraseña debe incluir al menos una letra minúscula.";
        }
        if (!Pattern.compile("[0-9]").matcher(pass).find()) {
            return "La contraseña debe incluir al menos un número.";
        }
        if (!Pattern.compile("[@#$%^&+=!._\\-*]").matcher(pass).find()) {
            return "La contraseña debe incluir al menos un carácter especial (@, #, $, %, etc.).";
        }
        return null; // Válido
    }

    /**
     * Generación del CURP de 20 caracteres a partir de los datos personales.
     */
    public static String generarCurpExtendido(
            String apellido1, String apellido2,
            String nombre1,
            String anio, String mes, String dia,
            String sexo, String entidad) {

        apellido1 = apellido1.trim().toUpperCase();
        apellido2 = apellido2.trim().toUpperCase();
        nombre1 = nombre1.trim().toUpperCase();
        sexo = sexo.trim().toUpperCase();
        entidad = entidad.trim().toUpperCase();

        if (apellido1.isEmpty() || nombre1.isEmpty() || anio.length() < 4 || mes.isEmpty() || dia.isEmpty()) {
            return "";
        }

        // c1: Inicial primer apellido
        String c1 = apellido1.substring(0, 1);

        // c2: Primera vocal interna del primer apellido
        String c2 = "X";
        for (int i = 1; i < apellido1.length(); i++) {
            char ch = apellido1.charAt(i);
            if ("AEIOU".indexOf(ch) != -1) {
                c2 = String.valueOf(ch);
                break;
            }
        }

        // c3: Inicial segundo apellido
        String c3 = apellido2.isEmpty() ? "X" : apellido2.substring(0, 1);

        // c4: Inicial primer nombre
        String c4 = nombre1.substring(0, 1);

        // c5-c10: Fecha AAMMDD (Tomamos los últimos 2 dígitos del año)
        String aA = anio.length() >= 4 ? anio.substring(2, 4) : "00";
        String mM = mes.length() == 1 ? "0" + mes : mes;
        String dD = dia.length() == 1 ? "0" + dia : dia;
        String c5_10 = aA + mM + dD;

        // c11: Sexo (H/M)
        String c11 = sexo.isEmpty() ? "H" : sexo.substring(0, 1);

        // c12-c13: Clave de Entidad Federativa (2 letras)
        String c12_13 = obtenerClaveEntidad(entidad);

        // c14-c16: Consonantes internas de apellidos y nombre + homoclave
        String c14_16 = "RLJ";

        // c17-c18: MEJORA: 2 letras adicionales correspondientes al estado de nacimiento (ej. LJ)
        String c17_18 = c12_13;

        // c19-c20: Homoclave final (número)
        String c19_20 = "01"; // Representa los caracteres finales

        return c1 + c2 + c3 + c4 + c5_10 + c11 + c12_13 + c14_16 + c17_18 + c19_20;
    }

    private static String obtenerClaveEntidad(String entidad) {
        switch (entidad) {
            case "AGUASCALIENTES": return "AS";
            case "BAJA CALIFORNIA": return "BC";
            case "BAJA CALIFORNIA SUR": return "BS";
            case "CAMPECHE": return "CC";
            case "COAHUILA": return "CL";
            case "COLIMA": return "CM";
            case "CHIAPAS": return "CS";
            case "CHIHUAHUA": return "CH";
            case "DISTRITO FEDERAL":
            case "CIUDAD DE MEXICO":
            case "CDMX": return "DF";
            case "DURANGO": return "DG";
            case "GUANAJUATO": return "GT";
            case "GUERRERO": return "GR";
            case "HIDALGO": return "HG";
            case "JALISCO": return "JC";
            case "MEXICO": return "MC";
            case "MICHOACAN": return "MN";
            case "MORELOS": return "MS";
            case "NAYARIT": return "NT";
            case "NUEVO LEON": return "NL";
            case "OAXACA": return "OC";
            case "PUEBLA": return "PL";
            case "QUERETARO": return "QT";
            case "QUINTANA ROO": return "QR";
            case "SAN LUIS POTOSI": return "SP";
            case "SINALOA": return "SL";
            case "SONORA": return "SR";
            case "TABASCO": return "TC";
            case "TAMAULIPAS": return "TS";
            case "TLAXCALA": return "TL";
            case "VERACRUZ": return "VZ";
            case "YUCATAN": return "YN";
            case "ZACATECAS": return "ZS";
            default: return "NE"; // Nacido en el Extranjero / No Especificado
        }
    }
}
