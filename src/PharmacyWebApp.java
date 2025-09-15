// Complete Pharmacy Management System
// File: PharmacyWebApp.java
// Displays completed consultations and manages medicine dispensing

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.net.BindException;

public class PharmacyWebApp {

    private static final String DB_URL = "jdbc:postgresql://localhost:5433/healthcare";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "admin123";
    private static Connection connection;

    // Port configuration with fallback options
    private static final int[] AVAILABLE_PORTS = {5002, 5003, 5004, 8080, 8081, 8082, 3000, 3001, 9000};
    private static int SERVER_PORT = 5002;

    static class PharmacyRecord {
        private Long id;
        private String patientId;
        private String patientName;
        private String medicines;
        private String tests;
        private Integer token;
        private LocalDateTime consultationTime;
        private String consultationStatus;
        private String pharmacyStatus;
        private LocalDateTime pharmacyCompletedTime;

        // Constructors
        public PharmacyRecord() {
        }

        // Getters and setters
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getPatientId() {
            return patientId;
        }

        public void setPatientId(String patientId) {
            this.patientId = patientId;
        }

        public String getPatientName() {
            return patientName;
        }

        public void setPatientName(String patientName) {
            this.patientName = patientName;
        }

        public String getMedicines() {
            return medicines;
        }

        public void setMedicines(String medicines) {
            this.medicines = medicines;
        }

        public String getTests() {
            return tests;
        }

        public void setTests(String tests) {
            this.tests = tests;
        }

        public Integer getToken() {
            return token;
        }

        public void setToken(Integer token) {
            this.token = token;
        }

        public LocalDateTime getConsultationTime() {
            return consultationTime;
        }

        public void setConsultationTime(LocalDateTime consultationTime) {
            this.consultationTime = consultationTime;
        }

        public String getConsultationStatus() {
            return consultationStatus;
        }

        public void setConsultationStatus(String consultationStatus) {
            this.consultationStatus = consultationStatus;
        }

        public String getPharmacyStatus() {
            return pharmacyStatus;
        }

        public void setPharmacyStatus(String pharmacyStatus) {
            this.pharmacyStatus = pharmacyStatus;
        }

        public LocalDateTime getPharmacyCompletedTime() {
            return pharmacyCompletedTime;
        }

        public void setPharmacyCompletedTime(LocalDateTime pharmacyCompletedTime) {
            this.pharmacyCompletedTime = pharmacyCompletedTime;
        }

        public String toJson() {
            return String.format(
                    "{\"id\":%d,\"patientId\":\"%s\",\"patientName\":\"%s\",\"medicines\":\"%s\"," +
                            "\"tests\":\"%s\",\"token\":%d,\"consultationTime\":\"%s\"," +
                            "\"consultationStatus\":\"%s\",\"pharmacyStatus\":\"%s\"}",
                    id != null ? id : 0,
                    escapeJson(patientId),
                    escapeJson(patientName),
                    escapeJson(medicines),
                    escapeJson(tests),
                    token != null ? token : 0,
                    consultationTime != null ? consultationTime.toString() : "",
                    escapeJson(consultationStatus),
                    escapeJson(pharmacyStatus)
            );
        }
    }

    static class DatabaseService {

        static void initializeDatabase() {
            try {
                Class.forName("org.postgresql.Driver");
                connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

                // Test connection
                Statement testStmt = connection.createStatement();
                ResultSet testRs = testStmt.executeQuery("SELECT 1");
                testRs.close();
                testStmt.close();

                ensurePharmacyStatusColumn();
                System.out.println("✓ Database connected successfully!");

            } catch (ClassNotFoundException e) {
                System.err.println("✗ PostgreSQL JDBC driver not found!");
                System.exit(1);
            } catch (SQLException e) {
                System.err.println("✗ Database connection failed: " + e.getMessage());
                System.exit(1);
            }
        }

        static void ensurePharmacyStatusColumn() {
            try (Statement stmt = connection.createStatement()) {
                // Check if pharmacy_status column exists in doctors table
                DatabaseMetaData dbmd = connection.getMetaData();
                ResultSet columns = dbmd.getColumns(null, null, "doctors", "pharmacy_status");

                if (!columns.next()) {
                    // Add pharmacy_status column if it doesn't exist
                    stmt.execute("ALTER TABLE doctors ADD COLUMN pharmacy_status VARCHAR(20) DEFAULT 'pending'");
                    stmt.execute("ALTER TABLE doctors ADD COLUMN pharmacy_completed_time TIMESTAMP");
                    System.out.println("✓ Pharmacy status columns added to doctors table!");
                } else {
                    System.out.println("✓ Pharmacy status columns already exist.");
                }
                columns.close();

                // Update existing completed consultations to have pharmacy_status = 'pending'
                stmt.execute("UPDATE doctors SET pharmacy_status = 'pending' WHERE consultation_status = 'completed' AND pharmacy_status IS NULL");

            } catch (SQLException e) {
                System.err.println("✗ Error ensuring pharmacy columns: " + e.getMessage());
            }
        }

