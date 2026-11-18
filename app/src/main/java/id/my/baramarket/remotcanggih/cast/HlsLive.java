package id.my.baramarket.remotcanggih.cast;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Locale;

/**
 * Siaran HLS langsung: frame H.264 dikemas jadi segmen TS pendek, dengan playlist bergeser.
 */
public final class HlsLive {
    private static final int KEEP = 6;
    private static final int LIST = 4;

    private static final class Segment {
        final long seq;
        final double duration;
        final byte[] data;

        Segment(long seq, double duration, byte[] data) {
            this.seq = seq;
            this.duration = duration;
            this.data = data;
        }
    }

    private final TsMuxer mux = new TsMuxer();
    private final ArrayDeque<Segment> segments = new ArrayDeque<>();
    private final long targetUs;
    private ByteArrayOutputStream current;
    private long currentStartUs = -1;
    private long lastPtsUs;
    private long nextSeq = 0;

    public HlsLive(long targetSegmentUs) {
        this.targetUs = targetSegmentUs;
    }

    public synchronized void setCodecConfig(byte[] annexB) {
        mux.setCodecConfig(annexB);
    }

    /** Menambah satu frame. Segmen baru hanya dimulai di keyframe. */
    public synchronized void addFrame(byte[] annexB, long ptsUs, boolean key) {
        if (current == null) {
            if (!key) return; // tunggu keyframe pertama
            startSegment(ptsUs);
        } else if (key && ptsUs - currentStartUs >= targetUs - 100_000L) {
            finishSegment(ptsUs);
            startSegment(ptsUs);
        }
        mux.writeFrame(current, annexB, ptsUs, key);
        lastPtsUs = ptsUs;
    }

    private void startSegment(long ptsUs) {
        current = new ByteArrayOutputStream(512 * 1024);
        currentStartUs = ptsUs;
        mux.writeTables(current);
    }

    private void finishSegment(long endUs) {
        double dur = Math.max(0.1, (endUs - currentStartUs) / 1_000_000.0);
        segments.addLast(new Segment(nextSeq++, dur, current.toByteArray()));
        while (segments.size() > KEEP) segments.removeFirst();
        notifyAll();
    }

    /** Jumlah segmen yang sudah siap. */
    public synchronized int readyCount() {
        return segments.size();
    }

    /** Menunggu sampai ada minimal n segmen (dipakai sebelum memberi tahu TV). */
    public synchronized boolean awaitSegments(int n, long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (segments.size() < n) {
            long left = end - System.currentTimeMillis();
            if (left <= 0) return false;
            wait(left);
        }
        return true;
    }

    public synchronized String playlist() {
        StringBuilder sb = new StringBuilder();
        int skip = Math.max(0, segments.size() - LIST);
        double max = 1;
        int i = 0;
        for (Segment s : segments) {
            if (i++ >= skip) max = Math.max(max, s.duration);
        }
        long first = segments.isEmpty() ? 0 : segments.peekFirst().seq + skip;
        sb.append("#EXTM3U\n#EXT-X-VERSION:3\n");
        sb.append("#EXT-X-TARGETDURATION:").append((int) Math.ceil(max)).append('\n');
        sb.append("#EXT-X-MEDIA-SEQUENCE:").append(first).append('\n');
        i = 0;
        for (Segment s : segments) {
            if (i++ < skip) continue;
            sb.append(String.format(Locale.US, "#EXTINF:%.3f,\n", s.duration));
            sb.append("seg").append(s.seq).append(".ts\n");
        }
        return sb.toString();
    }

    /** Data segmen, atau null bila sudah tidak ada. */
    public synchronized byte[] segment(long seq) {
        Iterator<Segment> it = segments.iterator();
        while (it.hasNext()) {
            Segment s = it.next();
            if (s.seq == seq) return s.data;
        }
        return null;
    }
}
