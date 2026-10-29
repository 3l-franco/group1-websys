package enrollease.model;

/** A registrar-office staff account (Registrar, Cashier, Clerk, Administrator). */
public class Staff extends Person {

    private String position;

    public Staff(String id, String name, String email, String position, String password) {
        super(id, name, email, password);
        this.position = position;
    }

    @Override
    public String getRole() { return "Staff"; }

    @Override
    public String getExtra() { return position; }

    @Override
    public void setExtra(String extra) { this.position = extra; }

    @Override
    public String describe() {
        return super.describe() + " - " + position;
    }
}
