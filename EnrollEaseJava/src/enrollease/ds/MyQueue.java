package enrollease.ds;

/**
 * A First-In-First-Out (FIFO) queue built on top of MyLinkedList.
 * enqueue() adds at the back, dequeue() removes from the front.
 *
 * Concepts: Queue, Linked List, Composition, Generics.
 */
public class MyQueue<T> {

    private MyLinkedList<T> list = new MyLinkedList<T>();

    public void enqueue(T item) {
        list.addLast(item);
    }

    /** Removes and returns the front item (null if the queue is empty). */
    public T dequeue() {
        return list.removeFirst();
    }

    /** Looks at the front item without removing it. */
    public T peek() {
        return list.peekFirst();
    }

    /** Position of an item in the queue (0 = front), or -1 if not found. */
    public int positionOf(T item) {
        return list.indexOf(item);
    }

    public int size() {
        return list.size();
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }
}
