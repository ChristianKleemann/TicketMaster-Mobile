package com.example.ticketmaster_mobile.ui.tickets.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ticketmaster_mobile.R;
import com.example.ticketmaster_mobile.data.entity.Establecimiento;

import com.example.ticketmaster_mobile.data.FechaUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EstablecimientoAdapter extends RecyclerView.Adapter<EstablecimientoAdapter.VH> {

    public interface OnEstablecimientoClickListener {
        void onClick(Establecimiento establecimiento);
    }

    private final List<Establecimiento> items = new ArrayList<>();
    private final OnEstablecimientoClickListener listener;

    public EstablecimientoAdapter(OnEstablecimientoClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Establecimiento> nuevosItems) {
        items.clear();
        if (nuevosItems != null) items.addAll(nuevosItems);
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_establecimiento, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Establecimiento e = items.get(position);
        holder.tipo.setText(e.tipoEstablecimiento.toUpperCase());
        holder.nombre.setText(e.nombre);
        holder.ubicacion.setText(e.ubicacion);
        holder.horario.setText("Servicio: " + FechaUtils.describirDias(e.diasLaborales)
                + " · " + e.horaApertura + "–" + e.horaCierre);
        String unidad = "Cine".equals(e.tipoEstablecimiento) ? "asientos por función"
                : "Museo".equals(e.tipoEstablecimiento) ? "lugares por franja de entrada" : "lugares";
        holder.capacidad.setText(String.format(Locale.getDefault(),
                "Capacidad: %,d %s · Máx. %d boletos por usuario", e.capacidad, unidad, e.maxBoletosPorUsuario));
        holder.itemView.setOnClickListener(v -> listener.onClick(e));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tipo, nombre, ubicacion, horario, capacidad;

        VH(@NonNull View itemView) {
            super(itemView);
            tipo = itemView.findViewById(R.id.tvEstablecimientoTipo);
            nombre = itemView.findViewById(R.id.tvEstablecimientoNombre);
            ubicacion = itemView.findViewById(R.id.tvEstablecimientoUbicacion);
            horario = itemView.findViewById(R.id.tvEstablecimientoHorario);
            capacidad = itemView.findViewById(R.id.tvEstablecimientoCapacidad);
        }
    }
}
