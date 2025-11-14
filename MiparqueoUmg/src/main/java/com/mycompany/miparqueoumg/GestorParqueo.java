package com.mycompany.miparqueoumg;

import hasmap.ConexionMysql;
import com.mycompany.miparqueoumg.Ticket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import javax.swing.JLabel;

public class GestorParqueo {
    private final ConexionMysql conexion;
    
    public GestorParqueo() {
        this.conexion = new ConexionMysql(); 
    }
    
    /**
     * Este es el método que llamará tu botón "Registrar Ingreso".
     */
    public String registrarIngreso(String placa, String areaNombre) {      
        // --- 1. Obtener el modo de tarifa actual ---
        String modoTarifaActual = "FLAT"; // O "VARIABLE"
        
        // --- 2. Convertir nombre de área (ej: "MOTOS") a ID (ej: "A01") ---
        String areaId = convertirAreaNombreAId(areaNombre);
        if (areaId == null) {
            return "Error: El área seleccionada no es válida.";
        }
        
        // --- 3. Validar Ocupación ---
        if (estaAreaLlena(areaId)) {
            return "Error: El área de " + areaNombre + " está llena.";
        }
        
        // --- 4. Validar Reingreso ---
        Ticket ticketExistente = buscarTicketActivo(placa);
        if (ticketExistente != null) {
            // Lógica de reingreso para FLAT
            if (modoTarifaActual.equals("FLAT") && ticketExistente.getModo().equals("FLAT")) {
                // Comprobar si es del mismo día
                if (ticketExistente.getFechaIngreso().toLocalDate().isEqual(java.time.LocalDate.now())) {
                    return "Reingreso (Tarifa Plana): Bienvenido de nuevo.";
                }
            }
            
            // Lógica de reingreso para VARIABLE
            if (modoTarifaActual.equals("VARIABLE") && ticketExistente.getEstado().equals("ACTIVO")) {
                return "Reingreso (Tarifa Variable): Ya tiene un ticket activo.";
            }
        }
        
        // --- 5. Crear Ticket Nuevo ---
        Ticket nuevoTicket = new Ticket(placa, areaId, modoTarifaActual);
        
        // --- 6. Guardar en Base de Datos ---
        String sql = "INSERT INTO ticket (placa, area_id, fecha_ingreso, modo, monto, estado) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection con = conexion.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setString(1, nuevoTicket.getPlaca());
            ps.setString(2, nuevoTicket.getAreaId());
            ps.setObject(3, nuevoTicket.getFechaIngreso());
            ps.setString(4, nuevoTicket.getModo());
            ps.setDouble(5, nuevoTicket.getMonto());
            ps.setString(6, nuevoTicket.getEstado());  
            ps.executeUpdate();
            
            return "Ingreso Registrado (Modo " + modoTarifaActual + "). Monto: Q" + nuevoTicket.getMonto();  
        } catch (Exception e) {
            return "Error al guardar en la base de datos: " + e.getMessage();
        }
    }
    
    /**
     * Este es el método que llamará tu botón "Registrar Salida".
     */
    public String registrarSalida(String placa) {
        return "Lógica de salida aún no implementada.";
    }
    
    // --- Métodos de Ayuda ---
    private String convertirAreaNombreAId(String nombre) {
        if (nombre.equals("Motos")) return "A01";
        if (nombre.equals("Estudiantes")) return "A02";
        if (nombre.equals("Catedraticos")) return "A03";
        if (nombre.equals("Visitantes")) return "A04";
        return null;
    }
    
    /**
     * Actualiza los labels de ocupación en el Dashboard.
     * Este método consulta la BD y actualiza los 3 JLabels.
     */
    public void actualizarLabelsOcupacion(JLabel lblMotos, JLabel lblEst, JLabel lblCat) {
        // Obtenemos los datos para MOTOS (A01)
        int capMotos = getCapacidadArea("A01");
        int ocupMotos = getOcupacionActual("A01");
        lblMotos.setText("Motos: " + ocupMotos + " / " + capMotos);
        
        // Obtenemos los datos para ESTUDIANTES (A02)
        int capEstudiantes = getCapacidadArea("A02");
        int ocupEstudiantes = getOcupacionActual("A02");
        lblEst.setText("Estudiantes: " + ocupEstudiantes + " / " + capEstudiantes);
        
        // Obtenemos los datos para CATEDRATICOS (A03)
        int capCatedraticos = getCapacidadArea("A03");
        int ocupCatedraticos = getOcupacionActual("A03");
        lblCat.setText("Catedráticos: " + ocupCatedraticos + " / " + capCatedraticos);
        
        // (Opcional) Puedes añadir lógica para cambiar el color si está lleno
        // Ej: if (ocupMotos >= capMotos) { lblMotos.setForeground(Color.RED); }
        //     else { lblMotos.setForeground(Color.BLACK); }
    }
    
    /**
     * Método de ayuda para obtener la capacidad MÁXIMA de un área.
     * Consulta la tabla 'areas' que cargaste desde el CSV.
     */
    private int getCapacidadArea(String areaId) {
        String sql = "SELECT capacidad FROM areas WHERE area_id = ?";
        try (Connection con = conexion.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setString(1, areaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("capacidad");
                }
            }
        } catch (Exception e) {
            System.err.println("Error al obtener capacidad: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * Método de ayuda para contar cuántos vehículos hay AHORA MISMO.
     * Un vehículo ocupa un espacio si su ticket no está "CERRADO"
     * y es del día de hoy (para manejar las tarifas planas).
     */
    private int getOcupacionActual(String areaId) {
        // Contamos tickets que no estén "CERRADO"
        // Y que sean del día de hoy (para los 'PAGADO' de tarifa plana)
        String sql = "SELECT COUNT(*) FROM ticket " +
                     "WHERE area_id = ? " +
                     "AND estado != 'CERRADO' " +
                     "AND DATE(fecha_ingreso) = CURDATE()"; // CURDATE() es MySQL para "hoy"
        
        try (Connection con = conexion.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setString(1, areaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1); // Devuelve el primer (y único) valor del COUNT
                }
            }
        } catch (Exception e) {
            System.err.println("Error al obtener ocupación actual: " + e.getMessage());
        }
        return 0;
    }
    
    // Métodos adicionales para compatibilidad con Dashboard
    public int getOcupacionMotos() {
        return getOcupacionActual("A01");
    }
    
    public int getOcupacionEstudiantes() {
        return getOcupacionActual("A02");
    }
    
    public int getOcupacionCatedraticos() {
        return getOcupacionActual("A03");
    }
    
    private boolean estaAreaLlena(String areaId) {
        int ocupacion = getOcupacionActual(areaId);
        int capacidad = getCapacidadArea(areaId);
        return ocupacion >= capacidad;
    }
    
    private Ticket buscarTicketActivo(String placa) {
        String sql = "SELECT * FROM ticket WHERE placa = ? AND estado IN ('ACTIVO', 'PAGADO') ORDER BY fecha_ingreso DESC LIMIT 1";
        try (Connection con = conexion.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setString(1, placa);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                Ticket ticket = new Ticket(
                    rs.getString("placa"),
                    rs.getString("area_id"),
                    rs.getString("modo")
                );
                ticket.setId(rs.getInt("id"));
                ticket.setFechaIngreso(rs.getObject("fecha_ingreso", LocalDateTime.class));
                ticket.setMonto(rs.getDouble("monto"));
                ticket.setEstado(rs.getString("estado"));
                return ticket;
            }
        } catch (Exception e) {
            System.err.println("Error al buscar ticket: " + e.getMessage());
        }
        return null;
    }
}