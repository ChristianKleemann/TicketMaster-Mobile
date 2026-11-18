package com.example.ticketmaster_mobile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.ticketmaster_mobile.data.AppDatabase;
import com.example.ticketmaster_mobile.data.DatabaseExecutor;
import com.example.ticketmaster_mobile.data.entity.Usuario;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.Locale;

import com.example.ticketmaster_mobile.data.remote.AuthFirebase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private TextInputLayout tilUsuario, tilPassword;
    private TextInputEditText etNombre1, etNombre2, etApellido1, etApellido2;
    private TextInputEditText etAnio, etMes, etDia, etSexo, etEntidad;
    private TextInputEditText etUsuario, etPassword;
    private Button btnGenerarCurp, btnRegister;

    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        db = AppDatabase.getInstance(this);

        // Inicializar vistas
        etNombre1 = findViewById(R.id.etNombre1);
        etNombre2 = findViewById(R.id.etNombre2);
        etApellido1 = findViewById(R.id.etApellido1);
        etApellido2 = findViewById(R.id.etApellido2);
        etAnio = findViewById(R.id.etAnio);
        etMes = findViewById(R.id.etMes);
        etDia = findViewById(R.id.etDia);
        etSexo = findViewById(R.id.etSexo);
        etEntidad = findViewById(R.id.etEntidad);

        tilUsuario = findViewById(R.id.tilUsuario);
        tilPassword = findViewById(R.id.tilPassword);
        etUsuario = findViewById(R.id.etUsuario);
        etPassword = findViewById(R.id.etPassword);

        btnGenerarCurp = findViewById(R.id.btnGenerarCurp);
        btnRegister = findViewById(R.id.btnRegister);

        // Botón para construir CURP automáticamente (sin cambios respecto al proyecto original)
        btnGenerarCurp.setOnClickListener(v -> {

            String apellido1 = etApellido1.getText().toString().trim();
            String apellido2 = etApellido2.getText().toString().trim();
            String nombre1 = etNombre1.getText().toString().trim();
            String anio = etAnio.getText().toString().trim();
            String mes = etMes.getText().toString().trim();
            String dia = etDia.getText().toString().trim();
            String sexo = etSexo.getText().toString().trim();
            String entidad = etEntidad.getText().toString().trim();

            if (apellido1.isEmpty() || apellido2.isEmpty() || nombre1.isEmpty()
                    || anio.isEmpty() || mes.isEmpty() || dia.isEmpty()
                    || sexo.isEmpty() || entidad.isEmpty()) {
                Toast.makeText(this, "Completa todos los datos personales antes de generar el CURP", Toast.LENGTH_SHORT).show();
                return;
            }

            String curpGenerado = ValidationsUtils.generarCurpExtendido(
                    apellido1, apellido2, nombre1, anio, mes, dia, sexo, entidad);

            if (!curpGenerado.isEmpty()) {
                etUsuario.setText(curpGenerado);
                tilUsuario.setError(null);
                Toast.makeText(this, "CURP generada correctamente", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "No se pudo generar la CURP. Verifica los datos ingresados.", Toast.LENGTH_SHORT).show();
            }
        });

        btnRegister.setOnClickListener(v -> procesarRegistro());
    }

    private void procesarRegistro() {
        String usuario = etUsuario.getText().toString().trim();
        String password = etPassword.getText().toString();

        String errorUsuario = ValidationsUtils.obtenerErrorUsuario(usuario);
        String errorPassword = ValidationsUtils.obtenerErrorPassword(password);

        boolean esValido = true;

        if (errorUsuario != null) {
            tilUsuario.setError(errorUsuario);
            esValido = false;
        } else {
            tilUsuario.setError(null);
        }

        if (errorPassword != null) {
            tilPassword.setError(errorPassword);
            esValido = false;
        } else {
            tilPassword.setError(null);
        }

        // Datos personales obligatorios para la tabla Usuario
        String nombre1 = etNombre1.getText().toString().trim();
        String nombre2 = etNombre2.getText().toString().trim();
        String apellido1 = etApellido1.getText().toString().trim();
        String apellido2 = etApellido2.getText().toString().trim();
        String anio = etAnio.getText().toString().trim();
        String mes = etMes.getText().toString().trim();
        String dia = etDia.getText().toString().trim();
        String sexoInput = etSexo.getText().toString().trim().toUpperCase(Locale.ROOT);
        String entidad = etEntidad.getText().toString().trim();

        if (apellido1.isEmpty() || nombre1.isEmpty() || anio.length() != 4 || mes.isEmpty() || dia.isEmpty()
                || entidad.isEmpty()) {
            Toast.makeText(this, "Completa nombre, apellidos, fecha de nacimiento y entidad", Toast.LENGTH_SHORT).show();
            return;
        }

        String sexoCompleto;
        if (sexoInput.equals("H")) {
            sexoCompleto = "Masculino";
        } else if (sexoInput.equals("M")) {
            sexoCompleto = "Femenino";
        } else {
            Toast.makeText(this, "El campo Sexo debe ser H (Masculino) o M (Femenino)", Toast.LENGTH_SHORT).show();
            return;
        }

        String fechaNacimiento;
        try {
            String mesRelleno = mes.length() == 1 ? "0" + mes : mes;
            String diaRelleno = dia.length() == 1 ? "0" + dia : dia;
            fechaNacimiento = anio + "-" + mesRelleno + "-" + diaRelleno;
        } catch (Exception e) {
            Toast.makeText(this, "La fecha de nacimiento no es válida", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!esValido) {
            return;
        }

        String nombreCompleto = nombre2.isEmpty() ? nombre1 : nombre1 + " " + nombre2;
        String fechaRegistro = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new java.util.Date());

        btnRegister.setEnabled(false);

        // 1) Crear la cuenta en Firebase Auth (la contraseña ya no se guarda en la app)
        FirebaseAuth auth = FirebaseAuth.getInstance();
        auth.createUserWithEmailAndPassword(AuthFirebase.correoDe(usuario), password)
                .addOnSuccessListener(resultadoAuth -> {
                    String uid = resultadoAuth.getUser().getUid();

                    // 2) Guardar el perfil en Firestore: usuarios/{uid}
                    Map<String, Object> perfil = new HashMap<>();
                    perfil.put("nombre", nombreCompleto);
                    perfil.put("apellidoPaterno", apellido1);
                    perfil.put("apellidoMaterno", apellido2.isEmpty() ? "" : apellido2);
                    perfil.put("curp", usuario);
                    perfil.put("fechaNacimiento", fechaNacimiento);
                    perfil.put("estado", entidad);
                    perfil.put("sexo", sexoCompleto);
                    perfil.put("fechaRegistro", fechaRegistro);

                    FirebaseFirestore.getInstance().collection("usuarios").document(uid)
                            .set(perfil)
                            .addOnSuccessListener(v -> guardarCopiaLocalYTerminar(
                                    nombreCompleto, apellido1, apellido2, usuario,
                                    fechaNacimiento, entidad, sexoCompleto, fechaRegistro))
                            .addOnFailureListener(e -> {
                                // El perfil no se pudo guardar: se revierte la cuenta para poder reintentar
                                resultadoAuth.getUser().delete();
                                btnRegister.setEnabled(true);
                                Toast.makeText(this, AuthFirebase.mensaje(e), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    btnRegister.setEnabled(true);
                    if (e instanceof com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                        tilUsuario.setError("Ya existe una cuenta registrada con esa CURP");
                    } else {
                        Toast.makeText(this, AuthFirebase.mensaje(e), Toast.LENGTH_LONG).show();
                    }
                });
    }

    /** Puente temporal: copia local en Room para que el resto de la app (idUsuario entero) siga funcionando. */
    private void guardarCopiaLocalYTerminar(String nombreCompleto, String apellido1, String apellido2,
                                            String curp, String fechaNacimiento, String entidad,
                                            String sexoCompleto, String fechaRegistro) {
        DatabaseExecutor.runAsync(() -> {
            if (db.usuarioDao().contarPorCurp(curp) > 0) {
                return 1L; // ya existía la copia local
            }
            Usuario nuevoUsuario = new Usuario();
            nuevoUsuario.nombre = nombreCompleto;
            nuevoUsuario.apellidoPaterno = apellido1;
            nuevoUsuario.apellidoMaterno = apellido2.isEmpty() ? "" : apellido2;
            nuevoUsuario.curp = curp;
            nuevoUsuario.fechaNacimiento = fechaNacimiento;
            nuevoUsuario.estado = entidad;
            nuevoUsuario.sexo = sexoCompleto;
            nuevoUsuario.fechaRegistro = fechaRegistro;
            nuevoUsuario.passwordHash = ""; // la contraseña vive en Firebase Auth
            return db.usuarioDao().insertar(nuevoUsuario);
        }, idGenerado -> {
            btnRegister.setEnabled(true);
            // Tras registrarse, Firebase deja la sesión abierta; se cierra para entrar por Login
            FirebaseAuth.getInstance().signOut();

            if (idGenerado == null || idGenerado <= 0) {
                Toast.makeText(this, "Ocurrió un error al registrar la cuenta. Intenta de nuevo.", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "¡Registro exitoso!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            intent.putExtra("EXTRA_CURP_USUARIO", curp);
            startActivity(intent);
            finish();
        });
    }
}
