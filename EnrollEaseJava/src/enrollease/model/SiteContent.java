package enrollease.model;

import java.util.ArrayList;

/**
 * The fixed text of the website's pages (Home, About, Services, Calendar, Contact),
 * kept in one place so the controllers and view stay short.
 */
public class SiteContent {

    public static final String SYSTEM_NAME = "EnrollEase";
    public static final String TERM = "1st Semester, A.Y. 2026-2027";
    public static final String PROGRAM = "BS Information Technology, 2nd Year";
    public static final int MAX_UNITS = 18;

    public static final String[] STUDENT_TYPES = {"New Student", "Continuing Student", "Transferee"};
    public static final String[] STAFF_POSITIONS = {"Registrar", "Cashier", "Clerk", "Administrator"};
    public static final String[] CONTACT_TOPICS = {"Enrollment", "Credentials", "Schedule", "Payment Queue", "Other"};

    public static final String[] HOME_SLIDES = {
        "Enroll online. Skip the long lines.\n   Upload requirements, pick your subjects, and get your payment queue number from anywhere.",
        "No more schedule conflicts\n   The system flags overlapping schedules and full sections before you submit.",
        "Know when it is your turn\n   Get notified when your cashier number is near."
    };

    public static final String[] HOME_FEATURES = {
        "Upload credentials - New students and transferees upload Form 138, PSA birth certificate and other documents.",
        "Check schedules - See available subjects and sections. Conflicts and full sections are flagged.",
        "Prerequisite check - Subjects with a failed prerequisite are locked automatically.",
        "Payment queue - Get an online queue number and an alert when your turn is near."
    };

    public static final String[] HOW_IT_WORKS = {
        "Create an account and log in.",
        "New students: upload credentials and wait for staff approval.",
        "Select subjects and sections.",
        "Staff review and approve your enrollment.",
        "Get your queue number and pay at the cashier when called."
    };

    public static final String MISSION =
        "EnrollEase replaces long enrollment lines with one online system for the Registrar and\n"
      + "   Enrollment Services Office. Students upload credentials, choose subjects without schedule\n"
      + "   conflicts, and receive a payment queue number. Payments are still made at the cashier.";

    public static final String[] TEAM = {
        "David, Mc Andrei Stephen C. - Home page",
        "Franco, Leemuelsam C. - Contact page",
        "Gabito, Ashaina Aiden H. (Leader) - Login page",
        "Gonzales, Jenelle M. - Services page",
        "Santos, Aliyah Janine D. - Enrollment page"
    };

    public static final String[] KEY_DATES = {
        "Oct 19-23, 2026: Credential upload and verification",
        "Oct 26-30, 2026: Subject enrollment",
        "Nov 2-6, 2026: Payment at the cashier",
        "Nov 9, 2026: Classes begin"
    };

    public static final String[] CONTACT_INFO = {
        "Registrar and Enrollment Services Office",
        "Technological Institute of the Philippines",
        "Email: registrar@tip.example.edu",
        "Office hours: Monday to Friday, 8:00 AM - 5:00 PM"
    };

    /** Days highlighted in the calendar for a given month (same data as the website). */
    public static int[] getEventDays(int year, int month) {
        if (year == 2026 && month == 10) {
            return new int[] {19, 20, 21, 22, 23, 26, 27, 28, 29, 30};
        }
        if (year == 2026 && month == 11) {
            return new int[] {2, 3, 4, 5, 6, 9};
        }
        return new int[0];
    }

    // Static nested class: one entry on the Services page.
    public static class ServiceItem {
        private String title;
        private String description;
        private String category; // "student" or "staff"

        public ServiceItem(String title, String description, String category) {
            this.title = title;
            this.description = description;
            this.category = category;
        }

        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getCategory() { return category; }
    }

    public static ArrayList<ServiceItem> getServices() {
        ArrayList<ServiceItem> list = new ArrayList<ServiceItem>();
        list.add(new ServiceItem("Credential Upload", "Upload Form 138, PSA birth certificate and other requirements as PDF or image files.", "student"));
        list.add(new ServiceItem("Credential Verification", "Staff approve or reject each document and provide remarks when needed.", "staff"));
        list.add(new ServiceItem("Schedule Checking", "View available schedules and get flagged for conflicts and full sections.", "student"));
        list.add(new ServiceItem("Prerequisite Locking", "Subjects with a failed prerequisite are locked and the allowed unit load is adjusted.", "student"));
        list.add(new ServiceItem("Subject and Section Management", "Add subjects, sections, slots, units and prerequisites.", "staff"));
        list.add(new ServiceItem("Payment Queue Number", "Get an online queue number and receive an alert when your turn is near.", "student"));
        return list;
    }
}
