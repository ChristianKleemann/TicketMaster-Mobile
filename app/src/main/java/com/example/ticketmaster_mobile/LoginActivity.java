package com.example.ticketmaster_mobile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.ticketmaster_mobile.data.AppDatabase;
import com.example.ticketmaster_mobile.data.DatabaseExecutor;
import com.example.ticketmaster_mobile.data.entity.Usuario;
import com.example.ticketmaster_mobile.session.SessionManager;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import android.widget.Toast;

import com.example.ticketmaster_mobile.data.remote.AuthFirebase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilUsuario, tilPassword;
    private TextInputEditText etUsuario, etPassword;
    private Button btnLogin;

    private AppDatabase db;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        db = AppDatabase.getInstance(this);
        sessionManager = new SessionManager(this);

        tilUsuario = findViewById(R.id.tilUsuario);
        tilPassword = findViewById(R.id.tilPassword);
        etUsuario = findViewById(R.id.etUsuario);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);

        // Recibir la CURP tras el registro, para no volver a teclearla
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("EXTRA_CURP_USUARIO")) {
            String curpRecibido = intent.getStringExtra("EXTRA_CURP_USUARIO");
            if (etUsuario != null) {
                etUsuario.setText(curpRecibido);
            }
        }

        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> intentarLogin());
        }

        Button btnGoToSignUp = findViewById(R.id.btnGoToSignUp);
        btnGoToSignUp.setOnClickListener(v -> {
            Intent signUpIntent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(signUpIntent);
        });
    }

    private void intentarLogin() {
        String usuario = etUsuario.getText().toString().trim();
        String password = etPassword.getText().toString();

        String errorUsuario = ValidationsUtils.obtenerErrorUsuario(usuario);
        tilUsuario.setError(errorUsuario);
        if (errorUsuario != null) {
            return;
        }
        if (password.isEmpty()) {
            tilPassword.setError("Ingresa tu contraseña");
            return;
        }
        tilPassword.setError(null);

        btnLogin.setEnabled(false);

        FirebaseAuth.getInstance()
                .signInWithEmailAndPassword(AuthFirebase.correoDe(usuario), password)
                .addOnSuccessListener(r -> asegurarCopiaLocalYEntrar(r.getUser().getUid(), usuario))
                .addOnFailureListener(e -> {
                    btnLogin.setEnabled(true);
                    mostrarError(AuthFirebase.mensaje(e));
                });
    }

    /**
     * Puente temporal: el resto de la app usa un idUsuario entero de Room. Si el usuario
     * existe en Firebase pero no en este teléfono (otro dispositivo, datos borrados),
     * se reconstruye la copia local desde Firestore.
     */
    private void asegurarCopiaLocalYEntrar(String uid, String curp) {
        DatabaseExecutor.runAsync(() -> db.usuarioDao().buscarPorCurp(curp), local -> {
            if (local != null) {
                entrar(local.idUsuario);
                return;
            }
            FirebaseFirestore.getInstance().collection("usuarios").document(uid).get()
                    .addOnSuccessListener(doc -> {
                        if (!doc.exists()) {
                            btnLogin.setEnabled(true);
                            mostrarError("No se encontró tu perfil. Contacta al administrador.");
                            return;
                        }
                        DatabaseExecutor.runAsync(() -> {
                            Usuario u = new Usuario();
                            u.nombre = valor(doc.getString("nombre"));
                            u.apellidoPaterno = valor(doc.getString("apellidoPaterno"));
                            u.apellidoMaterno = valor(doc.getString("apellidoMaterno"));
                            u.curp = curp;
                            u.fechaNacimiento = valor(doc.getString("fechaNacimiento"));
                            u.estado = valor(doc.getString("estado"));
                            u.sexo = valor(doc.getString("sexo"));
                            u.fechaRegistro = valor(doc.getString("fechaRegistro"));
                            u.passwordHash = "";
                            return (int) db.usuarioDao().insertar(u);
                        }, idLocal -> entrar(idLocal));
                    })
                    .addOnFailureListener(e -> {
                        btnLogin.setEnabled(true);
                        mostrarError(AuthFirebase.mensaje(e));
                    });
        });
    }

    private static String valor(String s) {
        return s == null ? "" : s;
    }

    private void entrar(int idUsuarioLocal) {
        btnLogin.setEnabled(true);
        sessionManager.iniciarSesion(idUsuarioLocal);
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finish();
    }

    private void mostrarError(String mensaje) {
        new AlertDialog.Builder(this)
                .setTitle("No se pudo iniciar sesión")
                .setMessage(mensaje)
                .setPositiveButton("Entendido", null)
                .show();
    }
}
