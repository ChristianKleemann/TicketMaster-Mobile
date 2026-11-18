package com.example.ticketmaster_mobile.ui.tickets.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ticketmaster_mobile.R;
import com.example.ticketmaster_mobile.data.ClasificacionCine;
import com.example.ticketmaster_mobile.data.FechaUtils;
import com.example.ticketmaster_mobile.data.entity.Funcion;

import java.util.ArrayList;
import java.util.List;

public class FuncionAdapter extends RecyclerView.Adapter<FuncionAdapter.VH> {

    public interface OnFuncionClickListener {
        void onClick(Funcion funcion);
    }

    private final List<Funcion> items = new ArrayList<>();
    private final OnFuncionClickListener listener;
    private String tipoEstablecimiento = "";

    public FuncionAdapter(OnFuncionClickListener listener) {
        this.listener = listener;
    }

    /** Permite adaptar los textos: en museos la hora de la función es el horario de entrada. */
    public void setTipoEstablecimiento(String tipo) {
        this.tipoEstablecimiento = tipo != null ? tipo : "";
        notifyDataSetChanged();
    }

    public void submitList(List<Funcion> nuevosItems) {
        items.clear();
        if (nuevosItems != null) items.addAll(nuevosItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_funcion, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Funcion f = items.get(position);
        holder.nombreEvento.setText(f.nombreEvento);

        String fecha = FechaUtils.fechaHoraLegible(f.fechaHora);
        holder.fechaHora.setText("Museo".equals(tipoEstablecimiento) ? "Horario de entrada: " + fecha : fecha);

        holder.detalle.setText(f.detalle);
        holder.detalle.setVisibility(f.detalle.isEmpty() ? View.GONE : View.VISIBLE);

        if (f.clasificacion.isEmpty()) {
            holder.clasificacion.setVisibility(View.GONE);
        } else {
            holder.clasificacion.setText(f.clasificacion);
            holder.clasificacion.setContentDescription("Clasificación " + ClasificacionCine.descripcion(f.clasificacion));
            holder.clasificacion.setVisibility(View.VISIBLE);
        }
        holder.itemView.setOnClickListener(v -> listener.onClick(f));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView nombreEvento, fechaHora, detalle, clasificacion;

        VH(@NonNull View itemView) {
            super(itemView);
            nombreEvento = itemView.findViewById(R.id.tvFuncionNombreEvento);
            fechaHora = itemView.findViewById(R.id.tvFuncionFechaHora);
            detalle = itemView.findViewById(R.id.tvFuncionDetalle);
            clasificacion = itemView.findViewById(R.id.tvFuncionClasificacion);
        }
    }
}
