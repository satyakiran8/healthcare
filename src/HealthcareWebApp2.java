// Healthcare Registration System - Fixed Version with Patient ID Reuse
// File: HealthcareWebApp.java

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
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;

public class HealthcareWebApp2 {

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

        public Patient() {}

        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPatientId() { return patientId; }
        public void setPatientId(String patientId) { this.patientId = patientId; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        public String getGender() { return gender; }
        public void setGender(String gender) { this.gender = gender; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public String getIssue() { return issue; }
        public void setIssue(String issue) { this.issue = issue; }
        public Integer getToken() { return token; }
        public void setToken(Integer token) { this.token = token; }
        public LocalDateTime getRegistrationTime() { return registrationTime; }
        public void setRegistrationTime(LocalDateTime registrationTime) { this.registrationTime = registrationTime; }
        public LocalDate getRegistrationDate() { return registrationDate; }
        public void setRegistrationDate(LocalDate registrationDate) { this.registrationDate = registrationDate; }

        public String toJson() {
            return String.format(
                    "{\"id\":%d,\"phoneNumber\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"patientId\":\"%s\"," +
                            "\"age\":%d,\"gender\":\"%s\",\"location\":\"%s\",\"issue\":\"%s\",\"token\":%d," +
                            "\"registrationTime\":\"%s\",\"registrationDate\":\"%s\"}",
                    id != null ? id : 0,
                    phoneNumber != null ? phoneNumber : "",
                    name != null ? name : "",
                    email != null ? email : "",
                    patientId != null ? patientId : "",
                    age != null ? age : 0,
                    gender != null ? gender : "",
                    location != null ? location : "",
                    issue != null ? issue.replace("\"", "\\\"") : "",
                    token != null ? token : 0,
                    registrationTime != null ? registrationTime.toString() : "",
                    registrationDate != null ? registrationDate.toString() : ""
            );
        }
    }

    static class DatabaseService {

        static String generatePatientId() {
            Random random = new Random();
            int randomNumber = 1000 + random.nextInt(9000);
            return "PAT" + randomNumber;
        }

        static void initializeDatabase() {
            try {
                Class.forName("org.postgresql.Driver");
                connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                createTable();
                System.out.println("Database connected successfully!");
            } catch (ClassNotFoundException e) {
                System.err.println("PostgreSQL JDBC driver not found!");
                System.err.println("Download: postgresql-42.7.3.jar");
                System.exit(1);
            } catch (SQLException e) {
                System.err.println("Database connection failed: " + e.getMessage());
                System.err.println("Check: PostgreSQL running on port 5433?");
                System.err.println("Database 'healthcare' exists?");
                System.exit(1);
            }
        }

        static void createTable() {
            try (Statement stmt = connection.createStatement()) {
                // Check if table exists first
                DatabaseMetaData dbmd = connection.getMetaData();
                ResultSet tables = dbmd.getTables(null, null, "patients", null);

                if (!tables.next()) {
                    // Table doesn't exist, create it
                    String createTableSQL = """
                        CREATE TABLE patients (
                            id BIGSERIAL PRIMARY KEY,
                            phone_number VARCHAR(10) NOT NULL,
                            name VARCHAR(100) NOT NULL,
                            email VARCHAR(100),
                            patient_id VARCHAR(20) NOT NULL,
                            age INTEGER NOT NULL CHECK (age >= 0 AND age <= 150),
                            gender VARCHAR(10) NOT NULL,
                            location VARCHAR(100) NOT NULL,
                            issue TEXT NOT NULL,
                            token INTEGER NOT NULL,
                            registration_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            registration_date DATE DEFAULT CURRENT_DATE
                        )
                    """;
                    stmt.execute(createTableSQL);
                    stmt.execute("CREATE INDEX idx_patients_phone_number ON patients(phone_number)");
                    stmt.execute("CREATE INDEX idx_patients_registration_date ON patients(registration_date)");
                    stmt.execute("CREATE INDEX idx_patients_phone_name ON patients(phone_number, name)");
                    System.out.println("Table created successfully with correct structure!");

                    // Verify table structure for new table
                    ResultSet rs = stmt.executeQuery(
                            "SELECT column_name, data_type FROM information_schema.columns " +
                                    "WHERE table_name = 'patients' ORDER BY ordinal_position"
                    );
                    System.out.println("Table columns:");
                    while (rs.next()) {
                        System.out.println("  " + rs.getString("column_name") + " - " + rs.getString("data_type"));
                    }
                } else {
                    System.out.println("Table 'patients' already exists. Preserving existing data.");

                    // Check existing records count
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as count FROM patients");
                    if (rs.next()) {
                        int count = rs.getInt("count");
                        System.out.println("Found " + count + " existing patient records.");

                        // Show current max token for today
                        ResultSet maxTokenRs = stmt.executeQuery(
                                "SELECT COALESCE(MAX(token), 0) as max_token FROM patients WHERE registration_date = CURRENT_DATE"
                        );
                        if (maxTokenRs.next()) {
                            int maxToken = maxTokenRs.getInt("max_token");
                            System.out.println("Current max token for today: " + maxToken);
                            System.out.println("Next token will be: " + (maxToken + 1));
                        }
                    }
                }
                tables.close();

            } catch (SQLException e) {
                System.err.println("Table creation/verification error: " + e.getMessage());
                e.printStackTrace();
            }
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

        // NEW METHOD: Get existing patient_id for returning patients
        static String getExistingPatientId(String phoneNumber, String name) {
            String sql = "SELECT patient_id FROM patients WHERE phone_number = ? AND LOWER(TRIM(name)) = LOWER(TRIM(?)) ORDER BY registration_time ASC LIMIT 1";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, phoneNumber);
                pstmt.setString(2, name);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    String existingPatientId = rs.getString("patient_id");
                    System.out.println("Found existing patient_id: " + existingPatientId + " for " + name + " (" + phoneNumber + ")");
                    return existingPatientId;
                }
            } catch (SQLException e) {
                System.err.println("Error checking existing patient_id: " + e.getMessage());
            }

            return null; // No existing patient found
        }

