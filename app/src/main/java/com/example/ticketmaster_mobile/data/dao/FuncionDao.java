package com.example.ticketmaster_mobile.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Upsert;

import com.example.ticketmaster_mobile.data.entity.Funcion;

import java.util.List;

@Dao
public interface FuncionDao {

    @Insert
    long insertar(Funcion funcion);

    /** Solo funciones que aún no han comenzado (:ahora con formato yyyy-MM-dd HH:mm). */
    @Query("SELECT * FROM Funcion WHERE ID_Establecimiento = :idEstablecimiento " +
            "AND FechaHora >= :ahora ORDER BY FechaHora, NombreEvento")
    LiveData<List<Funcion>> observarFuturasPorEstablecimiento(int idEstablecimiento, String ahora);

    @Query("SELECT * FROM Funcion WHERE ID_Funcion = :idFuncion LIMIT 1")
    Funcion buscarPorId(int idFuncion);

    /** Fecha-hora de la última función programada del establecimiento (null si no hay ninguna). */
    @Query("SELECT MAX(FechaHora) FROM Funcion WHERE ID_Establecimiento = :idEstablecimiento")
    String ultimaFechaHora(int idEstablecimiento);

    @Query("SELECT * FROM Funcion")
    List<Funcion> listarTodas();

    @Upsert
    void upsertar(List<Funcion> lista);
}
