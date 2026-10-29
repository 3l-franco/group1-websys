package enrollease.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import enrollease.model.Person;
import enrollease.model.Staff;
import enrollease.model.Student;

/**
 * All JDBC code for the "accounts" table (students and staff).
 * Uses PreparedStatement (the ? placeholders) so user input can never change the SQL.
 */
public class AccountDAO implements Dao<Person> {

    @Override
    public boolean add(Person p) {
        String sql = "INSERT INTO accounts (id, name, email, extra, password, role, failed) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            fill(ps, p);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Add failed: " + e.getMessage());
            return false;
        }
    }

    /** Updates the record whose id is oldId (the id itself may change). */
    public boolean update(String oldId, Person p) {
        String sql = "UPDATE accounts SET id=?, name=?, email=?, extra=?, password=?, role=?, failed=? WHERE id=?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            fill(ps, p);
            ps.setString(8, oldId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Update failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean delete(String id) {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement("DELETE FROM accounts WHERE id=?")) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Delete failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public Person findById(String id) {
        ArrayList<Person> found = query("SELECT * FROM accounts WHERE id=?", id);
        return found.isEmpty() ? null : found.get(0);
    }

    @Override
    public ArrayList<Person> getAll() {
        return query("SELECT * FROM accounts ORDER BY id");
    }

    /** role = "STUDENT" or "STAFF" */
    public ArrayList<Person> getByRole(String role) {
        return query("SELECT * FROM accounts WHERE role=? ORDER BY id", role);
    }

    public int count(String role) {
        return getByRole(role).size();
    }

    /** Case-insensitive check, ignoring the record being edited (exceptId may be null). */
    public boolean idExists(String id, String role, String exceptId) {
        for (Person p : getByRole(role)) {
            if (p.getId().equalsIgnoreCase(id) && !p.getId().equals(exceptId)) {
                return true;
            }
        }
        return false;
    }

    public boolean studentEmailExists(String email) {
        for (Person p : getByRole("STUDENT")) {
            if (p.getEmail().equalsIgnoreCase(email.trim())) {
                return true;
            }
        }
        return false;
    }

    /** Returns the student when the ID and password match, otherwise null. */
    public Student login(String id, String password) {
        for (Person p : getByRole("STUDENT")) {
            if (p.getId().equals(id) && p.checkPassword(password)) {
                return (Student) p;
            }
        }
        return null;
    }

    /** Next ID in the website's format: 2026-00126, 2026-00127, ... */
    public String nextStudentId() {
        int max = 0;
        for (Person p : getByRole("STUDENT")) {
            if (p.getId().matches("^2026-\\d+$")) {
                max = Math.max(max, Integer.parseInt(p.getId().substring(5)));
            }
        }
        return String.format("2026-%05d", max + 1);
    }

    // ---------- helpers ----------

    private void fill(PreparedStatement ps, Person p) throws SQLException {
        boolean isStudent = p instanceof Student;
        ps.setString(1, p.getId());
        ps.setString(2, p.getName());
        ps.setString(3, p.getEmail());
        ps.setString(4, p.getExtra());
        ps.setString(5, p.getPassword());
        ps.setString(6, isStudent ? "STUDENT" : "STAFF");
        ps.setString(7, isStudent ? String.join(",", ((Student) p).getFailedCodes()) : "");
    }

    private ArrayList<Person> query(String sql, String... params) {
        ArrayList<Person> list = new ArrayList<Person>();
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(fromRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Query failed: " + e.getMessage());
        }
        return list;
    }

    // Turns one database row into a Student or a Staff object.
    private Person fromRow(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String name = rs.getString("name");
        String email = rs.getString("email");
        String extra = rs.getString("extra");
        String password = rs.getString("password");
        if (rs.getString("role").equals("STUDENT")) {
            ArrayList<String> failed = new ArrayList<String>();
            String text = rs.getString("failed");
            if (text != null && !text.isEmpty()) {
                for (String code : text.split(",")) {
                    failed.add(code.trim());
                }
            }
            return new Student(id, name, email, extra, password, failed);
        }
        return new Staff(id, name, email, extra, password);
    }
}
