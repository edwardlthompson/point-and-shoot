const WISHLIST_ITEMS = [
  { id: 'raw.dng', label: 'RAW DNG' },
  { id: 'video.hfr', label: 'HFR 120+' },
  { id: 'video.av1', label: 'AV1' },
  { id: 'face.eye_af', label: 'Eye-AF' },
  { id: 'lens.variable_aperture', label: 'Var aperture' },
];

export function initTheme() {
  const stored = localStorage.getItem('pns-lb-theme');
  const theme = stored || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
  document.documentElement.setAttribute('data-theme', theme);
  document.getElementById('theme-toggle')?.addEventListener('click', () => {
    const next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem('pns-lb-theme', next);
  });
}

export function initMethodology() {
  const drawer = document.getElementById('methodology-drawer');
  document.getElementById('methodology-toggle')?.addEventListener('click', () => {
    drawer?.classList.remove('hidden');
    drawer?.setAttribute('aria-hidden', 'false');
  });
  document.getElementById('methodology-close')?.addEventListener('click', () => {
    drawer?.classList.add('hidden');
    drawer?.setAttribute('aria-hidden', 'true');
  });
}

export function initWishlist(onChange) {
  const el = document.getElementById('wishlist');
  if (!el) return;
  WISHLIST_ITEMS.forEach((item) => {
    const label = document.createElement('label');
    const cb = document.createElement('input');
    cb.type = 'checkbox';
    cb.value = item.id;
    cb.addEventListener('change', onChange);
    label.appendChild(cb);
    label.appendChild(document.createTextNode(item.label));
    el.appendChild(label);
  });
}

export function getWishlistSelected() {
  return [...document.querySelectorAll('#wishlist input:checked')].map((c) => c.value);
}

export function trustBadge(tier, rom) {
  if (rom === 'root_unlocked' || rom === 'custom_likely') return '<span class="badge badge-root">Root/Custom ROM</span>';
  if (tier === 'maintainer') return '<span class="badge badge-maintainer">Fleet tested</span>';
  if (tier === 'community_verified') return '<span class="badge badge-community">Community verified</span>';
  return '<span class="badge badge-preview">Community preview</span>';
}

const API_NAMES = {
  33: 'Android 13',
  34: 'Android 14',
  35: 'Android 15',
  36: 'Android 16',
};

/** Human-readable label for the Android API level the device was tested on. */
export function formatApiLevel(device) {
  if (device?.software?.apiLevelLabel) return device.software.apiLevelLabel;
  const sdk = device?.software?.sdkInt ?? device?.meta?.testedSdkInt;
  if (sdk == null || sdk === '') return null;
  const n = Number(sdk);
  const name = API_NAMES[n] || `Android API ${n}`;
  return `${name} (API ${n})`;
}

export function apiLevelBadge(device) {
  const label = formatApiLevel(device);
  if (!label) return '';
  return `<span class="badge badge-api" title="Android version when parity sweep ran">${label}</span>`;
}

export function fmtNum(n) {
  if (n == null || n === '') return '—';
  return typeof n === 'number' ? n.toLocaleString() : n;
}

export function getGsmarenaUrl(device) {
  if (device?.identity?.gsmarenaUrl) return device.identity.gsmarenaUrl;
  const links = device?.identity?.specLinks;
  const list = Array.isArray(links) ? links : links?.url ? [links] : [];
  const match = list.find((l) => /gsmarena\.com/i.test(l?.url || ''));
  if (match?.url) return match.url;
  const src = device?.sensors?.sourceUrl;
  if (/gsmarena\.com/i.test(src || '')) return src;
  return null;
}

export function gsmarenaLinkHtml(device, className = 'ext-spec-link') {
  const url = getGsmarenaUrl(device);
  if (!url) return '';
  const cls = className ? ` class="${className}"` : '';
  return `<a href="${url}" target="_blank" rel="noopener noreferrer"${cls}>GSMArena</a>`;
}

export function specLinksHtml(device) {
  const links = device?.identity?.specLinks;
  const list = Array.isArray(links) ? links : links?.url ? [links] : [];
  const gsmUrl = getGsmarenaUrl(device);
  const parts = [];
  if (gsmUrl) {
    parts.push(`<a href="${gsmUrl}" target="_blank" rel="noopener noreferrer" class="ext-spec-link">GSMArena</a>`);
  }
  for (const l of list) {
    if (!l?.url || /gsmarena\.com/i.test(l.url)) continue;
    parts.push(`<a href="${l.url}" target="_blank" rel="noopener noreferrer">${l.label || 'Specs'}</a>`);
  }
  return parts.join(' · ');
}

export function sensorSumLabel(device) {
  const mm2 = device?.sensors?.sensorSumMm2;
  if (mm2 == null || mm2 === 0) return '—';
  return fmtNum(mm2);
}

export function sensorSourceNote(device) {
  const s = device?.sensors;
  if (!s?.sourceLabel) return '';
  const gsm = gsmarenaLinkHtml(device, 'ext-spec-link inline');
  if (gsm && s.source === 'gsmarena') return gsm;
  const url = s.sourceUrl ? ` <a href="${s.sourceUrl}" target="_blank" rel="noopener noreferrer">source</a>` : '';
  return `${s.sourceLabel}${url}`;
}

