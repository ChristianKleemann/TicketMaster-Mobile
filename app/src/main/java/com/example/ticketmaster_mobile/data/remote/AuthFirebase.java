package com.example.ticketmaster_mobile.data.remote;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

import java.util.Locale;

/** Utilidades de Firebase Auth: correo interno a partir de la CURP y mensajes de error legibles. */
public final class AuthFirebase {

    private static final String DOMINIO = "@ticketmaster.app";

    private AuthFirebase() {}

    /** Firebase exige un correo; se deriva de la CURP (que es el usuario de la app). */
    public static String correoDe(String curp) {
        return curp.trim().toLowerCase(Locale.ROOT) + DOMINIO;
    }

    public static String mensaje(Exception e) {
        if (e instanceof FirebaseAuthUserCollisionException) {
            return "Ya existe una cuenta registrada con esa CURP.";
        }
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return "La contraseña es demasiado débil.";
        }
        // Con la protección contra enumeración de correos (activa por defecto), un usuario
        // inexistente y una contraseña incorrecta llegan como el mismo error.
        if (e instanceof FirebaseAuthInvalidUserException
                || e instanceof FirebaseAuthInvalidCredentialsException) {
            return "CURP o contraseña incorrectas. Si aún no tienes cuenta, regístrate primero.";
        }
        if (e instanceof FirebaseNetworkException) {
            return "Sin conexión a internet. Verifica tu red e intenta de nuevo.";
        }
        return "No se pudo completar la operación. Intenta de nuevo.";
    }
}