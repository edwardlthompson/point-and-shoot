import {
  trustBadge,
  fmtNum,
  progressBar,
  cellChip,
  apiLevelBadge,
  sensorSumLabel,
  sensorSumStatHtml,
  sensorSourceNote,
  gsmarenaLinkHtml,
  betrayalIndex,
  betrayalBadgeHtml,
  breakthroughBadgeHtml,
  fullMpBreakthrough,
} from './theme.js?v=20260606h';

const PERSONA_SORT = {
  default: (a, b) => (b.scores?.total?.score ?? 0) - (a.scores?.total?.score ?? 0),
  most_capable: (a, b) => (b.scores?.total?.score ?? 0) - (a.scores?.total?.score ?? 0),
  best_value: (a, b) => (b.value?.parityPerUsd ?? 0) - (a.value?.parityPerUsd ?? 0),
  pro_photo: (a, b) => {
    const aRaw = a.rawSummary?.dngProven ? 1 : 0;
    const bRaw = b.rawSummary?.dngProven ? 1 : 0;
    if (bRaw !== aRaw) return bRaw - aRaw;
    const aBet = betrayalIndex(a) ?? 100;
    const bBet = betrayalIndex(b) ?? 100;
    if (aBet !== bBet) return aBet - bBet;
    return (b.sensors?.sensorSumMm2 ?? 0) - (a.sensors?.sensorSumMm2 ?? 0);
  },
  custom_rom: (a, b) => (b.disparity?.honestyPercent ?? 0) - (a.disparity?.honestyPercent ?? 0),
  photographer: (a, b) => (b.sensors?.sensorSumMm2 ?? 0) - (a.sensors?.sensorSumMm2 ?? 0),
  videographer: (a, b) => (b.videoSummary?.hfrMaxFps1080 ?? 0) - (a.videoSummary?.hfrMaxFps1080 ?? 0),
  tinkerer: (a, b) => (b.disparity?.honestyPercent ?? 0) - (a.disparity?.honestyPercent ?? 0),
};

export function personaSort(devices, persona) {
  const fn = PERSONA_SORT[persona] || PERSONA_SORT.default;
  return [...devices].sort(fn);
}

export function filterDevices(devices, { search, oem, trust, wishlist, minAntutu, minSensor, stockOnly, hideShipBlockers }) {
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
    if (trust === 'custom_lane' && !['custom_likely', 'root_unlocked', 'engineering'].includes(d.software?.romFlavor)) return false;
    if (stockOnly && d.software?.romFlavor !== 'stock') return false;
    if (hideShipBlockers && (d.oemLossSummary?.shipBlockerCount ?? 0) > 0) return false;
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
  const frontHal = (d.sensors?.sensors || []).find((s) => s.role === 'FRONT');
  const frontGsm = (d.sensors?.frontLenses || [])[0];
  const frontMp = frontGsm?.megapixels ?? frontHal?.megapixels;
  const rearHtml = slots.map((s) =>
    `<div class="lens-slot"><strong>${s.focalMm35 ?? '?'}mm</strong>${fmtNum(Math.round((s.megapixels ?? 0) * 10) / 10)} MP</div>`
  ).join('');
  const frontHtml = frontMp
    ? `<div class="lens-slot lens-slot-front"><strong>Selfie</strong>${fmtNum(Math.round(frontMp * 10) / 10)} MP</div>`
    : '';
  if (!rearHtml && !frontHtml) return '';
  return `<div class="lens-strip">${rearHtml}${frontHtml}</div>`;
}

function withheldPills(d) {
  const w = d.withheldFeatures || [];
  if (!w.length) return '';
  const ship = w.filter((x) => x.consumerImpact === 'SHIP_BLOCKER');
  const rest = w.filter((x) => x.consumerImpact !== 'SHIP_BLOCKER');
  const ordered = [...ship, ...rest].slice(0, 5);
  return `<div class="pills">${ordered.map((x) => {
    const cls = x.consumerImpact === 'SHIP_BLOCKER' ? 'pill pill-ship-blocker' : x.consumerImpact === 'ENGINEERING_ONLY' ? 'pill pill-engineering' : 'pill pill-info';
    return `<span class="${cls}">${x.displayName || x.catalogId}</span>`;
  }).join('')}</div>`;
}

