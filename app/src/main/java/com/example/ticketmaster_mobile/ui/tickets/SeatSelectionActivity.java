package com.example.ticketmaster_mobile.ui.tickets;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ticketmaster_mobile.R;
import com.example.ticketmaster_mobile.data.AppDatabase;
import com.example.ticketmaster_mobile.data.DatabaseExecutor;
import com.example.ticketmaster_mobile.data.FechaUtils;
import com.example.ticketmaster_mobile.data.ServicioCine;
import com.example.ticketmaster_mobile.data.entity.Establecimiento;
import com.example.ticketmaster_mobile.data.entity.SeccionZona;
import com.example.ticketmaster_mobile.data.pojo.AsientoEstado;
import com.example.ticketmaster_mobile.session.SessionManager;
import com.example.ticketmaster_mobile.ui.tickets.adapter.SeatAdapter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;


/**
 * Selección de asientos de CINE. El usuario elige el tipo de servicio (tradicional, PLUUS, VIP,
 * Macro XE, Junior, 4DX, IMAX, VR, Screen X) que funciona como "tipo de asiento": cambia la
 * cuadrícula y su apariencia. Si el cine no ofrece el servicio elegido se le avisa con un mensaje.
 */
public class SeatSelectionActivity extends AppCompatActivity {

    public static final String EXTRA_ID_FUNCION = "EXTRA_ID_FUNCION";
    public static final String EXTRA_ID_ESTABLECIMIENTO = "EXTRA_ID_ESTABLECIMIENTO";
    public static final String EXTRA_NOMBRE_EVENTO = "EXTRA_NOMBRE_EVENTO";
    public static final String EXTRA_FECHA_HORA = "EXTRA_FECHA_HORA";

    private TextView tvEventoInfo, tvTotal, tvLimite, tvServicioDescripcion, tvPantalla;
    private Button btnContinuarPago;
    private ChipGroup chipGroupServicios;
    private RecyclerView rvAsientos;
    private GridLayoutManager gridManager;
    private SeatAdapter adapter;
    private AppDatabase db;

    private int idFuncion;
    private int idEstablecimiento;
    private String nombreEvento;
    private String fechaHora;

    private Establecimiento lugar;
    private final Map<String, SeccionZona> zonasOfrecidas = new HashMap<>(); // nombre servicio -> zona
    private final Map<Integer, ServicioCine> servicioPorChip = new HashMap<>();
    private int chipActualId = View.NO_ID;
    private boolean revirtiendoChip = false;

    private LiveData<List<AsientoEstado>> asientosLive;
    private List<AsientoEstado> ultimaSeleccion;

    private final Set<Integer> ocupadosRemotos = new HashSet<>();
    private List<AsientoEstado> ultimaListaLocal;
    private ListenerRegistration ventasListener;

    private static class DatosIniciales {
        Establecimiento lugar;
        List<SeccionZona> zonas;
        int yaTiene;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seat_selection);

        Toolbar toolbar = findViewById(R.id.toolbarAsientos);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        idFuncion = getIntent().getIntExtra(EXTRA_ID_FUNCION, -1);
        idEstablecimiento = getIntent().getIntExtra(EXTRA_ID_ESTABLECIMIENTO, -1);
        nombreEvento = getIntent().getStringExtra(EXTRA_NOMBRE_EVENTO);
        fechaHora = getIntent().getStringExtra(EXTRA_FECHA_HORA);

        tvEventoInfo = findViewById(R.id.tvEventoInfo);
        tvTotal = findViewById(R.id.tvTotal);
        tvLimite = findViewById(R.id.tvLimiteAsientos);
        tvServicioDescripcion = findViewById(R.id.tvServicioDescripcion);
        tvPantalla = findViewById(R.id.tvPantalla);
        btnContinuarPago = findViewById(R.id.btnContinuarPago);
        chipGroupServicios = findViewById(R.id.chipGroupServicios);
        rvAsientos = findViewById(R.id.rvAsientos);

        tvEventoInfo.setText(nombreEvento + " — " + FechaUtils.fechaHoraLegible(fechaHora));
        tvTotal.setText(getString(R.string.total_prefix) + "$0.00");

