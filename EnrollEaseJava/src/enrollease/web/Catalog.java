package enrollease.web;

import java.util.ArrayList;

import enrollease.db.AcademicDAO;
import enrollease.db.SubjectDAO;
import enrollease.ds.BinarySearchTree;
import enrollease.ds.PrerequisiteGraph;
import enrollease.model.Subject;

/**
 * Keeps the original subject-code BST and prerequisite graph for the DSA
 * requirements, while exposing program-specific section offerings to enrollment.
 */
public class Catalog {
    private ArrayList<Subject> subjects = new ArrayList<Subject>();
    private BinarySearchTree<String, Subject> tree = new BinarySearchTree<String, Subject>();
    private PrerequisiteGraph graph = new PrerequisiteGraph();
    private final SubjectDAO subjectDao = new SubjectDAO();
    private final AcademicDAO academicDao = new AcademicDAO();

    public Catalog() { reload(); }

    public synchronized void reload() {
        subjects = subjectDao.getAll();
        tree = new BinarySearchTree<String, Subject>();
        graph = new PrerequisiteGraph();
        for (Subject subject : subjects) {
            tree.insert(subject.getCode().toUpperCase(), subject);
            graph.addSubject(subject.getCode());
        }
        for (String[] pair : subjectDao.getPrerequisitePairs()) {
            graph.addPrerequisite(pair[0], pair[1]);
        }
    }

    public ArrayList<Subject> getAll() { return subjects; }
    public ArrayList<Subject> inOrder() { return tree.inOrder(); }

    public Subject find(String code) {
        return code == null ? null : tree.search(code.trim().toUpperCase());
    }

    public Subject findOffering(String offeringId) {
        if (offeringId == null || offeringId.trim().isEmpty()) return null;
        for (Subject offering : academicDao.getOfferings("", "")) {
            if (offeringId.equals(offering.getOfferingId())) return offering;
        }
        return null;
    }

    public ArrayList<Subject> getOfferings(String programId, String curriculumId) {
        return academicDao.getOfferings(programId, curriculumId);
    }

    public PrerequisiteGraph getGraph() { return graph; }
}
