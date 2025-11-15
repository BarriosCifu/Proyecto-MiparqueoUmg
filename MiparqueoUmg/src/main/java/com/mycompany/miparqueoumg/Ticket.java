package com.mycompany.miparqueoumg;
import java.time.LocalDateTime;

/**
 * Clase Ticket - Representa un ticket de estacionamiento
 * @author barri
 */
public class Ticket {
    private int id;
    private String placa;
    private String areaId; // Ej: "A01"
    private String spotId; // Ej: "A01-001"
    private LocalDateTime fechaIngreso;
    private LocalDateTime fechaSalida;
    private String modo; // "FLAT" o "VARIABLE"
    private double monto;
    private String estado; // "ACTIVO", "PAGADO", "CERRADO"
    private long horas; // No es de la BD, es temporal para el recibo
    
    // --- Constructor para tickets NUEVOS (CORREGIDO) ---
    /**
     * Constructor para crear un ticket nuevo al momento del ingreso.
     * @param placa Placa del vehículo
     * @param areaId ID del área (ej: "A01")
     * @param spotId ID del spot asignado (ej: "A01-001")
     * @param modo Modo de tarifa ("FLAT" o "VARIABLE")
     */
    public Ticket(String placa, String areaId, String spotId, String modo) {
        this.placa = placa;
        this.areaId = areaId;
        this.spotId = spotId;
        this.modo = modo;
        this.fechaIngreso = LocalDateTime.now();
        this.estado = (modo.equalsIgnoreCase("FLAT")) ? "PAGADO" : "ACTIVO"; // ✅ CORREGIDO
        this.monto = (modo.equalsIgnoreCase("FLAT")) ? 10.00 : 0.00; // ✅ CORREGIDO
    }
    
    // --- Constructor para RE-CREAR un ticket desde la Base de Datos ---
    /**
     * Constructor completo para recrear un ticket desde la BD.
     * @param id ID del ticket
     * @param placa Placa del vehículo
     * @param areaId ID del área
     * @param spotId ID del spot
     * @param fechaIngreso Fecha y hora de ingreso
     * @param fechaSalida Fecha y hora de salida (puede ser null)
     * @param modo Modo de tarifa
     * @param monto Monto a pagar
     * @param estado Estado del ticket
     */
    public Ticket(int id, String placa, String areaId, String spotId,
                  LocalDateTime fechaIngreso, LocalDateTime fechaSalida, 
                  String modo, double monto, String estado) {
        this.id = id;
        this.placa = placa;
        this.areaId = areaId;
        this.spotId = spotId;
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
    
    public String getSpotId() {
        return spotId;
    }
    
    public void setSpotId(String spotId) {
        this.spotId = spotId;
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