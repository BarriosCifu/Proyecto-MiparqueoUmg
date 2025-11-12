/*
 * Isaias Barrios
 */
package com.mycompany.miparqueoumg;

import hasmap.ConexionMysql;

public class MiparqueoUmg {
    public static void main(String[] args) {
    Login ingresar = new Login();
    ingresar.setVisible(true);
    ConexionMysql dbc = new ConexionMysql();
    dbc.conectar();
    }
}
