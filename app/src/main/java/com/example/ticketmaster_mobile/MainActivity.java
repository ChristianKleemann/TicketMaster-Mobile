package com.example.ticketmaster_mobile;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.ticketmaster_mobile.session.SessionManager;
import com.example.ticketmaster_mobile.ui.tickets.TicketsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Si por alguna razón se llega aquí sin sesión activa (ej. proceso del sistema
        // matado y relanzado en esta pantalla), regresamos al login.
        SessionManager sessionManager = new SessionManager(this);
        if (!sessionManager.haySesionActiva()
                || com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new ProfileFragment())
                    .commit();
            bottomNav.setSelectedItemId(R.id.nav_profile);
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_profile) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, new ProfileFragment())
                        .commit();
                return true;
            } else if (itemId == R.id.nav_tickets) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, new TicketsFragment())
                        .commit();
                return true;
            }
            return false;
        });
    }
}
