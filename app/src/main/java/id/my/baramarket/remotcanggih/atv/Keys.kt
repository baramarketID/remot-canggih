package id.my.baramarket.remotcanggih.atv

/** Kode tombol Android (sama dengan android.view.KeyEvent). */
object Keys {
    const val HOME = 3
    const val BACK = 4
    const val NUM_0 = 7
    const val UP = 19
    const val DOWN = 20
    const val LEFT = 21
    const val RIGHT = 22
    const val OK = 23
    const val VOL_UP = 24
    const val VOL_DOWN = 25
    const val POWER = 26
    const val SPACE = 62
    const val ENTER = 66
    const val DEL = 67
    const val SETTINGS = 176
    const val MENU = 82
    const val PLAY_PAUSE = 85
    const val NEXT = 87
    const val PREV = 88
    const val REWIND = 89
    const val FORWARD = 90
    const val MUTE = 164
    const val CH_UP = 166
    const val CH_DOWN = 167
    const val INPUT = 178
    const val INFO = 165
    const val GUIDE = 172

    fun number(n: Int) = NUM_0 + n

    /** Mengubah karakter jadi kode tombol (cadangan bila TV tidak menerima teks langsung). */
    fun forChar(c: Char): Int? = when (c.lowercaseChar()) {
        in 'a'..'z' -> 29 + (c.lowercaseChar() - 'a')
        in '0'..'9' -> NUM_0 + (c - '0')
        ' ' -> SPACE
        '\n' -> ENTER
        ',' -> 55
        '.' -> 56
        '`' -> 68
        '-' -> 69
        '=' -> 70
        '[' -> 71
        ']' -> 72
        '\\' -> 73
        ';' -> 74
        '\'' -> 75
        '/' -> 76
        '@' -> 77
        '+' -> 81
        '*' -> 17
        '#' -> 18
        else -> null
    }
}
