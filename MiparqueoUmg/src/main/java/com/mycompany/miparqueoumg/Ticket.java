/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.miparqueoumg;
import java.time.LocalDateTime;
/**
 *
 * @author barri
 */
public class Ticket {
    private int id;
    private String placa;
    private String areaId; // Ej: "A01"
    private LocalDateTime fechaIngreso;
    private LocalDateTime fechaSalida;
    private String modo; // "FLAT" o "VARIABLE"
    private double monto;
    private String estado; // "ACTIVO", "PAGADO", "CERRADO"
    private long horas; // No es de la BD, es temporal para el recibo
    
    // --- Constructor para tickets NUEVOS ---
    public Ticket(String placa, String areaId, String modo) {
        this.placa = placa;
        this.areaId = areaId;
        this.modo = modo;
        this.fechaIngreso = LocalDateTime.now();
        this.estado = (modo.equals("FLAT")) ? "PAGADO" : "ACTIVO";
        this.monto = (modo.equals("FLAT")) ? 10.00 : 0.00;
    }
    
    // ⬇️⬇️⬇️⬇️⬇️⬇️⬇️⬇️⬇️⬇️⬇️⬇️⬇️⬇️⬇️⬇️⬇️
    //      CONSTRUCTOR AÑADIDO
    // ⬇️⬇️⬇️⬇️⬇️⬇️⬇️⬇️⬇️⬇️⬇️⬇️⬇️⬇️⬇️⬇️⬇️
    /**
     * Constructor para RE-CREAR un ticket desde la Base de Datos.
     * @param id
     * @param placa
     * @param areaId
     * @param fechaIngreso
     * @param fechaSalida
     * @param monto
     * @param modo
     * @param estado
     */
    public Ticket(int id, String placa, String areaId, LocalDateTime fechaIngreso, 
                  LocalDateTime fechaSalida, String modo, double monto, String estado) {
        this.id = id;
        this.placa = placa;
        this.areaId = areaId;
        this.fechaIngreso = fechaIngreso;
        this.fechaSalida = fechaSalida;
        this.modo = modo;
        this.monto = monto;
        this.estado = estado;
    }
    
    // --- Getters y Setters ---
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getPlaca() {
        return placa;
    }
    
    public void setPlaca(String placa) {
        this.placa = placa;
    }
    
    public String getAreaId() {
        return areaId;
    }
    
    public void setAreaId(String areaId) {
        this.areaId = areaId;
    }
    
    public LocalDateTime getFechaIngreso() {
        return fechaIngreso;
    }
    
    public void setFechaIngreso(LocalDateTime fechaIngreso) {
        this.fechaIngreso = fechaIngreso;
    }
    
    public LocalDateTime getFechaSalida() {
        return fechaSalida;
    }
    
    public void setFechaSalida(LocalDateTime fechaSalida) {
        this.fechaSalida = fechaSalida;
    }
    
    public String getModo() {
        return modo;
    }
    
    public void setModo(String modo) {
        this.modo = modo;
    }
    
    public double getMonto() {
        return monto;
    }
    
    public void setMonto(double monto) {
        this.monto = monto;
    }
    
    public String getEstado() {
        return estado;
    }
    
    public void setEstado(String estado) {
        this.estado = estado;
    }
    
    public long getHoras() {
        return horas;
    }
    
    public void setHoras(long horas) {
        this.horas = horas;
    }
}