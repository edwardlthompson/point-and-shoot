#!/usr/bin/env python3

import struct

def create_test_dng():
    """Create a minimal DNG file for testing metadata extraction"""
    
    # DNG is essentially a TIFF file
    data = bytearray()
    
    # TIFF Header (8 bytes)
    data.extend(b'II')  # Little endian
    data.extend(struct.pack('<H', 42))  # Magic number
    data.extend(struct.pack('<I', 8))  # Offset to first IFD
    
    # First IFD
    ifd_offset = 8
    entry_count = 6
    
    # IFD header (2 bytes for entry count)
    data.extend(struct.pack('<H', entry_count))
    
    # IFD entries (12 bytes each)
    entries_start = len(data)
    
    # Entry 1: MAKE (271)
    data.extend(struct.pack('<HHII', 271, 2, 5, entries_start + entry_count * 12 + 4))  # ASCII, count=5, offset to string
    # Entry 2: MODEL (272) 
    data.extend(struct.pack('<HHII', 272, 2, 9, entries_start + entry_count * 12 + 10))  # ASCII, count=9, offset to string
    # Entry 3: FNUMBER (33437)
    data.extend(struct.pack('<HHII', 33437, 5, 1, entries_start + entry_count * 12 + 20))  # RATIONAL, count=1, offset
    # Entry 4: ISO (34855)
    data.extend(struct.pack('<HHII', 34855, 3, 1, 200))  # SHORT, count=1, value=200
    # Entry 5: EXPOSURE_TIME (33434)
    data.extend(struct.pack('<HHII', 33434, 5, 1, entries_start + entry_count * 12 + 28))  # RATIONAL, count=1, offset
    # Entry 6: FOCAL_LENGTH (37386)
    data.extend(struct.pack('<HHII', 37386, 5, 1, entries_start + entry_count * 12 + 36))  # RATIONAL, count=1, offset
    
    # Next IFD offset (0 = end of chain)
    data.extend(struct.pack('<I', 0))
    
    # String data
    data.extend(b'TEST\x00')  # MAKE
    data.extend(b'TEST_CAMERA\x00')  # MODEL
    
    # Rational data (8 bytes each: numerator, denominator)
    data.extend(struct.pack('<II', 18, 10))  # FNUMBER = 1.8
    data.extend(struct.pack('<II', 1, 125))  # EXPOSURE_TIME = 1/125
    data.extend(struct.pack('<II', 230, 10))  # FOCAL_LENGTH = 23.0mm
    
    return bytes(data)

if __name__ == "__main__":
    dng_data = create_test_dng()
    with open("test.dng", "wb") as f:
        f.write(dng_data)
    print(f"Created test.dng with {len(dng_data)} bytes")
