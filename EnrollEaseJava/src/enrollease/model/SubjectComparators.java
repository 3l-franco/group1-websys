package enrollease.model;

import java.util.Comparator;

/**
 * Different ways to order subjects. Each comparator is an ANONYMOUS INNER CLASS:
 * a class with no name, created on the spot with "new Comparator<Subject>() { ... }".
 */
public class SubjectComparators {

    public static final Comparator<Subject> BY_CODE = new Comparator<Subject>() {
        @Override
        public int compare(Subject a, Subject b) {
            return a.getCode().compareToIgnoreCase(b.getCode());
        }
    };

    public static final Comparator<Subject> BY_TITLE = new Comparator<Subject>() {
        @Override
        public int compare(Subject a, Subject b) {
            return a.getTitle().compareToIgnoreCase(b.getTitle());
        }
    };

    public static final Comparator<Subject> BY_UNITS = new Comparator<Subject>() {
        @Override
        public int compare(Subject a, Subject b) {
            return a.getUnits() - b.getUnits();
        }
    };

    public static final Comparator<Subject> BY_SCHEDULE = new Comparator<Subject>() {
        @Override
        public int compare(Subject a, Subject b) {
            int day = a.getSchedule().getDayIndex() - b.getSchedule().getDayIndex();
            if (day != 0) {
                return day;
            }
            return a.getSchedule().getStartMinute() - b.getSchedule().getStartMinute();
        }
    };
}
