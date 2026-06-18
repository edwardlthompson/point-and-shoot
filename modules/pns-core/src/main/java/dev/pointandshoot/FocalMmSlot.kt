package dev.pointandshoot

/** Seven equivalent focal presets (row 0); routing picks cameras when hardware allows. */
enum class FocalMmSlot(val labelMm: String) {
    M14("14"),
    M23("23"),
    M35("35"),
    M50("50"),
    M73("73"),
    M85("85"),
    M150("150"),
}
