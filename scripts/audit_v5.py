import struct, rawpy, numpy as np

def audit(path, label):
    print(f'=== {label} ===')
    try:
        with rawpy.imread(path) as raw:
            b = raw.raw_image_visible
            bl = raw.black_level_per_channel
            cfa = raw.raw_colors_visible
            means = []
            for ch in range(4):
                px = b[cfa==ch].astype(float) - bl[ch]
                px = px[px>0]
                means.append(px.mean() if len(px)>100 else 0.0)
            R=means[0]; G=(means[1]+means[3])/2; B=means[2]
            print(f'  raw means: R={R:.0f} G={G:.0f} B={B:.0f}  WB=[R={G/max(R,0.1):.3f} B={G/max(B,0.1):.3f}]')
            print(f'  max pixel: {b.max()}  dtype={b.dtype}')
    except Exception as e:
        print(f'  ERROR: {e}')

    # Read ASN
    with open(path,'rb') as f: data = f.read()
    ifd0 = struct.unpack_from('<I', data, 4)[0]
    n    = struct.unpack_from('<H', data, ifd0)[0]
    pos  = ifd0 + 2
    for _ in range(n):
        tag = struct.unpack_from('<H', data, pos)[0]
        cnt = struct.unpack_from('<I', data, pos+4)[0]
        off = struct.unpack_from('<I', data, pos+8)[0]
        if tag == 50728:
            asn = [float(struct.unpack_from('<I',data,off+i*8)[0]) /
                   max(float(struct.unpack_from('<I',data,off+i*8+4)[0]),1) for i in range(3)]
            print(f'  ASN=[{asn[0]:.4f},{asn[1]:.4f},{asn[2]:.4f}]  WB=[R={1/asn[0]:.3f} B={1/asn[2]:.3f}]')
        if tag == 50964:
            import numpy as npx
            vals = []
            for i in range(9):
                n0=struct.unpack_from('<i',data,off+i*8)[0]; d0=struct.unpack_from('<i',data,off+i*8+4)[0]
                vals.append(n0/max(abs(d0),1))
            fm = npx.array(vals).reshape(3,3)
            print(f'  FM1[0,0]={fm[0,0]:.4f}  (wide=0.4375, uw_correct=0.3083, tele_correct=0.5032)')
        pos += 12
    print()

for label, path in [('UW',   'hfr-runs/v5_uw.dng'),
                    ('Wide', 'hfr-runs/v5_wide.dng'),
                    ('Tele', 'hfr-runs/v5_tele.dng')]:
    audit(path, label)
