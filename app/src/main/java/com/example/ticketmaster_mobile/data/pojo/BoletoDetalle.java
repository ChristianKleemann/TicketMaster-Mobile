package com.example.ticketmaster_mobile.data.pojo;

import androidx.annotation.Nullable;

/**
 * Resultado de la consulta que combina Boleto + Funcion + Establecimiento + Seccion_Zona
 * (+ Asiento cuando existe), usado en el historial del Perfil y en la pantalla de confirmación.
 */
public class BoletoDetalle {
    public int idBoleto;
    public int idOrden;
    public String nombreEvento;
    public String fechaHora;
    public String clasificacion;
    public String detalleEvento;
    public String nombreEstablecimiento;
    public String tipoEstablecimiento;
    public String vestimenta;
    public String restricciones;
    public String nombreZona;

    /** NULL cuando el boleto no tiene asiento numerado (teatro por sección, museo). */
    @Nullable
    public String codigoAsiento;

    public double precioFinal;
    public String estadoBoleto;
}
