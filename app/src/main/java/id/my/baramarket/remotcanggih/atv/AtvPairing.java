package id.my.baramarket.remotcanggih.atv;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;

import javax.net.ssl.SSLSocket;

/**
 * Pairing dengan Android TV / Google TV (port 6467).
 * Alur: {@link #start()} → TV menampilkan kode 6 karakter → {@link #finish(String)}.
 */
public final class AtvPairing implements Closeable {
    public static final int PORT = 6467;

    private static final int OK = 200;
    private static final int ENC_HEX = 3;
    private static final int ROLE_INPUT = 1;

    private final AtvCert cert;
    private final String host;
    private final int port;
    private final String clientName;
    private SSLSocket socket;
    private InputStream in;
    private OutputStream out;

    public AtvPairing(AtvCert cert, String host, int port, String clientName) {
        this.cert = cert;
        this.host = host;
        this.port = port;
        this.clientName = clientName;
    }

    /** Membuka koneksi pairing. Setelah ini selesai, TV akan menampilkan kode. */
    public void start() throws Exception {
        socket = (SSLSocket) cert.sslContext().getSocketFactory().createSocket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        socket.setSoTimeout(60_000);
        socket.startHandshake();
        in = socket.getInputStream();
        out = socket.getOutputStream();

        send(outer().message(10, new Proto.Writer()
                .string(1, "atvremote")
                .string(2, clientName)));
        expect(11);

        Proto.Writer encoding = new Proto.Writer().varint(1, ENC_HEX).varint(2, 6);
        send(outer().message(20, new Proto.Writer()
                .message(1, encoding)
                .varint(3, ROLE_INPUT)));
        expect(20);

        send(outer().message(30, new Proto.Writer()
                .message(1, new Proto.Writer().varint(1, ENC_HEX).varint(2, 6))
                .varint(2, ROLE_INPUT)));
        expect(31);
    }

    /** Mengirim kode yang tampil di TV. Melempar {@link WrongCodeException} bila kode salah. */
    public void finish(String code) throws Exception {
        String c = code == null ? "" : code.trim().toUpperCase(java.util.Locale.US);
        if (!c.matches("[0-9A-F]{6}")) throw new WrongCodeException();

        Certificate[] peer = socket.getSession().getPeerCertificates();
        RSAPublicKey server = (RSAPublicKey) peer[0].getPublicKey();
        RSAPublicKey client = cert.publicKey();

        byte[] secret = computeSecret(client, server, c);
        if (secret == null) throw new WrongCodeException();

        send(outer().message(40, new Proto.Writer().bytes(1, secret)));
        Proto.Msg reply = read();
        if (reply.getLong(2, 0) != OK) throw new WrongCodeException();
        if (!reply.has(41)) throw new IOException("Balasan pairing tidak terduga");
    }

    /** Menghitung secret pairing. Mengembalikan null bila byte cek pada kode tidak cocok. */
    public static byte[] computeSecret(RSAPublicKey client, RSAPublicKey server, String code) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update(unsigned(client.getModulus()));
        sha.update(unsigned(client.getPublicExponent()));
        sha.update(unsigned(server.getModulus()));
        sha.update(unsigned(server.getPublicExponent()));
        sha.update(hex(code.substring(2)));
        byte[] hash = sha.digest();
        int check = Integer.parseInt(code.substring(0, 2), 16);
        return (hash[0] & 0xFF) == check ? hash : null;
    }

    static byte[] unsigned(BigInteger v) {
        byte[] b = v.toByteArray();
        int i = 0;
        while (i < b.length - 1 && b[i] == 0) i++;
        if (i == 0) return b;
        byte[] r = new byte[b.length - i];
        System.arraycopy(b, i, r, 0, r.length);
        return r;
    }

    private static byte[] hex(String s) {
        byte[] r = new byte[s.length() / 2];
        for (int i = 0; i < r.length; i++) r[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return r;
    }

    private static Proto.Writer outer() {
        return new Proto.Writer().varint(1, 2).varint(2, OK);
    }

    private void send(Proto.Writer w) throws IOException {
        out.write(Proto.delimit(w.toByteArray()));
        out.flush();
    }

    private Proto.Msg read() throws IOException {
        return Proto.parse(Proto.readDelimited(in));
    }

    private void expect(int field) throws IOException {
        Proto.Msg m = read();
        long status = m.getLong(2, 0);
        if (status != OK) throw new IOException("TV menolak pairing (status " + status + ")");
        if (!m.has(field)) throw new IOException("Balasan pairing tidak terduga");
    }

    @Override
    public void close() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }

    public static final class WrongCodeException extends Exception {
        public WrongCodeException() {
            super("Kode salah");
        }
    }
}
