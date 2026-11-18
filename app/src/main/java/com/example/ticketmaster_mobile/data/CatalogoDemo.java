package com.example.ticketmaster_mobile.data;

import com.example.ticketmaster_mobile.data.entity.Asiento;
import com.example.ticketmaster_mobile.data.entity.DiaFestivo;
import com.example.ticketmaster_mobile.data.entity.Establecimiento;
import com.example.ticketmaster_mobile.data.entity.Funcion;
import com.example.ticketmaster_mobile.data.entity.MetodoPago;
import com.example.ticketmaster_mobile.data.entity.SeccionZona;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Catálogo DEMO para pruebas: 3 teatros, 4 cines y 5 museos con sus horarios de servicio,
 * días festivos, zonas/precios, capacidades y funciones.
 *
 * Es idempotente: {@link #asegurar} puede llamarse en cada apertura de la base de datos.
 *  - Si no hay establecimientos, los crea (con zonas, asientos de cine y festivos).
 *  - Siempre completa las funciones de los próximos {@link #HORIZONTE_DIAS} días para que el
 *    catálogo no "caduque" con el paso del tiempo.
 *
 * Los precios están en pesos mexicanos (MXN, aproximados) y las capacidades son aproximadas;
 * todo se puede ajustar editando las constantes de esta clase.
 */
public final class CatalogoDemo {

    /** Días hacia adelante para los que se programan funciones. */
    public static final int HORIZONTE_DIAS = 14;

    private static final String TEATRO = "Teatro";
    private static final String CINE = "Cine";
    private static final String MUSEO = "Museo";

    private static final String RESTRICCIONES_TEATRO =
            "Apaga o silencia tu celular. Prohibido grabar o fotografiar durante la función. "
                    + "No se permite ingresar con alimentos ni bebidas, mascotas (solo perros de asistencia) ni armas. "
                    + "Una vez iniciada la función no se permite el acceso a la sala.";

    private CatalogoDemo() {
    }

    public static void asegurar(AppDatabase db) {
        db.runInTransaction(() -> {
            if (db.metodoPagoDao().contar() == 0) {
                sembrarMetodosPago(db);
            }
            if (db.establecimientoDao().contar() == 0) {
                sembrarTeatros(db);
                sembrarCines(db);
                sembrarMuseos(db);
            }
            generarFunciones(db);
        });
    }

    // ------------------------------------------------------------------ métodos de pago

    private static void sembrarMetodosPago(AppDatabase db) {
        for (String nombre : new String[]{"Tarjeta de Crédito", "Tarjeta de Débito", "PayPal"}) {
            MetodoPago m = new MetodoPago();
            m.nombre = nombre;
            db.metodoPagoDao().insertar(m);
        }
    }

    // ------------------------------------------------------------------ teatros

    private static void sembrarTeatros(AppDatabase db) {
        // Teatro Colón — Buenos Aires (aforo ≈ 2,487). Cerrado los lunes.
        int colon = crearEstablecimiento(db, "Teatro Colón", "Cerrito 628, Buenos Aires, Argentina", TEATRO,
                "10:00", "23:00", "2,3,4,5,6,7", 10,
                "Formal: traje oscuro o saco con corbata para caballeros; vestido de coctel o largo, o traje sastre "
                        + "para damas. No se permiten shorts, bermudas, playeras sin mangas ni calzado deportivo.",
                RESTRICCIONES_TEATRO);
        crearZona(db, colon, "Platea", "General", 2800, 700, "Planta baja, frente al escenario");
        crearZona(db, colon, "Palcos", "General", 3500, 487, "Palcos laterales con la mejor visibilidad");
        crearZona(db, colon, "Tertulia", "General", 1600, 700, "Nivel superior con vista panorámica");
        crearZona(db, colon, "Paraíso", "General", 650, 600, "Galería alta, la opción más económica");
        db.establecimientoDao().recalcularCapacidad(colon);
        crearFestivos(db, colon,
                "2026-10-12|Día del Respeto a la Diversidad Cultural", "2026-11-23|Día de la Soberanía Nacional (trasladado)",
                "2026-12-08|Inmaculada Concepción de María", "2026-12-25|Navidad",
                "2027-01-01|Año Nuevo", "2027-02-08|Carnaval", "2027-02-09|Carnaval", "2027-03-24|Día de la Memoria",
                "2027-03-26|Viernes Santo", "2027-04-02|Día de las Malvinas", "2027-05-01|Día del Trabajo",
                "2027-05-25|Revolución de Mayo", "2027-07-09|Día de la Independencia",
                "2027-12-08|Inmaculada Concepción de María", "2027-12-25|Navidad");

        // Teatro de La Scala — Milán (aforo ≈ 2,030). Cerrado los domingos.
        int scala = crearEstablecimiento(db, "Teatro de la Scala", "Via Filodrammatici 2, Milán, Italia", TEATRO,
                "12:00", "23:00", "1,2,3,4,5,6", 10,
                "Etiqueta de gala: esmoquin o traje oscuro con corbata; vestido de noche o largo. "
                        + "No se recomiendan jeans, shorts ni calzado deportivo.",
                RESTRICCIONES_TEATRO);
        crearZona(db, scala, "Platea", "General", 5500, 640, "Planta baja, filas centrales");
        crearZona(db, scala, "Palchi (Palcos)", "General", 6500, 470, "Palcos históricos en herradura");
        crearZona(db, scala, "Galleria", "General", 2200, 560, "Galería superior");
        crearZona(db, scala, "Loggione", "General", 800, 360, "Gallinero: visibilidad parcial, la más económica");
        db.establecimientoDao().recalcularCapacidad(scala);
        crearFestivos(db, scala,
                "2026-11-01|Todos los Santos", "2026-12-07|San Ambrosio (patrono de Milán)",
                "2026-12-08|Inmaculada Concepción", "2026-12-25|Navidad", "2026-12-26|San Esteban",
                "2027-01-01|Año Nuevo", "2027-01-06|Epifanía", "2027-03-29|Lunes de Pascua", "2027-04-25|Día de la Liberación",
                "2027-05-01|Día del Trabajo", "2027-06-02|Día de la República", "2027-08-15|Ferragosto",
                "2027-11-01|Todos los Santos", "2027-12-07|San Ambrosio", "2027-12-08|Inmaculada Concepción",
                "2027-12-25|Navidad", "2027-12-26|San Esteban");

        // Teatro Metropólitan — CDMX (aforo ≈ 3,200). Servicio de miércoles a domingo.
        int metro = crearEstablecimiento(db, "Teatro Metropólitan", "Av. Independencia 90, Centro, Ciudad de México", TEATRO,
                "11:00", "23:00", "3,4,5,6,7", 10,
                "Casual elegante: camisa o blusa y pantalón de vestir. No se permiten shorts, playeras sin mangas, "
                        + "chanclas ni gorras dentro de la sala.",
                RESTRICCIONES_TEATRO);
        crearZona(db, metro, "Luneta", "General", 1800, 1200, "Planta baja frente al escenario");
        crearZona(db, metro, "Palco", "General", 2500, 400, "Palcos laterales");
        crearZona(db, metro, "Balcón", "General", 1200, 900, "Primer nivel superior");
        crearZona(db, metro, "Galería", "General", 650, 700, "Nivel superior, la opción más económica");
        db.establecimientoDao().recalcularCapacidad(metro);
        crearFestivos(db, metro,
                "2026-11-16|Día de la Revolución Mexicana (conmemoración)", "2026-12-25|Navidad",
                "2027-01-01|Año Nuevo", "2027-02-01|Día de la Constitución (conmemoración)",
                "2027-03-15|Natalicio de Benito Juárez (conmemoración)", "2027-05-01|Día del Trabajo",
                "2027-09-16|Día de la Independencia", "2027-11-15|Día de la Revolución Mexicana (conmemoración)",
                "2027-12-25|Navidad");
    }

    // ------------------------------------------------------------------ cines

    private static void sembrarCines(AppDatabase db) {
        sembrarCine(db, "Cinemark", "Sucursal demo, Zapopan, Jalisco", 0.95,
                "Mascotas: no se permiten (solo perros guía o de asistencia). Armas: prohibido ingresar con armas de cualquier tipo, "
                        + "incluidas las blancas. Alimentos: no se permite ingresar alimentos ni bebidas del exterior; "
                        + "solo los comprados en dulcería.",
                ServicioCine.TRADICIONAL, ServicioCine.VIP, ServicioCine.SCREEN_X);

        sembrarCine(db, "Cinépolis", "Sucursal demo, Guadalajara Centro, Jalisco", 1.0,
                "Mascotas: no se permiten (solo perros guía o de asistencia). Armas: prohibido el acceso con armas; "
                        + "puede haber revisión de bolsas. Alimentos: solo los adquiridos en dulcería; "
                        + "no se permiten alimentos ni bebidas del exterior.",
                ServicioCine.TRADICIONAL, ServicioCine.PLUUS, ServicioCine.VIP, ServicioCine.MACRO_XE,
                ServicioCine.JUNIOR, ServicioCine.CUATRO_DX, ServicioCine.VR, ServicioCine.SCREEN_X);

        sembrarCine(db, "Cinemex", "Sucursal demo, Guadalajara Sur, Jalisco", 1.05,
                "Mascotas: no se permiten (excepto animales de servicio). Armas: prohibido el ingreso con armas o "
                        + "artículos punzocortantes. Alimentos: no se permiten alimentos ni bebidas del exterior; "
                        + "se pueden consumir los de la dulcería dentro de la sala.",
                ServicioCine.TRADICIONAL, ServicioCine.VIP, ServicioCine.IMAX, ServicioCine.CUATRO_DX);

        sembrarCine(db, "AMC", "Sucursal demo, Tlaquepaque, Jalisco", 1.15,
                "Mascotas: no se permiten (excepto animales de servicio acreditados). Armas: prohibido portar armas de fuego "
                        + "o blancas. Alimentos: no se permite ingresar alimentos ni bebidas del exterior; "
                        + "los de la tienda AMC sí pueden consumirse en sala.",
                ServicioCine.TRADICIONAL, ServicioCine.VIP, ServicioCine.IMAX);
    }

    private static void sembrarCine(AppDatabase db, String nombre, String ubicacion, double factorPrecio,
                                    String restricciones, ServicioCine... servicios) {
        int idCine = crearEstablecimiento(db, nombre, ubicacion, CINE, "10:00", "23:30", "1,2,3,4,5,6,7", 6,
                "", restricciones);
        for (ServicioCine s : servicios) {
            double precio = Math.round(s.precioBase * factorPrecio / 5.0) * 5.0;
            int idZona = crearZona(db, idCine, s.nombre, "Numerado", precio, s.capacidad(), s.descripcion);
            crearAsientos(db, idZona, s.filas, s.columnas);
        }
        db.establecimientoDao().recalcularCapacidad(idCine);
    }

    // ------------------------------------------------------------------ museos

    private static void sembrarMuseos(AppDatabase db) {
        // Louvre — cerrado los martes. Aforo ≈ 30,000/día repartido en 5 franjas de entrada.
        int louvre = crearEstablecimiento(db, "Museo del Louvre", "Rue de Rivoli, París, Francia", MUSEO,
                "09:00", "18:00", "1,3,4,5,6,7", 5, "",
                "No se permiten mochilas grandes ni maletas, objetos punzocortantes, armas, alimentos ni bebidas. "
                        + "Prohibidos el flash y los trípodes. Se revisan los bolsos en el acceso. "
                        + "No se admiten mascotas (solo perros de asistencia).");
        crearZona(db, louvre, "Entrada General", "General", 450, 6000, "Acceso a las colecciones permanentes");
        db.establecimientoDao().recalcularCapacidad(louvre);
        crearFestivos(db, louvre,
                "2026-11-01|Todos los Santos", "2026-11-11|Armisticio de 1918", "2026-12-25|Navidad",
                "2027-01-01|Año Nuevo", "2027-03-29|Lunes de Pascua", "2027-05-01|Día del Trabajo", "2027-05-06|Ascensión",
                "2027-05-08|Victoria de 1945", "2027-05-17|Lunes de Pentecostés", "2027-07-14|Fiesta Nacional",
                "2027-08-15|Asunción", "2027-11-01|Todos los Santos", "2027-11-11|Armisticio de 1918", "2027-12-25|Navidad");

        // The Met — cerrado los miércoles. ≈ 20,000/día en 4 franjas.
        int met = crearEstablecimiento(db, "Museo Metropolitano de Nueva York", "1000 5th Ave, Nueva York, EE. UU.", MUSEO,
                "10:00", "17:00", "1,2,4,5,6,7", 5, "",
                "Mochilas grandes y paraguas largos deben dejarse en guardarropa. No se permiten alimentos ni bebidas en las salas, "
                        + "ni trípodes. Fotografía sin flash permitida. No se admiten mascotas (solo animales de servicio).");
        crearZona(db, met, "Entrada General", "General", 560, 5000, "Acceso a las colecciones permanentes");
        db.establecimientoDao().recalcularCapacidad(met);
        crearFestivos(db, met,
                "2026-11-26|Día de Acción de Gracias", "2026-12-25|Navidad", "2027-01-01|Año Nuevo",
                "2027-11-25|Día de Acción de Gracias", "2027-12-25|Navidad");

        // Museos Vaticanos — cerrados los domingos. ≈ 28,000/día en 4 franjas.
        int vaticano = crearEstablecimiento(db, "Museo Vaticano", "Viale Vaticano, Ciudad del Vaticano", MUSEO,
                "09:00", "18:00", "1,2,3,4,5,6", 5, "",
                "Vestimenta obligatoria: hombros y rodillas cubiertos (sin shorts, minifaldas ni camisetas sin mangas). "
                        + "No se permite sombrero dentro de las salas, mochilas grandes ni palos de selfie. "
                        + "Silencio y sin fotografías en la Capilla Sixtina. Se revisan bolsos en el acceso.");
        crearZona(db, vaticano, "Entrada General", "General", 400, 7000, "Museos y Capilla Sixtina");
        db.establecimientoDao().recalcularCapacidad(vaticano);
        crearFestivos(db, vaticano,
                "2026-11-01|Todos los Santos", "2026-12-08|Inmaculada Concepción", "2026-12-25|Navidad",
                "2026-12-26|San Esteban", "2027-01-01|Año Nuevo", "2027-01-06|Epifanía", "2027-02-11|Aniversario de los Pactos de Letrán",
                "2027-03-19|San José", "2027-03-29|Lunes de Pascua", "2027-05-01|Día del Trabajo", "2027-06-29|San Pedro y San Pablo",
                "2027-08-15|Asunción", "2027-11-01|Todos los Santos", "2027-12-08|Inmaculada Concepción", "2027-12-25|Navidad",
                "2027-12-26|San Esteban");

        // Museo Nacional de Antropología — cerrado los lunes. ≈ 7,500/día en 5 franjas.
        int mna = crearEstablecimiento(db, "Museo Nacional de Antropología", "Av. Paseo de la Reforma s/n, Chapultepec, Ciudad de México", MUSEO,
                "09:00", "18:00", "2,3,4,5,6,7", 5, "",
                "No se permiten mochilas grandes, alimentos ni bebidas, mascotas (solo perros de asistencia), armas ni objetos "
                        + "punzocortantes. Prohibidos el flash y los trípodes; el equipo profesional requiere permiso.");
        crearZona(db, mna, "Entrada General", "General", 100, 1500, "Acceso a todas las salas permanentes");
        db.establecimientoDao().recalcularCapacidad(mna);
        crearFestivos(db, mna,
                "2026-11-16|Día de la Revolución Mexicana (conmemoración)", "2026-12-25|Navidad",
                "2027-01-01|Año Nuevo", "2027-02-01|Día de la Constitución (conmemoración)",
                "2027-03-15|Natalicio de Benito Juárez (conmemoración)", "2027-05-01|Día del Trabajo",
                "2027-09-16|Día de la Independencia", "2027-11-15|Día de la Revolución Mexicana (conmemoración)",
                "2027-12-25|Navidad");

        // MNAC Barcelona — cerrado los lunes. ≈ 2,400/día en 3 franjas.
        int mnac = crearEstablecimiento(db, "Museu Nacional d'Art de Catalunya", "Palau Nacional, Parc de Montjuïc, Barcelona, España", MUSEO,
                "10:00", "18:00", "2,3,4,5,6,7", 5, "",
                "No se permiten mochilas grandes (consigna gratuita), alimentos ni bebidas en las salas, trípodes ni flash. "
                        + "No se admiten mascotas (solo perros de asistencia). Prohibido tocar las obras.");
        crearZona(db, mnac, "Entrada General", "General", 240, 800, "Colección permanente");
        db.establecimientoDao().recalcularCapacidad(mnac);
        crearFestivos(db, mnac,
                "2026-10-12|Fiesta Nacional de España", "2026-11-01|Todos los Santos", "2026-12-06|Día de la Constitución",
                "2026-12-08|Inmaculada Concepción", "2026-12-25|Navidad", "2026-12-26|San Esteban",
                "2027-01-01|Año Nuevo", "2027-01-06|Reyes Magos", "2027-03-26|Viernes Santo", "2027-03-29|Lunes de Pascua",
                "2027-05-01|Día del Trabajo", "2027-06-24|San Juan", "2027-08-15|Asunción", "2027-09-11|Diada de Cataluña",
                "2027-10-12|Fiesta Nacional de España", "2027-11-01|Todos los Santos", "2027-12-06|Día de la Constitución",
                "2027-12-08|Inmaculada Concepción", "2027-12-25|Navidad", "2027-12-26|San Esteban");
    }

    // ------------------------------------------------------------------ funciones (rolling)

    private static final String[][] OBRAS_COLON = {
            {"La Traviata", "Ópera de G. Verdi · 3 actos · 2 h 45 min", "20:00"},
            {"El Lago de los Cisnes", "Ballet de P. I. Chaikovski · 2 h 30 min", "20:00"},
            {"Carmen", "Ópera de G. Bizet · 4 actos · 3 h", "19:30"}};
    private static final String[][] OBRAS_SCALA = {
            {"Aida", "Ópera de G. Verdi · 4 actos · 3 h", "20:00"},
            {"El Barbero de Sevilla", "Ópera cómica de G. Rossini · 2 actos · 3 h", "19:30"},
            {"Romeo y Julieta", "Ballet de S. Prokófiev · 2 h 40 min", "20:00"}};
    private static final String[][] OBRAS_METROPOLITAN = {
            {"Don Juan Tenorio", "Drama de José Zorrilla · 2 h 15 min", "20:30"},
            {"El Cascanueces", "Ballet de P. I. Chaikovski · 2 h", "19:00"},
            {"Bodas de Sangre", "Tragedia de F. García Lorca · 1 h 50 min", "20:00"}};

    private static final String[][] PELICULAS = {
            {"Intensamente 2", "A", "Animación · 96 min · Doblada al español"},
            {"Duna: Parte Dos", "B", "Ciencia ficción · 166 min · Subtitulada"},
            {"Oppenheimer", "B15", "Drama histórico · 180 min · Subtitulada"},
            {"Deadpool y Wolverine", "C", "Acción y comedia · 127 min · Subtitulada"},
            {"Avatar: El camino del agua", "B", "Ciencia ficción · 192 min · Doblada al español"}};
    private static final String[] HORARIOS_CINE = {"13:30", "16:00", "18:30", "21:00"};
    private static final int PELICULAS_POR_CINE = 4;
    private static final int FUNCIONES_POR_PELICULA = 3;

    private static final String[] FRANJAS_LOUVRE = {"09:00", "11:00", "13:00", "15:00", "17:00"};
    private static final String[] FRANJAS_MET = {"10:00", "12:00", "14:00", "16:00"};
    private static final String[] FRANJAS_VATICANO = {"09:00", "11:00", "13:00", "15:00"};
    private static final String[] FRANJAS_MNA = {"09:00", "11:00", "13:00", "15:00", "17:00"};
    private static final String[] FRANJAS_MNAC = {"10:00", "12:00", "14:00"};

    /** Programa funciones desde hoy (o desde la última existente) hasta hoy + HORIZONTE_DIAS. */
    private static void generarFunciones(AppDatabase db) {
        Date hoy = FechaUtils.parsearFecha(FechaUtils.fechaIso(new Date()));
        Date hasta = FechaUtils.sumarDias(hoy, HORIZONTE_DIAS);

        for (Establecimiento e : db.establecimientoDao().listarTodos()) {
            Date desde = hoy;
            String ultima = db.funcionDao().ultimaFechaHora(e.idEstablecimiento);
            if (ultima != null) {
                Date siguiente = FechaUtils.sumarDias(FechaUtils.parsearFecha(ultima), 1);
                if (siguiente.after(desde)) desde = siguiente;
            }
            if (desde.after(hasta)) continue;

            Set<Integer> diasServicio = FechaUtils.parsearDias(e.diasLaborales);
            Set<String> festivos = new HashSet<>(db.diaFestivoDao().listarFechas(e.idEstablecimiento));

            List<Date> diasAbiertos = new ArrayList<>();
            for (Date d = desde; !d.after(hasta); d = FechaUtils.sumarDias(d, 1)) {
                if (diasServicio.contains(FechaUtils.diaIso(d)) && !festivos.contains(FechaUtils.fechaIso(d))) {
                    diasAbiertos.add(d);
                }
            }

            for (Date dia : diasAbiertos) {
                switch (e.nombre) {
                    case "Teatro Colón":
                        programarTeatro(db, e, dia, OBRAS_COLON);
                        break;
                    case "Teatro de la Scala":
                        programarTeatro(db, e, dia, OBRAS_SCALA);
                        break;
                    case "Teatro Metropólitan":
                        programarTeatro(db, e, dia, OBRAS_METROPOLITAN);
                        break;
                    case "Museo del Louvre":
                        programarMuseo(db, e, dia, FRANJAS_LOUVRE);
                        break;
                    case "Museo Metropolitano de Nueva York":
                        programarMuseo(db, e, dia, FRANJAS_MET);
                        break;
                    case "Museo Vaticano":
                        programarMuseo(db, e, dia, FRANJAS_VATICANO);
                        break;
                    case "Museo Nacional de Antropología":
                        programarMuseo(db, e, dia, FRANJAS_MNA);
                        break;
                    case "Museu Nacional d'Art de Catalunya":
                        programarMuseo(db, e, dia, FRANJAS_MNAC);
                        break;
                    default:
                        if (CINE.equals(e.tipoEstablecimiento)) programarCine(db, e, dia);
                        break;
                }
            }
        }
    }

    /** Cada día se presentan 2 de las 3 obras (rotan); los domingos hay matiné a las 17:00. */
    private static void programarTeatro(AppDatabase db, Establecimiento e, Date dia, String[][] obras) {
        long diaAbsoluto = dia.getTime() / 86_400_000L;
        for (int k = 0; k < obras.length; k++) {
            if (diaAbsoluto % obras.length == k) continue; // la obra "descansa" este día
            String hora = FechaUtils.diaIso(dia) == 7 ? "17:00" : obras[k][2];
            insertarFuncion(db, e, obras[k][0], dia, hora, "", obras[k][1]);
        }
    }

    private static void programarCine(AppDatabase db, Establecimiento e, Date dia) {
        int inicio = Math.abs(e.nombre.hashCode()) % PELICULAS.length;
        for (int f = 0; f < PELICULAS_POR_CINE; f++) {
            String[] peli = PELICULAS[(inicio + f) % PELICULAS.length];
            for (int t = 0; t < FUNCIONES_POR_PELICULA; t++) {
                String hora = HORARIOS_CINE[(f + t) % HORARIOS_CINE.length];
                insertarFuncion(db, e, peli[0], dia, hora, peli[1], peli[2]);
            }
        }
    }

    private static void programarMuseo(AppDatabase db, Establecimiento e, Date dia, String[] franjas) {
        for (String hora : franjas) {
            insertarFuncion(db, e, "Visita general", dia, hora, "", "Entrada con horario asignado · Visita libre");
        }
    }

    private static void insertarFuncion(AppDatabase db, Establecimiento e, String nombre, Date dia,
                                        String hora, String clasificacion, String detalle) {
        Funcion f = new Funcion();
        f.idEstablecimiento = e.idEstablecimiento;
        f.nombreEvento = nombre;
        f.fechaHora = FechaUtils.fechaIso(dia) + " " + hora;
        f.clasificacion = clasificacion;
        f.detalle = detalle;
        db.funcionDao().insertar(f);
    }

    // ------------------------------------------------------------------ helpers de inserción

    private static int crearEstablecimiento(AppDatabase db, String nombre, String ubicacion, String tipo,
                                            String apertura, String cierre, String dias, int maxBoletos,
                                            String vestimenta, String restricciones) {
        Establecimiento e = new Establecimiento();
        e.nombre = nombre;
        e.ubicacion = ubicacion;
        e.tipoEstablecimiento = tipo;
        e.horaApertura = apertura;
        e.horaCierre = cierre;
        e.diasLaborales = dias;
        e.maxBoletosPorUsuario = maxBoletos;
        e.vestimenta = vestimenta;
        e.restricciones = restricciones;
        return (int) db.establecimientoDao().insertar(e);
    }

    private static int crearZona(AppDatabase db, int idEstablecimiento, String nombre, String tipo,
                                 double precio, int capacidad, String descripcion) {
        SeccionZona z = new SeccionZona();
        z.idEstablecimiento = idEstablecimiento;
        z.nombreZona = nombre;
        z.tipoZona = tipo;
        z.precioBase = precio;
        z.capacidad = capacidad;
        z.descripcion = descripcion;
        return (int) db.seccionZonaDao().insertar(z);
    }

    private static void crearAsientos(AppDatabase db, int idZona, int filas, int columnas) {
        for (int f = 0; f < filas; f++) {
            char letra = (char) ('A' + f);
            for (int c = 1; c <= columnas; c++) {
                Asiento a = new Asiento();
                a.idSeccionZona = idZona;
                a.fila = String.valueOf(letra);
                a.numero = String.valueOf(c);
                a.codigoAsiento = letra + String.valueOf(c);
                db.asientoDao().insertar(a);
            }
        }
    }

    /** Cada elemento tiene el formato "yyyy-MM-dd|Descripción". */
    private static void crearFestivos(AppDatabase db, int idEstablecimiento, String... fechasYDescripciones) {
        for (String par : fechasYDescripciones) {
            String[] partes = par.split("\\|", 2);
            DiaFestivo d = new DiaFestivo();
            d.idEstablecimiento = idEstablecimiento;
            d.fecha = partes[0];
            d.descripcion = partes[1];
            db.diaFestivoDao().insertar(d);
        }
    }
}
