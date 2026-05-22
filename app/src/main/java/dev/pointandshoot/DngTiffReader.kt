package dev.pointandshoot

import android.util.Log
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Advanced TIFF reader that follows IFD chains to extract DNG metadata.
 * Based on how professional tools like Lightroom, RawTherapee, and Darktable
 * navigate TIFF structures to access RAW metadata.
 */
class DngTiffReader {
    
    data class TiffMetadata(
        val aperture: Double?,
        val iso: Int?,
        val exposureTime: Double?,
        val focalLength: Double?,
        val make: String?,
        val model: String?
    )
    
    companion object {
        private const val TAG = "DngTiffReader"
        
        // TIFF tag constants - standard TIFF tags
        private const val TIFF_MAKE = 271
        private const val TIFF_MODEL = 272
        private const val TIFF_FNUMBER = 33437
        private const val TIFF_ISO = 34855
        private const val TIFF_EXPOSURE_TIME = 33434
        private const val TIFF_FOCAL_LENGTH = 37386
        
        // EXIF IFD pointer tag - points to EXIF subdirectory
        private const val EXIF_IFD_POINTER = 34665
        
        // DNG-specific tags for camera metadata
        private const val DNG_FNUMBER = -32102  // FNumber in DNG
        private const val DNG_EXPOSURE_TIME = -32104  // ExposureTime in DNG
        private const val DNG_ISO = -32115  // ISO in DNG
        private const val DNG_FOCAL_LENGTH = -32114  // FocalLength in DNG
        
        // TIFF data types
        private const val TIFF_BYTE = 1
        private const val TIFF_ASCII = 2
        private const val TIFF_SHORT = 3
        private const val TIFF_LONG = 4
        private const val TIFF_RATIONAL = 5
    }
    
