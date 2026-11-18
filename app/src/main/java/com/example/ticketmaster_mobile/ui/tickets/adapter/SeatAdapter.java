package com.example.ticketmaster_mobile.ui.tickets.adapter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ticketmaster_mobile.R;
import com.example.ticketmaster_mobile.data.ServicioCine;
import com.example.ticketmaster_mobile.data.pojo.AsientoEstado;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuadrícula de asientos de cine. El aspecto (color de acento, forma de la butaca) depende del
 * tipo de servicio elegido; el estado (disponible / seleccionado / ocupado) se pinta encima.
 * El máximo de asientos seleccionables se define con {@link #setMaxSeleccion(int)}.
 */
public class SeatAdapter extends RecyclerView.Adapter<SeatAdapter.VH> {

    public interface OnSeleccionCambiadaListener {
        void onCambio(List<AsientoEstado> seleccionados);
    }

    private final List<AsientoEstado> items = new ArrayList<>();
    private final List<AsientoEstado> seleccionados = new ArrayList<>();
    private final OnSeleccionCambiadaListener listener;
    private ServicioCine servicio = ServicioCine.TRADICIONAL;
    private int maxSeleccion = 6;
    private Context context;

    public SeatAdapter(OnSeleccionCambiadaListener listener) {
        this.listener = listener;
    }

    public void setMaxSeleccion(int max) {
        this.maxSeleccion = Math.max(0, max);
    }

    public void setServicio(ServicioCine servicio) {
        this.servicio = servicio;
        notifyDataSetChanged();
    }

    /** Actualiza la lista preservando la selección actual por ID_Asiento. */
    public void submitList(List<AsientoEstado> nuevosItems) {
        List<Integer> idsSeleccionados = new ArrayList<>();
        for (AsientoEstado a : seleccionados) idsSeleccionados.add(a.idAsiento);

        items.clear();
        seleccionados.clear();
        if (nuevosItems != null) {
            for (AsientoEstado a : nuevosItems) {
                a.seleccionado = (a.ocupado == 0) && idsSeleccionados.contains(a.idAsiento);
                items.add(a);
                if (a.seleccionado) seleccionados.add(a);
            }
        }
        notifyDataSetChanged();
        listener.onCambio(seleccionados);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_asiento, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        AsientoEstado asiento = items.get(position);
        holder.codigo.setText(asiento.codigoAsiento);
        holder.codigo.setTextSize(servicio.columnas >= 9 ? 10f : 12f);

        int relleno;
        int borde;
        if (asiento.ocupado == 1) {
            relleno = context.getColor(R.color.seat_ocupado_bg);
            borde = context.getColor(R.color.seat_ocupado);
            holder.candado.setVisibility(View.VISIBLE);
            holder.itemView.setAlpha(0.7f);
            holder.itemView.setOnClickListener(null);
        } else {
            holder.candado.setVisibility(View.GONE);
            holder.itemView.setAlpha(1f);
            if (asiento.seleccionado) {
                relleno = context.getColor(R.color.seat_seleccionado);
                borde = relleno;
            } else {
                relleno = oscurecer(servicio.colorAcento);
                borde = servicio.colorAcento;
            }
            holder.itemView.setOnClickListener(v -> alternarSeleccion(asiento));
        }

        GradientDrawable fondo = new GradientDrawable();
        fondo.setShape(GradientDrawable.RECTANGLE);
        fondo.setColor(relleno);
        fondo.setStroke(dp(2), borde);
        fondo.setCornerRadius(dp(servicio.radioDp));
        holder.codigo.setBackground(fondo);
    }

    private void alternarSeleccion(AsientoEstado asiento) {
        if (!asiento.seleccionado && seleccionados.size() >= maxSeleccion) {
            Toast.makeText(context, "Solo puedes seleccionar hasta " + maxSeleccion + " asientos",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        asiento.seleccionado = !asiento.seleccionado;
        if (asiento.seleccionado) {
            seleccionados.add(asiento);
        } else {
            seleccionados.remove(asiento);
        }
        notifyDataSetChanged();
        listener.onCambio(seleccionados);
    }

    private int dp(int valor) {
        return Math.round(valor * context.getResources().getDisplayMetrics().density);
    }

    /** Versión oscura (≈25 %) del color de acento, para el relleno de butacas disponibles. */
    private static int oscurecer(int color) {
        return Color.rgb((int) (Color.red(color) * 0.25), (int) (Color.green(color) * 0.25),
                (int) (Color.blue(color) * 0.25));
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        context = recyclerView.getContext();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView codigo;
        final ImageView candado;

        VH(@NonNull View itemView) {
            super(itemView);
            codigo = itemView.findViewById(R.id.tvAsientoCodigo);
            candado = itemView.findViewById(R.id.ivAsientoCandado);
        }
    }
}
