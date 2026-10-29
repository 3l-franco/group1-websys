package enrollease.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import enrollease.model.Schedule;
import enrollease.model.Staff;
import enrollease.model.Student;
import enrollease.model.Subject;

/**
 * Opens the JDBC connection, creates the tables, and inserts demonstration data.
 *
 * Local development uses SQLite exactly as before. When DATABASE_URL is set,
 * the web app uses the hosted PostgreSQL database (for example, Neon).
 * Set DATABASE_USER and DATABASE_PASSWORD separately in the hosting dashboard.
 */
public class DatabaseManager {

    private static Connection connection;

    public static boolean isExternalDatabase() {
        String url = System.getenv("DATABASE_URL");
        return url != null && !url.trim().isEmpty();
    }

    private static String databaseUrl() {
        if (isExternalDatabase()) {
            String url = System.getenv("DATABASE_URL").trim();
            String prefix;
            String remainder;
            if (url.startsWith("jdbc:postgresql://")) {
                prefix = "jdbc:postgresql://";
                remainder = url.substring(prefix.length());
            } else if (url.startsWith("postgresql://")) {
                prefix = "jdbc:postgresql://";
                remainder = url.substring("postgresql://".length());
            } else if (url.startsWith("postgres://")) {
                prefix = "jdbc:postgresql://";
                remainder = url.substring("postgres://".length());
            } else {
                return url;
            }
            // Neon sometimes presents a URI with user:password@host. Credentials
            // are passed separately from environment variables, so remove userinfo.
            int slash = remainder.indexOf('/');
            int at = remainder.indexOf('@');
            if (at >= 0 && (slash < 0 || at < slash)) remainder = remainder.substring(at + 1);
            return prefix + remainder;
        }
        String path = System.getenv("SQLITE_DB_PATH");
        if (path == null || path.trim().isEmpty()) path = "enrollease.db";
        return "jdbc:sqlite:" + path;
    }

