// Complete Patient Registration System - Java Backend
// File: PatientRegistrationSystem.java
// Configurable Port with Fallback Options

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

public class HealthcareWebApp2 {

    private static final String DB_URL = "jdbc:postgresql://localhost:5433/healthcare";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "admin123";
    private static Connection connection;

    // Port configuration with fallback options
    private static final int[] AVAILABLE_PORTS = {5000, 5002, 5003, 5004, 8080, 8081, 8082, 3000, 3001};
    private static int SERVER_PORT = 5000;

    static class Patient {
        private Long id;
        private String phoneNumber;
        private String name;
        private String email;
        private String patientId;
        private Integer age;
        private String gender;
        private String location;
        private String issue;
        private Integer token;
        private LocalDateTime registrationTime;
        private LocalDate registrationDate;

        // Constructors
        public Patient() {
        }

        public Patient(String phoneNumber, String name, String email, Integer age,
                       String gender, String location, String issue) {
            this.phoneNumber = phoneNumber;
            this.name = name;
            this.email = email;
            this.age = age;
            this.gender = gender;
            this.location = location;
            this.issue = issue;
            this.registrationTime = LocalDateTime.now();
            this.registrationDate = LocalDate.now();
        }

        // Getters and setters
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPatientId() {
            return patientId;
        }

        public void setPatientId(String patientId) {
            this.patientId = patientId;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }

        public String getGender() {
            return gender;
        }

        public void setGender(String gender) {
            this.gender = gender;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getIssue() {
            return issue;
        }

        public void setIssue(String issue) {
            this.issue = issue;
        }

        public Integer getToken() {
            return token;
        }

        public void setToken(Integer token) {
            this.token = token;
        }

        public LocalDateTime getRegistrationTime() {
            return registrationTime;
        }

        public void setRegistrationTime(LocalDateTime registrationTime) {
            this.registrationTime = registrationTime;
        }

        public LocalDate getRegistrationDate() {
            return registrationDate;
        }

        public void setRegistrationDate(LocalDate registrationDate) {
            this.registrationDate = registrationDate;
        }

        public String toJson() {
            return String.format(
                    "{\"id\":%d,\"phoneNumber\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"patientId\":\"%s\"," +
                            "\"age\":%d,\"gender\":\"%s\",\"location\":\"%s\",\"issue\":\"%s\",\"token\":%d," +
                            "\"registrationTime\":\"%s\",\"registrationDate\":\"%s\"}",
                    id != null ? id : 0,
                    escapeJson(phoneNumber),
                    escapeJson(name),
                    escapeJson(email),
                    escapeJson(patientId),
                    age != null ? age : 0,
                    escapeJson(gender),
                    escapeJson(location),
                    escapeJson(issue),
                    token != null ? token : 0,
                    registrationTime != null ? registrationTime.toString() : "",
                    registrationDate != null ? registrationDate.toString() : ""
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

                createPatientsTable();
                System.out.println("✓ Database connected successfully!");

            } catch (ClassNotFoundException e) {
                System.err.println("✗ PostgreSQL JDBC driver not found!");
                System.exit(1);
            } catch (SQLException e) {
                System.err.println("✗ Database connection failed: " + e.getMessage());
                System.exit(1);
            }
        }

        static void createPatientsTable() {
            try (Statement stmt = connection.createStatement()) {
                DatabaseMetaData dbmd = connection.getMetaData();
                ResultSet tables = dbmd.getTables(null, null, "patients", null);

                if (!tables.next()) {
                    String createTableSQL = """
                                CREATE TABLE patients (
                                    id BIGSERIAL PRIMARY KEY,
                                    phone_number VARCHAR(15) NOT NULL,
                                    name VARCHAR(255) NOT NULL,
                                    email VARCHAR(255),
                                    patient_id VARCHAR(7) UNIQUE NOT NULL,
                                    age INTEGER NOT NULL,
                                    gender VARCHAR(10) NOT NULL,
                                    location VARCHAR(255) NOT NULL,
                                    issue TEXT NOT NULL,
                                    token INTEGER NOT NULL,
                                    registration_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                    registration_date DATE DEFAULT CURRENT_DATE
                                )
                            """;
                    stmt.execute(createTableSQL);
                    stmt.execute("CREATE INDEX idx_patients_phone ON patients(phone_number)");
                    stmt.execute("CREATE INDEX idx_patients_patient_id ON patients(patient_id)");
                    stmt.execute("CREATE INDEX idx_patients_date ON patients(registration_date)");
                    System.out.println("✓ Patients table created successfully!");
                } else {
                    System.out.println("✓ Patients table already exists.");
                }
                tables.close();

            } catch (SQLException e) {
                System.err.println("✗ Patient table creation error: " + e.getMessage());
            }
        }

        static String generatePatientId() {
            String sql = "SELECT patient_id FROM patients ORDER BY id DESC LIMIT 1";

            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery(sql);

                if (rs.next()) {
                    String lastId = rs.getString("patient_id");
                    int lastNumber = Integer.parseInt(lastId.substring(1));
                    return "P" + String.format("%04d", lastNumber + 1);
                } else {
                    return "P1001";
                }
            } catch (SQLException e) {
                System.err.println("Error generating patient ID: " + e.getMessage());
                return "P" + String.format("%04d", (int) (Math.random() * 9000) + 1000);
            }
        }

