package com.example.ticketmaster_mobile.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Upsert;

import com.example.ticketmaster_mobile.data.entity.DiaFestivo;

import java.util.List;

@Dao
public interface DiaFestivoDao {

    @Insert
    long insertar(DiaFestivo diaFestivo);

    @Query("SELECT * FROM Dia_Festivo WHERE ID_Establecimiento = :idEstablecimiento AND Fecha = :fecha LIMIT 1")
    DiaFestivo buscarPorFecha(int idEstablecimiento, String fecha);

    @Query("SELECT * FROM Dia_Festivo WHERE ID_Establecimiento = :idEstablecimiento AND Fecha >= :desde ORDER BY Fecha")
    List<DiaFestivo> listarDesde(int idEstablecimiento, String desde);

    @Query("SELECT Fecha FROM Dia_Festivo WHERE ID_Establecimiento = :idEstablecimiento")
    List<String> listarFechas(int idEstablecimiento);

    @Query("SELECT * FROM Dia_Festivo")
    List<DiaFestivo> listarTodos();

    @Upsert
    void upsertar(List<DiaFestivo> lista);
}