    public static synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            String url = databaseUrl();
            if (isExternalDatabase()) {
                String user = System.getenv("DATABASE_USER");
                String password = System.getenv("DATABASE_PASSWORD");
                if (user == null || user.trim().isEmpty() || password == null || password.isEmpty()) {
                    throw new SQLException("DATABASE_USER and DATABASE_PASSWORD must be configured when DATABASE_URL is set.");
                }
                connection = DriverManager.getConnection(url, user, password);
            } else {
                connection = DriverManager.getConnection(url);
            }
        }
        return connection;
    }

    /** Returns true if the database is ready to use. */
    public static boolean initialize() {
        try {
            Class.forName(isExternalDatabase() ? "org.postgresql.Driver" : "org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.out.println(isExternalDatabase()
                ? "PostgreSQL JDBC driver is missing. Build with the included Maven configuration."
                : "SQLite JDBC driver not found. Add lib/sqlite-jdbc-3.42.0.0.jar to the Build Path.");
            return false;
        }
        try (Statement st = getConnection().createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS accounts ("
                + "id TEXT PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL, "
                + "extra TEXT NOT NULL, password TEXT NOT NULL, role TEXT NOT NULL, "
                + "failed TEXT NOT NULL DEFAULT '')");
            st.execute("CREATE TABLE IF NOT EXISTS subjects ("
                + "code TEXT PRIMARY KEY, title TEXT NOT NULL, units INTEGER NOT NULL, "
                + "section TEXT NOT NULL, day TEXT NOT NULL, start_min INTEGER NOT NULL, "
                + "end_min INTEGER NOT NULL, slots INTEGER NOT NULL, prereqs TEXT NOT NULL DEFAULT '')");
            st.execute("CREATE TABLE IF NOT EXISTS enrollment_slips ("
                + "id TEXT PRIMARY KEY, student_id TEXT NOT NULL, student_name TEXT NOT NULL, "
                + "total_units INTEGER NOT NULL, queue_number INTEGER NOT NULL, submitted_at TEXT NOT NULL, "
                + "created_at BIGINT NOT NULL, status TEXT NOT NULL DEFAULT 'Pending Review', "
                + "admin_notes TEXT NOT NULL DEFAULT '')");
            // Migrate databases created before enrollment review was added.
            ensureColumn(st, "enrollment_slips", "status", "TEXT NOT NULL DEFAULT 'Pending Review'");
            ensureColumn(st, "enrollment_slips", "admin_notes", "TEXT NOT NULL DEFAULT ''");
            st.execute("CREATE TABLE IF NOT EXISTS enrollment_slip_subjects ("
                + "slip_id TEXT NOT NULL, subject_index INTEGER NOT NULL, subject_code TEXT NOT NULL, "
                + "title TEXT NOT NULL, units INTEGER NOT NULL, section TEXT NOT NULL, day TEXT NOT NULL, "
                + "start_min INTEGER NOT NULL, end_min INTEGER NOT NULL, slots INTEGER NOT NULL, "
                + "PRIMARY KEY (slip_id, subject_index))");
            st.execute("CREATE TABLE IF NOT EXISTS student_credentials ("
                + "id TEXT PRIMARY KEY, student_id TEXT NOT NULL, student_name TEXT NOT NULL, "
                + "credential_type TEXT NOT NULL, file_name TEXT NOT NULL, content_type TEXT NOT NULL, "
                + "data_base64 TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'Pending Review', "
                + "admin_notes TEXT NOT NULL DEFAULT '', submitted_at TEXT NOT NULL, "
                + "reviewed_at TEXT NOT NULL DEFAULT '', reviewed_by TEXT NOT NULL DEFAULT '')");
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
            return false;
        }
        seedData();
        return true;
    }

    /** Adds a missing column without disturbing data created by an earlier version. */
    private static void ensureColumn(Statement statement, String table, String column, String definition) {
        try (java.sql.ResultSet ignored = statement.executeQuery("SELECT " + column + " FROM " + table + " WHERE 1=0")) {
            // Column already exists.
        } catch (SQLException missing) {
            try {
                statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            } catch (SQLException migrationError) {
                // If another process added it first, startup can continue. Other failures remain visible.
                try (java.sql.ResultSet ignored = statement.executeQuery("SELECT " + column + " FROM " + table + " WHERE 1=0")) {
                    // The column now exists.
                } catch (SQLException stillMissing) {
                    System.out.println("Database migration error for " + table + "." + column + ": " + migrationError.getMessage());
                }
            }
        }
    }

    // Inserts the same sample records the website had, only when the tables are empty.
    private static void seedData() {
        AccountDAO accounts = new AccountDAO();
        if (accounts.count("STUDENT") + accounts.count("STAFF") == 0) {
            ArrayList<String> failed = new ArrayList<String>();
            failed.add("IT 102"); // the demo student failed IT 102 (as in the website notice)
            accounts.add(new Student("2026-00123", "Demo Student", "demo@student.example.edu", "Continuing Student", "demo123", failed));
            accounts.add(new Student("2026-00124", "Alex Santos", "alex@student.example.edu", "New Student", "alex123"));
            accounts.add(new Student("2026-00125", "Jamie Reyes", "jamie@student.example.edu", "Transferee", "jamie123"));
            accounts.add(new Staff("registrar1", "Maria Cruz", "mcruz@tip.example.edu", "Registrar", "staff123"));
            accounts.add(new Staff("cashier1", "Joel Mendoza", "jmendoza@tip.example.edu", "Cashier", "staff123"));
        }
        SubjectDAO subjects = new SubjectDAO();
        if (subjects.count() == 0) {
            subjects.add(new Subject("IT 201", "Data Structures", 3, "A", new Schedule("Mon", 450, 540), 5), "");
            subjects.add(new Subject("IT 202", "Database Systems", 3, "A", new Schedule("Mon", 540, 630), 12), "");
            subjects.add(new Subject("MATH 201", "Discrete Math", 3, "B", new Schedule("Mon", 540, 630), 8), "");
            subjects.add(new Subject("IT 102", "Intro to Programming (retake)", 3, "C", new Schedule("Tue", 780, 870), 10), "");
            subjects.add(new Subject("IT 203", "Object-Oriented Programming", 3, "A", new Schedule("Wed", 780, 870), 15), "IT 102");
            subjects.add(new Subject("IT 204", "Networking 1", 3, "B", new Schedule("Thu", 600, 690), 0), "");
        }
    }

    public static synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            System.err.println("Could not close database: " + e.getMessage());
        }
    }
}
