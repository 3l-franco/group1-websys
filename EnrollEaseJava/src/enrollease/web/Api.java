package enrollease.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.sun.net.httpserver.HttpExchange;

import enrollease.db.AccountDAO;
import enrollease.db.DatabaseManager;
import enrollease.db.CredentialDAO;
import enrollease.db.EnrollmentDAO;
import enrollease.ds.MyStack;
import enrollease.model.Admin;
import enrollease.model.EnrollmentSlip;
import enrollease.model.Person;
import enrollease.model.QueueManager;
import enrollease.model.Schedule;
import enrollease.model.SiteContent;
import enrollease.model.Staff;
import enrollease.model.Student;
import enrollease.model.Subject;
import enrollease.model.SubjectComparators;
import enrollease.model.Ticket;
import enrollease.model.Timetable;
import enrollease.util.SortUtil;
import enrollease.util.Validator;

/**
 * The "controller" of the web version. The browser calls addresses such as
 * /api/login or /api/subjects; this class runs the matching Java code and answers with JSON.
 * (It does the same jobs as MainController, EnrollmentController and AdminController
 * of the console version, reusing the same models, data structures and DAOs.)
 */
public class Api {

    private AccountDAO accountDao = new AccountDAO();
    private Catalog catalog = new Catalog();
    private QueueManager queueManager = new QueueManager();
    private EnrollmentDAO enrollmentDao = new EnrollmentDAO();
    private CredentialDAO credentialDao = new CredentialDAO();
    private StudentProfileDAO profileDao = new StudentProfileDAO();
    private AcademicDAO academicDao = new AcademicDAO();
    private MyStack<Person> deletedStack = new MyStack<Person>();      // for "Undo last delete"
    private Map<String, Session> sessions = new HashMap<String, Session>();

    // Static nested class: who is logged in for one browser.
    private static class Session {
        private Person user;
        private StudentSession student; // only for students
    }

    // Anonymous inner classes used to sort accounts.
    private static final Comparator<Person> BY_ID = new Comparator<Person>() {
        @Override
        public int compare(Person a, Person b) {
            return a.getId().compareToIgnoreCase(b.getId());
        }
    };
    private static final Comparator<Person> BY_NAME = new Comparator<Person>() {
        @Override
        public int compare(Person a, Person b) {
            return a.getName().compareToIgnoreCase(b.getName());
        }
    };

    private static final List<String> STUDENT_KEYS = Arrays.asList(
        "GET /api/application/status", "GET /api/subjects", "GET /api/subjects/search", "POST /api/select", "POST /api/remove",
        "GET /api/selected", "POST /api/submit", "GET /api/queue", "POST /api/queue/next",
        "POST /api/queue/cashier", "GET /api/slips", "GET /api/credentials", "POST /api/credentials/upload");
    private static final List<String> ENROLLMENT_KEYS = Arrays.asList(
        "GET /api/subjects", "GET /api/subjects/search", "POST /api/select", "POST /api/remove",
        "GET /api/selected", "POST /api/submit");

    // =====================================================================
    //  Entry point: called by WebApp for every /api/... request
    // =====================================================================

