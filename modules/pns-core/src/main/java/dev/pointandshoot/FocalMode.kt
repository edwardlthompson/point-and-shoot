package dev.pointandshoot

/**
 * Focal-equivalent crop modes — shared with capture crop math and role routing.
 */
enum class FocalMode(
    val displayName: String,
    val zoomFactor: Double,
    val meteringHint: MeteringHint,
    val afHint: AfHint,
) {
    Street35(displayName = "35mm", zoomFactor = 1.50, meteringHint = MeteringHint.Average, afHint = AfHint.SinglePoint),
    Standard50(displayName = "50mm", zoomFactor = 2.20, meteringHint = MeteringHint.CenterWeighted, afHint = AfHint.SinglePoint),
    Portrait85(displayName = "85mm", zoomFactor = 1.16, meteringHint = MeteringHint.CenterWeighted, afHint = AfHint.EyeAf),
    LongTele150(displayName = "150mm", zoomFactor = 2.04, meteringHint = MeteringHint.CenterWeighted, afHint = AfHint.EyeAf),
}

enum class MeteringHint { Average, CenterWeighted, HighlightWeighted }

enum class AfHint { SinglePoint, FaceAware, EyeAf }
