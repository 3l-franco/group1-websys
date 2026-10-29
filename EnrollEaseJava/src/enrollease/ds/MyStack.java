package enrollease.ds;

/**
 * A Last-In-First-Out (LIFO) stack built on a plain array that grows when full.
 *
 * Concepts: Array, Stack, Generics.
 */
public class MyStack<T> {

    private Object[] items = new Object[4];
    private int top = 0; // number of items; also the next free index

    public void push(T item) {
        if (top == items.length) {
            grow();
        }
        items[top] = item;
        top++;
    }

    /** Removes and returns the top item (null if the stack is empty). */
    @SuppressWarnings("unchecked")
    public T pop() {
        if (top == 0) {
            return null;
        }
        top--;
        T item = (T) items[top];
        items[top] = null; // let Java free the object
        return item;
    }

    @SuppressWarnings("unchecked")
    public T peek() {
        return top == 0 ? null : (T) items[top - 1];
    }

    public boolean isEmpty() {
        return top == 0;
    }

    public int size() {
        return top;
    }

    // Doubles the array size by copying everything into a bigger array.
    private void grow() {
        Object[] bigger = new Object[items.length * 2];
        for (int i = 0; i < items.length; i++) {
            bigger[i] = items[i];
        }
        items = bigger;
    }
}