    public void handle(HttpExchange exchange) throws IOException {
        Map<String, String> p = readParams(exchange);
        Session session = sessions.get(getToken(exchange));
        Map<String, Object> result;
        try {
            result = route(exchange, p, session);
        } catch (RuntimeException e) {
            e.printStackTrace();
            result = fail("Server error: " + e.getMessage());
            result.put("_status", 500);
        }
        int status = 200;
        if (result.containsKey("_status")) {
            status = (Integer) result.remove("_status");
        }
        byte[] bytes = Json.toJson(result).getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private Map<String, Object> route(HttpExchange ex, Map<String, String> p, Session session) {
        String key = ex.getRequestMethod() + " " + ex.getRequestURI().getPath();

        switch (key) {   // pages anyone can use
            case "GET /api/me": return me(session);
            case "POST /api/login": return login(ex, p);
            case "POST /api/logout": return logout(ex);
            case "POST /api/register": return register(p);
            case "POST /api/contact": return contact(p);
            case "GET /api/services": return services(p);
            case "GET /api/calendar": return calendar(p);
            case "GET /api/programs": return ok("programs", academicDao.getPrograms());
            case "GET /api/curricula": return ok("curricula", academicDao.getCurricula(param(p, "programId")));
            default: break;
        }

        if (key.contains(" /api/staff/")) {
            if (session == null || !(session.user instanceof Staff)) return unauthorized();
            return staffRoute(key, p, (Staff) session.user);
        }

        if (key.contains(" /api/admin/")) {
            if (session == null || !(session.user instanceof Admin)) {
                return unauthorized();
            }
            return adminRoute(key, p);
        }

        if (STUDENT_KEYS.contains(key)) {
            if (session == null || session.student == null) {
                return unauthorized();
            }
            if (ENROLLMENT_KEYS.contains(key) && !profileDao.canEnroll(session.student.getStudent().getId())) {
                Map<String, Object> locked = fail("Enrollment is locked until your requirements and academic profile are approved by the registrar.");
                locked.put("locked", true);
                return locked;
            }
            return studentRoute(key, p, session.student);
        }

        Map<String, Object> notFound = fail("Unknown address: " + key);
        notFound.put("_status", 404);
        return notFound;
    }

    // =====================================================================
    //  Public pages: login, register, contact, services, calendar
    // =====================================================================

    private Map<String, Object> me(Session session) {
        if (session == null) {
            return Json.map("loggedIn", false);
        }
        String role = (session.user instanceof Admin) ? "admin" : (session.user instanceof Staff ? "staff" : "student");
        return Json.map("loggedIn", true, "role", role, "name", session.user.getName(), "id", session.user.getId(),
                "position", session.user instanceof Staff ? session.user.getExtra() : "");
    }

    private Map<String, Object> login(HttpExchange ex, Map<String, String> p) {
        String id = param(p, "id").trim();
        String password = param(p, "password");
        if (id.isEmpty() || password.isEmpty()) {
            return fail("Please fill in all fields.");
        }
        Session session = new Session();
        String adminUsername = System.getenv("ADMIN_USERNAME");
        if (adminUsername == null || adminUsername.trim().isEmpty()) adminUsername = "admin";
        String adminPassword = System.getenv("ADMIN_PASSWORD");
        boolean validAdmin = adminUsername.equals(id)
                && ((adminPassword != null && !adminPassword.isEmpty() && password.equals(adminPassword))
                    || (!DatabaseManager.isExternalDatabase() && (adminPassword == null || adminPassword.isEmpty())
                        && password.equals("admin")));
        if (validAdmin) {
            session.user = new Admin();
        } else {
            Student student = accountDao.login(id, password);
            if (student != null) {
                session.user = student;
                session.student = new StudentSession(student, catalog, queueManager, enrollmentDao);
            } else {
                Staff staff = accountDao.loginStaff(id, password);
                if (staff == null) return fail("Student ID, staff username, or password is incorrect.");
                session.user = staff;
            }
        }
        sessions.remove(getToken(ex));                       // forget any older login
        String token = UUID.randomUUID().toString();
        sessions.put(token, session);
        String cookie = "sid=" + token + "; Path=/; HttpOnly; SameSite=Strict";
        if (System.getenv("RENDER") != null) cookie += "; Secure";
        ex.getResponseHeaders().add("Set-Cookie", cookie);
        String role = session.user instanceof Admin ? "admin" : (session.user instanceof Staff ? "staff" : "student");
        return ok("role", role, "position", session.user instanceof Staff ? session.user.getExtra() : "", "name", session.user.getName());
    }

    private Map<String, Object> logout(HttpExchange ex) {
        sessions.remove(getToken(ex));
        ex.getResponseHeaders().add("Set-Cookie", "sid=; Path=/; Max-Age=0");
        return ok();
    }

    private Map<String, Object> register(Map<String, String> p) {
        String name = param(p, "name").trim();
        String email = param(p, "email").trim();
        String type = param(p, "type").trim();
        String password = param(p, "password");
        String programId = param(p, "programId").trim();
        String previousSchool = param(p, "previousSchool").trim();
        String previousProgram = param(p, "previousProgram").trim();
        if (Validator.isBlank(name) || !Validator.isValidEmail(email) || !Validator.isValidPassword(password)) {
            return fail("Enter your name, a valid email, and a password of at least 6 characters.");
        }
        if ("Continuing Student".equals(type)) {
            return fail("Continuing-student registration is temporarily unavailable until the registrar provides an official Student ID masterlist. Existing students can still log in with their current ID.");
        }
        if (!"New Student".equals(type) && !"Transferee".equals(type)) return fail("Choose a valid student classification.");
        if (accountDao.studentEmailExists(email)) return fail("That email is already registered.");
        if ("New Student".equals(type) && !academicDao.programExists(programId)) return fail("Choose a valid program.");
        if ("Transferee".equals(type) && (previousSchool.isEmpty() || previousProgram.isEmpty())) {
            return fail("Enter your previous school and previous program for academic evaluation.");
        }
        String newId = accountDao.nextStudentId();
        if (!accountDao.add(new Student(newId, name, email, type, password))) return fail("Could not create the account.");
        String status = "New Student".equals(type) ? "REQUIREMENTS_PENDING" : "ACADEMIC_EVALUATION";
        if (!profileDao.create(newId, type, status, programId, previousSchool, previousProgram)) {
            accountDao.delete(newId);
            return fail("Could not create the student application. Please try again.");
        }
        String process = "New Student".equals(type)
                ? "Please log in and submit your required documents. Enrollment opens after registrar approval."
                : "Your transferee application is awaiting academic evaluation. Please log in and submit your available documents.";
        return ok("message", "Account created! Your Student ID is " + newId + ". " + process);
    }

    private Map<String, Object> contact(Map<String, String> p) {
        ArrayList<String> bad = new ArrayList<String>();
        if (Validator.isBlank(param(p, "name"))) bad.add("cName");
        if (!Validator.isValidEmail(param(p, "email"))) bad.add("cEmail");
        if (Validator.isBlank(param(p, "topic"))) bad.add("cTopic");
        if (Validator.isBlank(param(p, "message"))) bad.add("cMessage");
        if (!param(p, "agree").equals("true")) bad.add("cAgree");
        if (bad.isEmpty()) {
            return ok("message", "This demo validates the message only; it does not save or send messages.");
        }
        Map<String, Object> result = fail("Please fix the highlighted fields.");
        result.put("fields", bad);
        return result;
    }

    private Map<String, Object> services(Map<String, String> p) {
        String category = param(p, "category");
        List<Object> list = new ArrayList<Object>();
        for (SiteContent.ServiceItem item : SiteContent.getServices()) {
            if (category.isEmpty() || category.equals("all") || category.equals(item.getCategory())) {
                String description = item.getDescription();
                // Keep the public demo descriptions aligned with the features that are implemented.
                if (item.getTitle().equals("Credential Upload")) description = "Students can submit PDF, PNG, and JPEG enrollment requirements up to 2 MB.";
                else if (item.getTitle().equals("Credential Verification")) description = "Registrar staff can review submitted documents, update verification status, and leave remarks.";
                else if (item.getTitle().equals("Schedule Checking")) description = "View sample schedules. Conflicting subjects are flagged before submission.";
                else if (item.getTitle().equals("Prerequisite Locking")) description = "The demo illustrates prerequisite locking using the seeded failed-subject example.";
                else if (item.getTitle().equals("Subject and Section Management")) description = "Administrators can add academic programs, curricula, subjects, and section offerings with lecture/laboratory units and capacity limits.";
                else if (item.getTitle().equals("Payment Queue Number")) description = "Submitting enrollment saves a slip and generates a demo queue number. Email alerts and online payment are not included.";
                list.add(Json.map("title", item.getTitle(), "description", description,
                                  "category", item.getCategory()));
            }
        }
        return ok("services", list);
    }

    private Map<String, Object> calendar(Map<String, String> p) {
        LocalDate today = LocalDate.now();
        YearMonth month;
        try {
            month = YearMonth.of(toInt(p.get("year"), today.getYear()), toInt(p.get("month"), today.getMonthValue()));
        } catch (DateTimeException e) {
            month = YearMonth.from(today);
        }
        int[][] grid = new int[6][7];                                  // 2D array: 6 weeks x 7 days
        int column = month.atDay(1).getDayOfWeek().getValue() % 7;     // Sunday = 0
        int row = 0;
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            grid[row][column] = day;
            column++;
            if (column == 7) {
                column = 0;
                row++;
            }
        }
        int todayNumber = YearMonth.from(today).equals(month) ? today.getDayOfMonth() : 0;
        String title = month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear();
        return ok("year", month.getYear(), "month", month.getMonthValue(), "title", title, "grid", grid,
                  "events", SiteContent.getEventDays(month.getYear(), month.getMonthValue()), "today", todayNumber);
    }

