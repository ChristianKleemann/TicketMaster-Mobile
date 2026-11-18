package com.example.ticketmaster_mobile.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Upsert;

import com.example.ticketmaster_mobile.data.entity.Establecimiento;

import java.util.List;

@Dao
public interface EstablecimientoDao {

    @Insert
    long insertar(Establecimiento establecimiento);

    /** "Todos" muestra el catálogo completo; cualquier otro valor filtra por TipoEstablecimiento. */
    @Query("SELECT * FROM Establecimiento WHERE :tipo = 'Todos' OR TipoEstablecimiento = :tipo ORDER BY Nombre")
    LiveData<List<Establecimiento>> observarPorTipo(String tipo);

    @Query("SELECT * FROM Establecimiento WHERE ID_Establecimiento = :id LIMIT 1")
    Establecimiento buscarPorId(int id);

    @Query("SELECT COUNT(*) FROM Establecimiento")
    int contar();

    @Query("SELECT * FROM Establecimiento")
    List<Establecimiento> listarTodos();

    /** Recalcula la capacidad total del lugar como la suma de la capacidad de sus zonas. */
    @Query("UPDATE Establecimiento SET Capacidad = " +
            "(SELECT COALESCE(SUM(Capacidad), 0) FROM Seccion_Zona WHERE ID_Establecimiento = :id) " +
            "WHERE ID_Establecimiento = :id")
    void recalcularCapacidad(int id);

    @Upsert
    void upsertar(List<Establecimiento> lista);
}
