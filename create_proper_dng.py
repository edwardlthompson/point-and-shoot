#!/usr/bin/env python3

import struct

def create_proper_dng():
    """Create a proper DNG file that Android media scanner will recognize"""
    
    # DNG files need proper TIFF structure with required tags
    data = bytearray()
    
    # TIFF Header (8 bytes)
    data.extend(b'II')  # Little endian
    data.extend(struct.pack('<H', 42))  # Magic number
    data.extend(struct.pack('<I', 8))  # Offset to first IFD
    
    # First IFD - main image directory
    ifd_offset = 8
    entry_count = 8
    
    # IFD header (2 bytes for entry count)
    data.extend(struct.pack('<H', entry_count))
    
    # Calculate offset for string and rational data
    entries_end = ifd_offset + 2 + entry_count * 12 + 4  # +4 for next IFD offset
    string_offset = entries_end
    
    # IFD entries (12 bytes each)
    # Entry 1: ImageWidth (256)
    data.extend(struct.pack('<HHII', 256, 4, 1, 1920))  # LONG, count=1, value=1920
    # Entry 2: ImageLength (257) 
    data.extend(struct.pack('<HHII', 257, 4, 1, 1080))  # LONG, count=1, value=1080
    # Entry 3: BitsPerSample (258)
    data.extend(struct.pack('<HHII', 258, 3, 3, string_offset))  # SHORT, count=3, offset
    # Entry 4: Compression (259)
    data.extend(struct.pack('<HHII', 259, 3, 1, 1))  # SHORT, count=1, value=1 (no compression)
    # Entry 5: PhotometricInterpretation (262)
    data.extend(struct.pack('<HHII', 262, 3, 1, 2))  # SHORT, count=1, value=2 (RGB)
    # Entry 6: StripOffsets (273)
    data.extend(struct.pack('<HHII', 273, 4, 1, string_offset + 6))  # LONG, count=1, offset
    # Entry 7: SamplesPerPixel (277)
    data.extend(struct.pack('<HHII', 277, 3, 1, 3))  # SHORT, count=1, value=3 (RGB)
    # Entry 8: StripByteCounts (279)
    data.extend(struct.pack('<HHII', 279, 4, 1, 1920 * 1080 * 3))  # LONG, count=1, value
    
    # Next IFD offset (0 = end of chain)
    data.extend(struct.pack('<I', 0))
    
    # BitsPerSample data (3 SHORTs: 8,8,8 for RGB)
    data.extend(struct.pack('<HHH', 8, 8, 8))
    
    # Simple image data placeholder (would normally be actual pixel data)
    # For testing purposes, we'll just add a small placeholder
    data.extend(b'\x00' * 100)  # Small placeholder
    
    return bytes(data)

if __name__ == "__main__":
    dng_data = create_proper_dng()
    with open("proper_test.dng", "wb") as f:
        f.write(dng_data)
    print(f"Created proper_test.dng with {len(dng_data)} bytes")
