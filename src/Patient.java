public class Patient {
    private String phoneNumber;
    private String name;
    private String email;
    private String patientId;
    private int age;
    private String gender;
    private String location;
    private String issue;
    private int token;
    private java.sql.Timestamp registrationTime;

    public Patient(String phoneNumber, String name, String email, String patientId,
                   int age, String gender, String location, String issue,
                   int token, java.sql.Timestamp registrationTime) {
        this.phoneNumber = phoneNumber;
        this.name = name;
        this.email = email;
        this.patientId = patientId;
        this.age = age;
        this.gender = gender;
        this.location = location;
        this.issue = issue;
        this.token = token;
        this.registrationTime = registrationTime;
    }

    // Getters and Setters
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getIssue() { return issue; }
    public void setIssue(String issue) { this.issue = issue; }

    public int getToken() { return token; }
    public void setToken(int token) { this.token = token; }

    public java.sql.Timestamp getRegistrationTime() { return registrationTime; }
    public void setRegistrationTime(java.sql.Timestamp registrationTime) {
        this.registrationTime = registrationTime;
    }
}
