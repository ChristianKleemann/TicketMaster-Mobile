package com.example.ticketmaster_mobile.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Upsert;

import com.example.ticketmaster_mobile.data.entity.SeccionZona;
import com.example.ticketmaster_mobile.data.pojo.ZonaDisponibilidad;

import java.util.List;

@Dao
public interface SeccionZonaDao {

    @Insert
    long insertar(SeccionZona seccionZona);

    @Query("SELECT * FROM Seccion_Zona WHERE ID_Establecimiento = :idEstablecimiento ORDER BY ID_SeccionZona")
    List<SeccionZona> listarPorEstablecimiento(int idEstablecimiento);

    @Query("SELECT * FROM Seccion_Zona WHERE ID_SeccionZona = :idSeccionZona LIMIT 1")
    SeccionZona buscarPorId(int idSeccionZona);

    /** Zonas del establecimiento con los boletos ya vendidos para la función indicada. */
    @Query("SELECT sz.ID_SeccionZona AS idSeccionZona, " +
            "       sz.NombreZona AS nombreZona, " +
            "       sz.TipoZona AS tipoZona, " +
            "       sz.PrecioBase AS precioBase, " +
            "       sz.Capacidad AS capacidad, " +
            "       sz.Descripcion AS descripcion, " +
            "       (SELECT COUNT(*) FROM Boleto b " +
            "         WHERE b.ID_SeccionZona = sz.ID_SeccionZona " +
            "           AND b.ID_Funcion = :idFuncion " +
            "           AND b.EstadoBoleto IN ('Reservado','Emitido','Usado')) AS vendidos " +
            "FROM Seccion_Zona sz " +
            "WHERE sz.ID_Establecimiento = :idEstablecimiento " +
            "ORDER BY sz.ID_SeccionZona")
    LiveData<List<ZonaDisponibilidad>> observarDisponibilidad(int idEstablecimiento, int idFuncion);

    @Query("SELECT * FROM Seccion_Zona")
    List<SeccionZona> listarTodas();

    @Upsert
    void upsertar(List<SeccionZona> lista);
}
