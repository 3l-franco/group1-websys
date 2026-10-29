package enrollease.model;

/**
 * A weekly timetable stored in a 2D ARRAY: rows = days, columns = 30-minute slots
 * from 7:00 to 21:00. A cell holds the subject code, or null when free.
 * If two subjects need the same cell, they have a schedule conflict.
 *
 * Concepts: 2D Array.
 */
public class Timetable {

    public static final int FIRST_MINUTE = 7 * 60;   // 7:00
    public static final int SLOT_LENGTH = 30;
    public static final int SLOTS_PER_DAY = 28;      // 7:00 to 21:00

    private String[][] grid = new String[Schedule.DAYS.length][SLOTS_PER_DAY];

    private int firstSlot(Schedule s) {
        return Math.max(0, (s.getStartMinute() - FIRST_MINUTE) / SLOT_LENGTH);
    }

    private int lastSlot(Schedule s) { // exclusive
        return Math.min(SLOTS_PER_DAY, (s.getEndMinute() - FIRST_MINUTE) / SLOT_LENGTH);
    }

    /** Returns the code of a subject that overlaps with this one, or null if there is none. */
    public String findConflict(Subject subject) {
        Schedule s = subject.getSchedule();
        int day = s.getDayIndex();
        if (day < 0) {
            return null;
        }
        for (int slot = firstSlot(s); slot < lastSlot(s); slot++) {
            if (grid[day][slot] != null) {
                return grid[day][slot];
            }
        }
        return null;
    }

    public void place(Subject subject) {
        Schedule s = subject.getSchedule();
        int day = s.getDayIndex();
        if (day < 0) {
            return;
        }
        for (int slot = firstSlot(s); slot < lastSlot(s); slot++) {
            grid[day][slot] = subject.getCode();
        }
    }

    public void remove(Subject subject) {
        for (int d = 0; d < grid.length; d++) {
            for (int slot = 0; slot < grid[d].length; slot++) {
                if (subject.getCode().equals(grid[d][slot])) {
                    grid[d][slot] = null;
                }
            }
        }
    }

    public String getCell(int day, int slot) {
        return grid[day][slot];
    }

    /** Minutes after midnight at the start of a slot (used for the row labels). */
    public static int slotStartMinute(int slot) {
        return FIRST_MINUTE + slot * SLOT_LENGTH;
    }
}
