package com.example.ticketmaster_mobile.ui.tickets.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ticketmaster_mobile.R;
import com.example.ticketmaster_mobile.data.pojo.BoletoDetalle;
import com.example.ticketmaster_mobile.data.pojo.OrdenHistorialItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrdenHistorialAdapter extends RecyclerView.Adapter<OrdenHistorialAdapter.OrdenViewHolder> {

    private final List<OrdenHistorialItem> items = new ArrayList<>();

    public void submitList(List<OrdenHistorialItem> nuevosItems) {
        items.clear();
        if (nuevosItems != null) {
            items.addAll(nuevosItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OrdenViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_orden_historial, parent, false);
        return new OrdenViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrdenViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class OrdenViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvFecha, tvEstado, tvTotal;
        private final LinearLayout llBoletosContainer;

        OrdenViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFecha = itemView.findViewById(R.id.tvOrdenFecha);
            tvEstado = itemView.findViewById(R.id.tvOrdenEstado);
            tvTotal = itemView.findViewById(R.id.tvOrdenTotal);
            llBoletosContainer = itemView.findViewById(R.id.llBoletosContainer);
        }

        void bind(OrdenHistorialItem item) {
            tvFecha.setText(item.orden.fechaCompra);
            tvEstado.setText(item.orden.estadoOrden);
            tvTotal.setText(String.format(Locale.getDefault(), "Total pagado: $%.2f", item.orden.montoTotal));

            llBoletosContainer.removeAllViews();
            // Agrupa los boletos de la misma función y zona/servicio en una sola línea
            java.util.Map<String, java.util.List<BoletoDetalle>> grupos = new java.util.LinkedHashMap<>();
            for (BoletoDetalle detalle : item.boletos) {
                String clave = detalle.nombreEvento + "|" + detalle.fechaHora + "|" + detalle.nombreZona;
                java.util.List<BoletoDetalle> lista = grupos.get(clave);
                if (lista == null) {
                    lista = new ArrayList<>();
                    grupos.put(clave, lista);
                }
                lista.add(detalle);
            }
            for (java.util.List<BoletoDetalle> grupo : grupos.values()) {
                BoletoDetalle d0 = grupo.get(0);
                double subtotal = 0;
                StringBuilder asientos = new StringBuilder();
                for (BoletoDetalle d : grupo) {
                    subtotal += d.precioFinal;
                    if (d.codigoAsiento != null) {
                        if (asientos.length() > 0) asientos.append(", ");
                        asientos.append(d.codigoAsiento);
                    }
                }
                String lugarZona = asientos.length() > 0
                        ? "Servicio " + d0.nombreZona + " — asientos " + asientos
                        : d0.nombreZona + " ×" + grupo.size();
                TextView linea = new TextView(itemView.getContext());
                linea.setTextColor(itemView.getContext().getColor(R.color.tm_text_primary));
                linea.setTextSize(14f);
                linea.setPadding(0, 4, 0, 4);
                linea.setText(String.format(Locale.getDefault(), "• %s (%s) — %s\n   %s — $%,.2f",
                        d0.nombreEvento, d0.nombreEstablecimiento,
                        com.example.ticketmaster_mobile.data.FechaUtils.fechaHoraLegible(d0.fechaHora),
                        lugarZona, subtotal));
                llBoletosContainer.addView(linea);
            }
        }
    }
}
