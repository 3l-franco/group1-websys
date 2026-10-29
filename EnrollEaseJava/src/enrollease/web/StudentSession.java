package enrollease.web;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import enrollease.ds.MyLinkedList;
import enrollease.db.EnrollmentDAO;
import enrollease.model.EnrollmentSlip;
import enrollease.model.QueueManager;
import enrollease.model.SiteContent;
import enrollease.model.Student;
import enrollease.model.Subject;
import enrollease.model.Ticket;
import enrollease.model.Timetable;

/**
 * Everything the server remembers about ONE logged-in student: the selected subjects,
 * the timetable, the unit count and the queue ticket. Each method returns an error
 * message (String) when something is not allowed, or null when it worked.
 */
public class StudentSession {

    private Student student;
    private Catalog catalog;
    private QueueManager queueManager;
    private EnrollmentDAO enrollmentDao;

    private ArrayList<String> lockedCodes;
    private MyLinkedList<Subject> selected = new MyLinkedList<Subject>();
    private Timetable timetable = new Timetable();
    private int units = 0;
    private Ticket ticket;                      // null until the enrollment is submitted
    private volatile boolean cashierRunning;    // written by a thread, so "volatile"

    public StudentSession(Student student, Catalog catalog, QueueManager queueManager,
                          EnrollmentDAO enrollmentDao) {
        this.student = student;
        this.catalog = catalog;
        this.queueManager = queueManager;
        this.enrollmentDao = enrollmentDao;
        this.lockedCodes = catalog.getGraph().findLockedSubjects(student.getFailedCodes()); // BFS
        // If the server was restarted, restore the latest submitted enrollment so
        // the student cannot submit a duplicate simply by signing in again.
        ArrayList<EnrollmentSlip> saved = enrollmentDao.getByStudentId(student.getId());
        if (!saved.isEmpty()) {
            EnrollmentSlip latest = saved.get(saved.size() - 1);
            ticket = new Ticket(latest.getQueueNumber(), student.getName());
            for (Subject subject : latest.getSubjects()) {
                selected.addLast(subject);
                timetable.place(subject);
                units += subject.getUnits();
            }
        }
    }

    public Student getStudent() { return student; }
    public int getUnits() { return units; }
    public Ticket getTicket() { return ticket; }
    public Timetable getTimetable() { return timetable; }
    public boolean isCashierRunning() { return cashierRunning; }
    public MyLinkedList<Subject> getSelected() { return selected; }

    public String statusOf(Subject s) {
        if (lockedCodes.contains(s.getCode())) {
            ArrayList<String> blockers = catalog.getGraph().getBlockingSubjects(s.getCode(), student.getFailedCodes());
            return "Locked (needs " + String.join(", ", blockers) + ")";
        }
        if (selected.contains(s)) {
            return "Selected";
        }
        if (s.isFull()) {
            return "Full";
        }
        return "Available";
    }

    public String select(String code) {
        Subject s = catalog.find(code);
        if (s == null) return "Subject not found.";
        if (ticket != null) return "Your enrollment was already submitted.";
        if (selected.contains(s)) return s.getCode() + " is already selected.";
        if (lockedCodes.contains(s.getCode())) return s.getCode() + " is " + statusOf(s) + ".";
        if (s.isFull()) return s.getCode() + " is full.";
        String conflict = timetable.findConflict(s);
        if (conflict != null) {
            return "Schedule conflict: " + conflict + " and " + s.getCode() + " both meet " + s.getSchedule() + ".";
        }
        if (units + s.getUnits() > SiteContent.MAX_UNITS) {
            return "Unit load exceeded. The maximum is " + SiteContent.MAX_UNITS + " units.";
        }
        selected.addLast(s);
        timetable.place(s);
        units += s.getUnits();
        return null;
    }

    public String remove(String code) {
        Subject s = catalog.find(code);
        if (ticket != null) return "Your enrollment was already submitted.";
        if (s == null || !selected.remove(s)) return "That subject is not in your selected list.";
        timetable.remove(s);
        units -= s.getUnits();
        return null;
    }

    public String submit() {
        if (ticket != null) return "Already submitted. Your queue number is " + ticket.getNumber() + ".";
        if (selected.isEmpty()) return "Select at least one subject.";
        ArrayList<Subject> chosen = new ArrayList<Subject>();
        for (Subject s : selected) {
            chosen.add(s);
        }
        Ticket newTicket = queueManager.issueTicket(student.getName());
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        EnrollmentSlip slip = new EnrollmentSlip(student.getId(), student.getName(), chosen, newTicket.getNumber(), now);
        if (!enrollmentDao.add(slip)) {
            return "Could not save your enrollment. Please try again.";
        }
        ticket = newTicket;
        return null;
    }

    public ArrayList<EnrollmentSlip> getSavedSlips() {
        return enrollmentDao.getByStudentId(student.getId());
    }

    // ---------------- payment queue ----------------

    /** The "Demo: call next number" button. */
    public void callNext() {
        if (ticket != null && queueManager.getNumbersAhead(ticket) > 0) {
            queueManager.callNext();
        }
    }

    /** Starts the cashier on a background thread. Returns false if it cannot start. */
    public boolean startCashier() {
        if (ticket == null || cashierRunning || queueManager.getNumbersAhead(ticket) == 0) {
            return false;
        }
        cashierRunning = true;
        Thread thread = new Thread(new CashierTask(), "Cashier-Thread");
        thread.setDaemon(true);   // do not keep the program alive just for this thread
        thread.start();
        return true;
    }

    // Inner class: it can use the outer object's ticket and queueManager directly.
    private class CashierTask implements Runnable {
        @Override
        public void run() {
            try {
                while (queueManager.getNumbersAhead(ticket) > 0) {
                    Thread.sleep(1500);          // the cashier takes a moment with each customer
                    queueManager.callNext();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                cashierRunning = false;
            }
        }
    }
}
