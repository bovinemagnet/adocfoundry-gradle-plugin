package io.github.bovinemagnet.adocfoundry.tool;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A release served by an in-process HTTP server: {@code /v<version>/<file>} answers with the
 * files given, and every request is counted by path.
 */
final class FakeRelease implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final Map<String, String> redirects = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();
    private volatile long delayMillis;

    FakeRelease() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requests.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();
            try {
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String location = redirects.get(path);
            byte[] body = files.get(path);
            if (location != null) {
                exchange.getResponseHeaders().add("Location", location);
                exchange.sendResponseHeaders(302, -1);
            } else if (body == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            exchange.close();
        });
        server.start();
    }

    String baseUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/releases";
    }

    /** Serves {@code body} at {@code /releases/<relative>}. */
    FakeRelease serve(String relative, byte[] body) {
        files.put("/releases/" + relative, body);
        return this;
    }

    /** Answers {@code /releases/<relative>} with a redirect to {@code /releases/<target>}. */
    FakeRelease redirect(String relative, String target) {
        redirects.put("/releases/" + relative, "/releases/" + target);
        return this;
    }

    FakeRelease delay(long millis) {
        this.delayMillis = millis;
        return this;
    }

    int requestsFor(String relative) {
        AtomicInteger count = requests.get("/releases/" + relative);
        return count == null ? 0 : count.get();
    }

    int totalRequests() {
        return requests.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Entries in order, from archive path to content. */
    static Map<String, String> entries(String... pathsAndContents) {
        Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i < pathsAndContents.length; i += 2) {
            entries.put(pathsAndContents[i], pathsAndContents[i + 1]);
        }
        return entries;
    }

    /** A gzipped ustar archive of the entries, each a regular file with mode 755. */
    static byte[] tarGz(Map<String, String> entries) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                byte[] content = entry.getValue().getBytes(StandardCharsets.UTF_8);
                out.write(tarHeader(entry.getKey(), content.length));
                out.write(content);
                out.write(new byte[(512 - content.length % 512) % 512]);
            }
            out.write(new byte[1024]);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static byte[] tarHeader(String name, int size) {
        byte[] header = new byte[512];
        put(header, 0, name, 100);
        put(header, 100, "0000755", 8);
        put(header, 108, "0000000", 8);
        put(header, 116, "0000000", 8);
        put(header, 124, String.format("%011o", size), 12);
        put(header, 136, String.format("%011o", 0), 12);
        for (int i = 148; i < 156; i++) {
            header[i] = ' ';
        }
        header[156] = '0';
        put(header, 257, "ustar", 6);
        put(header, 263, "00", 2);
        int checksum = 0;
        for (byte b : header) {
            checksum += b & 0xff;
        }
        put(header, 148, String.format("%06o", checksum), 7);
        header[155] = ' ';
        return header;
    }

    private static void put(byte[] header, int offset, String value, int length) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, header, offset, Math.min(bytes.length, length));
    }

    static byte[] zip(Map<String, String> entries) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }
}
