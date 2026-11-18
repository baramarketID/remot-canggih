package id.my.baramarket.remotcanggih.atv;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;

/**
 * Koneksi remot ke Android TV / Google TV (port 6466).
 * Memerlukan sertifikat klien yang sudah di-pairing.
 */
public final class AtvRemote {
    public static final int PORT = 6466;

    /** Bitmask fitur: ping, key, ime, power, volume, app link. */
    private static final int FEATURES = 622;

    public static final int DIR_SHORT = 3;

    public interface Listener {
        void onReady();

        void onPowerChanged(boolean on);

        void onDisconnected(Exception error);
    }

    /** Dilempar bila TV menolak sertifikat (belum pairing / pairing dihapus dari TV). */
    public static final class NotPairedException extends IOException {
        public NotPairedException(Throwable cause) {
            super("Perlu pairing", cause);
        }
    }

    private final AtvCert cert;
    private final String host;
    private final int port;
    private final Listener listener;
    private final String model;
    private final String vendor;
    private final String appVersion;

    private SSLSocket socket;
    private OutputStream out;
    private volatile boolean closed;
    private volatile boolean ready;
    private volatile int imeCounter;
    private volatile int fieldCounter;
    private volatile boolean imeSeen;

    public AtvRemote(AtvCert cert, String host, int port, String model, String vendor,
                     String appVersion, Listener listener) {
        this.cert = cert;
        this.host = host;
        this.port = port;
        this.model = model;
        this.vendor = vendor;
        this.appVersion = appVersion;
        this.listener = listener;
    }

    /** Membuka koneksi (blocking). Setelah berhasil, pembacaan berjalan di thread sendiri. */
    public void connect() throws Exception {
        SSLSocket s = (SSLSocket) cert.sslContext().getSocketFactory().createSocket();
        try {
            s.connect(new InetSocketAddress(host, port), 5000);
            s.setSoTimeout(8000);
            s.startHandshake();
        } catch (SSLException e) {
            closeQuietly(s);
            throw new NotPairedException(e);
        } catch (Exception e) {
            closeQuietly(s);
            throw e;
        }
        socket = s;
        out = s.getOutputStream();
        final InputStream in = s.getInputStream();

        // Pesan pertama dari TV harus remote_configure. Jika TV memutus di sini,
        // biasanya karena sertifikat belum di-pairing.
        Proto.Msg first;
        try {
            first = Proto.parse(Proto.readDelimited(in));
        } catch (SSLException e) {
            close();
            throw new NotPairedException(e);
        } catch (IOException e) {
            close();
            throw new NotPairedException(e);
        }
        handle(first);
        s.setSoTimeout(0);

        Thread t = new Thread(() -> {
            Exception err = null;
            try {
                while (!closed) handle(Proto.parse(Proto.readDelimited(in)));
            } catch (Exception e) {
                err = e;
            }
            boolean wasClosed = closed;
            close();
            if (!wasClosed) listener.onDisconnected(err);
        }, "atv-remote-reader");
        t.setDaemon(true);
        t.start();
    }

    private void handle(Proto.Msg m) throws IOException {
        if (m.has(1)) { // remote_configure
            Proto.Writer info = new Proto.Writer()
                    .string(1, model)
                    .string(2, vendor)
                    .varint(3, 1)
                    .string(4, "1")
                    .string(5, "atvremote")
                    .string(6, appVersion);
            send(new Proto.Writer().message(1, new Proto.Writer().varint(1, FEATURES).message(2, info)));
        }
        if (m.has(2)) { // remote_set_active
            send(new Proto.Writer().message(2, new Proto.Writer().varint(1, FEATURES)));
            if (!ready) {
                ready = true;
                listener.onReady();
            }
        }
        if (m.has(8)) { // ping
            Proto.Msg ping = m.getMsg(8);
            long v = ping == null ? 0 : ping.getLong(1, 0);
            send(new Proto.Writer().message(9, new Proto.Writer().varint(1, v)));
        }
        if (m.has(21)) { // ime_batch_edit dari TV
            Proto.Msg b = m.getMsg(21);
            if (b != null) {
                imeCounter = (int) b.getLong(1, imeCounter);
                fieldCounter = (int) b.getLong(2, fieldCounter);
                imeSeen = true;
            }
        }
        if (m.has(22)) { // ime_show_request
            Proto.Msg r = m.getMsg(22);
            Proto.Msg st = r == null ? null : r.getMsg(2);
            if (st != null) {
                fieldCounter = (int) st.getLong(1, fieldCounter);
                imeSeen = true;
            }
        }
        if (m.has(40)) { // remote_start
            Proto.Msg st = m.getMsg(40);
            listener.onPowerChanged(st != null && st.getLong(1, 0) != 0);
        }
    }

    public boolean isReady() {
        return ready && !closed;
    }

    /** True bila TV sedang menampilkan kolom teks yang bisa diisi langsung. */
    public boolean canTypeDirectly() {
        return imeSeen;
    }

    public void sendKey(int keyCode) throws IOException {
        sendKey(keyCode, DIR_SHORT);
    }

    public void sendKey(int keyCode, int direction) throws IOException {
        send(new Proto.Writer().message(10, new Proto.Writer().varint(1, keyCode).varint(2, direction)));
    }

    /** Mengisi kolom teks di TV dengan teks lengkap (menggantikan isinya). */
    public void sendText(String text) throws IOException {
        int cursor = Math.max(0, text.length() - 1);
        Proto.Writer obj = new Proto.Writer().varint(1, cursor).varint(2, cursor).string(3, text);
        Proto.Writer edit = new Proto.Writer().varint(1, 1).message(2, obj);
        Proto.Writer batch = new Proto.Writer().varint(1, imeCounter).varint(2, fieldCounter).message(3, edit);
        send(new Proto.Writer().message(21, batch));
    }

    /** Membuka aplikasi lewat tautan (mis. https://www.youtube.com). */
    public void launchApp(String link) throws IOException {
        send(new Proto.Writer().message(90, new Proto.Writer().string(1, link)));
    }

    private synchronized void send(Proto.Writer w) throws IOException {
        OutputStream o = out;
        if (o == null || closed) throw new IOException("Tidak terhubung");
        o.write(Proto.delimit(w.toByteArray()));
        o.flush();
    }

    public void close() {
        closed = true;
        closeQuietly(socket);
    }

    private static void closeQuietly(SSLSocket s) {
        try {
            if (s != null) s.close();
        } catch (IOException ignored) {
        }
    }
}
