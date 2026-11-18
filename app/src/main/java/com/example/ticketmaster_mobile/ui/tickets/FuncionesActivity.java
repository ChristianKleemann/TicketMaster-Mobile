package com.example.ticketmaster_mobile.ui.tickets;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ticketmaster_mobile.R;
import com.example.ticketmaster_mobile.data.AppDatabase;
import com.example.ticketmaster_mobile.data.DatabaseExecutor;
import com.example.ticketmaster_mobile.data.FechaUtils;
import com.example.ticketmaster_mobile.data.ReglasNegocio;
import com.example.ticketmaster_mobile.data.entity.DiaFestivo;
import com.example.ticketmaster_mobile.data.entity.Establecimiento;
import com.example.ticketmaster_mobile.data.entity.Funcion;
import com.example.ticketmaster_mobile.ui.tickets.adapter.FuncionAdapter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Funciones de un establecimiento. Muestra su horario de servicio, capacidad, reglas de acceso y
 * días festivos; permite filtrar por fecha y bloquea la compra cuando hoy no es día laborable.
 */
public class FuncionesActivity extends AppCompatActivity {

    public static final String EXTRA_ID_ESTABLECIMIENTO = "EXTRA_ID_ESTABLECIMIENTO";
    public static final String EXTRA_NOMBRE_ESTABLECIMIENTO = "EXTRA_NOMBRE_ESTABLECIMIENTO";

    private TextView tvNombreEstablecimiento, tvSinFunciones, tvInfoHorario, tvInfoCapacidad,
            tvAvisoDiaNoLaboral, tvToggleInfo, tvInfoVestimenta, tvInfoRestricciones, tvInfoFestivos;
    private View llInfoExtra;
    private RecyclerView rvFunciones;
    private ChipGroup chipGroupFechas;
    private FuncionAdapter adapter;

    private Establecimiento lugar;
    /** Motivo por el que hoy no se pueden comprar boletos; null si hoy sí hay venta. */
    private String motivoHoyNoDisponible;

    private List<Funcion> todasLasFunciones = new ArrayList<>();
    private final Map<Integer, String> fechaPorChip = new HashMap<>();
    private String fechaSeleccionada;

    /** Resultado de cargar la información del lugar en segundo plano. */
    private static class InfoLugar {
        Establecimiento lugar;
        List<DiaFestivo> festivos;
        String festivoHoy;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_funciones);

        Toolbar toolbar = findViewById(R.id.toolbarFunciones);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        tvNombreEstablecimiento = findViewById(R.id.tvNombreEstablecimiento);
        tvSinFunciones = findViewById(R.id.tvSinFunciones);
        tvInfoHorario = findViewById(R.id.tvInfoHorario);
        tvInfoCapacidad = findViewById(R.id.tvInfoCapacidad);
        tvAvisoDiaNoLaboral = findViewById(R.id.tvAvisoDiaNoLaboral);
        tvToggleInfo = findViewById(R.id.tvToggleInfo);
        tvInfoVestimenta = findViewById(R.id.tvInfoVestimenta);
        tvInfoRestricciones = findViewById(R.id.tvInfoRestricciones);
        tvInfoFestivos = findViewById(R.id.tvInfoFestivos);
        llInfoExtra = findViewById(R.id.llInfoExtra);
        chipGroupFechas = findViewById(R.id.chipGroupFechas);
        rvFunciones = findViewById(R.id.rvFunciones);

        int idEstablecimiento = getIntent().getIntExtra(EXTRA_ID_ESTABLECIMIENTO, -1);
        tvNombreEstablecimiento.setText(getIntent().getStringExtra(EXTRA_NOMBRE_ESTABLECIMIENTO));

        tvToggleInfo.setOnClickListener(v -> {
            boolean abrir = llInfoExtra.getVisibility() != View.VISIBLE;
            llInfoExtra.setVisibility(abrir ? View.VISIBLE : View.GONE);
            tvToggleInfo.setText(abrir ? "Ocultar reglas de acceso y días festivos ▴"
                    : "Ver reglas de acceso y días festivos ▾");
        });

        adapter = new FuncionAdapter(this::alElegirFuncion);
        rvFunciones.setLayoutManager(new LinearLayoutManager(this));
        rvFunciones.setAdapter(adapter);

        cargarInfoDelLugar(idEstablecimiento);

