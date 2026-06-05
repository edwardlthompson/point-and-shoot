import { fmtNum, progressBar, maxAdvertisedRearMp, maxHalMpFromEntry } from './theme.js';

function pickVariant(variants, romFlavors) {
  if (!variants?.length) return null;
  for (const rf of romFlavors) {
    const v = variants.find((x) => x.romFlavor === rf);
    if (v) return v;
  }
  return null;
}

function delta(a, b) {
  if (a == null || b == null) return '—';
  const d = Number(a) - Number(b);
  if (Number.isNaN(d)) return '—';
  return d > 0 ? `+${d}` : `${d}`;
}

export function renderProductGroup(group, devicesBySlug) {
  if (!group) return '<p>Product group not found.</p>';
  const custom = pickVariant(group.testedVariants, ['custom_likely', 'unknown', 'engineering', 'root_unlocked']);
  const stock = pickVariant(group.testedVariants, ['stock']);
  const adv = group.advertisedSpec;

  const col = (variant, kind) => {
    if (kind === 'advertised') {
      if (!adv) return { label: '—', pts: '—', honesty: '—', betrayal: '—', maxMp: '—', hfr: '—' };
      const maxClaim = (adv.advertisedClaims || []).find((c) => c.catalogId === 'still.resolution_max');
      const hfr = (adv.advertisedClaims || []).some((c) => c.catalogId?.startsWith('video.hfr'));
      return {
        label: 'spec',
        pts: '— (not ranked)',
        honesty: '—',
        betrayal: '—',
        maxMp: maxClaim?.advertisedValue || '—',
        hfr: hfr ? 'yes (spec)' : '—',
      };
    }
    if (!variant) {
      return { label: '—', pts: '—', honesty: '—', betrayal: '—', maxMp: '—', hfr: '—' };
    }
    const d = devicesBySlug[variant.slug];
    if (!d) {
      return {
        label: variant.slug,
        pts: variant.parityScore ?? '—',
        honesty: variant.honestyPercent != null ? `${variant.honestyPercent}%` : '—',
        betrayal: variant.resolutionBetrayalIndex ?? '—',
        maxMp: '—',
        hfr: '—',
      };
    }
    const specMp = maxAdvertisedRearMp(d);
    const halMax = (d.stillResolutionHonesty || []).reduce((m, r) => Math.max(m, maxHalMpFromEntry(r)), 0);
    const halRounded = halMax > 0 ? Math.round(halMax * 10) / 10 : 0;
    let maxMp = '—';
    if (specMp) {
      const specRounded = Math.round(specMp * 10) / 10;
      maxMp = halRounded > 0 && halRounded < specRounded * 0.9
        ? `${specRounded} spec · ${halRounded} Camera2`
        : `${specRounded} MP`;
    } else if (halRounded > 0) {
      maxMp = `${halRounded} (Camera2)`;
    }
    const hfrCell = Object.values(d.cellsByCategory || {}).flat().find((c) => c.catalogId?.startsWith('video.hfr') && c.provenOk);
    return {
      label: d.slug,
      pts: d.scores?.total?.score ?? '—',
      honesty: `${d.disparity?.honestyPercent ?? '—'}%`,
      betrayal: d.resolutionBetrayal?.index ?? variant.resolutionBetrayalIndex ?? '—',
      maxMp,
      hfr: hfrCell ? 'pass' : 'fail',
      device: d,
    };
  };

  const customCol = col(custom, 'tested');
  const stockCol = col(stock, 'tested');
  const advCol = col(null, 'advertised');

  const customDev = custom?.slug ? devicesBySlug[custom.slug] : null;
  const stockDev = stock?.slug ? devicesBySlug[stock.slug] : null;

  return `
    <p><a href="#/">&larr; Leaderboard</a></p>
    <div class="device-card">
      <h1>${group.marketingName}</h1>
      <p class="sub">Product comparison — separate line items for Camera2 tested vs GSMArena advertised specs.</p>
      <p class="disclosure-banner">Scores reflect Camera2 via Point & Shoot, not the OEM camera app. GSMArena column is marketing/spec-sheet only.</p>
    </div>
    <div class="data-table-wrap">
      <table class="data-table product-compare">
        <thead>
          <tr>
            <th>Metric</th>
            <th>Camera2 custom ROM (tested)</th>
            <th>Camera2 stock (tested)</th>
            <th>Advertised (GSMArena, untested)</th>
          </tr>
        </thead>
        <tbody>
          <tr><td>Parity pts</td><td>${customCol.pts}</td><td>${stockCol.pts}</td><td>${advCol.pts}</td></tr>
          <tr><td>Honesty %</td><td>${customCol.honesty}</td><td>${stockCol.honesty}</td><td>${advCol.honesty}</td></tr>
          <tr><td>Resolution betrayal</td><td>${customCol.betrayal}</td><td>${stockCol.betrayal}</td><td>${advCol.betrayal}</td></tr>
          <tr><td>Max rear MP</td><td>${customCol.maxMp}</td><td>${stockCol.maxMp}</td><td>${advCol.maxMp}</td></tr>
          <tr><td>4K120 / HFR</td><td>${customCol.hfr}</td><td>${stockCol.hfr}</td><td>${advCol.hfr}</td></tr>
        </tbody>
      </table>
    </div>
    <section class="device-card">
      <h3>Deltas</h3>
      <ul>
        <li><strong>Custom vs stock (Camera2):</strong> parity ${delta(customCol.pts, stockCol.pts)} · honesty ${delta(parseFloat(customCol.honesty), parseFloat(stockCol.honesty))}</li>
        <li><strong>Stock tested vs GSMArena:</strong> marketing vs measured stock Camera2 (when stock sweep exists)</li>
        <li><strong>Custom vs GSMArena:</strong> worst-case custom-ROM buyer view</li>
      </ul>
      ${!stock && custom ? '<p class="cta-box"><strong>No stock ROM Camera2 sweep yet.</strong> Contribute a Full parity sweep on stock firmware for this phone.</p>' : ''}
    </section>
    <section class="device-card">
      <h3>Tested device pages</h3>
      <ul>
        ${customDev ? `<li><a href="#/device/${customDev.slug}">Custom/custom-ROM sweep</a> (${customDev.software?.romFlavor})</li>` : '<li>No custom-ROM sweep linked</li>'}
        ${stockDev ? `<li><a href="#/device/${stockDev.slug}">Stock ROM sweep</a></li>` : '<li>No stock ROM sweep linked</li>'}
        ${adv?.gsmarenaUrl ? `<li><a href="${adv.gsmarenaUrl}" target="_blank" rel="noopener">GSMArena spec page</a> (advertised, untested)</li>` : ''}
      </ul>
    </section>`;
}
