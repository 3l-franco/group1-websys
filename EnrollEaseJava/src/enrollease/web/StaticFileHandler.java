package enrollease.web;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/** Sends the HTML, CSS, JavaScript and image files from the "web" folder to the browser. */
public class StaticFileHandler implements HttpHandler {

    private File root;

    public StaticFileHandler(File root) {
        this.root = root;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) {
            path = "/index.html";
        }
        File file = new File(root, path).getCanonicalFile();
        // Safety: never serve anything outside the web folder (blocks "../" tricks).
        boolean inside = file.getPath().startsWith(root.getCanonicalPath());
        if (!inside || !file.isFile()) {
            byte[] msg = "404 - Page not found".getBytes("UTF-8");
            exchange.sendResponseHeaders(404, msg.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(msg);
            }
            return;
        }
        byte[] data = Files.readAllBytes(file.toPath());
        exchange.getResponseHeaders().set("Content-Type", contentType(file.getName()));
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(data);
        }
    }

    private String contentType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
