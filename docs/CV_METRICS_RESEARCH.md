# Computer Vision Metrics Research (Sprint 12.6)

Research document for automating dE2000 color accuracy and MTF50 slanted-edge sharpness measurement using ImageMagick and OpenCV.

## Executive Summary

Both metrics are automatable with existing open-source tools. Recommended implementation path:
- **dE2000**: ImageMagick (built-in) or OpenCV + custom formula
- **MTF50**: ImageJ macro (reference) or Python scikit-image implementation

## dE2000 Color Accuracy

### What It Measures
Delta E 2000 (dE2000) quantifies perceptual color difference between two samples. Values:
- < 1.0: Imperceptible to human eye
- 1-2: Perceptible to trained observers
- 2-3.5: Perceptible to untrained observers
- > 5: Clearly different colors

### Implementation Options

#### Option A: ImageMagick (Recommended for PowerShell)
```powershell
# Compare captured chart vs reference
magick compare -metric DE -color-delta E2000 capture.jpg reference.jpg result.png
```

**Pros**:
- Native PowerShell integration
- Built-in DE metric
- No custom code needed

**Cons**:
- Requires calibrated reference image
- Works on flat regions, not chart-based

#### Option B: Python OpenCV + colormath
```python
from colormath.color_objects import LabColor
from colormath.color_diff import delta_e_cie2000

# Extract ColorChecker patches via OpenCV contour detection
# Compare to reference values from X-Rite datasheets
ref = LabColor(lab_l=50, lab_a=0, lab_b=0)
cap = LabColor(lab_l=48, lab_a=2, lab_b=-1)
de = delta_e_cie2000(ref, cap)
```

**Pros**:
- Chart-aware detection possible
- Industry standard library
- Works with Passport/ColorChecker targets

**Cons**:
- Requires Python runtime
- More complex integration

### Recommended Approach for Point & Shoot
Use **ImageMagick comparison** for simple validation, with Python OpenCV option for detailed chart analysis if needed later.

## MTF50 Slanted-Edge Sharpness

### What It Measures
Modulation Transfer Function at 50% contrast (MTF50) measures image sharpness in line pairs per mm (lp/mm) or cycles per pixel (cy/px).

### Implementation Options

#### Option A: ImageJ Macro (Reference Implementation)
The industry standard is the ImageJ "Slanted Edge MTF" macro by Frans van den Bergh.

```javascript
// ImageJ macro for slanted edge MTF
run("Slanted Edge MTF", "roi=[roi.txt]");
```

**Pros**:
- ISO 12233 compliant
- Validated against Imatest

**Cons**:
- Requires ImageJ installation
- GUI-based, harder to automate

#### Option B: Python scikit-image (Recommended for Automation)
```python
from skimage.measure import blur_effect
from skimage.io import imread

# Simpler approach: edge acutance
image = imread('capture.jpg', as_gray=True)
mtf_proxy = blur_effect(image)  # Lower = sharper
```

For proper MTF50, use the `sfrmat3` algorithm or `pyctf` library.

```python
# Using pyctf (Contrast Transfer Function)
from pyctf import sfr_from_image

# Detect slanted edge region
# Compute edge spread function (ESF)
# Derive line spread function (LSF)
# FFT to get MTF
# Find frequency at 50% contrast
mtf50 = sfr_from_image('slanted_edge_roi.png')
```

### Recommended Approach for Point & Shoot
Implement **Python scikit-image** solution in `pns_capture_quality_probe.ps1` wrapper:

1. Extract slanted edge ROI from test chart
2. Use `skimage.measure.blur_effect` as proxy metric
3. Or implement full SFR algorithm if pyctf available

## Integration Plan

### Sprint 12.6 Script: `pns_capture_quality_probe.ps1`

```powershell
# Workflow:
# 1. Pull captures from device
# 2. Detect chart in image (OpenCV or simple threshold)
# 3. Extract uniform patches for dE2000
# 4. Extract slanted edge for MTF50
# 5. Run metrics
# 6. Compare to thresholds
# 7. Output JSON for PROBE_BUILD_PLAN.md §5
```

### Dependencies

**ImageMagick** (for dE2000):
```powershell
choco install imagemagick
# or
winget install ImageMagick.ImageMagick
```

**Python** (for MTF50):
```powershell
pip install scikit-image numpy opencv-python-headless
```

### Output Format

```json
{
  "timestamp": "2026-05-16T12:00:00Z",
  "metrics": {
    "dE2000": {
      "mean": 1.2,
      "max": 2.1,
      "patches": [...],
      "pass": true,
      "threshold": 3.0
    },
    "MTF50": {
      "lp_mm": 45,
      "cy_px": 0.35,
      "pass": true,
      "threshold_lp_mm": 30
    }
  }
}
```

## Risk Assessment

| Risk | Mitigation |
|------|------------|
| Chart detection fails | Use controlled lighting + simple thresholding |
| Python not available | Fallback to ImageMagick-only (dE2000 only) |
| No slanted edge target | Use flat-field acutance metric instead |
| Reference data missing | Document expected ColorChecker values |

## Next Steps

1. ✅ Document research (this file)
2. ⬜ Install ImageMagick on build host
3. ⬜ Create `pns_capture_quality_probe.ps1` prototype
4. ⬜ Validate against manual darktable/RawTherapee analysis
5. ⬜ Integrate into Sprint 12.6 automation pack

## References

- [CIE Delta E 2000](https://en.wikipedia.org/wiki/Color_difference#CIEDE2000)
- [ISO 12233 MTF Measurement](https://www.imatest.com/solutions/mtf/)
- [ImageMagick Color Comparison](https://imagemagick.org/script/compare.php)
- [scikit-image blur effect](https://scikit-image.org/docs/dev/api/skimage.measure.html#skimage.measure.blur_effect)
- [pyctf MTF library](https://github.com/letienhung/pyctf)

## Decision: Deferred for Future Sprint

**Recommendation**: Mark CV metrics as **research complete** but defer implementation to post-M12 sprint. Rationale:
1. Requires chart-based physical setup (human-dependent)
2. Complex CV pipeline vs file validation scripts
3. H.2 visual review is acceptable interim solution

When resources available:
- Implement dE2000 with ImageMagick (simpler)
- Implement MTF50 proxy with scikit-image
- Create controlled chart capture automation
