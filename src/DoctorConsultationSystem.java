// Complete Fixed Doctor Consultation System - Final Version with Registration Sync
// File: DoctorConsultationSystem.java
// Port: 5001

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

public class DoctorConsultationSystem {

    private static final String DB_URL = "jdbc:postgresql://localhost:5433/healthcare";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "admin123";
    private static Connection connection;

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

    static class DoctorConsultation {
        private Long id;
        private String patientId;
        private String medicines;
        private String tests;
        private String nextVisitDays;
        private String issue;
        private LocalDateTime createdAt;

        public DoctorConsultation() {
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

        public String getNextVisitDays() {
            return nextVisitDays;
        }

        public void setNextVisitDays(String nextVisitDays) {
            this.nextVisitDays = nextVisitDays;
        }

        public String getIssue() {
            return issue;
        }

        public void setIssue(String issue) {
            this.issue = issue;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
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

                createDoctorTable();
                System.out.println("✓ Database connected successfully!");

            } catch (ClassNotFoundException e) {
                System.err.println("✗ PostgreSQL JDBC driver not found!");
                System.exit(1);
            } catch (SQLException e) {
                System.err.println("✗ Database connection failed: " + e.getMessage());
                System.exit(1);
            }
        }

        static void createDoctorTable() {
            try (Statement stmt = connection.createStatement()) {
                DatabaseMetaData dbmd = connection.getMetaData();
                ResultSet tables = dbmd.getTables(null, null, "doctors", null);

                if (!tables.next()) {
                    String createTableSQL = """
                                CREATE TABLE doctors (
                                    id BIGSERIAL PRIMARY KEY,
                                    patient_id VARCHAR(7) NOT NULL,
                                    medicines TEXT,
                                    tests TEXT,
                                    next_visit_days TEXT,
                                    issue TEXT,
                                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                                )
                            """;
                    stmt.execute(createTableSQL);
                    stmt.execute("CREATE INDEX idx_doctors_patient_id ON doctors(patient_id)");
                    System.out.println("✓ Doctors table created successfully!");
                } else {
                    System.out.println("✓ Doctors table already exists.");
                }
                tables.close();

            } catch (SQLException e) {
                System.err.println("✗ Doctor table creation error: " + e.getMessage());
            }
        }

        static List<Patient> getPendingPatients() {
            List<Patient> patients = new ArrayList<>();
            String sql = """
                        SELECT p.* FROM patients p 
                        WHERE DATE(p.registration_date) = CURRENT_DATE 
                        AND p.patient_id NOT IN (
                            SELECT DISTINCT d.patient_id FROM doctors d 
                            WHERE DATE(d.created_at) = CURRENT_DATE 
                            AND d.patient_id IS NOT NULL
                        )
                        ORDER BY p.token ASC
                    """;

            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery(sql);

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
                    patients.add(patient);
                }

                System.out.println("Found " + patients.size() + " pending patients");

            } catch (SQLException e) {
                System.err.println("Error loading pending patients: " + e.getMessage());
            }

