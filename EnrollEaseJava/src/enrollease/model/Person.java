package enrollease.model;

/**
 * Abstract parent class of every account in EnrollEase.
 * Fields are private (encapsulation) and accessed through getters/setters.
 * Subclasses must supply getRole(), getExtra() and setExtra().
 *
 * Concepts: Abstraction, Encapsulation, Inheritance, Interface, Method overriding.
 */
public abstract class Person implements Describable {

    private String id;
    private String name;
    private String email;
    private String password;

    public Person(String id, String name, String email, String password) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.password = password;
    }

    // Abstract methods: each child class decides its own answer (polymorphism).
    public abstract String getRole();
    public abstract String getExtra();
    public abstract void setExtra(String extra);

    public boolean checkPassword(String attempt) {
        return password.equals(attempt);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setPassword(String password) { this.password = password; }

    @Override
    public String describe() {
        return getRole() + ": " + name + " (" + id + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Person)) return false;
        return id.equalsIgnoreCase(((Person) other).id);
    }

    @Override
    public int hashCode() {
        return id.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return describe();
    }
}
