package enrollease.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import enrollease.model.Schedule;
import enrollease.model.Subject;

/** Academic programs, curricula, subject catalog, and individual section offerings. */
public class AcademicDAO {

    public ArrayList<Map<String, Object>> getPrograms() {
        ArrayList<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        String sql = "SELECT program_id, code, name FROM academic_programs WHERE active=1 ORDER BY name";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("id", rs.getString("program_id"));
                row.put("code", rs.getString("code"));
                row.put("name", rs.getString("name"));
                rows.add(row);
            }
        } catch (SQLException e) { System.err.println("Could not load programs: " + e.getMessage()); }
        return rows;
    }

    public ArrayList<Map<String, Object>> getCurricula(String programId) {
        ArrayList<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        String sql = "SELECT curriculum_id, program_id, name, effective_year FROM curricula WHERE active=1 AND (?='' OR program_id=?) ORDER BY name";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, programId == null ? "" : programId);
            ps.setString(2, programId == null ? "" : programId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", rs.getString("curriculum_id"));
                    row.put("programId", rs.getString("program_id"));
                    row.put("name", rs.getString("name"));
                    row.put("effectiveYear", rs.getString("effective_year"));
                    rows.add(row);
                }
            }
        } catch (SQLException e) { System.err.println("Could not load curricula: " + e.getMessage()); }
        return rows;
    }

    public boolean programExists(String id) {
        return exists("SELECT 1 FROM academic_programs WHERE program_id=? AND active=1", id);
    }

    public boolean curriculumBelongsToProgram(String curriculumId, String programId) {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "SELECT 1 FROM curricula WHERE curriculum_id=? AND program_id=? AND active=1")) {
            ps.setString(1, curriculumId);
            ps.setString(2, programId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }

    private boolean exists(String sql, String value) {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }

    public boolean addProgram(String code, String name) {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "INSERT INTO academic_programs (program_id, code, name, active) VALUES (?,?,?,1)")) {
            ps.setString(1, code.trim().toUpperCase());
            ps.setString(2, code.trim().toUpperCase());
            ps.setString(3, name.trim());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { System.err.println("Add program failed: " + e.getMessage()); return false; }
    }

    public boolean addCurriculum(String programId, String name, String effectiveYear) {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "INSERT INTO curricula (curriculum_id, program_id, name, effective_year, active) VALUES (?,?,?,?,1)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, programId);
            ps.setString(3, name.trim());
            ps.setString(4, effectiveYear.trim());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { System.err.println("Add curriculum failed: " + e.getMessage()); return false; }
    }

    public boolean addSubject(String code, String title, String prerequisites) {
        int units = 0;
        try {
            SubjectDAO dao = new SubjectDAO();
            return dao.add(new Subject(code.trim().toUpperCase(), title.trim(), 1, "CATALOG",
                    new Schedule("Mon", 420, 450), 0), prerequisites == null ? "" : prerequisites.trim());
        } catch (RuntimeException e) {
            System.err.println("Add subject failed: " + e.getMessage());
            return false;
        }
    }

    public ArrayList<Map<String, Object>> getSubjectCatalog() {
        ArrayList<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "SELECT code, title FROM subjects ORDER BY code");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("code", rs.getString("code"));
                row.put("title", rs.getString("title"));
                rows.add(row);
            }
        } catch (SQLException e) { System.err.println("Could not load subject catalog: " + e.getMessage()); }
        return rows;
    }

    public boolean addSection(String subjectCode, String section, String programId, String curriculumId,
                              int lectureUnits, int labUnits, int minimumStudents, int maximumStudents,
                              String lectureDay, int lectureStart, int lectureEnd,
                              String labDay, int labStart, int labEnd) {
        if (maximumStudents < 1 || minimumStudents < 0 || minimumStudents > maximumStudents
                || lectureUnits < 0 || labUnits < 0 || lectureUnits + labUnits < 1) return false;
        if (new SubjectDAO().findById(subjectCode) == null) return false;
        if (sectionExists(subjectCode, section, programId, curriculumId)) return false;
        if (lectureUnits > 0 && labUnits > 0 && lectureDay != null && lectureDay.equalsIgnoreCase(labDay)
                && lectureStart < labEnd && labStart < lectureEnd) return false;
        String offeringId = UUID.randomUUID().toString();
        java.sql.Connection c = null;
        boolean oldAutoCommit = true;
        try {
            c = DatabaseManager.getConnection();
            oldAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO course_sections (offering_id, subject_code, section_code, program_id, curriculum_id, term, lecture_units, lab_units, min_students, max_students, enrolled_students, active) VALUES (?,?,?,?,?,?,?,?,?,?,0,1)")) {
                ps.setString(1, offeringId);
                ps.setString(2, subjectCode);
                ps.setString(3, section.trim());
                ps.setString(4, programId);
                ps.setString(5, curriculumId);
                ps.setString(6, "1st Semester, A.Y. 2026-2027");
                ps.setInt(7, lectureUnits);
                ps.setInt(8, labUnits);
                ps.setInt(9, minimumStudents);
                ps.setInt(10, maximumStudents);
                ps.executeUpdate();
            }
            if (lectureUnits > 0) addSchedule(c, offeringId, "LEC", lectureDay, lectureStart, lectureEnd);
            if (labUnits > 0) addSchedule(c, offeringId, "LAB", labDay, labStart, labEnd);
            c.commit();
            return true;
        } catch (SQLException e) {
            if (c != null) try { c.rollback(); } catch (SQLException ignored) { }
            System.err.println("Add section failed: " + e.getMessage());
            return false;
        } finally {
            if (c != null) try { c.setAutoCommit(oldAutoCommit); } catch (SQLException ignored) { }
        }
    }

    private boolean sectionExists(String subjectCode, String section, String programId, String curriculumId) {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "SELECT 1 FROM course_sections WHERE subject_code=? AND section_code=? AND program_id=? AND curriculum_id=? AND active=1")) {
            ps.setString(1, subjectCode); ps.setString(2, section.trim()); ps.setString(3, programId); ps.setString(4, curriculumId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return true; }
    }

    private void addSchedule(java.sql.Connection c, String id, String component, String day, int start, int end) throws SQLException {
        if (day == null || day.trim().isEmpty() || start < 0 || end <= start || end > 1440) {
            throw new SQLException("A valid " + component + " day and schedule are required.");
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO section_schedules (offering_id, component, day, start_min, end_min) VALUES (?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, component);
            ps.setString(3, day);
            ps.setInt(4, start);
            ps.setInt(5, end);
            ps.executeUpdate();
        }
    }

    public ArrayList<Subject> getOfferings(String programId, String curriculumId) {
        ArrayList<Subject> rows = new ArrayList<Subject>();
        String sql = "SELECT cs.offering_id, cs.subject_code, s.title, cs.section_code, cs.lecture_units, cs.lab_units, cs.min_students, cs.max_students, cs.enrolled_students "
                   + "FROM course_sections cs JOIN subjects s ON s.code=cs.subject_code "
                   + "WHERE cs.active=1 AND (?='' OR cs.program_id=?) AND (?='' OR cs.curriculum_id=?) "
                   + "ORDER BY cs.subject_code, cs.section_code";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, programId == null ? "" : programId);
            ps.setString(2, programId == null ? "" : programId);
            ps.setString(3, curriculumId == null ? "" : curriculumId);
            ps.setString(4, curriculumId == null ? "" : curriculumId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String offeringId = rs.getString("offering_id");
                    Schedule lec = getSchedule(offeringId, "LEC");
                    Schedule lab = getSchedule(offeringId, "LAB");
                    rows.add(new Subject(offeringId, rs.getString("subject_code"), rs.getString("title"),
                            rs.getInt("lecture_units"), rs.getInt("lab_units"), rs.getString("section_code"),
                            lec, lab, rs.getInt("min_students"), rs.getInt("max_students"), rs.getInt("enrolled_students")));
                }
            }
        } catch (SQLException e) { System.err.println("Could not load offerings: " + e.getMessage()); }
        return rows;
    }

    public Subject findOffering(String offeringId) {
        ArrayList<Subject> rows = getOfferings("", "");
        for (Subject subject : rows) if (subject.getOfferingId().equals(offeringId)) return subject;
        return null;
    }

    private Schedule getSchedule(String id, String component) {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "SELECT day, start_min, end_min FROM section_schedules WHERE offering_id=? AND component=?")) {
            ps.setString(1, id);
            ps.setString(2, component);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new Schedule(rs.getString("day"), rs.getInt("start_min"), rs.getInt("end_min"));
            }
        } catch (SQLException e) { System.err.println("Could not load section schedule: " + e.getMessage()); }
        return null;
    }

    public boolean updateSectionCapacity(String offeringId, int minimumStudents, int maximumStudents) {
        if (offeringId == null || offeringId.trim().isEmpty() || minimumStudents < 0 || maximumStudents < 1 || minimumStudents > maximumStudents) return false;
        String sql = "UPDATE course_sections SET min_students=?, max_students=? WHERE offering_id=? AND enrolled_students<=? AND active=1";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, minimumStudents); ps.setInt(2, maximumStudents); ps.setString(3, offeringId); ps.setInt(4, maximumStudents);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { System.err.println("Update section capacity failed: " + e.getMessage()); return false; }
    }

    public ArrayList<Map<String, Object>> getSections() {
        ArrayList<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        String sql = "SELECT cs.offering_id, cs.subject_code, s.title, cs.section_code, cs.program_id, cs.curriculum_id, cs.lecture_units, cs.lab_units, cs.min_students, cs.max_students, cs.enrolled_students "
                   + "FROM course_sections cs JOIN subjects s ON s.code=cs.subject_code WHERE cs.active=1 ORDER BY cs.subject_code, cs.section_code";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("id", rs.getString("offering_id"));
                row.put("code", rs.getString("subject_code"));
                row.put("title", rs.getString("title"));
                row.put("section", rs.getString("section_code"));
                row.put("programId", rs.getString("program_id"));
                row.put("curriculumId", rs.getString("curriculum_id"));
                row.put("lectureUnits", rs.getInt("lecture_units"));
                row.put("labUnits", rs.getInt("lab_units"));
                row.put("minimum", rs.getInt("min_students"));
                row.put("maximum", rs.getInt("max_students"));
                row.put("enrolled", rs.getInt("enrolled_students"));
                rows.add(row);
            }
        } catch (SQLException e) { System.err.println("Could not load section list: " + e.getMessage()); }
        return rows;
    }

    public String getAcademicLabel(String programId, String curriculumId) {
        String program = programId, curriculum = curriculumId;
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "SELECT p.name, c.name FROM academic_programs p LEFT JOIN curricula c ON c.program_id=p.program_id AND c.curriculum_id=? WHERE p.program_id=?")) {
            ps.setString(1, curriculumId); ps.setString(2, programId);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) { program = rs.getString(1); curriculum = rs.getString(2) == null ? curriculumId : rs.getString(2); } }
        } catch (SQLException e) { }
        return program + " · " + curriculum;
    }

    public void ensureDefaultProgramAndCurriculum() {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "INSERT INTO academic_programs (program_id, code, name, active) SELECT 'BSIT','BSIT','Bachelor of Science in Information Technology',1 WHERE NOT EXISTS (SELECT 1 FROM academic_programs WHERE program_id='BSIT')")) {
            ps.executeUpdate();
        } catch (SQLException e) { System.err.println("Could not seed default program: " + e.getMessage()); }
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "INSERT INTO curricula (curriculum_id, program_id, name, effective_year, active) SELECT 'BSIT-2026','BSIT','BSIT Curriculum','A.Y. 2026-2027',1 WHERE NOT EXISTS (SELECT 1 FROM curricula WHERE curriculum_id='BSIT-2026')")) {
            ps.executeUpdate();
        } catch (SQLException e) { System.err.println("Could not seed default curriculum: " + e.getMessage()); }
    }

    /** One-time migration of the original one-subject/one-section rows. */
    public void migrateLegacySections() {
        SubjectDAO subjects = new SubjectDAO();
        for (Subject old : subjects.getAll()) {
            String id = "LEGACY-" + old.getCode().replaceAll("[^A-Za-z0-9]", "-");
            if (exists("SELECT 1 FROM course_sections WHERE offering_id=?", id)) continue;
            int max = Math.max(1, old.getSlots());
            int enrolled = old.getSlots() <= 0 ? 1 : 0;
            java.sql.Connection c = null;
            boolean previousAuto = true;
            try {
                c = DatabaseManager.getConnection();
                previousAuto = c.getAutoCommit();
                c.setAutoCommit(false);
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO course_sections (offering_id, subject_code, section_code, program_id, curriculum_id, term, lecture_units, lab_units, min_students, max_students, enrolled_students, active) VALUES (?,?,?,?,?,?,?,?,?,?,?,1)")) {
                    ps.setString(1, id); ps.setString(2, old.getCode()); ps.setString(3, old.getSection());
                    ps.setString(4, "BSIT"); ps.setString(5, "BSIT-2026"); ps.setString(6, "1st Semester, A.Y. 2026-2027");
                    ps.setInt(7, old.getUnits()); ps.setInt(8, 0); ps.setInt(9, 0); ps.setInt(10, max); ps.setInt(11, enrolled);
                    ps.executeUpdate();
                }
                addSchedule(c, id, "LEC", old.getSchedule().getDay(), old.getSchedule().getStartMinute(), old.getSchedule().getEndMinute());
                c.commit();
            } catch (SQLException e) {
                if (c != null) try { c.rollback(); } catch (SQLException ignored) { }
                System.err.println("Legacy section migration failed for " + old.getCode() + ": " + e.getMessage());
            } finally {
                if (c != null) try { c.setAutoCommit(previousAuto); } catch (SQLException ignored) { }
            }
        }
    }
}