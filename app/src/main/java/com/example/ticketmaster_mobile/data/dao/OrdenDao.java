package com.example.ticketmaster_mobile.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Upsert;

import com.example.ticketmaster_mobile.data.entity.Orden;

import java.util.List;

@Dao
public interface OrdenDao {

    @Insert
    long insertar(Orden orden);

    @Query("SELECT * FROM Orden WHERE ID_Orden = :idOrden LIMIT 1")
    Orden buscarPorId(int idOrden);

    @Query("SELECT * FROM Orden WHERE ID_Usuario = :idUsuario ORDER BY FechaCompra DESC")
    LiveData<List<Orden>> observarPorUsuario(int idUsuario);
}
