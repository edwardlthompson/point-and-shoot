import rawpy, struct, numpy as np, os, sys

def fmt_f(v):
    return '%.4f' % v if v is not None else 'null'

def audit(fn, label):
    if not os.path.exists(fn):
        print('%s: MISSING' % label); return
    with open(fn,'rb') as f: data = f.read()
    ifd0 = struct.unpack_from('<I', data, 4)[0]
    n = struct.unpack_from('<H', data, ifd0)[0]
    pos = ifd0+2; ucm=None; fm00=None; asn0=None
    for _ in range(n):
        tag = struct.unpack_from('<H', data, pos)[0]
        cnt = struct.unpack_from('<I', data, pos+4)[0]
        off = struct.unpack_from('<I', data, pos+8)[0]
        if tag == 50708 and off < len(data):
            ucm = data[off:off+cnt].decode('ascii','replace').strip(chr(0))
        if tag == 50964 and off+8 <= len(data):
            n0=struct.unpack_from('<i',data,off)[0]; d0=struct.unpack_from('<i',data,off+4)[0]
            fm00 = n0/max(abs(d0),1)
        if tag == 50728 and off+8 <= len(data):
            r0=struct.unpack_from('<I',data,off)[0]; d0=struct.unpack_from('<I',data,off+4)[0]
            asn0 = r0/max(d0,1)
        pos += 12
    try:
        with rawpy.imread(fn) as r:
            b = r.raw_image_visible
            maxv = int(b.max()); meanv = float(b.astype(float).mean())
            rgb = r.postprocess(use_camera_wb=True, output_bps=8)
            rmean = float(rgb[:,:,0].mean())
            gmean = float(rgb[:,:,1].mean())
            bmean = float(rgb[:,:,2].mean())
        status = 'READABLE'
    except Exception as e:
        maxv=0; meanv=0; rmean=0; gmean=0; bmean=0; status='ERROR:%s' % e

    asn_wb = '%.3f' % (1/asn0) if asn0 and asn0 > 0 else 'null'
    print('%s:' % label)
    print('  UCM=%r  FM[0,0]=%s  ASN[0]=%s (WB_R=%s)' % (ucm, fmt_f(fm00), fmt_f(asn0), asn_wb))
    print('  raw max=%d mean=%.1f  RGB postprocess: R=%.1f G=%.1f B=%.1f  [%s]' % (
          maxv, meanv, rmean, gmean, bmean, status))
    print()

files = [
    ('hfr-runs/earliest_0001.dng', 'May17 17:55 _0001'),
    ('hfr-runs/earliest_0003.dng', 'May17 17:55 _0003'),
    ('hfr-runs/earliest_0005.dng', 'May17 17:55 _0005'),
    ('hfr-runs/ultramax_0001.dng', 'May16 22:10 UltraMax'),
    ('hfr-runs/fresh_a.dng', 'fresh_a (Wide cam3)'),
    ('hfr-runs/fresh_b.dng', 'fresh_b (UW cam2)'),
    ('hfr-runs/fresh_c.dng', 'fresh_c (Tele cam4)'),
]

for fn, label in files:
    audit(fn, label)