        AppDatabase db = AppDatabase.getInstance(this);
        String ahora = FechaUtils.fechaHoraIso(new Date());
        db.funcionDao().observarFuturasPorEstablecimiento(idEstablecimiento, ahora).observe(this, funciones -> {
            todasLasFunciones = funciones != null ? funciones : new ArrayList<>();
            reconstruirChipsDeFecha();
        });
    }

    private void cargarInfoDelLugar(int idEstablecimiento) {
        AppDatabase db = AppDatabase.getInstance(this);
        String hoy = FechaUtils.fechaIso(new Date());
        DatabaseExecutor.runAsync(() -> {
            InfoLugar info = new InfoLugar();
            info.lugar = db.establecimientoDao().buscarPorId(idEstablecimiento);
            info.festivos = db.diaFestivoDao().listarDesde(idEstablecimiento, hoy);
            DiaFestivo festivoHoy = db.diaFestivoDao().buscarPorFecha(idEstablecimiento, hoy);
            info.festivoHoy = festivoHoy != null ? festivoHoy.descripcion : null;
            return info;
        }, this::pintarInfoDelLugar);
    }

    private void pintarInfoDelLugar(InfoLugar info) {
        if (info == null || info.lugar == null) return;
        lugar = info.lugar;
        adapter.setTipoEstablecimiento(lugar.tipoEstablecimiento);

        tvInfoHorario.setText("Servicio: " + FechaUtils.describirDias(lugar.diasLaborales)
                + " · " + lugar.horaApertura + "–" + lugar.horaCierre);
        String unidad = "Cine".equals(lugar.tipoEstablecimiento) ? "asientos por función"
                : "Museo".equals(lugar.tipoEstablecimiento) ? "lugares por franja de entrada" : "lugares";
        tvInfoCapacidad.setText(String.format(Locale.getDefault(),
                "Capacidad: %,d %s · Máx. %d boletos por usuario", lugar.capacidad, unidad, lugar.maxBoletosPorUsuario));

        // Reglas de acceso (según el tipo de establecimiento)
        if (lugar.vestimenta.isEmpty()) {
            tvInfoVestimenta.setVisibility(View.GONE);
        } else {
            tvInfoVestimenta.setText("Vestimenta: " + lugar.vestimenta);
        }
        tvInfoRestricciones.setText("Restricciones de acceso: " + lugar.restricciones);

        if (info.festivos == null || info.festivos.isEmpty()) {
            tvInfoFestivos.setText("Días festivos: sin festivos próximos registrados.");
        } else {
            StringBuilder sb = new StringBuilder("Próximos días festivos (sin servicio): ");
            int mostrados = Math.min(6, info.festivos.size());
            for (int i = 0; i < mostrados; i++) {
                if (i > 0) sb.append("; ");
                sb.append(FechaUtils.fechaCorta(info.festivos.get(i).fecha))
                        .append(" (").append(info.festivos.get(i).descripcion).append(")");
            }
            if (info.festivos.size() > mostrados) sb.append("…");
            tvInfoFestivos.setText(sb.toString());
        }

        // ¿Hoy se pueden adquirir boletos?
        motivoHoyNoDisponible = ReglasNegocio.VALIDAR_DIA_DE_COMPRA
                ? ReglasNegocio.motivoDiaNoLaboral(lugar.diasLaborales, FechaUtils.fechaIso(new Date()),
                info.festivoHoy, lugar.nombre)
                : null;
        if (motivoHoyNoDisponible != null) {
            tvAvisoDiaNoLaboral.setText("Hoy no hay venta de boletos. " + motivoHoyNoDisponible);
            tvAvisoDiaNoLaboral.setVisibility(View.VISIBLE);
        } else {
            tvAvisoDiaNoLaboral.setVisibility(View.GONE);
        }
    }

    /** Crea un chip por cada fecha con funciones y muestra las del día elegido. */
    private void reconstruirChipsDeFecha() {
        Set<String> fechas = new LinkedHashSet<>();
        for (Funcion f : todasLasFunciones) fechas.add(f.fechaHora.substring(0, 10));

        chipGroupFechas.setOnCheckedStateChangeListener(null);
        chipGroupFechas.removeAllViews();
        fechaPorChip.clear();

        if (fechas.isEmpty()) {
            tvSinFunciones.setVisibility(View.VISIBLE);
            adapter.submitList(new ArrayList<>());
            return;
        }
        tvSinFunciones.setVisibility(View.GONE);

        if (fechaSeleccionada == null || !fechas.contains(fechaSeleccionada)) {
            fechaSeleccionada = fechas.iterator().next();
        }

        for (String fecha : fechas) {
            Chip chip = new Chip(this);
            chip.setId(View.generateViewId());
            chip.setText(FechaUtils.fechaCorta(fecha));
            chip.setCheckable(true);
            chip.setChecked(fecha.equals(fechaSeleccionada));
            fechaPorChip.put(chip.getId(), fecha);
            chipGroupFechas.addView(chip);
        }

        chipGroupFechas.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            String fecha = fechaPorChip.get(checkedIds.get(0));
            if (fecha != null) {
                fechaSeleccionada = fecha;
                mostrarFuncionesDelDia();
            }
        });
        mostrarFuncionesDelDia();
    }

    private void mostrarFuncionesDelDia() {
        List<Funcion> delDia = new ArrayList<>();
        for (Funcion f : todasLasFunciones) {
            if (f.fechaHora.startsWith(fechaSeleccionada)) delDia.add(f);
        }
        adapter.submitList(delDia);
    }

    private void alElegirFuncion(Funcion funcion) {
        if (lugar == null) return; // la información del lugar aún no termina de cargar

        if (motivoHoyNoDisponible != null) {
            new AlertDialog.Builder(this)
                    .setTitle("Hoy no hay venta de boletos")
                    .setMessage(motivoHoyNoDisponible + "\n\nVuelve en un día de servicio para adquirir tus boletos.")
                    .setPositiveButton("Entendido", null)
                    .show();
            return;
        }

        boolean esCine = "Cine".equals(lugar.tipoEstablecimiento);
        Intent intent = new Intent(this, esCine ? SeatSelectionActivity.class : ZonaCantidadActivity.class);
        intent.putExtra(SeatSelectionActivity.EXTRA_ID_FUNCION, funcion.idFuncion);
        intent.putExtra(SeatSelectionActivity.EXTRA_ID_ESTABLECIMIENTO, funcion.idEstablecimiento);
        intent.putExtra(SeatSelectionActivity.EXTRA_NOMBRE_EVENTO, funcion.nombreEvento);
        intent.putExtra(SeatSelectionActivity.EXTRA_FECHA_HORA, funcion.fechaHora);
        startActivity(intent);
    }
}
