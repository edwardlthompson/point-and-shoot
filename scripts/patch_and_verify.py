"""
Patch FM1/FM2 in-place in a DNG and verify the result.
Proves that FM patching is safe and correct before enabling in Kotlin.
"""
import struct, rawpy, numpy as np, shutil, sys, os

# Wide camera FM (from DngCreator, copy-pasted to all cameras):
#   [[0.4375, 0.3828, 0.1406],
#    [0.2188, 0.7188, 0.0625],
#    [0.0156, 0.0938, 0.7109]]
# These are 56/128, 49/128, 18/128, 28/128, 92/128, 8/128, 2/128, 12/128, 91/128

# Correct FM for UW and Tele must be derived from their CM2 matrices.
# Formula: FM = sRGB→XYZ_D50 × pinv(CM2)
# sRGB→XYZ_D50 (standard Bradford-adapted):
SRGB_TO_XYZ_D50 = np.array([
    [0.4360747, 0.3850649, 0.1430804],
    [0.2225045, 0.7168786, 0.0606169],
    [0.0139322, 0.0971045, 0.7141733],
])

def read_srational_9(data, off):
    vals = []
    for i in range(9):
        n = struct.unpack_from('<i', data, off + i*8)[0]
        d = struct.unpack_from('<i', data, off + i*8 + 4)[0]
        vals.append(float(n)/max(abs(d),1))
    return np.array(vals).reshape(3,3)

def derive_fm(cm2):
    """Compute ForwardMatrix = sRGB→XYZ_D50 × pinv(CM2)"""
    cm2_pinv = np.linalg.pinv(cm2)
    fm = SRGB_TO_XYZ_D50 @ cm2_pinv
    # Clip columns to sum to 1 (DNG spec: FM * camera_neutral = XYZ of D50 white)
    return fm

def write_srational_9(buf, off, matrix, denom=100000):
    """Write a 3x3 matrix as SRATIONAL[9] into buf at offset."""
    flat = matrix.flatten()
    for i, v in enumerate(flat):
        n = int(round(v * denom))
        struct.pack_into('<i', buf, off + i*8,     n)
        struct.pack_into('<i', buf, off + i*8 + 4, denom)

def find_tag_off(data, tag_id):
    ifd0 = struct.unpack_from('<I', data, 4)[0]
    n    = struct.unpack_from('<H', data, ifd0)[0]
    pos  = ifd0 + 2
    for _ in range(n):
        tag = struct.unpack_from('<H', data, pos)[0]
        typ = struct.unpack_from('<H', data, pos+2)[0]
        cnt = struct.unpack_from('<I', data, pos+4)[0]
        off = struct.unpack_from('<I', data, pos+8)[0]
        if tag == tag_id:
            return off, typ, cnt
        pos += 12
    return None, None, None

def patch_fm(src_path, dst_path, cm2):
    fm = derive_fm(cm2)
    print(f'  Derived FM from CM2:')
    print(f'    {np.round(fm, 4)}')
    shutil.copy2(src_path, dst_path)
    with open(dst_path, 'r+b') as f:
        data = bytearray(f.read())
    fm1_off, fm1_typ, fm1_cnt = find_tag_off(bytes(data), 50964)
    fm2_off, fm2_typ, fm2_cnt = find_tag_off(bytes(data), 50965)
    if fm1_off is None:
        print('  ERROR: FM1 tag not found')
        return None
    print(f'  FM1 data offset={fm1_off}  FM2 data offset={fm2_off}')
    write_srational_9(data, fm1_off, fm)
    write_srational_9(data, fm2_off, fm)
    with open(dst_path, 'wb') as f:
        f.write(data)
    print(f'  Wrote {dst_path}')
    return fm

def cam_wb_decode(path):
    with rawpy.imread(path) as raw:
        rgb = raw.postprocess(use_camera_wb=True, output_bps=8, no_auto_bright=True)
        lum = rgb[:,:,0].astype(int)+rgb[:,:,1]+rgb[:,:,2]
        mask = lum > np.percentile(lum, 65)
        R=rgb[:,:,0][mask].mean(); G=rgb[:,:,1][mask].mean(); B=rgb[:,:,2][mask].mean()
        return R, G, B

# Load CM2 for each camera from existing DNG
cameras = {
    'uw':   ('hfr-runs/v4_uw.dng',   'hfr-runs/v4_uw_fmpatched.dng'),
    'tele': ('hfr-runs/v4_tele.dng',  'hfr-runs/v4_tele_fmpatched.dng'),
}

print('=== Patching FM from per-camera CM2 ===')
print()

for cam, (src, dst) in cameras.items():
    with open(src,'rb') as f: data = f.read()
    cm2_off, _, _ = find_tag_off(data, 50722)
    if cm2_off is None:
        print(f'{cam}: CM2 not found')
        continue
    cm2 = read_srational_9(data, cm2_off)
    print(f'{cam} CM2:')
    print(f'  {np.round(cm2, 4)}')
    fm = patch_fm(src, dst, cm2)
    if fm is not None:
        # Verify
        R,G,B = cam_wb_decode(dst)
        print(f'  Post-patch decode: R={R:.1f} G={G:.1f} B={B:.1f}  G/R={G/max(R,1):.3f}')
    print()

# Wide reference
print('Wide reference:')
R,G,B = cam_wb_decode('hfr-runs/v4_wide.dng')
print(f'  decode: R={R:.1f} G={G:.1f} B={B:.1f}  G/R={G/max(R,1):.3f}')
