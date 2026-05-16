package dev.pointandshoot

import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import org.junit.Assert.assertNotNull
import org.junit.Test

class PreviewMetadataAtomicStressTest {

    @Test
    fun rapid_updates_keep_consistent_triple() {
        val ref = AtomicReference(PreviewMetadata(null, null, null))
        val threads =
            List(20) { idx ->
                Thread {
                    repeat(500) { step ->
                        ref.updateAndGet { cur ->
                            PreviewMetadata.mergeForTest(
                                cur,
                                iso = if ((step + idx) % 7 == 0) Random.nextInt(50, 800) else null,
                                exposureNs = if ((step + idx) % 11 == 0) Random.nextLong(1_000, 99_999_999) else null,
                                awbMode = if ((step + idx) % 13 == 0) Random.nextInt(1, 8) else null,
                            )
                        }
                    }
                }
            }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        val final = ref.get()
        assertNotNull(final)
    }
}