        /**
         * Get all completed consultations that are pending pharmacy processing
         */
        static List<PharmacyRecord> getPendingPharmacyRecords() {
            List<PharmacyRecord> records = new ArrayList<>();
            String sql = """
                    SELECT id, patient_id, patient_name, medicines, tests, token, 
                           consultation_time, consultation_status, pharmacy_status
                    FROM doctors 
                    WHERE consultation_status = 'completed' 
                    AND (pharmacy_status = 'pending' OR pharmacy_status IS NULL)
                    ORDER BY consultation_time ASC
                    """;

            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery(sql);

                while (rs.next()) {
                    PharmacyRecord record = new PharmacyRecord();
                    record.setId(rs.getLong("id"));
                    record.setPatientId(rs.getString("patient_id"));
                    record.setPatientName(rs.getString("patient_name"));
                    record.setMedicines(rs.getString("medicines"));
                    record.setTests(rs.getString("tests"));
                    record.setToken(rs.getInt("token"));
                    record.setConsultationTime(rs.getTimestamp("consultation_time").toLocalDateTime());
                    record.setConsultationStatus(rs.getString("consultation_status"));
                    record.setPharmacyStatus(rs.getString("pharmacy_status"));
                    records.add(record);
                }
            } catch (SQLException e) {
                System.err.println("Error loading pending pharmacy records: " + e.getMessage());
            }
            return records;
        }

