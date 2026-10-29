package enrollease.model;

import java.io.Serializable;

/** Day and time of a class. Times are stored as minutes after midnight (7:30 = 450). */
public class Schedule implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String[] DAYS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    private String day;
    private int startMinute;
    private int endMinute;

    public Schedule(String day, int startMinute, int endMinute) {
        this.day = day;
        this.startMinute = startMinute;
        this.endMinute = endMinute;
    }

    public String getDay() { return day; }
    public int getStartMinute() { return startMinute; }
    public int getEndMinute() { return endMinute; }

    /** Position of the day in DAYS (Mon = 0), or -1 if unknown. */
    public int getDayIndex() {
        for (int i = 0; i < DAYS.length; i++) {
            if (DAYS[i].equalsIgnoreCase(day)) {
                return i;
            }
        }
        return -1;
    }

    // 780 -> "1:00" (12-hour style, same as the website)
    private static String formatTime(int minutes) {
        int hour = minutes / 60;
        int min = minutes % 60;
        int hour12 = (hour % 12 == 0) ? 12 : hour % 12;
        return hour12 + ":" + (min < 10 ? "0" + min : String.valueOf(min));
    }

    @Override
    public String toString() {
        return day + " " + formatTime(startMinute) + "-" + formatTime(endMinute);
    }
}