    fun readMetadata(inputStream: InputStream): TiffMetadata {
        Log.d(TAG, "=== DngTiffReader.readMetadata() called ===")
        
        return try {
            val bytes = inputStream.readBytes()
            Log.d(TAG, "Read ${bytes.size} bytes from input stream")
            
            val buffer = ByteBuffer.wrap(bytes)
            
            // Read TIFF header
            val byteOrder1 = buffer.get().toInt()
            val byteOrder2 = buffer.get().toInt()
            val byteOrder = if (byteOrder1 == 73 && byteOrder2 == 73) "II" else "MM"
            Log.d(TAG, "Byte order: $byteOrder (${if (byteOrder == "II") "Little Endian" else "Big Endian"})")
            
            buffer.order(if (byteOrder == "II") ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
            
            val magic = buffer.short.toInt()
            Log.d(TAG, "Magic number: $magic")
            
            if (magic != 42) {
                Log.d(TAG, "Not a valid TIFF file - magic number is $magic, expected 42")
                return TiffMetadata(null, null, null, null, null, null)
            }
            
            val firstIfdOffset = buffer.int
            Log.d(TAG, "TIFF header valid, first IFD offset: $firstIfdOffset")
            
            var metadata = TiffMetadata(null, null, null, null, null, null)
            
            // Follow IFD chain starting from first IFD
            var currentIfdOffset = firstIfdOffset
            var ifdCount = 0
            
            while (currentIfdOffset != 0 && ifdCount < 10) { // Safety limit
                // Validate IFD offset
                if (currentIfdOffset < 0 || currentIfdOffset >= bytes.size) {
                    Log.d(TAG, "Invalid IFD offset: $currentIfdOffset, file size: ${bytes.size}, stopping chain")
                    break
                }
                
                Log.d(TAG, "Reading IFD at offset: $currentIfdOffset")
                
                val (newMetadata, nextIfdOffset, exifIfdOffset) = readIfd(buffer, currentIfdOffset, metadata)
                metadata = newMetadata
                currentIfdOffset = nextIfdOffset
                
                // If we found EXIF IFD pointer, read it too
                if (exifIfdOffset != null && exifIfdOffset > 0 && exifIfdOffset < bytes.size) {
                    Log.d(TAG, "Found EXIF IFD at offset: $exifIfdOffset")
                    val (exifMetadata, _, _) = readIfd(buffer, exifIfdOffset, metadata)
                    metadata = exifMetadata
                }
                
                ifdCount++
            }
            
            Log.d(TAG, "Final metadata - Aperture: ${metadata.aperture}, ISO: ${metadata.iso}, Exposure: ${metadata.exposureTime}, Focal: ${metadata.focalLength}")
            metadata
            
        } catch (e: Exception) {
            Log.e(TAG, "Error reading TIFF metadata", e)
            TiffMetadata(null, null, null, null, null, null)
        }
    }
    
    private fun readIfd(buffer: ByteBuffer, ifdOffset: Int, currentMetadata: TiffMetadata): Triple<TiffMetadata, Int, Int?> {
        buffer.position(ifdOffset)
        val numEntries = buffer.short.toInt()
        Log.d(TAG, "IFD has $numEntries entries")
        
        var aperture = currentMetadata.aperture
        var iso = currentMetadata.iso
        var exposureTime = currentMetadata.exposureTime
        var focalLength = currentMetadata.focalLength
        var make = currentMetadata.make
        var model = currentMetadata.model
        var nextIfdOffset = 0
        var exifIfdOffset: Int? = null
        
        for (i in 0 until numEntries) {
            val entryStartPos = buffer.position()
            val tagRaw = buffer.short
            val tag = tagRaw.toInt()
            val type = buffer.short.toInt()
            val count = buffer.int
            val valueOffset = buffer.int
            
            // Each TIFF entry is exactly 12 bytes, ensure we're at the right position
            val expectedEntryEndPos = entryStartPos + 12
            
            // Log raw tag bytes for debugging negative tags
            if (tag < 0 || i >= 20) {
                Log.d(TAG, "Entry $i: tag=$tag (raw=${tagRaw}), type=$type, count=$count, offset=$valueOffset, pos=${buffer.position()}")
            } else {
                Log.d(TAG, "Entry $i: tag=$tag, type=$type, count=$count, offset=$valueOffset")
            }
            
            when (tag) {
                TIFF_MAKE -> {
                    make = readAsciiValue(buffer, type, count, valueOffset)
                    Log.d(TAG, "Found MAKE: $make")
                }
                TIFF_MODEL -> {
                    model = readAsciiValue(buffer, type, count, valueOffset)
                    Log.d(TAG, "Found MODEL: $model")
                }
                TIFF_FNUMBER -> {
                    aperture = readRationalValue(buffer, type, count, valueOffset)
                    Log.d(TAG, "Found FNUMBER: $aperture")
                }
                TIFF_ISO -> {
                    iso = readShortValue(buffer, type, count, valueOffset)
                    Log.d(TAG, "Found ISO: $iso")
                }
                TIFF_EXPOSURE_TIME -> {
                    exposureTime = readRationalValue(buffer, type, count, valueOffset)
                    Log.d(TAG, "Found EXPOSURE_TIME: $exposureTime")
                }
                TIFF_FOCAL_LENGTH -> {
                    focalLength = readRationalValue(buffer, type, count, valueOffset)
                    Log.d(TAG, "Found FOCAL_LENGTH: $focalLength")
                }
                EXIF_IFD_POINTER -> {
                    exifIfdOffset = valueOffset
                    Log.d(TAG, "Found EXIF IFD pointer: $exifIfdOffset")
                }
                DNG_FNUMBER -> {
                    Log.d(TAG, "DNG FNUMBER raw: type=$type, count=$count, offset=$valueOffset")
                    aperture = when (type) {
                        TIFF_RATIONAL -> readRationalValue(buffer, type, count, valueOffset)
                        else -> null
                    }
                    Log.d(TAG, "Found DNG FNUMBER: $aperture")
                }
                -32099 -> {
                    Log.d(TAG, "DNG tag -32099 (correct aperture): type=$type, count=$count, offset=$valueOffset")
                    aperture = when (type) {
                        TIFF_RATIONAL -> readRationalValue(buffer, type, count, valueOffset)
                        else -> null
                    }
                    Log.d(TAG, "Found correct DNG aperture from tag -32099: $aperture")
                }
                DNG_EXPOSURE_TIME -> {
                    Log.d(TAG, "DNG EXPOSURE_TIME raw: type=$type, count=$count, offset=$valueOffset")
                    // Handle different data types for exposure time
                    exposureTime = when (type) {
                        TIFF_RATIONAL -> readRationalValue(buffer, type, count, valueOffset)
                        TIFF_ASCII -> readAsciiValue(buffer, type, count, valueOffset)?.toDoubleOrNull()
                        else -> null
                    }
                    Log.d(TAG, "Found DNG EXPOSURE_TIME: $exposureTime")
                }
                DNG_ISO -> {
                    // Handle different data types for ISO
                    iso = when (type) {
                        TIFF_SHORT -> {
                            if (count == 1) {
                                // Value is embedded in offset field
                                valueOffset and 0xFFFF
                            } else {
                                readShortValue(buffer, type, count, valueOffset)
                            }
                        }
                        TIFF_LONG -> {
                            if (count == 1) {
                                // Value is embedded in offset field
                                valueOffset
                            } else {
                                readLongValue(buffer, type, count, valueOffset)?.toInt()
                            }
                        }
                        else -> null
                    }
                    Log.d(TAG, "Found DNG ISO: $iso")
                }
                DNG_FOCAL_LENGTH -> {
                    // Handle different data types for focal length
                    focalLength = when (type) {
                        TIFF_RATIONAL -> readRationalValue(buffer, type, count, valueOffset)
                        TIFF_SHORT -> {
                            if (count == 1) {
                                // Value is embedded in offset field
                                (valueOffset and 0xFFFF).toDouble()
                            } else {
                                readShortValue(buffer, type, count, valueOffset)?.toDouble()
                            }
                        }
                        TIFF_LONG -> {
                            if (count == 1) {
                                // Value is embedded in offset field
                                valueOffset.toDouble()
                            } else {
                                readLongValue(buffer, type, count, valueOffset)?.toDouble()
                            }
                        }
                        TIFF_BYTE -> {
                            if (count == 1) {
                                // Value is embedded in offset field
                                (valueOffset and 0xFF).toDouble()
                            } else {
                                readByteValue(buffer, type, count, valueOffset)?.toDouble()
                            }
                        }
                        else -> null
                    }
                    Log.d(TAG, "Found DNG FOCAL_LENGTH: $focalLength")
                }
                else -> {
                    if (tag < 0) {
                        Log.d(TAG, "Unhandled DNG tag: $tag, type=$type, count=$count, offset=$valueOffset")
                        // Try to read some common DNG tags for debugging
                        when (tag) {
                            -32115 -> {
                                val value = readShortValue(buffer, type, count, valueOffset)
                                Log.d(TAG, "DNG tag -32115 (possible ISO): $value")
                            }
                            -32114 -> {
                                val value = readRationalValue(buffer, type, count, valueOffset)
                                Log.d(TAG, "DNG tag -32114 (possible Focal Length): $value")
                            }
                            -32099 -> {
                                val value = readRationalValue(buffer, type, count, valueOffset)
                                Log.d(TAG, "DNG tag -32099 (possible alternative aperture): $value")
                            }
                            -30681 -> {
                                val value = readShortValue(buffer, type, count, valueOffset)
                                Log.d(TAG, "DNG tag -30681 (possible aperture): $value")
                            }
                            -28669 -> {
                                val value = readAsciiValue(buffer, type, count, valueOffset)
                                Log.d(TAG, "DNG tag -28669 (possible exposure string): $value")
                            }
                        }
                    }
                }
            }
            
            // Ensure buffer position is correct after processing this tag
            if (buffer.position() != expectedEntryEndPos) {
                Log.d(TAG, "Correcting buffer position from ${buffer.position()} to $expectedEntryEndPos for entry $i")
                buffer.position(expectedEntryEndPos)
            }
        }
        
        // Read next IFD offset (last 4 bytes of IFD)
        nextIfdOffset = buffer.int
        
        val newMetadata = TiffMetadata(aperture, iso, exposureTime, focalLength, make, model)
        return Triple(newMetadata, nextIfdOffset, exifIfdOffset)
    }
    
    private fun readAsciiValue(buffer: ByteBuffer, type: Int, count: Int, valueOffset: Int): String? {
        if (type != TIFF_ASCII) return null
        
        return try {
            if (count <= 4) {
                // Value is embedded in offset field
                val currentPos = buffer.position()
                buffer.position(currentPos - 4) // Go back to read the offset field
                val bytes = ByteArray(4)
                buffer.get(bytes)
                buffer.position(currentPos + 8) // Skip to next field
                String(bytes, 0, bytes.indexOf(0))
            } else {
                // Value is at offset
                val currentPos = buffer.position()
                buffer.position(valueOffset)
                val bytes = ByteArray(count)
                buffer.get(bytes)
                buffer.position(currentPos)
                String(bytes, 0, bytes.indexOf(0))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading ASCII value", e)
            null
        }
    }
    
    private fun readRationalValue(buffer: ByteBuffer, type: Int, count: Int, valueOffset: Int): Double? {
        if (type != TIFF_RATIONAL || count != 1) return null
        
        // Validate offset
        if (valueOffset <= 0) {
            Log.d(TAG, "Invalid rational offset: $valueOffset")
            return null
        }
        
        return try {
            val currentPos = buffer.position()
            if (valueOffset >= buffer.limit()) {
                Log.d(TAG, "Rational offset $valueOffset beyond buffer limit ${buffer.limit()}")
                return null
            }
            buffer.position(valueOffset)
            val numerator = buffer.int
            val denominator = buffer.int
            buffer.position(currentPos)
            
            if (denominator != 0) {
                val value = numerator.toDouble() / denominator
                Log.d(TAG, "Read rational: $numerator/$denominator = $value")
                value
            } else {
                Log.d(TAG, "Rational denominator is zero")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading rational value at offset $valueOffset", e)
            null
        }
    }
    
    private fun readShortValue(buffer: ByteBuffer, type: Int, count: Int, valueOffset: Int): Int? {
        if (type != TIFF_SHORT || count != 1) return null
        
        return try {
            if (count == 1) {
                // Value is embedded in offset field
                valueOffset and 0xFFFF
            } else {
                val currentPos = buffer.position()
                buffer.position(valueOffset)
                val value = buffer.short.toInt()
                buffer.position(currentPos)
                value
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading short value", e)
            null
        }
    }
    
    private fun readLongValue(buffer: ByteBuffer, type: Int, count: Int, valueOffset: Int): Long? {
        return try {
            when (type) {
                TIFF_LONG -> {
                    if (count == 1) valueOffset.toLong()
                    else {
                        val currentPos = buffer.position()
                        buffer.position(valueOffset)
                        val value = buffer.int.toLong()
                        buffer.position(currentPos)
                        value
                    }
                }
                TIFF_SHORT -> {
                    if (count == 1) (valueOffset and 0xFFFF).toLong()
                    else {
                        val currentPos = buffer.position()
                        buffer.position(valueOffset)
                        val value = buffer.short.toLong()
                        buffer.position(currentPos)
                        value
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error reading long value: $e")
            null
        }
    }
    
    private fun readByteValue(buffer: ByteBuffer, type: Int, count: Int, valueOffset: Int): Int? {
        return try {
            when (type) {
                TIFF_BYTE -> {
                    if (count == 1) valueOffset and 0xFF
                    else {
                        val currentPos = buffer.position()
                        buffer.position(valueOffset)
                        val value = buffer.get().toInt() and 0xFF
                        buffer.position(currentPos)
                        value
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error reading byte value: $e")
            null
        }
    }
}
