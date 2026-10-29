package enrollease.web;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import enrollease.db.DatabaseManager;

/**
 * Entry point of the WEB version. Run this class, then open  http://localhost:8080
 * in a browser. The browser shows the HTML/CSS/JavaScript pages (folder "web");
 * the pages talk to the Java code through the /api/... addresses handled by Api.
 */
public class WebApp {

    public static void main(String[] args) throws IOException {
        if (!DatabaseManager.initialize()) {
            System.out.println("The system could not start because the database is not available.");
            return;
        }
        int port = portFromEnvironment();
        File webRoot = new File("web");
        if (!webRoot.isDirectory()) {
            System.out.println("Folder 'web' not found in: " + new File(".").getCanonicalPath());
            System.out.println("In Eclipse: Run > Run Configurations > Arguments > Working directory = the project folder.");
            return;
        }

        final Api api = new Api();
        HttpServer server;
        try {
            // Only this computer can connect (127.0.0.1).
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        } catch (BindException e) {
            System.out.println("Port " + port + " is already in use. Stop the other program (or the old run) first.");
            return;
        }

        // Anonymous inner class: handles every address that starts with /api/
        server.createContext("/api/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                api.handle(exchange);
            }
        });
        server.createContext("/", new StaticFileHandler(webRoot));
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> DatabaseManager.close()));

        System.out.println("EnrollEase web server is running on port " + port + ".");
        System.out.println("Local URL: http://localhost:" + port);
        System.out.println("(Press the red Stop button in Eclipse to shut it down.)");
    }
    private static int portFromEnvironment() {
        String value = System.getenv("PORT");
        if (value == null || value.trim().isEmpty()) return 8080;
        try {
            int port = Integer.parseInt(value.trim());
            return port > 0 && port <= 65535 ? port : 8080;
        } catch (NumberFormatException e) {
            return 8080;
        }
    }
}
