package com.example.ticketmaster_mobile.ui.tickets.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ticketmaster_mobile.R;
import com.example.ticketmaster_mobile.data.pojo.ZonaDisponibilidad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lista de zonas/secciones con precio, disponibilidad y un selector de cantidad (− / +). */
public class ZonaCantidadAdapter extends RecyclerView.Adapter<ZonaCantidadAdapter.VH> {

    public interface OnCantidadesCambiadasListener {
        void onCambio(List<ZonaDisponibilidad> zonas, Map<Integer, Integer> cantidadPorZona);
    }

    private final List<ZonaDisponibilidad> items = new ArrayList<>();
    private final Map<Integer, Integer> cantidades = new HashMap<>();
    private final OnCantidadesCambiadasListener listener;
    private int maxTotal = 0;

    public ZonaCantidadAdapter(OnCantidadesCambiadasListener listener) {
        this.listener = listener;
    }

    public void setMaxTotal(int maxTotal) {
        this.maxTotal = Math.max(0, maxTotal);
    }

    public void submitList(List<ZonaDisponibilidad> nuevos) {
        items.clear();
        if (nuevos != null) items.addAll(nuevos);
        // Recorta cantidades si la disponibilidad bajó mientras el usuario elegía
        for (ZonaDisponibilidad z : items) {
            Integer q = cantidades.get(z.idSeccionZona);
            if (q != null && q > z.disponibles()) cantidades.put(z.idSeccionZona, z.disponibles());
        }
        notifyDataSetChanged();
        listener.onCambio(items, cantidades);
    }

    private int totalSeleccionado() {
        int t = 0;
        for (int q : cantidades.values()) t += q;
        return t;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_zona_cantidad, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ZonaDisponibilidad z = items.get(position);
        Integer actual = cantidades.get(z.idSeccionZona);
        int cantidad = actual != null ? actual : 0;

        h.nombre.setText(z.nombreZona);
        h.descripcion.setText(z.descripcion);
        h.precio.setText(String.format(Locale.getDefault(), "$%,.2f", z.precioBase));
        h.disponibles.setText(z.disponibles() == 0 ? "Agotado"
                : String.format(Locale.getDefault(), "Disponibles: %,d de %,d", z.disponibles(), z.capacidad));
        h.cantidad.setText(String.valueOf(cantidad));
        h.menos.setEnabled(cantidad > 0);
        h.mas.setEnabled(z.disponibles() > cantidad);

        h.menos.setOnClickListener(v -> cambiar(z, -1, h.itemView));
        h.mas.setOnClickListener(v -> cambiar(z, +1, h.itemView));
    }

    private void cambiar(ZonaDisponibilidad z, int delta, View vista) {
        Integer actual = cantidades.get(z.idSeccionZona);
        int nueva = (actual != null ? actual : 0) + delta;
        if (nueva < 0 || nueva > z.disponibles()) return;
        if (delta > 0 && totalSeleccionado() >= maxTotal) {
            Toast.makeText(vista.getContext(), "Solo puedes comprar " + maxTotal + " boletos más para esta función",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        cantidades.put(z.idSeccionZona, nueva);
        notifyDataSetChanged();
        listener.onCambio(items, cantidades);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView nombre, descripcion, precio, disponibles, cantidad;
        final Button menos, mas;

        VH(@NonNull View itemView) {
            super(itemView);
            nombre = itemView.findViewById(R.id.tvZonaNombre);
            descripcion = itemView.findViewById(R.id.tvZonaDescripcion);
            precio = itemView.findViewById(R.id.tvZonaPrecio);
            disponibles = itemView.findViewById(R.id.tvZonaDisponibles);
            cantidad = itemView.findViewById(R.id.tvCantidad);
            menos = itemView.findViewById(R.id.btnMenos);
            mas = itemView.findViewById(R.id.btnMas);
        }
    }
}
