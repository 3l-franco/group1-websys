package enrollease.model;

/** A payment queue number given to a student (or a walk-in) for the cashier. */
public class Ticket {

    private int number;
    private String owner;

    public Ticket(int number, String owner) {
        this.number = number;
        this.owner = owner;
    }

    public int getNumber() { return number; }
    public String getOwner() { return owner; }

    @Override
    public boolean equals(Object other) {
        return other instanceof Ticket && number == ((Ticket) other).number;
    }

    @Override
    public int hashCode() {
        return number;
    }
}
