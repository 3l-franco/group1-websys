package enrollease.model;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * The record of a submitted enrollment. It is SERIALIZED (saved as an object)
 * into slips.ser, so every class it contains must also be Serializable.
 * A slip HAS subjects (aggregation).
 */
public class EnrollmentSlip implements Serializable {

    private static final long serialVersionUID = 1L;

    private String studentId;
    private String studentName;
    private ArrayList<Subject> subjects;
    private int totalUnits;
    private int queueNumber;
    private String submittedAt;
    private String status = "Pending Review";
    private String adminNotes = "";

    public EnrollmentSlip(String studentId, String studentName, ArrayList<Subject> subjects,
                          int queueNumber, String submittedAt) {
        this(studentId, studentName, subjects, queueNumber, submittedAt, "Pending Review", "");
    }

    public EnrollmentSlip(String studentId, String studentName, ArrayList<Subject> subjects,
                          int queueNumber, String submittedAt, String status, String adminNotes) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.subjects = subjects;
        this.queueNumber = queueNumber;
        this.submittedAt = submittedAt;
        this.status = (status == null || status.isEmpty()) ? "Pending Review" : status;
        this.adminNotes = adminNotes == null ? "" : adminNotes;
        for (Subject s : subjects) {
            totalUnits += s.getUnits();
        }
    }

    public String getStudentId() { return studentId; }
    public String getStudentName() { return studentName; }
    public ArrayList<Subject> getSubjects() { return subjects; }
    public int getTotalUnits() { return totalUnits; }
    public int getQueueNumber() { return queueNumber; }
    public String getSubmittedAt() { return submittedAt; }
    public String getStatus() { return status; }
    public String getAdminNotes() { return adminNotes; }
}