        /**
         * Get all completed pharmacy records (for history)
         */
        static List<PharmacyRecord> getCompletedPharmacyRecords() {
            List<PharmacyRecord> records = new ArrayList<>();
            String sql = """
                    SELECT id, patient_id, patient_name, medicines, tests, token, 
                           consultation_time, consultation_status, pharmacy_status,
                           pharmacy_completed_time
                    FROM doctors 
                    WHERE consultation_status = 'completed' 
                    AND pharmacy_status = 'completed'
                    ORDER BY pharmacy_completed_time DESC
                    LIMIT 50
                    """;

            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery(sql);

                while (rs.next()) {
                    PharmacyRecord record = new PharmacyRecord();
                    record.setId(rs.getLong("id"));
                    record.setPatientId(rs.getString("patient_id"));
                    record.setPatientName(rs.getString("patient_name"));
                    record.setMedicines(rs.getString("medicines"));
                    record.setTests(rs.getString("tests"));
                    record.setToken(rs.getInt("token"));
                    record.setConsultationTime(rs.getTimestamp("consultation_time").toLocalDateTime());
                    record.setConsultationStatus(rs.getString("consultation_status"));
                    record.setPharmacyStatus(rs.getString("pharmacy_status"));

                    Timestamp pharmacyTime = rs.getTimestamp("pharmacy_completed_time");
                    if (pharmacyTime != null) {
                        record.setPharmacyCompletedTime(pharmacyTime.toLocalDateTime());
                    }
                    records.add(record);
                }
            } catch (SQLException e) {
                System.err.println("Error loading completed pharmacy records: " + e.getMessage());
            }
            return records;
        }

        /**
         * Mark pharmacy record as completed
         */
        static boolean markPharmacyCompleted(Long recordId) {
            String sql = """
                    UPDATE doctors 
                    SET pharmacy_status = 'completed', 
                        pharmacy_completed_time = CURRENT_TIMESTAMP 
                    WHERE id = ? AND consultation_status = 'completed'
                    """;

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, recordId);
                int rowsAffected = pstmt.executeUpdate();

                if (rowsAffected > 0) {
                    System.out.println("✓ Pharmacy record completed: ID " + recordId);
                    return true;
                }
            } catch (SQLException e) {
                System.err.println("✗ Error marking pharmacy record as completed: " + e.getMessage());
            }
            return false;
        }

        /**
         * Get pharmacy statistics
         */
        static Map<String, Integer> getPharmacyStats() {
            Map<String, Integer> stats = new HashMap<>();

            try (Statement stmt = connection.createStatement()) {
                // Pending count
                ResultSet rs = stmt.executeQuery("""
                        SELECT COUNT(*) as count FROM doctors 
                        WHERE consultation_status = 'completed' 
                        AND (pharmacy_status = 'pending' OR pharmacy_status IS NULL)
                        """);
                if (rs.next()) {
                    stats.put("pending", rs.getInt("count"));
                }

                // Today's completed count
                rs = stmt.executeQuery("""
                        SELECT COUNT(*) as count FROM doctors 
                        WHERE pharmacy_status = 'completed' 
                        AND DATE(pharmacy_completed_time) = CURRENT_DATE
                        """);
                if (rs.next()) {
                    stats.put("todayCompleted", rs.getInt("count"));
                }

                // Total completed count
                rs = stmt.executeQuery("""
                        SELECT COUNT(*) as count FROM doctors 
                        WHERE pharmacy_status = 'completed'
                        """);
                if (rs.next()) {
                    stats.put("totalCompleted", rs.getInt("count"));
                }

            } catch (SQLException e) {
                System.err.println("Error loading pharmacy stats: " + e.getMessage());
                stats.put("pending", 0);
                stats.put("todayCompleted", 0);
                stats.put("totalCompleted", 0);
            }

            return stats;
        }
    }

    // Port management methods
    static int findAvailablePort() {
        System.out.println("🔍 Searching for available ports...");

        for (int port : AVAILABLE_PORTS) {
            System.out.println("   Testing port " + port + "...");
            if (isPortAvailable(port)) {
                System.out.println("✓ Port " + port + " is available!");
                return port;
            } else {
                System.out.println("✗ Port " + port + " is in use");
            }
        }

        // If no predefined port is available, try random ports
        System.out.println("🔍 Trying random ports in range 9100-9199...");
        for (int i = 0; i < 20; i++) {
            int randomPort = 9100 + (int) (Math.random() * 100);
            System.out.println("   Testing random port " + randomPort + "...");
            if (isPortAvailable(randomPort)) {
                System.out.println("✓ Random port " + randomPort + " is available!");
                return randomPort;
            }
        }

        System.err.println("❌ No available ports found after extensive search!");
        return -1;
    }

    static boolean isPortAvailable(int port) {
        try (java.net.ServerSocket socket = new java.net.ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new java.net.InetSocketAddress(port));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static class WebHandlers {

        static class HomeHandler implements HttpHandler {
            public void handle(HttpExchange exchange) throws IOException {
                String response = getPharmacyPageHTML();
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes(StandardCharsets.UTF_8));
                os.close();
            }
        }

        static class PendingRecordsHandler implements HttpHandler {
            public void handle(HttpExchange exchange) throws IOException {
                if ("GET".equals(exchange.getRequestMethod())) {
                    List<PharmacyRecord> records = DatabaseService.getPendingPharmacyRecords();
                    StringBuilder json = new StringBuilder("[");
                    for (int i = 0; i < records.size(); i++) {
                        json.append(records.get(i).toJson());
                        if (i < records.size() - 1) json.append(",");
                    }
                    json.append("]");

                    sendJsonResponse(exchange, 200, json.toString());
                }
            }
        }

        static class CompletedRecordsHandler implements HttpHandler {
            public void handle(HttpExchange exchange) throws IOException {
                if ("GET".equals(exchange.getRequestMethod())) {
                    List<PharmacyRecord> records = DatabaseService.getCompletedPharmacyRecords();
                    StringBuilder json = new StringBuilder("[");
                    for (int i = 0; i < records.size(); i++) {
                        json.append(records.get(i).toJson());
                        if (i < records.size() - 1) json.append(",");
                    }
                    json.append("]");

                    sendJsonResponse(exchange, 200, json.toString());
                }
            }
        }

        static class CompletePharmacyHandler implements HttpHandler {
            public void handle(HttpExchange exchange) throws IOException {
                if ("POST".equals(exchange.getRequestMethod())) {
                    handlePharmacyCompletion(exchange);
                } else if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    handleCorsOptions(exchange);
                }
            }

            private void handleCorsOptions(HttpExchange exchange) throws IOException {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().close();
            }

            private void handlePharmacyCompletion(HttpExchange exchange) throws IOException {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

                try {
                    String requestBody = readRequestBody(exchange);
                    System.out.println("=== PHARMACY COMPLETION REQUEST ===");
                    System.out.println("Raw request body: " + requestBody);

                    Long recordId = extractRecordIdFromJson(requestBody);
                    if (recordId == null) {
                        sendJsonResponse(exchange, 400, "{\"success\": false, \"message\": \"Invalid record ID\"}");
                        return;
                    }

                    boolean success = DatabaseService.markPharmacyCompleted(recordId);
                    if (success) {
                        String response = "{\"success\": true, \"message\": \"Pharmacy record marked as completed\"}";
                        sendJsonResponse(exchange, 200, response);
                    } else {
                        sendJsonResponse(exchange, 500, "{\"success\": false, \"message\": \"Failed to mark as completed\"}");
                    }

                } catch (Exception e) {
                    System.err.println("Pharmacy completion error: " + e.getMessage());
                    e.printStackTrace();
                    String errorResponse = String.format("{\"success\": false, \"message\": \"Server error: %s\"}",
                            escapeJson(e.getMessage()));
                    sendJsonResponse(exchange, 500, errorResponse);
                }
            }

            private String readRequestBody(HttpExchange exchange) throws IOException {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }

            private Long extractRecordIdFromJson(String json) {
                try {
                    String pattern = "\"recordId\":";
                    int start = json.indexOf(pattern);
                    if (start == -1) return null;

                    start += pattern.length();
                    int end = json.indexOf(",", start);
                    if (end == -1) {
                        end = json.indexOf("}", start);
                    }
                    if (end == -1) return null;

                    String idStr = json.substring(start, end).trim();
                    return Long.parseLong(idStr);
                } catch (Exception e) {
                    return null;
                }
            }
        }

        static class StatsHandler implements HttpHandler {
            public void handle(HttpExchange exchange) throws IOException {
                if ("GET".equals(exchange.getRequestMethod())) {
                    Map<String, Integer> stats = DatabaseService.getPharmacyStats();
                    String response = String.format(
                            "{\"pending\": %d, \"todayCompleted\": %d, \"totalCompleted\": %d}",
                            stats.get("pending"),
                            stats.get("todayCompleted"),
                            stats.get("totalCompleted")
                    );
                    sendJsonResponse(exchange, 200, response);
                }
            }
        }

        private static void sendJsonResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes(StandardCharsets.UTF_8));
            os.close();
        }
    }

    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    public static void main(String[] args) {
        try {
            System.out.println("💊 Starting Pharmacy Management System...");

            DatabaseService.initializeDatabase();

            // Find an available port
            SERVER_PORT = findAvailablePort();

            if (SERVER_PORT == -1) {
                System.err.println("❌ No available port found! Please free up one of these ports: " + Arrays.toString(AVAILABLE_PORTS));
                System.exit(1);
            }

            HttpServer server;
            try {
                server = HttpServer.create(new InetSocketAddress(SERVER_PORT), 0);
            } catch (BindException e) {
                System.err.println("❌ Port " + SERVER_PORT + " is already in use!");
                System.out.println("🔍 Trying to find alternative port...");

                SERVER_PORT = findAvailablePort();
                if (SERVER_PORT == -1) {
                    System.err.println("❌ No available ports found!");
                    System.exit(1);
                }

                server = HttpServer.create(new InetSocketAddress(SERVER_PORT), 0);
            }

            server.createContext("/", new WebHandlers.HomeHandler());
            server.createContext("/api/pending-records", new WebHandlers.PendingRecordsHandler());
            server.createContext("/api/completed-records", new WebHandlers.CompletedRecordsHandler());
            server.createContext("/api/complete-pharmacy", new WebHandlers.CompletePharmacyHandler());
            server.createContext("/api/stats", new WebHandlers.StatsHandler());

            server.setExecutor(Executors.newFixedThreadPool(10));
            server.start();

            System.out.println("✅ Pharmacy Management System started!");
            System.out.println("🌐 Server running at: http://localhost:" + SERVER_PORT);
            System.out.println("💊 Access pharmacy dashboard at: http://localhost:" + SERVER_PORT);
            System.out.println("💾 Database: " + DB_URL);
            System.out.println("🔗 Receives data from Doctor System at: http://localhost:5001");
            System.out.println("🔄 Server is ready to manage pharmacy records...");
            System.out.println("\n📊 API Endpoints:");
            System.out.println("  GET  /api/pending-records - Get pending pharmacy records");
            System.out.println("  GET  /api/completed-records - Get completed pharmacy records");
            System.out.println("  POST /api/complete-pharmacy - Mark pharmacy record as completed");
            System.out.println("  GET  /api/stats - Get pharmacy statistics");
            System.out.println("\n💊 Pharmacy Workflow:");
            System.out.println("  1. Doctor completes consultation → Data appears in pharmacy");
            System.out.println("  2. Pharmacy dispenses medicines → Mark as completed");
            System.out.println("  3. Completed records move to history");
            System.out.println("\n⚠  To stop server: Press Ctrl+C");

            if (SERVER_PORT != 5002) {
                System.out.println("\n⚠️  NOTE: Server is running on port " + SERVER_PORT + " instead of 5002");
                System.out.println("   This is because port 5002 was already in use.");
            }

        } catch (Exception e) {
            System.err.println("❌ Failed to start Pharmacy Management System: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    static String getPharmacyPageHTML() {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Pharmacy Management System</title>
                    <link href="https://cdnjs.cloudflare.com/ajax/libs/bootstrap/5.3.0/css/bootstrap.min.css" rel="stylesheet">
                    <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
                    <style>
                        :root {
                            --primary-color: #059669;
                            --secondary-color: #0891b2;
                            --success-color: #10b981;
                            --danger-color: #ef4444;
                            --warning-color: #f59e0b;
                            --info-color: #06b6d4;
                            --dark-color: #1f2937;
                            --pharmacy-gradient: linear-gradient(135deg, #059669, #0891b2);
                        }
                
                        body {
                            background: var(--pharmacy-gradient);
                            min-height: 100vh;
                            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        }
                
                        .main-container {
                            background: rgba(255, 255, 255, 0.95);
                            backdrop-filter: blur(10px);
                            border-radius: 20px;
                            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1);
                            margin: 20px;
                            overflow: hidden;
                            padding: 30px;
                        }
                
                        .header {
                            text-align: center;
                            margin-bottom: 30px;
                        }
                
                        .header h1 {
                            background: var(--pharmacy-gradient);
                            -webkit-background-clip: text;
                            -webkit-text-fill-color: transparent;
                            background-clip: text;
                        }
                
                        .stats-container {
                            display: flex;
                            justify-content: center;
                            gap: 30px;
                            margin-bottom: 40px;
                            flex-wrap: wrap;
                        }
                
                        .stat-card {
                            background: var(--pharmacy-gradient);
                            color: white;
                            padding: 25px;
                            border-radius: 15px;
                            text-align: center;
                            box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);
                            min-width: 180px;
                            transition: transform 0.3s ease;
                        }
                
                        .stat-card:hover {
                            transform: translateY(-5px);
                        }
                
                        .stat-card h3 {
                            font-size: 2rem;
                            font-weight: 700;
                            margin-bottom: 10px;
                        }
                
                        .stat-card p {
                            font-size: 0.9rem;
                            margin: 0;
                            opacity: 0.9;
                        }
                
                        .section-card {
                            background: white;
                            border-radius: 15px;
                            padding: 25px;
                            margin-bottom: 30px;
                            box-shadow: 0 5px 15px rgba(0, 0, 0, 0.05);
                        }
                
                        .section-title {
                            display: flex;
                            align-items: center;
                            gap: 10px;
                            margin-bottom: 20px;
                            font-size: 1.3rem;
                            font-weight: 600;
                            color: var(--dark-color);
                        }
                
                        .table {
                            border-radius: 10px;
                            overflow: hidden;
                            box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
                        }
                
                        .table thead th {
                            background: var(--pharmacy-gradient);
                            color: white;
                            border: none;
                            font-weight: 600;
                            font-size: 0.9rem;
                        }
                
                        .table tbody tr {
                            transition: all 0.2s ease;
                        }
                
                        .table tbody tr:hover {
                            background: #f0fdfa;
                            cursor: default;
                        }
                
                        .btn {
                            border-radius: 10px;
                            padding: 8px 16px;
                            font-weight: 600;
                            font-size: 0.85rem;
                            transition: all 0.3s ease;
                        }
                
                        .btn-success {
                            background: linear-gradient(135deg, var(--success-color), #047857);
                            border: none;
                        }
                
                        .btn-success:hover {
                            transform: translateY(-2px);
                            box-shadow: 0 5px 15px rgba(16, 185, 129, 0.4);
                        }
                
                        .btn-outline-primary {
                            border-color: var(--primary-color);
                            color: var(--primary-color);
                        }
                
                        .btn-outline-primary:hover {
                            background: var(--primary-color);
                            border-color: var(--primary-color);
                        }
                
                        .nav-tabs {
                            border: none;
                            margin-bottom: 25px;
                        }
                
                        .nav-tabs .nav-link {
                            border: none;
                            border-radius: 10px;
                            margin-right: 10px;
                            color: var(--dark-color);
                            font-weight: 600;
                        }
                
                        .nav-tabs .nav-link.active {
                            background: var(--pharmacy-gradient);
                            color: white;
                        }
                
                        .medicine-cell, .test-cell {
                            max-width: 200px;
                            word-wrap: break-word;
                            white-space: pre-wrap;
                        }
                
                        .badge {
                            font-size: 0.8rem;
                        }
                
                        .empty-state {
                            text-align: center;
                            padding: 60px 20px;
                            color: #6b7280;
                        }
                
                        .empty-state i {
                            font-size: 4rem;
                            margin-bottom: 20px;
                            opacity: 0.3;
                        }
                
                        .refresh-btn {
                            position: fixed;
                            bottom: 30px;
                            right: 30px;
                            width: 60px;
                            height: 60px;
                            border-radius: 50%;
                            background: var(--pharmacy-gradient);
                            color: white;
                            border: none;
                            box-shadow: 0 8px 25px rgba(5, 150, 105, 0.3);
                            font-size: 1.2rem;
                            transition: all 0.3s ease;
                        }
                
                        .refresh-btn:hover {
                            transform: scale(1.1);
                            box-shadow: 0 12px 35px rgba(5, 150, 105, 0.4);
                        }
                
                        .status-pending {
                            background: #fef3c7;
                            color: #d97706;
                        }
                
                        .status-completed {
                            background: #d1fae5;
                            color: #059669;
                        }
                
                        .urgent-record {
                            border-left: 4px solid #ef4444;
                            background: #fef2f2;
                        }
                
                        .notification {
                            position: fixed;
                            top: 20px;
                            right: 20px;
                            z-index: 1050;
                            max-width: 350px;
                        }
                
                        @media (max-width: 768px) {
                            .stats-container {
                                flex-direction: column;
                                align-items: center;
                            }
                
                            .main-container {
                                margin: 10px;
                                padding: 20px;
                            }
                
                            .medicine-cell, .test-cell {
                                max-width: 120px;
                                font-size: 0.8rem;
                            }
                
                            .refresh-btn {
                                bottom: 20px;
                                right: 20px;
                                width: 50px;
                                height: 50px;
                            }
                        }
                    </style>
                </head>
                <body>
                    <!-- Notification Container -->
                    <div id="notificationContainer"></div>
                
                    <div class="container-fluid">
                        <div class="main-container">
                            <!-- Header -->
                            <div class="header">
                                <h1 class="display-4 fw-bold mb-2">
                                    <i class="fas fa-pills me-3"></i>Pharmacy Management System
                                </h1>
                                <p class="lead text-muted">Medicine Dispensing & Prescription Management</p>
                            </div>
                
                            <!-- Stats Section -->
                            <div class="stats-container">
                                <div class="stat-card">
                                    <h3 id="pendingCount">0</h3>
                                    <p><i class="fas fa-clock me-2"></i>Pending Prescriptions</p>
                                </div>
                                <div class="stat-card">
                                    <h3 id="todayCompletedCount">0</h3>
                                    <p><i class="fas fa-check-circle me-2"></i>Today's Completed</p>
                                </div>
                                <div class="stat-card">
                                    <h3 id="totalCompletedCount">0</h3>
                                    <p><i class="fas fa-history me-2"></i>Total Completed</p>
                                </div>
                            </div>
                
                            <!-- Navigation Tabs -->
                            <ul class="nav nav-tabs" id="pharmacyTabs" role="tablist">
                                <li class="nav-item" role="presentation">
                                    <button class="nav-link active" id="pending-tab" data-bs-toggle="tab" 
                                            data-bs-target="#pending-pane" type="button" role="tab">
                                        <i class="fas fa-clock me-2"></i>Pending Prescriptions
                                        <span class="badge bg-warning ms-2" id="pendingBadge">0</span>
                                    </button>
                                </li>
                                <li class="nav-item" role="presentation">
                                    <button class="nav-link" id="completed-tab" data-bs-toggle="tab" 
                                            data-bs-target="#completed-pane" type="button" role="tab">
                                        <i class="fas fa-check-circle me-2"></i>Completed Records
                                        <span class="badge bg-success ms-2" id="completedBadge">0</span>
                                    </button>
                                </li>
                            </ul>
                
                            <!-- Tab Content -->
                            <div class="tab-content" id="pharmacyTabContent">
                                <!-- Pending Prescriptions Tab -->
                                <div class="tab-pane fade show active" id="pending-pane" role="tabpanel">
                                    <div class="section-card">
                                        <div class="section-title">
                                            <i class="fas fa-prescription-bottle-alt text-warning"></i>
                                            <span>Pending Prescriptions</span>
                                            <small class="ms-auto text-muted">Auto-refreshes every 10 seconds</small>
                                        </div>
                
                                        <div class="table-responsive">
                                            <table class="table table-hover">
                                                <thead>
                                                    <tr>
                                                        <th>Token</th>
                                                        <th>Patient ID</th>
                                                        <th>Patient Name</th>
                                                        <th>Medicines</th>
                                                        <th>Tests</th>
                                                        <th>Consultation Time</th>
                                                        <th>Action</th>
                                                    </tr>
                                                </thead>
                                                <tbody id="pendingTableBody">
                                                    <tr>
                                                        <td colspan="7" class="text-center py-4">
                                                            <i class="fas fa-spinner fa-spin me-2"></i>Loading pending prescriptions...
                                                        </td>
                                                    </tr>
                                                </tbody>
                                            </table>
                                        </div>
                                    </div>
                                </div>
                
                                <!-- Completed Records Tab -->
                                <div class="tab-pane fade" id="completed-pane" role="tabpanel">
                                    <div class="section-card">
                                        <div class="section-title">
                                            <i class="fas fa-check-circle text-success"></i>
                                            <span>Completed Records</span>
                                            <small class="ms-auto text-muted">Last 50 completed prescriptions</small>
                                        </div>
                
                                        <div class="table-responsive">
                                            <table class="table table-hover">
                                                <thead>
                                                    <tr>
                                                        <th>Token</th>
                                                        <th>Patient ID</th>
                                                        <th>Patient Name</th>
                                                        <th>Medicines</th>
                                                        <th>Tests</th>
                                                        <th>Consultation Time</th>
                                                        <th>Completed Time</th>
                                                    </tr>
                                                </thead>
                                                <tbody id="completedTableBody">
                                                    <tr>
                                                        <td colspan="7" class="text-center py-4">
                                                            <i class="fas fa-spinner fa-spin me-2"></i>Loading completed records...
                                                        </td>
                                                    </tr>
                                                </tbody>
                                            </table>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                
                    <!-- Refresh Button -->
                    <button class="refresh-btn" id="refreshBtn" onclick="pharmacySystem.loadAllData()">
                        <i class="fas fa-sync-alt"></i>
                    </button>
                
                    <script src="https://cdnjs.cloudflare.com/ajax/libs/bootstrap/5.3.0/js/bootstrap.bundle.min.js"></script>
                    <script>
                        // Pharmacy Management System
                        class PharmacySystem {
                            constructor() {
                                this.refreshInterval = null;
                                this.initializeSystem();
                            }
                
                            initializeSystem() {
                                this.loadAllData();
                                this.setupAutoRefresh();
                                this.attachEventListeners();
                                console.log('Pharmacy Management System initialized');
                            }
                
                            attachEventListeners() {
                                // Tab change events
                                document.getElementById('pending-tab').addEventListener('shown.bs.tab', () => {
                                    this.loadPendingRecords();
                                });
                
                                document.getElementById('completed-tab').addEventListener('shown.bs.tab', () => {
                                    this.loadCompletedRecords();
                                });
                            }
                
                            setupAutoRefresh() {
                                // Auto-refresh every 10 seconds for pending records
                                this.refreshInterval = setInterval(() => {
                                    this.loadStats();
                                    // Only refresh pending if that tab is active
                                    if (document.getElementById('pending-tab').classList.contains('active')) {
                                        this.loadPendingRecords();
                                    }
                                }, 10000);
                            }
                
                            async loadAllData() {
                                const refreshBtn = document.getElementById('refreshBtn');
                                refreshBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
                
                                try {
                                    await Promise.all([
                                        this.loadStats(),
                                        this.loadPendingRecords(),
                                        this.loadCompletedRecords()
                                    ]);
                                    this.showNotification('Data refreshed successfully', 'success');
                                } catch (error) {
                                    console.error('Error loading data:', error);
                                    this.showNotification('Error refreshing data', 'danger');
                                } finally {
                                    refreshBtn.innerHTML = '<i class="fas fa-sync-alt"></i>';
                                }
                            }
                
                            async loadStats() {
                                try {
                                    const response = await fetch('/api/stats');
                                    if (response.ok) {
                                        const stats = await response.json();
                                        document.getElementById('pendingCount').textContent = stats.pending || 0;
                                        document.getElementById('todayCompletedCount').textContent = stats.todayCompleted || 0;
                                        document.getElementById('totalCompletedCount').textContent = stats.totalCompleted || 0;
                                        document.getElementById('pendingBadge').textContent = stats.pending || 0;
                                        document.getElementById('completedBadge').textContent = stats.totalCompleted || 0;
                                    }
                                } catch (error) {
                                    console.log('Loading stats...');
                                }
                            }
                
                            async loadPendingRecords() {
                                const tbody = document.getElementById('pendingTableBody');
                                tbody.innerHTML = `
                                    <tr>
                                        <td colspan="7" class="text-center py-4">
                                            <i class="fas fa-spinner fa-spin me-2"></i>Loading pending prescriptions...
                                        </td>
                                    </tr>
                                `;
                
                                try {
                                    const response = await fetch('/api/pending-records');
                                    if (response.ok) {
                                        const records = await response.json();
                                        this.displayPendingRecords(records);
                                    } else {
                                        throw new Error('Failed to load pending records');
                                    }
                                } catch (error) {
                                    tbody.innerHTML = `
                                        <tr>
                                            <td colspan="7" class="text-center py-4">
                                                <div class="empty-state">
                                                    <i class="fas fa-server text-muted"></i>
                                                    <p class="mt-3">Backend server required for pharmacy data</p>
                                                </div>
                                            </td>
                                        </tr>
                                    `;
                                }
                            }
                
                            async loadCompletedRecords() {
                                const tbody = document.getElementById('completedTableBody');
                                tbody.innerHTML = `
                                    <tr>
                                        <td colspan="7" class="text-center py-4">
                                            <i class="fas fa-spinner fa-spin me-2"></i>Loading completed records...
                                        </td>
                                    </tr>
                                `;
                
                                try {
                                    const response = await fetch('/api/completed-records');
                                    if (response.ok) {
                                        const records = await response.json();
                                        this.displayCompletedRecords(records);
                                    } else {
                                        throw new Error('Failed to load completed records');
                                    }
                                } catch (error) {
                                    tbody.innerHTML = `
                                        <tr>
                                            <td colspan="7" class="text-center py-4">
                                                <div class="empty-state">
                                                    <i class="fas fa-server text-muted"></i>
                                                    <p class="mt-3">Backend server required for pharmacy data</p>
                                                </div>
                                            </td>
                                        </tr>
                                    `;
                                }
                            }
                
                            displayPendingRecords(records) {
                                const tbody = document.getElementById('pendingTableBody');
                
                                if (records.length === 0) {
                                    tbody.innerHTML = `
                                        <tr>
                                            <td colspan="7" class="text-center py-4">
                                                <div class="empty-state">
                                                    <i class="fas fa-prescription-bottle-alt"></i>
                                                    <p class="mt-3">No pending prescriptions</p>
                                                    <small class="text-muted">Completed consultations will appear here</small>
                                                </div>
                                            </td>
                                        </tr>
                                    `;
                                    return;
                                }
                
                                tbody.innerHTML = records.map(record => {
                                    const consultationTime = new Date(record.consultationTime);
                                    const now = new Date();
                                    const timeDiff = (now - consultationTime) / (1000 * 60); // minutes
                                    const isUrgent = timeDiff > 30; // More than 30 minutes waiting
                
                                    return `
                                        <tr ${isUrgent ? 'class="urgent-record"' : ''}>
                                            <td>
                                                <span class="badge bg-primary">${record.token}</span>
                                                ${isUrgent ? '<i class="fas fa-exclamation-triangle text-danger ms-2" title="Waiting > 30 minutes"></i>' : ''}
                                            </td>
                                            <td><strong>${record.patientId}</strong></td>
                                            <td>${record.patientName}</td>
                                            <td class="medicine-cell">
                                                <small>${record.medicines || 'No medicines prescribed'}</small>
                                            </td>
                                            <td class="test-cell">
                                                <small>${record.tests || 'No tests prescribed'}</small>
                                            </td>
                                            <td>
                                                <small>${consultationTime.toLocaleString()}</small>
                                                <br><small class="text-muted">${this.getTimeAgo(consultationTime)} ago</small>
                                            </td>
                                            <td>
                                                <button class="btn btn-success btn-sm" 
                                                        onclick="pharmacySystem.completePharmacy(${record.id}, '${record.patientName}')">
                                                    <i class="fas fa-check me-1"></i>Complete
                                                </button>
                                            </td>
                                        </tr>
                                    `;
                                }).join('');
                            }
                
                            displayCompletedRecords(records) {
                                const tbody = document.getElementById('completedTableBody');
                
                                if (records.length === 0) {
                                    tbody.innerHTML = `
                                        <tr>
                                            <td colspan="7" class="text-center py-4">
                                                <div class="empty-state">
                                                    <i class="fas fa-check-circle"></i>
                                                    <p class="mt-3">No completed records</p>
                                                    <small class="text-muted">Completed prescriptions will appear here</small>
                                                </div>
                                            </td>
                                        </tr>
                                    `;
                                    return;
                                }
                
                                tbody.innerHTML = records.map(record => {
                                    const consultationTime = new Date(record.consultationTime);
                                    const pharmacyTime = new Date(record.pharmacyCompletedTime || record.consultationTime);
                
                                    return `
                                        <tr>
                                            <td><span class="badge bg-success">${record.token}</span></td>
                                            <td><strong>${record.patientId}</strong></td>
                                            <td>${record.patientName}</td>
                                            <td class="medicine-cell">
                                                <small>${record.medicines || 'No medicines prescribed'}</small>
                                            </td>
                                            <td class="test-cell">
                                                <small>${record.tests || 'No tests prescribed'}</small>
                                            </td>
                                            <td><small>${consultationTime.toLocaleString()}</small></td>
                                            <td>
                                                <small class="text-success">${pharmacyTime.toLocaleString()}</small>
                                            </td>
                                        </tr>
                                    `;
                                }).join('');
                            }
                
                            async completePharmacy(recordId, patientName) {
                                if (!confirm(`Mark prescription as completed for ${patientName}?`)) {
                                    return;
                                }
                
                                try {
                                    const response = await fetch('/api/complete-pharmacy', {
                                        method: 'POST',
                                        headers: {
                                            'Content-Type': 'application/json'
                                        },
                                        body: JSON.stringify({ recordId: recordId })
                                    });
                
                                    const result = await response.json();
                
                                    if (response.ok && result.success) {
                                        this.showNotification(`Prescription completed for ${patientName}`, 'success');
                                        this.loadAllData(); // Refresh all data
                                    } else {
                                        this.showNotification(result.message || 'Failed to complete prescription', 'danger');
                                    }
                                } catch (error) {
                                    console.error('Error completing pharmacy record:', error);
                                    this.showNotification('Error completing prescription', 'danger');
                                }
                            }
                
                            getTimeAgo(date) {
                                const now = new Date();
                                const diffInMinutes = Math.floor((now - date) / (1000 * 60));
                
                                if (diffInMinutes < 1) return '< 1 min';
                                if (diffInMinutes < 60) return `${diffInMinutes} min`;
                
                                const diffInHours = Math.floor(diffInMinutes / 60);
                                if (diffInHours < 24) return `${diffInHours}h ${diffInMinutes % 60}m`;
                
                                const diffInDays = Math.floor(diffInHours / 24);
                                return `${diffInDays} day${diffInDays > 1 ? 's' : ''}`;
                            }
                
                            showNotification(message, type) {
                                const container = document.getElementById('notificationContainer');
                                const notificationId = `notification-${Date.now()}`;
                
                                const notification = document.createElement('div');
                                notification.id = notificationId;
                                notification.className = `alert alert-${type} alert-dismissible notification`;
                                notification.innerHTML = `
                                    ${message}
                                    <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
                                `;
                
                                container.appendChild(notification);
                
                                // Auto-hide after 5 seconds
                                setTimeout(() => {
                                    if (document.getElementById(notificationId)) {
                                        notification.remove();
                                    }
                                }, 5000);
                            }
                        }
                
                        // Initialize the system
                        let pharmacySystem;
                        document.addEventListener('DOMContentLoaded', () => {
                            pharmacySystem = new PharmacySystem();
                        });
                    </script>
                </body>
                </html>
                """;
    }
}