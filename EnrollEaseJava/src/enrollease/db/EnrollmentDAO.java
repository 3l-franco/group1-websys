package enrollease.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import enrollease.model.EnrollmentSlip;
import enrollease.model.Schedule;
import enrollease.model.Subject;

/** Persists enrollment slips and their subject snapshots in the configured database. */
public class EnrollmentDAO {

    /** Saves a complete slip in one transaction. */
    public synchronized boolean add(EnrollmentSlip slip) {
        Connection connection = null;
        boolean oldAutoCommit = true;
        String id = UUID.randomUUID().toString();
        try {
            connection = DatabaseManager.getConnection();
            oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            for (Subject subject : slip.getSubjects()) {
                if (subject.getOfferingId() != null && !subject.getOfferingId().isEmpty()) {
                    try (PreparedStatement seat = connection.prepareStatement(
                            "UPDATE course_sections SET enrolled_students=enrolled_students+1 WHERE offering_id=? AND active=1 AND enrolled_students < max_students")) {
                        seat.setString(1, subject.getOfferingId());
                        if (seat.executeUpdate() != 1) throw new SQLException("Section " + subject.getCode() + " " + subject.getSection() + " is full or no longer available.");
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO enrollment_slips (id, student_id, student_name, total_units, queue_number, submitted_at, created_at) VALUES (?,?,?,?,?,?,?)")) {
                ps.setString(1, id);
                ps.setString(2, slip.getStudentId());
                ps.setString(3, slip.getStudentName());
                ps.setInt(4, slip.getTotalUnits());
                ps.setInt(5, slip.getQueueNumber());
                ps.setString(6, slip.getSubmittedAt());
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO enrollment_slip_subjects (slip_id, subject_index, subject_code, title, units, section, day, start_min, end_min, slots, offering_id, lecture_units, lab_units, lab_day, lab_start_min, lab_end_min) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                int index = 0;
                for (Subject subject : slip.getSubjects()) {
                    ps.setString(1, id);
                    ps.setInt(2, index++);
                    ps.setString(3, subject.getCode());
                    ps.setString(4, subject.getTitle());
                    ps.setInt(5, subject.getUnits());
                    ps.setString(6, subject.getSection());
                    ps.setString(7, subject.getSchedule().getDay());
                    ps.setInt(8, subject.getSchedule().getStartMinute());
                    ps.setInt(9, subject.getSchedule().getEndMinute());
                    ps.setInt(10, subject.getSlots());
                    ps.setString(11, subject.getOfferingId());
                    ps.setInt(12, subject.getLectureUnits());
                    ps.setInt(13, subject.getLabUnits());
                    Schedule lab = subject.getLabSchedule();
                    ps.setString(14, lab == null ? "" : lab.getDay());
                    ps.setInt(15, lab == null ? 0 : lab.getStartMinute());
                    ps.setInt(16, lab == null ? 0 : lab.getEndMinute());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
            return true;
        } catch (SQLException e) {
            if (connection != null) {
                try { connection.rollback(); } catch (SQLException ignored) { }
            }
            System.err.println("Could not save enrollment slip: " + e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                try { connection.setAutoCommit(oldAutoCommit); } catch (SQLException ignored) { }
            }
        }
    }

    /** Loads a student's saved slips in creation order. */
    public ArrayList<EnrollmentSlip> getByStudentId(String studentId) {
        ArrayList<EnrollmentSlip> slips = new ArrayList<EnrollmentSlip>();
        String sql = "SELECT id, student_id, student_name, queue_number, submitted_at, status, admin_notes "
                   + "FROM enrollment_slips WHERE student_id=? ORDER BY created_at, id";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    slips.add(loadSlip(rs.getString("id"), rs.getString("student_id"),
                            rs.getString("student_name"), rs.getInt("queue_number"),
                            rs.getString("submitted_at"), rs.getString("status"), rs.getString("admin_notes")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Could not load enrollment slips: " + e.getMessage());
        }
        return slips;
    }

    private EnrollmentSlip loadSlip(String slipId, String studentId, String studentName,
                                    int queueNumber, String submittedAt, String status, String adminNotes) throws SQLException {
        ArrayList<Subject> subjects = new ArrayList<Subject>();
        String sql = "SELECT subject_code, title, units, section, day, start_min, end_min, slots, offering_id, lecture_units, lab_units, lab_day, lab_start_min, lab_end_min "
                   + "FROM enrollment_slip_subjects WHERE slip_id=? ORDER BY subject_index";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, slipId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Schedule schedule = new Schedule(rs.getString("day"),
                            rs.getInt("start_min"), rs.getInt("end_min"));
                    String offeringId = rs.getString("offering_id");
                    String labDay = rs.getString("lab_day");
                    Schedule lab = labDay == null || labDay.isEmpty() ? null
                            : new Schedule(labDay, rs.getInt("lab_start_min"), rs.getInt("lab_end_min"));
                    if (offeringId == null || offeringId.isEmpty()) {
                        subjects.add(new Subject(rs.getString("subject_code"), rs.getString("title"),
                                rs.getInt("units"), rs.getString("section"), schedule, rs.getInt("slots")));
                    } else {
                        subjects.add(new Subject(offeringId, rs.getString("subject_code"), rs.getString("title"),
                                rs.getInt("lecture_units"), rs.getInt("lab_units"), rs.getString("section"),
                                schedule, lab, 0, rs.getInt("slots"), 0));
                    }
                }
            }
        }
        return new EnrollmentSlip(studentId, studentName, subjects, queueNumber, submittedAt, status, adminNotes);
    }

    /** Loads all submitted enrollments for the registrar/admin dashboard. */
    public synchronized ArrayList<Map<String, Object>> getAllForAdmin() {
        ArrayList<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
        String sql = "SELECT id, student_id, student_name, total_units, queue_number, submitted_at, status, admin_notes "
                   + "FROM enrollment_slips ORDER BY created_at DESC, id DESC";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", rs.getString("id"));
                    row.put("studentId", rs.getString("student_id"));
                    row.put("studentName", rs.getString("student_name"));
                    row.put("totalUnits", rs.getInt("total_units"));
                    row.put("queueNumber", rs.getInt("queue_number"));
                    row.put("submittedAt", rs.getString("submitted_at"));
                    row.put("status", rs.getString("status"));
                    row.put("adminNotes", rs.getString("admin_notes"));
                    records.add(row);
                }
            }
            for (Map<String, Object> row : records) {
                row.put("subjects", loadAdminSubjects((String) row.get("id")));
            }
        } catch (SQLException e) {
            System.err.println("Could not load admin enrollment records: " + e.getMessage());
        }
        return records;
    }

    private ArrayList<Map<String, Object>> loadAdminSubjects(String slipId) throws SQLException {
        ArrayList<Map<String, Object>> subjects = new ArrayList<Map<String, Object>>();
        String sql = "SELECT subject_code, title, units, section, day, start_min, end_min, lecture_units, lab_units, lab_day, lab_start_min, lab_end_min "
                   + "FROM enrollment_slip_subjects WHERE slip_id=? ORDER BY subject_index";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, slipId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Schedule schedule = new Schedule(rs.getString("day"), rs.getInt("start_min"), rs.getInt("end_min"));
                    Map<String, Object> subject = new LinkedHashMap<String, Object>();
                    subject.put("code", rs.getString("subject_code"));
                    subject.put("title", rs.getString("title"));
                    subject.put("units", rs.getInt("units"));
                    subject.put("section", rs.getString("section"));
                    int lectureUnits = rs.getInt("lecture_units");
                    int labUnits = rs.getInt("lab_units");
                    if (lectureUnits + labUnits == 0) lectureUnits = rs.getInt("units");
                    subject.put("lectureUnits", lectureUnits);
                    subject.put("labUnits", labUnits);
                    String labDay = rs.getString("lab_day");
                    Schedule lab = labDay == null || labDay.isEmpty() ? null : new Schedule(labDay, rs.getInt("lab_start_min"), rs.getInt("lab_end_min"));
                    subject.put("schedule", schedule.toString() + (lab == null ? "" : " | LAB " + lab.toString()));
                    subjects.add(subject);
                }
            }
        }
        return subjects;
    }

    /** Updates the registrar's review decision and internal remarks. */
    public synchronized boolean updateReview(String id, String status, String notes) {
        Connection connection = null;
        boolean oldAutoCommit = true;
        try {
            connection = DatabaseManager.getConnection();
            oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            String oldStatus = "";
            try (PreparedStatement find = connection.prepareStatement("SELECT status FROM enrollment_slips WHERE id=?")) {
                find.setString(1, id);
                try (ResultSet rs = find.executeQuery()) { if (!rs.next()) { connection.rollback(); return false; } oldStatus = rs.getString(1); }
            }
            if (!"Rejected".equals(status) && "Rejected".equals(oldStatus)) {
                int expected = 0;
                try (PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) FROM enrollment_slip_subjects WHERE slip_id=? AND offering_id<>''")) {
                    count.setString(1, id);
                    try (ResultSet rs = count.executeQuery()) { if (rs.next()) expected = rs.getInt(1); }
                }
                try (PreparedStatement reserve = connection.prepareStatement(
                        "UPDATE course_sections SET enrolled_students=enrolled_students+1 WHERE offering_id IN (SELECT offering_id FROM enrollment_slip_subjects WHERE slip_id=? AND offering_id<>'') AND active=1 AND enrolled_students < max_students")) {
                    reserve.setString(1, id);
                    if (reserve.executeUpdate() != expected) { connection.rollback(); return false; }
                }
            } else if ("Rejected".equals(status) && !"Rejected".equals(oldStatus)) {
                try (PreparedStatement release = connection.prepareStatement(
                        "UPDATE course_sections SET enrolled_students=CASE WHEN enrolled_students>0 THEN enrolled_students-1 ELSE 0 END WHERE offering_id IN (SELECT offering_id FROM enrollment_slip_subjects WHERE slip_id=? AND offering_id<>'')")) {
                    release.setString(1, id);
                    release.executeUpdate();
                }
            }
            try (PreparedStatement ps = connection.prepareStatement("UPDATE enrollment_slips SET status=?, admin_notes=? WHERE id=?")) {
                ps.setString(1, status);
                ps.setString(2, notes == null ? "" : notes.trim());
                ps.setString(3, id);
                if (ps.executeUpdate() != 1) { connection.rollback(); return false; }
            }
            connection.commit();
            return true;
        } catch (SQLException e) {
            if (connection != null) try { connection.rollback(); } catch (SQLException ignored) { }
            System.err.println("Could not update enrollment review: " + e.getMessage());
            return false;
        } finally {
            if (connection != null) try { connection.setAutoCommit(oldAutoCommit); } catch (SQLException ignored) { }
        }
    }
}
