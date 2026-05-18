"""
DNG color verification script for CPH2655 aux camera green/dark cast fix.
Compares UW and Tele DNG WB against Wide reference.
PASS criteria: G/R within 0.08 of wide, file opens without error.
"""
import struct, sys, os
import rawpy
import numpy as np

PASS_THRESHOLD = 0.08   # G/R delta vs wide to count as PASS

def read_asn(data):
    ifd0 = struct.unpack_from('<I', data, 4)[0]
    n    = struct.unpack_from('<H', data, ifd0)[0]
    pos  = ifd0 + 2
    for _ in range(n):
        tag = struct.unpack_from('<H', data, pos)[0]
        cnt = struct.unpack_from('<I', data, pos+4)[0]
        off = struct.unpack_from('<I', data, pos+8)[0]
        if tag == 50728:
            asn = [float(struct.unpack_from('<I',data,off+i*8)[0]) /
                   max(float(struct.unpack_from('<I',data,off+i*8+4)[0]),1)
                   for i in range(cnt)]
            return asn
        pos += 12
    return None

def cam_wb_decode(path):
    with rawpy.imread(path) as raw:
        rgb = raw.postprocess(use_camera_wb=True, output_bps=8, no_auto_bright=True)
        lum = rgb[:,:,0].astype(int)+rgb[:,:,1]+rgb[:,:,2]
        mask = lum > np.percentile(lum, 65)
        R=rgb[:,:,0][mask].mean(); G=rgb[:,:,1][mask].mean(); B=rgb[:,:,2][mask].mean()
        return R, G, B

def raw_ch_means(path):
    with rawpy.imread(path) as raw:
        bayer = raw.raw_image_visible
        bl    = raw.black_level_per_channel
        cfa   = raw.raw_colors_visible
        means = []
        for ch in range(4):
            px = bayer[cfa==ch].astype(float) - bl[ch]
            px = px[px > 0]
            means.append(px.mean() if len(px) > 100 else 0.0)
        R = means[0]; G = (means[1]+means[3])/2; B = means[2]
        return R, G, B

def audit(path, label):
    result = {'label': label, 'path': path, 'ok': False, 'gr': None, 'asn_wb_r': None, 'error': None}
    if not os.path.exists(path):
        result['error'] = 'FILE NOT FOUND'
        return result
    try:
        with open(path,'rb') as f: data = f.read()
        bo = data[0:2]; magic = struct.unpack_from('<H',data,2)[0]
        if bo != b'II' or magic != 42:
            result['error'] = 'Not a valid TIFF/DNG'
            return result
        asn = read_asn(data)
        if asn:
            result['asn_wb_r'] = round(1/asn[0], 3)
            result['asn_wb_b'] = round(1/asn[2], 3)
        R_dec, G_dec, B_dec = cam_wb_decode(path)
        result['gr']  = round(G_dec/max(R_dec,1), 3)
        result['br']  = round(B_dec/max(R_dec,1), 3)
        R_raw, G_raw, B_raw = raw_ch_means(path)
        result['raw_wb_r'] = round(G_raw/max(R_raw,0.1), 3)
        result['raw_wb_b'] = round(G_raw/max(B_raw,0.1), 3)
        result['ok'] = True
    except Exception as e:
        result['error'] = str(e)
    return result

def run(uw_path, wide_path, tele_path):
    print('=== DNG Color Verification ===')
    print()
    results = {
        'uw':   audit(uw_path,   'UW   (cam2)'),
        'wide': audit(wide_path, 'Wide (cam3)'),
        'tele': audit(tele_path, 'Tele (cam4)'),
    }

    wide_gr = results['wide']['gr']
    all_pass = True

    for key, r in results.items():
        if not r['ok']:
            print(f"  {r['label']}: ERROR — {r['error']}")
            all_pass = False
            continue
        asn_str = f"ASN_WB=[R={r.get('asn_wb_r','?')} B={r.get('asn_wb_b','?')}]"
        raw_str = f"raw_WB=[R={r.get('raw_wb_r','?')} B={r.get('raw_wb_b','?')}]"
        dec_str = f"decode G/R={r['gr']} B/R={r['br']}"
        if key == 'wide':
            print(f"  {r['label']}: {asn_str}  {raw_str}  {dec_str}  [REFERENCE]")
        elif wide_gr is not None:
            delta = abs(r['gr'] - wide_gr)
            status = 'PASS' if delta <= PASS_THRESHOLD else 'FAIL'
            if status == 'FAIL': all_pass = False
            print(f"  {r['label']}: {asn_str}  {raw_str}  {dec_str}  delta={delta:.3f}  [{status}]")
        else:
            print(f"  {r['label']}: {asn_str}  {raw_str}  {dec_str}")

    print()
    if wide_gr is not None and results['wide']['ok']:
        print(f"  Wide G/R reference: {wide_gr:.3f}")
        print(f"  PASS threshold:     ±{PASS_THRESHOLD}")

    print()
    if all_pass:
        print('OVERALL: PASS ✓')
    else:
        print('OVERALL: FAIL ✗')
    print()
    return all_pass

if __name__ == '__main__':
    uw   = sys.argv[1] if len(sys.argv) > 1 else 'hfr-runs/v4_uw.dng'
    wide = sys.argv[2] if len(sys.argv) > 2 else 'hfr-runs/v4_wide.dng'
    tele = sys.argv[3] if len(sys.argv) > 3 else 'hfr-runs/v4_tele.dng'
    ok = run(uw, wide, tele)
    sys.exit(0 if ok else 1)
