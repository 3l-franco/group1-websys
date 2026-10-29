package enrollease.ds;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A simple singly linked list written from scratch (no java.util.LinkedList).
 * Generic, so it can store any type: MyLinkedList<Subject>, MyLinkedList<Ticket>, ...
 *
 * Concepts: Linked List, Generics, Static nested class (Node), Inner class (iterator).
 */
public class MyLinkedList<T> implements Iterable<T> {

    // Static nested class: a Node does not need access to the list itself.
    private static class Node<E> {
        private E data;
        private Node<E> next;

        Node(E data) {
            this.data = data;
        }
    }

    private Node<T> head;
    private Node<T> tail;
    private int size;

    /** Adds an item at the end of the list. */
    public void addLast(T item) {
        Node<T> node = new Node<T>(item);
        if (head == null) {
            head = node;
            tail = node;
        } else {
            tail.next = node;
            tail = node;
        }
        size++;
    }

    /** Adds an item at the front of the list. */
    public void addFirst(T item) {
        Node<T> node = new Node<T>(item);
        node.next = head;
        head = node;
        if (tail == null) {
            tail = node;
        }
        size++;
    }

    /** Removes and returns the first item, or null if the list is empty. */
    public T removeFirst() {
        if (head == null) {
            return null;
        }
        T data = head.data;
        head = head.next;
        if (head == null) {
            tail = null;
        }
        size--;
        return data;
    }

    /** Removes the first item that equals the given item. Returns true if removed. */
    public boolean remove(T item) {
        Node<T> previous = null;
        Node<T> current = head;
        while (current != null) {
            if (current.data.equals(item)) {
                if (previous == null) {
                    head = current.next;
                } else {
                    previous.next = current.next;
                }
                if (current == tail) {
                    tail = previous;
                }
                size--;
                return true;
            }
            previous = current;
            current = current.next;
        }
        return false;
    }

    public T peekFirst() {
        return head == null ? null : head.data;
    }

    public T get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        Node<T> current = head;
        for (int i = 0; i < index; i++) {
            current = current.next;
        }
        return current.data;
    }

    /** Returns the position of the item (0 = first), or -1 if it is not in the list. */
    public int indexOf(T item) {
        int index = 0;
        for (Node<T> current = head; current != null; current = current.next) {
            if (current.data.equals(item)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public boolean contains(T item) {
        return indexOf(item) != -1;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        head = null;
        tail = null;
        size = 0;
    }

    // Makes the list usable in a for-each loop.
    @Override
    public Iterator<T> iterator() {
        return new ListIterator();
    }

    // Inner (non-static) class: it reads the outer list's "head" field directly.
    private class ListIterator implements Iterator<T> {
        private Node<T> current = head;

        @Override
        public boolean hasNext() {
            return current != null;
        }

        @Override
        public T next() {
            if (current == null) {
                throw new NoSuchElementException();
            }
            T data = current.data;
            current = current.next;
            return data;
        }
    }
}
