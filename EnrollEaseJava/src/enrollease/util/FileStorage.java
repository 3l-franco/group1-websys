package enrollease.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;

/**
 * Saves and loads a list of objects to/from a file using Java serialization.
 * Generic: FileStorage<EnrollmentSlip> works for any class that implements Serializable.
 *
 * Concepts: Serialization, Generics (bounded type), File I/O, exceptions.
 */
public class FileStorage<T extends Serializable> {

    private String fileName;

    public FileStorage(String fileName) {
        this.fileName = fileName;
    }

    public boolean save(ArrayList<T> items) {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(fileName))) {
            out.writeObject(items);
            return true;
        } catch (IOException e) {
            System.err.println("Could not save file: " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public ArrayList<T> load() {
        File file = new File(fileName);
        if (!file.exists()) {
            return new ArrayList<T>();
        }
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            return (ArrayList<T>) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Could not read file: " + e.getMessage());
            return new ArrayList<T>();
        }
    }

    /** Loads the file, adds one more item, and saves it again. */
    public boolean append(T item) {
        ArrayList<T> items = load();
        items.add(item);
        return save(items);
    }
}
