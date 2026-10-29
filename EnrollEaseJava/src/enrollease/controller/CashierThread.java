package enrollease.controller;

import enrollease.model.QueueManager;
import enrollease.model.Ticket;
import enrollease.view.ConsoleView;

/**
 * A background THREAD that acts as the cashier: every few seconds it calls the next
 * queue number until it is the student's turn. This replaces the website's
 * "Demo: Call next number" button with an automatic one.
 *
 * Concepts: Threads (Runnable, Thread.sleep, InterruptedException).
 */
public class CashierThread implements Runnable {

    private ConsoleView view;
    private QueueManager queueManager;
    private Ticket myTicket;
    private long delayMillis;

    public CashierThread(ConsoleView view, QueueManager queueManager, Ticket myTicket, long delayMillis) {
        this.view = view;
        this.queueManager = queueManager;
        this.myTicket = myTicket;
        this.delayMillis = delayMillis;
    }

    @Override
    public void run() {
        view.println("[Cashier] Window is open. Calling numbers...");
        try {
            while (queueManager.getNumbersAhead(myTicket) > 0) {
                Thread.sleep(delayMillis); // the cashier takes a moment with each customer
                Ticket serving = queueManager.callNext();
                int ahead = queueManager.getNumbersAhead(myTicket);
                view.println("[Cashier] Now serving No. " + serving.getNumber()
                           + "  |  numbers ahead of you: " + ahead);
                if (ahead > 0 && ahead <= 2) {
                    view.println("[Alert]   Your turn is near. Please get ready.");
                }
            }
            view.println("[Alert]   It is your turn! Please proceed to the cashier.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            view.println("[Cashier] Window closed early.");
        }
    }
}
