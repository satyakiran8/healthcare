import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:postgresql://localhost:5433/healthcare";
    private static final String DB_USER = "postgres";  // Change as needed
    private static final String DB_PASSWORD = "admin123";  // Change as needed

    private Connection connection;

    public DatabaseManager() {
        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            updateTableStructure(); // Add missing columns to existing table
            System.out.println("Database connected successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Database Connection Error: " + e.getMessage());
        }
    }

    private void updateTableStructure() {
        try (Statement stmt = connection.createStatement()) {
            // Add missing columns if they don't exist
            try {
                stmt.execute("ALTER TABLE patients ADD COLUMN IF NOT EXISTS registration_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                stmt.execute("ALTER TABLE patients ADD COLUMN IF NOT EXISTS registration_date DATE DEFAULT CURRENT_DATE");
                stmt.execute("ALTER TABLE patients ADD COLUMN IF NOT EXISTS token INTEGER DEFAULT 1");
                System.out.println("Table structure updated successfully!");
            } catch (SQLException e) {
                // Columns might already exist, continue
                System.out.println("Table structure check completed.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Table update error: " + e.getMessage());
        }
    }

    public List<Patient> getPatientHistory(String phoneNumber) {
        List<Patient> history = new ArrayList<>();
        String sql = "SELECT * FROM patients WHERE phone_number = ? ORDER BY created_at DESC";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, phoneNumber);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                // Convert patient_id from integer to string
                String patientIdStr = "PAT" + String.format("%06d", rs.getInt("patient_id"));

                Patient patient = new Patient(
                        rs.getString("phone_number"),
                        rs.getString("name"),
                        rs.getString("email"),
                        patientIdStr,
                        rs.getInt("age"),
                        rs.getString("gender"),
                        rs.getString("location"),
                        rs.getString("issue"),
                        rs.getInt("token"),
                        rs.getTimestamp("created_at")
                );
                history.add(patient);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Error loading patient history: " + e.getMessage());
        }

        return history;
    }

    public boolean canRegisterToday(String phoneNumber, String name) {
        String sql = """
            SELECT created_at FROM patients 
            WHERE phone_number = ? AND name = ? AND DATE(created_at) = CURRENT_DATE 
            ORDER BY created_at DESC LIMIT 1
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, phoneNumber);
            pstmt.setString(2, name);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                Timestamp lastRegistration = rs.getTimestamp("created_at");
                LocalDateTime lastRegTime = lastRegistration.toLocalDateTime();
                LocalDateTime now = LocalDateTime.now();

                // Check if last registration was within 3 hours
                return lastRegTime.plusHours(3).isBefore(now);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Error checking registration eligibility: " + e.getMessage());
        }

        return true; // Can register if no previous registration found
    }

    public int getNextToken() {
        String sql = "SELECT COALESCE(MAX(token), 0) + 1 as next_token FROM patients WHERE DATE(created_at) = CURRENT_DATE";

        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next()) {
                return rs.getInt("next_token");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Error getting next token: " + e.getMessage());
        }

        return 1; // Default to 1 if error
    }

    public boolean registerPatient(Patient patient) {
        // Don't include patient_id in INSERT since it's auto-generated
        String sql = """
            INSERT INTO patients (phone_number, name, email, age, gender, location, issue, token)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, patient.getPhoneNumber());
            pstmt.setString(2, patient.getName());
            pstmt.setString(3, patient.getEmail());
            pstmt.setInt(4, patient.getAge());
            pstmt.setString(5, patient.getGender());
            pstmt.setString(6, patient.getLocation());
            pstmt.setString(7, patient.getIssue());
            pstmt.setInt(8, patient.getToken());

            int rowsAffected = pstmt.executeUpdate();
            System.out.println("Patient registered successfully in database!");
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Error registering patient: " + e.getMessage());
            return false;
        }
    }

    private void showError(String message) {
        System.err.println("Database Error: " + message);
        try {
            JOptionPane.showMessageDialog(null, message, "Database Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            // Ignore if GUI not available
        }
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}