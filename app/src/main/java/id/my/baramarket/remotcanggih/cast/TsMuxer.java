package id.my.baramarket.remotcanggih.cast;

import java.io.ByteArrayOutputStream;

/**
 * Pengemas MPEG-TS minimal untuk video H.264 (tanpa audio), dipakai untuk siaran HLS mirror layar.
 * Input: satu access unit H.264 format Annex-B per panggilan.
 */
public final class TsMuxer {
    private static final int PID_PMT = 0x1000;
    private static final int PID_VIDEO = 0x100;
    private static final byte[] AUD = {0, 0, 0, 1, 0x09, (byte) 0xF0};

    private int ccPat, ccPmt, ccVideo;
    private byte[] spsPps;

    /** Menyimpan SPS/PPS (buffer codec-config dari encoder) untuk disisipkan di tiap keyframe. */
    public void setCodecConfig(byte[] annexB) {
        spsPps = annexB == null ? null : annexB.clone();
    }

    /** PAT + PMT; tulis di awal setiap segmen. */
    public void writeTables(ByteArrayOutputStream out) {
        out.write(psiPacket(0, ccPat++, pat()), 0, 188);
        out.write(psiPacket(PID_PMT, ccPmt++, pmt()), 0, 188);
    }

    /** Mengemas satu frame. ptsUs dalam mikrodetik. */
    public void writeFrame(ByteArrayOutputStream out, byte[] annexB, long ptsUs, boolean key) {
        ByteArrayOutputStream es = new ByteArrayOutputStream(annexB.length + 64);
        if (!startsWithAud(annexB)) es.write(AUD, 0, AUD.length);
        if (key && spsPps != null && !containsNal(annexB, 7)) es.write(spsPps, 0, spsPps.length);
        es.write(annexB, 0, annexB.length);
        byte[] payload = es.toByteArray();

        long pts90 = (ptsUs * 9 / 100) + 90_000L; // geser 1 detik supaya PCR < PTS
        byte[] pesHeader = new byte[14];
        pesHeader[0] = 0;
        pesHeader[1] = 0;
        pesHeader[2] = 1;
        pesHeader[3] = (byte) 0xE0;
        pesHeader[4] = 0; // panjang 0 = tidak dibatasi (boleh untuk video)
        pesHeader[5] = 0;
        pesHeader[6] = (byte) 0x80;
        pesHeader[7] = (byte) 0x80; // hanya PTS
        pesHeader[8] = 5;
        writePts(pesHeader, 9, pts90, 0x20);

        byte[] pes = new byte[pesHeader.length + payload.length];
        System.arraycopy(pesHeader, 0, pes, 0, pesHeader.length);
        System.arraycopy(payload, 0, pes, pesHeader.length, payload.length);

        long pcr = pts90 - 9000; // PCR 100 ms sebelum PTS
        int pos = 0;
        boolean first = true;
        while (pos < pes.length) {
            byte[] pkt = new byte[188];
            pkt[0] = 0x47;
            pkt[1] = (byte) ((first ? 0x40 : 0) | ((PID_VIDEO >> 8) & 0x1F));
            pkt[2] = (byte) (PID_VIDEO & 0xFF);
            int headerLen = 4;
            int remaining = pes.length - pos;

            int afLen = -1; // -1 = tanpa adaptation field
            int afFlags = 0;
            boolean withPcr = first;
            if (withPcr) {
                afLen = 7; // flags(1) + PCR(6)
                afFlags = 0x10 | (key ? 0x40 : 0);
            }
            int space = 188 - headerLen - (afLen >= 0 ? afLen + 1 : 0);
            if (remaining < space) {
                // perlu stuffing lewat adaptation field
                int stuff = space - remaining;
                if (afLen < 0) {
                    afLen = stuff - 1;
                    if (afLen > 0) afFlags = 0;
                } else {
                    afLen += stuff;
                }
                space = remaining;
            }
            int cc = ccVideo++ & 0x0F;
            pkt[3] = (byte) ((afLen >= 0 ? 0x30 : 0x10) | cc);
            int p = 4;
            if (afLen >= 0) {
                pkt[p++] = (byte) afLen;
                if (afLen > 0) {
                    pkt[p++] = (byte) afFlags;
                    int written = 1;
                    if ((afFlags & 0x10) != 0) {
                        long base = pcr & 0x1FFFFFFFFL;
                        pkt[p++] = (byte) (base >> 25);
                        pkt[p++] = (byte) (base >> 17);
                        pkt[p++] = (byte) (base >> 9);
                        pkt[p++] = (byte) (base >> 1);
                        pkt[p++] = (byte) (((base & 1) << 7) | 0x7E);
                        pkt[p++] = 0;
                        written += 6;
                    }
                    while (written < afLen) {
                        pkt[p++] = (byte) 0xFF;
                        written++;
                    }
                }
            }
            System.arraycopy(pes, pos, pkt, p, space);
            pos += space;
            out.write(pkt, 0, 188);
            first = false;
        }
    }

