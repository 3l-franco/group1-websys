package enrollease.model;

/** The built-in system administrator (website login: admin / admin). */
public class Admin extends Person {

    public Admin() {
        super("admin", "System Administrator", "admin@tip.example.edu", "admin");
    }

    @Override
    public String getRole() { return "Admin"; }

    @Override
    public String getExtra() { return "Administrator"; }

    @Override
    public void setExtra(String extra) { /* fixed value, nothing to change */ }
}
