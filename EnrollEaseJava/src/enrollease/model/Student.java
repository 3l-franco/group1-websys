package enrollease.model;

import java.util.ArrayList;

/** A student account. Also remembers the subjects the student has failed. */
public class Student extends Person {

    private String studentType; // New Student / Continuing Student / Transferee
    private ArrayList<String> failedCodes;

    // Constructor 1: no failed subjects yet.
    public Student(String id, String name, String email, String studentType, String password) {
        this(id, name, email, studentType, password, new ArrayList<String>());
    }

    // Constructor 2 (overloaded): with a list of failed subject codes.
    public Student(String id, String name, String email, String studentType,
                   String password, ArrayList<String> failedCodes) {
        super(id, name, email, password);
        this.studentType = studentType;
        this.failedCodes = failedCodes;
    }

    @Override
    public String getRole() { return "Student"; }

    @Override
    public String getExtra() { return studentType; }

    @Override
    public void setExtra(String extra) { this.studentType = extra; }

    public ArrayList<String> getFailedCodes() { return failedCodes; }

    @Override
    public String describe() {
        return super.describe() + " - " + studentType;
    }
}
