"""
Audit ColorMatrix1/2 and ForwardMatrix1/2 across all three cameras.
Show whether matrices are identical (copy-paste bug) and compute
what a correct UW/tele DNG decode should look like.
"""
import struct, rawpy, numpy as np

def read_srational_matrix(data, off, n=9):
    vals = []
    for i in range(n):
        num = struct.unpack_from('<i', data, off + i*8)[0]
        den = struct.unpack_from('<i', data, off + i*8 + 4)[0]
        vals.append(float(num)/max(abs(den),1) * (1 if den >= 0 else -1))
    return np.array(vals).reshape(3,3)

def read_rational(data, off):
    num = struct.unpack_from('<I', data, off)[0]
    den = struct.unpack_from('<I', data, off+4)[0]
    return float(num)/max(den,1)

def parse_dng(path):
    with open(path,'rb') as f: data = f.read()
    ifd0 = struct.unpack_from('<I', data, 4)[0]
    n    = struct.unpack_from('<H', data, ifd0)[0]
    pos  = ifd0 + 2
    tags = {}
    for _ in range(n):
        tag = struct.unpack_from('<H', data, pos)[0]
        typ = struct.unpack_from('<H', data, pos+2)[0]
        cnt = struct.unpack_from('<I', data, pos+4)[0]
        off = struct.unpack_from('<I', data, pos+8)[0]
        tags[tag] = (typ, cnt, off)
        pos += 12
    result = {}
    for tag, name in [(50721,'cm1'),(50722,'cm2'),(50964,'fm1'),(50965,'fm2')]:
        if tag in tags:
            typ, cnt, off = tags[tag]
            result[name] = read_srational_matrix(data, off)
    if 50728 in tags:
        _, cnt, off = tags[50728]
        asn = [read_rational(data, off+i*8) for i in range(3)]
        result['asn'] = asn
    if 50717 in tags:
        _, _, off = tags[50717]
        result['wl'] = off  # stored inline for SHORT/LONG
    return result, data

def cam_wb_decode_custom_asn(path, asn_r, asn_g, asn_b):
    """Decode using a custom AsShotNeutral (as WB multipliers = 1/asn)."""
    with rawpy.imread(path) as raw:
        wb = [1/asn_r, 1/asn_g, 1/asn_b, 1/asn_g]
        rgb = raw.postprocess(use_camera_wb=False, use_auto_wb=False,
                              user_wb=wb, output_bps=8, no_auto_bright=True)
        lum = rgb[:,:,0].astype(int)+rgb[:,:,1]+rgb[:,:,2]
        mask = lum > np.percentile(lum, 65)
        R=rgb[:,:,0][mask].mean(); G=rgb[:,:,1][mask].mean(); B=rgb[:,:,2][mask].mean()
        return R, G, B

files = {
    'uw':   'hfr-runs/v4_uw.dng',
    'wide': 'hfr-runs/v4_wide.dng',
    'tele': 'hfr-runs/v4_tele.dng',
}

parsed = {}
for cam, path in files.items():
    parsed[cam], _ = parse_dng(path)

print('=== ColorMatrix2 (D65 → sensor) ===')
print('Wide:')
print(np.round(parsed['wide']['cm2'], 4))
print()
print('UW:')
print(np.round(parsed['uw']['cm2'], 4))
print()
print('Tele:')
print(np.round(parsed['tele']['cm2'], 4))
print()

print('=== CM2 difference from Wide ===')
print('UW - Wide:')
print(np.round(parsed['uw']['cm2'] - parsed['wide']['cm2'], 4))
print()
print('Tele - Wide:')
print(np.round(parsed['tele']['cm2'] - parsed['wide']['cm2'], 4))
print()

# Are UW/tele CM2 identical to wide?
uw_eq   = np.allclose(parsed['uw']['cm2'],   parsed['wide']['cm2'], atol=0.001)
tele_eq = np.allclose(parsed['tele']['cm2'], parsed['wide']['cm2'], atol=0.001)
print(f'UW  CM2 == Wide CM2: {uw_eq}')
print(f'Tele CM2 == Wide CM2: {tele_eq}')
print()

