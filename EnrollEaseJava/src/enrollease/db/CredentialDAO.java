package enrollease.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Stores student-submitted credential documents and registrar review decisions. */
public class CredentialDAO {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public boolean add(String studentId, String studentName, String type, String fileName,
                       String contentType, String dataBase64) {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(
                "INSERT INTO student_credentials (id, student_id, student_name, credential_type, file_name, content_type, data_base64, status, admin_notes, submitted_at, reviewed_at, reviewed_by) VALUES (?,?,?,?,?,?,?,'Pending Review','',?,'','')")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, studentId);
            ps.setString(3, studentName);
            ps.setString(4, type);
            ps.setString(5, fileName);
            ps.setString(6, contentType);
            ps.setString(7, dataBase64);
            ps.setString(8, LocalDateTime.now().format(DATE_FORMAT));
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Could not save student credential: " + e.getMessage());
            return false;
        }
    }

    public ArrayList<Map<String, Object>> getByStudent(String studentId) {
        return list("SELECT id, student_id, student_name, credential_type, file_name, content_type, status, admin_notes, submitted_at "
                + "FROM student_credentials WHERE student_id=? ORDER BY submitted_at DESC, id DESC", studentId);
    }

    public ArrayList<Map<String, Object>> getAllForAdmin() {
        return list("SELECT id, student_id, student_name, credential_type, file_name, content_type, status, admin_notes, submitted_at "
                + "FROM student_credentials ORDER BY submitted_at DESC, id DESC", null);
    }

    private ArrayList<Map<String, Object>> list(String sql, String studentId) {
        ArrayList<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            if (studentId != null) ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", rs.getString("id"));
                    row.put("studentId", rs.getString("student_id"));
                    row.put("studentName", rs.getString("student_name"));
                    row.put("type", rs.getString("credential_type"));
                    row.put("fileName", rs.getString("file_name"));
                    row.put("contentType", rs.getString("content_type"));
                    row.put("status", rs.getString("status"));
                    row.put("adminNotes", rs.getString("admin_notes"));
                    row.put("submittedAt", rs.getString("submitted_at"));
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("Could not load student credentials: " + e.getMessage());
        }
        return rows;
    }

    public Map<String, Object> getFile(String id) {
        String sql = "SELECT file_name, content_type, data_base64 FROM student_credentials WHERE id=?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> file = new LinkedHashMap<String, Object>();
                    file.put("fileName", rs.getString("file_name"));
                    file.put("contentType", rs.getString("content_type"));
                    file.put("data", rs.getString("data_base64"));
                    file.put("ok", true);
                    return file;
                }
            }
        } catch (SQLException e) {
            System.err.println("Could not load credential file: " + e.getMessage());
        }
        Map<String, Object> missing = new LinkedHashMap<String, Object>();
        missing.put("ok", false);
        missing.put("message", "Credential file not found.");
        return missing;
    }

    /** Number of documents the registrar has individually verified for this applicant. */
    public int countVerifiedForStudent(String studentId) {
        String sql = "SELECT COUNT(*) FROM student_credentials WHERE student_id=? AND status='Verified'";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            System.err.println("Could not count verified student requirements: " + e.getMessage());
            return 0;
        }
    }

    public boolean review(String id, String status, String notes) {
        String sql = "UPDATE student_credentials SET status=?, admin_notes=?, reviewed_at=?, reviewed_by=? WHERE id=?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, notes == null ? "" : notes.trim());
            ps.setString(3, LocalDateTime.now().format(DATE_FORMAT));
            ps.setString(4, "Admin");
            ps.setString(5, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Could not update credential review: " + e.getMessage());
            return false;
        }
    }
}
