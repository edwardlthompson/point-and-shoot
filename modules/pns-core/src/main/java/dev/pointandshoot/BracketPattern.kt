package dev.pointandshoot

enum class BracketPattern(val shotCount: Int, val displayName: String) {
    Three(3, "3-shot"),
    Five(5, "5-shot"),
    Seven(7, "7-shot"),
}
