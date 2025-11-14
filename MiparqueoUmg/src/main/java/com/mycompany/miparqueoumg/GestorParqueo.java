package com.mycompany.miparqueoumg;
// ... (otros imports)
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter; // Para formatear la fecha
import hasmap.ConexionMysql;
import com.mycompany.miparqueoumg.Ticket;
import java.sql.Connection;
import java.time.temporal.ChronoUnit;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import javax.swing.JLabel;
public class GestorParqueo {
private final ConexionMysql conexion; 
    // Define una tarifa (puedes moverla a un config después)
    private static final double TARIFA_HORA_VARIABLE = 5.00; // Ej: Q5.00 por hora
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
     * Procesa la salida de un vehículo.
     * Calcula el monto, actualiza la BD y devuelve el ticket finalizado.
     * Si hay un error, devuelve null.
     * @param placa
     * @return 
     */
    public Ticket registrarSalida(String placa) {
        // 1. Buscar el ticket activo de la placa
        Ticket ticket = buscarTicketActivo(placa);
        
        // 2. Validar si no se encontró
        if (ticket == null) {
            System.err.println("Error: No se encontró ticket activo para la placa: " + placa);
            return null; // Devolvemos null para que el Dashboard muestre error
        }
        
        LocalDateTime fechaSalidaActual = LocalDateTime.now();
        double montoFinal = 0.0;
        long horasTranscurridas = 0;
        
        // 3. Aplicar lógica de cobro según el MODO del TICKET
        switch (ticket.getModo()) {
            case "FLAT" -> // En Tarifa Plana, el monto ya está pagado. Solo se cierra.
                montoFinal = ticket.getMonto(); // Debería ser 10.00
            case "VARIABLE" -> {
                // En Tarifa Variable, calculamos el costo
                LocalDateTime fechaIngreso = ticket.getFechaIngreso();
                // Calcular minutos y redondear horas hacia ARRIBA
                long minutos = ChronoUnit.MINUTES.between(fechaIngreso, fechaSalidaActual);
                horasTranscurridas = (long) Math.ceil(minutos / 60.0);
                // Si estuvo 0 minutos (o negativo), cobrar 1 hora
                if (horasTranscurridas <= 0) {
                    horasTranscurridas = 1;
                }   
                montoFinal = horasTranscurridas * TARIFA_HORA_VARIABLE;
            }
            default -> {
                System.err.println("Error: Modo de tarifa desconocido en el ticket: " + ticket.getModo());
                return null;
            }
        }
        
        // 4. Actualizar el ticket en la Base de Datos
        String sql = "UPDATE ticket SET fecha_salida = ?, monto = ?, estado = 'CERRADO' WHERE id = ?";
        
        try (Connection con = conexion.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setObject(1, fechaSalidaActual);
            ps.setDouble(2, montoFinal);
            ps.setInt(3, ticket.getId());
            
            int filasAfectadas = ps.executeUpdate();
            
            if (filasAfectadas > 0) {
                // ¡Éxito! Actualizamos el objeto Ticket en Java antes de devolverlo
                ticket.setFechaSalida(fechaSalidaActual);
                ticket.setMonto(montoFinal);
                ticket.setEstado("CERRADO");
                ticket.setHoras(horasTranscurridas);
                
                return ticket; // Devolvemos el ticket completo
            } else {
                return null; // No se pudo actualizar
            }
            
        } catch (Exception e) {
            return null; // Error de BD
        }
    }
    
    /**
     * Genera un PDF con el recibo del ticket.
     * @param ticket El ticket a imprimir
     * @return Mensaje con la ruta del archivo o error
     */
    public String generarTicketPDF(Ticket ticket) {
        // Definir el formato de fecha y hora
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        // Crear el directorio "recibos" si no existe
        new File("recibos").mkdirs();
        
        String rutaArchivo = "recibos/Ticket_" + ticket.getPlaca() + "_" + ticket.getId() + ".pdf";
        
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 14);
                contentStream.setLeading(16.5f); // Espacio entre líneas
                contentStream.newLineAtOffset(50, 700); // Posición inicial (X, Y)
                
                contentStream.showText("--- RECIBO DE PARQUEO UMG ---");
                contentStream.newLine();
                contentStream.newLine();
                
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.showText("Ticket ID: " + ticket.getId());
                contentStream.newLine();
                contentStream.showText("Placa: " + ticket.getPlaca());
                contentStream.newLine();
                contentStream.showText("Area: " + ticket.getAreaId());
                contentStream.newLine();
                contentStream.newLine();
                
                contentStream.showText("Fecha Ingreso: " + ticket.getFechaIngreso().format(formatter));
                contentStream.newLine();
                contentStream.showText("Fecha Salida: " + ticket.getFechaSalida().format(formatter));
                contentStream.newLine();
                contentStream.newLine();
                
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12);
                contentStream.showText("Modo: " + ticket.getModo());
                contentStream.newLine();
                
                if (ticket.getModo().equals("VARIABLE")) {
                    contentStream.showText("Tiempo: " + ticket.getHoras() + " horas (o fracción)");
                    contentStream.newLine();
                }
                
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 16);
                contentStream.showText("TOTAL A PAGAR: Q" + String.format("%.2f", ticket.getMonto()));
                contentStream.newLine();
                
                contentStream.endText();
            }
            
            document.save(rutaArchivo);
            return "PDF generado en: " + rutaArchivo;
            
        } catch (IOException e) {
            return "Error al generar PDF: " + e.getMessage();
        }
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