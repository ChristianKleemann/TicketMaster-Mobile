package com.example.ticketmaster_mobile.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.ticketmaster_mobile.PasswordUtils;
import com.example.ticketmaster_mobile.data.AppDatabase;
import com.example.ticketmaster_mobile.data.entity.Orden;
import com.example.ticketmaster_mobile.data.entity.Usuario;
import com.example.ticketmaster_mobile.data.entity.WhatsappOutbox;
import com.example.ticketmaster_mobile.integration.soporte.Compras;
import com.example.ticketmaster_mobile.integration.soporte.EntornoRule;
import com.example.ticketmaster_mobile.integration.soporte.Escenario;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Integración con la base de datos real (Room + SQLite): lectura, escritura, restricciones,
 * persistencia y migración. Las pruebas de pantalla que leen datos están en CompraBoletosUiTest.
 */
@RunWith(AndroidJUnit4.class)
public class BaseDatosIntegracionTest {

    @Rule
    public final EntornoRule entorno = new EntornoRule();

    private Usuario nuevoUsuario(String curp) {
        Usuario u = new Usuario();
        u.nombre = "ANA";
        u.apellidoPaterno = "RUIZ";
        u.apellidoMaterno = "DIAZ";
        u.curp = curp;
        u.fechaNacimiento = "1999-01-02";
        u.estado = "JALISCO";
        u.sexo = "Femenino";
        u.fechaRegistro = "2026-01-01";
        u.passwordHash = PasswordUtils.hashPassword("Zz9#aaaa");
        return u;
    }

    // ------------------------------------------------------------------ esquema

    @Test
    public void estanTodasLasTablasDelModelo() {
        List<String> tablas = new ArrayList<>();
        try (Cursor c = entorno.esc.db.query(
                "SELECT name FROM sqlite_master WHERE type='table'", null)) {
            while (c.moveToNext()) tablas.add(c.getString(0));
        }
        for (String esperada : new String[]{"Usuario", "Establecimiento", "Seccion_Zona", "Asiento",
                "Funcion", "Orden", "Boleto", "MetodoPago", "Pago", "Dia_Festivo", "WhatsappOutbox"}) {
            assertTrue("Falta la tabla " + esperada, tablas.contains(esperada));
        }
    }

    @Test
    public void lasLlavesForaneasEstanActivas() {
        assertEquals(1L, entorno.esc.numero("PRAGMA foreign_keys"));
    }

    // ------------------------------------------------------------------ escritura y lectura

    @Test
    public void escrituraYLecturaDeUsuarioConservaTodosLosCampos() {
        long id = entorno.esc.db.usuarioDao().insertar(nuevoUsuario("RUDA990102MJCRLJJC01"));
        assertTrue(id > 0);

        Usuario leido = entorno.esc.db.usuarioDao().buscarPorId((int) id);
        assertNotNull(leido);
        assertEquals("ANA", leido.nombre);
        assertEquals("RUIZ", leido.apellidoPaterno);
        assertEquals("DIAZ", leido.apellidoMaterno);
        assertEquals("RUDA990102MJCRLJJC01", leido.curp);
        assertEquals("1999-01-02", leido.fechaNacimiento);
        assertEquals("JALISCO", leido.estado);
        assertEquals("Femenino", leido.sexo);
        assertEquals(PasswordUtils.hashPassword("Zz9#aaaa"), leido.passwordHash);
        assertEquals(leido.idUsuario, entorno.esc.db.usuarioDao().buscarPorCurp("RUDA990102MJCRLJJC01").idUsuario);
    }

    @Test
    public void buscarUsuarioInexistenteDevuelveNull() {
        assertNull(entorno.esc.db.usuarioDao().buscarPorCurp("NOEXISTE000000000000"));
        assertNull(entorno.esc.db.usuarioDao().buscarPorId(987654));
        assertEquals(0, entorno.esc.db.usuarioDao().contarPorCurp("NOEXISTE000000000000"));
    }

    @Test
    public void textoConAcentosYComillasSobreviveAlViajeDeIdaYVuelta() {
        Usuario u = nuevoUsuario("RUDA990102MJCRLJJC02");
        u.nombre = "JOSÉ O'NEIL \"EL ÑANDÚ\"";
        long id = entorno.esc.db.usuarioDao().insertar(u);

        assertEquals("JOSÉ O'NEIL \"EL ÑANDÚ\"", entorno.esc.db.usuarioDao().buscarPorId((int) id).nombre);
    }

    @Test
    public void lasInyeccionesSqlEnParametrosSeTratanComoTexto() {
        int antes = entorno.esc.contar("Usuario");
        assertNull(entorno.esc.db.usuarioDao().buscarPorCurp("' OR '1'='1"));
        assertNull(entorno.esc.db.usuarioDao().buscarPorCurp("x'; DROP TABLE Usuario; --"));
        assertEquals(antes, entorno.esc.contar("Usuario"));
    }

    // ------------------------------------------------------------------ restricciones

