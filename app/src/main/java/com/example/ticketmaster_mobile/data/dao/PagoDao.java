package com.example.ticketmaster_mobile.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Upsert;

import com.example.ticketmaster_mobile.data.entity.Pago;

@Dao
public interface PagoDao {

    @Insert
    long insertar(Pago pago);

    @Query("SELECT * FROM Pago WHERE ID_Orden = :idOrden LIMIT 1")
    Pago buscarPorOrden(int idOrden);

    @Query("SELECT COUNT(*) FROM Pago WHERE ReferenciaExterna = :referencia")
    int contarPorReferencia(String referencia);
}
