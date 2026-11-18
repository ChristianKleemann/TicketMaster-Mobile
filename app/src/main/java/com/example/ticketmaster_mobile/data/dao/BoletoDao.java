package com.example.ticketmaster_mobile.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Upsert;

import com.example.ticketmaster_mobile.data.entity.Boleto;
import com.example.ticketmaster_mobile.data.pojo.BoletoDetalle;

import java.util.List;

@Dao
public interface BoletoDao {

    @Insert
    long insertar(Boleto boleto);

    @Insert
    List<Long> insertarVarios(List<Boleto> boletos);

    /** Boletos activos de una función en una zona (para controlar el aforo). */
    @Query("SELECT COUNT(*) FROM Boleto " +
            "WHERE ID_Funcion = :idFuncion AND ID_SeccionZona = :idSeccionZona " +
            "AND EstadoBoleto IN ('Reservado','Emitido','Usado')")
    int contarActivosEnZona(int idFuncion, int idSeccionZona);

    /** Boletos activos que ya tiene un usuario para una función (límite de compra por usuario). */
    @Query("SELECT COUNT(*) FROM Boleto b " +
            "INNER JOIN Orden o ON o.ID_Orden = b.ID_Orden " +
            "WHERE o.ID_Usuario = :idUsuario AND b.ID_Funcion = :idFuncion " +
            "AND b.EstadoBoleto IN ('Reservado','Emitido','Usado')")
    int contarActivosDeUsuarioEnFuncion(int idUsuario, int idFuncion);

    /**
     * Detalle de los boletos de una Orden: evento, establecimiento, zona/servicio y, si existe,
     * el código de asiento. Se usa en el historial del Perfil y en la confirmación de compra.
     */
    @Query("SELECT b.ID_Boleto AS idBoleto, " +
            "       b.ID_Orden AS idOrden, " +
            "       f.NombreEvento AS nombreEvento, " +
            "       f.FechaHora AS fechaHora, " +
            "       f.Clasificacion AS clasificacion, " +
            "       f.Detalle AS detalleEvento, " +
            "       e.Nombre AS nombreEstablecimiento, " +
            "       e.TipoEstablecimiento AS tipoEstablecimiento, " +
            "       e.Vestimenta AS vestimenta, " +
            "       e.Restricciones AS restricciones, " +
            "       sz.NombreZona AS nombreZona, " +
            "       a.CodigoAsiento AS codigoAsiento, " +
            "       b.PrecioFinal AS precioFinal, " +
            "       b.EstadoBoleto AS estadoBoleto " +
            "FROM Boleto b " +
            "INNER JOIN Funcion f ON f.ID_Funcion = b.ID_Funcion " +
            "INNER JOIN Establecimiento e ON e.ID_Establecimiento = f.ID_Establecimiento " +
            "INNER JOIN Seccion_Zona sz ON sz.ID_SeccionZona = b.ID_SeccionZona " +
            "LEFT JOIN Asiento a ON a.ID_Asiento = b.ID_Asiento " +
            "WHERE b.ID_Orden = :idOrden " +
            "ORDER BY b.ID_Boleto")
    List<BoletoDetalle> listarDetallePorOrden(int idOrden);
}
