"""
Compute the static WB scale factors for CPH2655 UW and Tele cameras.

The HAL reports wide camera ccGains for ALL cameras. The correct gains for
each aux camera = wide_gains * sensor_sensitivity_ratio.

The sensor sensitivity ratio is a fixed hardware property:
  sens_ratio_R = (aux R/G in raw) / (wide R/G in raw)   -- for same illuminant
  sens_ratio_B = (aux B/G in raw) / (wide B/G in raw)

Since we have multiple paired shots (same illuminant, all cameras):
  For each shot pair (aux_shot, wide_shot):
    ratio_R = aux_R/G / wide_R/G
    ratio_B = aux_B/G / wide_B/G

Average across shots for a robust estimate.

Then in the app:
  corrected_R_gain = wide_ccGains_R * sens_ratio_R
  corrected_B_gain = wide_ccGains_B * sens_ratio_B

But we don't have wide's ccGains at aux save time. HOWEVER:
  The correct ASN = 1 / corrected_gain (normalized)
  And: corrected_R_gain = wide_ccGains_R * sens_ratio_R
     = (wide_embedded_ASN_WB_R) * sens_ratio_R
     
We CAN get wide's embedded ASN from the TotalCaptureResult's
SENSOR_NEUTRAL_COLOR_POINT — which IS correct for wide (only wrong for aux).

Actually simpler: the ccGains the HAL gives to aux IS wide's ccGains.
So: corrected_aux_gains_R = hal_ccGains_R * sens_ratio_R
"""

import rawpy, numpy as np

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

# Paired shots: (wide_path, uw_path, tele_path)
# These are the chart shots and the diag shots (different scenes/lighting)
paired = [
    # original chart (indoor, same scene angle)
    ('hfr-runs/chart_cam_b.dng', 'hfr-runs/chart_cam_a.dng', 'hfr-runs/chart_cam_c.dng'),
    # v3 chart
    ('hfr-runs/chart_v3_b.dng',  'hfr-runs/chart_v3_a.dng',  'hfr-runs/chart_v3_c.dng'),
    # diag shots
    ('hfr-runs/diag_wide.dng',   'hfr-runs/diag_uw.dng',     'hfr-runs/diag_tele.dng'),
]

uw_ratios_R = []; uw_ratios_B = []
tele_ratios_R = []; tele_ratios_B = []

print('Per-shot sensor sensitivity ratios (aux R/G / wide R/G):')
print()
for (wp, up, tp) in paired:
    wR,wG,wB = raw_means(wp)
    uR,uG,uB = raw_means(up)
    tR,tG,tB = raw_means(tp)
    if all(x > 5 for x in [wR,wG,wB,uR,uG,uB,tR,tG,tB]):
        ur = (uR/uG) / (wR/wG)
        ub = (uB/uG) / (wB/wG)
        tr = (tR/tG) / (wR/wG)
        tb = (tB/tG) / (wB/wG)
        uw_ratios_R.append(ur);   uw_ratios_B.append(ub)
        tele_ratios_R.append(tr); tele_ratios_B.append(tb)
        scene = wp.split('/')[-1].replace('.dng','')
        print(f'  {scene}: UW R={ur:.4f} B={ub:.4f}  Tele R={tr:.4f} B={tb:.4f}')

print()
uw_R   = np.mean(uw_ratios_R);   uw_B   = np.mean(uw_ratios_B)
tele_R = np.mean(tele_ratios_R); tele_B = np.mean(tele_ratios_B)
print(f'MEAN sensor sensitivity ratios:')
print(f'  UW   R={uw_R:.4f}  B={uw_B:.4f}  (std R={np.std(uw_ratios_R):.4f} B={np.std(uw_ratios_B):.4f})')
print(f'  Tele R={tele_R:.4f}  B={tele_B:.4f}  (std R={np.std(tele_ratios_R):.4f} B={np.std(tele_ratios_B):.4f})')
print()
print('Low std = stable across scenes = truly a fixed hardware property.')
print()
print(f'Apply in DngForwardMatrixFix or Dng12Saver:')
print(f'  UW   corrected_ccGains_R = hal_ccGains_R * {uw_R:.4f}f')
print(f'  UW   corrected_ccGains_B = hal_ccGains_B * {uw_B:.4f}f')
print(f'  Tele corrected_ccGains_R = hal_ccGains_R * {tele_R:.4f}f')
print(f'  Tele corrected_ccGains_B = hal_ccGains_B * {tele_B:.4f}f')
print()

# Final verification: apply corrected gains to diag shots and decode
print('=== Verification: corrected gains decode vs wide reference ===')
import struct

def read_asn_wb(path):
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
                   max(float(struct.unpack_from('<I',data,off+i*8+4)[0]),1)
                   for i in range(cnt)]
            return 1/asn[0], 1/asn[1], 1/asn[2]
        pos += 12
    return None

for label, path, cR, cB in [
        ('UW',   'hfr-runs/diag_uw.dng',   uw_R,   uw_B),
        ('Tele', 'hfr-runs/diag_tele.dng',  tele_R, tele_B)]:
    wb = read_asn_wb(path)
    corrR = wb[0] * cR
    corrB = wb[2] * cB
    corrG = wb[1]
    R,G,B = raw_means(path)
    print(f'{label}: hal WB R={wb[0]:.3f} B={wb[2]:.3f}  corrected WB R={corrR:.3f} B={corrB:.3f}  raw needs R={G/R:.3f} B={G/B:.3f}')
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

with rawpy.imread('hfr-runs/diag_wide.dng') as raw:
    rgb = raw.postprocess(use_camera_wb=True, output_bps=8, no_auto_bright=True)
    lum = rgb[:,:,0].astype(int)+rgb[:,:,1]+rgb[:,:,2]
    mask = lum > np.percentile(lum, 65)
    Rw=rgb[:,:,0][mask].mean(); Gw=rgb[:,:,1][mask].mean(); Bw=rgb[:,:,2][mask].mean()
    print(f'Wide ref:  R={Rw:.1f} G={Gw:.1f} B={Bw:.1f}  G/R={Gw/max(Rw,1):.3f} B/R={Bw/max(Rw,1):.3f}')
