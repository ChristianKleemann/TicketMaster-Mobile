package com.example.ticketmaster_mobile.integration.soporte;

import android.content.Context;

import com.example.ticketmaster_mobile.data.repository.CompraRepository;
import com.example.ticketmaster_mobile.data.repository.CompraRepository.DatosPago;
import com.example.ticketmaster_mobile.data.repository.CompraRepository.ItemCompra;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Atajos para ejecutar compras REALES a través de {@link CompraRepository} (sin pasar por la UI). */
public final class Compras {

    public static final class Resultado {
        public final int idOrden;
        public final String error;

        Resultado(int idOrden, String error) {
            this.idOrden = idOrden;
            this.error = error;
        }

        public boolean exito() {
            return error == null;
        }
    }

    private Compras() {
    }

    /** Ejecuta la compra y espera su resultado (el repositorio contesta en el hilo principal). */
    public static Resultado comprar(Context ctx, int idUsuario, int idFuncion,
                                    List<ItemCompra> items, DatosPago datos) {
        final CountDownLatch listo = new CountDownLatch(1);
        final AtomicReference<Resultado> res = new AtomicReference<>();
        new CompraRepository(ctx).realizarCompra(idUsuario, idFuncion, items, datos,
                new CompraRepository.CompraCallback() {
                    @Override
                    public void onExito(int idOrden) {
                        res.set(new Resultado(idOrden, null));
                        listo.countDown();
                    }

                    @Override
                    public void onError(String mensaje) {
                        res.set(new Resultado(-1, mensaje));
                        listo.countDown();
                    }
                });
        try {
            if (!listo.await(20, TimeUnit.SECONDS)) {
                throw new AssertionError("La compra no respondió en 20 s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrumpido esperando la compra", e);
        }
        return res.get();
    }

    /** {@code n} boletos de una zona sin asiento numerado (teatro / museo). */
    public static List<ItemCompra> porZona(int idZona, double precio, int n) {
        List<ItemCompra> items = new ArrayList<>();
        for (int i = 0; i < n; i++) items.add(new ItemCompra(null, idZona, precio));
        return items;
    }

    /** Un boleto por asiento numerado (cine). */
    public static List<ItemCompra> asientos(Escenario e, double precio, String... codigos) {
        List<ItemCompra> items = new ArrayList<>();
        for (String codigo : codigos) {
            Integer id = e.asientos.get(codigo);
            if (id == null) throw new IllegalArgumentException("Asiento inexistente: " + codigo);
            items.add(new ItemCompra(id, e.cineZona, precio));
        }
        return items;
    }

    public static DatosPago tarjetaCredito(Escenario e) {
        return new DatosPago(e.metodoCredito, false, "Juan Perez", "1111", null);
    }

    public static DatosPago tarjetaDebito(Escenario e) {
        return new DatosPago(e.metodoDebito, false, "Juan Perez", "4242", null);
    }

    public static DatosPago paypal(Escenario e, String correo) {
        return new DatosPago(e.metodoPaypal, true, "Juan Perez", null, correo);
    }
}
