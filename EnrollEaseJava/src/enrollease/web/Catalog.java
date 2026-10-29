package enrollease.web;

import java.util.ArrayList;

import enrollease.db.SubjectDAO;
import enrollease.ds.BinarySearchTree;
import enrollease.ds.PrerequisiteGraph;
import enrollease.model.Subject;

/**
 * The subject catalog loaded once from the database.
 * Keeps the subjects in a list, a Binary Search Tree (lookup by code)
 * and a prerequisite Graph (locking).
 */
public class Catalog {

    private ArrayList<Subject> subjects;
    private BinarySearchTree<String, Subject> tree = new BinarySearchTree<String, Subject>();
    private PrerequisiteGraph graph = new PrerequisiteGraph();

    public Catalog() {
        SubjectDAO dao = new SubjectDAO();
        subjects = dao.getAll();
        for (Subject s : subjects) {
            tree.insert(s.getCode().toUpperCase(), s);
            graph.addSubject(s.getCode());
        }
        for (String[] pair : dao.getPrerequisitePairs()) {
            graph.addPrerequisite(pair[0], pair[1]);
        }
    }

    public ArrayList<Subject> getAll() { return subjects; }

    /** Subjects in order of code, read from the tree (in-order traversal). */
    public ArrayList<Subject> inOrder() { return tree.inOrder(); }

    /** Tree lookup. Returns null if the code does not exist. */
    public Subject find(String code) {
        return code == null ? null : tree.search(code.trim().toUpperCase());
    }

    public PrerequisiteGraph getGraph() { return graph; }
}
