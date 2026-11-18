package com.example.ticketmaster_mobile.ui.tickets;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ticketmaster_mobile.R;
import com.example.ticketmaster_mobile.data.AppDatabase;
import com.example.ticketmaster_mobile.data.DatabaseExecutor;
import com.example.ticketmaster_mobile.data.FechaUtils;
import com.example.ticketmaster_mobile.data.entity.Establecimiento;
import com.example.ticketmaster_mobile.data.pojo.ZonaDisponibilidad;
import com.example.ticketmaster_mobile.session.SessionManager;
import com.example.ticketmaster_mobile.ui.tickets.adapter.ZonaCantidadAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compra por zona/sección y cantidad (TEATRO y MUSEO): el precio depende de la sección elegida
 * (luneta, palco...) y el aforo de cada una limita los boletos disponibles. El total de boletos
 * respeta el máximo por usuario del establecimiento (10 teatro, 5 museo).
 */
public class ZonaCantidadActivity extends AppCompatActivity {

    private TextView tvEvento, tvLimite, tvTotal;
    private Button btnContinuar;
    private ZonaCantidadAdapter adapter;

    private int idFuncion;
    private String nombreEvento;
    private String fechaHora;

    private List<ZonaDisponibilidad> zonasActuales = new ArrayList<>();
    private Map<Integer, Integer> cantidadesActuales;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zona_cantidad);

        Toolbar toolbar = findViewById(R.id.toolbarZonas);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        idFuncion = getIntent().getIntExtra(SeatSelectionActivity.EXTRA_ID_FUNCION, -1);
        int idEstablecimiento = getIntent().getIntExtra(SeatSelectionActivity.EXTRA_ID_ESTABLECIMIENTO, -1);
        nombreEvento = getIntent().getStringExtra(SeatSelectionActivity.EXTRA_NOMBRE_EVENTO);
        fechaHora = getIntent().getStringExtra(SeatSelectionActivity.EXTRA_FECHA_HORA);

        tvEvento = findViewById(R.id.tvZonaEvento);
        tvLimite = findViewById(R.id.tvZonaLimite);
        tvTotal = findViewById(R.id.tvZonaTotal);
        btnContinuar = findViewById(R.id.btnZonaContinuar);
        RecyclerView rv = findViewById(R.id.rvZonas);

        tvEvento.setText(nombreEvento + " — " + FechaUtils.fechaHoraLegible(fechaHora));
        tvTotal.setText("0 boletos · Total: $0.00");

        adapter = new ZonaCantidadAdapter(this::actualizarResumen);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        AppDatabase db = AppDatabase.getInstance(this);
        int idUsuario = new SessionManager(this).getIdUsuarioActivo();

        // Límite por usuario: máximo del lugar menos lo que ya compró para esta función
        DatabaseExecutor.runAsync(() -> {
            Establecimiento lugar = db.establecimientoDao().buscarPorId(idEstablecimiento);
            int yaTiene = db.boletoDao().contarActivosDeUsuarioEnFuncion(idUsuario, idFuncion);
            return new int[]{lugar != null ? lugar.maxBoletosPorUsuario : 0, yaTiene};
        }, r -> {
            int max = r[0];
            int restantes = Math.max(0, max - r[1]);
            adapter.setMaxTotal(restantes);
            tvLimite.setText(restantes == 0
                    ? "Ya tienes el máximo de " + max + " boletos para esta función."
                    : "Máximo " + max + " boletos por usuario"
                    + (r[1] > 0 ? " (ya tienes " + r[1] + ", puedes comprar " + restantes + " más)" : ""));
        });

        db.seccionZonaDao().observarDisponibilidad(idEstablecimiento, idFuncion)
                .observe(this, adapter::submitList);

        btnContinuar.setOnClickListener(v -> continuarAlPago());
    }

    private void actualizarResumen(List<ZonaDisponibilidad> zonas, Map<Integer, Integer> cantidades) {
        zonasActuales = zonas;
        cantidadesActuales = cantidades;
        int total = 0;
        double monto = 0;
        for (ZonaDisponibilidad z : zonas) {
            Integer q = cantidades.get(z.idSeccionZona);
            if (q != null) {
                total += q;
                monto += q * z.precioBase;
            }
        }
        tvTotal.setText(String.format(Locale.getDefault(), "%d boleto%s · Total: $%,.2f",
                total, total == 1 ? "" : "s", monto));
        btnContinuar.setEnabled(total > 0);
    }

    private void continuarAlPago() {
        List<Integer> zonasIds = new ArrayList<>();
        List<Double> precios = new ArrayList<>();
        List<String> etiquetas = new ArrayList<>();
        double total = 0;

        for (ZonaDisponibilidad z : zonasActuales) {
            Integer q = cantidadesActuales != null ? cantidadesActuales.get(z.idSeccionZona) : null;
            if (q == null) continue;
            for (int i = 0; i < q; i++) {
                zonasIds.add(z.idSeccionZona);
                precios.add(z.precioBase);
                etiquetas.add(z.nombreZona);
                total += z.precioBase;
            }
        }
        if (zonasIds.isEmpty()) return;

        int n = zonasIds.size();
        int[] idsZonas = new int[n];
        int[] idsAsientos = new int[n];
        double[] preciosArr = new double[n];
        for (int i = 0; i < n; i++) {
            idsZonas[i] = zonasIds.get(i);
            idsAsientos[i] = -1; // sin asiento numerado
            preciosArr[i] = precios.get(i);
        }

        Intent intent = new Intent(this, PaymentActivity.class);
        intent.putExtra(PaymentActivity.EXTRA_ID_FUNCION, idFuncion);
        intent.putExtra(PaymentActivity.EXTRA_NOMBRE_EVENTO, nombreEvento);
        intent.putExtra(PaymentActivity.EXTRA_FECHA_HORA, fechaHora);
        intent.putExtra(PaymentActivity.EXTRA_IDS_ASIENTOS, idsAsientos);
        intent.putExtra(PaymentActivity.EXTRA_IDS_ZONAS, idsZonas);
        intent.putExtra(PaymentActivity.EXTRA_PRECIOS, preciosArr);
        intent.putExtra(PaymentActivity.EXTRA_ETIQUETAS, etiquetas.toArray(new String[0]));
        intent.putExtra(PaymentActivity.EXTRA_TOTAL, total);
        startActivity(intent);
    }
}
