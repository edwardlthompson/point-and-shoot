@file:Suppress("MagicNumber")

package dev.pointandshoot

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Fleet-generic **AsShotNeutral ↔ Bayer** sync for pure-HAL DNG saves.
 *
 * Same-scene UW (14 mm / FL 2.3) vs ProShot (2026-07-12): ProShot’s ASN **R** tracks Bayer R/G
 * (Δ≈0) while P&S HAL ASN R sat ~0.06 above Bayer R/G → camera-WB green cast. ProShot ASN **B**
 * does **not** track Bayer B/G (stays low to boost blue). Naive full Bayer ASN (R+B) crushed blue
 * after ColorMatrix on OP13.
 *
 * Shipped mode: **Bayer R + HAL B** — set ASN R from center-crop Bayer R/G; keep DngCreator’s B
 * (and renormalize max==1). In-place IFD0 patch only — never [ExifInterface], never CM/FM.
 */
object DngBayerAsnSyncPolicy {
    private const val TAG = "PNS.BayerAsnSync"
    private const val TAG_AS_SHOT_NEUTRAL = 50728

    /** When true, [Dng12Saver] patches ASN after [DngCreator.writeImage] under pure-HAL. */
    const val ENABLED: Boolean = true

    /**
     * Build max-normalized ASN `[R,G,B]`: R from Bayer R/G, B from HAL ASN (relative to G=1).
     *
     * Returns null when Bayer R/G looks CFA-suspect (e.g. R/G>1 from a wrong phase) so the
     * caller keeps HAL ASN instead of writing a broken neutral (ASN R=1 → no red WB gain).
     */
    fun hybridBayerRHalB(
        bayerRg: Float,
        halAsn: FloatArray,
    ): FloatArray? {
        require(halAsn.size >= 3) { "halAsn length" }
        var rg = bayerRg
        // Wrong CFA phase often inverts R/G (>1). Invert once; still reject if out of band.
        if (rg > 1f) {
            rg = 1f / rg.coerceAtLeast(1e-6f)
        }
        if (rg !in TRUSTED_BAYER_RG) {
            return null
        }
        val asnR = rg
        val halG = halAsn[1].coerceAtLeast(1e-6f)
        val asnB = (halAsn[2] / halG).coerceIn(0.15f, 1.5f)
        val max = maxOf(asnR, 1f, asnB)
        return floatArrayOf(asnR / max, 1f / max, asnB / max)
    }

    /** Typical OP13 UW/tele Bayer R/G under real scenes (not grey-card only). */
    private val TRUSTED_BAYER_RG = 0.35f..0.95f

    /** Read IFD0 AsShotNeutral rationals as floats (or null). */
    fun readAsShotNeutral(dng: ByteArray): FloatArray? {
        if (dng.size < 8) return null
        val bb = ByteBuffer.wrap(dng).order(ByteOrder.LITTLE_ENDIAN)
        if (bb.short.toInt() and 0xFFFF != 0x4949) return null
        if (bb.short.toInt() and 0xFFFF != 42) return null
        val ifd0 = bb.int
        if (ifd0 < 0 || ifd0 + 2 > dng.size) return null
        val n = (dng[ifd0].toInt() and 0xFF) or ((dng[ifd0 + 1].toInt() and 0xFF) shl 8)
        var pos = ifd0 + 2
        repeat(n) {
            if (pos + 12 > dng.size) return null
            val tag = (dng[pos].toInt() and 0xFF) or ((dng[pos + 1].toInt() and 0xFF) shl 8)
            val type = (dng[pos + 2].toInt() and 0xFF) or ((dng[pos + 3].toInt() and 0xFF) shl 8)
            val cnt =
                (dng[pos + 4].toLong() and 0xFF) or
                    ((dng[pos + 5].toLong() and 0xFF) shl 8) or
                    ((dng[pos + 6].toLong() and 0xFF) shl 16) or
                    ((dng[pos + 7].toLong() and 0xFF) shl 24)
            val valueOrOff =
                (dng[pos + 8].toLong() and 0xFF) or
                    ((dng[pos + 9].toLong() and 0xFF) shl 8) or
                    ((dng[pos + 10].toLong() and 0xFF) shl 16) or
                    ((dng[pos + 11].toLong() and 0xFF) shl 24)
            if (tag == TAG_AS_SHOT_NEUTRAL && type == 5 && cnt >= 3) {
                val off = valueOrOff.toInt()
                if (off < 0 || off + 24 > dng.size) return null
                fun rational(i: Int): Float {
                    val o = off + i * 8
                    val num =
                        (dng[o].toLong() and 0xFF) or
                            ((dng[o + 1].toLong() and 0xFF) shl 8) or
                            ((dng[o + 2].toLong() and 0xFF) shl 16) or
                            ((dng[o + 3].toLong() and 0xFF) shl 24)
                    val den =
                        (dng[o + 4].toLong() and 0xFF) or
                            ((dng[o + 5].toLong() and 0xFF) shl 8) or
                            ((dng[o + 6].toLong() and 0xFF) shl 16) or
                            ((dng[o + 7].toLong() and 0xFF) shl 24)
                    return num.toFloat() / den.coerceAtLeast(1).toFloat()
                }
                return floatArrayOf(rational(0), rational(1), rational(2))
            }
            pos += 12
        }
        return null
    }

    fun applyToDngBytes(
        dng: ByteArray,
        bayerEstimate: DngBayerAsShotNeutral.BayerAsnEstimate,
    ): ByteArray {
        val hal = readAsShotNeutral(dng)
        if (hal == null) {
            Log.w(TAG, "skip: no HAL AsShotNeutral in DNG")
            return dng
        }
        val patched = hybridBayerRHalB(bayerEstimate.bayerRg, hal)
        if (patched == null) {
            Log.w(
                TAG,
                "skip unsafe bayerRg=%.4f (keep HAL ASN [%.4f %.4f %.4f])".format(
                    bayerEstimate.bayerRg,
                    hal[0],
                    hal[1],
                    hal[2],
                ),
            )
            return dng
        }
        Log.i(
            TAG,
            "asn sync bayerR+halB bayerRg=%.4f bayerBg=%.4f hal=[%.4f %.4f %.4f] -> [%.4f %.4f %.4f]".format(
                bayerEstimate.bayerRg,
                bayerEstimate.bayerBg,
                hal[0],
                hal[1],
                hal[2],
                patched[0],
                patched[1],
                patched[2],
            ),
        )
        return TiffDngColorMatrixPatch.patchAsShotNeutralFromFloats(dng, patched)
    }
}
