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

export function renderSensorSvg(device) {
  const lenses = device?.sensors?.rearLenses?.length
    ? device.sensors.rearLenses.filter((l) => l.role !== 'selfie')
    : (device?.sensors?.sensors || []).filter((s) => s.role !== 'FRONT');
  if (!lenses.length) return '';
  const max = Math.max(...lenses.map((l) => l.areaMm2 || 0), 1);
  const bars = lenses.map((l, i) => {
    const h = Math.max(4, ((l.areaMm2 || 0) / max) * 36);
    const x = 8 + i * 48;
    const y = 40 - h;
    return `<rect x="${x}" y="${y}" width="32" height="${h}" fill="var(--accent)" rx="2"/><text x="${x + 16}" y="38" text-anchor="middle" font-size="8" fill="var(--muted)">${l.role || l.cameraId || i}</text>`;
  }).join('');
  return `<svg class="sensor-chart" viewBox="0 0 200 40" width="200" height="40">${bars}</svg>`;
}

export function cellChip(advertised, proven, gap) {
  if (proven) return '<span class="chip-proven">● proven</span>';
  if (advertised && gap?.includes('DELIVERY')) return '<span class="chip-withheld">● delivery mismatch</span>';
  if (advertised) return '<span class="chip-advertised">● advertised only</span>';
  return '<span class="chip-na">○ n/a</span>';
}

export { WISHLIST_ITEMS };