    private static void writePts(byte[] b, int off, long pts, int marker) {
        b[off] = (byte) (marker | (((pts >> 30) & 0x07) << 1) | 1);
        b[off + 1] = (byte) (pts >> 22);
        b[off + 2] = (byte) ((((pts >> 15) & 0x7F) << 1) | 1);
        b[off + 3] = (byte) (pts >> 7);
        b[off + 4] = (byte) (((pts & 0x7F) << 1) | 1);
    }

    private static boolean startsWithAud(byte[] b) {
        int i = nalStart(b, 0);
        return i >= 0 && i < b.length && (b[i] & 0x1F) == 9;
    }

    private static boolean containsNal(byte[] b, int type) {
        int i = 0;
        while ((i = nalStart(b, i)) >= 0 && i < b.length) {
            if ((b[i] & 0x1F) == type) return true;
        }
        return false;
    }

    /** Posisi byte header NAL berikutnya setelah start code, atau -1. */
    private static int nalStart(byte[] b, int from) {
        for (int i = from; i + 2 < b.length; i++) {
            if (b[i] == 0 && b[i + 1] == 0 && b[i + 2] == 1) return i + 3;
        }
        return -1;
    }

    // ------------------------------------------------------------------ tabel PSI

    private static byte[] pat() {
        byte[] s = {
                0x00, (byte) 0xB0, 0x0D, 0x00, 0x01, (byte) 0xC1, 0x00, 0x00,
                0x00, 0x01, (byte) (0xE0 | (PID_PMT >> 8)), (byte) (PID_PMT & 0xFF),
        };
        return withCrc(s);
    }

    private static byte[] pmt() {
        byte[] s = {
                0x02, (byte) 0xB0, 0x12, 0x00, 0x01, (byte) 0xC1, 0x00, 0x00,
                (byte) (0xE0 | (PID_VIDEO >> 8)), (byte) (PID_VIDEO & 0xFF), (byte) 0xF0, 0x00,
                0x1B, (byte) (0xE0 | (PID_VIDEO >> 8)), (byte) (PID_VIDEO & 0xFF), (byte) 0xF0, 0x00,
        };
        return withCrc(s);
    }

    private static byte[] psiPacket(int pid, int cc, byte[] section) {
        byte[] pkt = new byte[188];
        java.util.Arrays.fill(pkt, (byte) 0xFF);
        pkt[0] = 0x47;
        pkt[1] = (byte) (0x40 | ((pid >> 8) & 0x1F));
        pkt[2] = (byte) (pid & 0xFF);
        pkt[3] = (byte) (0x10 | (cc & 0x0F));
        pkt[4] = 0; // pointer field
        System.arraycopy(section, 0, pkt, 5, section.length);
        return pkt;
    }

    private static byte[] withCrc(byte[] s) {
        int crc = 0xFFFFFFFF;
        for (byte value : s) {
            crc ^= (value & 0xFF) << 24;
            for (int i = 0; i < 8; i++) crc = (crc & 0x80000000) != 0 ? (crc << 1) ^ 0x04C11DB7 : crc << 1;
        }
        byte[] r = new byte[s.length + 4];
        System.arraycopy(s, 0, r, 0, s.length);
        r[s.length] = (byte) (crc >> 24);
        r[s.length + 1] = (byte) (crc >> 16);
        r[s.length + 2] = (byte) (crc >> 8);
        r[s.length + 3] = (byte) crc;
        return r;
    }
}