    // =====================================================================
    //  Student pages: enrollment and payment queue
    // =====================================================================

    private Map<String, Object> studentRoute(String key, Map<String, String> p, StudentSession s) {
        s.refreshAfterRejection();
        switch (key) {
            case "GET /api/application/status": return ok("application", profileDao.get(s.getStudent().getId()));
            case "GET /api/subjects": return subjects(s, p);
            case "GET /api/subjects/search": return searchSubject(s, param(p, "code"));
            case "POST /api/select": return change(s, s.select(param(p, "offeringId")), "added");
            case "POST /api/remove": return change(s, s.remove(param(p, "offeringId")), "removed");
            case "GET /api/selected": return selected(s);
            case "POST /api/submit": return submit(s);
            case "GET /api/queue": return queue(s);
            case "POST /api/queue/next": s.callNext(); return queue(s);
            case "POST /api/queue/cashier": s.startCashier(); return queue(s);
            case "GET /api/credentials": return studentCredentials(s);
            case "POST /api/credentials/upload": return uploadCredential(s, p);
            default: return slips(s);
        }
    }

    private Map<String, Object> subjects(StudentSession s, Map<String, String> p) {
        Map<String, Object> profile = profileDao.get(s.getStudent().getId());
        String programId = String.valueOf(profile.get("programId"));
        String curriculumId = String.valueOf(profile.get("curriculumId"));
        ArrayList<Subject> list = catalog.getOfferings(programId, curriculumId);
        List<Object> rows = new ArrayList<Object>();
        for (Subject sub : list) {
            rows.add(subjectMap(s, sub));
        }
        String notice = "";
        if (!s.getStudent().getFailedCodes().isEmpty()) {
            notice = "You failed " + String.join(", ", s.getStudent().getFailedCodes())
                   + ". Subjects that require it are locked, and it is offered for retake.";
        }
        return ok("subjects", rows, "notice", notice, "maxUnits", SiteContent.MAX_UNITS, "units", s.getUnits(),
                  "submitted", s.getTicket() != null, "programId", programId, "curriculumId", curriculumId,
                  "term", SiteContent.TERM + " | " + academicDao.getAcademicLabel(programId, curriculumId));
    }

