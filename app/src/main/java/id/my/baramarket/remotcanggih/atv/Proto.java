package id.my.baramarket.remotcanggih.atv;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Encoder/decoder protobuf minimal (cukup untuk protokol Android TV Remote v2). */
public final class Proto {
    private Proto() {}

    public static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        private void rawVarint(long v) {
            while ((v & ~0x7FL) != 0) {
                out.write((int) ((v & 0x7F) | 0x80));
                v >>>= 7;
            }
            out.write((int) v);
        }

        private void tag(int field, int wireType) {
            rawVarint(((long) field << 3) | wireType);
        }

        public Writer varint(int field, long value) {
            tag(field, 0);
            rawVarint(value);
            return this;
        }

        public Writer bytes(int field, byte[] value) {
            tag(field, 2);
            rawVarint(value.length);
            out.write(value, 0, value.length);
            return this;
        }

        public Writer string(int field, String value) {
            return bytes(field, value.getBytes(StandardCharsets.UTF_8));
        }

        public Writer message(int field, Writer msg) {
            return bytes(field, msg.toByteArray());
        }

        public byte[] toByteArray() {
            return out.toByteArray();
        }
    }

    /** Hasil parse: nomor field -> daftar nilai (Long untuk varint, byte[] untuk length-delimited). */
    public static final class Msg {
        public final Map<Integer, List<Object>> fields = new LinkedHashMap<>();

        public boolean has(int field) {
            return fields.containsKey(field);
        }

        public long getLong(int field, long def) {
            List<Object> l = fields.get(field);
            if (l == null || l.isEmpty() || !(l.get(0) instanceof Long)) return def;
            return (Long) l.get(0);
        }

        public byte[] getBytes(int field) {
            List<Object> l = fields.get(field);
            if (l == null || l.isEmpty() || !(l.get(0) instanceof byte[])) return null;
            return (byte[]) l.get(0);
        }

        public String getString(int field) {
            byte[] b = getBytes(field);
            return b == null ? null : new String(b, StandardCharsets.UTF_8);
        }

        public Msg getMsg(int field) {
            byte[] b = getBytes(field);
            if (b == null) return null;
            try {
                return parse(b);
            } catch (IOException e) {
                return null;
            }
        }
    }

    public static Msg parse(byte[] data) throws IOException {
        Msg m = new Msg();
        int[] pos = {0};
        while (pos[0] < data.length) {
            long key = readVarint(data, pos);
            int field = (int) (key >>> 3);
            int wt = (int) (key & 7);
            Object val;
            switch (wt) {
                case 0:
                    val = readVarint(data, pos);
                    break;
                case 1:
                    pos[0] += 8;
                    continue;
                case 2: {
                    int len = (int) readVarint(data, pos);
                    if (len < 0 || pos[0] + len > data.length) throw new IOException("Panjang tidak valid");
                    byte[] b = new byte[len];
                    System.arraycopy(data, pos[0], b, 0, len);
                    pos[0] += len;
                    val = b;
                    break;
                }
                case 5:
                    pos[0] += 4;
                    continue;
                default:
                    throw new IOException("Wire type tidak dikenal: " + wt);
            }
            List<Object> l = m.fields.get(field);
            if (l == null) {
                l = new ArrayList<>();
                m.fields.put(field, l);
            }
            l.add(val);
        }
        return m;
    }

    private static long readVarint(byte[] data, int[] pos) throws IOException {
        long result = 0;
        int shift = 0;
        while (true) {
            if (pos[0] >= data.length) throw new IOException("Varint terpotong");
            int b = data[pos[0]++] & 0xFF;
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return result;
            shift += 7;
            if (shift > 63) throw new IOException("Varint terlalu panjang");
        }
    }

    /** Membaca satu pesan yang diawali panjang (varint) dari stream. */
    public static byte[] readDelimited(InputStream in) throws IOException {
        long len = 0;
        int shift = 0;
        while (true) {
            int b = in.read();
            if (b < 0) throw new EOFException("Koneksi ditutup TV");
            len |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
            if (shift > 35) throw new IOException("Panjang pesan tidak valid");
        }
        if (len > 1 << 20) throw new IOException("Pesan terlalu besar");
        byte[] buf = new byte[(int) len];
        int off = 0;
        while (off < buf.length) {
            int r = in.read(buf, off, buf.length - off);
            if (r < 0) throw new EOFException("Koneksi ditutup TV");
            off += r;
        }
        return buf;
    }

    /** Menambahkan prefix panjang (varint) di depan pesan. */
    public static byte[] delimit(byte[] msg) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(msg.length + 5);
        long v = msg.length;
        while ((v & ~0x7FL) != 0) {
            out.write((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        out.write((int) v);
        out.write(msg, 0, msg.length);
        return out.toByteArray();
    }
}
