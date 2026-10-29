package enrollease.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import enrollease.db.SubjectDAO;
import enrollease.ds.BinarySearchTree;
import enrollease.ds.MyLinkedList;
import enrollease.ds.PrerequisiteGraph;
import enrollease.model.EnrollmentSlip;
import enrollease.model.QueueManager;
import enrollease.model.SiteContent;
import enrollease.model.Student;
import enrollease.model.Subject;
import enrollease.model.SubjectComparators;
import enrollease.model.Ticket;
import enrollease.model.Timetable;
import enrollease.util.FileStorage;
import enrollease.util.SortUtil;
import enrollease.view.ConsoleView;

/**
 * CONTROLLER for the student side: subject enrollment and the payment queue.
 * (Website pages: enrollment.html + the enrollment part of script.js)
 */
public class EnrollmentController {

    private ConsoleView view;
    private QueueManager queueManager;
    private FileStorage<EnrollmentSlip> slipStorage = new FileStorage<EnrollmentSlip>("slips.ser");

    // data loaded once from the database
    private ArrayList<Subject> subjects;
    private BinarySearchTree<String, Subject> catalog = new BinarySearchTree<String, Subject>();
    private PrerequisiteGraph graph = new PrerequisiteGraph();

    // data for the student who is currently logged in
    private Student student;
    private ArrayList<String> lockedCodes;
    private MyLinkedList<Subject> selected;
    private Timetable timetable;
    private int units;
    private Ticket myTicket; // null until the enrollment is submitted

    public EnrollmentController(ConsoleView view, QueueManager queueManager) {
        this.view = view;
        this.queueManager = queueManager;

        SubjectDAO subjectDao = new SubjectDAO();
        subjects = subjectDao.getAll();
        for (Subject s : subjects) {
            catalog.insert(s.getCode().toUpperCase(), s);   // Tree: look up by code
            graph.addSubject(s.getCode());                  // Graph: one vertex per subject
        }
        for (String[] pair : subjectDao.getPrerequisitePairs()) {
            graph.addPrerequisite(pair[0], pair[1]);        // Graph: prerequisite -> subject
        }
    }

    public void run(Student loggedIn) {
        student = loggedIn;
        selected = new MyLinkedList<Subject>();
        timetable = new Timetable();
        units = 0;
        myTicket = null;
        lockedCodes = graph.findLockedSubjects(student.getFailedCodes()); // BFS

        view.header("Enrollment and Payment Queue");
        view.println("Welcome, " + student.getName() + " (" + student.getId() + ")");
        view.println(SiteContent.TERM + " | " + SiteContent.PROGRAM);
        view.println("Unit load allowed: " + SiteContent.MAX_UNITS + " units");
        if (!student.getFailedCodes().isEmpty()) {
            view.showInfo("You failed " + String.join(", ", student.getFailedCodes())
                + ". Subjects that require it are locked, and it is offered for retake.");
        }

        String[] options = {
            "View subjects and schedules",
            "Sort subjects",
            "Search a subject by code",
            "Select a subject",
            "Remove a selected subject",
            "View selected subjects and timetable",
            "Submit enrollment",
            "Payment queue",
            "View my saved enrollment slip"
        };
        boolean running = true;
        while (running) {
            switch (view.menu("Student Menu", options, "Log out")) {
                case 1: viewSubjects(catalog.inOrder(), "Subjects (from the tree, in order of code)"); break;
                case 2: sortSubjects(); break;
                case 3: searchSubject(); break;
                case 4: selectSubject(); break;
                case 5: removeSubject(); break;
                case 6: viewSelected(); break;
                case 7: submitEnrollment(); break;
                case 8: paymentQueue(); break;
                case 9: viewSavedSlips(); break;
                default: running = false; break;
            }
        }
        view.showSuccess("Logged out.");
    }

    // ---------------- viewing subjects ----------------

    private String statusOf(Subject s) {
        if (lockedCodes.contains(s.getCode())) {
            ArrayList<String> blockers = graph.getBlockingSubjects(s.getCode(), student.getFailedCodes()); // DFS
            return "Locked (needs " + String.join(", ", blockers) + ")";
        }
        if (s.isFull()) {
            return "Full";
        }
        if (selected.contains(s)) {
            return "Selected";
        }
        return "Available";
    }