        static int getNextToken() {
            String sql = """
                        SELECT CASE 
                            WHEN MAX(token) IS NULL THEN 1 
                            ELSE MAX(token) + 1 
                        END as next_token 
                        FROM patients 
                        WHERE DATE(registration_date) = CURRENT_DATE
                    """;

            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery(sql);
                if (rs.next()) {
                    return rs.getInt("next_token");
                }
            } catch (SQLException e) {
                System.err.println("Error getting next token: " + e.getMessage());
            }
            return 1;
        }

        static Patient findExistingPatient(String phoneNumber) {
            String sql = "SELECT * FROM patients WHERE phone_number = ? ORDER BY id DESC LIMIT 1";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, phoneNumber);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    Patient patient = new Patient();
                    patient.setId(rs.getLong("id"));
                    patient.setPhoneNumber(rs.getString("phone_number"));
                    patient.setName(rs.getString("name"));
                    patient.setEmail(rs.getString("email"));
                    patient.setPatientId(rs.getString("patient_id"));
                    patient.setAge(rs.getInt("age"));
                    patient.setGender(rs.getString("gender"));
                    patient.setLocation(rs.getString("location"));
                    patient.setIssue(rs.getString("issue"));
                    patient.setToken(rs.getInt("token"));
                    patient.setRegistrationTime(rs.getTimestamp("registration_time").toLocalDateTime());
                    patient.setRegistrationDate(rs.getDate("registration_date").toLocalDate());
                    return patient;
                }
            } catch (SQLException e) {
                System.err.println("Error finding existing patient: " + e.getMessage());
            }
            return null;
        }

        static Patient registerPatient(Patient patient) {
            // Check if patient already exists
            Patient existingPatient = findExistingPatient(patient.getPhoneNumber());

            if (existingPatient != null) {
                // Returning patient - reuse patient ID, assign new token
                patient.setPatientId(existingPatient.getPatientId());
            } else {
                // New patient - generate new patient ID
                patient.setPatientId(generatePatientId());
            }

            // Assign next token for today
            patient.setToken(getNextToken());

            String sql = """
                        INSERT INTO patients (phone_number, name, email, patient_id, age, gender, 
                                            location, issue, token, registration_time, registration_date)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_DATE)
                    """;

            try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, patient.getPhoneNumber());
                pstmt.setString(2, patient.getName());
                pstmt.setString(3, patient.getEmail());
                pstmt.setString(4, patient.getPatientId());
                pstmt.setInt(5, patient.getAge());
                pstmt.setString(6, patient.getGender());
                pstmt.setString(7, patient.getLocation());
                pstmt.setString(8, patient.getIssue());
                pstmt.setInt(9, patient.getToken());

                int rowsAffected = pstmt.executeUpdate();

                if (rowsAffected > 0) {
                    ResultSet generatedKeys = pstmt.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        patient.setId(generatedKeys.getLong(1));
                        patient.setRegistrationTime(LocalDateTime.now());
                        patient.setRegistrationDate(LocalDate.now());
                        System.out.println("✓ Patient registered: " + patient.getPatientId() +
                                " Token: " + patient.getToken());
                        return patient;
                    }
                }
            } catch (SQLException e) {
                System.err.println("✗ Error registering patient: " + e.getMessage());
            }
            return null;
        }

        static List<Patient> getPatientHistory(String phoneNumber) {
            List<Patient> history = new ArrayList<>();
            String sql = "SELECT * FROM patients WHERE phone_number = ? ORDER BY registration_time DESC";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, phoneNumber);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    Patient patient = new Patient();
                    patient.setId(rs.getLong("id"));
                    patient.setPhoneNumber(rs.getString("phone_number"));
                    patient.setName(rs.getString("name"));
                    patient.setEmail(rs.getString("email"));
                    patient.setPatientId(rs.getString("patient_id"));
                    patient.setAge(rs.getInt("age"));
                    patient.setGender(rs.getString("gender"));
                    patient.setLocation(rs.getString("location"));
                    patient.setIssue(rs.getString("issue"));
                    patient.setToken(rs.getInt("token"));
                    patient.setRegistrationTime(rs.getTimestamp("registration_time").toLocalDateTime());
                    patient.setRegistrationDate(rs.getDate("registration_date").toLocalDate());
                    history.add(patient);
                }
            } catch (SQLException e) {
                System.err.println("Error loading patient history: " + e.getMessage());
            }
            return history;
        }

        static int getTodayRegistrationsCount() {
            String sql = "SELECT COUNT(*) as count FROM patients WHERE DATE(registration_date) = CURRENT_DATE";

            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery(sql);
                if (rs.next()) {
                    return rs.getInt("count");
                }
            } catch (SQLException e) {
                System.err.println("Error getting today's count: " + e.getMessage());
            }
            return 0;
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
        System.out.println("🔍 Trying random ports in range 9000-9999...");
        for (int i = 0; i < 20; i++) {
            int randomPort = 9000 + (int) (Math.random() * 1000);
            System.out.println("   Testing random port " + randomPort + "...");
            if (isPortAvailable(randomPort)) {
                System.out.println("✓ Random port " + randomPort + " is available!");
                return randomPort;
            }
        }

        System.err.println("❌ No available ports found after extensive search!");
        return -1; // No available port found
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
                String response = getRegistrationPageHTML();
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes(StandardCharsets.UTF_8));
                os.close();
            }
        }

        static class RegisterHandler implements HttpHandler {
            public void handle(HttpExchange exchange) throws IOException {
                if ("POST".equals(exchange.getRequestMethod())) {
                    handleRegistration(exchange);
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

            private void handleRegistration(HttpExchange exchange) throws IOException {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

                try {
                    String requestBody = readRequestBody(exchange);
                    System.out.println("=== RECEIVED REGISTRATION REQUEST ===");
                    System.out.println("Raw request body: " + requestBody);

                    Patient patient = parsePatientFromJson(requestBody);

                    if (patient == null) {
                        sendJsonResponse(exchange, 400, "{\"success\": false, \"message\": \"Invalid patient data\"}");
                        return;
                    }

                    Patient registeredPatient = DatabaseService.registerPatient(patient);

                    if (registeredPatient != null) {
                        String response = String.format(
                                "{\"success\": true, \"message\": \"Registration successful!\", " +
                                        "\"patientId\": \"%s\", \"token\": %d, \"isReturning\": %s}",
                                registeredPatient.getPatientId(),
                                registeredPatient.getToken(),
                                DatabaseService.findExistingPatient(patient.getPhoneNumber()) != null ? "true" : "false"
                        );
                        sendJsonResponse(exchange, 200, response);
                    } else {
                        sendJsonResponse(exchange, 500, "{\"success\": false, \"message\": \"Registration failed\"}");
                    }

                } catch (Exception e) {
                    System.err.println("Registration error: " + e.getMessage());
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

            private Patient parsePatientFromJson(String json) {
                try {
                    Patient patient = new Patient();

                    // Extract phoneNumber
                    String phoneNumber = extractJsonValue(json, "phoneNumber");
                    if (phoneNumber == null || phoneNumber.isEmpty()) return null;
                    patient.setPhoneNumber(phoneNumber);

                    // Extract fullName
                    String fullName = extractJsonValue(json, "fullName");
                    if (fullName == null || fullName.isEmpty()) return null;
                    patient.setName(fullName);

                    // Extract email
                    String email = extractJsonValue(json, "email");
                    patient.setEmail(email);

                    // Extract age
                    String ageStr = extractJsonValue(json, "age");
                    if (ageStr != null && !ageStr.isEmpty()) {
                        patient.setAge(Integer.parseInt(ageStr));
                    } else {
                        return null;
                    }

                    // Extract gender
                    String gender = extractJsonValue(json, "gender");
                    if (gender == null || gender.isEmpty()) return null;
                    patient.setGender(gender);

                    // Extract location
                    String location = extractJsonValue(json, "location");
                    if (location == null || location.isEmpty()) return null;
                    patient.setLocation(location);

                    // Extract medicalIssue
                    String medicalIssue = extractJsonValue(json, "medicalIssue");
                    if (medicalIssue == null || medicalIssue.isEmpty()) return null;
                    patient.setIssue(medicalIssue);

                    return patient;

                } catch (Exception e) {
                    System.err.println("Error parsing JSON: " + e.getMessage());
                    return null;
                }
            }

            private String extractJsonValue(String json, String key) {
                try {
                    String pattern = "\"" + key + "\":\"";
                    int start = json.indexOf(pattern);
                    if (start == -1) {
                        // Try without quotes (for numbers)
                        pattern = "\"" + key + "\":";
                        start = json.indexOf(pattern);
                        if (start == -1) return null;
                        start += pattern.length();

                        int end = json.indexOf(",", start);
                        if (end == -1) {
                            end = json.indexOf("}", start);
                        }
                        if (end == -1) return null;

                        return json.substring(start, end).trim();
                    } else {
                        start += pattern.length();
                        int end = json.indexOf("\"", start);
                        if (end == -1) return null;
                        return json.substring(start, end);
                    }
                } catch (Exception e) {
                    return null;
                }
            }
        }

        static class PatientHistoryHandler implements HttpHandler {
            public void handle(HttpExchange exchange) throws IOException {
                if ("GET".equals(exchange.getRequestMethod())) {
                    String path = exchange.getRequestURI().getPath();
                    String phoneNumber = path.substring(path.lastIndexOf("/") + 1);

                    try {
                        phoneNumber = URLDecoder.decode(phoneNumber, "UTF-8");
                    } catch (Exception e) {
                        System.err.println("Error decoding phone number: " + e.getMessage());
                    }

                    List<Patient> history = DatabaseService.getPatientHistory(phoneNumber);
                    StringBuilder json = new StringBuilder("[");
                    for (int i = 0; i < history.size(); i++) {
                        json.append(history.get(i).toJson());
                        if (i < history.size() - 1) json.append(",");
                    }
                    json.append("]");

                    sendJsonResponse(exchange, 200, json.toString());
                }
            }
        }

        static class StatsHandler implements HttpHandler {
            public void handle(HttpExchange exchange) throws IOException {
                if ("GET".equals(exchange.getRequestMethod())) {
                    int todayCount = DatabaseService.getTodayRegistrationsCount();
                    String response = String.format("{\"todayCount\": %d}", todayCount);
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
            System.out.println("🏥 Starting Patient Registration System...");

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
            server.createContext("/api/register", new WebHandlers.RegisterHandler());
            server.createContext("/api/patient-history", new WebHandlers.PatientHistoryHandler());
            server.createContext("/api/stats", new WebHandlers.StatsHandler());

            server.setExecutor(Executors.newFixedThreadPool(10));
            server.start();

            System.out.println("✅ Patient Registration System started successfully!");
            System.out.println("🌐 Server running at: http://localhost:" + SERVER_PORT);
            System.out.println("📋 Access registration page at: http://localhost:" + SERVER_PORT);
            System.out.println("💾 Database: " + DB_URL);
            System.out.println("🔗 Syncs with Doctor System at: http://localhost:5001");
            System.out.println("🔄 Server is ready to handle registrations...");
            System.out.println("\n📊 API Endpoints:");
            System.out.println("  POST /api/register - Register new patient");
            System.out.println("  GET  /api/patient-history/{phoneNumber} - Get patient history");
            System.out.println("  GET  /api/stats - Get today's registration count");
            System.out.println("\n⚠  To stop server: Press Ctrl+C");

            if (SERVER_PORT != 5000) {
                System.out.println("\n⚠️  NOTE: Server is running on port " + SERVER_PORT + " instead of 5000");
                System.out.println("   This is because port 5000 was already in use.");
            }

        } catch (Exception e) {
            System.err.println("❌ Failed to start Patient Registration System: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    static String getRegistrationPageHTML() {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Healthcare Registration System</title>
                    <link href="https://cdnjs.cloudflare.com/ajax/libs/bootstrap/5.3.0/css/bootstrap.min.css" rel="stylesheet">
                    <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
                    <style>
                        :root {
                            --primary-color: #3b82f6;
                            --secondary-color: #8b5cf6;
                            --success-color: #10b981;
                            --danger-color: #ef4444;
                            --warning-color: #f59e0b;
                            --info-color: #06b6d4;
                            --dark-color: #1f2937;
                        }
                
                        body {
                            background: linear-gradient(135deg, var(--primary-color) 0%, var(--secondary-color) 100%);
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
                
                        .stats-container {
                            display: flex;
                            justify-content: center;
                            gap: 30px;
                            margin-bottom: 40px;
                            flex-wrap: wrap;
                        }
                
                        .stat-card {
                            background: linear-gradient(135deg, var(--success-color), var(--info-color));
                            color: white;
                            padding: 25px;
                            border-radius: 15px;
                            text-align: center;
                            box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);
                            min-width: 200px;
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
                            font-size: 1rem;
                            margin: 0;
                            opacity: 0.9;
                        }
                
                        .form-section {
                            background: white;
                            border-radius: 15px;
                            padding: 30px;
                            margin-bottom: 30px;
                            box-shadow: 0 5px 15px rgba(0, 0, 0, 0.05);
                        }
                
                        .form-control, .form-select {
                            border: 2px solid #e2e8f0;
                            border-radius: 10px;
                            padding: 12px 16px;
                            font-size: 1rem;
                            transition: all 0.3s ease;
                        }
                
                        .form-control:focus, .form-select:focus {
                            border-color: var(--primary-color);
                            box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
                        }
                
                        .form-label {
                            font-weight: 600;
                            color: var(--dark-color);
                            margin-bottom: 8px;
                        }
                
                        .btn {
                            border-radius: 10px;
                            padding: 12px 30px;
                            font-weight: 600;
                            font-size: 1rem;
                            transition: all 0.3s ease;
                        }
                
                        .btn-primary {
                            background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
                            border: none;
                        }
                
                        .btn-primary:hover {
                            transform: translateY(-2px);
                            box-shadow: 0 5px 15px rgba(59, 130, 246, 0.4);
                        }
                
                        .btn-danger {
                            background: linear-gradient(135deg, var(--danger-color), #dc2626);
                            border: none;
                        }
                
                        .btn-danger:hover {
                            transform: translateY(-2px);
                            box-shadow: 0 5px 15px rgba(239, 68, 68, 0.4);
                        }
                
                        .action-buttons {
                            display: flex;
                            gap: 15px;
                            justify-content: center;
                            flex-wrap: wrap;
                        }
                
                        .alert {
                            border: none;
                            border-radius: 10px;
                            padding: 15px 20px;
                            margin-bottom: 20px;
                        }
                
                        .patient-history-section {
                            background: white;
                            border-radius: 15px;
                            padding: 30px;
                            box-shadow: 0 5px 15px rgba(0, 0, 0, 0.05);
                        }
                
                        .search-container {
                            position: relative;
                            margin-bottom: 20px;
                        }
                
                        .search-container .form-control {
                            padding-left: 50px;
                        }
                
                        .search-container .fas {
                            position: absolute;
                            left: 18px;
                            top: 50%;
                            transform: translateY(-50%);
                            color: #6b7280;
                        }
                
                        .table {
                            border-radius: 10px;
                            overflow: hidden;
                            box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
                        }
                
                        .table thead th {
                            background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
                            color: white;
                            border: none;
                            font-weight: 600;
                        }
                
                        .table tbody tr:hover {
                            background: #f8fafc;
                            cursor: pointer;
                        }
                
                        .info-note {
                            background: #eff6ff;
                            border: 1px solid #bfdbfe;
                            color: #1e40af;
                            padding: 12px;
                            border-radius: 8px;
                            font-size: 0.9rem;
                            margin-bottom: 20px;
                        }
                
                        @media (max-width: 768px) {
                            .stats-container {
                                flex-direction: column;
                                align-items: center;
                            }
                
                            .action-buttons {
                                flex-direction: column;
                            }
                
                            .btn {
                                width: 100%;
                            }
                        }
                    </style>
                </head>
                <body>
                    <div class="container-fluid">
                        <div class="main-container">
                            <!-- Main Header -->
                            <div class="header">
                                <h1 class="display-4 fw-bold text-primary mb-2">
                                    <i class="fas fa-hospital-user me-3"></i>Healthcare Registration System
                                </h1>
                                <p class="lead text-muted" id="serverInfo">Complete Patient Registration & Management</p>
                            </div>
                
                            <!-- Stats Section at Top -->
                            <div class="stats-container">
                                <div class="stat-card">
                                    <h3 id="nextConsultationToken">-</h3>
                                    <p><i class="fas fa-stethoscope me-2"></i>Next Consultation Token</p>
                                </div>
                                <div class="stat-card">
                                    <h3 id="todayRegistrations">0</h3>
                                    <p><i class="fas fa-users me-2"></i>Today's Registrations</p>
                                </div>
                            </div>
                
                            <div class="row">
                                <!-- Registration Form -->
                                <div class="col-lg-6">
                                    <div class="form-section">
                                        <div class="info-note">
                                            <i class="fas fa-info-circle me-2"></i>
                                            New patients get unique ID, returning patients reuse existing ID
                                        </div>
                
                                        <div id="alertContainer"></div>
                
                                        <form id="registrationForm">
                                            <div class="mb-3">
                                                <label class="form-label">
                                                    <i class="fas fa-phone me-2"></i>Phone Number *
                                                </label>
                                                <input type="tel" class="form-control" id="phoneNumber" 
                                                       pattern="[0-9]{10}" placeholder="Enter 10-digit phone number" required>
                                            </div>
                
                                            <div class="mb-3">
                                                <label class="form-label">
                                                    <i class="fas fa-user me-2"></i>Full Name *
                                                </label>
                                                <input type="text" class="form-control" id="fullName" 
                                                       placeholder="Enter full name" required>
                                            </div>
                
                                            <div class="mb-3">
                                                <label class="form-label">
                                                    <i class="fas fa-envelope me-2"></i>Email
                                                </label>
                                                <input type="email" class="form-control" id="email" 
                                                       placeholder="Enter email address">
                                            </div>
                
                                            <div class="row">
                                                <div class="col-md-6 mb-3">
                                                    <label class="form-label">
                                                        <i class="fas fa-birthday-cake me-2"></i>Age *
                                                    </label>
                                                    <input type="number" class="form-control" id="age" 
                                                           min="1" max="120" placeholder="Enter age" required>
                                                </div>
                                                <div class="col-md-6 mb-3">
                                                    <label class="form-label">
                                                        <i class="fas fa-venus-mars me-2"></i>Gender *
                                                    </label>
                                                    <select class="form-select" id="gender" required>
                                                        <option value="">Select Gender</option>
                                                        <option value="Male">Male</option>
                                                        <option value="Female">Female</option>
                                                        <option value="Other">Other</option>
                                                    </select>
                                                </div>
                                            </div>
                
                                            <div class="mb-3">
                                                <label class="form-label">
                                                    <i class="fas fa-map-marker-alt me-2"></i>Location *
                                                </label>
                                                <input type="text" class="form-control" id="location" 
                                                       placeholder="Enter location" required>
                                            </div>
                
                                            <div class="mb-4">
                                                <label class="form-label">
                                                    <i class="fas fa-notes-medical me-2"></i>Medical Issue *
                                                </label>
                                                <textarea class="form-control" id="medicalIssue" rows="4" 
                                                          placeholder="Describe the medical issue" required></textarea>
                                            </div>
                
                                            <div class="action-buttons">
                                                <button type="button" class="btn btn-danger" id="clearFormBtn">
                                                    <i class="fas fa-eraser me-2"></i>Clear Form
                                                </button>
                                                <button type="submit" class="btn btn-primary" id="registerBtn">
                                                    <i class="fas fa-user-plus me-2"></i>Register Patient
                                                </button>
                                            </div>
                                        </form>
                                    </div>
                                </div>
                
                                <!-- Patient History -->
                                <div class="col-lg-6">
                                    <div class="patient-history-section">
                                        <div class="search-container">
                                            <i class="fas fa-search"></i>
                                            <input type="tel" class="form-control" id="historySearch" 
                                                   placeholder="Enter a 10-digit phone number to view patient history">
                                        </div>
                
                                        <div class="info-note">
                                            <i class="fas fa-mouse-pointer me-2"></i>
                                            Double-click on any row to auto-fill the form. Same Patient ID for returning patients.
                                        </div>
                
                                        <div class="table-responsive">
                                            <table class="table table-hover">
                                                <thead>
                                                    <tr>
                                                        <th>Name</th>
                                                        <th>Patient ID</th>
                                                        <th>Age</th>
                                                        <th>Gender</th>
                                                        <th>Token</th>
                                                        <th>Date/Time</th>
                                                    </tr>
                                                </thead>
                                                <tbody id="historyTableBody">
                                                    <tr>
                                                        <td colspan="6" class="text-center text-muted py-4">
                                                            Enter a phone number to search patient history
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
                
                    <script src="https://cdnjs.cloudflare.com/ajax/libs/bootstrap/5.3.0/js/bootstrap.bundle.min.js"></script>
                    <script>
                        // Registration System JavaScript
                        class RegistrationSystem {
                            constructor() {
                                this.initializeSystem();
                            }
                
                            initializeSystem() {
                                this.attachEventListeners();
                                this.loadStats();
                                this.updateServerInfo();
                                // Auto-refresh stats every 10 seconds
                                setInterval(() => this.loadStats(), 10000);
                                console.log('Healthcare Registration System initialized');
                            }
                
                            updateServerInfo() {
                                const port = window.location.port || '80';
                                document.getElementById('serverInfo').textContent = 
                                    `Complete Patient Registration & Management - localhost:${port}`;
                            }
                
                            attachEventListeners() {
                                // Form submission
                                document.getElementById('registrationForm').addEventListener('submit', (e) => {
                                    e.preventDefault();
                                    this.registerPatient();
                                });
                
                                // Clear form
                                document.getElementById('clearFormBtn').addEventListener('click', () => {
                                    this.clearForm();
                                });
                
                                // History search
                                document.getElementById('historySearch').addEventListener('input', (e) => {
                                    if (e.target.value.length === 10) {
                                        this.searchPatientHistory(e.target.value);
                                    } else if (e.target.value.length === 0) {
                                        this.clearHistoryTable();
                                    }
                                });
                            }
                
                            async loadStats() {
                                try {
                                    // Load next consultation token from doctor system
                                    try {
                                        const consultationResponse = await fetch('http://localhost:5001/api/next-consultation-token');
                                        if (consultationResponse.ok) {
                                            const consultationData = await consultationResponse.json();
                                            const nextTokenElement = document.getElementById('nextConsultationToken');
                
                                            if (consultationData.nextConsultationToken !== null) {
                                                nextTokenElement.textContent = consultationData.nextConsultationToken;
                                            } else {
                                                nextTokenElement.textContent = '-';
                                            }
                                        } else {
                                            document.getElementById('nextConsultationToken').textContent = 'N/A';
                                        }
                                    } catch (consultationError) {
                                        console.log('Doctor system not available');
                                        document.getElementById('nextConsultationToken').textContent = 'N/A';
                                    }
                
                                    // Load today's registrations count
                                    try {
                                        const statsResponse = await fetch('/api/stats');
                                        if (statsResponse.ok) {
                                            const statsData = await statsResponse.json();
                                            document.getElementById('todayRegistrations').textContent = statsData.todayCount || 0;
                                        }
                                    } catch (statsError) {
                                        console.log('Stats API loading from server...');
                                    }
                                } catch (error) {
                                    console.log('Loading stats...');
                                }
                            }
                
                            async registerPatient() {
                                const registerBtn = document.getElementById('registerBtn');
                                registerBtn.disabled = true;
                                registerBtn.innerHTML = '<i class="fas fa-spinner fa-spin me-2"></i>Registering...';
                
                                try {
                                    const formData = {
                                        phoneNumber: document.getElementById('phoneNumber').value,
                                        fullName: document.getElementById('fullName').value,
                                        email: document.getElementById('email').value,
                                        age: parseInt(document.getElementById('age').value),
                                        gender: document.getElementById('gender').value,
                                        location: document.getElementById('location').value,
                                        medicalIssue: document.getElementById('medicalIssue').value
                                    };
                
                                    const response = await fetch('/api/register', {
                                        method: 'POST',
                                        headers: {
                                            'Content-Type': 'application/json'
                                        },
                                        body: JSON.stringify(formData)
                                    });
                
                                    if (response.ok) {
                                        const result = await response.json();
                                        this.showAlert(`Registration successful! Patient ID: ${result.patientId}, Token: ${result.token}`, 'success');
                                        this.clearForm();
                                        this.loadStats(); // Refresh stats immediately
                                    } else {
                                        const error = await response.json().catch(() => ({ message: 'Registration failed' }));
                                        this.showAlert(error.message || 'Registration failed', 'danger');
                                    }
                                } catch (error) {
                                    console.log('Registration processing...');
                                    this.showAlert('Please ensure backend server is running', 'warning');
                                } finally {
                                    registerBtn.disabled = false;
                                    registerBtn.innerHTML = '<i class="fas fa-user-plus me-2"></i>Register Patient';
                                }
                            }
                
                            async searchPatientHistory(phoneNumber) {
                                try {
                                    const response = await fetch(`/api/patient-history/${phoneNumber}`);
                                    if (response.ok) {
                                        const history = await response.json();
                                        this.displayHistory(history);
                                    } else {
                                        this.displayHistory([]);
                                    }
                                } catch (error) {
                                    const tbody = document.getElementById('historyTableBody');
                                    tbody.innerHTML = `
                                        <tr>
                                            <td colspan="6" class="text-center text-muted py-4">
                                                <i class="fas fa-server me-2"></i>
                                                Backend server required for history search
                                            </td>
                                        </tr>
                                    `;
                                }
                            }
                
                            displayHistory(history) {
                                const tbody = document.getElementById('historyTableBody');
                
                                if (history.length === 0) {
                                    tbody.innerHTML = `
                                        <tr>
                                            <td colspan="6" class="text-center text-muted py-4">
                                                No patient history found
                                            </td>
                                        </tr>
                                    `;
                                    return;
                                }
                
                                tbody.innerHTML = history.map(patient => `
                                    <tr onclick="registrationSystem.fillForm(${JSON.stringify(patient).replace(/"/g, '&quot;')})" 
                                        style="cursor: pointer;">
                                        <td>${patient.name}</td>
                                        <td>${patient.patientId}</td>
                                        <td>${patient.age}</td>
                                        <td>${patient.gender}</td>
                                        <td>${patient.token}</td>
                                        <td>${new Date(patient.registrationTime).toLocaleString()}</td>
                                    </tr>
                                `).join('');
                            }
                
                            fillForm(patient) {
                                document.getElementById('phoneNumber').value = patient.phoneNumber;
                                document.getElementById('fullName').value = patient.name;
                                document.getElementById('email').value = patient.email || '';
                                document.getElementById('age').value = patient.age;
                                document.getElementById('gender').value = patient.gender;
                                document.getElementById('location').value = patient.location;
                                document.getElementById('medicalIssue').value = patient.issue;
                
                                this.showAlert('Form auto-filled from patient history', 'info');
                            }
                
                            clearForm() {
                                document.getElementById('registrationForm').reset();
                                this.showAlert('Form cleared successfully', 'info');
                            }
                
                            clearHistoryTable() {
                                document.getElementById('historyTableBody').innerHTML = `
                                    <tr>
                                        <td colspan="6" class="text-center text-muted py-4">
                                            Enter a phone number to search patient history
                                        </td>
                                    </tr>
                                `;
                            }
                
                            showAlert(message, type) {
                                const alertContainer = document.getElementById('alertContainer');
                                const alertDiv = document.createElement('div');
                                alertDiv.className = `alert alert-${type} alert-dismissible fade show`;
                                alertDiv.innerHTML = `
                                    <i class="fas ${type === 'success' ? 'fa-check-circle' : 
                                                   type === 'danger' ? 'fa-exclamation-triangle' : 
                                                   'fa-info-circle'} me-2"></i>
                                    ${message}
                                    <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
                                `;
                                alertContainer.appendChild(alertDiv);
                
                                setTimeout(() => {
                                    if (alertDiv.parentElement) {
                                        alertDiv.remove();
                                    }
                                }, 5000);
                            }
                        }
                
                        // Initialize the system
                        let registrationSystem;
                        document.addEventListener('DOMContentLoaded', () => {
                            registrationSystem = new RegistrationSystem();
                        });
                    </script>
                </body>
                </html>
                """;
    }
}