            return patients;
        }

        // NEW METHOD: Get next consultation token (same logic as patient registration)
        static Integer getNextTokenForConsultation() {
            String sql = """
                        SELECT CASE 
                            WHEN MIN(p.token) IS NULL THEN NULL
                            ELSE MIN(p.token)
                        END as next_consultation_token 
                        FROM patients p 
                        WHERE DATE(p.registration_date) = CURRENT_DATE 
                        AND p.patient_id NOT IN (
                            SELECT DISTINCT d.patient_id FROM doctors d 
                            WHERE DATE(d.created_at) = CURRENT_DATE 
                            AND d.patient_id IS NOT NULL
                        )
                    """;

            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery(sql);
                if (rs.next()) {
                    Integer nextToken = rs.getObject("next_consultation_token", Integer.class);
                    System.out.println("Next token for consultation: " + (nextToken != null ? nextToken : "No pending patients"));
                    return nextToken; // Can be null if no pending patients
                }
            } catch (SQLException e) {
                System.err.println("Error getting next consultation token: " + e.getMessage());
            }
            return null;
        }

        static List<DoctorConsultation> getPatientHistory(String patientId) {
            List<DoctorConsultation> history = new ArrayList<>();
            String sql = "SELECT * FROM doctors WHERE patient_id = ? ORDER BY created_at DESC";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, patientId);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    DoctorConsultation consultation = new DoctorConsultation();
                    consultation.setId(rs.getLong("id"));
                    consultation.setPatientId(rs.getString("patient_id"));
                    consultation.setMedicines(rs.getString("medicines"));
                    consultation.setTests(rs.getString("tests"));
                    consultation.setNextVisitDays(rs.getString("next_visit_days"));
                    consultation.setIssue(rs.getString("issue"));
                    consultation.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    history.add(consultation);
                }
            } catch (SQLException e) {
                System.err.println("Error loading consultation history: " + e.getMessage());
            }

            return history;
        }

        static boolean saveConsultation(DoctorConsultation consultation) {
            String sql = """
                        INSERT INTO doctors (patient_id, medicines, tests, next_visit_days, issue, created_at)
                        VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """;

            System.out.println("=== SAVING CONSULTATION ===");
            System.out.println("Patient ID: " + consultation.getPatientId());
            System.out.println("Medicines: " + consultation.getMedicines());
            System.out.println("Tests: " + consultation.getTests());
            System.out.println("Next Visit Days: " + consultation.getNextVisitDays());
            System.out.println("Issue: " + consultation.getIssue());

            try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                pstmt.setString(1, consultation.getPatientId());
                pstmt.setString(2, consultation.getMedicines());
                pstmt.setString(3, consultation.getTests());
                pstmt.setString(4, consultation.getNextVisitDays());
                pstmt.setString(5, consultation.getIssue());

                int rowsAffected = pstmt.executeUpdate();
                System.out.println("Rows affected: " + rowsAffected);

                if (rowsAffected > 0) {
                    ResultSet generatedKeys = pstmt.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        consultation.setId(generatedKeys.getLong(1));
                        System.out.println("✓ Consultation saved successfully with ID: " + consultation.getId());
                        return true;
                    }
                }

                return false;

            } catch (SQLException e) {
                System.err.println("✗ Error saving consultation: " + e.getMessage());
                e.printStackTrace();
                return false;
            } finally {
                System.out.println("=== END SAVE CONSULTATION ===\n");
            }
        }
    }

    static class WebHandlers {

        static class DoctorHomeHandler implements HttpHandler {
            public void handle(HttpExchange exchange) throws IOException {
                String response = getDoctorPageHTML();
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes(StandardCharsets.UTF_8));
                os.close();
            }
        }

        static class PendingPatientsHandler implements HttpHandler {
            public void handle(HttpExchange exchange) throws IOException {
                if ("GET".equals(exchange.getRequestMethod())) {
                    List<Patient> patients = DatabaseService.getPendingPatients();
                    StringBuilder json = new StringBuilder("[");
                    for (int i = 0; i < patients.size(); i++) {
                        json.append(patients.get(i).toJson());
                        if (i < patients.size() - 1) json.append(",");
                    }
                    json.append("]");

                    sendJsonResponse(exchange, 200, json.toString());
                }
            }
        }

        // NEW HANDLER: Get next consultation token for sync with registration system
        static class NextConsultationTokenHandler implements HttpHandler {
            public void handle(HttpExchange exchange) throws IOException {
                if ("GET".equals(exchange.getRequestMethod())) {
                    Integer nextConsultationToken = DatabaseService.getNextTokenForConsultation();

                    String response = String.format(
                            "{\"nextConsultationToken\": %s}",
                            nextConsultationToken != null ? nextConsultationToken.toString() : "null"
                    );

                    sendJsonResponse(exchange, 200, response);
                }
            }
        }

        static class ConsultationHistoryHandler implements HttpHandler {
            public void handle(HttpExchange exchange) throws IOException {
                if ("GET".equals(exchange.getRequestMethod())) {
                    String path = exchange.getRequestURI().getPath();
                    String patientId = path.substring(path.lastIndexOf("/") + 1);

                    List<DoctorConsultation> history = DatabaseService.getPatientHistory(patientId);
                    StringBuilder json = new StringBuilder("[");
                    for (int i = 0; i < history.size(); i++) {
                        DoctorConsultation consultation = history.get(i);
                        json.append(String.format(
                                "{\"id\":%d,\"patientId\":\"%s\",\"medicines\":\"%s\",\"tests\":\"%s\",\"nextVisitDays\":\"%s\",\"issue\":\"%s\",\"createdAt\":\"%s\"}",
                                consultation.getId(),
                                escapeJson(consultation.getPatientId()),
                                escapeJson(consultation.getMedicines()),
                                escapeJson(consultation.getTests()),
                                escapeJson(consultation.getNextVisitDays()),
                                escapeJson(consultation.getIssue()),
                                consultation.getCreatedAt().toString()
                        ));
                        if (i < history.size() - 1) json.append(",");
                    }
                    json.append("]");

                    sendJsonResponse(exchange, 200, json.toString());
                }
            }
        }

        static class SaveConsultationHandler implements HttpHandler {
            public void handle(HttpExchange exchange) throws IOException {
                if ("POST".equals(exchange.getRequestMethod())) {
                    handleSaveConsultation(exchange);
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

            private void handleSaveConsultation(HttpExchange exchange) throws IOException {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

                try {
                    String requestBody = readRequestBody(exchange);
                    System.out.println("=== RECEIVED CONSULTATION REQUEST ===");
                    System.out.println("Raw request body: " + requestBody);

                    DoctorConsultation consultation = parseConsultationFromJson(requestBody);

                    String patientIssue = getPatientIssue(consultation.getPatientId());
                    consultation.setIssue(patientIssue);
                    consultation.setCreatedAt(LocalDateTime.now());

                    boolean saved = DatabaseService.saveConsultation(consultation);
                    System.out.println("Save result: " + saved);

                    if (saved) {
                        sendJsonResponse(exchange, 200, "{\"success\": true, \"message\": \"Consultation completed successfully!\"}");
                    } else {
                        sendJsonResponse(exchange, 500, "{\"success\": false, \"message\": \"Failed to save consultation to database.\"}");
                    }

                } catch (Exception e) {
                    System.err.println("Consultation save error: " + e.getMessage());
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

            private DoctorConsultation parseConsultationFromJson(String json) {
                DoctorConsultation consultation = new DoctorConsultation();

                try {
                    // Extract patientId
                    int patientIdStart = json.indexOf("\"patientId\":\"") + 13;
                    int patientIdEnd = json.indexOf("\"", patientIdStart);
                    String patientId = json.substring(patientIdStart, patientIdEnd);
                    consultation.setPatientId(patientId);

                    // Extract medicines
                    int medicinesStart = json.indexOf("\"medicines\":\"") + 13;
                    if (medicinesStart > 12) {
                        int medicinesEnd = json.indexOf("\",\"tests\":", medicinesStart);
                        if (medicinesEnd == -1) {
                            medicinesEnd = json.indexOf("\",\"nextVisitDays\":", medicinesStart);
                        }
                        if (medicinesEnd == -1) {
                            medicinesEnd = json.indexOf("\"}", medicinesStart);
                        }

                        String medicines = "";
                        if (medicinesEnd > medicinesStart) {
                            medicines = json.substring(medicinesStart, medicinesEnd);
                            medicines = medicines.replace("\\\"", "\"");
                        }

                        if (medicines == null || medicines.trim().isEmpty() || medicines.equals("\"\"")) {
                            medicines = "[]";
                        }

                        consultation.setMedicines(medicines);
                    } else {
                        consultation.setMedicines("[]");
                    }

                    // Extract tests
                    int testsStart = json.indexOf("\"tests\":\"") + 9;
                    if (testsStart > 8) {
                        int testsEnd = json.indexOf("\"", testsStart);
                        if (testsEnd > testsStart) {
                            String tests = json.substring(testsStart, testsEnd);
                            consultation.setTests(tests);
                        }
                    }

                    // Extract nextVisitDays
                    int nextVisitStart = json.indexOf("\"nextVisitDays\":\"") + 17;
                    if (nextVisitStart > 16) {
                        int nextVisitEnd = json.indexOf("\"", nextVisitStart);
                        if (nextVisitEnd > nextVisitStart) {
                            String nextVisit = json.substring(nextVisitStart, nextVisitEnd);
                            consultation.setNextVisitDays(nextVisit);
                        }
                    }

                } catch (Exception e) {
                    System.err.println("Error in manual JSON parsing: " + e.getMessage());
                }

                return consultation;
            }

            private String getPatientIssue(String patientId) {
                String sql = "SELECT issue FROM patients WHERE patient_id = ?";
                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, patientId);
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) {
                        return rs.getString("issue");
                    }
                } catch (SQLException e) {
                    System.err.println("Error getting patient issue: " + e.getMessage());
                }
                return "";
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
            System.out.println("🏥 Starting Doctor Consultation System...");

            DatabaseService.initializeDatabase();

            HttpServer server = HttpServer.create(new InetSocketAddress(5001), 0);

            server.createContext("/", new WebHandlers.DoctorHomeHandler());
            server.createContext("/api/pending-patients", new WebHandlers.PendingPatientsHandler());
            server.createContext("/api/next-consultation-token", new WebHandlers.NextConsultationTokenHandler());
            server.createContext("/api/consultation-history", new WebHandlers.ConsultationHistoryHandler());
            server.createContext("/api/save-consultation", new WebHandlers.SaveConsultationHandler());

            server.setExecutor(Executors.newFixedThreadPool(10));
            server.start();

            System.out.println("✅ Doctor Consultation System started successfully!");
            System.out.println("🌐 Server running at: http://localhost:5001");
            System.out.println("📋 Access doctor panel at: http://localhost:5001");
            System.out.println("💾 Database: " + DB_URL);
            System.out.println("🔄 Server is ready to handle consultations...");
            System.out.println("🔗 Registration sync endpoint: /api/next-consultation-token");
            System.out.println("\n⚠  To stop server: Press Ctrl+C");

        } catch (Exception e) {
            System.err.println("❌ Failed to start Doctor Consultation System: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    static String getDoctorPageHTML() {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Doctor Consultation System</title>
                    <link href="https://cdnjs.cloudflare.com/ajax/libs/bootstrap/5.3.0/css/bootstrap.min.css" rel="stylesheet">
                    <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
                    <style>
                        :root {
                            --primary-color: #059669;
                            --secondary-color: #0891b2;
                            --success-color: #10b981;
                            --danger-color: #ef4444;
                            --dark-color: #1f2937;
                            --consultation-color: #059669;
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
                        }
                
                        .header {
                            background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
                            color: white;
                            padding: 30px;
                            text-align: center;
                            position: relative;
                        }
                
                        .consultation-token-display {
                            position: absolute;
                            top: 15px;
                            right: 15px;
                            background: rgba(255, 255, 255, 0.2);
                            backdrop-filter: blur(10px);
                            color: white;
                            padding: 10px 20px;
                            border-radius: 25px;
                            font-weight: 700;
                            font-size: 1rem;
                            border: 2px solid rgba(255, 255, 255, 0.3);
                        }
                
                        .consultation-section {
                            background: white;
                            border-radius: 15px;
                            padding: 30px;
                            margin: 20px;
                            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.05);
                        }
                
                        .patient-card {
                            border: 2px solid #e2e8f0;
                            border-radius: 15px;
                            padding: 20px;
                            margin: 10px 0;
                            cursor: pointer;
                            transition: all 0.3s ease;
                            position: relative;
                        }
                
                        .patient-card:hover {
                            border-color: var(--primary-color);
                            box-shadow: 0 5px 15px rgba(5, 150, 105, 0.2);
                        }
                
                        .patient-card.selected {
                            border-color: var(--primary-color);
                            background: #f0fdf4;
                        }
                
                        .patient-card.next-consultation {
                            border-color: var(--consultation-color);
                            background: linear-gradient(135deg, #f0fdf4, #ecfdf5);
                            box-shadow: 0 5px 20px rgba(5, 150, 105, 0.3);
                        }
                
                        .patient-card.next-consultation::after {
                            content: "NEXT";
                            position: absolute;
                            top: -10px;
                            right: -10px;
                            background: var(--consultation-color);
                            color: white;
                            padding: 5px 12px;
                            border-radius: 15px;
                            font-size: 0.75rem;
                            font-weight: 700;
                            animation: pulse 2s infinite;
                        }
                
                        @keyframes pulse {
                            0% { transform: scale(1); }
                            50% { transform: scale(1.05); }
                            100% { transform: scale(1); }
                        }
                
                        .form-control, .form-select {
                            border: 2px solid #e2e8f0;
                            border-radius: 10px;
                            padding: 12px 16px;
                            transition: all 0.3s ease;
                        }
                
                        .form-control:focus, .form-select:focus {
                            border-color: var(--primary-color);
                            box-shadow: 0 0 0 3px rgba(5, 150, 105, 0.1);
                        }
                
                        .btn {
                            border-radius: 10px;
                            padding: 12px 30px;
                            font-weight: 600;
                            transition: all 0.3s ease;
                        }
                
                        .btn-primary {
                            background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
                            border: none;
                        }
                
                        .medicine-item {
                            background: #f8fafc;
                            border: 1px solid #e2e8f0;
                            border-radius: 10px;
                            padding: 15px;
                            margin: 10px 0;
                        }
                
                        .section-title {
                            font-size: 1.5rem;
                            font-weight: 700;
                            color: var(--dark-color);
                            margin-bottom: 20px;
                            padding-bottom: 10px;
                            border-bottom: 3px solid var(--primary-color);
                        }
                
                        .status-indicator {
                            position: fixed;
                            top: 20px;
                            left: 20px;
                            background: var(--primary-color);
                            color: white;
                            padding: 10px 20px;
                            border-radius: 50px;
                            font-weight: 600;
                            box-shadow: 0 5px 15px rgba(0, 0, 0, 0.2);
                            z-index: 1000;
                        }
                
                        .timing-options {
                            margin-top: 10px;
                            padding: 10px;
                            border-left: 3px solid var(--primary-color);
                            background: #f0fdf4;
                            border-radius: 5px;
                        }
                
                        .timing-section {
                            margin-bottom: 15px;
                        }
                
                        .timing-section h6 {
                            color: var(--primary-color);
                            margin-bottom: 8px;
                            font-weight: 600;
                        }
                
                        .timing-option {
                            display: inline-block;
                            margin-right: 20px;
                            margin-bottom: 8px;
                        }
                
                        .debug-info {
                            background: #f8f9fa;
                            border: 1px solid #dee2e6;
                            border-radius: 8px;
                            padding: 15px;
                            margin: 10px 0;
                            font-family: monospace;
                            font-size: 12px;
                            max-height: 200px;
                            overflow-y: auto;
                        }
                
                        .sync-status {
                            background: rgba(16, 185, 129, 0.1);
                            border: 1px solid var(--success-color);
                            border-radius: 10px;
                            padding: 10px;
                            margin: 10px 0;
                            font-size: 0.9rem;
                        }
                    </style>
                </head>
                <body>
                    <div class="status-indicator">
                        <i class="fas fa-stethoscope text-white me-2"></i>Doctor Panel
                    </div>
                
                    <div class="container-fluid">
                        <div class="main-container">
                            <div class="header">
                                <h1><i class="fas fa-user-md me-3"></i>Doctor Consultation System</h1>
                                <p class="mb-0 fs-5">Patient Management & Consultation - localhost:5001</p>
                                <div class="consultation-token-display" id="consultationTokenDisplay">
                                    <i class="fas fa-stethoscope me-2"></i>Next: <span id="nextConsultationToken">Loading...</span>
                                </div>
                            </div>
                
                            <div class="row">
                                <div class="col-lg-4">
                                    <div class="consultation-section">
                                        <h3 class="section-title"><i class="fas fa-users me-2"></i>Pending Patients</h3>
                                        <div class="sync-status">
                                            <i class="fas fa-sync me-2"></i>Synced with Registration System - Auto-refresh every 10s
                                        </div>
                                        <div id="patientsList"></div>
                                    </div>
                                </div>
                
                                <div class="col-lg-8">
                                    <div class="consultation-section">
                                        <h3 class="section-title"><i class="fas fa-clipboard-list me-2"></i>Consultation</h3>
                
                                        <div id="alertContainer"></div>
                
                                        <div id="selectedPatientDetails" style="display: none;">
                                            <div class="card mb-4">
                                                <div class="card-body">
                                                    <h5 class="card-title"><i class="fas fa-user me-2"></i>Patient Details</h5>
                                                    <div id="patientInfo"></div>
                                                </div>
                                            </div>
                                        </div>
                
                                        <div id="consultationForm" style="display: none;">
                                            <form id="doctorForm">
                                                <input type="hidden" id="selectedPatientId">
                
                                                <div class="mb-4">
                                                    <h5><i class="fas fa-pills me-2"></i>Medicines</h5>
                                                    <div id="medicinesSection"></div>
                                                </div>
                
                                                <div class="mb-4">
                                                    <h5><i class="fas fa-vial me-2"></i>Tests</h5>
                                                    <div id="testsSection">
                                                        <div class="form-check mb-2">
                                                            <input class="form-check-input" type="checkbox" value="Blood Test" id="test1">
                                                            <label class="form-check-label" for="test1">Blood Test</label>
                                                        </div>
                                                        <div class="form-check mb-2">
                                                            <input class="form-check-input" type="checkbox" value="X-Ray" id="test2">
                                                            <label class="form-check-label" for="test2">X-Ray</label>
                                                        </div>
                                                        <div class="form-check mb-2">
                                                            <input class="form-check-input" type="checkbox" value="ECG" id="test3">
                                                            <label class="form-check-label" for="test3">ECG</label>
                                                        </div>
                                                        <div class="form-check mb-2">
                                                            <input class="form-check-input" type="checkbox" value="Urine Test" id="test4">
                                                            <label class="form-check-label" for="test4">Urine Test</label>
                                                        </div>
                                                        <div class="form-check mb-2">
                                                            <input class="form-check-input" type="checkbox" value="CT Scan" id="test5">
                                                            <label class="form-check-label" for="test5">CT Scan</label>
                                                        </div>
                                                        <div class="form-check mb-2">
                                                            <input class="form-check-input" type="checkbox" value="MRI Scan" id="test6">
                                                            <label class="form-check-label" for="test6">MRI Scan</label>
                                                        </div>
                                                        <div class="form-check mb-2">
                                                            <input class="form-check-input" type="checkbox" value="Ultrasound" id="test7">
                                                            <label class="form-check-label" for="test7">Ultrasound</label>
                                                        </div>
                                                    </div>
                                                </div>
                
                                                <div class="mb-4">
                                                    <label class="form-label"><i class="fas fa-calendar-plus me-2"></i>Next Visit (Days)</label>
                                                    <input type="number" class="form-control" id="nextVisitDays" min="1" max="365" placeholder="Enter days for next visit">
                                                </div>
                
                                                <div class="d-grid">
                                                    <button type="submit" class="btn btn-primary btn-lg" id="saveConsultationBtn">
                                                        <i class="fas fa-check-circle me-2"></i>Complete Consultation
                                                    </button>
                                                </div>
                                            </form>
                                        </div>
                
                                        <div id="patientHistory" class="mt-4" style="display: none;">
                                            <h5><i class="fas fa-history me-2"></i>Consultation History</h5>
                                            <div id="historyContent"></div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                
                    <script src="https://cdnjs.cloudflare.com/ajax/libs/bootstrap/5.3.0/js/bootstrap.bundle.min.js"></script>
                    <script>
                        class DoctorApp {
                            constructor() {
                                this.selectedPatient = null;
                                this.currentPendingPatients = [];
                                this.defaultMedicines = [
                                    { name: 'Paracetamol 500mg', id: 'med1' },
                                    { name: 'Amoxicillin 250mg', id: 'med2' },
                                    { name: 'Ibuprofen 400mg', id: 'med3' },
                                    { name: 'Crocin 650mg', id: 'med4' },
                                    { name: 'Azithromycin 500mg', id: 'med5' },
                                    { name: 'Cetirizine 10mg', id: 'med6' },
                                    { name: 'Omeprazole 20mg', id: 'med7' },
                                    { name: 'Metformin 500mg', id: 'med8' },
                                    { name: 'Aspirin 75mg', id: 'med9' },
                                    { name: 'Dolo 650mg', id: 'med10' }
                                ];
                                this.initializeApp();
                            }
                
                            initializeApp() {
                                this.loadPendingPatients();
                                this.setupMedicinesSection();
                                this.attachEventListeners();
                                // Auto-refresh every 10 seconds
                                setInterval(() => {
                                    this.loadPendingPatients();
                                    this.updateConsultationToken();
                                }, 10000);
                                this.updateConsultationToken();
                                console.log('Doctor Consultation System initialized with Registration Sync');
                            }
                
                            attachEventListeners() {
                                document.getElementById('doctorForm').addEventListener('submit', (e) => {
                                    e.preventDefault();
                                    this.saveConsultation();
                                });
                            }
                
                            async updateConsultationToken() {
                                try {
                                    const response = await fetch('/api/next-consultation-token');
                                    if (response.ok) {
                                        const data = await response.json();
                                        const tokenSpan = document.getElementById('nextConsultationToken');
                
                                        if (data.nextConsultationToken !== null) {
                                            tokenSpan.textContent = `Token #${data.nextConsultationToken}`;
                                            tokenSpan.parentElement.style.background = 'rgba(5, 150, 105, 0.2)';
                                            tokenSpan.parentElement.style.border = '2px solid rgba(5, 150, 105, 0.5)';
                                        } else {
                                            tokenSpan.textContent = 'No Pending';
                                            tokenSpan.parentElement.style.background = 'rgba(107, 114, 128, 0.2)';
                                            tokenSpan.parentElement.style.border = '2px solid rgba(107, 114, 128, 0.3)';
                                        }
                                    }
                                } catch (error) {
                                    console.error('Error updating consultation token:', error);
                                    document.getElementById('nextConsultationToken').textContent = 'Error';
                                }
                            }
                
                            async loadPendingPatients() {
                                try {
                                    const response = await fetch('/api/pending-patients');
                                    if (!response.ok) {
                                        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
                                    }
                
                                    const patients = await response.json();
                                    this.currentPendingPatients = patients;
                                    const patientsList = document.getElementById('patientsList');
                
                                    if (patients.length === 0) {
                                        patientsList.innerHTML = `
                                            <div class="text-center text-muted py-4">
                                                <i class="fas fa-user-clock fa-3x mb-3"></i>
                                                <p>No pending patients</p>
                                                <small>Waiting for new registrations...</small>
                                            </div>
                                        `;
                                        return;
                                    }
                
                                    // Find the next consultation patient (lowest token number)
                                    const nextPatient = patients.length > 0 ? patients[0] : null;
                
                                    patientsList.innerHTML = patients.map((patient, index) => {
                                        const isNext = nextPatient && patient.token === nextPatient.token;
                                        const cardClass = isNext ? 'patient-card next-consultation' : 'patient-card';
                
                                        return `
                                            <div class="${cardClass}" data-patient-id="${patient.patientId}" onclick="doctorApp.selectPatient('${patient.patientId}')">
                                                <div class="d-flex justify-content-between align-items-center">
                                                    <div>
                                                        <h6 class="mb-1"><i class="fas fa-user me-2"></i>${patient.name}</h6>
                                                        <small class="text-muted">ID: ${patient.patientId}</small>
                                                        <div class="mt-2">
                                                            <span class="badge ${isNext ? 'bg-success' : 'bg-primary'}">Token #${patient.token}</span>
                                                            <span class="badge bg-secondary">${patient.age}Y ${patient.gender}</span>
                                                            ${isNext ? '<span class="badge bg-warning text-dark">NEXT</span>' : ''}
                                                        </div>
                                                    </div>
                                                    <div class="text-end">
                                                        <i class="fas fa-chevron-right text-muted"></i>
                                                    </div>
                                                </div>
                                                <div class="mt-2">
                                                    <small><i class="fas fa-map-marker-alt me-1"></i>${patient.location}</small>
                                                </div>
                                                <div class="mt-2">
                                                    <small class="text-primary"><i class="fas fa-notes-medical me-1"></i>${patient.issue}</small>
                                                </div>
                                            </div>
                                        `;
                                    }).join('');
                
                                    console.log(`Loaded ${patients.length} pending patients. Next: ${nextPatient ? nextPatient.token : 'None'}`);
                
                                } catch (error) {
                                    console.error('Error loading patients:', error);
                                }
                            }
                
                            selectPatient(patientId) {
                                document.querySelectorAll('.patient-card').forEach(card => {
                                    card.classList.remove('selected');
                                });
                                const selectedCard = document.querySelector(`[data-patient-id="${patientId}"]`);
                                if (selectedCard) {
                                    selectedCard.classList.add('selected');
                                }
                
                                this.resetForm();
                                this.showPatientDetails(patientId);
                                document.getElementById('selectedPatientId').value = patientId;
                                document.getElementById('consultationForm').style.display = 'block';
                                this.loadPatientHistory(patientId);
                                this.selectedPatient = patientId;
                                console.log('Selected patient: ' + patientId);
                            }
                
                            async showPatientDetails(patientId) {
                                try {
                                    const patient = this.currentPendingPatients.find(p => p.patientId === patientId);
                
                                    if (patient) {
                                        document.getElementById('patientInfo').innerHTML = `
                                            <div class="row">
                                                <div class="col-md-6">
                                                    <p><strong>Name:</strong> ${patient.name}</p>
                                                    <p><strong>Age:</strong> ${patient.age} years</p>
                                                    <p><strong>Gender:</strong> ${patient.gender}</p>
                                                </div>
                                                <div class="col-md-6">
                                                    <p><strong>Phone:</strong> ${patient.phoneNumber}</p>
                                                    <p><strong>Location:</strong> ${patient.location}</p>
                                                    <p><strong>Token:</strong> #${patient.token}</p>
                                                </div>
                                                <div class="col-12">
                                                    <p><strong>Issue:</strong> ${patient.issue}</p>
                                                </div>
                                            </div>
                                        `;
                                        document.getElementById('selectedPatientDetails').style.display = 'block';
                                    }
                                } catch (error) {
                                    console.error('Error loading patient details:', error);
                                }
                            }
                
                            setupMedicinesSection() {
                                const medicinesSection = document.getElementById('medicinesSection');
                                medicinesSection.innerHTML = this.defaultMedicines.map(medicine => `
                                    <div class="medicine-item">
                                        <div class="form-check">
                                            <input class="form-check-input medicine-checkbox" type="checkbox" value="${medicine.name}" id="${medicine.id}">
                                            <label class="form-check-label" for="${medicine.id}">
                                                <strong>${medicine.name}</strong>
                                            </label>
                                        </div>
                                        <div class="timing-options" id="timing-${medicine.id}" style="display: none;">
                                            <div class="timing-section">
                                                <h6>Food Timing:</h6>
                                                <div class="timing-option">
                                                    <input class="form-check-input" type="radio" name="food-${medicine.id}" id="before-${medicine.id}" value="Before Food">
                                                    <label class="form-check-label" for="before-${medicine.id}">Before Food</label>
                                                </div>
                                                <div class="timing-option">
                                                    <input class="form-check-input" type="radio" name="food-${medicine.id}" id="after-${medicine.id}" value="After Food">
                                                    <label class="form-check-label" for="after-${medicine.id}">After Food</label>
                                                </div>
                                            </div>
                                            <div class="timing-section">
                                                <h6>Day Timing:</h6>
                                                <div class="timing-option">
                                                    <input class="form-check-input" type="checkbox" name="day-${medicine.id}" id="morning-${medicine.id}" value="Morning">
                                                    <label class="form-check-label" for="morning-${medicine.id}">Morning</label>
                                                </div>
                                                <div class="timing-option">
                                                    <input class="form-check-input" type="checkbox" name="day-${medicine.id}" id="afternoon-${medicine.id}" value="Afternoon">
                                                    <label class="form-check-label" for="afternoon-${medicine.id}">Afternoon</label>
                                                </div>
                                                <div class="timing-option">
                                                    <input class="form-check-input" type="checkbox" name="day-${medicine.id}" id="evening-${medicine.id}" value="Evening">
                                                    <label class="form-check-label" for="evening-${medicine.id}">Evening</label>
                                                </div>
                                                <div class="timing-option">
                                                    <input class="form-check-input" type="checkbox" name="day-${medicine.id}" id="night-${medicine.id}" value="Night">
                                                    <label class="form-check-label" for="night-${medicine.id}">Night</label>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                `).join('');
                
                                document.querySelectorAll('.medicine-checkbox').forEach(checkbox => {
                                    checkbox.addEventListener('change', (e) => {
                                        const timingDiv = document.getElementById(`timing-${e.target.id}`);
                                        timingDiv.style.display = e.target.checked ? 'block' : 'none';
                                        if (!e.target.checked) {
                                            timingDiv.querySelectorAll('input').forEach(cb => cb.checked = false);
                                        }
                                    });
                                });
                            }
                
                            async loadPatientHistory(patientId) {
                                try {
                                    const response = await fetch(`/api/consultation-history/${patientId}`);
                                    const history = await response.json();
                                    const historyContent = document.getElementById('historyContent');
                
                                    if (history.length === 0) {
                                        historyContent.innerHTML = `
                                            <div class="text-muted text-center py-3">
                                                <i class="fas fa-history me-2"></i>No previous consultations
                                            </div>
                                        `;
                                    } else {
                                        historyContent.innerHTML = history.map(consultation => `
                                            <div class="card mb-3">
                                                <div class="card-body">
                                                    <div class="d-flex justify-content-between">
                                                        <h6>Consultation #${consultation.id}</h6>
                                                        <small class="text-muted">${new Date(consultation.createdAt).toLocaleDateString()}</small>
                                                    </div>
                                                    <div class="row mt-2">
                                                        <div class="col-md-4">
                                                            <strong>Medicines:</strong>
                                                            <div class="mt-1">${this.formatMedicines(consultation.medicines)}</div>
                                                        </div>
                                                        <div class="col-md-4">
                                                            <strong>Tests:</strong>
                                                            <div class="mt-1">${consultation.tests || 'None'}</div>
                                                        </div>
                                                        <div class="col-md-4">
                                                            <strong>Next Visit:</strong>
                                                            <div class="mt-1">${consultation.nextVisitDays ? consultation.nextVisitDays + ' days' : 'Not specified'}</div>
                                                        </div>
                                                    </div>
                                                </div>
                                            </div>
                                        `).join('');
                                    }
                                    document.getElementById('patientHistory').style.display = 'block';
                                } catch (error) {
                                    console.error('Error loading patient history:', error);
                                }
                            }
                
                            formatMedicines(medicinesStr) {
                                try {
                                    if (!medicinesStr || medicinesStr === '[]') return 'None';
                                    const medicines = JSON.parse(medicinesStr);
                                    if (Array.isArray(medicines)) {
                                        return medicines.map(med => `
                                            <small class="d-block">• ${med.name} - ${med.timing || 'No timing specified'}</small>
                                        `).join('');
                                    } else {
                                        return medicinesStr;
                                    }
                                } catch (error) {
                                    return medicinesStr || 'None';
                                }
                            }
                
                            async saveConsultation() {
                                const patientId = document.getElementById('selectedPatientId').value;
                
                                if (!patientId) {
                                    this.showAlert('Please select a patient first', 'danger');
                                    return;
                                }
                
                                const saveBtn = document.getElementById('saveConsultationBtn');
                                saveBtn.disabled = true;
                                saveBtn.innerHTML = '<i class="fas fa-spinner fa-spin me-2"></i>Saving...';
                
                                try {
                                    const selectedMedicines = [];
                                    document.querySelectorAll('.medicine-checkbox:checked').forEach(checkbox => {
                                        const medicineId = checkbox.id;
                                        const medicineName = checkbox.value;
                                        const foodTiming = document.querySelector(`input[name="food-${medicineId}"]:checked`);
                                        const dayTimings = [];
                                        document.querySelectorAll(`input[name="day-${medicineId}"]:checked`).forEach(cb => {
                                            dayTimings.push(cb.value);
                                        });
                                        const allTimings = [];
                                        if (foodTiming) allTimings.push(foodTiming.value);
                                        allTimings.push(...dayTimings);
                                        const timingStr = allTimings.length > 0 ? allTimings.join(', ') : 'As needed';
                                        selectedMedicines.push({ name: medicineName, timing: timingStr });
                                    });
                
                                    const selectedTests = [];
                                    document.querySelectorAll('#testsSection input[type="checkbox"]:checked').forEach(checkbox => {
                                        selectedTests.push(checkbox.value);
                                    });
                
                                    const nextVisitDays = document.getElementById('nextVisitDays').value;
                
                                    if (selectedMedicines.length === 0 && selectedTests.length === 0) {
                                        this.showAlert('Please select at least one medicine or test', 'warning');
                                        return;
                                    }
                
                                    const consultationData = {
                                        patientId: patientId,
                                        medicines: selectedMedicines.length > 0 ? JSON.stringify(selectedMedicines) : "[]",
                                        tests: selectedTests.length > 0 ? selectedTests.join(', ') : "",
                                        nextVisitDays: nextVisitDays && nextVisitDays.trim() !== "" ? nextVisitDays : ""
                                    };
                
                                    const response = await fetch('/api/save-consultation', {
                                        method: 'POST',
                                        headers: { 
                                            'Content-Type': 'application/json',
                                            'Accept': 'application/json'
                                        },
                                        body: JSON.stringify(consultationData)
                                    });
                
                                    if (!response.ok) {
                                        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
                                    }
                
                                    const result = await response.json();
                
                                    if (result.success) {
                                        this.showAlert('Consultation completed successfully! Registration system will be updated automatically.', 'success');
                                        this.completeReset();
                                        // Immediately refresh both lists to sync with registration system
                                        this.loadPendingPatients();
                                        this.updateConsultationToken();
                                    } else {
                                        this.showAlert(result.message || 'Failed to save consultation', 'danger');
                                    }
                
                                } catch (error) {
                                    console.error('Save consultation error:', error);
                                    this.showAlert('Network error. Please try again: ' + error.message, 'danger');
                                } finally {
                                    saveBtn.disabled = false;
                                    saveBtn.innerHTML = '<i class="fas fa-check-circle me-2"></i>Complete Consultation';
                                }
                            }
                
                            resetForm() {
                                document.getElementById('doctorForm').reset();
                                document.querySelectorAll('.timing-options').forEach(div => {
                                    div.style.display = 'none';
                                    div.querySelectorAll('input').forEach(cb => cb.checked = false);
                                });
                                document.querySelectorAll('.medicine-checkbox').forEach(cb => cb.checked = false);
                                document.querySelectorAll('#testsSection input[type="checkbox"]').forEach(cb => cb.checked = false);
                                document.getElementById('nextVisitDays').value = '';
                            }
                
                            completeReset() {
                                this.resetForm();
                                document.getElementById('selectedPatientDetails').style.display = 'none';
                                document.getElementById('consultationForm').style.display = 'none';
                                document.getElementById('patientHistory').style.display = 'none';
                                document.querySelectorAll('.patient-card').forEach(card => card.classList.remove('selected'));
                                this.selectedPatient = null;
                            }
                
                            showAlert(message, type) {
                                const alertDiv = document.createElement('div');
                                alertDiv.className = `alert alert-${type} alert-dismissible fade show`;
                                alertDiv.innerHTML = `
                                    <i class="fas ${type === 'success' ? 'fa-check-circle' : 
                                                   type === 'danger' ? 'fa-exclamation-triangle' : 
                                                   'fa-info-circle'} me-2"></i>
                                    ${message}
                                    <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
                                `;
                                document.getElementById('alertContainer').appendChild(alertDiv);
                                setTimeout(() => { if (alertDiv.parentElement) alertDiv.remove(); }, 5000);
                            }
                        }
                
                        let doctorApp;
                        document.addEventListener('DOMContentLoaded', () => {
                            doctorApp = new DoctorApp();
                            console.log('Doctor Consultation System with Registration Sync loaded successfully!');
                        });
                    </script>
                </body>
                </html>
                """;
    }
}