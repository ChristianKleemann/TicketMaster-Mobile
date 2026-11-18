package com.example.ticketmaster_mobile.integration.soporte;

import android.view.View;

import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.matcher.ViewMatchers;

import org.hamcrest.Matcher;

/** Acciones de Espresso propias del proyecto. */
public final class Acciones {

    private Acciones() {
    }

    /** Hace clic en un hijo (por id) de la fila de un RecyclerView; sirve para los botones − / +. */
    public static ViewAction clicEnHijo(final int idHijo) {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return ViewMatchers.isAssignableFrom(View.class);
            }

            @Override
            public String getDescription() {
                return "clic en el hijo con id " + idHijo;
            }

            @Override
            public void perform(UiController uiController, View fila) {
                View hijo = fila.findViewById(idHijo);
                if (hijo == null) throw new AssertionError("La fila no tiene el hijo con id " + idHijo);
                // Toque real (respeta enabled/visibilidad), no performClick()
                ViewActions.click().perform(uiController, hijo);
            }
        };
    }
}
