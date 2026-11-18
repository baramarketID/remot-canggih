package id.my.baramarket.remotcanggih.cast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Server HTTP kecil di HP: menyajikan file galeri (dengan Range, supaya video bisa digeser)
 * dan siaran HLS mirror layar ke TV di jaringan yang sama.
 */
public final class MediaServer {

    /** Sumber file yang bisa dibuka mulai dari posisi tertentu. */
    public interface Source {
        long length();

        String mime();

        InputStream open(long offset) throws IOException;
    }

    private static final Pattern RANGE = Pattern.compile("bytes=(\\d*)-(\\d*)");
    private static final Pattern SEG = Pattern.compile("/live/seg(\\d+)\\.ts");

    private final Map<String, Source> files = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "media-server");
        t.setDaemon(true);
        return t;
    });
    private volatile HlsLive live;
    private ServerSocket server;

    /** Mulai di port pertama yang kosong dari daftar. Mengembalikan port yang dipakai. */
    public synchronized int start(InetAddress bind, int... ports) throws IOException {
        if (server != null) return server.getLocalPort();
        IOException last = null;
        for (int port : ports) {
            try {
                ServerSocket s = new ServerSocket();
                s.setReuseAddress(true);
                s.bind(new InetSocketAddress(bind, port));
                server = s;
                break;
            } catch (IOException e) {
                last = e;
            }
        }
        if (server == null) throw last != null ? last : new IOException("Tidak ada port kosong");
        final ServerSocket s = server;
        pool.execute(() -> {
            while (!s.isClosed()) {
                try {
                    final Socket c = s.accept();
                    pool.execute(() -> handle(c));
                } catch (IOException e) {
                    break;
                }
            }
        });
        return server.getLocalPort();
    }

    public synchronized void stop() {
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
        }
        server = null;
        files.clear();
        live = null;
    }

    public void putFile(String token, Source src) {
        files.put(token, src);
    }

    public void setLive(HlsLive l) {
        live = l;
    }

    // ------------------------------------------------------------------ HTTP

    private void handle(Socket c) {
        try (Socket sock = c) {
            sock.setSoTimeout(15000);
            BufferedReader r = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.ISO_8859_1));
            String line = r.readLine();
            if (line == null) return;
            String[] parts = line.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String path = parts[1];
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
            String range = null;
            String h;
            while ((h = r.readLine()) != null && !h.isEmpty()) {
                int i = h.indexOf(':');
                if (i > 0 && h.substring(0, i).trim().equalsIgnoreCase("range")) range = h.substring(i + 1).trim();
            }
            OutputStream out = sock.getOutputStream();
            boolean head = method.equals("HEAD");
            if (method.equals("OPTIONS")) {
                send(out, 204, "No Content", null, 0, null, false);
                return;
            }
            if (!method.equals("GET") && !head) {
                send(out, 405, "Method Not Allowed", "text/plain", 0, null, false);
                return;
            }

            if (path.equals("/live/index.m3u8")) {
                HlsLive l = live;
                if (l == null) {
                    send(out, 404, "Not Found", "text/plain", 0, null, false);
                    return;
                }
                byte[] body = l.playlist().getBytes(StandardCharsets.UTF_8);
                sendBytes(out, 200, "OK", "application/vnd.apple.mpegurl", body, head, "no-cache");
                return;
            }
            Matcher sm = SEG.matcher(path);
            if (sm.matches()) {
                HlsLive l = live;
                byte[] data = l == null ? null : l.segment(Long.parseLong(sm.group(1)));
                if (data == null) {
                    send(out, 404, "Not Found", "text/plain", 0, null, false);
                    return;
                }
                sendBytes(out, 200, "OK", "video/mp2t", data, head, "max-age=30");
                return;
            }
            if (path.startsWith("/m/")) {
                String token = path.substring(3);
                int dot = token.indexOf('.');
                if (dot >= 0) token = token.substring(0, dot);
                Source src = files.get(token);
                if (src == null) {
                    send(out, 404, "Not Found", "text/plain", 0, null, false);
                    return;
                }
                serveFile(out, src, range, head);
                return;
            }
            send(out, 404, "Not Found", "text/plain", 0, null, false);
        } catch (Exception ignored) {
            // klien memutus koneksi: abaikan
        }
    }

    private static void serveFile(OutputStream out, Source src, String range, boolean head) throws IOException {
        long len = src.length();
        long start = 0;
        long end = len - 1;
        boolean partial = false;
        if (range != null && len > 0) {
            Matcher m = RANGE.matcher(range);
            if (m.matches()) {
                String a = m.group(1);
                String b = m.group(2);
                if (a.isEmpty() && !b.isEmpty()) {
                    start = Math.max(0, len - Long.parseLong(b));
                } else {
                    start = Long.parseLong(a);
                    if (!b.isEmpty()) end = Math.min(end, Long.parseLong(b));
                }
                if (start > end || start >= len) {
                    String hdr = "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */" + len +
                            "\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                    out.write(hdr.getBytes(StandardCharsets.ISO_8859_1));
                    return;
                }
                partial = true;
            }
        }
        long count = len < 0 ? -1 : end - start + 1;
        StringBuilder sb = new StringBuilder();
        sb.append(partial ? "HTTP/1.1 206 Partial Content\r\n" : "HTTP/1.1 200 OK\r\n");
        sb.append("Content-Type: ").append(src.mime()).append("\r\n");
        sb.append("Accept-Ranges: bytes\r\n");
        if (count >= 0) sb.append("Content-Length: ").append(count).append("\r\n");
        if (partial) sb.append("Content-Range: bytes ").append(start).append('-').append(end).append('/').append(len).append("\r\n");
        sb.append("Access-Control-Allow-Origin: *\r\n");
        sb.append("Access-Control-Expose-Headers: Content-Length, Content-Range\r\n");
        sb.append("Connection: close\r\n\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (head) {
            out.flush();
            return;
        }
        try (InputStream in = src.open(start)) {
            byte[] buf = new byte[64 * 1024];
            long left = count;
            while (left != 0) {
                int want = left < 0 ? buf.length : (int) Math.min(buf.length, left);
                int n = in.read(buf, 0, want);
                if (n < 0) break;
                out.write(buf, 0, n);
                if (left > 0) left -= n;
            }
        }
        out.flush();
    }

    private static void sendBytes(OutputStream out, int code, String msg, String mime, byte[] body, boolean head,
                                  String cache) throws IOException {
        send(out, code, msg, mime, body.length, cache, false);
        if (!head) out.write(body);
        out.flush();
    }

    private static void send(OutputStream out, int code, String msg, String mime, long length, String cache,
                             boolean flush) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(code).append(' ').append(msg).append("\r\n");
        if (mime != null) sb.append("Content-Type: ").append(mime).append("\r\n");
        sb.append("Content-Length: ").append(length).append("\r\n");
        if (cache != null) sb.append("Cache-Control: ").append(cache).append("\r\n");
        sb.append("Access-Control-Allow-Origin: *\r\n");
        sb.append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n");
        sb.append("Access-Control-Allow-Headers: *\r\n");
        sb.append("Connection: close\r\n\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (flush) out.flush();
    }
}
