
package hasmap;
import java.sql.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConexionMysql {
static String url = "jdbc:mysql://localhost:3306/miparqueoumg";
   static String usuario = "root";
   static String contraseña ="";

    public static Connection conectar(){
      try{
            return DriverManager.getConnection(url, usuario, contraseña);
        } catch(SQLException e) {
            System.out.println("Error de conexión: " + e.getMessage());
            return null; 
        }
    }    

    public Connection getConnection() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
}