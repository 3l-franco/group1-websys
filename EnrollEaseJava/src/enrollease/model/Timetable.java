package enrollease.model;

/**
 * Weekly timetable stored in a 2D array. Each subject can have an independent
 * lecture and laboratory schedule. Both components participate in conflict checks.
 */
public class Timetable {
    public static final int FIRST_MINUTE = 7 * 60;
    public static final int SLOT_LENGTH = 30;
    public static final int SLOTS_PER_DAY = 28;

    private String[][] grid = new String[Schedule.DAYS.length][SLOTS_PER_DAY];

    private int firstSlot(Schedule schedule) {
        return Math.max(0, (schedule.getStartMinute() - FIRST_MINUTE) / SLOT_LENGTH);
    }

    private int lastSlot(Schedule schedule) {
        return Math.min(SLOTS_PER_DAY,
                (schedule.getEndMinute() - FIRST_MINUTE + SLOT_LENGTH - 1) / SLOT_LENGTH);
    }

    private String findConflict(Schedule schedule) {
        if (schedule == null) return null;
        int day = schedule.getDayIndex();
        if (day < 0 || day >= grid.length) return null;
        for (int slot = firstSlot(schedule); slot < lastSlot(schedule); slot++) {
            if (slot >= 0 && slot < SLOTS_PER_DAY && grid[day][slot] != null) {
                return grid[day][slot];
            }
        }
        return null;
    }

    public String findConflict(Subject subject) {
        String conflict = findConflict(subject.getLectureSchedule());
        return conflict != null ? conflict : findConflict(subject.getLabSchedule());
    }

    private void placeSchedule(Subject subject, Schedule schedule) {
        if (schedule == null) return;
        int day = schedule.getDayIndex();
        if (day < 0 || day >= grid.length) return;
        for (int slot = firstSlot(schedule); slot < lastSlot(schedule); slot++) {
            if (slot >= 0 && slot < SLOTS_PER_DAY) grid[day][slot] = subject.getCode();
        }
    }

    public void place(Subject subject) {
        placeSchedule(subject, subject.getLectureSchedule());
        placeSchedule(subject, subject.getLabSchedule());
    }

    public void remove(Subject subject) {
        for (int day = 0; day < grid.length; day++) {
            for (int slot = 0; slot < grid[day].length; slot++) {
                if (subject.getCode().equals(grid[day][slot])) grid[day][slot] = null;
            }
        }
    }

    public String getCell(int day, int slot) { return grid[day][slot]; }

    public static int slotStartMinute(int slot) {
        return FIRST_MINUTE + slot * SLOT_LENGTH;
    }
}