    private void viewSubjects(ArrayList<Subject> list, String title) {
        view.header(title);
        String[] statuses = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            statuses[i] = statusOf(list.get(i));
        }
        view.showSubjectTable(list, statuses);
    }

    private void sortSubjects() {
        view.println("Sort by:");
        String by = view.choose("Field", new String[] {"Code", "Title", "Units", "Schedule"});
        Comparator<Subject> comparator;
        if (by.equals("Code")) {
            comparator = SubjectComparators.BY_CODE;
        } else if (by.equals("Title")) {
            comparator = SubjectComparators.BY_TITLE;
        } else if (by.equals("Units")) {
            comparator = SubjectComparators.BY_UNITS;
        } else {
            comparator = SubjectComparators.BY_SCHEDULE;
        }
        view.println("Algorithm:");
        int algorithm = view.askInt("Choose 1-4 " + Arrays.toString(SortUtil.ALGORITHM_NAMES) + ": ", 1, 4);

        Subject[] array = subjects.toArray(new Subject[0]);   // sort works on an array
        SortUtil.sort(array, comparator, algorithm);
        viewSubjects(new ArrayList<Subject>(Arrays.asList(array)), "Subjects sorted by " + by);
        view.showInfo(SortUtil.ALGORITHM_NAMES[algorithm - 1] + " used " + SortUtil.getComparisons() + " comparisons.");
    }

    private void searchSubject() {
        String code = view.ask("Subject code (e.g. IT 201): ").toUpperCase();
        Subject s = catalog.search(code);   // Binary Search Tree lookup
        if (s == null) {
            view.showError("Subject not found.");
            return;
        }
        view.println(s.describe() + " | Section " + s.getSection() + " | Slots: " + s.getSlots());
        view.println("Status: " + statusOf(s));
        ArrayList<String> chain = graph.getAllPrerequisites(s.getCode());
        view.println("Prerequisites: " + (chain.isEmpty() ? "none" : String.join(", ", chain)));
    }

    // ---------------- selecting subjects ----------------

    private void selectSubject() {
        String code = view.ask("Subject code to select: ").toUpperCase();
        Subject s = catalog.search(code);
        if (s == null) {
            view.showError("Subject not found.");
        } else if (myTicket != null) {
            view.showError("Your enrollment was already submitted.");
        } else if (selected.contains(s)) {
            view.showError(s.getCode() + " is already selected.");
        } else if (lockedCodes.contains(s.getCode())) {
            view.showError(s.getCode() + " is " + statusOf(s) + ".");
        } else if (s.isFull()) {
            view.showError(s.getCode() + " is full.");
        } else {
            String conflict = timetable.findConflict(s);
            if (conflict != null) {
                view.showError("Schedule conflict: " + conflict + " and " + s.getCode() + " both meet " + s.getSchedule() + ".");
            } else if (units + s.getUnits() > SiteContent.MAX_UNITS) {
                view.showError("Unit load exceeded. The maximum is " + SiteContent.MAX_UNITS + " units.");
            } else {
                selected.addLast(s);
                timetable.place(s);
                units += s.getUnits();
                view.showSuccess(s.getCode() + " added. Units: " + units + " / " + SiteContent.MAX_UNITS);
            }
        }
    }

    private void removeSubject() {
        if (myTicket != null) {
            view.showError("Your enrollment was already submitted.");
            return;
        }
        String code = view.ask("Subject code to remove: ").toUpperCase();
        Subject s = catalog.search(code);
        if (s != null && selected.remove(s)) {
            timetable.remove(s);
            units -= s.getUnits();
            view.showSuccess(s.getCode() + " removed. Units: " + units + " / " + SiteContent.MAX_UNITS);
        } else {
            view.showError("That subject is not in your selected list.");
        }
    }

    private void viewSelected() {
        view.header("My Selected Subjects");
        view.showSelected(selected, units);
        view.showTimetable(timetable);
    }

    // ---------------- submit ----------------

    private void submitEnrollment() {
        if (myTicket != null) {
            view.showError("Already submitted. Your queue number is " + myTicket.getNumber() + ".");
            return;
        }
        if (selected.isEmpty()) {
            view.showError("Select at least one subject.");
            return;
        }
        ArrayList<Subject> chosen = new ArrayList<Subject>();
        for (Subject s : selected) {
            chosen.add(s);
        }
        myTicket = queueManager.issueTicket(student.getName());
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        EnrollmentSlip slip = new EnrollmentSlip(student.getId(), student.getName(), chosen, myTicket.getNumber(), now);
        slipStorage.append(slip);   // Serialization: save the object to slips.ser
        view.showSuccess("Enrollment submitted. Queue number " + myTicket.getNumber() + " generated.");
        view.showInfo("Your enrollment slip was saved to slips.ser. Payment queue is now unlocked.");
    }

    // ---------------- payment queue ----------------

    private void paymentQueue() {
        if (myTicket == null) {
            view.showInfo("Submit a valid enrollment first to access the payment queue.");
            return;
        }
        String[] options = {"Demo: Call next number", "Start cashier simulation (runs on a thread)"};
        boolean inQueue = true;
        while (inQueue) {
            showQueue();
            int choice = view.menu("Payment Queue", options, "Back");
            if (choice == 0) {
                inQueue = false;
            } else if (queueManager.getNumbersAhead(myTicket) == 0) {
                view.showInfo(queueManager.getMessage(myTicket));
            } else if (choice == 1) {
                queueManager.callNext();
            } else {
                Thread cashier = new Thread(new CashierThread(view, queueManager, myTicket, 1500), "Cashier-Thread");
                cashier.start();
                try {
                    cashier.join(); // wait here until the cashier thread finishes
                } catch (InterruptedException e) {
                    cashier.interrupt();
                }
            }
        }
    }

    private void showQueue() {
        view.showQueueStatus(myTicket.getNumber(), queueManager.getNumbersAhead(myTicket),
            queueManager.getNowServing().getNumber(), queueManager.getStatus(myTicket),
            queueManager.getMessage(myTicket));
    }

    // ---------------- saved slips ----------------

    private void viewSavedSlips() {
        ArrayList<EnrollmentSlip> all = slipStorage.load();   // Serialization: read objects back
        boolean found = false;
        for (EnrollmentSlip slip : all) {
            if (slip.getStudentId().equals(student.getId())) {
                view.showSlip(slip);
                found = true;
            }
        }
        if (!found) {
            view.showInfo("No saved enrollment slip for " + student.getId() + " yet.");
        }
    }
}
