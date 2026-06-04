import { trustBadge, fmtNum, progressBar, cellChip, apiLevelBadge } from './theme.js';

const PERSONA_SORT = {
  default: (a, b) => (b.scores?.total?.percent ?? 0) - (a.scores?.total?.percent ?? 0),
  photographer: (a, b) => (b.sensors?.sensorSumMm2 ?? 0) - (a.sensors?.sensorSumMm2 ?? 0),
  videographer: (a, b) => (b.videoSummary?.hfrMaxFps1080 ?? 0) - (a.videoSummary?.hfrMaxFps1080 ?? 0),
  tinkerer: (a, b) => (b.disparity?.honestyPercent ?? 0) - (a.disparity?.honestyPercent ?? 0),
};

export function personaSort(devices, persona) {
  const fn = PERSONA_SORT[persona] || PERSONA_SORT.default;
  return [...devices].sort(fn);
}

export function filterDevices(devices, { search, oem, trust, wishlist, minAntutu, minSensor, stockOnly }) {
  return devices.filter((d) => {
    if (search) {
      const q = search.toLowerCase();
      const hay = `${d.identity?.marketingName} ${d.identity?.model} ${d.identity?.manufacturer}`.toLowerCase();
      if (!hay.includes(q)) return false;
    }
    if (oem && d.identity?.manufacturer !== oem) return false;
    if (trust === 'maintainer' && d.meta?.trustTier !== 'maintainer') return false;
    if (trust === 'community' && d.meta?.trustTier === 'maintainer') return false;
    if (trust === 'exclude_root' && (d.software?.rootGranted || d.software?.romFlavor === 'custom_likely')) return false;
    if (stockOnly && d.software?.romFlavor !== 'stock') return false;
    if (minAntutu && (d.antutu?.total ?? 0) < minAntutu) return false;
    if (minSensor && (d.sensors?.sensorSumMm2 ?? 0) < minSensor) return false;
    if (wishlist?.length) {
      for (const id of wishlist) {
        let found = false;
        for (const cat of Object.values(d.cellsByCategory || {})) {
          for (const c of cat) {
            if (c.catalogId === id && c.provenOk) { found = true; break; }
          }
          if (found) break;
        }
        if (!found) return false;
      }
    }
    return true;
  });
}

function videoOneLiner(d) {
  const v = d.videoSummary;
  if (!v) return '';
  const parts = [];
  if (v.video4k120Class) parts.push(`4K120: ${v.video4k120Class}`);
  if (v.hfrMaxFps1080) parts.push(`HFR max ${v.hfrMaxFps1080}@1080`);
  const c = v.codecs || {};
  parts.push(`AV1: ${c.av1 ? 'yes' : 'no'}`);
  return parts.join(' · ');
}

function lensStripHtml(d) {
  const slots = d.lensLineup || [];
  if (!slots.length) return '';
  return `<div class="lens-strip">${slots.map((s) =>
    `<div class="lens-slot"><strong>${s.focalMm35 ?? '?'}mm</strong>${fmtNum(Math.round((s.megapixels ?? 0) * 10) / 10)} MP</div>`
  ).join('')}</div>`;
}

function withheldPills(d) {
  const w = (d.withheldFeatures || []).slice(0, 3);
  if (!w.length) return '';
  return `<div class="pills">${w.map((x) => `<span class="pill">${x.displayName || x.catalogId}</span>`).join('')}</div>`;
}

export function renderDeviceCard(d, { selected, onToggleCompare, glossary }) {
  const mode = d.meta?.lastSweepMode || '';
  const tier = d.meta?.trustTier || 'community_preview';
  const freshness = mode.toLowerCase() === 'full'
    ? '<span class="badge badge-full">Full sweep</span>'
    : '<span class="badge badge-quick">Quick sweep</span>';
  const valueScore = d.identity?.msrpUsd && d.antutu?.total
    ? Math.round(d.antutu.total / d.identity.msrpUsd)
    : null;
  return `
    <article class="device-card" data-slug="${d.slug}">
      <h2><a href="#/device/${d.slug}">${d.identity?.marketingName || d.identity?.model}</a></h2>
      <p class="sub">${d.identity?.manufacturer} ${d.identity?.model} · tested ${(d.meta?.lastSweepUtc || '').slice(0, 10)}${formatApiSubline(d)}</p>
      <div class="badges">
        ${trustBadge(tier, d.software?.romFlavor)}
        ${apiLevelBadge(d)}
        ${freshness}
        ${d.software?.romFlavor === 'stock' ? '<span class="badge badge-stock">Stock ROM</span>' : ''}
      </div>
      <div class="stat-row">
        <div class="stat"><strong>${d.scores?.total?.percent ?? '—'}%</strong> Parity ${progressBar(d.scores?.total?.percent)}</div>
        <div class="stat"><strong>${d.disparity?.honestyPercent ?? '—'}%</strong> Honesty ${progressBar(d.disparity?.honestyPercent)}</div>
        <div class="stat"><strong>${fmtNum(d.antutu?.total)}</strong> AnTuTu</div>
        <div class="stat"><strong>${fmtNum(d.sensors?.sensorSumMm2)}</strong> mm² sensors</div>
        ${valueScore ? `<div class="stat"><strong>${valueScore}</strong> Value (AnTuTu/$)</div>` : ''}
      </div>
      <p class="video-line" style="font-size:0.8rem;color:var(--muted)">${videoOneLiner(d)}</p>
      ${lensStripHtml(d)}
      ${withheldPills(d)}
      <div style="margin-top:0.75rem;display:flex;gap:0.5rem;flex-wrap:wrap">
        <a class="btn btn-primary" href="#/device/${d.slug}">Details</a>
        <label><input type="checkbox" data-compare="${d.slug}" ${selected ? 'checked' : ''}> Compare</label>
      </div>
    </article>`;
}

