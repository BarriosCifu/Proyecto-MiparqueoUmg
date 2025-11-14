package com.mycompany.miparqueoumg;
import hasmap.ConexionMysql;
import com.mycompany.miparqueoumg.Ticket;
import java.sql.Connection;
import java.time.temporal.ChronoUnit;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalDate;
import javax.swing.JLabel;

public class GestorParqueo {
    private final ConexionMysql conexion;
    
    public GestorParqueo() {
        this.conexion = new ConexionMysql(); 
    }
    
    /**
     * Este es el método que llamará tu botón "Registrar Ingreso".
     * @param placa
     * @param areaNombre
     * @return 
     */
    public String registrarIngreso(String placa, String areaNombre) {
        
        // --- 1. Obtener el modo de tarifa actual ---
        String modoTarifaActual = "FLAT"; // O "VARIABLE"

        // --- 2. Convertir nombre de área
        String areaId = convertirAreaNombreAId(areaNombre);
        if (areaId == null) {
            return "Error: El área seleccionada no es válida.";
        }

        // --- 3. Validar Ocupación ---
        if (estaAreaLlena(areaId)) {
            return "Error: El área de " + areaNombre + " está llena.";
        }

        // --- 4. Validar si YA EXISTE ---
        Ticket ticketExistente = buscarTicketActivo(placa);
        if (ticketExistente != null) {
            // SI SE ENCUENTRA un ticket, se rechaza el INGRESO.
            return "Error: Esta placa ya tiene un ticket activo. Use el botón 'Reingreso'.";
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
     * Valida un reingreso basado en las reglas del PDF.
     * Este método es llamado por el botón "Reingreso".
     * @param placa
     * @return 
     */
    public String registrarReingreso(String placa) {
        // 1. Buscar el ticket
        Ticket ticketExistente = buscarTicketActivo(placa);
        
        // 2. Validar si NO se encontró
        if (ticketExistente == null) {
            return "Error: No se encontró ticket activo para la placa. Use el botón 'Ingreso'.";
        }
        
        // 3. Obtener modo de tarifa actual (eventualmente de un config)
        String modoTarifaActual = "FLAT"; // O "VARIABLE"
        
        // 4. Aplicar reglas de reingreso del PDF
        
        // REGLA PARA TARIFA PLANA
        if (modoTarifaActual.equals("FLAT")) {
            // Comprobar que el ticket sea FLAT y del mismo día
            if (ticketExistente.getModo().equals("FLAT") && 
                ticketExistente.getFechaIngreso().toLocalDate().isEqual(LocalDate.now())) {
                // ¡Éxito! Se permite el reingreso
                return "Reingreso (Tarifa Plana) PERMITIDO. (Ticket del día)";
            } else {
                return "Rechazado: El ticket (PLANA) no es válido para reingreso hoy.";
            }
        }
        
        // REGLA PARA TARIFA VARIABLE
        if (modoTarifaActual.equals("VARIABLE")) {
            // Comprobar que el ticket (VARIABLE) siga "ACTIVO"
            if (ticketExistente.getModo().equals("VARIABLE") && 
                ticketExistente.getEstado().equals("ACTIVO")) {
                // ¡Éxito! El ticket sigue abierto
                return "Reingreso (Tarifa Variable) PERMITIDO. (Ticket sigue activo)";
            } else {
                return "Rechazado: El ticket (VARIABLE) no está activo.";
            }
        }
        
        return "Error: Modo de tarifa desconocido.";
    }
    
    /**
     * Este es el método que llamará tu botón "Registrar Salida".
     * @param placa
     * @return 
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
     * @param lblMotos
     * @param lblEst
     * @param lblCat
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
                     "AND DATE(fecha_ingreso) = CURDATE()";
        
        try (Connection con = conexion.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setString(1, areaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
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
    
    /**
     * Busca un ticket que no esté CERRADO para una placa.
     * Devuelve un objeto Ticket si lo encuentra, o null si no.
     */
    private Ticket buscarTicketActivo(String placa) {
        
        // Buscamos tickets "ACTIVO" (variable) o "PAGADO" (plana del día de hoy)
        String sql = "SELECT * FROM ticket WHERE placa = ? AND estado != 'CERRADO' " +
                     "ORDER BY fecha_ingreso DESC LIMIT 1";
        
        try (Connection con = conexion.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setString(1, placa);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // ¡Encontramos un ticket! Lo re-creamos usando el constructor completo
                    
                    int id = rs.getInt("id");
                    String areaId = rs.getString("area_id");
                    
                    // Convertir de SQL Timestamp a Java LocalDateTime
                    LocalDateTime fechaIngreso = rs.getTimestamp("fecha_ingreso").toLocalDateTime();
                    
                    // La fecha de salida puede ser NULL en la BD
                    LocalDateTime fechaSalida = null;
                    Timestamp tsSalida = rs.getTimestamp("fecha_salida");
                    if (tsSalida != null) {
                        fechaSalida = tsSalida.toLocalDateTime();
                    }
                    
                    String modo = rs.getString("modo");
                    double monto = rs.getDouble("monto");
                    String estado = rs.getString("estado");
                    
                    // Usamos el constructor completo que añadimos a Ticket
                    return new Ticket(id, placa, areaId, fechaIngreso, fechaSalida, modo, monto, estado);
                }
            }
        } catch (Exception e) {
            System.err.println("Error al buscar ticket: " + e.getMessage());
        }
        
        // Si no se encontró nada o hubo un error
        return null;
    }
}