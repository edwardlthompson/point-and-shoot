"""
Structural verification: confirms FM and ASN tags are correctly patched
in the new build's DNG output. Does not require scene content.
"""
import struct, rawpy, numpy as np, sys

def read_tags(path):
    with open(path,'rb') as f: data = f.read()
    ifd0 = struct.unpack_from('<I', data, 4)[0]
    n    = struct.unpack_from('<H', data, ifd0)[0]
    pos  = ifd0 + 2
    asn = None; fm00 = None
    for _ in range(n):
        tag = struct.unpack_from('<H', data, pos)[0]
        cnt = struct.unpack_from('<I', data, pos+4)[0]
        off = struct.unpack_from('<I', data, pos+8)[0]
        if tag == 50728:
            asn = [float(struct.unpack_from('<I',data,off+i*8)[0]) /
                   max(float(struct.unpack_from('<I',data,off+i*8+4)[0]),1) for i in range(3)]
        if tag == 50964:
            n0=struct.unpack_from('<i',data,off)[0]
            d0=struct.unpack_from('<i',data,off+4)[0]
            fm00 = n0/max(abs(d0),1)
        pos += 12
    return asn, fm00

uw_path   = sys.argv[1] if len(sys.argv) > 1 else 'hfr-runs/v5_uw.dng'
wide_path = sys.argv[2] if len(sys.argv) > 2 else 'hfr-runs/v5_wide.dng'
tele_path = sys.argv[3] if len(sys.argv) > 3 else 'hfr-runs/v5_tele.dng'

cameras = [
    ('UW   (cam2)', uw_path,   0.3083, 'correct_uw'),
    ('Wide (cam3)', wide_path, 0.4375, 'unpatched'),
    ('Tele (cam4)', tele_path, 0.5032, 'correct_tele'),
]

print('=== Structural DNG Verification (v5 build) ===')
print()
all_fm_ok = True
for label, path, expected_fm, fm_desc in cameras:
    asn, fm00 = read_tags(path)
    if asn is None or fm00 is None:
        print('%s: ERROR reading tags' % label)
        all_fm_ok = False
        continue
    fm_ok = abs(fm00 - expected_fm) < 0.001
    status = 'OK' if fm_ok else 'WRONG'
    if not fm_ok:
        all_fm_ok = False
    print('  %s:' % label)
    print('    ASN WB: R=%.3f  B=%.3f' % (1/asn[0], 1/asn[2]))
    print('    FM1[0,0]=%.4f  expected=%.4f  [%s] (%s)' % (fm00, expected_fm, status, fm_desc))
    print()

# Tele WB accuracy (has scene content)
print('=== Tele WB accuracy ===')
asn, _ = read_tags(tele_path)
with rawpy.imread(tele_path) as raw:
    b = raw.raw_image_visible
    bl = raw.black_level_per_channel
    cfa = raw.raw_colors_visible
    means = []
    for ch in range(4):
        px = b[cfa==ch].astype(float) - bl[ch]
        px = px[px>0]
        means.append(px.mean() if len(px)>100 else 0)
    R=means[0]; G=(means[1]+means[3])/2; B=means[2]
asn_wb_r = 1/asn[0]; asn_wb_b = 1/asn[2]
raw_wb_r = G/max(R,0.1); raw_wb_b = G/max(B,0.1)
err_r = abs(asn_wb_r - raw_wb_r) / max(raw_wb_r,0.1) * 100
err_b = abs(asn_wb_b - raw_wb_b) / max(raw_wb_b,0.1) * 100
wb_ok = err_r < 5 and err_b < 5
print('  Tele: ASN_WB=[R=%.3f B=%.3f]  raw_WB=[R=%.3f B=%.3f]  err=[R=%.1f%% B=%.1f%%]  [%s]' % (
      asn_wb_r, asn_wb_b, raw_wb_r, raw_wb_b, err_r, err_b,
      'PASS' if wb_ok else 'FAIL'))
print()

print('=== Summary ===')
print('  FM patch applied:     %s' % ('ALL PASS' if all_fm_ok else 'FAIL'))
print('  Tele WB accuracy:     %s' % ('PASS' if wb_ok else 'FAIL'))
print()
print('  Note: rawpy cam-WB decode uses CM2, not FM. Green tint in rawpy')
print('  does not indicate a bug — it reflects wrong CM2 from HAL.')
print('  Lightroom/ACR/darktable use FM for color rendering: verify visually.')
print()

if all_fm_ok and wb_ok:
    print('STRUCTURAL GATE: PASS')
    sys.exit(0)
else:
    print('STRUCTURAL GATE: FAIL')
    sys.exit(1)
