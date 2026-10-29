package enrollease.controller;

import java.util.ArrayList;
import java.util.Comparator;

import enrollease.db.AccountDAO;
import enrollease.ds.MyStack;
import enrollease.model.Admin;
import enrollease.model.Person;
import enrollease.model.SiteContent;
import enrollease.model.Staff;
import enrollease.model.Student;
import enrollease.util.SortUtil;
import enrollease.util.Validator;
import enrollease.view.ConsoleView;

/**
 * CONTROLLER for the admin "Maintenance" page: add, view, edit and delete
 * User (student) and Staff accounts. Deleted accounts go on a stack so the last
 * delete can be undone.
 */
public class AdminController {

    private ConsoleView view;
    private AccountDAO accountDao;
    private MyStack<Person> deletedStack = new MyStack<Person>();

    // Anonymous inner classes used to sort accounts.
    private static final Comparator<Person> BY_ID = new Comparator<Person>() {
        @Override
        public int compare(Person a, Person b) {
            return a.getId().compareToIgnoreCase(b.getId());
        }
    };
    private static final Comparator<Person> BY_NAME = new Comparator<Person>() {
        @Override
        public int compare(Person a, Person b) {
            return a.getName().compareToIgnoreCase(b.getName());
        }
    };

    public AdminController(ConsoleView view, AccountDAO accountDao) {
        this.view = view;
        this.accountDao = accountDao;
    }

    public void run(Admin admin) {
        view.header("Maintenance - " + admin.describe());
        String[] options = {"User Maintenance (students)", "Staff Maintenance", "Undo last delete"};
        boolean running = true;
        while (running) {
            view.println("");
            view.println("Users: " + accountDao.count("STUDENT") + "   Staff: " + accountDao.count("STAFF"));
            switch (view.menu("Admin Menu", options, "Log out")) {
                case 1: manage(true); break;
                case 2: manage(false); break;
                case 3: undoDelete(); break;
                default: running = false; break;
            }
        }
        view.showSuccess("Logged out.");
    }

    // ---------------- one maintenance screen (students OR staff) ----------------

    private void manage(boolean students) {
        String label = students ? "User" : "Staff";
        String[] options = {"View records", "Add " + label, "Edit " + label, "Delete " + label};
        boolean back = false;
        while (!back) {
            switch (view.menu(label + " Maintenance", options, "Back")) {
                case 1: listRecords(students); break;
                case 2: addRecord(students); break;
                case 3: editRecord(students); break;
                case 4: deleteRecord(students); break;
                default: back = true; break;
            }
        }
    }

    private String roleOf(boolean students) { return students ? "STUDENT" : "STAFF"; }
    private String idLabel(boolean students) { return students ? "Student ID" : "Username"; }
    private String extraLabel(boolean students) { return students ? "Student type" : "Position"; }
    private String[] extraOptions(boolean students) {
        return students ? SiteContent.STUDENT_TYPES : SiteContent.STAFF_POSITIONS;
    }

    private void listRecords(boolean students) {
        ArrayList<Person> people = accountDao.getByRole(roleOf(students));
        int sortBy = view.askInt("Sort by: 1 = as added, 2 = ID, 3 = name: ", 1, 3);
        if (sortBy > 1) {
            Person[] array = people.toArray(new Person[0]);
            SortUtil.insertionSort(array, sortBy == 2 ? BY_ID : BY_NAME);
            people = new ArrayList<Person>(java.util.Arrays.asList(array));
        }
        view.showAccounts(people, idLabel(students), extraLabel(students));
    }

    private void addRecord(boolean students) {
        String id = view.ask(idLabel(students) + ": ");
        String name = view.ask("Full name: ");
        String email = view.ask("Email: ");
        String extra = view.choose(extraLabel(students), extraOptions(students));
        String password = view.ask("Password: ");

        String error = validate(students, id, name, email, password, true, null);
        if (error != null) {
            view.showError(error);
            return;
        }
        Person person = students ? new Student(id, name, email, extra, password)
                                 : new Staff(id, name, email, extra, password);
        if (accountDao.add(person)) {
            view.showSuccess((students ? "User" : "Staff") + " record added.");
        } else {
            view.showError("Could not save the record.");
        }
    }

