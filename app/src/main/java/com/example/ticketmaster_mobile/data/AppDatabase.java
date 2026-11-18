package com.example.ticketmaster_mobile.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.ticketmaster_mobile.data.dao.AsientoDao;
import com.example.ticketmaster_mobile.data.dao.BoletoDao;
import com.example.ticketmaster_mobile.data.dao.DiaFestivoDao;
import com.example.ticketmaster_mobile.data.dao.EstablecimientoDao;
import com.example.ticketmaster_mobile.data.dao.FuncionDao;
import com.example.ticketmaster_mobile.data.dao.MetodoPagoDao;
import com.example.ticketmaster_mobile.data.dao.OrdenDao;
import com.example.ticketmaster_mobile.data.dao.PagoDao;
import com.example.ticketmaster_mobile.data.dao.SeccionZonaDao;
import com.example.ticketmaster_mobile.data.dao.UsuarioDao;
import com.example.ticketmaster_mobile.data.dao.WhatsappOutboxDao;
import com.example.ticketmaster_mobile.data.entity.Asiento;
import com.example.ticketmaster_mobile.data.entity.Boleto;
import com.example.ticketmaster_mobile.data.entity.DiaFestivo;
import com.example.ticketmaster_mobile.data.entity.Establecimiento;
import com.example.ticketmaster_mobile.data.entity.Funcion;
import com.example.ticketmaster_mobile.data.entity.MetodoPago;
import com.example.ticketmaster_mobile.data.entity.Orden;
import com.example.ticketmaster_mobile.data.entity.Pago;
import com.example.ticketmaster_mobile.data.entity.SeccionZona;
import com.example.ticketmaster_mobile.data.entity.Usuario;
import com.example.ticketmaster_mobile.data.entity.WhatsappOutbox;

/**
 * Base de datos Room de la aplicación. Expone un DAO por cada una de las 10 tablas
 * del esquema. Se usa como Singleton para evitar abrir varias conexiones a la vez.
 */
@Database(
        entities = {
                Usuario.class,
                Establecimiento.class,
                SeccionZona.class,
                Asiento.class,
                Funcion.class,
                Orden.class,
                Boleto.class,
                MetodoPago.class,
                Pago.class,
                DiaFestivo.class,
                WhatsappOutbox.class
        },
        version = 4,
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "ticketmaster.db";
    private static volatile AppDatabase INSTANCE;

    public abstract UsuarioDao usuarioDao();
    public abstract EstablecimientoDao establecimientoDao();
    public abstract SeccionZonaDao seccionZonaDao();
    public abstract AsientoDao asientoDao();
    public abstract FuncionDao funcionDao();
    public abstract OrdenDao ordenDao();
    public abstract BoletoDao boletoDao();
    public abstract MetodoPagoDao metodoPagoDao();
    public abstract PagoDao pagoDao();
    public abstract DiaFestivoDao diaFestivoDao();
    public abstract WhatsappOutboxDao whatsappOutboxDao();

    /**
     * v2 -> v3: cola de notificaciones de WhatsApp (WAHA). Solo AÑADE una tabla: no se pierde ningún
     * dato existente. Las compras anteriores a esta versión no se notifican (no hay backfill).
     * El SQL debe coincidir exactamente con el esquema que Room genera para {@link WhatsappOutbox}.
     */
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `WhatsappOutbox` (" +
                    "`ID_Outbox` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`ID_Orden` INTEGER NOT NULL, " +
                    "`Estado` TEXT NOT NULL, " +
                    "`Intentos` INTEGER NOT NULL, " +
                    "`ProximoIntento` INTEGER NOT NULL, " +
                    "`LeaseHasta` INTEGER NOT NULL, " +
                    "`UltimoError` TEXT, " +
                    "`MensajeIdWaha` TEXT, " +
                    "`CreadoEn` INTEGER NOT NULL, " +
                    "`EnviadoEn` INTEGER, " +
                    "FOREIGN KEY(`ID_Orden`) REFERENCES `Orden`(`ID_Orden`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_WhatsappOutbox_ID_Orden` " +
                    "ON `WhatsappOutbox` (`ID_Orden`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_WhatsappOutbox_Estado_ProximoIntento` " +
                    "ON `WhatsappOutbox` (`Estado`, `ProximoIntento`)");
        }
    };

    /** v3 -> v4: número de WhatsApp vinculado por el usuario. Solo AÑADE una columna nullable. */
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `Usuario` ADD COLUMN `WhatsappChatId` TEXT");
        }
    };

    /**
     * SOLO PRUEBAS INSTRUMENTADAS: sustituye la instancia singleton (p. ej. por una base en memoria)
     * para que las pantallas, el repositorio y el worker trabajen sobre datos controlados y no sobre
     * los del usuario. Pasar null restablece el comportamiento normal.
     */
    @VisibleForTesting
    public static void setInstanceForTesting(AppDatabase instancia) {
        synchronized (AppDatabase.class) {
            INSTANCE = instancia;
        }
    }

    public static AppDatabase getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    DATABASE_NAME
                            )
                            // v1 -> v2 (catálogo demo con horarios, festivos, aforo por zona):
                            // en fase de pruebas se recrea la base. Las cuentas registradas en v1 se
                            // pierden; basta volver a registrarse.
                            // v2 -> v3 es una migración real: conserva usuarios, órdenes y pagos.
                            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                            .fallbackToDestructiveMigrationFrom(1)
                            .addCallback(SEED_CALLBACK)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Garantiza que existan los métodos de pago y el catálogo demo (teatros, cines y museos) y que
     * haya funciones programadas para los próximos días. Se ejecuta en cada apertura de la base de
     * datos, en segundo plano; {@link CatalogoDemo#asegurar} es idempotente.
     */
    private static final RoomDatabase.Callback SEED_CALLBACK = new RoomDatabase.Callback() {
        @Override
        public void onOpen(@NonNull SupportSQLiteDatabase db) {
            super.onOpen(db);
            DatabaseExecutor.IO_EXECUTOR.execute(() -> {
                // INSTANCE se asigna justo después de build(); esperamos un instante si aún es null
                AppDatabase instancia = INSTANCE;
                for (int i = 0; instancia == null && i < 50; i++) {
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    instancia = INSTANCE;
                }
                if (instancia == null) return;
                // El catálogo ahora viene de Firestore (ver CatalogoFirestore.sincronizar).
                // CatalogoDemo.asegurar(instancia);
            });
        }
    };
}