        static Integer getNextToken() {
            String sql = """
                SELECT CASE 
                    WHEN MAX(token) IS NULL THEN 1 
                    ELSE MAX(token) + 1 
                END as next_token 
                FROM patients 
                WHERE registration_date = CURRENT_DATE
            """;

            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery(sql);
                if (rs.next()) {
                    int nextToken = rs.getInt("next_token");
                    System.out.println("Generated next token: " + nextToken);
                    return nextToken;
                }
            } catch (SQLException e) {
                System.err.println("Error getting next token: " + e.getMessage());
            }
            System.out.println("Using fallback token: 1");
            return 1;
        }

        static boolean canRegisterToday(String phoneNumber, String name) {
            String sql = """
                SELECT registration_time FROM patients 
                WHERE phone_number = ? AND LOWER(TRIM(name)) = LOWER(TRIM(?)) AND registration_date = CURRENT_DATE 
                ORDER BY registration_time DESC LIMIT 1
            """;

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, phoneNumber);
                pstmt.setString(2, name);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    LocalDateTime lastRegTime = rs.getTimestamp("registration_time").toLocalDateTime();
                    LocalDateTime threeHoursAgo = LocalDateTime.now().minusHours(3);
                    boolean canRegister = lastRegTime.isBefore(threeHoursAgo);
                    System.out.println("Last registration: " + lastRegTime + ", Can register: " + canRegister);
                    return canRegister;
                }
            } catch (SQLException e) {
                System.err.println("Error checking registration eligibility: " + e.getMessage());
            }
            return true;
        }

        static boolean registerPatient(Patient patient) {
            // MODIFIED: Check for existing patient_id first
            String existingPatientId = getExistingPatientId(patient.getPhoneNumber(), patient.getName());
            if (existingPatientId != null) {
                patient.setPatientId(existingPatientId);
                System.out.println("Reusing existing patient_id: " + existingPatientId + " for returning patient: " + patient.getName());
            } else {
                // Generate new patient_id only for new patients
                String newPatientId;
                do {
                    newPatientId = generatePatientId();
                } while (isPatientIdExists(newPatientId)); // Ensure uniqueness

                patient.setPatientId(newPatientId);
                System.out.println("Generated new patient_id: " + newPatientId + " for new patient: " + patient.getName());
            }

            String sql = """
                INSERT INTO patients (phone_number, name, email, patient_id, age, gender, location, issue, token, registration_time, registration_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                pstmt.setTimestamp(10, Timestamp.valueOf(patient.getRegistrationTime()));
                pstmt.setDate(11, java.sql.Date.valueOf(patient.getRegistrationDate()));

                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {
                    ResultSet generatedKeys = pstmt.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        patient.setId(generatedKeys.getLong(1));
                    }
                    System.out.println("Patient registered successfully: " + patient.getName() +
                            " (ID: " + patient.getPatientId() + ", Token: " + patient.getToken() +
                            (existingPatientId != null ? " - RETURNING PATIENT)" : " - NEW PATIENT)"));
                    return true;
                }
            } catch (SQLException e) {
                System.err.println("Error registering patient: " + e.getMessage());
                System.err.println("SQL State: " + e.getSQLState());
                System.err.println("Error Code: " + e.getErrorCode());
            }
            return false;
        }

        // NEW METHOD: Check if patient_id already exists
        static boolean isPatientIdExists(String patientId) {
            String sql = "SELECT COUNT(*) as count FROM patients WHERE patient_id = ?";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, patientId);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    return rs.getInt("count") > 0;
                }
            } catch (SQLException e) {
                System.err.println("Error checking patient_id existence: " + e.getMessage());
            }

            return false;
        }
    }

    static class WebHandlers {

        static class HomeHandler implements HttpHandler {
            public void handle(HttpExchange exchange) throws IOException {
                String response = getMainPageHTML();
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes(StandardCharsets.UTF_8));
                os.close();
            }
        }

        static class HistoryHandler implements HttpHandler {
            public void handle(HttpExchange exchange) throws IOException {
                if ("GET".equals(exchange.getRequestMethod())) {
                    String path = exchange.getRequestURI().getPath();
                    String phoneNumber = path.substring(path.lastIndexOf("/") + 1);

                    List<Patient> history = DatabaseService.getPatientHistory(phoneNumber);
                    StringBuilder json = new StringBuilder("[");
                    for (int i = 0; i < history.size(); i++) {
                        json.append(history.get(i).toJson());
                        if (i < history.size() - 1) json.append(",");
                    }
                    json.append("]");

                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    exchange.sendResponseHeaders(200, json.toString().getBytes(StandardCharsets.UTF_8).length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                    os.close();
                }
            }
        }

        static class TokenHandler implements HttpHandler {
            public void handle(HttpExchange exchange) throws IOException {
                if ("GET".equals(exchange.getRequestMethod())) {
                    Integer nextToken = DatabaseService.getNextToken();
                    String response = String.format("{\"nextToken\": %d}", nextToken);

                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                    os.close();
                }
            }
        }

        static class RegisterHandler implements HttpHandler {
            public void handle(HttpExchange exchange) throws IOException {
                if ("POST".equals(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(isr);
                    String requestBody = br.lines().reduce("", (a, b) -> a + b);

                    System.out.println("Registration request received: " + requestBody);

                    try {
                        Patient patient = parsePatientFromJson(requestBody);

                        System.out.println("Processing patient: " + patient.getName() + " (" + patient.getPhoneNumber() + ")");

                        String validationError = validatePatient(patient);
                        if (validationError != null) {
                            String errorResponse = String.format("{\"success\": false, \"message\": \"%s\"}", validationError);
                            sendJsonResponse(exchange, 400, errorResponse);
                            return;
                        }

                        if (!DatabaseService.canRegisterToday(patient.getPhoneNumber(), patient.getName())) {
                            String errorResponse = "{\"success\": false, \"message\": \"Patient has already registered within the last 3 hours.\"}";
                            sendJsonResponse(exchange, 400, errorResponse);
                            return;
                        }

                        patient.setToken(DatabaseService.getNextToken());
                        patient.setRegistrationTime(LocalDateTime.now());
                        patient.setRegistrationDate(LocalDate.now());

                        if (DatabaseService.registerPatient(patient)) {
                            String existingPatientId = DatabaseService.getExistingPatientId(patient.getPhoneNumber(), patient.getName());
                            boolean isReturningPatient = existingPatientId != null;

                            String successMessage = isReturningPatient ?
                                    "Welcome back! Registered with existing Patient ID." :
                                    "New patient registered successfully!";

                            String successResponse = String.format(
                                    "{\"success\": true, \"message\": \"%s\", \"token\": %d, \"patientId\": \"%s\", \"registrationTime\": \"%s\", \"isReturningPatient\": %b}",
                                    successMessage, patient.getToken(), patient.getPatientId(), patient.getRegistrationTime().toString(), isReturningPatient
                            );
                            sendJsonResponse(exchange, 200, successResponse);
                        } else {
                            String errorResponse = "{\"success\": false, \"message\": \"Failed to register patient in database.\"}";
                            sendJsonResponse(exchange, 500, errorResponse);
                        }

                    } catch (Exception e) {
                        System.err.println("Registration error: " + e.getMessage());
                        e.printStackTrace();
                        String errorResponse = String.format("{\"success\": false, \"message\": \"Server error: %s\"}", e.getMessage());
                        sendJsonResponse(exchange, 500, errorResponse);
                    }
                } else if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                    exchange.sendResponseHeaders(200, 0);
                    exchange.getResponseBody().close();
                }
            }

            private void sendJsonResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes(StandardCharsets.UTF_8));
                os.close();
            }

            private Patient parsePatientFromJson(String json) {
                Patient patient = new Patient();
                patient.setPhoneNumber(extractJsonValue(json, "phoneNumber"));
                patient.setName(extractJsonValue(json, "name"));
                patient.setEmail(extractJsonValue(json, "email"));

                String ageStr = extractJsonValue(json, "age");
                if (!ageStr.isEmpty()) {
                    patient.setAge(Integer.parseInt(ageStr));
                }

                patient.setGender(extractJsonValue(json, "gender"));
                patient.setLocation(extractJsonValue(json, "location"));
                patient.setIssue(extractJsonValue(json, "issue"));
                return patient;
            }

            private String extractJsonValue(String json, String key) {
                String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"|\"" + key + "\"\\s*:\\s*([^,}]*)";
                Pattern p = Pattern.compile(pattern);
                java.util.regex.Matcher m = p.matcher(json);
                if (m.find()) {
                    return m.group(1) != null ? m.group(1) : m.group(2);
                }
                return "";
            }

            private String validatePatient(Patient patient) {
                if (patient.getPhoneNumber() == null || !patient.getPhoneNumber().matches("\\d{10}")) {
                    return "Phone number must be exactly 10 digits";
                }
                if (patient.getName() == null || patient.getName().trim().length() < 2) {
                    return "Name must be at least 2 characters";
                }
                if (patient.getAge() == null || patient.getAge() < 0 || patient.getAge() > 150) {
                    return "Age must be between 0 and 150";
                }
                if (patient.getGender() == null || patient.getGender().trim().isEmpty()) {
                    return "Gender is required";
                }
                if (patient.getLocation() == null || patient.getLocation().trim().length() < 2) {
                    return "Location must be at least 2 characters";
                }
                if (patient.getIssue() == null || patient.getIssue().trim().length() < 5) {
                    return "Issue description must be at least 5 characters";
                }
                return null;
            }
        }
    }

    static String getMainPageHTML() {
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
                    --primary-color: #4f46e5;
                    --secondary-color: #06b6d4;
                    --success-color: #10b981;
                    --danger-color: #ef4444;
                    --dark-color: #1f2937;
                    --returning-patient-color: #f59e0b;
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
                }
                
                .form-section {
                    background: white;
                    border-radius: 15px;
                    padding: 30px;
                    margin: 20px;
                    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.05);
                }
                
                .form-control, .form-select {
                    border: 2px solid #e2e8f0;
                    border-radius: 10px;
                    padding: 12px 16px;
                    transition: all 0.3s ease;
                }
                
                .form-control:focus, .form-select:focus {
                    border-color: var(--primary-color);
                    box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.1);
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
                
                .btn-danger {
                    background: linear-gradient(135deg, var(--danger-color), #dc2626);
                    border: none;
                }
                
                .token-display {
                    background: linear-gradient(135deg, var(--success-color), #059669);
                    color: white;
                    padding: 20px;
                    border-radius: 15px;
                    text-align: center;
                    margin: 20px 0;
                    font-size: 1.2rem;
                    font-weight: 700;
                }
                
                .table {
                    border-radius: 15px;
                    overflow: hidden;
                }
                
                .table thead {
                    background: linear-gradient(135deg, var(--dark-color), #374151);
                    color: white;
                }
                
                .table tbody tr:hover {
                    background: #f8fafc;
                    cursor: pointer;
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
                    right: 20px;
                    background: var(--success-color);
                    color: white;
                    padding: 10px 20px;
                    border-radius: 50px;
                    font-weight: 600;
                    box-shadow: 0 5px 15px rgba(0, 0, 0, 0.2);
                }
                
                .returning-patient-badge {
                    background: linear-gradient(135deg, var(--returning-patient-color), #d97706);
                    color: white;
                    padding: 5px 10px;
                    border-radius: 15px;
                    font-size: 0.8rem;
                    font-weight: 600;
                }
                
                .new-patient-badge {
                    background: linear-gradient(135deg, var(--success-color), #059669);
                    color: white;
                    padding: 5px 10px;
                    border-radius: 15px;
                    font-size: 0.8rem;
                    font-weight: 600;
                }
            </style>
        </head>
        <body>
            <div class="status-indicator">
                <i class="fas fa-circle text-success me-2"></i>System Online
            </div>
            
            <div class="container-fluid">
                <div class="main-container">
                    <div class="header">
                        <h1><i class="fas fa-hospital-user me-3"></i>Healthcare Registration System</h1>
                        <p class="mb-0 fs-5">Complete Patient Registration & Management - localhost:5000</p>
                        <p class="mb-0 mt-2"><i class="fas fa-info-circle me-2"></i>Returning patients will reuse their existing Patient ID</p>
                    </div>

                    <div class="row">
                        <div class="col-lg-5">
                            <div class="form-section">
                                <h3 class="section-title"><i class="fas fa-user-plus me-2"></i>Patient Registration</h3>
                                <p class="text-muted mb-4"><i class="fas fa-id-card me-2"></i>New patients get unique ID, returning patients reuse existing ID</p>
                                
                                <div id="alertContainer"></div>
                                
                                <form id="patientForm">
                                    <div class="mb-3">
                                        <label class="form-label"><i class="fas fa-phone me-1"></i>Phone Number *</label>
                                        <input type="text" class="form-control" id="phoneNumber" maxlength="10" required>
                                    </div>
                                    <div class="mb-3">
                                        <label class="form-label"><i class="fas fa-user me-1"></i>Full Name *</label>
                                        <input type="text" class="form-control" id="name" required>
                                    </div>
                                    <div class="mb-3">
                                        <label class="form-label"><i class="fas fa-envelope me-1"></i>Email</label>
                                        <input type="email" class="form-control" id="email">
                                    </div>
                                    <div class="row">
                                        <div class="col-md-6 mb-3">
                                            <label class="form-label"><i class="fas fa-calendar me-1"></i>Age *</label>
                                            <input type="number" class="form-control" id="age" min="0" max="150" required>
                                        </div>
                                        <div class="col-md-6 mb-3">
                                            <label class="form-label"><i class="fas fa-venus-mars me-1"></i>Gender *</label>
                                            <select class="form-select" id="gender" required>
                                                <option value="">Select Gender</option>
                                                <option value="Male">Male</option>
                                                <option value="Female">Female</option>
                                                <option value="Other">Other</option>
                                            </select>
                                        </div>
                                    </div>
                                    <div class="mb-3">
                                        <label class="form-label"><i class="fas fa-map-marker-alt me-1"></i>Location *</label>
                                        <input type="text" class="form-control" id="location" required>
                                    </div>
                                    <div class="mb-3">
                                        <label class="form-label"><i class="fas fa-notes-medical me-1"></i>Medical Issue *</label>
                                        <textarea class="form-control" id="issue" rows="3" required></textarea>
                                    </div>

                                    <div class="token-display">
                                        <i class="fas fa-ticket-alt me-2"></i>Next Token: <span id="nextToken">Loading...</span>
                                    </div>

                                    <div class="d-grid gap-2 d-md-flex justify-content-md-end">
                                        <button type="button" class="btn btn-danger me-md-2" id="clearBtn">
                                            <i class="fas fa-eraser me-2"></i>Clear Form
                                        </button>
                                        <button type="submit" class="btn btn-primary" id="submitBtn">
                                            <i class="fas fa-user-plus me-2"></i>Register Patient
                                        </button>
                                    </div>
                                </form>
                            </div>
                        </div>

                        <div class="col-lg-7">
                            <div class="form-section">
                                <h3 class="section-title"><i class="fas fa-history me-2"></i>Patient History</h3>
                                
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
                                                    <i class="fas fa-search fa-2x mb-2 d-block"></i>
                                                    Enter a 10-digit phone number to view patient history
                                                </td>
                                            </tr>
                                        </tbody>
                                    </table>
                                </div>
                                
                                <div class="mt-3">
                                    <small class="text-muted">
                                        <i class="fas fa-info-circle me-1"></i>
                                        Double-click on any row to auto-fill the form. Same Patient ID for returning patients.
                                    </small>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <script src="https://cdnjs.cloudflare.com/ajax/libs/bootstrap/5.3.0/js/bootstrap.bundle.min.js"></script>
            <script>
                class HealthcareApp {
                    constructor() {
                        this.initializeElements();
                        this.attachEventListeners();
                        this.loadNextToken();
                    }

                    initializeElements() {
                        this.form = document.getElementById('patientForm');
                        this.phoneField = document.getElementById('phoneNumber');
                        this.nameField = document.getElementById('name');
                        this.emailField = document.getElementById('email');
                        this.ageField = document.getElementById('age');
                        this.genderField = document.getElementById('gender');
                        this.locationField = document.getElementById('location');
                        this.issueField = document.getElementById('issue');
                        this.nextTokenSpan = document.getElementById('nextToken');
                        this.historyTableBody = document.getElementById('historyTableBody');
                        this.alertContainer = document.getElementById('alertContainer');
                        this.submitBtn = document.getElementById('submitBtn');
                        this.clearBtn = document.getElementById('clearBtn');
                    }

                    attachEventListeners() {
                        this.phoneField.addEventListener('input', (e) => {
                            e.target.value = e.target.value.replace(/\\D/g, '').substring(0, 10);
                            if (e.target.value.length === 10) {
                                this.loadPatientHistory(e.target.value);
                            } else {
                                this.clearHistory();
                            }
                        });

                        this.form.addEventListener('submit', (e) => {
                            e.preventDefault();
                            this.registerPatient();
                        });

                        this.clearBtn.addEventListener('click', () => {
                            this.clearForm();
                        });

                        this.historyTableBody.addEventListener('dblclick', (e) => {
                            const row = e.target.closest('tr');
                            if (row && row.dataset.patient) {
                                this.fillFormFromHistory(JSON.parse(row.dataset.patient));
                            }
                        });
                    }

                    async loadNextToken() {
                        try {
                            const response = await fetch('/api/next-token');
                            const data = await response.json();
                            this.nextTokenSpan.textContent = data.nextToken;
                        } catch (error) {
                            console.error('Error loading next token:', error);
                            this.nextTokenSpan.textContent = '1';
                        }
                    }

                    async loadPatientHistory(phoneNumber) {
                        try {
                            const response = await fetch(`/api/history/${phoneNumber}`);
                            const history = await response.json();
                            
                            if (history.length === 0) {
                                this.historyTableBody.innerHTML = `
                                    <tr><td colspan="6" class="text-center text-muted py-4">
                                        <i class="fas fa-user-slash fa-2x mb-2 d-block"></i>
                                        No records found for this phone number
                                    </td></tr>
                                `;
                                return;
                            }

                            // Group by patient_id to show unique patient IDs
                            const patientIds = [...new Set(history.map(p => p.patientId))];
                            
                            this.historyTableBody.innerHTML = history.map((patient, index) => {
                                const isFirstOccurrence = history.findIndex(p => p.patientId === patient.patientId) === index;
                                const patientIdBadge = isFirstOccurrence ? 
                                    `<span class="badge bg-secondary">${patient.patientId}</span>` +
                                    (patientIds.length > 1 ? ' <span class="returning-patient-badge">REUSED ID</span>' : '') :
                                    `<span class="badge bg-light text-dark">${patient.patientId}</span> <small class="text-muted">(same ID)</small>`;
                                
                                return `
                                <tr data-patient='${JSON.stringify(patient)}' style="animation: fadeIn 0.5s ease-in;">
                                    <td><strong>${patient.name}</strong></td>
                                    <td>${patientIdBadge}</td>
                                    <td>${patient.age}</td>
                                    <td>
                                        <i class="fas ${patient.gender === 'Male' ? 'fa-mars text-primary' : 
                                                        patient.gender === 'Female' ? 'fa-venus text-danger' : 
                                                        'fa-genderless text-info'}"></i>
                                        ${patient.gender}
                                    </td>
                                    <td><span class="badge bg-primary">#${patient.token}</span></td>
                                    <td><small>${new Date(patient.registrationTime).toLocaleString()}</small></td>
                                </tr>
                            `}).join('');
                        } catch (error) {
                            console.error('Error loading patient history:', error);
                            this.showAlert('Error loading patient history', 'danger');
                        }
                    }

                    clearHistory() {
                        this.historyTableBody.innerHTML = `
                            <tr><td colspan="6" class="text-center text-muted py-4">
                                <i class="fas fa-search fa-2x mb-2 d-block"></i>
                                Enter a 10-digit phone number to view patient history
                            </td></tr>
                        `;
                    }

                    fillFormFromHistory(patient) {
                        this.nameField.value = patient.name;
                        this.emailField.value = patient.email || '';
                        this.ageField.value = patient.age;
                        this.genderField.value = patient.gender;
                        this.locationField.value = patient.location;
                        this.issueField.value = patient.issue;
                        this.showAlert('Form auto-filled from patient history', 'info');
                    }

                    async registerPatient() {
                        if (!this.validateForm()) {
                            return;
                        }

                        this.setLoading(true);
                        
                        const formData = {
                            phoneNumber: this.phoneField.value.trim(),
                            name: this.nameField.value.trim(),
                            email: this.emailField.value.trim() || null,
                            age: parseInt(this.ageField.value),
                            gender: this.genderField.value,
                            location: this.locationField.value.trim(),
                            issue: this.issueField.value.trim()
                        };

                        try {
                            const response = await fetch('/api/register', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify(formData)
                            });

                            const result = await response.json();

                            if (response.ok && result.success) {
                                const alertType = result.isReturningPatient ? 'warning' : 'success';
                                const badgeClass = result.isReturningPatient ? 'returning-patient-badge' : 'new-patient-badge';
                                const statusText = result.isReturningPatient ? 'RETURNING PATIENT' : 'NEW PATIENT';
                                
                                this.showAlert(`${result.message} <br><strong>Patient ID:</strong> ${result.patientId} <br><strong>Token:</strong> #${result.token} <br><span class="${badgeClass}">${statusText}</span>`, alertType);
                                this.clearForm();
                                this.loadNextToken();
                                if (this.phoneField.value.length === 10) {
                                    this.loadPatientHistory(this.phoneField.value);
                                }
                            } else {
                                this.showAlert(result.message || 'Registration failed', 'danger');
                            }
                        } catch (error) {
                            console.error('Registration error:', error);
                            this.showAlert('Network error. Please check if server is running.', 'danger');
                        } finally {
                            this.setLoading(false);
                        }
                    }

                    validateForm() {
                        const phone = this.phoneField.value.trim();
                        const name = this.nameField.value.trim();
                        const age = this.ageField.value;
                        const gender = this.genderField.value;
                        const location = this.locationField.value.trim();
                        const issue = this.issueField.value.trim();

                        if (phone.length !== 10) {
                            this.showAlert('Phone number must be exactly 10 digits', 'danger');
                            return false;
                        }
                        if (name.length < 2) {
                            this.showAlert('Name must be at least 2 characters', 'danger');
                            return false;
                        }
                        if (!age || age < 0 || age > 150) {
                            this.showAlert('Please enter a valid age (0-150)', 'danger');
                            return false;
                        }
                        if (!gender) {
                            this.showAlert('Please select gender', 'danger');
                            return false;
                        }
                        if (location.length < 2) {
                            this.showAlert('Location must be at least 2 characters', 'danger');
                            return false;
                        }
                        if (issue.length < 5) {
                            this.showAlert('Issue description must be at least 5 characters', 'danger');
                            return false;
                        }
                        return true;
                    }

                    clearForm() {
                        this.form.reset();
                        this.clearHistory();
                        this.loadNextToken();
                        this.showAlert('Form cleared successfully', 'info');
                    }

                    setLoading(loading) {
                        if (loading) {
                            this.submitBtn.disabled = true;
                            this.submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin me-2"></i>Registering...';
                        } else {
                            this.submitBtn.disabled = false;
                            this.submitBtn.innerHTML = '<i class="fas fa-user-plus me-2"></i>Register Patient';
                        }
                    }

                    showAlert(message, type) {
                        const alertDiv = document.createElement('div');
                        alertDiv.className = `alert alert-${type} alert-dismissible fade show`;
                        alertDiv.innerHTML = `
                            <i class="fas ${type === 'success' ? 'fa-check-circle' : 
                                           type === 'danger' ? 'fa-exclamation-triangle' : 
                                           type === 'warning' ? 'fa-user-clock' :
                                           'fa-info-circle'} me-2"></i>
                            ${message}
                            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
                        `;
                        
                        this.alertContainer.appendChild(alertDiv);
                        
                        setTimeout(() => {
                            if (alertDiv.parentElement) {
                                alertDiv.remove();
                            }
                        }, 8000);
                    }
                }

                document.addEventListener('DOMContentLoaded', () => {
                    new HealthcareApp();
                    console.log('Healthcare Registration System loaded with Patient ID Reuse Feature!');
                });
            </script>
        </body>
        </html>
        """;
    }

    public static void main(String[] args) {
        try {
            DatabaseService.initializeDatabase();
            HttpServer server = HttpServer.create(new InetSocketAddress(5000), 0);

            server.createContext("/", new WebHandlers.HomeHandler());
            server.createContext("/api/history/", new WebHandlers.HistoryHandler());
            server.createContext("/api/next-token", new WebHandlers.TokenHandler());
            server.createContext("/api/register", new WebHandlers.RegisterHandler());

            server.setExecutor(Executors.newFixedThreadPool(10));
            server.start();

            System.out.println("\n" + "=".repeat(60));
            System.out.println("HEALTHCARE REGISTRATION SYSTEM STARTED!");
            System.out.println("=".repeat(60));
            System.out.println("Server: http://localhost:5000");
            System.out.println("Database: " + DB_URL);
            System.out.println("Feature: Patient ID Reuse for Returning Patients");
            System.out.println("Status: All systems operational");
            System.out.println("=".repeat(60));
            System.out.println("Open browser: http://localhost:5000");
            System.out.println("Press Ctrl+C to stop server");
            System.out.println("=".repeat(60) + "\n");

        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}