    private Map<String, Object> subjectMap(StudentSession s, Subject sub) {
        String schedule = sub.getLectureSchedule() == null ? "" : "LEC " + sub.getLectureSchedule().toString();
        if (sub.getLabSchedule() != null) schedule += (schedule.isEmpty() ? "" : " | ") + "LAB " + sub.getLabSchedule().toString();
        String sectionStatus = sub.isFull() ? "Full" : (sub.isBelowMinimum() ? "Below minimum" : (sub.getMinimumStudents() > 0 ? "Minimum reached" : "Open"));
        return Json.map("offeringId", sub.getOfferingId(), "code", sub.getCode(), "title", sub.getTitle(),
                        "units", sub.getUnits(), "lectureUnits", sub.getLectureUnits(), "labUnits", sub.getLabUnits(),
                        "section", sub.getSection(), "schedule", schedule,
                        "minimumStudents", sub.getMinimumStudents(), "maximumStudents", sub.getMaximumStudents(),
                        "enrolledStudents", sub.getEnrolledStudents(), "slots", sub.getSlots(),
                        "sectionStatus", sectionStatus, "status", s.statusOf(sub));
    }

    private Map<String, Object> searchSubject(StudentSession s, String code) {
        Map<String, Object> profile = profileDao.get(s.getStudent().getId());
        ArrayList<Subject> offerings = catalog.getOfferings(String.valueOf(profile.get("programId")), String.valueOf(profile.get("curriculumId")));
        Subject sub = null;
        for (Subject offering : offerings) if (offering.getCode().equalsIgnoreCase(code.trim())) { sub = offering; break; }
        if (sub == null) return fail("Subject not found in your assigned curriculum.");
        return ok("subject", subjectMap(s, sub), "prerequisites", catalog.getGraph().getAllPrerequisites(sub.getCode()));
    }

    // Shared answer for select/remove: error text if it failed, otherwise success.
    private Map<String, Object> change(StudentSession s, String error, String verb) {
        if (error != null) {
            return fail(error);
        }
        return ok("message", "Subject " + verb + ". Units: " + s.getUnits() + " / " + SiteContent.MAX_UNITS,
                  "units", s.getUnits());
    }

    private Map<String, Object> selected(StudentSession s) {
        List<Object> rows = new ArrayList<Object>();
        for (Subject sub : s.getSelected()) {
            rows.add(subjectMap(s, sub));
        }
        // Timetable: only show the time rows that have a subject in them.
        List<Object> timetable = new ArrayList<Object>();
        for (int slot = 0; slot < Timetable.SLOTS_PER_DAY; slot++) {
            String[] cells = new String[Schedule.DAYS.length];
            boolean used = false;
            for (int day = 0; day < cells.length; day++) {
                String cell = s.getTimetable().getCell(day, slot);
                cells[day] = (cell == null) ? "" : cell;
                used = used || cell != null;
            }
            if (used) {
                int minutes = Timetable.slotStartMinute(slot);
                int hour12 = (minutes / 60) % 12 == 0 ? 12 : (minutes / 60) % 12;
                String time = hour12 + ":" + (minutes % 60 == 0 ? "00" : "30");
                timetable.add(Json.map("time", time, "cells", cells));
            }
        }
        return ok("subjects", rows, "units", s.getUnits(), "days", Schedule.DAYS, "timetable", timetable);
    }

    private Map<String, Object> submit(StudentSession s) {
        String error = s.submit();
        if (error != null) {
            return fail(error);
        }
        return ok("message", "Enrollment submitted. Queue number " + s.getTicket().getNumber() + " generated.",
                  "queueNumber", s.getTicket().getNumber());
    }

    private Map<String, Object> queue(StudentSession s) {
        Ticket t = s.getTicket();
        if (t == null) {
            return ok("submitted", false);
        }
        return ok("submitted", true, "myNumber", t.getNumber(),
                  "ahead", queueManager.getNumbersAhead(t),
                  "nowServing", queueManager.getNowServing().getNumber(),
                  "status", queueManager.getStatus(t), "message", queueManager.getMessage(t),
                  "cashierRunning", s.isCashierRunning());
    }

