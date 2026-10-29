package enrollease.ds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * A directed graph (adjacency list) of subject prerequisites.
 * An edge  IT 102 -> IT 203  means "IT 102 is a prerequisite of IT 203".
 *
 * BFS  (uses MyQueue): finds every subject that becomes LOCKED when a subject was failed.
 * DFS  (recursion)   : finds every subject a given subject depends on.
 *
 * Concepts: Graph, BFS, DFS, Queue, HashMap.
 */
public class PrerequisiteGraph {

    // subject code -> subjects that REQUIRE it (edges pointing forward)
    private HashMap<String, ArrayList<String>> unlocks = new HashMap<String, ArrayList<String>>();
    // subject code -> subjects it REQUIRES (edges pointing backward)
    private HashMap<String, ArrayList<String>> requires = new HashMap<String, ArrayList<String>>();

    public void addSubject(String code) {
        if (!unlocks.containsKey(code)) {
            unlocks.put(code, new ArrayList<String>());
            requires.put(code, new ArrayList<String>());
        }
    }

    public void addPrerequisite(String prerequisite, String subject) {
        addSubject(prerequisite);
        addSubject(subject);
        unlocks.get(prerequisite).add(subject);
        requires.get(subject).add(prerequisite);
    }

    /** BFS: all subjects that cannot be taken because of the failed subjects. */
    public ArrayList<String> findLockedSubjects(ArrayList<String> failedCodes) {
        ArrayList<String> locked = new ArrayList<String>();
        HashSet<String> visited = new HashSet<String>();
        MyQueue<String> queue = new MyQueue<String>();

        for (String failed : failedCodes) {
            if (unlocks.containsKey(failed)) {
                queue.enqueue(failed);
            }
        }
        while (!queue.isEmpty()) {
            String current = queue.dequeue();
            for (String next : unlocks.get(current)) {
                if (!visited.contains(next)) {
                    visited.add(next);
                    locked.add(next);
                    queue.enqueue(next);
                }
            }
        }
        return locked;
    }

    /** DFS: every direct and indirect prerequisite of a subject. */
    public ArrayList<String> getAllPrerequisites(String code) {
        ArrayList<String> result = new ArrayList<String>();
        if (requires.containsKey(code)) {
            dfs(code, new HashSet<String>(), result);
        }
        return result;
    }

    private void dfs(String code, HashSet<String> visited, ArrayList<String> result) {
        for (String prerequisite : requires.get(code)) {
            if (!visited.contains(prerequisite)) {
                visited.add(prerequisite);
                result.add(prerequisite);
                dfs(prerequisite, visited, result);
            }
        }
    }

    /** Failed subjects that block the given subject (used for the "Locked (needs ...)" label). */
    public ArrayList<String> getBlockingSubjects(String code, ArrayList<String> failedCodes) {
        ArrayList<String> blockers = new ArrayList<String>();
        for (String prerequisite : getAllPrerequisites(code)) {
            if (failedCodes.contains(prerequisite)) {
                blockers.add(prerequisite);
            }
        }
        return blockers;
    }
}
