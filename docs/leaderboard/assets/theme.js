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

export function progressBar(pct) {
  const p = Math.min(100, Math.max(0, Number(pct) || 0));
  return `<div class="progress" title="${p}%"><span style="width:${p}%"></span></div>`;
}

export function cellChip(advertised, proven, gap) {
  if (proven) return '<span class="chip-proven">● proven</span>';
  if (advertised && gap?.includes('DELIVERY')) return '<span class="chip-withheld">● delivery mismatch</span>';
  if (advertised) return '<span class="chip-advertised">● advertised only</span>';
  return '<span class="chip-na">○ n/a</span>';
}

export { WISHLIST_ITEMS };