        adapter = new SeatAdapter(this::actualizarResumen);
        gridManager = new GridLayoutManager(this, 8);
        rvAsientos.setLayoutManager(gridManager);
        rvAsientos.setAdapter(adapter);

        db = AppDatabase.getInstance(this);
        btnContinuarPago.setOnClickListener(v -> continuarAlPago());

        cargarDatosIniciales();
    }

    private void cargarDatosIniciales() {
        int idUsuario = new SessionManager(this).getIdUsuarioActivo();
        DatabaseExecutor.runAsync(() -> {
            DatosIniciales d = new DatosIniciales();
            d.lugar = db.establecimientoDao().buscarPorId(idEstablecimiento);
            d.zonas = db.seccionZonaDao().listarPorEstablecimiento(idEstablecimiento);
            d.yaTiene = db.boletoDao().contarActivosDeUsuarioEnFuncion(idUsuario, idFuncion);
            return d;
        }, datos -> {
            if (datos == null || datos.lugar == null) return;
            lugar = datos.lugar;
            for (SeccionZona z : datos.zonas) zonasOfrecidas.put(z.nombreZona, z);

            int restantes = Math.max(0, lugar.maxBoletosPorUsuario - datos.yaTiene);
            adapter.setMaxSeleccion(restantes);
            tvLimite.setText(restantes == 0
                    ? "Ya tienes el máximo de " + lugar.maxBoletosPorUsuario + " boletos para esta función."
                    : "Máximo " + lugar.maxBoletosPorUsuario + " boletos por usuario"
                    + (datos.yaTiene > 0 ? " (ya tienes " + datos.yaTiene + ", puedes elegir " + restantes + " más)" : ""));

            construirChipsDeServicio();
        });
    }

    private void construirChipsDeServicio() {
        chipGroupServicios.setOnCheckedStateChangeListener(null);
        chipGroupServicios.removeAllViews();
        servicioPorChip.clear();

        ServicioCine inicial = null;
        int chipInicial = View.NO_ID;
        for (ServicioCine s : ServicioCine.TODOS) {
            boolean ofrecido = zonasOfrecidas.containsKey(s.nombre);
            Chip chip = new Chip(this);
            chip.setId(View.generateViewId());
            chip.setText(s.nombre);
            chip.setCheckable(true);
            chip.setAlpha(ofrecido ? 1f : 0.45f); // atenuado si el cine no lo ofrece
            servicioPorChip.put(chip.getId(), s);
            chipGroupServicios.addView(chip);
            if (ofrecido && inicial == null) {
                inicial = s;
                chipInicial = chip.getId();
            }
        }

        chipGroupServicios.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (revirtiendoChip || checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            ServicioCine elegido = servicioPorChip.get(id);
            if (elegido == null) return;
            if (!zonasOfrecidas.containsKey(elegido.nombre)) {
                avisarServicioNoDisponible(elegido);
                revirtiendoChip = true;
                if (chipActualId != View.NO_ID) group.check(chipActualId); else group.clearCheck();
                revirtiendoChip = false;
                return;
            }
            chipActualId = id;
            mostrarServicio(elegido);
        });

        if (inicial != null) {
            chipGroupServicios.check(chipInicial);
        }
    }

    private void avisarServicioNoDisponible(ServicioCine pedido) {
        StringBuilder ofrecidos = new StringBuilder();
        for (ServicioCine s : ServicioCine.TODOS) {
            if (zonasOfrecidas.containsKey(s.nombre)) {
                if (ofrecidos.length() > 0) ofrecidos.append(", ");
                ofrecidos.append(s.nombre);
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("Servicio no disponible")
                .setMessage(lugar.nombre + " no cuenta con el servicio " + pedido.nombre + ".\n\nServicios que sí ofrece: "
                        + ofrecidos + ".")
                .setPositiveButton("Entendido", null)
                .show();
    }

    /** Cambia la cuadrícula, su aspecto y la fuente de datos al servicio elegido. */
    private void mostrarServicio(ServicioCine servicio) {
        SeccionZona zona = zonasOfrecidas.get(servicio.nombre);
        if (zona == null) return;

        tvServicioDescripcion.setText(String.format(Locale.getDefault(), "%s · $%.2f por asiento · %d asientos",
                servicio.descripcion, zona.precioBase, zona.capacidad));

        tvPantalla.setText(servicio.textoPantalla);
        GradientDrawable banner = new GradientDrawable();
        banner.setColor(servicio.colorAcento);
        banner.setCornerRadii(new float[]{40, 40, 40, 40, 8, 8, 8, 8});
        tvPantalla.setBackground(banner);
        tvPantalla.setTextColor(Color.BLACK);
        // El ancho del banner refleja el tamaño de la pantalla del servicio
        int anchoPantalla = getResources().getDisplayMetrics().widthPixels;
        android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) tvPantalla.getLayoutParams();
        int margen = (int) ((anchoPantalla * (1f - servicio.anchoPantalla)) / 2f) + dp(16);
        lp.leftMargin = margen;
        lp.rightMargin = margen;
        tvPantalla.setLayoutParams(lp);

        gridManager.setSpanCount(servicio.columnas);
        adapter.setServicio(servicio);

        if (asientosLive != null) asientosLive.removeObservers(this);
        asientosLive = db.asientoDao().observarAsientosDeZona(zona.idSeccionZona, idFuncion);
        asientosLive.observe(this, lista -> {
            ultimaListaLocal = lista;
            pintarAsientos();
        });
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void actualizarResumen(List<AsientoEstado> seleccionados) {
        double total = 0.0;
        for (AsientoEstado a : seleccionados) total += a.precioBase;
        tvTotal.setText(String.format(Locale.getDefault(), "%s$%.2f (%d)", getString(R.string.total_prefix),
                total, seleccionados.size()));
        btnContinuarPago.setEnabled(!seleccionados.isEmpty());
        this.ultimaSeleccion = seleccionados;
    }

    private void continuarAlPago() {
        if (ultimaSeleccion == null || ultimaSeleccion.isEmpty()) return;

        int n = ultimaSeleccion.size();
        int[] idsAsientos = new int[n];
        int[] idsZonas = new int[n];
        double[] precios = new double[n];
        String[] etiquetas = new String[n];
        double total = 0.0;

        for (int i = 0; i < n; i++) {
            AsientoEstado a = ultimaSeleccion.get(i);
            idsAsientos[i] = a.idAsiento;
            idsZonas[i] = a.idSeccionZona;
            precios[i] = a.precioBase;
            etiquetas[i] = "Asiento " + a.codigoAsiento + " (" + a.nombreZona + ")";
            total += a.precioBase;
        }

        Intent intent = new Intent(this, PaymentActivity.class);
        intent.putExtra(PaymentActivity.EXTRA_ID_FUNCION, idFuncion);
        intent.putExtra(PaymentActivity.EXTRA_NOMBRE_EVENTO, nombreEvento);
        intent.putExtra(PaymentActivity.EXTRA_FECHA_HORA, fechaHora);
        intent.putExtra(PaymentActivity.EXTRA_IDS_ASIENTOS, idsAsientos);
        intent.putExtra(PaymentActivity.EXTRA_IDS_ZONAS, idsZonas);
        intent.putExtra(PaymentActivity.EXTRA_PRECIOS, precios);
        intent.putExtra(PaymentActivity.EXTRA_ETIQUETAS, etiquetas);
        intent.putExtra(PaymentActivity.EXTRA_TOTAL, total);
        startActivity(intent);
    }

    /** Escucha en Firestore los asientos vendidos de esta función (por cualquier teléfono). */
    private void escucharVentasRemotas() {
        ventasListener = FirebaseFirestore.getInstance().collection("ventas")
                .whereEqualTo("idFuncion", idFuncion)
                .addSnapshotListener((snap, error) -> {
                    if (snap == null) return;
                    ocupadosRemotos.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Long a = d.getLong("idAsiento");
                        if (a != null) ocupadosRemotos.add(a.intValue());
                    }
                    pintarAsientos();
                });
    }

    private void pintarAsientos() {
        if (ultimaListaLocal == null) return;
        for (AsientoEstado a : ultimaListaLocal) {
            if (a.ocupado == 0 && ocupadosRemotos.contains(a.idAsiento)) a.ocupado = 1;
        }
        adapter.submitList(ultimaListaLocal);
    }

    @Override
    protected void onDestroy() {
        if (ventasListener != null) ventasListener.remove();
        super.onDestroy();
    }
}
