// 1. Asegúrate de que el paquete sea el correcto
// (Debería ser el paquete donde está tu GestorParqueo, etc.)
package hasmap; 

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * VERSIÓN CORREGIDA
 */
public class ConexionMysql {

    // 2. CORREGIDO: URL, usuario y contraseña (sin 'ñ')
    private static final String URL = "jdbc:mysql://localhost:3306/miparqueoumg";
    private static final String USUARIO = "root";
    private static final String CONTRASENA = ""; // Sin 'ñ'

    public static void conectar() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    /**
     * 3. CORREGIDO: Este es el método que tu GestorParqueo SÍ puede usar.
     * Devuelve una nueva conexión a la base de datos.
     * @return 
     */
    public Connection getConnection() {
        Connection con = null;
        try {
            // Usamos las variables corregidas
            con = DriverManager.getConnection(URL, USUARIO, CONTRASENA);
            
        } catch (SQLException e) {
            System.out.println("Error de conexión: " + e.getMessage());
            e.printStackTrace(); // Imprime más detalles del error
        }
        return con;
    }
}