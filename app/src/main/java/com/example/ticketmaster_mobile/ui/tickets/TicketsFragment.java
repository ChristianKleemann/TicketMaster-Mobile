package com.example.ticketmaster_mobile.ui.tickets;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ticketmaster_mobile.R;
import com.example.ticketmaster_mobile.data.AppDatabase;
import com.example.ticketmaster_mobile.data.entity.Establecimiento;
import com.example.ticketmaster_mobile.ui.tickets.adapter.EstablecimientoAdapter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

/**
 * Pantalla "Adquirir Boletos": muestra el catálogo de Establecimientos, filtrable
 * por TipoEstablecimiento mediante Chips. Al tocar un establecimiento se navega
 * a FuncionesActivity para ver sus funciones disponibles.
 */
public class TicketsFragment extends Fragment {

    private ChipGroup chipGroupFiltro;
    private RecyclerView rvEstablecimientos;
    private TextView tvSinEstablecimientos;

    private AppDatabase db;
    private EstablecimientoAdapter adapter;
    private LiveData<List<Establecimiento>> liveDataActual;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tickets, container, false);

        chipGroupFiltro = view.findViewById(R.id.chipGroupFiltro);
        rvEstablecimientos = view.findViewById(R.id.rvEstablecimientos);
        tvSinEstablecimientos = view.findViewById(R.id.tvSinEstablecimientos);

        if (getContext() == null) return view;

        db = AppDatabase.getInstance(requireContext());

        adapter = new EstablecimientoAdapter(this::abrirFunciones);
        rvEstablecimientos.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvEstablecimientos.setAdapter(adapter);

        cargarEstablecimientos("Todos");

        chipGroupFiltro.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            String tipo = tipoDesdeChipId(id);
            cargarEstablecimientos(tipo);
        });

        return view;
    }

    private String tipoDesdeChipId(int chipId) {
        if (chipId == R.id.chipCine) return "Cine";
        if (chipId == R.id.chipTeatro) return "Teatro";
        if (chipId == R.id.chipMuseo) return "Museo";
        return "Todos";
    }

    /** Cambia la fuente de LiveData observada según el filtro elegido. */
    private void cargarEstablecimientos(String tipo) {
        if (liveDataActual != null) {
            liveDataActual.removeObservers(getViewLifecycleOwner());
        }
        liveDataActual = db.establecimientoDao().observarPorTipo(tipo);
        liveDataActual.observe(getViewLifecycleOwner(), establecimientos -> {
            adapter.submitList(establecimientos);
            boolean vacio = establecimientos == null || establecimientos.isEmpty();
            tvSinEstablecimientos.setVisibility(vacio ? View.VISIBLE : View.GONE);
        });
    }

    private void abrirFunciones(Establecimiento establecimiento) {
        if (getContext() == null) return;
        Intent intent = new Intent(getContext(), FuncionesActivity.class);
        intent.putExtra(FuncionesActivity.EXTRA_ID_ESTABLECIMIENTO, establecimiento.idEstablecimiento);
        intent.putExtra(FuncionesActivity.EXTRA_NOMBRE_ESTABLECIMIENTO, establecimiento.nombre);
        startActivity(intent);
    }
}
