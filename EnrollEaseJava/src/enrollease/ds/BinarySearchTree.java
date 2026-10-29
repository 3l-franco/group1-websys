package enrollease.ds;

import java.util.ArrayList;

/**
 * A Binary Search Tree that maps a key (K) to a value (V).
 * Smaller keys go to the left, bigger keys go to the right.
 * Used to look up subjects quickly by their code.
 *
 * Concepts: Tree, recursion, Generics with bounded type (K extends Comparable<K>).
 */
public class BinarySearchTree<K extends Comparable<K>, V> {

    private static class Node<K, V> {
        private K key;
        private V value;
        private Node<K, V> left;
        private Node<K, V> right;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private Node<K, V> root;
    private int size;

    public void insert(K key, V value) {
        root = insert(root, key, value);
    }

    private Node<K, V> insert(Node<K, V> node, K key, V value) {
        if (node == null) {
            size++;
            return new Node<K, V>(key, value);
        }
        int cmp = key.compareTo(node.key);
        if (cmp < 0) {
            node.left = insert(node.left, key, value);
        } else if (cmp > 0) {
            node.right = insert(node.right, key, value);
        } else {
            node.value = value; // same key: replace the value
        }
        return node;
    }

    /** Returns the value for the key, or null if the key is not in the tree. */
    public V search(K key) {
        Node<K, V> current = root;
        while (current != null) {
            int cmp = key.compareTo(current.key);
            if (cmp == 0) {
                return current.value;
            }
            current = (cmp < 0) ? current.left : current.right;
        }
        return null;
    }

    /** In-order traversal (left, node, right) returns the values sorted by key. */
    public ArrayList<V> inOrder() {
        ArrayList<V> result = new ArrayList<V>();
        inOrder(root, result);
        return result;
    }

    private void inOrder(Node<K, V> node, ArrayList<V> result) {
        if (node == null) {
            return;
        }
        inOrder(node.left, result);
        result.add(node.value);
        inOrder(node.right, result);
    }

    public int height() {
        return height(root);
    }

    private int height(Node<K, V> node) {
        if (node == null) {
            return 0;
        }
        return 1 + Math.max(height(node.left), height(node.right));
    }

    public int size() {
        return size;
    }
}
