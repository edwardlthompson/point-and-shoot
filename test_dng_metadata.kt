import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Simple test to verify DNG TIFF reader works
 */
fun createTestDNG(): ByteArray {
    val output = ByteArrayOutputStream()
    
    // TIFF header
    output.write(0x49) // 'I' - little endian
    output.write(0x49)
    output.write(0x2A) // magic number 42
    output.write(0x00)
    
    // IFD offset (starts after header, so 8)
    output.write(0x08)
    output.write(0x00)
    output.write(0x00)
    output.write(0x00)
    
    // IFD with 2 entries
    output.write(0x02) // 2 entries
    output.write(0x00)
    
    // Entry 1: MAKE tag
    output.write(0x0F) // tag 271 (MAKE)
    output.write(0x01)
    output.write(0x02) // ASCII type
    output.write(0x00)
    output.write(0x05) // count 5
    output.write(0x00)
    output.write(0x00)
    output.write(0x00)
    output.write(0x12) // offset to string (next available)
    output.write(0x00)
    output.write(0x00)
    output.write(0x00)
    
    // Entry 2: ISO tag
    output.write(0x87) // tag 34855 (ISO)
    output.write(0x88)
    output.write(0x03) // SHORT type
    output.write(0x00)
    output.write(0x01) // count 1
    output.write(0x00)
    output.write(0x00)
    output.write(0x00)
    output.write(0x64) // value 100
    output.write(0x00)
    
    // Next IFD offset (0 = end)
    output.write(0x00)
    output.write(0x00)
    output.write(0x00)
    output.write(0x00)
    
    // String data for MAKE
    output.write("TEST".toByteArray())
    output.write(0x00) // null terminator
    
    return output.toByteArray()
}

fun main() {
    val testData = createTestDNG()
    println("Test DNG created with ${testData.size} bytes")
    
    // Test the reader
    val reader = dev.pointandshoot.DngTiffReader()
    val inputStream = testData.inputStream()
    val metadata = reader.readMetadata(inputStream)
    
    println("Metadata: $metadata")
}
