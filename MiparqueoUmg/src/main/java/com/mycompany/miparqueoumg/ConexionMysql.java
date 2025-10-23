/*
 * Conexion de sql(es la conexion a la base de datos)
 * 
 */
package com.mycompany.miparqueoumg;
import java.sql.Connection;
import java.sql.DriverManager

public class ConexionMysql {
    public static Conection Conectar (){
    Conection cn=null;
    try{
     Class.forName("com.mysql.jdbc.Driver");
     cn=DriverManager("jdbc:mysql://localhost")
    } catch (Exception e){
        System.out.println(String.valueOf(e));
    }
        
    }
    
}
