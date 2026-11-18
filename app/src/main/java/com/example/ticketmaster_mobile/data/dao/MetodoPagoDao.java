package com.example.ticketmaster_mobile.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Upsert;

import com.example.ticketmaster_mobile.data.entity.MetodoPago;

import java.util.List;

@Dao
public interface MetodoPagoDao {

    @Insert
    long insertar(MetodoPago metodoPago);

    @Query("SELECT * FROM MetodoPago ORDER BY ID_MetodoPago")
    LiveData<List<MetodoPago>> observarTodos();

    @Query("SELECT * FROM MetodoPago WHERE ID_MetodoPago = :id LIMIT 1")
    MetodoPago buscarPorId(int id);

    @Query("SELECT COUNT(*) FROM MetodoPago")
    int contar();

    @Query("SELECT * FROM MetodoPago")
    List<MetodoPago> listarTodos();

    @Upsert
    void upsertar(List<MetodoPago> lista);
}
