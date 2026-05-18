import rawpy, numpy as np, struct

def raw_means(path):
    with rawpy.imread(path) as raw:
        bayer = raw.raw_image_visible
        bl = raw.black_level_per_channel
        cfa = raw.raw_colors_visible
        means = []
        for ch in range(4):
            px = bayer[cfa==ch].astype(float) - bl[ch]
            px = px[px > 0]
            means.append(px.mean() if len(px) > 1000 else 0.0)
        R = means[0]; G = (means[1]+means[3])/2.0; B = means[2]
        return R, G, B

def read_asn_wb(path):
    with open(path,'rb') as f:
        data = f.read()
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
            return 1/asn[0], 1/asn[1], 1/asn[2]
        pos += 12
    return None

# All chart/diag shots per camera
shots = {
    'wide': [
        'hfr-runs/chart_cam_b.dng',
        'hfr-runs/chart_v3_b.dng',
        'hfr-runs/diag_wide.dng',
    ],
    'uw': [
        'hfr-runs/chart_cam_a.dng',
        'hfr-runs/chart_v3_a.dng',
        'hfr-runs/diag_uw.dng',
    ],
    'tele': [
        'hfr-runs/chart_cam_c.dng',
        'hfr-runs/chart_v3_c.dng',
        'hfr-runs/diag_tele.dng',
    ],
}

# Collect raw R/G and B/G ratios per camera across all shots
cam_rg = {k: [] for k in shots}
cam_bg = {k: [] for k in shots}

for cam, paths in shots.items():
    for p in paths:
        try:
            R, G, B = raw_means(p)
            if R > 5 and G > 5 and B > 5:
                cam_rg[cam].append(R/G)
                cam_bg[cam].append(B/G)
                fname = p.split('/')[-1]
                print(f'  {cam:5s} {fname}: R/G={R/G:.4f}  B/G={B/G:.4f}')
        except Exception as e:
            print(f'  SKIP {p}: {e}')

print()
mW_R = np.mean(cam_rg['wide']);  mW_B = np.mean(cam_bg['wide'])
mU_R = np.mean(cam_rg['uw']);    mU_B = np.mean(cam_bg['uw'])
mT_R = np.mean(cam_rg['tele']);  mT_B = np.mean(cam_bg['tele'])

print(f'Mean R/G: wide={mW_R:.4f}  uw={mU_R:.4f}  tele={mT_R:.4f}')
print(f'Mean B/G: wide={mW_B:.4f}  uw={mU_B:.4f}  tele={mT_B:.4f}')
print()

# Correction factor = how much to MULTIPLY the HAL-reported WB gains
# HAL gives wide's gains to aux cameras.
# Correct aux gains = HAL_gains * correction
# correction_R = (aux_R/G) / (wide_R/G)  -- aux sensor is more/less red-sensitive
corr_uw_R   = mU_R / mW_R
corr_uw_B   = mU_B / mW_B
corr_tele_R = mT_R / mW_R
corr_tele_B = mT_B / mW_B

print('Sensor sensitivity correction factors (multiply HAL ccGains by these):')
print(f'  UW   R: {corr_uw_R:.4f}   B: {corr_uw_B:.4f}')
print(f'  Tele R: {corr_tele_R:.4f}   B: {corr_tele_B:.4f}')
print()

# Verify on diag shots
print('=== Verification on diag shots ===')
wide_wb = read_asn_wb('hfr-runs/diag_wide.dng')
uw_wb   = read_asn_wb('hfr-runs/diag_uw.dng')
tele_wb = read_asn_wb('hfr-runs/diag_tele.dng')

print(f'Wide  embedded WB: R={wide_wb[0]:.3f} B={wide_wb[2]:.3f}')

for label, path, emb_wb, cR, cB in [
        ('UW',   'hfr-runs/diag_uw.dng',   uw_wb,   corr_uw_R,   corr_uw_B),
        ('Tele', 'hfr-runs/diag_tele.dng',  tele_wb, corr_tele_R, corr_tele_B)]:
    corrR = emb_wb[0] * cR
    corrB = emb_wb[2] * cB
    corrG = emb_wb[1]
    R, G, B = raw_means(path)
    print(f'{label}: embedded WB R={emb_wb[0]:.3f} B={emb_wb[2]:.3f}')
    print(f'       corrected WB R={corrR:.3f} B={corrB:.3f}  (true raw: R={G/R:.3f} B={G/B:.3f})')
    with rawpy.imread(path) as raw:
        rgb = raw.postprocess(
            use_camera_wb=False, use_auto_wb=False,
            user_wb=[corrR, corrG, corrB, corrG],
            output_bps=8, no_auto_bright=True)
        lum = rgb[:,:,0].astype(int)+rgb[:,:,1]+rgb[:,:,2]
        mask = lum > np.percentile(lum, 65)
        Rd = rgb[:,:,0][mask].mean()
        Gd = rgb[:,:,1][mask].mean()
        Bd = rgb[:,:,2][mask].mean()
        print(f'       decode: R={Rd:.1f} G={Gd:.1f} B={Bd:.1f}  G/R={Gd/max(Rd,1):.3f} B/R={Bd/max(Rd,1):.3f}')
    print()

# Wide reference
with rawpy.imread('hfr-runs/diag_wide.dng') as raw:
    rgb = raw.postprocess(use_camera_wb=True, output_bps=8, no_auto_bright=True)
    lum = rgb[:,:,0].astype(int)+rgb[:,:,1]+rgb[:,:,2]
    mask = lum > np.percentile(lum, 65)
    Rw = rgb[:,:,0][mask].mean()
    Gw = rgb[:,:,1][mask].mean()
    Bw = rgb[:,:,2][mask].mean()
    print(f'Wide reference: R={Rw:.1f} G={Gw:.1f} B={Bw:.1f}  G/R={Gw/max(Rw,1):.3f} B/R={Bw/max(Rw,1):.3f}')
