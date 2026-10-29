package enrollease.db;

import java.util.ArrayList;

/** Generic Data Access Object interface: the basic database operations for any type T. */
public interface Dao<T> {
    boolean add(T item);
    ArrayList<T> getAll();
    T findById(String id);
    boolean delete(String id);
}