print('=== ForwardMatrix2 ===')
print('Wide FM2:')
print(np.round(parsed['wide']['fm2'], 4))
print()
print('UW FM2:')
print(np.round(parsed['uw']['fm2'], 4))
print()
print('Tele FM2:')
print(np.round(parsed['tele']['fm2'], 4))
print()

uw_fm_eq   = np.allclose(parsed['uw']['fm2'],   parsed['wide']['fm2'], atol=0.001)
tele_fm_eq = np.allclose(parsed['tele']['fm2'], parsed['wide']['fm2'], atol=0.001)
print(f'UW  FM2 == Wide FM2: {uw_fm_eq}')
print(f'Tele FM2 == Wide FM2: {tele_fm_eq}')
print()

print('=== AsShotNeutral (embedded) ===')
for cam in ['uw','wide','tele']:
    asn = parsed[cam]['asn']
    print(f'  {cam}: [{asn[0]:.4f},{asn[1]:.4f},{asn[2]:.4f}]  WB=[R={1/asn[0]:.3f} B={1/asn[2]:.3f}]')
print()

print('=== cam-WB decode with embedded ASN ===')
for cam, path in files.items():
    try:
        with rawpy.imread(path) as raw:
            rgb = raw.postprocess(use_camera_wb=True, output_bps=8, no_auto_bright=True)
            lum = rgb[:,:,0].astype(int)+rgb[:,:,1]+rgb[:,:,2]
            mask = lum > np.percentile(lum, 65)
            R=rgb[:,:,0][mask].mean(); G=rgb[:,:,1][mask].mean(); B=rgb[:,:,2][mask].mean()
            print(f'  {cam}: R={R:.1f} G={G:.1f} B={B:.1f}  G/R={G/max(R,1):.3f}')
    except Exception as e:
        print(f'  {cam}: ERROR {e}')
print()

# Try decoding UW with wide's CM2 embedded (simulate correct matrix)
print('=== UW decode with WIDE ASN (what if wide ASN applied to UW) ===')
wide_asn = parsed['wide']['asn']
R,G,B = cam_wb_decode_custom_asn('hfr-runs/v4_uw.dng', wide_asn[0], wide_asn[1], wide_asn[2])
print(f'  UW decoded with wide ASN: R={R:.1f} G={G:.1f} B={B:.1f}  G/R={G/max(R,1):.3f}')
print()

print('=== UW decode with raw-means-derived ASN ===')
with rawpy.imread('hfr-runs/v4_uw.dng') as raw:
    bayer = raw.raw_image_visible
    bl = raw.black_level_per_channel
    cfa = raw.raw_colors_visible
    means = []
    for ch in range(4):
        px = bayer[cfa==ch].astype(float) - bl[ch]
        px = px[px>0]
        means.append(px.mean() if len(px)>100 else 0.0)
    Rr=means[0]; Gr=(means[1]+means[3])/2; Br=means[2]
    print(f'  Raw means: R={Rr:.1f} G={Gr:.1f} B={Br:.1f}  WB=[R={Gr/max(Rr,1):.3f} B={Gr/max(Br,1):.3f}]')
    # ASN = [r/g, 1, b/g] normalized
    asn_r = Rr/Gr; asn_g = 1.0; asn_b = Br/Gr
    mx = max(asn_r, asn_g, asn_b)
    asn_r/=mx; asn_b/=mx; asn_g/=mx
    print(f'  Derived ASN: [{asn_r:.4f},{asn_g:.4f},{asn_b:.4f}]')
    R,G,B = cam_wb_decode_custom_asn('hfr-runs/v4_uw.dng', asn_r, asn_g, asn_b)
    print(f'  UW decoded with raw-means ASN: R={R:.1f} G={G:.1f} B={B:.1f}  G/R={G/max(R,1):.3f}')
print()

print('Note: even with correct ASN, if CM2 is wrong the decode will still be off.')
print('The CM2 determines how XYZ maps to sensor RGB — if copy-pasted from wide,')
print('the color transform is wrong regardless of ASN.')
