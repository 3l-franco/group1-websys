package enrollease.model;

import java.io.Serializable;

/**
 * A subject offered this semester (one section).
 * A Subject HAS-A Schedule (composition).
 */
public class Subject implements Describable, Comparable<Subject>, Serializable {

    private static final long serialVersionUID = 1L;

    private String code;
    private String title;
    private int units;
    private String section;
    private Schedule schedule;
    private int slots;

    public Subject(String code, String title, int units, String section, Schedule schedule, int slots) {
        this.code = code;
        this.title = title;
        this.units = units;
        this.section = section;
        this.schedule = schedule;
        this.slots = slots;
    }

    public String getCode() { return code; }
    public String getTitle() { return title; }
    public int getUnits() { return units; }
    public String getSection() { return section; }
    public Schedule getSchedule() { return schedule; }
    public int getSlots() { return slots; }

    public boolean isFull() {
        return slots <= 0;
    }

    @Override
    public String describe() {
        return code + " " + title + " (" + units + " units, " + schedule + ")";
    }

    // Natural ordering = by subject code (needed by the Binary Search Tree).
    @Override
    public int compareTo(Subject other) {
        return code.compareToIgnoreCase(other.code);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Subject && code.equalsIgnoreCase(((Subject) other).code);
    }

    @Override
    public int hashCode() {
        return code.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return describe();
    }
}
