package enrollease.model;

import java.io.Serializable;

/**
 * A subject section offering. Legacy constructor retained for the original
 * single-section catalog and DSA demonstrations.
 */
public class Subject implements Describable, Comparable<Subject>, Serializable {
    private static final long serialVersionUID = 1L;

    private String offeringId;
    private String code;
    private String title;
    private int units;
    private int lectureUnits;
    private int labUnits;
    private String section;
    private Schedule schedule;
    private Schedule lectureSchedule;
    private Schedule labSchedule;
    private int slots;
    private int minimumStudents;
    private int maximumStudents;
    private int enrolledStudents;

    /** Original catalog constructor. */
    public Subject(String code, String title, int units, String section, Schedule schedule, int slots) {
        this(null, code, title, units, 0, section, schedule, null, 0,
                Math.max(0, slots), 0);
    }

    /** Constructor for an independently scheduled section offering. */
    public Subject(String offeringId, String code, String title, int lectureUnits, int labUnits,
                   String section, Schedule lectureSchedule, Schedule labSchedule,
                   int minimumStudents, int maximumStudents, int enrolledStudents) {
        this.offeringId = offeringId;
        this.code = code;
        this.title = title;
        this.lectureUnits = lectureUnits;
        this.labUnits = labUnits;
        this.units = lectureUnits + labUnits;
        this.section = section;
        this.lectureSchedule = lectureSchedule;
        this.labSchedule = labSchedule;
        this.schedule = lectureSchedule != null ? lectureSchedule : labSchedule;
        this.minimumStudents = minimumStudents;
        this.maximumStudents = maximumStudents;
        this.enrolledStudents = enrolledStudents;
        this.slots = Math.max(0, maximumStudents - enrolledStudents);
    }

    public String getOfferingId() { return offeringId; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public int getUnits() { return units; }
    public int getLectureUnits() { return lectureUnits; }
    public int getLabUnits() { return labUnits; }
    public String getSection() { return section; }
    public Schedule getSchedule() { return schedule; }
    public Schedule getLectureSchedule() { return lectureSchedule; }
    public Schedule getLabSchedule() { return labSchedule; }
    public int getSlots() { return slots; }
    public int getMinimumStudents() { return minimumStudents; }
    public int getMaximumStudents() { return maximumStudents; }
    public int getEnrolledStudents() { return enrolledStudents; }

    public boolean isFull() { return slots <= 0; }
    public boolean isBelowMinimum() {
        return minimumStudents > 0 && enrolledStudents < minimumStudents;
    }

    @Override
    public String describe() {
        String result = code + " " + title + " (" + units + " units";
        if (lectureSchedule != null) result += ", LEC " + lectureSchedule;
        if (labSchedule != null) result += ", LAB " + labSchedule;
        return result + ")";
    }

    // Natural ordering remains by subject code for the original BST exercise.
    @Override
    public int compareTo(Subject other) {
        return code.compareToIgnoreCase(other.code);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Subject)) return false;
        Subject value = (Subject) other;
        if (hasOfferingId() || value.hasOfferingId()) {
            return hasOfferingId() && value.hasOfferingId()
                    && offeringId.equals(value.offeringId);
        }
        return code.equalsIgnoreCase(value.code);
    }

    @Override
    public int hashCode() {
        return hasOfferingId() ? offeringId.hashCode() : code.toLowerCase().hashCode();
    }

    private boolean hasOfferingId() {
        return offeringId != null && !offeringId.isEmpty();
    }

    @Override
    public String toString() { return describe(); }
}