export function renderLeaderboardTable(devices, sortKey, sortDir, selectedSlugs) {
  const cols = [
    { key: 'rank', label: '#', get: (d) => d.scores?.rank },
    { key: 'name', label: 'Device', get: (d) => d.identity?.marketingName },
    { key: 'antutu', label: 'AnTuTu', get: (d) => d.antutu?.total ?? 0 },
    { key: 'sensor', label: 'Sensor mm²', get: (d) => d.sensors?.sensorSumMm2 ?? 0 },
    { key: 'parity', label: 'Parity %', get: (d) => d.scores?.total?.percent ?? 0 },
    { key: 'honesty', label: 'Honesty %', get: (d) => d.disparity?.honestyPercent ?? 0 },
    { key: 'api', label: 'Tested API', get: (d) => d.software?.sdkInt ?? 0 },
    { key: 'oem', label: 'OEM', get: (d) => d.identity?.manufacturer },
  ];
  let sorted = [...devices];
  if (sortKey) {
    const col = cols.find((c) => c.key === sortKey);
    if (col) {
      sorted.sort((a, b) => {
        const va = col.get(a);
        const vb = col.get(b);
        return sortDir === 'asc' ? (va > vb ? 1 : -1) : (va < vb ? 1 : -1);
      });
    }
  }
  const header = cols.map((c) =>
    `<th data-sort="${c.key}">${c.label}${sortKey === c.key ? (sortDir === 'asc' ? ' ↑' : ' ↓') : ''}</th>`
  ).join('') + '<th>Compare</th>';
  const rows = sorted.map((d) => `
    <tr data-slug="${d.slug}">
      <td>${d.scores?.rank ?? '—'}</td>
      <td><a href="#/device/${d.slug}">${d.identity?.marketingName}</a>
        ${trustBadge(d.meta?.trustTier, d.software?.romFlavor)}</td>
      <td>${fmtNum(d.antutu?.total)}</td>
      <td>${fmtNum(d.sensors?.sensorSumMm2)}</td>
      <td>${d.scores?.total?.percent ?? '—'}%</td>
      <td>${d.disparity?.honestyPercent ?? '—'}%</td>
      <td>${d.software?.apiLevelLabel || (d.software?.sdkInt ? `API ${d.software.sdkInt}` : '—')}</td>
      <td>${d.identity?.manufacturer}</td>
      <td><input type="checkbox" data-compare="${d.slug}" ${selectedSlugs.has(d.slug) ? 'checked' : ''}></td>
    </tr>`).join('');
  return `<div class="data-table-wrap desktop-table"><table class="data-table"><thead><tr>${header}</tr></thead><tbody>${rows}</tbody></table></div>`;
}

export function renderHome(devices, state) {
  const filtered = filterDevices(devices, state);
  const sorted = personaSort(filtered, state.persona);
  const cards = sorted.map((d) => renderDeviceCard(d, {
    selected: state.compareSlugs.has(d.slug),
    onToggleCompare: null,
  })).join('');
  const table = renderLeaderboardTable(sorted, state.sortKey, state.sortDir, state.compareSlugs);
  const compareBar = state.compareSlugs.size
    ? `<p><a class="btn btn-primary" href="#/compare?devices=${[...state.compareSlugs].join(',')}">Compare ${state.compareSlugs.size} devices</a></p>`
    : '';
  const cta = devices.length < 5
    ? `<div class="cta-box"><h3>Help grow the fleet</h3><p>Only ${devices.length} device(s) tested. Run Point & Shoot on your phone and contribute via Engineering Hub → Parity Sweep → Submit.</p>
       <a class="btn" href="https://github.com/edwardlthompson/point-and-shoot">Get the app</a></div>`
    : '';
  return `
    <div class="tabs">
      <button type="button" class="active" data-tab="leaderboard">Leaderboard</button>
      <button type="button" data-tab="oem" onclick="location.hash='#/oem'">OEM Index</button>
    </div>
    ${compareBar}
    ${table}
    <div class="device-grid cards-only mobile-cards-only">${cards}</div>
    ${cta}`;
}

export { videoOneLiner, lensStripHtml, withheldPills, cellChip };

function formatApiSubline(d) {
  const label = d.software?.apiLevelLabel || d.meta?.testedApiLevel;
  if (!label) return '';
  return ` · ${label}`;
}
