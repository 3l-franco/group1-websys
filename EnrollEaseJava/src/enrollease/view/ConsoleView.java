package enrollease.view;

import java.util.ArrayList;
import java.util.Scanner;

import enrollease.ds.MyLinkedList;
import enrollease.model.EnrollmentSlip;
import enrollease.model.Person;
import enrollease.model.Schedule;
import enrollease.model.SiteContent;
import enrollease.model.Subject;
import enrollease.model.Timetable;

/**
 * The VIEW of the MVC design: the ONLY class that prints to the screen or reads the keyboard.
 * Controllers call these methods and never use System.out or Scanner directly.
 * (To change to a Swing/JavaFX screen later, only this class would need to be replaced.)
 */
public class ConsoleView {

    private Scanner scanner = new Scanner(System.in);

    // ---------------- basic input ----------------

    /** Shows a prompt and returns what the user typed. Exits the program if input has ended. */
    public String ask(String prompt) {
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            System.out.println();
            System.out.println("Input ended. Goodbye!");
            System.exit(0);
        }
        return scanner.nextLine().trim();
    }

    /** Keeps asking until the user types a whole number from min to max. */
    public int askInt(String prompt, int min, int max) {
        while (true) {
            String text = ask(prompt);
            try {
                int number = Integer.parseInt(text);
                if (number >= min && number <= max) {
                    return number;
                }
            } catch (NumberFormatException e) {
                // fall through to the message below
            }
            showError("Please enter a number from " + min + " to " + max + ".");
        }
    }

    public boolean confirm(String question) {
        String answer = ask(question + " (y/n): ");
        return answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes");
    }

    /** Shows a numbered menu. Returns 1..n for an option, or 0 for the last "back/exit" choice. */
    public int menu(String title, String[] options, String exitLabel) {
        System.out.println();
        System.out.println("--- " + title + " ---");
        for (int i = 0; i < options.length; i++) {
            System.out.println("  " + (i + 1) + ". " + options[i]);
        }
        System.out.println("  0. " + exitLabel);
        return askInt("Choose: ", 0, options.length);
    }

    /** Lets the user pick one of several text choices. Returns the chosen text. */
    public String choose(String label, String[] choices) {
        for (int i = 0; i < choices.length; i++) {
            System.out.println("  " + (i + 1) + ". " + choices[i]);
        }
        int pick = askInt(label + " (1-" + choices.length + "): ", 1, choices.length);
        return choices[pick - 1];
    }

    // ---------------- basic output ----------------

    public void println(String text) {
        System.out.println(text);
    }

    public void showSuccess(String text) {
        System.out.println("[OK] " + text);
    }

    public void showError(String text) {
        System.out.println("[!] " + text);
    }

    public void showInfo(String text) {
        System.out.println("[i] " + text);
    }

    public void header(String title) {
        System.out.println();
        System.out.println(line('=', 64));
        System.out.println("  " + title);
        System.out.println(line('=', 64));
    }

    private String line(char c, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    private String pad(String text, int width) {
        if (text.length() >= width) {
            return text.substring(0, width);
        }
        return text + line(' ', width - text.length());
    }

    // ---------------- public pages ----------------

    public void showHome() {
        header(SiteContent.SYSTEM_NAME + " - Home");
        for (String slide : SiteContent.HOME_SLIDES) {
            System.out.println(" * " + slide);
        }
        System.out.println();
        System.out.println("What you can do:");
        for (String feature : SiteContent.HOME_FEATURES) {
            System.out.println(" - " + feature);
        }
        System.out.println();
        System.out.println("How enrollment works:");
        for (int i = 0; i < SiteContent.HOW_IT_WORKS.length; i++) {
            System.out.println(" " + (i + 1) + ". " + SiteContent.HOW_IT_WORKS[i]);
        }
        System.out.println();
        System.out.println("Payments are still made at the cashier. EnrollEase only gives you a queue number.");
    }

    public void showAbout() {
        header("About EnrollEase");
        System.out.println("Our mission:");
        System.out.println("   " + SiteContent.MISSION);
        System.out.println();
        System.out.println("Team (BSIT-IT21S7):");
        for (String member : SiteContent.TEAM) {
            System.out.println(" - " + member);
        }
    }

    public void showServices(ArrayList<SiteContent.ServiceItem> items, String filterName) {
        header("Our Services (" + filterName + ")");
        for (SiteContent.ServiceItem item : items) {
            System.out.println(" * " + item.getTitle() + "  [" + item.getCategory() + "]");
            System.out.println("     " + item.getDescription());
        }
    }

    /**
     * grid[row][col] holds day numbers (0 = empty cell); isEvent is indexed by day number;
     * today is the day number to highlight (0 if today is not in this month).
     */
    public void showCalendar(String title, int[][] grid, boolean[] isEvent, int today) {
        header("Enrollment Calendar - " + title);
        System.out.println(" Sun   Mon   Tue   Wed   Thu   Fri   Sat");
        for (int row = 0; row < grid.length; row++) {
            StringBuilder sb = new StringBuilder();
            boolean empty = true;
            for (int col = 0; col < 7; col++) {
                int day = grid[row][col];
                if (day == 0) {
                    sb.append("      ");   // 4 spaces for the cell + 2 spaces between cells
                    continue;
                }
                empty = false;
                String number = (day < 10 ? " " : "") + day;
                if (day == today) {
                    sb.append("[" + number + "]  ");
                } else if (isEvent[day]) {
                    sb.append(" " + number + "*  ");
                } else {
                    sb.append(" " + number + "   ");
                }
            }
            if (!empty) {
                System.out.println(sb.toString());
            }
        }
        System.out.println("[ ] = today     * = enrollment activity");
        System.out.println();
        System.out.println("Key dates:");
        for (String date : SiteContent.KEY_DATES) {
            System.out.println(" - " + date);
        }
    }

    public void showContactInfo() {
        System.out.println();
        System.out.println("Office:");
        for (String info : SiteContent.CONTACT_INFO) {
            System.out.println(" - " + info);
        }
    }

    // ---------------- enrollment ----------------

    /** statuses[i] is the status text for subjects.get(i): Available / Full / Locked (...). */
    public void showSubjectTable(ArrayList<Subject> subjects, String[] statuses) {
        System.out.println(pad("Code", 10) + pad("Subject", 32) + pad("Un", 4) + pad("Sec", 5)
                         + pad("Schedule", 18) + pad("Slots", 7) + "Status");
        System.out.println(line('-', 100));
        for (int i = 0; i < subjects.size(); i++) {
            Subject s = subjects.get(i);
            System.out.println(pad(s.getCode(), 10) + pad(s.getTitle(), 32) + pad("" + s.getUnits(), 4)
                             + pad(s.getSection(), 5) + pad(s.getSchedule().toString(), 18)
                             + pad("" + s.getSlots(), 7) + statuses[i]);
        }
    }

    public void showSelected(MyLinkedList<Subject> selected, int units) {
        System.out.println("Selected subjects:");
        if (selected.isEmpty()) {
            System.out.println("  (none)");
        }
        for (Subject s : selected) { // works because MyLinkedList is Iterable
            System.out.println("  " + pad(s.getCode(), 10) + pad(s.getTitle(), 32) + s.getUnits() + " units  "
                             + s.getSchedule());
        }
        System.out.println("Units: " + units + " / " + SiteContent.MAX_UNITS);
    }

    public void showTimetable(Timetable timetable) {
        System.out.println();
        System.out.println("Weekly timetable:");
        StringBuilder head = new StringBuilder(pad("Time", 8));
        for (String day : Schedule.DAYS) {
            head.append(pad(day, 10));
        }
        System.out.println(head);
        boolean any = false;
        for (int slot = 0; slot < Timetable.SLOTS_PER_DAY; slot++) {
            boolean used = false;
            for (int day = 0; day < Schedule.DAYS.length; day++) {
                if (timetable.getCell(day, slot) != null) {
                    used = true;
                }
            }
            if (!used) {
                continue;
            }
            any = true;
            int minutes = Timetable.slotStartMinute(slot);
            int hour12 = (minutes / 60) % 12 == 0 ? 12 : (minutes / 60) % 12;
            String time = hour12 + ":" + (minutes % 60 == 0 ? "00" : "30");
            StringBuilder row = new StringBuilder(pad(time, 8));
            for (int day = 0; day < Schedule.DAYS.length; day++) {
                String cell = timetable.getCell(day, slot);
                row.append(pad(cell == null ? "." : cell, 10));
            }
            System.out.println(row);
        }
        if (!any) {
            System.out.println("  (empty)");
        }
    }

    public void showQueueStatus(int myNumber, int ahead, int nowServing, String status, String message) {
        System.out.println();
        System.out.println("+----------------------+----------------------+");
        System.out.println("| " + pad("Your queue number", 21) + "| " + pad("Now serving", 21) + "|");
        System.out.println("| " + pad("No. " + myNumber, 21) + "| " + pad("No. " + nowServing, 21) + "|");
        System.out.println("| " + pad("Numbers ahead: " + ahead, 21) + "| " + pad(status, 21) + "|");
        System.out.println("+----------------------+----------------------+");
        showInfo(message);
    }

    public void showSlip(EnrollmentSlip slip) {
        System.out.println();
        System.out.println("Enrollment slip - " + slip.getStudentName() + " (" + slip.getStudentId() + ")");
        System.out.println("Submitted: " + slip.getSubmittedAt() + "   Queue No.: " + slip.getQueueNumber());
        for (Subject s : slip.getSubjects()) {
            System.out.println("  " + pad(s.getCode(), 10) + pad(s.getTitle(), 32) + s.getUnits() + " units  " + s.getSchedule());
        }
        System.out.println("Total units: " + slip.getTotalUnits());
    }

    // ---------------- admin ----------------

    public void showAccounts(ArrayList<Person> people, String idLabel, String extraLabel) {
        System.out.println(pad(idLabel, 14) + pad("Full Name", 22) + pad("Email", 30) + extraLabel);
        System.out.println(line('-', 86));
        if (people.isEmpty()) {
            System.out.println("No records found.");
        }
        for (Person p : people) {
            System.out.println(pad(p.getId(), 14) + pad(p.getName(), 22) + pad(p.getEmail(), 30) + p.getExtra());
        }
    }
}
