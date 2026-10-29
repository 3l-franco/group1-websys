package enrollease.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Locale;

import enrollease.db.AccountDAO;
import enrollease.model.Admin;
import enrollease.model.QueueManager;
import enrollease.model.SiteContent;
import enrollease.model.Student;
import enrollease.util.Validator;
import enrollease.view.ConsoleView;

/**
 * Main CONTROLLER: shows the main menu (the website's navigation bar) and handles the
 * public pages: Home, About, Services, Calendar, Contact, Login and Register.
 * After a successful login it hands control to the Enrollment or Admin controller.
 */
public class MainController {

    private ConsoleView view;
    private AccountDAO accountDao = new AccountDAO();
    private EnrollmentController enrollmentController;
    private AdminController adminController;

    public MainController(ConsoleView view) {
        this.view = view;
        QueueManager queueManager = new QueueManager(); // one cashier queue for the whole run
        this.enrollmentController = new EnrollmentController(view, queueManager);
        this.adminController = new AdminController(view, accountDao);
    }

    public void run() {
        view.header(SiteContent.SYSTEM_NAME + " - Automated Student Enrollment System");
        view.println("Registrar and Enrollment Services Office, Technological Institute of the Philippines");

        String[] options = {"Home", "About Us", "Services", "Calendar", "Contact", "Login", "Register"};
        boolean running = true;
        while (running) {
            switch (view.menu("Main Menu", options, "Exit")) {
                case 1: view.showHome(); break;
                case 2: view.showAbout(); break;
                case 3: showServices(); break;
                case 4: showCalendar(); break;
                case 5: contact(); break;
                case 6: login(); break;
                case 7: register(); break;
                default: running = false; break;
            }
        }
        view.println("Thank you for using EnrollEase. Goodbye!");
    }

    // ---------------- Services (filter) ----------------

    private void showServices() {
        String[] filters = {"All", "For Students", "For Staff"};
        String filter = view.choose("Show", filters);
        ArrayList<SiteContent.ServiceItem> shown = new ArrayList<SiteContent.ServiceItem>();
        for (SiteContent.ServiceItem item : SiteContent.getServices()) {
            if (filter.equals("All")
                || (filter.equals("For Students") && item.getCategory().equals("student"))
                || (filter.equals("For Staff") && item.getCategory().equals("staff"))) {
                shown.add(item);
            }
        }
        view.showServices(shown, filter);
    }

    // ---------------- Calendar ----------------

    private void showCalendar() {
        YearMonth month = YearMonth.now();
        boolean open = true;
        while (open) {
            int[][] grid = new int[6][7];                       // 2D array: 6 weeks x 7 days
            int column = month.atDay(1).getDayOfWeek().getValue() % 7; // Sunday = 0
            int row = 0;
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                grid[row][column] = day;
                column++;
                if (column == 7) {
                    column = 0;
                    row++;
                }
            }
            boolean[] isEvent = new boolean[32];
            for (int day : SiteContent.getEventDays(month.getYear(), month.getMonthValue())) {
                isEvent[day] = true;
            }
            LocalDate today = LocalDate.now();
            int todayNumber = YearMonth.from(today).equals(month) ? today.getDayOfMonth() : 0;

            view.showCalendar(month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear(),
                              grid, isEvent, todayNumber);
            int choice = view.askInt("1 = Prev month, 2 = Next month, 0 = Back: ", 0, 2);
            if (choice == 1) {
                month = month.minusMonths(1);
            } else if (choice == 2) {
                month = month.plusMonths(1);
            } else {
                open = false;
            }
        }
    }

    // ---------------- Contact ----------------

    private void contact() {
        view.header("Contact Us");
        String name = view.ask("Full name: ");
        String email = view.ask("Email: ");
        String type = view.choose("I am a", new String[] {"New student", "Continuing student"});
        String topic = view.choose("Topic", SiteContent.CONTACT_TOPICS);
        String message = view.ask("Message: ");
        boolean agree = view.confirm("I agree to be contacted by the Registrar Office");

        ArrayList<String> problems = new ArrayList<String>();
        if (Validator.isBlank(name)) problems.add("Full name is required");
        if (!Validator.isValidEmail(email)) problems.add("A valid email is required");
        if (Validator.isBlank(message)) problems.add("Message is required");
        if (!agree) problems.add("You must agree to be contacted");

        if (problems.isEmpty()) {
            view.showSuccess("Your message has been submitted successfully. (" + type + ", topic: " + topic + ")");
        } else {
            for (String problem : problems) {
                view.showError(problem);
            }
        }
        view.showContactInfo();
    }

    // ---------------- Login / Register ----------------

    private void login() {
        view.header("Log in");
        String id = view.ask("Student ID / Username: ");
        String password = view.ask("Password: ");
        if (id.isEmpty() || password.isEmpty()) {
            view.showError("Please fill in all fields.");
            return;
        }
        if (id.equals("admin") && password.equals("admin")) {
            adminController.run(new Admin());
            return;
        }
        Student student = accountDao.login(id, password);
        if (student == null) {
            view.showError("Student ID or password is incorrect.");
        } else {
            enrollmentController.run(student);
        }
    }

    private void register() {
        view.header("Create account");
        String name = view.ask("Full name: ");
        String email = view.ask("Email: ");
        String type = view.choose("Student type", SiteContent.STUDENT_TYPES);
        String password = view.ask("Password (at least 6 characters): ");

        if (Validator.isBlank(name) || !Validator.isValidEmail(email) || !Validator.isValidPassword(password)) {
            view.showError("Enter your name, a valid email, and a password of at least 6 characters.");
            return;
        }
        if (accountDao.studentEmailExists(email)) {
            view.showError("That email is already registered.");
            return;
        }
        String newId = accountDao.nextStudentId();
        if (accountDao.add(new Student(newId, name, email, type, password))) {
            view.showSuccess("Account created! Your Student ID is " + newId + ". You can now log in.");
        } else {
            view.showError("Could not create the account.");
        }
    }
}
