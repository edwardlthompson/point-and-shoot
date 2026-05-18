"""
Structural audit of DNG files — reports TIFF validity, key tags, and rawpy decode status.
"""
import struct, sys
import rawpy
import numpy as np

TIFF_TAGS = {
    256: 'ImageWidth', 257: 'ImageLength', 258: 'BitsPerSample',
    259: 'Compression', 262: 'PhotometricInterp', 273: 'StripOffsets',
    278: 'RowsPerStrip', 279: 'StripByteCounts', 324: 'TileOffsets',
    325: 'TileByteCounts', 50706: 'DNGVersion', 50708: 'UniqueCameraModel',
    50717: 'WhiteLevel', 50718: 'BlackLevel', 50719: 'BlackLevelDeltaH',
    50721: 'ColorMatrix1', 50722: 'ColorMatrix2',
    50728: 'AsShotNeutral', 50964: 'ForwardMatrix1', 50965: 'ForwardMatrix2',
    50827: 'ActiveArea', 33434: 'ExposureTime', 34855: 'ISOSpeedRatings',
}

def read_ifd(data, ifd_off):
    if ifd_off + 2 > len(data):
        return [], 0
    n = struct.unpack_from('<H', data, ifd_off)[0]
    entries = []
    pos = ifd_off + 2
    for _ in range(n):
        if pos + 12 > len(data):
            break
        tag  = struct.unpack_from('<H', data, pos)[0]
        typ  = struct.unpack_from('<H', data, pos+2)[0]
        cnt  = struct.unpack_from('<I', data, pos+4)[0]
        voff = struct.unpack_from('<I', data, pos+8)[0]
        entries.append((tag, typ, cnt, voff))
        pos += 12
    next_ifd = struct.unpack_from('<I', data, pos)[0] if pos + 4 <= len(data) else 0
    return entries, next_ifd

def asn_from_entry(data, off):
    vals = []
    for i in range(3):
        num = struct.unpack_from('<I', data, off + i*8)[0]
        den = struct.unpack_from('<I', data, off + i*8 + 4)[0]
        vals.append(float(num) / max(den, 1))
    return vals

def audit(path, label):
    print(f'=== {label}: {path.split("/")[-1]} ===')
    try:
        with open(path, 'rb') as f:
            data = f.read()
    except Exception as e:
        print(f'  CANNOT OPEN: {e}')
        print()
        return

    size = len(data)
    print(f'  File size: {size:,} bytes')

    # TIFF header
    if size < 8:
        print('  ERROR: file too small for TIFF header')
        print(); return

    bo = data[0:2]
    magic = struct.unpack_from('<H', data, 2)[0]
    ifd0_off = struct.unpack_from('<I', data, 4)[0]
    print(f'  ByteOrder={bo} Magic={magic} IFD0_offset={ifd0_off}')

    if bo != b'II' or magic != 42:
        print('  ERROR: not a valid TIFF/DNG')
        print(); return
    if ifd0_off >= size:
        print(f'  ERROR: IFD0 offset {ifd0_off} >= file size {size}')
        print(); return

    entries, next_ifd = read_ifd(data, ifd0_off)
    print(f'  IFD0 entry count: {len(entries)}')

    asn_wb = None
    strip_off = None
    strip_cnt = None
    w = h = bps = None

    for tag, typ, cnt, voff in entries:
        if tag == 50728:  # AsShotNeutral
            asn = asn_from_entry(data, voff)
            asn_wb = (1/asn[0], 1/asn[1], 1/asn[2])
            print(f'  AsShotNeutral: [{asn[0]:.4f},{asn[1]:.4f},{asn[2]:.4f}]  WB=[R={asn_wb[0]:.3f} G={asn_wb[1]:.3f} B={asn_wb[2]:.3f}]')
        elif tag == 256:
            w = voff if cnt == 1 else None
        elif tag == 257:
            h = voff if cnt == 1 else None
        elif tag == 258:
            bps = voff & 0xFFFF if cnt == 1 else None
        elif tag == 273:
            strip_off = voff
            strip_cnt = cnt
        elif tag == 324:
            strip_off = voff  # tile offsets
            strip_cnt = cnt
        elif tag in TIFF_TAGS and tag not in (50728,):
            pass  # silent for brevity

    print(f'  Dimensions: {w}x{h}  BitsPerSample: {bps}')
    if strip_off is not None:
        print(f'  Strip/Tile offsets tag: cnt={strip_cnt} first_offset_field={strip_off}')

    # rawpy decode
    try:
        with rawpy.imread(path) as raw:
            bayer = raw.raw_image_visible
            bl = raw.black_level_per_channel
            cfa = raw.raw_colors_visible
            wl = raw.white_level
            ch_means = []
            for ch in range(4):
                px = bayer[cfa==ch].astype(float) - bl[ch]
                px = px[px > 0]
                ch_means.append(px.mean() if len(px) > 100 else 0)
            R = ch_means[0]; G = (ch_means[1]+ch_means[3])/2; B = ch_means[2]
            print(f'  rawpy OK: shape={bayer.shape} dtype={bayer.dtype} wl={wl}')
            print(f'  Raw means (BL-sub): R={R:.0f} G={G:.0f} B={B:.0f}')
            print(f'  True raw WB: R={G/max(R,0.1):.3f} B={G/max(B,0.1):.3f}')
            if asn_wb:
                print(f'  WB error vs ASN: R={asn_wb[0]/max(G/max(R,0.1),0.01):.3f} B={asn_wb[2]/max(G/max(B,0.1),0.01):.3f} (1.000=perfect)')
            rgb = raw.postprocess(use_camera_wb=True, output_bps=8, no_auto_bright=True)
            lum = rgb[:,:,0].astype(int)+rgb[:,:,1]+rgb[:,:,2]
            mask = lum > np.percentile(lum, 65)
            Rd=rgb[:,:,0][mask].mean(); Gd=rgb[:,:,1][mask].mean(); Bd=rgb[:,:,2][mask].mean()
            print(f'  cam-WB decode (bright): R={Rd:.1f} G={Gd:.1f} B={Bd:.1f}  G/R={Gd/max(Rd,1):.3f}')
    except Exception as e:
        print(f'  rawpy ERROR: {e}')

    print()

files = [
    ('hfr-runs/new_uw.dng',   'UW   (cam2)'),
    ('hfr-runs/new_wide.dng', 'Wide (cam3)'),
    ('hfr-runs/new_tele.dng', 'Tele (cam4)'),
]
for path, label in files:
    audit(path, label)
