// Complete Fixed Doctor Consultation System - Working Medicine & Tests Modal Version
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
                    return nextToken;
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
                
                        .sync-status {
                            background: rgba(16, 185, 129, 0.1);
                            border: 1px solid var(--success-color);
                            border-radius: 10px;
                            padding: 10px;
                            margin: 10px 0;
                            font-size: 0.9rem;
                        }
                
                        .medicine-list-item, .test-list-item {
                            cursor: pointer;
                            transition: all 0.3s ease;
                        }
                
                        .medicine-list-item:hover, .test-list-item:hover {
                            background-color: #f8fafc;
                        }
                
                        .modal-content {
                            border-radius: 15px;
                            border: none;
                            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.2);
                        }
                
                        .modal-header {
                            background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
                            color: white;
                            border-radius: 15px 15px 0 0;
                        }
                
                        .selected-medicines-display, .selected-tests-display {
                            max-height: 400px;
                            overflow-y: auto;
                        }
                
                        .form-check-input:checked {
                            background-color: var(--primary-color);
                            border-color: var(--primary-color);
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
                                                    <div class="d-flex align-items-center gap-3 mb-3">
                                                        <button type="button" class="btn btn-outline-primary" id="addMedicineBtn">
                                                            <i class="fas fa-plus me-2"></i>Add Medicine
                                                        </button>
                                                        <span class="text-muted">Click to add medicines from list or enter custom</span>
                                                    </div>
                                                    <div id="selectedMedicinesContainer"></div>
                                                </div>
                
                                                <div class="mb-4">
                                                    <h5><i class="fas fa-vial me-2"></i>Tests</h5>
                                                    <div class="d-flex align-items-center gap-3 mb-3">
                                                        <button type="button" class="btn btn-outline-primary" id="addTestBtn">
                                                            <i class="fas fa-plus me-2"></i>Add Test
                                                        </button>
                                                        <span class="text-muted">Click to add tests from list or enter custom</span>
                                                    </div>
                                                    <div id="selectedTestsContainer"></div>
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
                
                    <!-- Medicine Selection Modal -->
                    <div class="modal fade" id="medicineModal" tabindex="-1">
                        <div class="modal-dialog modal-lg">
                            <div class="modal-content">
                                <div class="modal-header">
                                    <h5 class="modal-title"><i class="fas fa-pills me-2"></i>Select Medicines</h5>
                                    <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                                </div>
                                <div class="modal-body">
                                    <!-- Search Bar -->
                                    <div class="mb-3">
                                        <input type="text" class="form-control" id="medicineSearch" placeholder="Search medicines...">
                                    </div>
                                    
                                    <!-- Medicine List -->
                                    <div class="row mb-4">
                                        <div class="col-12">
                                            <h6>Available Medicines:</h6>
                                            <div id="medicineList" class="border rounded p-3" style="max-height: 300px; overflow-y: auto;">
                                                <!-- Medicine items will be populated here -->
                                            </div>
                                        </div>
                                    </div>
                                    
                                    <!-- Custom Medicine Input -->
                                    <div class="mb-3">
                                        <h6>Add Custom Medicine:</h6>
                                        <div class="d-flex gap-2">
                                            <input type="text" class="form-control" id="customMedicine" placeholder="Enter medicine name...">
                                            <button type="button" class="btn btn-outline-primary" id="addCustomMedicineBtn">
                                                <i class="fas fa-plus"></i>
                                            </button>
                                        </div>
                                    </div>
                                    
                                    <!-- Selected Medicines Preview -->
                                    <div id="modalSelectedMedicines">
                                        <h6>Selected Medicines:</h6>
                                        <div id="modalMedicinesList" class="selected-medicines-display"></div>
                                    </div>
                                </div>
                                <div class="modal-footer">
                                    <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                                    <button type="button" class="btn btn-primary" id="confirmMedicinesBtn">Confirm Selection</button>
                                </div>
                            </div>
                        </div>
                    </div>
                
                    <!-- Tests Selection Modal -->
                    <div class="modal fade" id="testsModal" tabindex="-1">
                        <div class="modal-dialog modal-lg">
                            <div class="modal-content">
                                <div class="modal-header">
                                    <h5 class="modal-title"><i class="fas fa-vial me-2"></i>Select Tests</h5>
                                    <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                                </div>
                                <div class="modal-body">
                                    <!-- Search Bar -->
                                    <div class="mb-3">
                                        <input type="text" class="form-control" id="testSearch" placeholder="Search tests...">
                                    </div>
                                    
                                    <!-- Test List -->
                                    <div class="row mb-4">
                                        <div class="col-12">
                                            <h6>Available Tests:</h6>
                                            <div id="testList" class="border rounded p-3" style="max-height: 300px; overflow-y: auto;">
                                                <!-- Test items will be populated here -->
                                            </div>
                                        </div>
                                    </div>
                                    
                                    <!-- Custom Test Input -->
                                    <div class="mb-3">
                                        <h6>Add Custom Test:</h6>
                                        <div class="d-flex gap-2">
                                            <input type="text" class="form-control" id="customTest" placeholder="Enter test name...">
                                            <button type="button" class="btn btn-outline-primary" id="addCustomTestBtn">
                                                <i class="fas fa-plus"></i>
                                            </button>
                                        </div>
                                    </div>
                                    
                                    <!-- Selected Tests Preview -->
                                    <div id="modalSelectedTests">
                                        <h6>Selected Tests:</h6>
                                        <div id="modalTestsList" class="selected-tests-display"></div>
                                    </div>
                                </div>
                                <div class="modal-footer">
                                    <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                                    <button type="button" class="btn btn-primary" id="confirmTestsBtn">Confirm Selection</button>
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
                                this.selectedMedicines = new Map(); // Store medicines with their timings
                                this.tempSelectedMedicines = new Map(); // Temporary storage for modal
                                this.selectedTests = new Set(); // Store selected tests
                                this.tempSelectedTests = new Set(); // Temporary storage for tests modal
                                
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
                                    { name: 'Dolo 650mg', id: 'med10' },
                                    { name: 'Combiflam', id: 'med11' },
                                    { name: 'Disprin', id: 'med12' },
                                    { name: 'Digene', id: 'med13' },
                                    { name: 'Pantop 40mg', id: 'med14' },
                                    { name: 'Augmentin 625mg', id: 'med15' }
                                ];
                                
                                this.defaultTests = [
                                    { name: 'Blood Test', id: 'test1' },
                                    { name: 'X-Ray', id: 'test2' },
                                    { name: 'ECG', id: 'test3' },
                                    { name: 'Urine Test', id: 'test4' },
                                    { name: 'CT Scan', id: 'test5' },
                                    { name: 'MRI Scan', id: 'test6' },
                                    { name: 'Ultrasound', id: 'test7' },
                                    { name: 'Blood Sugar Test', id: 'test8' },
                                    { name: 'Thyroid Function Test', id: 'test9' },
                                    { name: 'Liver Function Test', id: 'test10' },
                                    { name: 'Kidney Function Test', id: 'test11' },
                                    { name: 'Lipid Profile', id: 'test12' },
                                    { name: 'Hemoglobin Test', id: 'test13' },
                                    { name: 'Chest X-Ray', id: 'test14' },
                                    { name: 'Stress Test', id: 'test15' }
                                ];
                                
                                this.medicineModal = null;
                                this.testsModal = null;
                                this.initializeApp();
                            }
                
                            initializeApp() {
                                this.loadPendingPatients();
                                this.setupMedicineModal();
                                this.setupTestsModal();
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
                
                                // Medicine modal event listeners
                                document.getElementById('addMedicineBtn').addEventListener('click', () => {
                                    this.openMedicineModal();
                                });
                
                                document.getElementById('medicineSearch').addEventListener('input', (e) => {
                                    this.filterMedicines(e.target.value);
                                });
                
                                document.getElementById('addCustomMedicineBtn').addEventListener('click', () => {
                                    this.addCustomMedicine();
                                });
                
                                document.getElementById('customMedicine').addEventListener('keypress', (e) => {
                                    if (e.key === 'Enter') {
                                        this.addCustomMedicine();
                                    }
                                });
                
                                document.getElementById('confirmMedicinesBtn').addEventListener('click', () => {
                                    this.confirmMedicineSelection();
                                });
                                
                                // Tests modal event listeners
                                document.getElementById('addTestBtn').addEventListener('click', () => {
                                    this.openTestsModal();
                                });
                
                                document.getElementById('testSearch').addEventListener('input', (e) => {
                                    this.filterTests(e.target.value);
                                });
                
                                document.getElementById('addCustomTestBtn').addEventListener('click', () => {
                                    this.addCustomTest();
                                });
                
                                document.getElementById('customTest').addEventListener('keypress', (e) => {
                                    if (e.key === 'Enter') {
                                        this.addCustomTest();
                                    }
                                });
                
                                document.getElementById('confirmTestsBtn').addEventListener('click', () => {
                                    this.confirmTestSelection();
                                });
                
                                // Initialize Bootstrap modals
                                this.medicineModal = new bootstrap.Modal(document.getElementById('medicineModal'));
                                this.testsModal = new bootstrap.Modal(document.getElementById('testsModal'));
                            }
                
                            setupMedicineModal() {
                                this.populateMedicineList();
                                this.updateSelectedMedicinesDisplay();
                            }
                            
                            setupTestsModal() {
                                this.populateTestList();
                                this.updateSelectedTestsDisplay();
                            }
                
                            populateMedicineList() {
                                const medicineList = document.getElementById('medicineList');
                                medicineList.innerHTML = this.defaultMedicines.map(medicine => `
                                    <div class="medicine-list-item p-2 border-bottom" data-medicine="${medicine.name}">
                                        <div class="form-check">
                                            <input class="form-check-input medicine-list-checkbox" type="checkbox" value="${medicine.name}" id="list-${medicine.id}">
                                            <label class="form-check-label fw-bold" for="list-${medicine.id}">
                                                ${medicine.name}
                                            </label>
                                        </div>
                                    </div>
                                `).join('');
                                
                                // Attach event listeners after creating the HTML
                                this.attachMedicineListEventListeners();
                            }
                            
                            populateTestList() {
                                const testList = document.getElementById('testList');
                                testList.innerHTML = this.defaultTests.map(test => `
                                    <div class="test-list-item p-2 border-bottom" data-test="${test.name}">
                                        <div class="form-check">
                                            <input class="form-check-input test-list-checkbox" type="checkbox" value="${test.name}" id="list-${test.id}">
                                            <label class="form-check-label fw-bold" for="list-${test.id}">
                                                ${test.name}
                                            </label>
                                        </div>
                                    </div>
                                `).join('');
                                
                                // Attach event listeners after creating the HTML
                                this.attachTestListEventListeners();
                            }
                            
                            attachMedicineListEventListeners() {
                                document.querySelectorAll('.medicine-list-checkbox').forEach(checkbox => {
                                    checkbox.addEventListener('change', (e) => {
                                        if (e.target.checked) {
                                            this.tempSelectedMedicines.set(e.target.value, {
                                                name: e.target.value,
                                                foodTiming: '',
                                                dayTimings: []
                                            });
                                        } else {
                                            this.tempSelectedMedicines.delete(e.target.value);
                                        }
                                        this.updateModalSelectedMedicines();
                                    });
                                });
                            }
                            
                            attachTestListEventListeners() {
                                document.querySelectorAll('.test-list-checkbox').forEach(checkbox => {
                                    checkbox.addEventListener('change', (e) => {
                                        if (e.target.checked) {
                                            this.tempSelectedTests.add(e.target.value);
                                        } else {
                                            this.tempSelectedTests.delete(e.target.value);
                                        }
                                        this.updateModalSelectedTests();
                                    });
                                });
                            }
                
                            filterMedicines(searchTerm) {
                                const items = document.querySelectorAll('.medicine-list-item');
                                items.forEach(item => {
                                    const medicineName = item.dataset.medicine.toLowerCase();
                                    if (medicineName.includes(searchTerm.toLowerCase())) {
                                        item.style.display = 'block';
                                    } else {
                                        item.style.display = 'none';
                                    }
                                });
                            }
                            
                            filterTests(searchTerm) {
                                const items = document.querySelectorAll('.test-list-item');
                                items.forEach(item => {
                                    const testName = item.dataset.test.toLowerCase();
                                    if (testName.includes(searchTerm.toLowerCase())) {
                                        item.style.display = 'block';
                                    } else {
                                        item.style.display = 'none';
                                    }
                                });
                            }
                
                            openMedicineModal() {
                                // Copy current selection to temp storage
                                this.tempSelectedMedicines = new Map(this.selectedMedicines);
                                
                                // Update modal checkboxes based on current selection
                                document.querySelectorAll('.medicine-list-checkbox').forEach(checkbox => {
                                    checkbox.checked = this.tempSelectedMedicines.has(checkbox.value);
                                });
                
                                // Clear search and custom input
                                document.getElementById('medicineSearch').value = '';
                                document.getElementById('customMedicine').value = '';
                                this.filterMedicines('');
                
                                this.updateModalSelectedMedicines();
                                this.medicineModal.show();
                            }
                            
                            openTestsModal() {
                                // Copy current selection to temp storage
                                this.tempSelectedTests = new Set(this.selectedTests);
                                
                                // Update modal checkboxes based on current selection
                                document.querySelectorAll('.test-list-checkbox').forEach(checkbox => {
                                    checkbox.checked = this.tempSelectedTests.has(checkbox.value);
                                });
                
                                // Clear search and custom input
                                document.getElementById('testSearch').value = '';
                                document.getElementById('customTest').value = '';
                                this.filterTests('');
                
                                this.updateModalSelectedTests();
                                this.testsModal.show();
                            }
                
                            addCustomMedicine() {
                                const customInput = document.getElementById('customMedicine');
                                const medicineName = customInput.value.trim();
                
                                if (medicineName && !this.tempSelectedMedicines.has(medicineName)) {
                                    this.tempSelectedMedicines.set(medicineName, {
                                        name: medicineName,
                                        foodTiming: '',
                                        dayTimings: []
                                    });
                                    customInput.value = '';
                                    this.updateModalSelectedMedicines();
                                }
                            }
                            
                            addCustomTest() {
                                const customInput = document.getElementById('customTest');
                                const testName = customInput.value.trim();
                
                                if (testName && !this.tempSelectedTests.has(testName)) {
                                    this.tempSelectedTests.add(testName);
                                    customInput.value = '';
                                    this.updateModalSelectedTests();
                                }
                            }
                
                            updateModalSelectedMedicines() {
                                const modalMedicinesList = document.getElementById('modalMedicinesList');
                                
                                if (this.tempSelectedMedicines.size === 0) {
                                    modalMedicinesList.innerHTML = '<p class="text-muted">No medicines selected</p>';
                                    return;
                                }
                
                                modalMedicinesList.innerHTML = Array.from(this.tempSelectedMedicines.entries()).map(([name, data]) => {
                                    const medicineId = name.replace(/[^a-zA-Z0-9]/g, '');
                                    return `
                                        <div class="card mb-2" data-medicine-name="${name}">
                                            <div class="card-body p-3">
                                                <div class="d-flex justify-content-between align-items-start">
                                                    <h6 class="mb-2">${name}</h6>
                                                    <button type="button" class="btn btn-sm btn-outline-danger" onclick="doctorApp.removeMedicineFromTemp('${name}')">
                                                        <i class="fas fa-times"></i>
                                                    </button>
                                                </div>
                                                
                                                <div class="row">
                                                    <div class="col-md-6">
                                                        <small class="fw-bold text-primary">Food Timing:</small>
                                                        <div class="mt-1">
                                                            <div class="form-check form-check-inline">
                                                                <input class="form-check-input food-timing-radio" type="radio" name="food-${medicineId}" id="before-${medicineId}" value="Before Food" 
                                                                    ${data.foodTiming === 'Before Food' ? 'checked' : ''}>
                                                                <label class="form-check-label small" for="before-${medicineId}">Before Food</label>
                                                            </div>
                                                            <div class="form-check form-check-inline">
                                                                <input class="form-check-input food-timing-radio" type="radio" name="food-${medicineId}" id="after-${medicineId}" value="After Food" 
                                                                    ${data.foodTiming === 'After Food' ? 'checked' : ''}>
                                                                <label class="form-check-label small" for="after-${medicineId}">After Food</label>
                                                            </div>
                                                        </div>
                                                    </div>
                                                    
                                                    <div class="col-md-6">
                                                        <small class="fw-bold text-primary">Day Timing:</small>
                                                        <div class="mt-1">
                                                            ${['Morning', 'Afternoon', 'Evening', 'Night'].map(time => `
                                                                <div class="form-check form-check-inline">
                                                                    <input class="form-check-input day-timing-checkbox" type="checkbox" id="${time}-${medicineId}" value="${time}" 
                                                                        ${data.dayTimings.includes(time) ? 'checked' : ''}>
                                                                    <label class="form-check-label small" for="${time}-${medicineId}">${time}</label>
                                                                </div>
                                                            `).join('')}
                                                        </div>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    `;
                                }).join('');
                                
                                // Attach event listeners to timing controls
                                this.attachTimingEventListeners();
                            }
                            
                            updateModalSelectedTests() {
                                const modalTestsList = document.getElementById('modalTestsList');
                                
                                if (this.tempSelectedTests.size === 0) {
                                    modalTestsList.innerHTML = '<p class="text-muted">No tests selected</p>';
                                    return;
                                }
                
                                modalTestsList.innerHTML = Array.from(this.tempSelectedTests).map(testName => `
                                    <div class="card mb-2">
                                        <div class="card-body p-3">
                                            <div class="d-flex justify-content-between align-items-center">
                                                <h6 class="mb-0">${testName}</h6>
                                                <button type="button" class="btn btn-sm btn-outline-danger" onclick="doctorApp.removeTestFromTemp('${testName}')">
                                                    <i class="fas fa-times"></i>
                                                </button>
                                            </div>
                                        </div>
                                    </div>
                                `).join('');
                            }
                            
                            attachTimingEventListeners() {
                                // Food timing radio buttons
                                document.querySelectorAll('.food-timing-radio').forEach(radio => {
                                    radio.addEventListener('change', (e) => {
                                        const medicineCard = e.target.closest('[data-medicine-name]');
                                        const medicineName = medicineCard.getAttribute('data-medicine-name');
                                        this.updateMedicineTiming(medicineName, 'food', e.target.value);
                                    });
                                });
                                
                                // Day timing checkboxes
                                document.querySelectorAll('.day-timing-checkbox').forEach(checkbox => {
                                    checkbox.addEventListener('change', (e) => {
                                        const medicineCard = e.target.closest('[data-medicine-name]');
                                        const medicineName = medicineCard.getAttribute('data-medicine-name');
                                        this.updateMedicineTiming(medicineName, 'day', e.target.value, e.target.checked);
                                    });
                                });
                            }
                
                            removeMedicineFromTemp(medicineName) {
                                this.tempSelectedMedicines.delete(medicineName);
                                // Uncheck if it's in the list
                                const checkbox = document.querySelector(`input[value="${medicineName}"]`);
                                if (checkbox) checkbox.checked = false;
                                this.updateModalSelectedMedicines();
                            }
                            
                            removeTestFromTemp(testName) {
                                this.tempSelectedTests.delete(testName);
                                // Uncheck if it's in the list
                                const checkbox = document.querySelector(`input[value="${testName}"]`);
                                if (checkbox) checkbox.checked = false;
                                this.updateModalSelectedTests();
                            }
                
                            updateMedicineTiming(medicineName, timingType, value, checked = true) {
                                if (this.tempSelectedMedicines.has(medicineName)) {
                                    const medicineData = this.tempSelectedMedicines.get(medicineName);
                                    
                                    if (timingType === 'food') {
                                        medicineData.foodTiming = value;
                                    } else if (timingType === 'day') {
                                        if (checked) {
                                            if (!medicineData.dayTimings.includes(value)) {
                                                medicineData.dayTimings.push(value);
                                            }
                                        } else {
                                            medicineData.dayTimings = medicineData.dayTimings.filter(t => t !== value);
                                        }
                                    }
                                    
                                    this.tempSelectedMedicines.set(medicineName, medicineData);
                                }
                            }
                
                            confirmMedicineSelection() {
                                // Copy temp selection to actual selection
                                this.selectedMedicines = new Map(this.tempSelectedMedicines);
                                this.updateSelectedMedicinesDisplay();
                                this.medicineModal.hide();
                            }
                            
                            confirmTestSelection() {
                                // Copy temp selection to actual selection
                                this.selectedTests = new Set(this.tempSelectedTests);
                                this.updateSelectedTestsDisplay();
                                this.testsModal.hide();
                            }
                
                            updateSelectedMedicinesDisplay() {
                                const container = document.getElementById('selectedMedicinesContainer');
                                
                                if (this.selectedMedicines.size === 0) {
                                    container.innerHTML = '<p class="text-muted">No medicines selected</p>';
                                    return;
                                }
                
                                container.innerHTML = `
                                    <div class="border rounded p-3">
                                        <div class="d-flex justify-content-between align-items-center mb-2">
                                            <small class="fw-bold text-primary">Selected Medicines (${this.selectedMedicines.size})</small>
                                            <button type="button" class="btn btn-sm btn-outline-primary" onclick="doctorApp.openMedicineModal()">
                                                <i class="fas fa-edit me-1"></i>Edit
                                            </button>
                                        </div>
                                        ${Array.from(this.selectedMedicines.entries()).map(([name, data]) => {
                                            const allTimings = [];
                                            if (data.foodTiming) allTimings.push(data.foodTiming);
                                            allTimings.push(...data.dayTimings);
                                            const timingStr = allTimings.length > 0 ? allTimings.join(', ') : 'As needed';
                                            
                                            return `
                                                <div class="d-flex justify-content-between align-items-center py-1 border-bottom">
                                                    <div>
                                                        <span class="fw-bold">${name}</span>
                                                        <br><small class="text-muted">${timingStr}</small>
                                                    </div>
                                                    <button type="button" class="btn btn-sm btn-outline-danger" onclick="doctorApp.removeMedicine('${name}')">
                                                        <i class="fas fa-times"></i>
                                                    </button>
                                                </div>
                                            `;
                                        }).join('')}
                                    </div>
                                `;
                            }
                            
                            updateSelectedTestsDisplay() {
                                const container = document.getElementById('selectedTestsContainer');
                                
                                if (this.selectedTests.size === 0) {
                                    container.innerHTML = '<p class="text-muted">No tests selected</p>';
                                    return;
                                }
                
                                container.innerHTML = `
                                    <div class="border rounded p-3">
                                        <div class="d-flex justify-content-between align-items-center mb-2">
                                            <small class="fw-bold text-primary">Selected Tests (${this.selectedTests.size})</small>
                                            <button type="button" class="btn btn-sm btn-outline-primary" onclick="doctorApp.openTestsModal()">
                                                <i class="fas fa-edit me-1"></i>Edit
                                            </button>
                                        </div>
                                        ${Array.from(this.selectedTests).map(testName => `
                                            <div class="d-flex justify-content-between align-items-center py-1 border-bottom">
                                                <span class="fw-bold">${testName}</span>
                                                <button type="button" class="btn btn-sm btn-outline-danger" onclick="doctorApp.removeTest('${testName}')">
                                                    <i class="fas fa-times"></i>
                                                </button>
                                            </div>
                                        `).join('')}
                                    </div>
                                `;
                            }
                
                            removeMedicine(medicineName) {
                                this.selectedMedicines.delete(medicineName);
                                this.updateSelectedMedicinesDisplay();
                            }
                            
                            removeTest(testName) {
                                this.selectedTests.delete(testName);
                                this.updateSelectedTestsDisplay();
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
                                    // Get selected medicines from the new system
                                    const selectedMedicines = [];
                                    this.selectedMedicines.forEach((data, name) => {
                                        const allTimings = [];
                                        if (data.foodTiming) allTimings.push(data.foodTiming);
                                        allTimings.push(...data.dayTimings);
                                        const timingStr = allTimings.length > 0 ? allTimings.join(', ') : 'As needed';
                                        selectedMedicines.push({ name: name, timing: timingStr });
                                    });
                
                                    // Get selected tests from the new system
                                    const selectedTests = Array.from(this.selectedTests);
                
                                    const nextVisitDays = document.getElementById('nextVisitDays').value;
                
                                    if (selectedMedicines.length === 0 && selectedTests.length === 0) {
                                        this.showAlert('Please select at least one medicine or test', 'warning');
                                        saveBtn.disabled = false;
                                        saveBtn.innerHTML = '<i class="fas fa-check-circle me-2"></i>Complete Consultation';
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
                                document.getElementById('nextVisitDays').value = '';
                                this.selectedMedicines.clear();
                                this.selectedTests.clear();
                                this.updateSelectedMedicinesDisplay();
                                this.updateSelectedTestsDisplay();
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
                
                        // Global function to expose app methods to onclick handlers
                        let doctorApp;
                        
                        document.addEventListener('DOMContentLoaded', () => {
                            doctorApp = new DoctorApp();
                            console.log('Doctor Consultation System with Medicine & Tests Modal loaded successfully!');
                        });
                    </script>
                </body>
                </html>
                """;
    }
}