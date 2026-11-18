package id.my.baramarket.remotcanggih.atv;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.security.Principal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Sertifikat klien (self-signed) yang dipakai untuk pairing dan koneksi ke Android TV.
 * Dibuat sekali lalu disimpan di penyimpanan aplikasi, supaya TV tetap mengenali HP ini.
 */
public final class AtvCert {
    private static final String ALIAS = "atvremote";
    private static final char[] PASS = "remotcanggih".toCharArray();

    private final PrivateKey privateKey;
    private final X509Certificate certificate;

    private AtvCert(PrivateKey key, X509Certificate cert) {
        this.privateKey = key;
        this.certificate = cert;
    }

    public RSAPublicKey publicKey() {
        return (RSAPublicKey) certificate.getPublicKey();
    }

    /** Memuat sertifikat dari file, atau membuat baru bila belum ada / rusak. */
    public static synchronized AtvCert loadOrCreate(File file) throws Exception {
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                KeyStore ks = KeyStore.getInstance("PKCS12");
                ks.load(in, PASS);
                PrivateKey key = (PrivateKey) ks.getKey(ALIAS, PASS);
                X509Certificate cert = (X509Certificate) ks.getCertificate(ALIAS);
                if (key != null && cert != null) return new AtvCert(key, cert);
            } catch (Exception ignored) {
                // file rusak: buat ulang di bawah
            }
        }
        AtvCert created = generate();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry(ALIAS, created.privateKey, PASS, new Certificate[]{created.certificate});
        File tmp = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            ks.store(out, PASS);
        }
        if (!tmp.renameTo(file)) throw new IllegalStateException("Gagal menyimpan sertifikat");
        return created;
    }

    /** Menghapus sertifikat (dipakai saat "lupakan semua TV"). */
    public static synchronized void delete(File file) {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    public SSLContext sslContext() throws Exception {
        // Selalu kirim sertifikat ini, apa pun daftar penerbit yang diminta TV.
        final PrivateKey key = privateKey;
        final X509Certificate[] chain = new X509Certificate[]{certificate};
        KeyManager[] keyManagers = new KeyManager[]{new X509ExtendedKeyManager() {
            @Override
            public String[] getClientAliases(String keyType, Principal[] issuers) {
                return new String[]{ALIAS};
            }

            @Override
            public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
                return ALIAS;
            }

            @Override
            public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
                return ALIAS;
            }

            @Override
            public String[] getServerAliases(String keyType, Principal[] issuers) {
                return new String[]{ALIAS};
            }

            @Override
            public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
                return ALIAS;
            }

            @Override
            public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
                return ALIAS;
            }

            @Override
            public X509Certificate[] getCertificateChain(String alias) {
                return chain;
            }

            @Override
            public PrivateKey getPrivateKey(String alias) {
                return key;
            }
        }};
        // TV memakai sertifikat self-signed; keamanan dijamin lewat kode PIN saat pairing.
        TrustManager[] trustAll = new TrustManager[]{new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(keyManagers, trustAll, new SecureRandom());
        return ctx;
    }

    // ---------------------------------------------------------------- pembuatan sertifikat

    private static AtvCert generate() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, new SecureRandom());
        KeyPair kp = kpg.generateKeyPair();

        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 24L * 3600 * 1000);
        Date notAfter = new Date(now + 20L * 365 * 24 * 3600 * 1000);
        BigInteger serial = new BigInteger(63, new SecureRandom()).add(BigInteger.ONE);

        byte[] sigAlg = seq(cat(oid(new int[]{1, 2, 840, 113549, 1, 1, 11}), new byte[]{0x05, 0x00}));
        byte[] name = seq(set(seq(cat(oid(new int[]{2, 5, 4, 3}),
                tlv(0x0C, "atvremote".getBytes(StandardCharsets.UTF_8))))));
        byte[] validity = seq(cat(time(notBefore), time(notAfter)));
        byte[] version = tlv(0xA0, tlv(0x02, new byte[]{0x02})); // v3

        byte[] tbs = seq(cat(version, tlv(0x02, serial.toByteArray()), sigAlg, name, validity, name,
                kp.getPublic().getEncoded()));

        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(kp.getPrivate());
        s.update(tbs);
        byte[] sig = s.sign();
        byte[] bitString = new byte[sig.length + 1];
        System.arraycopy(sig, 0, bitString, 1, sig.length);

        byte[] der = seq(cat(tbs, sigAlg, tlv(0x03, bitString)));
        X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));
        return new AtvCert(kp.getPrivate(), cert);
    }

    private static byte[] time(Date d) {
        SimpleDateFormat f = new SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return tlv(0x17, f.format(d).getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] oid(int[] parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(parts[0] * 40 + parts[1]);
        for (int i = 2; i < parts.length; i++) {
            int v = parts[i];
            byte[] tmp = new byte[5];
            int n = 0;
            do {
                tmp[n++] = (byte) (v & 0x7F);
                v >>>= 7;
            } while (v != 0);
            for (int j = n - 1; j >= 0; j--) out.write(tmp[j] | (j > 0 ? 0x80 : 0));
        }
        return tlv(0x06, out.toByteArray());
    }

    private static byte[] seq(byte[] content) {
        return tlv(0x30, content);
    }

    private static byte[] set(byte[] content) {
        return tlv(0x31, content);
    }

    private static byte[] tlv(int tag, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(content.length + 6);
        out.write(tag);
        int len = content.length;
        if (len < 0x80) {
            out.write(len);
        } else if (len < 0x100) {
            out.write(0x81);
            out.write(len);
        } else if (len < 0x10000) {
            out.write(0x82);
            out.write(len >> 8);
            out.write(len & 0xFF);
        } else {
            out.write(0x83);
            out.write(len >> 16);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
        }
        out.write(content, 0, content.length);
        return out.toByteArray();
    }

    private static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.write(p, 0, p.length);
        return out.toByteArray();
    }
}
