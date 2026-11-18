package com.example.ticketmaster_mobile.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Upsert;

import com.example.ticketmaster_mobile.data.entity.Usuario;

@Dao
public interface UsuarioDao {

    @Insert
    long insertar(Usuario usuario);

    @Query("SELECT * FROM Usuario WHERE CURP = :curp LIMIT 1")
    Usuario buscarPorCurp(String curp);

    @Query("SELECT * FROM Usuario WHERE ID_Usuario = :idUsuario LIMIT 1")
    Usuario buscarPorId(int idUsuario);

    @Query("SELECT COUNT(*) FROM Usuario WHERE CURP = :curp")
    int contarPorCurp(String curp);

    @Query("UPDATE Usuario SET WhatsappChatId = :chatId WHERE ID_Usuario = :idUsuario")
    int guardarWhatsapp(int idUsuario, String chatId);

    @Query("UPDATE Usuario SET WhatsappChatId = NULL WHERE ID_Usuario = :idUsuario")
    int quitarWhatsapp(int idUsuario);
}
