package com.example.ticketmaster_mobile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ticketmaster_mobile.data.AppDatabase;
import com.example.ticketmaster_mobile.data.DatabaseExecutor;
import com.example.ticketmaster_mobile.data.entity.Orden;
import com.example.ticketmaster_mobile.data.entity.Usuario;
import com.example.ticketmaster_mobile.data.pojo.OrdenHistorialItem;
import com.example.ticketmaster_mobile.session.SessionManager;
import com.example.ticketmaster_mobile.ui.tickets.adapter.OrdenHistorialAdapter;
import com.example.ticketmaster_mobile.waha.TelefonoWhatsapp;
import com.example.ticketmaster_mobile.waha.WahaClient;
import com.example.ticketmaster_mobile.waha.WahaConfigProvider;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private TextView tvWhatsapp, tvNombre, tvCurp, tvEdad, tvSexo, tvEstado, tvFechaRegistro, tvSinCompras;
    private Button btnVincularWhatsapp, btnProbarWhatsapp, btnLogout;
    private RecyclerView rvHistorialOrdenes;
    private String chatIdWhatsapp;

    private AppDatabase db;
    private SessionManager sessionManager;
    private OrdenHistorialAdapter ordenAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        tvNombre = view.findViewById(R.id.tvProfileNombre);
        tvCurp = view.findViewById(R.id.tvProfileCurp);
        tvEdad = view.findViewById(R.id.tvProfileEdad);
        tvSexo = view.findViewById(R.id.tvProfileSexo);
        tvEstado = view.findViewById(R.id.tvProfileEstado);
        tvFechaRegistro = view.findViewById(R.id.tvProfileFechaRegistro);
        tvSinCompras = view.findViewById(R.id.tvSinCompras);
        tvWhatsapp = view.findViewById(R.id.tvProfileWhatsapp);
        btnVincularWhatsapp = view.findViewById(R.id.btnVincularWhatsapp);
        btnProbarWhatsapp = view.findViewById(R.id.btnProbarWhatsapp);
        btnLogout = view.findViewById(R.id.btnLogout);
        rvHistorialOrdenes = view.findViewById(R.id.rvHistorialOrdenes);

        if (getContext() == null) return view;

        db = AppDatabase.getInstance(requireContext());
        sessionManager = new SessionManager(requireContext());

        ordenAdapter = new OrdenHistorialAdapter();
        rvHistorialOrdenes.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvHistorialOrdenes.setAdapter(ordenAdapter);

        cargarDatosPersonales();
        observarHistorialDeCompras();

        com.example.ticketmaster_mobile.data.remote.OrdenesFirestore.sincronizar(
                requireContext(), sessionManager.getIdUsuarioActivo());

        btnVincularWhatsapp.setOnClickListener(v -> VincularWhatsappDialog.mostrar(
                requireContext(), sessionManager.getIdUsuarioActivo(), chatIdWhatsapp,
                this::cargarDatosPersonales));
        btnProbarWhatsapp.setOnClickListener(v -> probarConexionWhatsapp());

        btnLogout.setOnClickListener(v -> cerrarSesion());

        return view;
    }

    private void cargarDatosPersonales() {
        int idUsuario = sessionManager.getIdUsuarioActivo();
        DatabaseExecutor.runAsync(
                () -> db.usuarioDao().buscarPorId(idUsuario),
                usuario -> {
                    if (usuario == null || getContext() == null) return;
                    pintarDatosPersonales(usuario);
                }
        );
    }

    private void pintarDatosPersonales(Usuario usuario) {
        String apellidos = (usuario.apellidoPaterno + " " + usuario.apellidoMaterno).trim();
        tvNombre.setText((usuario.nombre + " " + apellidos).trim());
        tvCurp.setText("CURP: " + usuario.curp);
        tvSexo.setText("Sexo: " + usuario.sexo);
        tvEstado.setText("Estado: " + usuario.estado);
        tvFechaRegistro.setText("Cuenta creada el " + usuario.fechaRegistro);

        chatIdWhatsapp = usuario.whatsappChatId;
        boolean vinculado = chatIdWhatsapp != null && !chatIdWhatsapp.isEmpty();
        tvWhatsapp.setText(vinculado
                ? "Número vinculado: " + TelefonoWhatsapp.formatear(chatIdWhatsapp)
                : getString(R.string.whatsapp_sin_vincular));
        btnVincularWhatsapp.setText(vinculado ? R.string.whatsapp_cambiar : R.string.whatsapp_vincular);

        int edad = calcularEdad(usuario.fechaNacimiento);
        tvEdad.setText(edad >= 0 ? ("Edad: " + edad + " años") : "Edad: no disponible");
    }

    /** Calcula la edad en años a partir de una fecha "yyyy-MM-dd", comparando con la fecha actual. */
    private int calcularEdad(String fechaNacimientoIso) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Calendar nacimiento = Calendar.getInstance();
            nacimiento.setTime(sdf.parse(fechaNacimientoIso));

            Calendar hoy = Calendar.getInstance();

            int edad = hoy.get(Calendar.YEAR) - nacimiento.get(Calendar.YEAR);
            // Si en el año actual aún no ha llegado el mes/día de nacimiento, restamos un año
            if (hoy.get(Calendar.DAY_OF_YEAR) < nacimiento.get(Calendar.DAY_OF_YEAR)) {
                edad--;
            }
            return edad;
        } catch (ParseException | NullPointerException e) {
            return -1;
        }
    }

    private void observarHistorialDeCompras() {
        int idUsuario = sessionManager.getIdUsuarioActivo();
        db.ordenDao().observarPorUsuario(idUsuario).observe(getViewLifecycleOwner(), ordenes -> {
            if (ordenes == null || ordenes.isEmpty()) {
                tvSinCompras.setVisibility(View.VISIBLE);
                ordenAdapter.submitList(new ArrayList<>());
                return;
            }
            tvSinCompras.setVisibility(View.GONE);
            cargarDetalleDeCadaOrden(ordenes);
        });
    }


    /** Por cada Orden, trae en segundo plano el detalle de sus Boletos (evento, asiento, precio). */
    private void cargarDetalleDeCadaOrden(List<Orden> ordenes) {
        DatabaseExecutor.runAsync(() -> {
            List<OrdenHistorialItem> resultado = new ArrayList<>();
            for (Orden orden : ordenes) {
                resultado.add(new OrdenHistorialItem(orden, db.boletoDao().listarDetallePorOrden(orden.idOrden)));
            }
            return resultado;
        }, ordenAdapter::submitList);
    }

    /** Comprueba conexión, clave y sesión de WAHA y muestra el resultado en un diálogo. */
    private void probarConexionWhatsapp() {
        btnProbarWhatsapp.setEnabled(false);
        Toast.makeText(requireContext(), "Probando conexión con WAHA…", Toast.LENGTH_SHORT).show();
        DatabaseExecutor.runAsync(
                () -> new WahaClient(WahaConfigProvider.actual()).diagnosticar(null),
                dg -> {
                    btnProbarWhatsapp.setEnabled(true);
                    if (getContext() == null) return;
                    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle(dg.esOk() ? "✅ Conexión correcta" : "⚠️ Problema de conexión")
                            .setMessage(dg.mensaje)
                            .setPositiveButton("Aceptar", null)
                            .show();
                });
    }

    private void cerrarSesion() {
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
        sessionManager.cerrarSesion();
        if (getActivity() == null) return;

        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        getActivity().finish();
    }
}