    @Test
    public void curpDuplicadaViolaLaRestriccionUnique() {
        try {
            entorno.esc.db.usuarioDao().insertar(nuevoUsuario(Escenario.CURP));
            fail("Debió rechazar una CURP repetida");
        } catch (SQLiteConstraintException esperado) {
            // correcto
        }
        assertEquals(1, entorno.esc.db.usuarioDao().contarPorCurp(Escenario.CURP));
    }

    @Test
    public void ordenDeUsuarioInexistenteViolaLaLlaveForanea() {
        Orden o = new Orden();
        o.idUsuario = 987654;
        o.fechaCompra = "2026-01-01 10:00:00";
        o.montoTotal = 10;
        try {
            entorno.esc.db.ordenDao().insertar(o);
            fail("Debió rechazar una orden sin usuario");
        } catch (SQLiteConstraintException esperado) {
            // correcto
        }
    }

    @Test
    public void borrarUnaOrdenEliminaEnCascadaSusBoletosPagoYNotificacion() {
        Compras.Resultado r = Compras.comprar(entorno.contexto, entorno.esc.usuario1,
                entorno.esc.teatroFuncion,
                Compras.porZona(entorno.esc.teatroPlatea, 1000, 2),
                Compras.tarjetaCredito(entorno.esc));
        assertTrue(r.error, r.exito());
        assertEquals(2, entorno.esc.contar("Boleto"));
        assertEquals(1, entorno.esc.contar("Pago"));
        assertEquals(1, entorno.esc.contar("WhatsappOutbox"));

        entorno.esc.db.getOpenHelper().getWritableDatabase()
                .execSQL("DELETE FROM Orden WHERE ID_Orden = " + r.idOrden);

        assertEquals(0, entorno.esc.contar("Boleto"));
        assertEquals(0, entorno.esc.contar("Pago"));
        assertEquals(0, entorno.esc.contar("WhatsappOutbox"));
    }

    @Test
    public void transaccionFallidaNoDejaDatosParcialesEnLaBase() {
        int antes = entorno.esc.contar("Usuario");
        try {
            entorno.esc.db.runInTransaction(() -> {
                entorno.esc.db.usuarioDao().insertar(nuevoUsuario("RUDA990102MJCRLJJC03"));
                // segundo insert repetido: aborta la transacción completa
                entorno.esc.db.usuarioDao().insertar(nuevoUsuario(Escenario.CURP));
            });
            fail("Debió abortar la transacción");
        } catch (SQLiteConstraintException esperado) {
            // correcto
        }
        assertEquals("El primer insert debe deshacerse", antes, entorno.esc.contar("Usuario"));
        assertNull(entorno.esc.db.usuarioDao().buscarPorCurp("RUDA990102MJCRLJJC03"));
    }

    @Test
    public void unaCompraYSuNotificacionSonAtomicas() {
        // Compra rechazada (más boletos que el máximo) => no queda orden, pago ni notificación
        Compras.Resultado r = Compras.comprar(entorno.contexto, entorno.esc.usuario1,
                entorno.esc.teatroFuncion,
                Compras.porZona(entorno.esc.teatroPlatea, 1000, Escenario.MAX_BOLETOS_TEATRO + 1),
                Compras.tarjetaCredito(entorno.esc));
        assertFalse(r.exito());
        assertEquals(0, entorno.esc.contar("Orden"));
        assertEquals(0, entorno.esc.contar("Boleto"));
        assertEquals(0, entorno.esc.contar("Pago"));
        assertEquals(0, entorno.esc.contar("WhatsappOutbox"));
    }

    // ------------------------------------------------------------------ persistencia

    @Test
    public void losDatosSobrevivenAlCerrarYReabrirLaBase() {
        Context ctx = ApplicationProvider.getApplicationContext();
        String archivo = "persistencia_prueba.db";
        ctx.deleteDatabase(archivo);
        try {
            AppDatabase a = Room.databaseBuilder(ctx, AppDatabase.class, archivo).build();
            long id = a.usuarioDao().insertar(nuevoUsuario("RUDA990102MJCRLJJC04"));
            a.close();

            AppDatabase b = Room.databaseBuilder(ctx, AppDatabase.class, archivo).build();
            try {
                Usuario leido = b.usuarioDao().buscarPorId((int) id);
                assertNotNull("El usuario debe persistir tras reabrir la base", leido);
                assertEquals("RUDA990102MJCRLJJC04", leido.curp);
            } finally {
                b.close();
            }
        } finally {
            ctx.deleteDatabase(archivo);
        }
    }

    // ------------------------------------------------------------------ migración 2 → 3