    private Map<String, Object> slips(StudentSession s) {
        List<Object> list = new ArrayList<Object>();
        for (EnrollmentSlip slip : s.getSavedSlips()) {
            List<Object> subs = new ArrayList<Object>();
            for (Subject sub : slip.getSubjects()) {
                String schedule = sub.getLectureSchedule() == null ? "" : "LEC " + sub.getLectureSchedule().toString();
                if (sub.getLabSchedule() != null) schedule += (schedule.isEmpty() ? "" : " | ") + "LAB " + sub.getLabSchedule().toString();
                subs.add(Json.map("code", sub.getCode(), "title", sub.getTitle(), "units", sub.getUnits(),
                                  "lectureUnits", sub.getLectureUnits(), "labUnits", sub.getLabUnits(),
                                  "section", sub.getSection(), "schedule", schedule));
            }
            list.add(Json.map("submittedAt", slip.getSubmittedAt(), "queueNumber", slip.getQueueNumber(),
                              "totalUnits", slip.getTotalUnits(), "status", slip.getStatus(),
                              "adminNotes", slip.getAdminNotes(), "subjects", subs));
        }
        return ok("slips", list);
    }

    // =====================================================================
    //  Admin page: user and staff maintenance
    // =====================================================================

    private Map<String, Object> adminRoute(String key, Map<String, String> p) {
        boolean students = !param(p, "role").equals("staff");
        switch (key) {
            case "GET /api/admin/accounts": return adminAccounts(p, students);
            case "POST /api/admin/add": return adminSave(p, students, null);
            case "POST /api/admin/update": return adminSave(p, students, param(p, "oldId"));
            case "POST /api/admin/delete": return adminDelete(p, students);
            case "POST /api/admin/undo": return adminUndo();
            case "GET /api/admin/enrollments": return adminEnrollments();
            case "POST /api/admin/enrollment/review": return updateEnrollmentReview(p);
            case "GET /api/admin/credentials": return adminCredentials();
            case "GET /api/admin/credentials/file": return credentialFile(p);
            case "POST /api/admin/credentials/review": return reviewCredential(p);
            case "GET /api/admin/applications": return ok("applications", profileDao.getApplications());
            case "POST /api/admin/application/review": return reviewApplication(p);
            case "GET /api/admin/academic/programs": return ok("programs", academicDao.getPrograms());
            case "POST /api/admin/academic/program": return addProgram(p);
            case "GET /api/admin/academic/curricula": return ok("curricula", academicDao.getCurricula(param(p, "programId")));
            case "POST /api/admin/academic/curriculum": return addCurriculum(p);
            case "GET /api/admin/academic/subjects": return ok("subjects", academicDao.getSubjectCatalog());
            case "POST /api/admin/academic/subject": return addAcademicSubject(p);
            case "GET /api/admin/academic/sections": return ok("sections", academicDao.getSections());
            case "POST /api/admin/academic/section": return addAcademicSection(p);
            case "POST /api/admin/academic/section/capacity": return updateAcademicSectionCapacity(p);
            default:
                Map<String, Object> notFound = fail("Unknown address: " + key);
                notFound.put("_status", 404);
                return notFound;
        }
    }

    private Map<String, Object> staffRoute(String key, Map<String, String> p, Staff staff) {
        String position = staff.getExtra();
        boolean registrar = "Registrar".equals(position) || "Administrator".equals(position);
        boolean clerk = "Clerk".equals(position);
        boolean cashier = "Cashier".equals(position) || "Administrator".equals(position);
        if ("GET /api/staff/me".equals(key)) return ok("name", staff.getName(), "position", position);
        if ("GET /api/staff/queue".equals(key) || "POST /api/staff/queue/next".equals(key)) {
            if (!cashier) return unauthorized();
            if ("POST /api/staff/queue/next".equals(key)) queueManager.callNext();
            Ticket current = queueManager.getNowServing();
            return ok("nowServing", current == null ? 0 : current.getNumber());
        }
        if ("GET /api/staff/applications".equals(key)) {
            if (!registrar && !clerk) return unauthorized();
            return ok("applications", profileDao.getApplications());
        }
        if ("POST /api/staff/application/review".equals(key)) {
            if (!registrar) return unauthorized();
            return reviewApplication(p);
        }
        if ("GET /api/staff/enrollments".equals(key)) {
            if (!registrar && !clerk) return unauthorized();
            return adminEnrollments();
        }
        if ("POST /api/staff/enrollment/review".equals(key)) {
            if (!registrar) return unauthorized();
            return updateEnrollmentReview(p);
        }
        if ("GET /api/staff/credentials".equals(key)) {
            if (!registrar && !clerk) return unauthorized();
            return adminCredentials();
        }
        if ("GET /api/staff/credentials/file".equals(key)) {
            if (!registrar && !clerk) return unauthorized();
            return credentialFile(p);
        }
        if ("POST /api/staff/credentials/review".equals(key)) {
            if (!registrar) return unauthorized();
            return reviewCredential(p);
        }
        Map<String, Object> missing = fail("Unknown staff address: " + key);
        missing.put("_status", 404);
        return missing;
    }

    private Map<String, Object> adminEnrollments() {
        return ok("records", enrollmentDao.getAllForAdmin());
    }

