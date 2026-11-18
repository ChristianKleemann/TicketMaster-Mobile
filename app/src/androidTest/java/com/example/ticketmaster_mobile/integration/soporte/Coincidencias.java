package com.example.ticketmaster_mobile.integration.soporte;

import android.view.View;
import android.view.ViewParent;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.espresso.matcher.BoundedMatcher;
import androidx.test.espresso.matcher.ViewMatchers;

import com.google.android.material.textfield.TextInputLayout;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

/** Matchers de Espresso propios del proyecto. */
public final class Coincidencias {

    private Coincidencias() {
    }

    /** El TextInputLayout muestra exactamente este mensaje de error. */
    public static Matcher<View> conError(final String esperado) {
        return new BoundedMatcher<View, TextInputLayout>(TextInputLayout.class) {
            @Override
            public void describeTo(Description d) {
                d.appendText("TextInputLayout con error: ").appendValue(esperado);
            }

            @Override
            protected boolean matchesSafely(TextInputLayout til) {
                CharSequence error = til.getError();
                return error != null && esperado.contentEquals(error);
            }
        };
    }

    /** El TextInputLayout NO muestra error. */
    public static Matcher<View> sinError() {
        return new BoundedMatcher<View, TextInputLayout>(TextInputLayout.class) {
            @Override
            public void describeTo(Description d) {
                d.appendText("TextInputLayout sin error");
            }

            @Override
            protected boolean matchesSafely(TextInputLayout til) {
                return til.getError() == null;
            }
        };
    }

    /**
     * Vista con id {@code idHijo} que está dentro de la fila de un RecyclerView cuyo contenido
     * incluye {@code textoDeLaFila} (p. ej. el botón "+" de la zona "Palco").
     */
    public static Matcher<View> hijoDeFila(final int idHijo, final String textoDeLaFila) {
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description d) {
                d.appendText("vista id=" + idHijo + " en la fila con texto ").appendValue(textoDeLaFila);
            }

            @Override
            protected boolean matchesSafely(View vista) {
                if (vista.getId() != idHijo) return false;
                View fila = vista;
                ViewParent padre = vista.getParent();
                while (padre instanceof View && !(padre instanceof RecyclerView)) {
                    fila = (View) padre;
                    padre = padre.getParent();
                }
                if (!(padre instanceof RecyclerView)) return false;
                return ViewMatchers.hasDescendant(ViewMatchers.withText(textoDeLaFila)).matches(fila);
            }
        };
    }
}
