/*
 * Conexion de sql(es la conexion a la base de datos)
 * 
 */
package hasmap;
import java.sql.Connection;
import java.sql.DriverManager;

public class ConexionMysql {
    public static Connection Conectar (){
    Connection cn=null;
    try{
     Class.forName("com.mysql.jdbc.Driver");
     cn=DriverManager.getConnection("jdbc:mysql://localhost:3306/miparqueoumg","root","")
    } catch (Exception e){
        System.out.println(String.valueOf(e));
    }
       return cn; 
    }
    
}
