package enrollease.web;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import enrollease.db.DatabaseManager;
import enrollease.model.Person;
import enrollease.db.AccountDAO;

/** Persists new-student and transferee applications. Legacy accounts are not forced
 * through the new workflow while the continuing-student masterlist is unavailable. */
public class StudentProfileDAO {

    public boolean create(String studentId, String studentType, String status, String programId,
                          String previousSchool, String previousProgram) {
        String sql = "INSERT INTO student_profiles (student_id, student_type, status, program_id, curriculum_id, previous_school, previous_program, admin_notes, requirements_confirmed, created_at) VALUES (?,?,?,?,?,?,?,?,0,?)";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, studentId);
            ps.setString(2, studentType);
            ps.setString(3, status);
            ps.setString(4, programId == null ? "" : programId);
            ps.setString(5, "");
            ps.setString(6, previousSchool == null ? "" : previousSchool);
            ps.setString(7, previousProgram == null ? "" : previousProgram);
            ps.setString(8, "");
            ps.setLong(9, System.currentTimeMillis());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            System.err.println("Could not create student profile: " + e.getMessage());
            return false;
        }
    }

    /** One-time, additive migration for existing accounts created before applications existed.
     * Only Continuing Student accounts retain the legacy path. New Students and Transferees
     * are placed into the appropriate review queue rather than bypassing document checks. */
    public void migrateLegacyStudentAccounts() {
        AccountDAO accounts = new AccountDAO();
        for (Person person : accounts.getByRole("STUDENT")) {
            if ("Continuing Student".equals(person.getExtra()) || profileExists(person.getId())) continue;
            boolean transferee = "Transferee".equals(person.getExtra());
            String status = transferee ? "ACADEMIC_EVALUATION" : "REQUIREMENTS_PENDING";
            String programId = transferee ? "" : "BSIT";
            create(person.getId(), person.getExtra(), status, programId, "", "");
        }
    }

    private boolean profileExists(String studentId) {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "SELECT 1 FROM student_profiles WHERE student_id=?")) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            System.err.println("Could not check existing student profile: " + e.getMessage());
            return true;
        }
    }

    /** Only pre-existing Continuing Student accounts retain the legacy enrollment path. */
    public boolean canEnroll(String studentId) {
        String sql = "SELECT status, program_id, curriculum_id, requirements_confirmed FROM student_profiles WHERE student_id=?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    Person person = new AccountDAO().findById(studentId);
                    return person != null && "Continuing Student".equals(person.getExtra());
                }
                return "APPROVED".equals(rs.getString("status"))
                        && rs.getInt("requirements_confirmed") == 1
                        && !empty(rs.getString("program_id")) && !empty(rs.getString("curriculum_id"));
            }
        } catch (SQLException e) {
            System.err.println("Could not check enrollment eligibility: " + e.getMessage());
            return false;
        }
    }

    public Map<String, Object> get(String studentId) {
        String sql = "SELECT student_type, status, program_id, curriculum_id, previous_school, previous_program, admin_notes, requirements_confirmed FROM student_profiles WHERE student_id=?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Json.map("studentType", rs.getString("student_type"),
                            "status", rs.getString("status"), "programId", rs.getString("program_id"),
                            "curriculumId", rs.getString("curriculum_id"),
                            "previousSchool", rs.getString("previous_school"),
                            "previousProgram", rs.getString("previous_program"),
                            "adminNotes", rs.getString("admin_notes"),
                            "requirementsConfirmed", rs.getInt("requirements_confirmed") == 1);
                }
            }
        } catch (SQLException e) {
            System.err.println("Could not load student profile: " + e.getMessage());
        }

        // Only pre-existing Continuing Student accounts keep their legacy enrollment access.
        // Other older classifications remain pending if profile migration could not complete.
        Person person = new AccountDAO().findById(studentId);
        String type = person == null ? "" : person.getExtra();
        if ("Continuing Student".equals(type)) {
            return Json.map("studentType", type, "status", "APPROVED",
                    "programId", "BSIT", "curriculumId", "BSIT-2026",
                    "previousSchool", "", "previousProgram", "", "adminNotes", "",
                    "requirementsConfirmed", true, "legacy", true);
        }
        String status = "Transferee".equals(type) ? "ACADEMIC_EVALUATION" : "REQUIREMENTS_PENDING";
        return Json.map("studentType", type, "status", status,
                "programId", "Transferee".equals(type) ? "" : "BSIT", "curriculumId", "",
                "previousSchool", "", "previousProgram", "", "adminNotes", "",
                "requirementsConfirmed", false);
    }

    public ArrayList<Map<String, Object>> getApplications() {
        ArrayList<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        String sql = "SELECT p.student_id, p.student_type, p.status, p.program_id, p.curriculum_id, p.previous_school, p.previous_program, p.admin_notes, p.requirements_confirmed, a.name, a.email "
                   + "FROM student_profiles p JOIN accounts a ON a.id=p.student_id ORDER BY p.created_at DESC, p.student_id";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("studentId", rs.getString("student_id"));
                row.put("studentName", rs.getString("name"));
                row.put("email", rs.getString("email"));
                row.put("studentType", rs.getString("student_type"));
                row.put("status", rs.getString("status"));
                row.put("programId", rs.getString("program_id"));
                row.put("curriculumId", rs.getString("curriculum_id"));
                row.put("previousSchool", rs.getString("previous_school"));
                row.put("previousProgram", rs.getString("previous_program"));
                row.put("adminNotes", rs.getString("admin_notes"));
                row.put("requirementsConfirmed", rs.getInt("requirements_confirmed") == 1);
                rows.add(row);
            }
        } catch (SQLException e) {
            System.err.println("Could not load student applications: " + e.getMessage());
        }
        return rows;
    }

    public boolean updateReview(String studentId, String status, String programId, String curriculumId,
                                String notes, boolean requirementsConfirmed) {
        String sql = "UPDATE student_profiles SET status=?, program_id=?, curriculum_id=?, admin_notes=?, requirements_confirmed=? WHERE student_id=?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, programId == null ? "" : programId);
            ps.setString(3, curriculumId == null ? "" : curriculumId);
            ps.setString(4, notes == null ? "" : notes.trim());
            ps.setInt(5, requirementsConfirmed ? 1 : 0);
            ps.setString(6, studentId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            System.err.println("Could not update student application: " + e.getMessage());
            return false;
        }
    }

    /** Compatibility overload for trusted administrator maintenance actions. */
    public boolean updateReview(String studentId, String status, String programId, String curriculumId, String notes) {
        return updateReview(studentId, status, programId, curriculumId, notes, "APPROVED".equals(status));
    }

    /** Keeps profile and historical records linked if an administrator corrects a Student ID. */
    public boolean renameStudentId(String oldId, String newId) {
        String[] tables = { "student_profiles", "enrollment_slips", "student_credentials" };
        try {
            for (String table : tables) {
                try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                        "UPDATE " + table + " SET student_id=? WHERE student_id=?")) {
                    ps.setString(1, newId);
                    ps.setString(2, oldId);
                    ps.executeUpdate();
                }
            }
            return true;
        } catch (SQLException e) {
            System.err.println("Could not rename linked student records: " + e.getMessage());
            return false;
        }
    }

    /** Updating a classification does not enroll a student in the new-application path. */
    public boolean updateType(String studentId, String studentType) {
        String sql = "UPDATE student_profiles SET student_type=?, "
                + "status=CASE WHEN ?='Continuing Student' THEN 'APPROVED' ELSE status END, "
                + "program_id=CASE WHEN ?='Continuing Student' AND program_id='' THEN 'BSIT' ELSE program_id END, "
                + "curriculum_id=CASE WHEN ?='Continuing Student' AND curriculum_id='' THEN 'BSIT-2026' ELSE curriculum_id END, "
                + "requirements_confirmed=CASE WHEN ?='Continuing Student' THEN 1 ELSE requirements_confirmed END "
                + "WHERE student_id=?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, studentType);
            ps.setString(2, studentType);
            ps.setString(3, studentType);
            ps.setString(4, studentType);
            ps.setString(5, studentType);
            ps.setString(6, studentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Could not update student type: " + e.getMessage());
            return false;
        }
    }

    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
