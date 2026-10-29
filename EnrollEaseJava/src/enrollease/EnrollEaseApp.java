package enrollease;

import enrollease.controller.MainController;
import enrollease.db.DatabaseManager;
import enrollease.view.ConsoleView;

/** Entry point. Run this class in Eclipse: Right-click > Run As > Java Application. */
public class EnrollEaseApp {

    public static void main(String[] args) {
        if (!DatabaseManager.initialize()) {
            System.out.println("The system could not start because the database is not available.");
            return;
        }
        ConsoleView view = new ConsoleView();
        MainController controller = new MainController(view);
        controller.run();
        DatabaseManager.close();
    }
}
