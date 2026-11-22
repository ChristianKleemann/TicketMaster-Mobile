package com.example.ticketmaster_mobile.whatsapp;

import com.example.ticketmaster_mobile.data.dao.WhatsappOutboxDao;
import com.example.ticketmaster_mobile.data.entity.WhatsappOutbox;

/** Implementación de {@link OutboxStore} sobre Room. */
public class RoomOutboxStore implements OutboxStore {

    private final WhatsappOutboxDao dao;

    public RoomOutboxStore(WhatsappOutboxDao dao) {
        this.dao = dao;
    }

    @Override
    public Entrada reclamarSiguiente(long ahoraMs, long leaseMs) {
        WhatsappOutbox e = dao.reclamarSiguiente(ahoraMs, leaseMs);
        return e == null ? null : new Entrada(e.idOutbox, e.idOrden, e.intentos);
    }

    @Override
    public void marcarEnviado(long id, String mensajeId, long ahoraMs) {
        dao.marcarEnviado(id, mensajeId, ahoraMs);
    }

    @Override
    public void reprogramar(long id, long proximoIntentoMs, String error, int intentos) {
        dao.reprogramar(id, proximoIntentoMs, error, intentos);
    }

    @Override
    public void marcarFallido(long id, String error) {
        dao.marcarFallido(id, error);
    }

    @Override
    public long proximoTrabajoMs() {
        Long pendiente = dao.minProximoPendiente();
        Long enCurso = dao.minLeaseEnCurso();   // por si un proceso murió con la entrada en ENVIANDO
        long min = -1;
        if (pendiente != null) min = pendiente;
        if (enCurso != null && (min == -1 || enCurso + 1 < min)) min = enCurso + 1;
        return min;
    }
}