export function renderRearLensTable(device) {
  const lenses = device?.sensors?.rearLenses || [];
  const hal = device?.sensors?.sensors || [];
  if (lenses.length) {
    const rows = lenses.map((l) => {
      const type = l.sensorTypeFraction ? `${l.sensorTypeFraction}"` : '—';
      const area = l.areaMm2 ? `${l.areaMm2} mm²` : '—';
      const role = l.role || 'rear';
      const mp = l.megapixels ? `${l.megapixels} MP` : '';
      const focal = l.focalLengthMm ? `${l.focalLengthMm}mm` : '';
      return `<tr><td>${role}</td><td>${[mp, focal].filter(Boolean).join(' · ') || '—'}</td><td>${type}</td><td>${area}</td></tr>`;
    }).join('');
    return `<table class="data-table sensor-table"><thead><tr><th>Role</th><th>Spec</th><th>Sensor type</th><th>Area</th></tr></thead><tbody>${rows}</tbody></table>`;
  }
  if (hal.length) {
    const rows = hal.map((s) =>
      `<tr><td>${s.role || s.cameraId}</td><td>${s.megapixels ? `${Math.round(s.megapixels * 10) / 10} MP` : '—'}</td><td>${s.widthMm && s.heightMm ? `${s.widthMm}×${s.heightMm} mm` : '—'}</td><td>${s.areaMm2 ? `${s.areaMm2} mm²` : '—'}</td></tr>`
    ).join('');
    return `<table class="data-table sensor-table"><thead><tr><th>Role</th><th>MP</th><th>Physical size</th><th>Area</th></tr></thead><tbody>${rows}</tbody></table>`;
  }
  return '<p>No sensor size data.</p>';
}

export function progressBar(pct) {
  const p = Math.min(100, Math.max(0, Number(pct) || 0));
  return `<div class="progress" title="${p}%"><span style="width:${p}%"></span></div>`;
}

export function glossaryLabel(text, glossary, termId) {
  const term = glossary?.terms?.find((t) => t.id === termId);
  if (!term) return text;
  return `<span class="glossary-term" title="${term.definition.replace(/"/g, '&quot;')}">${text}</span>`;
}

const MP_RATIO_THRESHOLD = 1.25;

function mpFromSize(s) {
  if (!s) return 0;
  const mp = s.mp ?? (s.width && s.height ? (s.width * s.height) / 1e6 : 0);
  return mp > 0 ? mp : 0;
}

/** Default still MP on the Camera2 path for a resolution-honesty row. */
export function defaultMpFromEntry(r) {
  return Math.max(mpFromSize(r?.defaultJpeg), mpFromSize(r?.defaultRawSensor));
}

/** Max MP from Camera2 HAL stream maps on a resolution-honesty row. */
export function maxHalMpFromEntry(r) {
  const sizes = [
    r.defaultJpeg,
    r.highResJpeg,
    r.maxResMapJpeg,
    r.multiResJpeg,
    r.defaultRawSensor,
    r.highResRawSensor,
    r.maxResMapRawSensor,
  ];
  return sizes.reduce((m, s) => {
    const mp = s?.mp ?? (s?.width && s?.height ? (s.width * s.height) / 1e6 : 0);
    return Math.max(m, mp || 0);
  }, 0);
}

/** Spec / focal-row advertised MP for a logical camera id (matches lens strip). */
export function advertisedMpForCamera(d, cameraId) {
  if (d == null || cameraId == null) return null;
  const entries = d.stillResolutionHonesty || d.resolutionBetrayal?.entries || [];
  const row = entries.find((e) => String(e.cameraId) === String(cameraId));
  if (row?.advertisedMegapixels != null) return row.advertisedMegapixels;
  const slot = (d.lensLineup || []).find((s) => String(s.cameraId) === String(cameraId));
  if (slot?.megapixels != null) return slot.megapixels;
  return null;
}

/** Highest rear advertised MP from focal row or GSMArena lens list. */
export function maxAdvertisedRearMp(d) {
  const fromLineup = (d.lensLineup || []).reduce((m, s) => Math.max(m, s.megapixels ?? 0), 0);
  if (fromLineup > 0) return fromLineup;
  const fromSpec = (d.sensors?.rearLenses || []).reduce((m, l) => Math.max(m, l.megapixels ?? 0), 0);
  return fromSpec > 0 ? fromSpec : null;
}

function isBetrayedEntry(d, r) {
  if (r.hasLargerThanDefault) return true;
  const defaultMp = defaultMpFromEntry(r);
  const maxHal = maxHalMpFromEntry(r);
  if (defaultMp > 0 && maxHal > 0 && maxHal / defaultMp >= MP_RATIO_THRESHOLD) return true;
  const spec = advertisedMpForCamera(d, r.cameraId);
  if (defaultMp > 0 && spec > 0 && spec / defaultMp >= MP_RATIO_THRESHOLD) return true;
  return false;
}