    private Map<String, Object> updateEnrollmentReview(Map<String, String> p) {
        String id = param(p, "id").trim();
        String status = param(p, "status").trim();
        String notes = param(p, "notes").trim();
        if (id.isEmpty()) return fail("Enrollment record ID is required.");
        if (!Arrays.asList("Pending Review", "Requirements Incomplete", "Approved", "Rejected").contains(status)) {
            return fail("Choose a valid enrollment status.");
        }
        if (notes.length() > 1000) return fail("Remarks must be 1,000 characters or fewer.");
        if (!enrollmentDao.updateReview(id, status, notes)) return fail("Could not update the enrollment record.");
        return ok("message", "Enrollment status updated to " + status + ".");
    }

    private Map<String, Object> studentCredentials(StudentSession session) {
        return ok("credentials", credentialDao.getByStudent(session.getStudent().getId()));
    }

    private Map<String, Object> uploadCredential(StudentSession session, Map<String, String> p) {
        String type = param(p, "type").trim();
        String fileName = param(p, "fileName").trim();
        String contentType = param(p, "contentType").trim().toLowerCase(Locale.ENGLISH);
        String data = param(p, "data").trim();
        if (!Arrays.asList("School ID", "Report Card", "Transcript", "Other").contains(type)) {
            return fail("Choose a valid credential type.");
        }
        if (fileName.isEmpty() || data.isEmpty()) return fail("Choose a document to upload.");
        fileName = fileName.replace('\\', '/');
        if (fileName.contains("/")) fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        if (fileName.length() > 160) fileName = fileName.substring(fileName.length() - 160);
        boolean supported = "application/pdf".equals(contentType) || "image/png".equals(contentType)
                || "image/jpeg".equals(contentType);
        if (!supported) return fail("Only PDF, PNG, and JPG/JPEG files are supported.");
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            return fail("The selected document could not be read. Please choose it again.");
        }
        if (decoded.length == 0) return fail("The selected document is empty.");
        if (decoded.length > 2 * 1024 * 1024) return fail("The document must be 2 MB or smaller.");
        if (!credentialDao.add(session.getStudent().getId(), session.getStudent().getName(), type,
                fileName, contentType, data)) {
            return fail("Could not save the document. Please try again.");
        }
        return ok("message", "Credential submitted for registrar review.");
    }

    private Map<String, Object> adminCredentials() {
        return ok("credentials", credentialDao.getAllForAdmin());
    }

    private Map<String, Object> credentialFile(Map<String, String> p) {
        String id = param(p, "id").trim();
        if (id.isEmpty()) return fail("Credential ID is required.");
        return credentialDao.getFile(id);
    }

    private Map<String, Object> reviewCredential(Map<String, String> p) {
        String id = param(p, "id").trim();
        String status = param(p, "status").trim();
        String notes = param(p, "notes").trim();
        if (id.isEmpty()) return fail("Credential ID is required.");
        if (!Arrays.asList("Pending Review", "Verified", "Needs Resubmission", "Rejected").contains(status)) {
            return fail("Choose a valid credential status.");
        }
        if (notes.length() > 1000) return fail("Remarks must be 1,000 characters or fewer.");
        if (!credentialDao.review(id, status, notes)) return fail("Could not update the credential review.");
        return ok("message", "Credential review updated to " + status + ".");
    }

    private Map<String, Object> reviewApplication(Map<String, String> p) {
        String studentId = param(p, "studentId").trim();
        String status = param(p, "status").trim();
        String programId = param(p, "programId").trim();
        String curriculumId = param(p, "curriculumId").trim();
        String notes = param(p, "notes").trim();
        if (studentId.isEmpty()) return fail("Student ID is required.");
        if (!Arrays.asList("REQUIREMENTS_PENDING", "ACADEMIC_EVALUATION", "APPROVED", "REJECTED").contains(status)) return fail("Choose a valid application status.");
        if (notes.length() > 1000) return fail("Remarks must be 1,000 characters or fewer.");
        if ("APPROVED".equals(status)) {
            if (!academicDao.programExists(programId) || !academicDao.curriculumBelongsToProgram(curriculumId, programId)) return fail("Assign a valid program and matching curriculum before approval.");
            if (credentialDao.countVerifiedForStudent(studentId) < 1) return fail("At least one uploaded requirement must be marked Verified before approval. Confirm the complete official checklist manually.");
        }
        if (!profileDao.updateReview(studentId, status, programId, curriculumId, notes)) return fail("Could not update the student application.");
        return ok("message", "Student application updated to " + status + ".");
    }

    private Map<String, Object> addProgram(Map<String, String> p) {
        String code = param(p, "code").trim().toUpperCase();
        String name = param(p, "name").trim();
        if (code.isEmpty() || name.isEmpty()) return fail("Enter a program code and name.");
        if (!academicDao.addProgram(code, name)) return fail("Could not add the program. Check that its code is unique.");
        return ok("message", "Program added.");
    }

    private Map<String, Object> addCurriculum(Map<String, String> p) {
        String programId = param(p, "programId").trim();
        String name = param(p, "name").trim();
        String year = param(p, "effectiveYear").trim();
        if (!academicDao.programExists(programId) || name.isEmpty() || year.isEmpty()) return fail("Choose a program and enter a curriculum name and effective year.");
        if (!academicDao.addCurriculum(programId, name, year)) return fail("Could not add the curriculum.");
        return ok("message", "Curriculum added.");
    }

    private Map<String, Object> addAcademicSubject(Map<String, String> p) {
        String code = param(p, "code").trim().toUpperCase();
        String title = param(p, "title").trim();
        String prerequisites = param(p, "prerequisites").trim();
        if (code.isEmpty() || title.isEmpty()) return fail("Enter a subject code and title.");
        if (!academicDao.addSubject(code, title, prerequisites)) return fail("Could not add the subject. The subject code may already exist.");
        catalog.reload();
        return ok("message", "Subject added to the catalog. Add one or more section offerings next.");
    }

    private Map<String, Object> updateAcademicSectionCapacity(Map<String, String> p) {
        String id = param(p, "offeringId").trim();
        int minimum = toInt(p.get("minimum"), -1);
        int maximum = toInt(p.get("maximum"), -1);
        if (id.isEmpty() || minimum < 0 || maximum < 1 || minimum > maximum) return fail("Enter valid minimum and maximum student counts.");
        if (!academicDao.updateSectionCapacity(id, minimum, maximum)) return fail("Capacity was not updated. Maximum cannot be lower than current enrollment.");
        return ok("message", "Section minimum and maximum enrollment were updated.");
    }

    private Map<String, Object> addAcademicSection(Map<String, String> p) {
        String subjectCode = param(p, "subjectCode").trim().toUpperCase();
        String section = param(p, "section").trim();
        String programId = param(p, "programId").trim();
        String curriculumId = param(p, "curriculumId").trim();
        int lecUnits = toInt(p.get("lectureUnits"), -1);
        int labUnits = toInt(p.get("labUnits"), -1);
        int minimum = toInt(p.get("minimum"), -1);
        int maximum = toInt(p.get("maximum"), -1);
        String lecDay = param(p, "lectureDay");
        int lecStart = toMinutes(param(p, "lectureStart"));
        int lecEnd = toMinutes(param(p, "lectureEnd"));
        String labDay = param(p, "labDay");
        int labStart = toMinutes(param(p, "labStart"));
        int labEnd = toMinutes(param(p, "labEnd"));
        if (section.isEmpty() || !academicDao.programExists(programId) || !academicDao.curriculumBelongsToProgram(curriculumId, programId)) return fail("Choose a valid program and matching curriculum, and enter a section name.");
        if ((lecUnits > 0 && (lecStart < 0 || lecEnd <= lecStart)) || (labUnits > 0 && (labStart < 0 || labEnd <= labStart))) return fail("Enter valid start and end times for each class component with units.");
        boolean saved = academicDao.addSection(subjectCode, section, programId, curriculumId, lecUnits, labUnits,
                minimum, maximum, lecDay, lecStart, lecEnd, labDay, labStart, labEnd);
        if (!saved) return fail("Could not add section. Check the subject, units, minimum/maximum enrollment, and lecture/lab schedules.");
        catalog.reload();
        return ok("message", "Section offering added.");
    }

    private int toMinutes(String value) {
        if (value == null || value.trim().isEmpty()) return -1;
        try {
            String[] parts = value.trim().split(":");
            if (parts.length != 2) return -1;
            int h = Integer.parseInt(parts[0]), m = Integer.parseInt(parts[1]);
            if (h < 0 || h > 23 || (m != 0 && m != 30)) return -1;
            return h * 60 + m;
        } catch (RuntimeException e) { return -1; }
    }

    private Map<String, Object> adminAccounts(Map<String, String> p, boolean students) {
        ArrayList<Person> people = accountDao.getByRole(students ? "STUDENT" : "STAFF");
        String sort = param(p, "sort");
        if (sort.equals("id") || sort.equals("name")) {
            Person[] array = people.toArray(new Person[0]);
            SortUtil.insertionSort(array, sort.equals("id") ? BY_ID : BY_NAME);
            people = new ArrayList<Person>(Arrays.asList(array));
        }
        List<Object> records = new ArrayList<Object>();
        for (Person person : people) {
            records.add(Json.map("id", person.getId(), "name", person.getName(),
                                 "email", person.getEmail(), "extra", person.getExtra()));
        }
        return ok("records", records, "userCount", accountDao.count("STUDENT"),
                  "staffCount", accountDao.count("STAFF"), "undoCount", deletedStack.size());
    }

    // Adds a new record (oldId == null) or updates an existing one.
    private Map<String, Object> adminSave(Map<String, String> p, boolean students, String oldId) {
        String idLabel = students ? "Student ID" : "Username";
        String id = param(p, "id").trim();
        String name = param(p, "name").trim();
        String email = param(p, "email").trim();
        String extra = param(p, "extra");
        String password = param(p, "password");
        boolean adding = (oldId == null);

        Person old = null;
        if (!adding) {
            old = accountDao.findById(oldId);
            if (old == null || (old instanceof Student) != students) {
                return fail("That record no longer exists.");
            }
        }
        if (id.isEmpty() || name.isEmpty()) return fail("Please complete all fields.");
        if (!Validator.isValidEmail(email)) return fail("Enter a valid email address.");
        if (adding && password.isEmpty()) return fail("Please enter a password.");
        if (accountDao.idExists(id, students ? "STUDENT" : "STAFF", oldId)) return fail(idLabel + " already exists.");
        if (!Arrays.asList(students ? SiteContent.STUDENT_TYPES : SiteContent.STAFF_POSITIONS).contains(extra)) {
            return fail("Please choose a valid " + (students ? "student type." : "position."));
        }
        if (!adding && password.isEmpty()) {
            password = old.getPassword();                    // blank = keep current password
        }

        Person person;
        if (students) {
            ArrayList<String> failed = (old instanceof Student) ? ((Student) old).getFailedCodes() : new ArrayList<String>();
            person = new Student(id, name, email, extra, password, failed);
        } else {
            person = new Staff(id, name, email, extra, password);
        }
        boolean saved = adding ? accountDao.add(person) : accountDao.update(oldId, person);
        if (!saved) {
            return fail("Could not save the record.");
        }
        if (students && adding) {
            String applicationStatus = "Continuing Student".equals(extra) ? "APPROVED"
                    : ("Transferee".equals(extra) ? "ACADEMIC_EVALUATION" : "REQUIREMENTS_PENDING");
            String programId = "BSIT";
            if (!profileDao.create(id, extra, applicationStatus, programId, "", "")) {
                accountDao.delete(id);
                return fail("The account could not be linked to its student application profile.");
            }
            if ("Continuing Student".equals(extra)) {
                profileDao.updateReview(id, "APPROVED", "BSIT", "BSIT-2026", "Added by administrator using the existing registrar record.");
            }
        } else if (students) {
            if (!oldId.equals(id) && !profileDao.renameStudentId(oldId, id)) {
                return fail("The account ID was updated, but linked enrollment records need administrator review.");
            }
            profileDao.updateType(id, extra);
        }
        String what = students ? "User" : "Staff";
        return ok("message", what + " record " + (adding ? "added." : "updated."));
    }

    private Map<String, Object> adminDelete(Map<String, String> p, boolean students) {
        Person person = accountDao.findById(param(p, "id"));
        if (person == null || (person instanceof Student) != students) {
            return fail("No such record.");
        }
        if (!accountDao.delete(person.getId())) {
            return fail("Could not delete the record.");
        }
        deletedStack.push(person);                           // Stack: remember it for Undo
        return ok("message", (students ? "User" : "Staff") + " record deleted.");
    }

    private Map<String, Object> adminUndo() {
        Person person = deletedStack.pop();                  // last deleted comes back first
        if (person == null) {
            return fail("Nothing to undo.");
        }
        if (!accountDao.add(person)) {
            return fail("Could not restore the record (ID may already exist).");
        }
        return ok("message", "Restored " + person.describe());
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private Map<String, Object> ok(Object... keysAndValues) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("ok", true);
        result.putAll(Json.map(keysAndValues));
        return result;
    }

    private Map<String, Object> fail(String message) {
        return Json.map("ok", false, "message", message);
    }

    private Map<String, Object> unauthorized() {
        Map<String, Object> result = fail("Please log in first.");
        result.put("auth", false);
        result.put("_status", 401);
        return result;
    }

    private String param(Map<String, String> p, String name) {
        String value = p.get(name);
        return value == null ? "" : value;
    }

    private int toInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (RuntimeException e) {   // null text or not a number
            return fallback;
        }
    }

    private String getToken(HttpExchange ex) {
        String cookies = ex.getRequestHeaders().getFirst("Cookie");
        if (cookies != null) {
            for (String part : cookies.split(";")) {
                part = part.trim();
                if (part.startsWith("sid=")) {
                    return part.substring(4);
                }
            }
        }
        return "";
    }

    // Reads ?a=1&b=2 from the address and the same format from a POST body.
    private Map<String, String> readParams(HttpExchange ex) throws IOException {
        Map<String, String> params = new HashMap<String, String>();
        parseInto(ex.getRequestURI().getRawQuery(), params);
        if (ex.getRequestMethod().equals("POST")) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            InputStream in = ex.getRequestBody();
            byte[] chunk = new byte[1024];
            int count;
            while ((count = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, count);
            }
            parseInto(buffer.toString("UTF-8"), params);
        }
        return params;
    }

    private void parseInto(String text, Map<String, String> params) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (String pair : text.split("&")) {
            String[] parts = pair.split("=", 2);
            try {
                String key = URLDecoder.decode(parts[0], "UTF-8");
                String value = parts.length > 1 ? URLDecoder.decode(parts[1], "UTF-8") : "";
                params.put(key, value);
            } catch (UnsupportedEncodingException e) {
                // UTF-8 always exists, so this never happens
            }
        }
    }
}
