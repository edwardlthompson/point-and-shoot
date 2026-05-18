import rawpy, numpy as np, struct

def raw_channel_ratios(path):
    with rawpy.imread(path) as raw:
        bayer = raw.raw_image_visible
        bl = raw.black_level_per_channel
        cfa = raw.raw_colors_visible
        means = []
        for ch in range(4):
            px = bayer[cfa==ch].astype(float) - bl[ch]
            px = px[px > 0]
            means.append(px.mean() if len(px) > 1000 else 0)
        R = means[0]; G = (means[1]+means[3])/2; B = means[2]
        return R, G, B

def day_wb(path):
    with rawpy.imread(path) as raw:
        return raw.daylight_whitebalance  # [R,G,B,G2]

wide_dw  = day_wb('hfr-runs/diag_wide.dng')
uw_dw    = day_wb('hfr-runs/diag_uw.dng')
tele_dw  = day_wb('hfr-runs/diag_tele.dng')

print(f'Wide daylight_wb: R={wide_dw[0]:.4f} G={wide_dw[1]:.4f} B={wide_dw[2]:.4f}')
print(f'UW   daylight_wb: R={uw_dw[0]:.4f} G={uw_dw[1]:.4f} B={uw_dw[2]:.4f}')
print(f'Tele daylight_wb: R={tele_dw[0]:.4f} G={tele_dw[1]:.4f} B={tele_dw[2]:.4f}')
print()

# Wide scene WB from raw
wR, wG, wB = raw_channel_ratios('hfr-runs/diag_wide.dng')
wide_wb_R = wG / wR
wide_wb_B = wG / wB
print(f'Wide raw scene WB: R={wide_wb_R:.4f} B={wide_wb_B:.4f}')

# Illuminant shift = scene WB / daylight WB  (same illuminant for all cameras)
shift_R = wide_wb_R / wide_dw[0]
shift_B = wide_wb_B / wide_dw[2]
print(f'Illuminant shift vs D65: R={shift_R:.4f} B={shift_B:.4f}')
print()

def compute_correct_asn(cam_dw, shift_R, shift_B):
    wb_R = cam_dw[0] * shift_R
    wb_G = cam_dw[1]
    wb_B = cam_dw[2] * shift_B
    asn_R = 1.0 / wb_R
    asn_G = 1.0 / wb_G
    asn_B = 1.0 / wb_B
    mx = max(asn_R, asn_G, asn_B)
    return [asn_R/mx, asn_G/mx, asn_B/mx]

uw_asn   = compute_correct_asn(uw_dw,   shift_R, shift_B)
tele_asn = compute_correct_asn(tele_dw, shift_R, shift_B)
wide_asn = compute_correct_asn(wide_dw, shift_R, shift_B)

uR, uG, uB = raw_channel_ratios('hfr-runs/diag_uw.dng')
tR, tG, tB = raw_channel_ratios('hfr-runs/diag_tele.dng')

print(f'UW  correct ASN: R={uw_asn[0]:.4f} G={uw_asn[1]:.4f} B={uw_asn[2]:.4f}  -> WB R={1/uw_asn[0]:.3f} B={1/uw_asn[2]:.3f}')
print(f'     (raw true scene WB: R={uG/uR:.3f} B={uG/uB:.3f})')
print()
print(f'Tele correct ASN: R={tele_asn[0]:.4f} G={tele_asn[1]:.4f} B={tele_asn[2]:.4f}  -> WB R={1/tele_asn[0]:.3f} B={1/tele_asn[2]:.3f}')
print(f'     (raw true scene WB: R={tG/tR:.3f} B={tG/tB:.3f})')
print()

# Verify by decoding with corrected ASN
print('=== Verification: decode with corrected ASN ===')
for label, path, asn in [
        ('UW',   'hfr-runs/diag_uw.dng',   uw_asn),
        ('Wide', 'hfr-runs/diag_wide.dng',  wide_asn),
        ('Tele', 'hfr-runs/diag_tele.dng',  tele_asn)]:
    with rawpy.imread(path) as raw:
        wb = [1/asn[0], 1/asn[1], 1/asn[2], 1/asn[1]]
        rgb = raw.postprocess(use_camera_wb=False, use_auto_wb=False,
                              user_wb=wb, output_bps=8, no_auto_bright=True)
        lum = rgb[:,:,0].astype(int)+rgb[:,:,1]+rgb[:,:,2]
        mask = lum > np.percentile(lum, 65)
        R=rgb[:,:,0][mask].mean(); G=rgb[:,:,1][mask].mean(); B=rgb[:,:,2][mask].mean()
        print(f'  {label:5s}: R={R:.1f} G={G:.1f} B={B:.1f}  G/R={G/max(R,1):.3f} B/R={B/max(R,1):.3f}')
