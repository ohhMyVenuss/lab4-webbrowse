package vn.ltm.web.server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/** HTTP/1.1 teaching server supporting GET, HEAD, POST, downloads and byte ranges. */
public final class WebServerMain {
    private static final long MAX_UPLOAD_BYTES = 50L * 1024 * 1024;
    private static final List<String> HISTORY = new CopyOnWriteArrayList<>();
    private static Path webRoot;
    private static Path uploadRoot;
    private static String bindAddress;
    private static int port;

    private WebServerMain() { }

    public static void main(String[] args) throws Exception {
        Properties config = new Properties();
        Path configFile = Path.of("config", "server.properties");
        if (Files.exists(configFile)) {
            try (InputStream input = Files.newInputStream(configFile)) {
                config.load(input);
            }
        }

        bindAddress = config.getProperty("web.bind", "0.0.0.0").trim();
        port = Integer.parseInt(config.getProperty("web.port", "8080").trim());
        webRoot = Path.of(config.getProperty("web.root", "webroot")).toAbsolutePath().normalize();
        uploadRoot = Path.of(config.getProperty("web.uploads", "data/uploads")).toAbsolutePath().normalize();
        Files.createDirectories(webRoot);
        Files.createDirectories(webRoot.resolve("files"));
        Files.createDirectories(uploadRoot);

        HttpServer server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        server.createContext("/", WebServerMain::handle);
        server.setExecutor(Executors.newFixedThreadPool(Math.max(8, Runtime.getRuntime().availableProcessors() * 2)));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1), "http-shutdown"));
        server.start();
        System.out.printf("[WEB] HTTP server ready at http://%s:%d/ | root=%s%n", bindAddress, port, webRoot);
        System.out.println("[WEB] Sample resources: /files/document.pdf, /files/music.mp3, /files/video.mp4");
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        String requestPath = exchange.getRequestURI().getPath();
        HISTORY.add(Instant.now() + "  " + method + "  " + exchange.getRequestURI() + "  " + exchange.getRemoteAddress());
        System.out.printf("[HTTP] %s %s from %s%n", method, exchange.getRequestURI(), exchange.getRemoteAddress());

        try {
            if (requestPath.equals("/api/login")) {
                handleLogin(exchange, method);
                return;
            }
            if (requestPath.equals("/api/history")) {
                handleHistory(exchange, method);
                return;
            }
            if (requestPath.equals("/api/upload")) {
                handleUpload(exchange, method);
                return;
            }
            if (requestPath.startsWith("/download/")) {
                handleDownload(exchange, method, requestPath.substring("/download/".length()));
                return;
            }
            if (!method.equals("GET") && !method.equals("HEAD")) {
                sendText(exchange, 405, "Method Not Allowed", method.equals("HEAD"));
                return;
            }

            String relative = requestPath.equals("/") || requestPath.equals("/home")
                    ? "index.html" : requestPath.substring(1);
            Path file = webRoot.resolve(relative).normalize();
            if (!file.startsWith(webRoot)) {
                sendText(exchange, 403, "Forbidden", method.equals("HEAD"));
                return;
            }
            if (Files.isDirectory(file)) file = file.resolve("index.html");
            serveFile(exchange, file, method, false);
        } catch (Exception exception) {
            System.err.println("[HTTP] Request error: " + exception.getMessage());
            if (exchange.getResponseCode() == -1) {
                try { sendText(exchange, 500, "Internal Server Error", method.equals("HEAD")); }
                catch (IOException ignored) { exchange.close(); }
            } else {
                exchange.close();
            }
        }
    }

    private static void handleLogin(HttpExchange exchange, String method) throws IOException {
        if (!method.equals("POST")) {
            sendText(exchange, 405, "Method Not Allowed", method.equals("HEAD"));
            return;
        }
        byte[] body = readLimited(exchange.getRequestBody(), 1024 * 1024);
        String value = new String(body, StandardCharsets.UTF_8);
        boolean accepted = value.contains("student") && value.contains("password");
        send(exchange, accepted ? 200 : 401,
                accepted ? "{\"status\":\"ok\",\"message\":\"demo login accepted\"}"
                        : "{\"error\":\"invalid credentials\"}",
                "application/json; charset=utf-8", false);
    }

    private static void handleHistory(HttpExchange exchange, String method) throws IOException {
        if (!method.equals("GET") && !method.equals("HEAD")) {
            sendText(exchange, 405, "Method Not Allowed", method.equals("HEAD"));
            return;
        }
        List<String> lastEntries = HISTORY.stream().skip(Math.max(0, HISTORY.size() - 50)).toList();
        String json = "[" + String.join(",", lastEntries.stream()
                .map(value -> "{\"event\":\"" + escapeJson(value) + "\"}").toList()) + "]";
        send(exchange, 200, json, "application/json; charset=utf-8", method.equals("HEAD"));
    }

    private static void handleUpload(HttpExchange exchange, String method) throws IOException {
        if (!method.equals("POST")) {
            sendText(exchange, 405, "Method Not Allowed", method.equals("HEAD"));
            return;
        }
        String suppliedName = exchange.getRequestHeaders().getFirst("X-File-Name");
        String safeName = sanitizeName(suppliedName == null || suppliedName.isBlank()
                ? "upload-" + System.currentTimeMillis() + ".bin" : suppliedName);
        Path destination = uploadRoot.resolve(safeName).normalize();
        if (!destination.startsWith(uploadRoot)) {
            sendText(exchange, 400, "Invalid filename", false);
            return;
        }
        byte[] bytes = readLimited(exchange.getRequestBody(), MAX_UPLOAD_BYTES);
        Files.write(destination, bytes);
        send(exchange, 201, "{\"uploaded\":\"" + escapeJson(safeName) + "\",\"bytes\":" + bytes.length + "}",
                "application/json; charset=utf-8", false);
    }

    private static void handleDownload(HttpExchange exchange, String method, String requestedName) throws IOException {
        if (!method.equals("GET") && !method.equals("HEAD")) {
            sendText(exchange, 405, "Method Not Allowed", method.equals("HEAD"));
            return;
        }
        String safeName = sanitizeName(requestedName);
        if (!safeName.equals(requestedName) || safeName.isBlank() || safeName.equals(".") || safeName.equals("..")) {
            sendText(exchange, 400, "Invalid filename", method.equals("HEAD"));
            return;
        }
        Path filesRoot = webRoot.resolve("files").normalize();
        Path file = filesRoot.resolve(safeName).normalize();
        if (!file.startsWith(filesRoot)) {
            sendText(exchange, 403, "Forbidden", method.equals("HEAD"));
            return;
        }
        serveFile(exchange, file, method, true);
    }

    private static void serveFile(HttpExchange exchange, Path file, String method, boolean attachment) throws IOException {
        if (!Files.isRegularFile(file)) {
            sendText(exchange, 404, "Not Found: " + exchange.getRequestURI().getPath(), method.equals("HEAD"));
            return;
        }

        long size = Files.size(file);
        String contentType = contentType(file);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Accept-Ranges", "bytes");
        headers.set("X-Content-Type-Options", "nosniff");
        if (attachment) {
            headers.set("Content-Disposition", "attachment; filename=\"" + sanitizeName(file.getFileName().toString()) + "\"");
        }

        long start = 0;
        long end = Math.max(0, size - 1);
        int status = 200;
        String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
        if (rangeHeader != null && rangeHeader.startsWith("bytes=") && size > 0) {
            try {
                String range = rangeHeader.substring("bytes=".length()).trim();
                String[] values = range.split("-", 2);
                if (values.length != 2) throw new NumberFormatException("Invalid Range");
                if (values[0].isBlank()) {
                    long suffixLength = Long.parseLong(values[1]);
                    if (suffixLength <= 0) throw new NumberFormatException("Invalid suffix range");
                    start = Math.max(0, size - suffixLength);
                } else {
                    start = Long.parseLong(values[0]);
                    if (!values[1].isBlank()) end = Long.parseLong(values[1]);
                }
                if (start < 0 || start >= size || end < start) throw new NumberFormatException("Range outside resource");
                end = Math.min(end, size - 1);
                status = 206;
                headers.set("Content-Range", "bytes " + start + "-" + end + "/" + size);
            } catch (NumberFormatException exception) {
                headers.set("Content-Range", "bytes */" + size);
                sendText(exchange, 416, "Range Not Satisfiable", method.equals("HEAD"));
                return;
            }
        }

        long length = size == 0 ? 0 : end - start + 1;
        headers.set("Content-Length", Long.toString(length));
        if (method.equals("HEAD") || length == 0) {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(status, length);
        try (var input = Files.newInputStream(file); OutputStream output = exchange.getResponseBody()) {
            input.skipNBytes(start);
            byte[] buffer = new byte[16 * 1024];
            long remaining = length;
            while (remaining > 0) {
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) break;
                output.write(buffer, 0, read);
                remaining -= read;
            }
        }
    }

    private static byte[] readLimited(InputStream input, long limit) throws IOException {
        try (input; var output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                total += count;
                if (total > limit) throw new IOException("Request body exceeds " + limit + " bytes");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static void sendText(HttpExchange exchange, int code, String body, boolean head) throws IOException {
        send(exchange, code, body, "text/plain; charset=utf-8", head);
    }

    private static void send(HttpExchange exchange, int code, String body, String type, boolean head) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Content-Length", Integer.toString(bytes.length));
        if (head) {
            exchange.sendResponseHeaders(code, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".mp4")) return "video/mp4";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".txt")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }

    private static String sanitizeName(String supplied) {
        if (supplied == null) return "";
        String name = supplied.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        return name.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }
}
