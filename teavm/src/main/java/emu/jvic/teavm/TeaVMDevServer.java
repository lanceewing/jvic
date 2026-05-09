package emu.jvic.teavm;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class TeaVMDevServer {

    private static final Map<String, String> CONTENT_TYPES = createContentTypes();

    public static void main(String[] args) throws Exception {
        Path rootPath = getRootPath(args);
        int port = getPort(args);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", new StaticHandler(rootPath));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));

        System.out.println("TeaVM dev server listening on http://127.0.0.1:" + port + "/");
        System.out.println("Serving files from " + rootPath.toAbsolutePath());

        new CountDownLatch(1).await();
    }

    private static Path getRootPath(String[] args) {
        String rootArg = ((args != null) && (args.length > 0) && (args[0] != null) && !args[0].isBlank())
                ? args[0]
                : "build/dist/webapp";
        return Path.of(rootArg).toAbsolutePath().normalize();
    }

    private static int getPort(String[] args) {
        if ((args != null) && (args.length > 1) && (args[1] != null) && !args[1].isBlank()) {
            return Integer.parseInt(args[1]);
        }
        return 8081;
    }

    private static Map<String, String> createContentTypes() {
        Map<String, String> contentTypes = new HashMap<>();
        contentTypes.put("html", "text/html; charset=utf-8");
        contentTypes.put("js", "text/javascript; charset=utf-8");
        contentTypes.put("json", "application/json; charset=utf-8");
        contentTypes.put("css", "text/css; charset=utf-8");
        contentTypes.put("txt", "text/plain; charset=utf-8");
        contentTypes.put("xml", "application/xml; charset=utf-8");
        contentTypes.put("ico", "image/x-icon");
        contentTypes.put("png", "image/png");
        contentTypes.put("jpg", "image/jpeg");
        contentTypes.put("jpeg", "image/jpeg");
        contentTypes.put("gif", "image/gif");
        contentTypes.put("svg", "image/svg+xml");
        contentTypes.put("wasm", "application/wasm");
        contentTypes.put("atlas", "text/plain; charset=utf-8");
        contentTypes.put("fnt", "text/plain; charset=utf-8");
        contentTypes.put("glsl", "text/plain; charset=utf-8");
        contentTypes.put("rom", "application/octet-stream");
        contentTypes.put("prg", "application/octet-stream");
        contentTypes.put("d64", "application/octet-stream");
        contentTypes.put("tap", "application/octet-stream");
        contentTypes.put("crt", "application/octet-stream");
        return contentTypes;
    }

    private static final class StaticHandler implements HttpHandler {

        private final Path rootPath;

        private StaticHandler(Path rootPath) {
            this.rootPath = rootPath;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) {
                    send(exchange, 405, "Method not allowed\n".getBytes(StandardCharsets.UTF_8), "text/plain; charset=utf-8");
                    return;
                }

                Path resolvedPath = resolvePath(exchange.getRequestURI().getPath());
                if ((resolvedPath == null) || !Files.exists(resolvedPath) || Files.isDirectory(resolvedPath)) {
                    send(exchange, 404, "Not found\n".getBytes(StandardCharsets.UTF_8), "text/plain; charset=utf-8");
                    return;
                }

                byte[] content = "HEAD".equals(exchange.getRequestMethod()) ? new byte[0] : Files.readAllBytes(resolvedPath);
                send(exchange, 200, content, getContentType(resolvedPath), Files.size(resolvedPath));
            } catch (Exception exception) {
                send(exchange, 500,
                        ("Internal server error\n" + exception.getMessage() + "\n").getBytes(StandardCharsets.UTF_8),
                        "text/plain; charset=utf-8");
            } finally {
                exchange.close();
            }
        }

        private Path resolvePath(String requestPath) {
            String decodedPath = URLDecoder.decode((requestPath == null) ? "/" : requestPath, StandardCharsets.UTF_8);
            if (decodedPath.isBlank() || "/".equals(decodedPath)) {
                return rootPath.resolve("index.html");
            }

            String relativePath = decodedPath.startsWith("/") ? decodedPath.substring(1) : decodedPath;
            Path resolvedPath = rootPath.resolve(relativePath).normalize();
            if (!resolvedPath.startsWith(rootPath)) {
                return null;
            }
            if (Files.isDirectory(resolvedPath)) {
                return resolvedPath.resolve("index.html");
            }
            return resolvedPath;
        }

        private String getContentType(Path path) {
            String fileName = path.getFileName().toString();
            int extensionIndex = fileName.lastIndexOf('.');
            if ((extensionIndex < 0) || (extensionIndex == (fileName.length() - 1))) {
                return "application/octet-stream";
            }
            String extension = fileName.substring(extensionIndex + 1).toLowerCase();
            return CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
        }

        private void send(HttpExchange exchange, int statusCode, byte[] content, String contentType) throws IOException {
            send(exchange, statusCode, content, contentType, content.length);
        }

        private void send(HttpExchange exchange, int statusCode, byte[] content, String contentType, long contentLength)
                throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", contentType);
            headers.set("Cross-Origin-Opener-Policy", "same-origin");
            headers.set("Cross-Origin-Embedder-Policy", "credentialless");
            headers.set("Cache-Control", "no-store, max-age=0");

            exchange.sendResponseHeaders(statusCode, contentLength);
            if (content.length > 0) {
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(content);
                }
            }
        }
    }
}