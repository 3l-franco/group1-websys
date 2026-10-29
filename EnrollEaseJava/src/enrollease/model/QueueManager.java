package enrollease.model;

import enrollease.ds.MyQueue;

/**
 * The cashier's payment queue shared by all students.
 * Methods are "synchronized" because the cashier THREAD and the menu may use it
 * at the same time.
 *
 * Starts like the website demo: No. 99 is being served, 100-103 are waiting,
 * so the next student gets No. 104 with 5 numbers ahead.
 *
 * Concepts: Queue (FIFO), Threads (synchronized).
 */
public class QueueManager {

    private MyQueue<Ticket> waiting = new MyQueue<Ticket>();
    private Ticket nowServing;
    private int lastIssued;

    public QueueManager() {
        nowServing = new Ticket(99, "Walk-in");
        for (int n = 100; n <= 103; n++) {
            waiting.enqueue(new Ticket(n, "Walk-in"));
        }
        lastIssued = 103;
    }

    public synchronized Ticket issueTicket(String owner) {
        lastIssued++;
        Ticket ticket = new Ticket(lastIssued, owner);
        waiting.enqueue(ticket);
        return ticket;
    }

    /** The cashier calls the next number. Returns the ticket now being served. */
    public synchronized Ticket callNext() {
        Ticket next = waiting.dequeue();
        if (next != null) {
            nowServing = next;
        }
        return nowServing;
    }

    public synchronized Ticket getNowServing() {
        return nowServing;
    }

    /** How many numbers come before this ticket (0 = it is your turn). */
    public synchronized int getNumbersAhead(Ticket mine) {
        if (mine.equals(nowServing)) {
            return 0;
        }
        int position = waiting.positionOf(mine);
        if (position < 0) {
            return 0; // already served
        }
        return position + 1; // +1 for the number being served now
    }

    public synchronized String getStatus(Ticket mine) {
        int ahead = getNumbersAhead(mine);
        if (ahead == 0) return "Your turn";
        if (ahead <= 2) return "Your turn is near";
        return "Payment queue";
    }

    public synchronized String getMessage(Ticket mine) {
        int ahead = getNumbersAhead(mine);
        if (ahead == 0) return "It is your turn. Please proceed to the cashier.";
        if (ahead <= 2) return "Your turn is near. Please get ready.";
        return "You are " + ahead + " numbers away. Please proceed to the cashier area.";
    }
}