    private void editRecord(boolean students) {
        Person old = findRecord(students, view.ask("Enter the " + idLabel(students) + " to edit: "));
        if (old == null) {
            return;
        }
        view.println("Press Enter to keep the current value.");
        String id = keep(view.ask(idLabel(students) + " [" + old.getId() + "]: "), old.getId());
        String name = keep(view.ask("Full name [" + old.getName() + "]: "), old.getName());
        String email = keep(view.ask("Email [" + old.getEmail() + "]: "), old.getEmail());
        String extra = pickExtra(students, old.getExtra());
        String password = view.ask("Password [hidden, Enter keeps it]: ");

        String error = validate(students, id, name, email, password, false, old.getId());
        if (error != null) {
            view.showError(error);
            return;
        }
        if (password.isEmpty()) {
            password = old.getPassword();
        }
        Person updated;
        if (old instanceof Student) {
            updated = new Student(id, name, email, extra, password, ((Student) old).getFailedCodes());
        } else {
            updated = new Staff(id, name, email, extra, password);
        }
        if (accountDao.update(old.getId(), updated)) {
            view.showSuccess((students ? "User" : "Staff") + " record updated.");
        } else {
            view.showError("Could not update the record.");
        }
    }

    private void deleteRecord(boolean students) {
        Person person = findRecord(students, view.ask("Enter the " + idLabel(students) + " to delete: "));
        if (person == null) {
            return;
        }
        if (view.confirm("Delete this " + (students ? "user" : "staff") + " record (" + person.describe() + ")?")
                && accountDao.delete(person.getId())) {
            deletedStack.push(person);   // remember it so it can be undone
            view.showSuccess((students ? "User" : "Staff") + " record deleted.");
        }
    }

    private void undoDelete() {
        Person person = deletedStack.pop();   // Stack: last deleted comes back first
        if (person == null) {
            view.showInfo("Nothing to undo.");
        } else if (accountDao.add(person)) {
            view.showSuccess("Restored " + person.describe());
        } else {
            view.showError("Could not restore the record (ID may already exist).");
        }
    }

    // ---------------- helpers ----------------

    // Finds a record of the correct kind, or prints an error and returns null.
    private Person findRecord(boolean students, String id) {
        Person p = accountDao.findById(id);
        if (p == null || (p instanceof Student) != students) {
            view.showError("No " + (students ? "user" : "staff") + " record with that " + idLabel(students) + ".");
            return null;
        }
        return p;
    }

    private String keep(String typed, String current) {
        return typed.isEmpty() ? current : typed;
    }

    private String pickExtra(boolean students, String current) {
        String[] options = extraOptions(students);
        for (int i = 0; i < options.length; i++) {
            view.println("  " + (i + 1) + ". " + options[i]);
        }
        while (true) {
            String text = view.ask(extraLabel(students) + " [" + current + "] (1-" + options.length + ", Enter keeps): ");
            if (text.isEmpty()) {
                return current;
            }
            try {
                int n = Integer.parseInt(text);
                if (n >= 1 && n <= options.length) {
                    return options[n - 1];
                }
            } catch (NumberFormatException e) {
                // ask again
            }
            view.showError("Please choose a number from the list.");
        }
    }

    // Same rules as the website's form. Returns an error message, or null if everything is fine.
    private String validate(boolean students, String id, String name, String email,
                            String password, boolean adding, String editingId) {
        if (Validator.isBlank(id) || Validator.isBlank(name)) {
            return "Please complete all fields.";
        }
        if (!Validator.isValidEmail(email)) {
            return "Enter a valid email address.";
        }
        if (adding && password.isEmpty()) {
            return "Please enter a password.";
        }
        if (accountDao.idExists(id, roleOf(students), editingId)) {
            return idLabel(students) + " already exists.";
        }
        return null;
    }
}
