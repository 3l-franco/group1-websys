package enrollease.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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
                    "INSERT INTO enrollment_slip_subjects (slip_id, subject_index, subject_code, title, units, section, day, start_min, end_min, slots) VALUES (?,?,?,?,?,?,?,?,?,?)")) {
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
        String sql = "SELECT id, student_id, student_name, queue_number, submitted_at "
                   + "FROM enrollment_slips WHERE student_id=? ORDER BY created_at, id";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    slips.add(loadSlip(rs.getString("id"), rs.getString("student_id"),
                            rs.getString("student_name"), rs.getInt("queue_number"),
                            rs.getString("submitted_at")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Could not load enrollment slips: " + e.getMessage());
        }
        return slips;
    }

    private EnrollmentSlip loadSlip(String slipId, String studentId, String studentName,
                                    int queueNumber, String submittedAt) throws SQLException {
        ArrayList<Subject> subjects = new ArrayList<Subject>();
        String sql = "SELECT subject_code, title, units, section, day, start_min, end_min, slots "
                   + "FROM enrollment_slip_subjects WHERE slip_id=? ORDER BY subject_index";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, slipId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Schedule schedule = new Schedule(rs.getString("day"),
                            rs.getInt("start_min"), rs.getInt("end_min"));
                    subjects.add(new Subject(rs.getString("subject_code"), rs.getString("title"),
                            rs.getInt("units"), rs.getString("section"), schedule, rs.getInt("slots")));
                }
            }
        }
        return new EnrollmentSlip(studentId, studentName, subjects, queueNumber, submittedAt);
    }
}
