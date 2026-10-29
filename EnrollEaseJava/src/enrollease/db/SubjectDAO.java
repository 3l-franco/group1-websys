package enrollease.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import enrollease.model.Schedule;
import enrollease.model.Subject;

/** JDBC code for the "subjects" table. */
public class SubjectDAO implements Dao<Subject> {

    @Override
    public boolean add(Subject s) {
        return add(s, "");
    }

    /** prereqs = comma-separated codes of required subjects, e.g. "IT 102". */
    public boolean add(Subject s, String prereqs) {
        String sql = "INSERT INTO subjects (code, title, units, section, day, start_min, end_min, slots, prereqs) "
                   + "VALUES (?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, s.getCode());
            ps.setString(2, s.getTitle());
            ps.setInt(3, s.getUnits());
            ps.setString(4, s.getSection());
            ps.setString(5, s.getSchedule().getDay());
            ps.setInt(6, s.getSchedule().getStartMinute());
            ps.setInt(7, s.getSchedule().getEndMinute());
            ps.setInt(8, s.getSlots());
            ps.setString(9, prereqs);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Add subject failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public ArrayList<Subject> getAll() {
        ArrayList<Subject> list = new ArrayList<Subject>();
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement("SELECT * FROM subjects ORDER BY code");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(fromRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("Load subjects failed: " + e.getMessage());
        }
        return list;
    }

    /** Returns the prerequisite pairs as {prerequisiteCode, subjectCode} for building the graph. */
    public ArrayList<String[]> getPrerequisitePairs() {
        ArrayList<String[]> pairs = new ArrayList<String[]>();
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement("SELECT code, prereqs FROM subjects");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String text = rs.getString("prereqs");
                if (text != null && !text.trim().isEmpty()) {
                    for (String prereq : text.split(",")) {
                        pairs.add(new String[] {prereq.trim(), rs.getString("code")});
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Load prerequisites failed: " + e.getMessage());
        }
        return pairs;
    }

    @Override
    public Subject findById(String code) {
        for (Subject s : getAll()) {
            if (s.getCode().equalsIgnoreCase(code)) {
                return s;
            }
        }
        return null;
    }

    @Override
    public boolean delete(String code) {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement("DELETE FROM subjects WHERE code=?")) {
            ps.setString(1, code);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Delete subject failed: " + e.getMessage());
            return false;
        }
    }

    public int count() {
        return getAll().size();
    }

    private Subject fromRow(ResultSet rs) throws SQLException {
        Schedule schedule = new Schedule(rs.getString("day"), rs.getInt("start_min"), rs.getInt("end_min"));
        return new Subject(rs.getString("code"), rs.getString("title"), rs.getInt("units"),
                           rs.getString("section"), schedule, rs.getInt("slots"));
    }
}
