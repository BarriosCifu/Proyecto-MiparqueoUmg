// El paquete debe ser el de tu proyecto, no 'hasmap'
package com.mycompany.miparqueoumg; 

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * VERSIÓN CORREGIDA
 */
public class ConexionMysql {

    private static final String URL = "jdbc:mysql://localhost:3306/miparqueoumg";
    private static final String USUARIO = "root";
    private static final String CONTRASENA = ""; // Sin 'ñ'

    /**
     * Este es el método que tu GestorParqueo SÍ puede usar.
     * @return 
     */
    public Connection getConnection() {
        Connection con = null;
        try {
            con = DriverManager.getConnection(URL, USUARIO, CONTRASENA);
        } catch (SQLException e) {
            System.out.println("Error de conexión: " + e.getMessage());
            e.printStackTrace();
        }
        return con;
    }
}