/** Client-side resolution betrayal index (0–100), mirrors [ResolutionBetrayal.kt]. */
export function computeBetrayalIndex(d) {
  const entries = d?.stillResolutionHonesty || d?.resolutionBetrayal?.entries || [];
  if (!entries.length) return null;
  const betrayed = entries.filter((r) => isBetrayedEntry(d, r)).length;
  return Math.round((betrayed * 100) / entries.length);
}

export function betrayalIndex(d) {
  const computed = computeBetrayalIndex(d);
  const stored = d?.resolutionBetrayal?.index;
  if (computed != null) return computed;
  if (stored != null && stored !== '') return Number(stored);
  return null;
}

/** Recompute betrayal from honesty rows so stale published JSON still renders correctly. */
export function normalizeDeviceProfile(d) {
  if (!d) return d;
  const idx = computeBetrayalIndex(d);
  if (idx != null) {
    d.resolutionBetrayal = { ...(d.resolutionBetrayal || {}), index: idx };
  }
  return d;
}

export function betrayalBadgeHtml(d) {
  const idx = betrayalIndex(d);
  if (idx == null) return '';
  let cls = 'badge-betrayal-none';
  if (idx >= 50) cls = 'badge-betrayal-high';
  else if (idx >= 25) cls = 'badge-betrayal-mid';
  else if (idx > 0) cls = 'badge-betrayal-low';
  return `<span class="badge ${cls}" title="Resolution withholding index — % of cameras where spec or alternate HAL maps exceed Camera2 default by ≥25%">Res betrayal ${idx}</span>`;
}

export function fullMpBreakthrough(d) {
  return d?.camera2FullMpBreakthrough ?? null;
}

export function breakthroughBadgeHtml(d) {
  const b = fullMpBreakthrough(d);
  if (!b?.proven) return '';
  const mp = b.maxMpPerSensor ?? '?';
  const tier = b.evidenceTier ? ` (${b.evidenceTier})` : '';
  return `<span class="badge badge-breakthrough" title="Third-party Camera2 verified full sensor resolution (>12 MP) — rare aftermarket access${tier}">Camera2 ${mp} MP ✓</span>`;
}

export function breakthroughHeroHtml(d) {
  const b = fullMpBreakthrough(d);
  if (!b?.proven) return '';
  const mp = b.maxMpPerSensor ?? '?';
  const count = b.cameraCount ?? 0;
  const verified = (d.meta?.lastSweepUtc || '').slice(0, 10) || 'recent sweep';
  return `
    <section class="breakthrough-hero" role="status">
      <h2>Camera2 full resolution breakthrough</h2>
      <p>Most OEMs bin third-party Camera2 apps to ~12 MP even on 48–64 MP hardware. This device exposed up to <strong>${mp} MP</strong> on ${count} rear sensor(s) through the standard Camera2 API — verified ${verified}.</p>
    </section>`;
}

export function renderSensorSvg(device) {
  const lenses = device?.sensors?.rearLenses?.length
    ? device.sensors.rearLenses.filter((l) => l.role !== 'selfie')
    : (device?.sensors?.sensors || []).filter((s) => s.role !== 'FRONT');
  if (!lenses.length) return '';
  const barW = 32;
  const gap = 16;
  const pad = 8;
  const chartW = pad * 2 + lenses.length * (barW + gap) - gap;
  const chartH = 48;
  const max = Math.max(...lenses.map((l) => l.areaMm2 || 0), 1);
  const bars = lenses.map((l, i) => {
    const h = Math.max(4, ((l.areaMm2 || 0) / max) * 32);
    const x = pad + i * (barW + gap);
    const y = chartH - 14 - h;
    const mp = l.megapixels ?? (l.cameraId != null ? advertisedMpForCamera(device, l.cameraId) : null);
    const roleLabel = l.role || l.cameraId || i;
    const mpLabel = mp ? `<text x="${x + barW / 2}" y="10" text-anchor="middle" font-size="8" fill="var(--text)">${Math.round(mp)}MP</text>` : '';
    return `<rect x="${x}" y="${y}" width="${barW}" height="${h}" fill="var(--accent)" rx="2"/>${mpLabel}<text x="${x + barW / 2}" y="${chartH - 4}" text-anchor="middle" font-size="9" fill="var(--muted)">${roleLabel}</text>`;
  }).join('');
  return `<svg class="sensor-chart" viewBox="0 0 ${chartW} ${chartH}" preserveAspectRatio="xMidYMid meet" role="img" aria-label="Rear sensor area chart">${bars}</svg>`;
}

export function cellChip(advertised, proven, gap) {
  if (proven) return '<span class="chip-proven">● proven</span>';
  if (advertised && gap?.includes('DELIVERY')) return '<span class="chip-withheld">● delivery mismatch</span>';
  if (advertised) return '<span class="chip-advertised">● advertised only</span>';
  return '<span class="chip-na">○ n/a</span>';
}

export { WISHLIST_ITEMS };