    @Test
    public void migracion2a3CreaLaMismaTablaDeNotificacionesQueRoom() {
        Context ctx = ApplicationProvider.getApplicationContext();
        SupportSQLiteOpenHelper.Configuration config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
                .name(null) // en memoria
                .callback(new SupportSQLiteOpenHelper.Callback(2) {
                    @Override
                    public void onCreate(SupportSQLiteDatabase db) {
                        // v2: la tabla padre ya existe; WhatsappOutbox todavía no
                        db.execSQL("CREATE TABLE `Orden` (`ID_Orden` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)");
                        db.execSQL("INSERT INTO Orden (ID_Orden) VALUES (7)");
                    }

                    @Override
                    public void onUpgrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
                    }
                })
                .build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(config);
        try {
            SupportSQLiteDatabase v2 = helper.getWritableDatabase();
            AppDatabase.MIGRATION_2_3.migrate(v2);

            assertEquals(describir(entorno.esc.db.getOpenHelper().getWritableDatabase(), "WhatsappOutbox"),
                    describir(v2, "WhatsappOutbox"));

            // Los datos previos se conservan y la tabla nueva ya acepta filas
            try (Cursor c = v2.query("SELECT COUNT(*) FROM Orden")) {
                c.moveToFirst();
                assertEquals(1, c.getInt(0));
            }
            v2.execSQL("INSERT INTO WhatsappOutbox (ID_Orden, Estado, Intentos, ProximoIntento, LeaseHasta, CreadoEn) "
                    + "VALUES (7, 'PENDIENTE', 0, 0, 0, 0)");
            try {
                v2.execSQL("INSERT INTO WhatsappOutbox (ID_Orden, Estado, Intentos, ProximoIntento, LeaseHasta, CreadoEn) "
                        + "VALUES (7, 'PENDIENTE', 0, 0, 0, 0)");
                fail("El índice único por orden debe impedir duplicados");
            } catch (SQLiteConstraintException esperado) {
                // correcto
            }
        } finally {
            helper.close();
        }
    }

    @Test
    public void migracion2a3EsIdempotente() {
        Context ctx = ApplicationProvider.getApplicationContext();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(ctx).name(null)
                        .callback(new SupportSQLiteOpenHelper.Callback(2) {
                            @Override
                            public void onCreate(SupportSQLiteDatabase db) {
                                db.execSQL("CREATE TABLE `Orden` (`ID_Orden` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)");
                            }

                            @Override
                            public void onUpgrade(SupportSQLiteDatabase db, int o, int n) {
                            }
                        }).build());
        try {
            SupportSQLiteDatabase db = helper.getWritableDatabase();
            AppDatabase.MIGRATION_2_3.migrate(db);
            AppDatabase.MIGRATION_2_3.migrate(db); // no debe fallar si se repite
        } finally {
            helper.close();
        }
    }

    /** Columnas, índices y llaves foráneas de una tabla, en forma comparable. */
    private static List<String> describir(SupportSQLiteDatabase db, String tabla) {
        List<String> partes = new ArrayList<>();
        try (Cursor c = db.query("PRAGMA table_info(`" + tabla + "`)")) {
            while (c.moveToNext()) {
                partes.add("col:" + c.getString(c.getColumnIndexOrThrow("name"))
                        + "|" + c.getString(c.getColumnIndexOrThrow("type"))
                        + "|notnull=" + c.getInt(c.getColumnIndexOrThrow("notnull"))
                        + "|pk=" + c.getInt(c.getColumnIndexOrThrow("pk")));
            }
        }
        List<String> nombresIndices = new ArrayList<>();
        try (Cursor c = db.query("PRAGMA index_list(`" + tabla + "`)")) {
            while (c.moveToNext()) {
                String nombre = c.getString(c.getColumnIndexOrThrow("name"));
                if (nombre.startsWith("sqlite_autoindex")) continue;
                partes.add("idx:" + nombre + "|unique=" + c.getInt(c.getColumnIndexOrThrow("unique")));
                nombresIndices.add(nombre);
            }
        }
        for (String indice : nombresIndices) {
            try (Cursor c = db.query("PRAGMA index_info(`" + indice + "`)")) {
                while (c.moveToNext()) {
                    partes.add("idxcol:" + indice + "|" + c.getInt(c.getColumnIndexOrThrow("seqno"))
                            + "|" + c.getString(c.getColumnIndexOrThrow("name")));
                }
            }
        }
        try (Cursor c = db.query("PRAGMA foreign_key_list(`" + tabla + "`)")) {
            while (c.moveToNext()) {
                partes.add("fk:" + c.getString(c.getColumnIndexOrThrow("table"))
                        + "|" + c.getString(c.getColumnIndexOrThrow("from"))
                        + "|" + c.getString(c.getColumnIndexOrThrow("to"))
                        + "|del=" + c.getString(c.getColumnIndexOrThrow("on_delete")));
            }
        }
        Collections.sort(partes);
        return partes;
    }

    // ------------------------------------------------------------------ valores de dominio

    @Test
    public void estadosDeLaColaDeNotificacionesSonLosDocumentados() {
        assertEquals("PENDIENTE", WhatsappOutbox.PENDIENTE);
        assertEquals("ENVIANDO", WhatsappOutbox.ENVIANDO);
        assertEquals("ENVIADO", WhatsappOutbox.ENVIADO);
        assertEquals("FALLIDO", WhatsappOutbox.FALLIDO);
    }
}