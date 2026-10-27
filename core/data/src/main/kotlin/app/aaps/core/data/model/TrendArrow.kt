package app.aaps.core.data.model

enum class TrendArrow(val text: String, val symbol: String) {
    NONE("NONE", "??"),
    TRIPLE_UP("TripleUp", "X"),
    DOUBLE_UP("DoubleUp", "\u21c8"),
    SINGLE_UP("SingleUp", "\u2191"),
    FORTY_FIVE_UP("FortyFiveUp", "\u2197"),
    FLAT("Flat", "\u2192"),
    FORTY_FIVE_DOWN("FortyFiveDown", "\u2198"),
    SINGLE_DOWN("SingleDown", "\u2193"),
    DOUBLE_DOWN("DoubleDown", "\u21ca"),
    TRIPLE_DOWN("TripleDown", "X")
    ;

    companion object {

        fun fromString(direction: String?): TrendArrow {
            if (direction.isNullOrEmpty()) return NONE
            
            // 1. 既存の大文字小文字を無視した完全一致を試みる
            entries.firstOrNull { it.text.equals(direction, ignoreCase = true) }?.let { return it }
            
            // 2. xDrip+から送信され得る多様な文字列表記や数値・記号のゆらぎを吸収するマッピング
            return when (direction.lowercase()) {
                "flat", "rightarrow", "->", "0", "horizontal" -> FLAT
                "singleup", "uparrow", "↑", "1", "single_up" -> SINGLE_UP
                "doubleup", "upleft", "⇈", "2", "double_up" -> DOUBLE_UP
                "tripleup", "3", "triple_up" -> TRIPLE_UP
                "fortyfiveup", "se", "↗", "4", "forty_five_up" -> FORTY_FIVE_UP
                "fortyfivedown", "↘", "5", "forty_five_down" -> FORTY_FIVE_DOWN
                "singledown", "downarrow", "↓", "6", "single_down" -> SINGLE_DOWN
                "doubledown", "⇊", "7", "double_down" -> DOUBLE_DOWN
                "trippledown", "trpledown", "8", "triple_down" -> TRIPLE_DOWN
                else -> NONE
            }
        }
    }
}
