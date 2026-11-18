package com.example.ticketmaster_mobile.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.Nullable;

/**
 * Tabla Usuario.
 *
 * NOTA TÉCNICA (desviación informada del esquema recibido):
 * El esquema original no incluye una columna de contraseña para la CURP. Dado que
 * la pantalla de Registro ya solicita y valida una contraseña (ValidationsUtils),
 * se añadió la columna PasswordHash para no desechar esa validación y para no dejar
 * el login abierto a cualquiera que conozca una CURP válida. Se almacena como hash
 * SHA-256, nunca en texto plano. Si se prefiere el comportamiento original (login
 * solo por existencia de CURP), basta con ignorar la validación de contraseña en
 * LoginActivity; la columna puede quedar sin usarse.
 */
@Entity(
        tableName = "Usuario",
        indices = {@Index(value = "CURP", unique = true)}
)
public class Usuario {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "ID_Usuario")
    public int idUsuario;

    @NonNull
    @ColumnInfo(name = "Nombre")
    public String nombre = "";

    @NonNull
    @ColumnInfo(name = "ApellidoPaterno")
    public String apellidoPaterno = "";

    @NonNull
    @ColumnInfo(name = "ApellidoMaterno")
    public String apellidoMaterno = "";

    @NonNull
    @ColumnInfo(name = "CURP")
    public String curp = "";

    @NonNull
    @ColumnInfo(name = "FechaNacimiento") // formato ISO: yyyy-MM-dd
    public String fechaNacimiento = "";

    @NonNull
    @ColumnInfo(name = "Estado")
    public String estado = "";

    @NonNull
    @ColumnInfo(name = "Sexo") // "Masculino" | "Femenino"
    public String sexo = "";

    @NonNull
    @ColumnInfo(name = "FechaRegistro")
    public String fechaRegistro = "";

    // Columna adicional (ver nota técnica arriba). Almacena el hash SHA-256 de la contraseña.
    @NonNull
    @ColumnInfo(name = "PasswordHash")
    public String passwordHash = "";

    /** chatId de WhatsApp vinculado por el usuario (p. ej. "5213322180006@c.us"); null si no lo ha vinculado. */
    @Nullable
    @ColumnInfo(name = "WhatsappChatId")
    public String whatsappChatId;
}
