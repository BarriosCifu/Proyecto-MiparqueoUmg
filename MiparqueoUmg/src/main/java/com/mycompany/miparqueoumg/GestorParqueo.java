package com.mycompany.miparqueoumg;
import java.util.Map;
import java.util.HashMap;
import java.sql.Statement;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import com.mycompany.miparqueoumg.Ticket;
import java.sql.Connection;
import java.time.temporal.ChronoUnit;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JTable;

public class GestorParqueo {
    private final ConexionMysql conexion;
    
    public GestorParqueo() {
        this.conexion = new ConexionMysql(); 
    }  
    
    /**
     * MÉTODO "REGISTRAR INGRESO" (VERSIÓN CORREGIDA)
     * Ahora valida el tipo de vehículo y asigna correctamente el spotId.
     * @param placa
     * @param areaNombre
     * @param modoTarifaActual
     * @param metodoPago
     * @return 
     */
    public String registrarIngreso(String placa, String areaNombre, String modoTarifaActual, String metodoPago) {

        // --- 1. Obtener el modo de tarifa actual (del ComboBox) ---
        // (Ya viene como parámetro)

        // --- 2. Convertir nombre de área
        String areaId = convertirAreaNombreAId(areaNombre);
        if (areaId == null) {
            return "Error: El área seleccionada no es válida.";
        }
        
        // --- NUEVA VALIDACIÓN: TIPO DE VEHÍCULO ---
        String tipoVehiculo = getTipoVehiculoDePlaca(placa);
        if (tipoVehiculo == null) {
            return "Error: La placa '" + placa + "' no está registrada en el sistema.";
        }

        // --- 3. Buscar un Spot Libre (Lógica Corregida) ---
        String spotAsignado = buscarSpotLibre(areaId, tipoVehiculo);
        
        if (spotAsignado == null) {
            // Ahora sí, este error es correcto
            return "Error: No hay spots libres para '" + tipoVehiculo + "' en el área de '" + areaNombre + "'.";
        }

        // --- 4. Validar si YA EXISTE (Lógica de Reingreso) ---
        Ticket ticketExistente = buscarTicketActivo(placa);
        if (ticketExistente != null) {
            return "Error: Esta placa ya tiene un ticket activo. Use el botón 'Reingreso'.";
        }

        // --- 5. Crear Ticket Nuevo (CORREGIDO) ---
        Ticket nuevoTicket = new Ticket(placa, areaId, spotAsignado, modoTarifaActual); // ⬅️ AÑADIDO spotAsignado
        
        // --- 6. Guardar en Base de Datos ---
        String sql = "INSERT INTO ticket (placa, area_id, fecha_ingreso, modo, monto, estado, metodo_pago, `spot-id`) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection con = conexion.getConnection(); 
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setString(1, nuevoTicket.getPlaca());
            ps.setString(2, nuevoTicket.getAreaId());
            ps.setObject(3, nuevoTicket.getFechaIngreso());
            ps.setString(4, nuevoTicket.getModo());
            ps.setDouble(5, nuevoTicket.getMonto());
            ps.setString(6, nuevoTicket.getEstado());

            if (modoTarifaActual.equalsIgnoreCase("FLAT")) {
                ps.setString(7, metodoPago);
            } else {
                ps.setNull(7, java.sql.Types.VARCHAR);
            }
            
            ps.setString(8, spotAsignado); // Parámetro 8 es spot-id
            
            ps.executeUpdate();
            
            // Marcar el spot como ocupado
            actualizarEstadoSpot(spotAsignado, "OCCUPIED");
            
            return "Ingreso Registrado (Modo " + modoTarifaActual + ") en Spot: " + spotAsignado;
            
        } catch (Exception e) {
            e.printStackTrace();
            return "Error al guardar en la base de datos: " + e.getMessage();
        }
    }
    
    /**
     * Valida un reingreso basado en las reglas del PDF.
     * Este método es llamado por el botón "Reingreso".
     * @param placa
     * @param modoTarifaActual
     * @return 
     */
    public String registrarReingreso(String placa, String modoTarifaActual) {
        // 1. Buscar el ticket
        Ticket ticketExistente = buscarTicketActivo(placa);
        
        // 2. Validar si NO se encontró
        if (ticketExistente == null) {
            return "Error: No se encontró ticket activo para la placa. Use el botón 'Ingreso'.";
        }
        
        // 3. Aplicar reglas de reingreso del PDF
        
        // REGLA PARA TARIFA PLANA
        if (modoTarifaActual.equalsIgnoreCase("FLAT")) {
            // Comprobar que el ticket sea FLAT y del mismo día
            if (ticketExistente.getModo().equalsIgnoreCase("FLAT") && 
                ticketExistente.getFechaIngreso().toLocalDate().isEqual(LocalDate.now())) {
                // ¡Éxito! Se permite el reingreso
                return "Reingreso (Tarifa Plana) PERMITIDO. (Ticket del día)";
            } else {
                return "Rechazado: El ticket (PLANA) no es válido para reingreso hoy.";
            }
        }
        
        // REGLA PARA TARIFA VARIABLE
        if (modoTarifaActual.equalsIgnoreCase("VARIABLE")) {
            // Comprobar que el ticket (VARIABLE) siga "ACTIVO"
            if (ticketExistente.getModo().equalsIgnoreCase("VARIABLE") && 
                ticketExistente.getEstado().equalsIgnoreCase("ACTIVO")) {
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
     * @param tarifaVariableConfigurada
     * @param metodoPago
     * @return 
     */
    public Ticket registrarSalida(String placa, double tarifaVariableConfigurada, String metodoPago) {
        // 1. Buscar el ticket activo de la placa
        Ticket ticket = buscarTicketActivo(placa);
        
        // 2. Validar si no se encontró
        if (ticket == null) {
            System.err.println("Error: No se encontró ticket activo para la placa: " + placa);
            return null;
        }
        
        LocalDateTime fechaSalidaActual = LocalDateTime.now();
        double montoFinal = 0.0;
        long horasTranscurridas = 0;
        
        // 3. Aplicar lógica de cobro según el MODO del TICKET
        switch (ticket.getModo().toUpperCase()) {
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
                montoFinal = horasTranscurridas * tarifaVariableConfigurada;
            }
            default -> {
                System.err.println("Error: Modo de tarifa desconocido en el ticket: " + ticket.getModo());
                return null;
            }
        }
        
        // 4. Actualizar el ticket en la Base de Datos
        String sql = "UPDATE ticket SET fecha_salida = ?, monto = ?, estado = 'CERRADO', metodo_pago = ? WHERE id = ?";
        
        try (Connection con = conexion.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setObject(1, fechaSalidaActual);
            ps.setDouble(2, montoFinal);
            
            // Si el modo es VARIABLE, guardamos el método de pago; si es FLAT, no actualizamos
            if (ticket.getModo().equalsIgnoreCase("VARIABLE")) {
                ps.setString(3, metodoPago);
            } else {
                ps.setNull(3, java.sql.Types.VARCHAR);
            }
            
            ps.setInt(4, ticket.getId());
            
            int filasAfectadas = ps.executeUpdate();
            
            if (filasAfectadas > 0) {
                // ¡Éxito! El ticket se actualizó a "CERRADO"
                
                // Liberar el spot en la tabla 'spot'
                actualizarEstadoSpot(ticket.getSpotId(), "FREE");
                
                // Actualizamos el objeto Ticket en Java antes de devolverlo
                ticket.setFechaSalida(fechaSalidaActual);
                ticket.setMonto(montoFinal);
                ticket.setEstado("CERRADO");
                ticket.setHoras(horasTranscurridas);
                
                return ticket; // Devolvemos el ticket completo
            } else {
                return null; // No se pudo actualizar
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Genera un PDF con el recibo del ticket.
     * @param ticket
     * @return 
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
                contentStream.showText("Spot: " + ticket.getSpotId()); // ⬅️ AÑADIDO
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
                
                if (ticket.getModo().equalsIgnoreCase("VARIABLE")) {
                    contentStream.showText("Tiempo: " + ticket.getHoras() + " horas (o fraccion)");
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
     * Actualiza los JLabels del dashboard con la ocupación.
     * @param lblMotos
     * @param lblEst
     * @param lblCat
     */
    public void actualizarLabelsOcupacion(JLabel lblMotos, JLabel lblEst, JLabel lblCat) {
        
        // Obtenemos los datos para MOTOS (A01)
        int capMotos = getCapacidadPorSpots("A01");
        int ocupMotos = getOcupacionPorSpots("A01");
        lblMotos.setText("Motos: " + ocupMotos + " / " + capMotos);

        // Obtenemos los datos para ESTUDIANTES (A02)
        int capEstudiantes = getCapacidadPorSpots("A02");
        int ocupEstudiantes = getOcupacionPorSpots("A02");
        lblEst.setText("Estudiantes: " + ocupEstudiantes + " / " + capEstudiantes);

        // Obtenemos los datos para CATEDRATICOS (A03)
        int capCatedraticos = getCapacidadPorSpots("A03");
        int ocupCatedraticos = getOcupacionPorSpots("A03");
        lblCat.setText("Catedraticos: " + ocupCatedraticos + " / " + capCatedraticos);
    }
    
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
    
    private int getOcupacionActual(String areaId) {
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
            System.err.println("Error al obtener ocupacion actual: " + e.getMessage());
        }
        return 0;
    }
    
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
     * MÉTODO "BUSCAR TICKET ACTIVO" (YA CORREGIDO)
     * Lee todas las columnas, incluyendo las que tienen guiones.
     */
    private Ticket buscarTicketActivo(String placa) {
        String sql = "SELECT * FROM ticket WHERE placa = ? AND estado != 'CERRADO' " +
                     "ORDER BY fecha_ingreso DESC LIMIT 1";
        
        try (Connection con = conexion.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setString(1, placa);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    String areaId = rs.getString("area_id");
                    String spotId = rs.getString("spot-id");
                    
                    LocalDateTime fechaIngreso = rs.getTimestamp("fecha_ingreso").toLocalDateTime();
                    
                    LocalDateTime fechaSalida = null;
                    Timestamp tsSalida = rs.getTimestamp("fecha_salida");
                    if (tsSalida != null) {
                        fechaSalida = tsSalida.toLocalDateTime();
                    }
                    
                    String modo = rs.getString("modo");
                    double monto = rs.getDouble("monto");
                    String estado = rs.getString("estado");
                    
                    return new Ticket(id, placa, areaId, spotId, fechaIngreso, fechaSalida, modo, monto, estado);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * Busca el tipo de vehículo de una placa
     */
    private String getTipoVehiculoDePlaca(String placa) {
        String sql = "SELECT `tipo-vehiculo` FROM vehiculos WHERE TRIM(UPPER(placa)) = TRIM(UPPER(?))";
        try (Connection con = conexion.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setString(1, placa);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("tipo-vehiculo").trim();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Busca un spot libre para un ÁREA y un TIPO DE VEHÍCULO
     */
    private String buscarSpotLibre(String areaId, String tipoVehiculo) {
        String sql = "SELECT `spot-id` FROM spot WHERE `area-id` = ? AND TRIM(`tipo-vehiculo`) = ? AND status = 'FREE' LIMIT 1";
        try (Connection con = conexion.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setString(1, areaId);
            ps.setString(2, tipoVehiculo);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("spot-id");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Actualiza el estado de un spot
     */
    private void actualizarEstadoSpot(String spotId, String nuevoEstado) {
        String sql = "UPDATE spot SET status = ? WHERE `spot-id` = ?";
        try (Connection con = conexion.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setString(1, nuevoEstado);
            ps.setString(2, spotId);
            ps.executeUpdate();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Devuelve un mapa con el estado visual de CADA spot.
     * @return 
     */
    public Map<String, String> getEstadoVisualSpots() {
        
        Map<String, String> mapaEstados = new HashMap<>();
        
        String sql = "SELECT " +
                     "  s.`spot-id`, " +
                     "  s.status, " +
                     "  t.modo " +
                     "FROM spot s " +
                     "LEFT JOIN ticket t ON s.`spot-id` = t.`spot-id` " +
                     "  AND t.estado != 'CERRADO' " +
                     "  AND DATE(t.fecha_ingreso) = CURDATE()";

        try (Connection con = conexion.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                String spotId = rs.getString("spot-id");
                String status = rs.getString("status");
                String modo = rs.getString("modo");

                if (status.equals("FREE")) {
                    mapaEstados.put(spotId, "FREE");
                } else {
                    if ("FLAT".equals(modo)) {
                        mapaEstados.put(spotId, "OCUPADO_FLAT");
                    } else {
                        mapaEstados.put(spotId, "OCUPADO_VARIABLE");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return mapaEstados;
    }
    
    /**
     * Devuelve la capacidad MÁXIMA de un área contando sus spots.
     * @param areaId
     * @return 
     */
    public int getCapacidadPorSpots(String areaId) {
        String sql = "SELECT COUNT(*) FROM spot WHERE `area-id` = ?";
        try (Connection con = conexion.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setString(1, areaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }
    
    /**
     * Devuelve la ocupación ACTUAL de un área contando sus spots 'OCCUPIED'.
     * @param areaId
     * @return 
     */
    public int getOcupacionPorSpots(String areaId) {
        String sql = "SELECT COUNT(*) FROM spot WHERE `area-id` = ? AND status = 'OCCUPIED'";
        try (Connection con = conexion.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setString(1, areaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    void cargarReporteCompleto(JTable tblReporte) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
}