export function renderDeviceCard(d, { selected }) {
  const mode = d.meta?.lastSweepMode || '';
  const tier = d.meta?.trustTier || 'community_preview';
  const freshness = mode.toLowerCase() === 'full'
    ? '<span class="badge badge-full">Full sweep</span>'
    : '<span class="badge badge-quick">Quick sweep</span>';
  const parityPerUsd = d.value?.parityPerUsd;
  const gsmLink = gsmarenaLinkHtml(d);
  const shipCount = d.oemLossSummary?.shipBlockerCount ?? 0;
  return `
    <article class="device-card${fullMpBreakthrough(d)?.proven ? ' row-breakthrough' : ''}" data-slug="${d.slug}">
      <h2><a href="#/device/${d.slug}">${d.identity?.marketingName || d.identity?.model}</a></h2>
      <p class="sub">${d.identity?.manufacturer} ${d.identity?.model} · tested ${(d.meta?.lastSweepUtc || '').slice(0, 10)}${formatApiSubline(d)}${gsmLink ? ` · ${gsmLink}` : ''}</p>
      <div class="badges">
        ${trustBadge(tier, d.software?.romFlavor)}
        ${apiLevelBadge(d)}
        ${freshness}
        ${betrayalBadgeHtml(d)}
        ${breakthroughBadgeHtml(d)}
        ${shipCount ? `<span class="badge badge-ship-blocker">${shipCount} ship-blocker${shipCount > 1 ? 's' : ''}</span>` : ''}
        ${d.software?.romFlavor === 'stock' ? '<span class="badge badge-stock">Stock ROM</span>' : ''}
      </div>
      <div class="stat-row">
        <div class="stat"><strong>#${d.scores?.rank ?? '—'}</strong> Rank</div>
        <div class="stat"><strong>${d.scores?.total?.score ?? '—'}</strong> Parity pts <small class="muted">(${d.scores?.total?.percent ?? '—'}%)</small> ${progressBar(d.scores?.total?.percent)}</div>
        <div class="stat"><strong>${d.disparity?.honestyPercent ?? '—'}%</strong> Honesty ${progressBar(d.disparity?.honestyPercent)}</div>
        <div class="stat"><strong>${fmtNum(d.antutu?.total)}</strong> AnTuTu</div>
        <div class="stat">${sensorSumStatHtml(d)}${sensorSourceNote(d) ? `<br><small class="muted">${sensorSourceNote(d)}</small>` : ''}</div>
        ${parityPerUsd ? `<div class="stat"><strong>${parityPerUsd}</strong> Parity/$</div>` : ''}
      </div>
      <p class="video-line" style="font-size:0.8rem;color:var(--muted)">${videoOneLiner(d)}</p>
      ${lensStripHtml(d)}
      ${withheldPills(d)}
      <div style="margin-top:0.75rem;display:flex;gap:0.5rem;flex-wrap:wrap">
        <a class="btn btn-primary" href="#/device/${d.slug}">Details</a>
        ${d.identity?.productGroupId ? `<a class="btn" href="#/product/${d.identity.productGroupId}">Product compare</a>` : ''}
        <label><input type="checkbox" data-compare="${d.slug}" ${selected ? 'checked' : ''}> Compare</label>
      </div>
    </article>`;
}

export function renderLeaderboardTable(devices, sortKey, sortDir, selectedSlugs) {
  const cols = [
    { key: 'rank', label: '#', get: (d) => d.scores?.rank },
    { key: 'name', label: 'Device', get: (d) => d.identity?.marketingName },
    { key: 'parity', label: 'Parity pts', get: (d) => d.scores?.total?.score ?? 0 },
    { key: 'value', label: 'Parity/$', get: (d) => d.value?.parityPerUsd ?? 0 },
    { key: 'honesty', label: 'Honesty %', get: (d) => d.disparity?.honestyPercent ?? 0 },
    { key: 'betrayal', label: 'Res betrayal', get: (d) => betrayalIndex(d) ?? -1 },
    { key: 'fullmp', label: 'Full MP', get: (d) => fullMpBreakthrough(d)?.maxMpPerSensor ?? 0 },
    { key: 'sensor', label: 'Sensor mm²', get: (d) => d.sensors?.sensorSumMm2 ?? 0 },
    { key: 'video', label: 'HFR@1080', get: (d) => d.videoSummary?.hfrMaxFps1080 ?? 0 },
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
        ${trustBadge(d.meta?.trustTier, d.software?.romFlavor)}
        ${gsmarenaLinkHtml(d) ? `<br><small>${gsmarenaLinkHtml(d)}</small>` : ''}</td>
      <td>${d.scores?.total?.score ?? '—'} <small class="muted">(${d.scores?.total?.percent ?? '—'}%)</small></td>
      <td>${d.value?.parityPerUsd ?? '—'}</td>
      <td>${d.disparity?.honestyPercent ?? '—'}%</td>
      <td class="col-betrayal">${betrayalIndex(d) ?? '—'}</td>
      <td class="col-fullmp">${fullMpBreakthrough(d)?.proven ? `${fullMpBreakthrough(d).maxMpPerSensor} ✓` : '—'}</td>
      <td>${fmtNum(d.sensors?.sensorSumMm2)}</td>
      <td>${d.videoSummary?.hfrMaxFps1080 ?? '—'}</td>
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
  })).join('');
  const table = renderLeaderboardTable(sorted, state.sortKey, state.sortDir, state.compareSlugs);
  const compareBar = state.compareSlugs.size
    ? `<p><a class="btn btn-primary" href="#/compare?devices=${[...state.compareSlugs].join(',')}">Compare ${state.compareSlugs.size} devices</a></p>`
    : '';
  const loadWarning = devices.length === 0
    ? '<div class="cta-box"><p><strong>No device profiles loaded.</strong> Check the browser console for JSON fetch errors, then hard-refresh.</p></div>'
    : (sorted.length === 0
      ? '<div class="cta-box"><p>No devices match the current filters.</p></div>'
      : '');
  const cta = devices.length > 0 && devices.length < 5
    ? `<div class="cta-box"><h3>Help grow the fleet</h3><p>Only ${devices.length} device(s) tested. Run Point & Shoot on your phone and contribute via Engineering Hub → Parity Sweep → Submit.</p>
       <a class="btn" href="https://github.com/edwardlthompson/point-and-shoot">Get the app</a></div>`
    : '';
  return `
    <p class="disclosure-banner"><strong>Camera2 only:</strong> Rankings reflect tested third-party Camera2 capability — not the OEM camera app. <a href="#/oem">OEM accountability index</a></p>
    <p class="hero-sub">Ranked by tested Camera2 capability (parity points from Full sweeps).</p>
    <div class="tabs">
      <button type="button" class="active" data-tab="leaderboard">Leaderboard</button>
      <button type="button" data-tab="oem" onclick="location.hash='#/oem'">OEM accountability</button>
    </div>
    ${compareBar}
    ${loadWarning}
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
