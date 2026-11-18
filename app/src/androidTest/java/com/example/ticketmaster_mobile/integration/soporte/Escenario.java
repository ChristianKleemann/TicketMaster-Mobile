package com.example.ticketmaster_mobile.integration.soporte;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Room;

import com.example.ticketmaster_mobile.PasswordUtils;
import com.example.ticketmaster_mobile.data.AppDatabase;
import com.example.ticketmaster_mobile.data.DatabaseExecutor;
import com.example.ticketmaster_mobile.data.entity.Asiento;
import com.example.ticketmaster_mobile.data.entity.DiaFestivo;
import com.example.ticketmaster_mobile.data.entity.Establecimiento;
import com.example.ticketmaster_mobile.data.entity.Funcion;
import com.example.ticketmaster_mobile.data.entity.MetodoPago;
import com.example.ticketmaster_mobile.data.entity.SeccionZona;
import com.example.ticketmaster_mobile.data.entity.Usuario;
import com.example.ticketmaster_mobile.session.SessionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Datos de prueba controlados sobre una base de datos de Room EN MEMORIA que reemplaza a la real
 * (ver {@link AppDatabase#setInstanceForTesting}). Así las pruebas no tocan los datos del usuario y
 * no dependen del día de la semana ni de festivos del catálogo demo.
 *
 * Catálogo:
 *  - Teatro Prueba   : abre todos los días, máx. 3 boletos por usuario. Platea $1,000 (aforo 50) y
 *                      Palco $2,500 (aforo 2). Funciones mañana: "Obra de Prueba" y una con
 *                      caracteres especiales; y una ya pasada (ayer).
 *  - Cine Prueba     : abre todos los días, máx. 6. Servicio Tradicional $90 con asientos A1–A5, B1–B5.
 *  - Teatro Cerrado Hoy : no da servicio el día en que se ejecuta la prueba.
 *  - Teatro Festivo  : hoy es día festivo.
 */
public final class Escenario {

    public static final String CURP = "PELJ950512HJCRLJJC01";
    public static final String CURP_2 = "GOMA900101MJCRLJJC02";
    /** 8 caracteres con mayúscula, minúscula, número y especial (cumple ValidationsUtils). */
    public static final String PASSWORD = "Abc123#x";

    public static final String TEATRO = "Teatro Prueba";
    public static final String PLATEA = "Platea";
    public static final String PALCO = "Palco";
    public static final String EVENTO_TEATRO = "Obra de Prueba";
    public static final String EVENTO_RARO = "Ópera \"Aida\" – ñandú";
    public static final String CINE = "Cine Prueba";
    public static final String SERVICIO_CINE = "Tradicional";
    public static final String EVENTO_CINE = "Película de Prueba";
    public static final String TEATRO_CERRADO = "Teatro Cerrado Hoy";
    public static final String EVENTO_CERRADO = "Obra Cerrada";
    public static final String TEATRO_FESTIVO = "Teatro Festivo";
    public static final String EVENTO_FESTIVO = "Obra Festiva";
    public static final String DESCRIPCION_FESTIVO = "Día de Prueba";

    public static final int MAX_BOLETOS_TEATRO = 3;

    public final AppDatabase db;
    private final Context contexto;

    public int metodoCredito, metodoDebito, metodoPaypal;
    public int usuario1, usuario2;
    public int teatro, teatroPlatea, teatroPalco, teatroFuncion, teatroFuncionRara, teatroFuncionPasada;
    public int cine, cineZona, cineFuncion;
    public final Map<String, Integer> asientos = new HashMap<>();
    public int teatroCerrado, teatroCerradoFuncion;
    public int teatroFestivo, teatroFestivoFuncion;

    private Escenario(Context contexto, AppDatabase db) {
        this.contexto = contexto;
        this.db = db;
    }

    public static Escenario crear(Context contexto) {
        // Mismo executor que usa la app: Espresso espera por él mediante DbIdlingResource
        AppDatabase db = Room.inMemoryDatabaseBuilder(contexto, AppDatabase.class)
                .setQueryExecutor(DatabaseExecutor.IO_EXECUTOR)
                .build();
        AppDatabase.setInstanceForTesting(db);
        Escenario e = new Escenario(contexto, db);
        db.runInTransaction(e::sembrar);
        return e;
    }

    public void cerrar() {
        AppDatabase.setInstanceForTesting(null);
        try {
            db.close();
        } catch (RuntimeException ignorada) {
        }
    }

    // ------------------------------------------------------------------ siembra

    private void sembrar() {
        metodoCredito = metodo("Tarjeta de Crédito");
        metodoDebito = metodo("Tarjeta de Débito");
        metodoPaypal = metodo("PayPal");

        usuario1 = usuario(CURP, "JUAN", "PEREZ", "LOPEZ", PASSWORD);
        usuario2 = usuario(CURP_2, "MARIA", "GOMEZ", "ARIAS", PASSWORD);

        String todos = "1,2,3,4,5,6,7";
        teatro = lugar(TEATRO, "Teatro", todos, MAX_BOLETOS_TEATRO);
        teatroPlatea = zona(teatro, PLATEA, "General", 1000, 50, "Planta baja de prueba");
        teatroPalco = zona(teatro, PALCO, "General", 2500, 2, "Balcones de prueba");
        db.establecimientoDao().recalcularCapacidad(teatro);
        teatroFuncion = funcion(teatro, EVENTO_TEATRO, Fechas.manana(20, 0), "");
        teatroFuncionRara = funcion(teatro, EVENTO_RARO, Fechas.manana(21, 0), "");
        teatroFuncionPasada = funcion(teatro, "Obra Pasada", Fechas.ayer(20, 0), "");

        cine = lugar(CINE, "Cine", todos, 6);
        cineZona = zona(cine, SERVICIO_CINE, "Numerado", 90, 10, "Sala de prueba");
        db.establecimientoDao().recalcularCapacidad(cine);
        for (char fila = 'A'; fila <= 'B'; fila++) {
            for (int n = 1; n <= 5; n++) {
                Asiento a = new Asiento();
                a.idSeccionZona = cineZona;
                a.fila = String.valueOf(fila);
                a.numero = String.valueOf(n);
                a.codigoAsiento = fila + String.valueOf(n);
                asientos.put(a.codigoAsiento, (int) db.asientoDao().insertar(a));
            }
        }
        cineFuncion = funcion(cine, EVENTO_CINE, Fechas.manana(21, 0), "B");

        teatroCerrado = lugar(TEATRO_CERRADO, "Teatro", Fechas.todosExcepto(Fechas.diaIsoDeHoy()), 10);
        zona(teatroCerrado, PLATEA, "General", 500, 20, "Zona de prueba");
        db.establecimientoDao().recalcularCapacidad(teatroCerrado);
        teatroCerradoFuncion = funcion(teatroCerrado, EVENTO_CERRADO, Fechas.manana(20, 0), "");

        teatroFestivo = lugar(TEATRO_FESTIVO, "Teatro", todos, 10);
        zona(teatroFestivo, PLATEA, "General", 500, 20, "Zona de prueba");
        db.establecimientoDao().recalcularCapacidad(teatroFestivo);
        DiaFestivo festivo = new DiaFestivo();
        festivo.idEstablecimiento = teatroFestivo;
        festivo.fecha = Fechas.hoyIso();
        festivo.descripcion = DESCRIPCION_FESTIVO;
        db.diaFestivoDao().insertar(festivo);
        teatroFestivoFuncion = funcion(teatroFestivo, EVENTO_FESTIVO, Fechas.manana(20, 0), "");
    }

    private int metodo(String nombre) {
        MetodoPago m = new MetodoPago();
        m.nombre = nombre;
        return (int) db.metodoPagoDao().insertar(m);
    }

    private int usuario(String curp, String nombre, String ap1, String ap2, String password) {
        Usuario u = new Usuario();
        u.nombre = nombre;
        u.apellidoPaterno = ap1;
        u.apellidoMaterno = ap2;
        u.curp = curp;
        u.fechaNacimiento = "1995-05-12";
        u.estado = "JALISCO";
        u.sexo = "Masculino";
        u.fechaRegistro = Fechas.hoyIso();
        u.passwordHash = PasswordUtils.hashPassword(password);
        return (int) db.usuarioDao().insertar(u);
    }

    private int lugar(String nombre, String tipo, String dias, int maxBoletos) {
        Establecimiento e = new Establecimiento();
        e.nombre = nombre;
        e.ubicacion = "Dirección de prueba";
        e.tipoEstablecimiento = tipo;
        e.horaApertura = "00:00";
        e.horaCierre = "23:59";
        e.diasLaborales = dias;
        e.maxBoletosPorUsuario = maxBoletos;
        e.vestimenta = "Teatro".equals(tipo) ? "Casual de prueba" : "";
        e.restricciones = "Restricciones de prueba";
        return (int) db.establecimientoDao().insertar(e);
    }

    private int zona(int idLugar, String nombre, String tipo, double precio, int capacidad, String descripcion) {
        SeccionZona z = new SeccionZona();
        z.idEstablecimiento = idLugar;
        z.nombreZona = nombre;
        z.tipoZona = tipo;
        z.precioBase = precio;
        z.capacidad = capacidad;
        z.descripcion = descripcion;
        return (int) db.seccionZonaDao().insertar(z);
    }

    /** Crea una función extra (para pruebas que necesitan dos funciones del mismo lugar). */
    public int funcionAdicional(int idLugar, String evento, String fechaHora, String clasificacion) {
        return funcion(idLugar, evento, fechaHora, clasificacion);
    }

    private int funcion(int idLugar, String evento, String fechaHora, String clasificacion) {
        Funcion f = new Funcion();
        f.idEstablecimiento = idLugar;
        f.nombreEvento = evento;
        f.fechaHora = fechaHora;
        f.clasificacion = clasificacion;
        f.detalle = "Función de prueba";
        return (int) db.funcionDao().insertar(f);
    }

    // ------------------------------------------------------------------ sesión

    public void iniciarSesionComo(int idUsuario) {
        new SessionManager(contexto).iniciarSesion(idUsuario);
    }

    public void cerrarSesion() {
        new SessionManager(contexto).cerrarSesion();
    }

    // ------------------------------------------------------------------ consultas crudas

    public long numero(String sql) {
        try (Cursor c = db.query(sql, null)) {
            return c.moveToFirst() && !c.isNull(0) ? c.getLong(0) : 0L;
        }
    }

    public String texto(String sql) {
        try (Cursor c = db.query(sql, null)) {
            return c.moveToFirst() && !c.isNull(0) ? c.getString(0) : null;
        }
    }

    public int contar(String tabla) {
        return (int) numero("SELECT COUNT(*) FROM " + tabla);
    }

    public int ultimaOrden() {
        return (int) numero("SELECT MAX(ID_Orden) FROM Orden");
    }

    /** Todo el contenido de todas las tablas como texto, para buscar datos que NO deben guardarse. */
    public List<String> volcarTodoComoTexto() {
        List<String> celdas = new ArrayList<>();
        List<String> tablas = new ArrayList<>();
        try (Cursor c = db.query("SELECT name FROM sqlite_master WHERE type='table' "
                + "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' AND name NOT LIKE 'android_%'", null)) {
            while (c.moveToNext()) tablas.add(c.getString(0));
        }
        for (String tabla : tablas) {
            try (Cursor c = db.query("SELECT * FROM \"" + tabla + "\"", null)) {
                while (c.moveToNext()) {
                    for (int i = 0; i < c.getColumnCount(); i++) {
                        if (c.getType(i) == Cursor.FIELD_TYPE_NULL || c.getType(i) == Cursor.FIELD_TYPE_BLOB) continue;
                        celdas.add(tabla + "." + c.getColumnName(i) + "=" + c.getString(i));
                    }
                }
            }
        }
        return celdas;
    }
}
