# Aturan tambahan R8 (default sudah cukup untuk Compose).
-keepattributes SourceFile,LineNumberTable

# Kelas yang dipanggil lewat nama dari manifest Cast.
-keep class id.my.baramarket.remotcanggih.cast.CastOptionsProvider { *; }
