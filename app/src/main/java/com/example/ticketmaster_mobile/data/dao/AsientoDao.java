package com.example.ticketmaster_mobile.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Upsert;

import com.example.ticketmaster_mobile.data.entity.Asiento;
import com.example.ticketmaster_mobile.data.pojo.AsientoEstado;

import java.util.List;

@Dao
public interface AsientoDao {

    @Insert
    long insertar(Asiento asiento);

    /**
     * Devuelve todos los asientos de todas las Secciones/Zonas del Establecimiento al que
     * pertenece la Función indicada, marcando con "ocupado = 1" aquellos que ya tienen un
     * Boleto activo (Reservado/Emitido/Usado) para esa Función específica.
     * Se observa como LiveData para que la cuadrícula se refresque sola si cambia la BD.
     */
    @Query("SELECT a.ID_Asiento AS idAsiento, " +
            "       a.ID_SeccionZona AS idSeccionZona, " +
            "       a.Fila AS fila, " +
            "       a.Numero AS numero, " +
            "       a.CodigoAsiento AS codigoAsiento, " +
            "       sz.NombreZona AS nombreZona, " +
            "       sz.PrecioBase AS precioBase, " +
            "       CASE WHEN EXISTS (" +
            "           SELECT 1 FROM Boleto b " +
            "           WHERE b.ID_Asiento = a.ID_Asiento " +
            "             AND b.ID_Funcion = :idFuncion " +
            "             AND b.EstadoBoleto IN ('Reservado','Emitido','Usado')" +
            "       ) THEN 1 ELSE 0 END AS ocupado " +
            "FROM Asiento a " +
            "INNER JOIN Seccion_Zona sz ON sz.ID_SeccionZona = a.ID_SeccionZona " +
            "WHERE sz.ID_Establecimiento = :idEstablecimiento " +
            "ORDER BY sz.NombreZona, a.Fila, a.Numero")
    LiveData<List<AsientoEstado>> observarAsientosDeFuncion(int idEstablecimiento, int idFuncion);

    /** Igual que la anterior pero limitada a una zona (en cine: un tipo de servicio/asiento). */
    @Query("SELECT a.ID_Asiento AS idAsiento, " +
            "       a.ID_SeccionZona AS idSeccionZona, " +
            "       a.Fila AS fila, " +
            "       a.Numero AS numero, " +
            "       a.CodigoAsiento AS codigoAsiento, " +
            "       sz.NombreZona AS nombreZona, " +
            "       sz.PrecioBase AS precioBase, " +
            "       CASE WHEN EXISTS (" +
            "           SELECT 1 FROM Boleto b " +
            "           WHERE b.ID_Asiento = a.ID_Asiento " +
            "             AND b.ID_Funcion = :idFuncion " +
            "             AND b.EstadoBoleto IN ('Reservado','Emitido','Usado')" +
            "       ) THEN 1 ELSE 0 END AS ocupado " +
            "FROM Asiento a " +
            "INNER JOIN Seccion_Zona sz ON sz.ID_SeccionZona = a.ID_SeccionZona " +
            "WHERE a.ID_SeccionZona = :idSeccionZona " +
            "ORDER BY a.ID_Asiento")
    LiveData<List<AsientoEstado>> observarAsientosDeZona(int idSeccionZona, int idFuncion);

    @Query("SELECT * FROM Asiento")
    List<Asiento> listarTodos();

    @Upsert
    void upsertar(List<Asiento> lista);
}
