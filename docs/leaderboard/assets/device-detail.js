import {
  trustBadge,
  fmtNum,
  progressBar,
  cellChip,
  formatApiLevel,
  apiLevelBadge,
  sensorSumLabel,
  sensorSumStatHtml,
  sensorSourceNote,
  renderRearLensTable,
  renderSensorSvg,
  gsmarenaLinkHtml,
  specLinksHtml,
  glossaryLabel,
  maxHalMpFromEntry,
  defaultMpFromEntry,
  advertisedMpForCamera,
  betrayalIndex,
  breakthroughHeroHtml,
  breakthroughBadgeHtml,
  antutuStatHtml,
} from './theme.js?v=20260606i';
import { lensStripHtml, withheldPills, videoOneLiner } from './leaderboard.js?v=20260606h';
import { drawSparkline } from './charts.js?v=20260606h';
import { shareDeviceUrl } from './router.js?v=20260606h';

function resolutionNotes(d, r) {
  const halMax = maxHalMpFromEntry(r);
  const parts = [];
  if (r.hasLargerThanDefault) {
    parts.push('hidden high-res on alt map');
  } else if (halMax > 0) {
    parts.push(`Camera2 max map ${fmtNum(Math.round(halMax * 10) / 10)} MP`);
  } else {
    parts.push('default path only');
  }
  const advertised = advertisedMpForCamera(d, r.cameraId);
  if (advertised != null && halMax > 0 && advertised > halMax * 1.05) {
    parts.push('spec MP above Camera2 default');
  }
  return parts.join(' · ');
}

function historyTrendPoints(history) {
  if (!history?.length) return [];
  const seen = new Set();
  const points = [];
  for (const p of history) {
    const key = `${p.totalPercent ?? ''}|${p.honestyPercent ?? ''}|${p.totalScore ?? ''}`;
    if (seen.has(key)) continue;
    seen.add(key);
    points.push(p);
  }
  return points.length >= 2 ? points : [];
}

function formatTrendDate(ts) {
  if (!ts) return '?';
  const s = String(ts);
  if (/^\d{4}-\d{2}-\d{2}/.test(s)) return s.slice(0, 10);
  const m = s.match(/(\d{2})\/(\d{2})\/(\d{4})/);
  if (m) return `${m[3]}-${m[1]}-${m[2]}`;
  return s.slice(0, 10);
}

function renderScoreTrend(history, glossary) {
  const points = historyTrendPoints(history);
  if (!points.length) return '';
  const percents = points.map((p) => p.totalPercent ?? p.honestyPercent ?? 0);
  const first = Math.round(percents[0] * 10) / 10;
  const last = Math.round(percents[percents.length - 1] * 10) / 10;
  const sweepLines = points.map((p, i) => {
    const pct = Math.round((p.totalPercent ?? p.honestyPercent ?? 0) * 10) / 10;
    return `${formatTrendDate(p.timestampUtc)}: ${pct}%`;
  }).join('\n');
  const chartTitle = `${sweepLines}\n(left = oldest Full sweep · right = newest)`;
  return `
    <div class="score-trend">
      <p class="score-trend-head">
        ${glossaryLabel('Parity trend', glossary, 'parity_trend')}:
        <span class="score-trend-chart" title="${chartTitle.replace(/"/g, '&quot;')}">
          <svg class="sparkline" width="160" height="36" aria-label="Parity score trend across Full sweeps" role="img"></svg>
        </span>
        <span class="score-trend-range">${first}% → ${last}%</span>
        <small class="muted">(${points.length} sweeps)</small>
      </p>
      <p class="score-trend-hint"><small>Total Camera2 parity % over time — hover the chart for dates. Not the OEM camera app.</small></p>
    </div>`;
}

function renderResolutionBetrayalPanel(d, glossary) {
  const entries = d.stillResolutionHonesty || d.resolutionBetrayal?.entries || [];
  if (!entries.length && d.resolutionBetrayal?.index == null) return '';
  const idx = d.resolutionBetrayal?.index ?? '—';
  const rows = entries.map((r) => {
    const def = fmtNum(defaultMpFromEntry(r));
    const advertised = advertisedMpForCamera(d, r.cameraId);
    const max = fmtNum(advertised != null ? Math.round(advertised * 10) / 10 : null);
    return `<tr><td>${r.cameraId}</td><td>${def} MP</td><td>${max !== '—' ? `${max} MP` : '—'}</td><td>${resolutionNotes(d, r)}</td></tr>`;
  }).join('');
  return `
    <section class="device-card resolution-panel">
      <h3>${glossaryLabel('Resolution withholding', glossary, 'resolution_betrayal')}</h3>
      <p class="software-line">Betrayal index: <strong>${idx}</strong> (% of cameras where spec/focal-row MP or alternate HAL maps exceed Camera2 default by ≥25%)</p>
      <p>Counts hidden high-res stream maps and spec-sheet megapixel claims above the default Camera2 still path.</p>
      <table class="data-table"><thead><tr><th>Camera</th><th>Default MP</th><th>Max advertised MP</th><th>Notes</th></tr></thead><tbody>${rows || '<tr><td colspan="4">No data</td></tr>'}</tbody></table>
    </section>`;
}

function renderVideoPanel(d) {
  const v = d.videoSummary;
  if (!v) return '';
  const c = v.codecs || {};
  return `
    <section class="device-card api-panel api-panel-camera2">
      <h3>Video (Camera2 tested)</h3>
      <p>4K120 class: <strong>${v.video4k120Class || '—'}</strong> · HFR max @1080: <strong>${v.hfrMaxFps1080 || '—'}</strong></p>
      <p>Codecs: H.264 yes · HEVC ${c.hevc ? 'yes' : 'no'} · AV1 ${c.av1 ? 'yes' : 'no'} · HEVC 10-bit ${c.hevc10 ? 'yes' : 'no'}</p>
    </section>`;
}

function renderCameraXPanel(d) {
  const cx = d.cameraXSummary;
  if (!cx) return '';
  const exts = (cx.extensions || []).join(', ') || 'none detected';
  const honesty = cx.honestyScore != null ? ` · honesty ${cx.honestyScore}%` : '';
  return `
    <section class="device-card api-panel api-panel-camerax hidden">
      <h3>CameraX extensions (informational)</h3>
      <p>Probe complete: ${cx.probeComplete ? 'yes' : 'no'} · Extensions: ${exts}${honesty}</p>
      <p><small>Camera2 remains the primary ranked path; CameraX data is supplementary.</small></p>
    </section>`;
}

function apiToggleBar() {
  return `
    <div class="api-toggle" role="tablist" aria-label="Measurement API">
      <button type="button" class="api-toggle-btn active" data-api="camera2">Camera2 (ranked)</button>
      <button type="button" class="api-toggle-btn" data-api="camerax">CameraX (info)</button>
    </div>`;
}

export function renderDeviceDetail(d, history, glossary) {
  const specLinks = specLinksHtml(d);
  const gsmLink = gsmarenaLinkHtml(d);
  const categories = Object.entries(d.cellsByCategory || {}).map(([cat, cells]) => `
    <details class="accordion-item">
      <summary>${cat} (${cells.length})</summary>
      <div class="body"><ul>${cells.map((c) =>
        `<li>${cellChip(c.advertised, c.provenOk, c.gap)} <strong>${c.displayName || c.catalogId}</strong> ${c.failReason ? `<em>(${c.failReason})</em>` : ''}</li>`
      ).join('')}</ul></div>
    </details>`).join('');

  const rawPanel = `
    <section class="device-card">
      <h3>RAW / Pro photo</h3>
      <p>DNG proven: <strong>${d.rawSummary?.dngProven ? 'Yes' : 'No'}</strong></p>
      <p>RAW formats: ${(d.rawSummary?.rawFormats || []).join(', ') || '—'}</p>
      <p>Max RAW MP: ${fmtNum(d.rawSummary?.maxRawMp)}</p>
    </section>`;

  return `
    <p><a href="#/">&larr; Back</a>${d.identity?.productGroupId ? ` · <a href="#/product/${d.identity.productGroupId}">Product compare</a>` : ''}</p>
    <p class="disclosure-banner"><strong>Camera2 measurement:</strong> Scores reflect third-party Camera2 access — not the OEM camera app.</p>
    ${breakthroughHeroHtml(d)}
    <div class="device-card">
      <h1>${d.identity?.marketingName || d.identity?.displayLabel}</h1>
      <p class="sub">${d.identity?.displayLabel || `${d.identity?.manufacturer} ${d.identity?.model}`}${gsmLink ? ` · ${gsmLink}` : ''}</p>
      <div class="badges">${trustBadge(d.meta?.trustTier, d.software?.romFlavor)} ${apiLevelBadge(d)} ${breakthroughBadgeHtml(d)}</div>
      <p class="software-line"><strong>Tested on:</strong> ${formatApiLevel(d) || '—'}${d.software?.buildDisplay ? ` · ${d.software.buildDisplay}` : ''}${d.software?.securityPatch ? ` · patch ${d.software.securityPatch}` : ''}</p>
      <p style="font-size:0.85rem">${videoOneLiner(d)}</p>
      ${lensStripHtml(d)}
      ${withheldPills(d)}
      <div class="stat-row">
        <div class="stat"><strong>#${d.scores?.rank ?? '—'}</strong> Rank</div>
        <div class="stat"><strong>${d.scores?.total?.score ?? '—'}</strong> ${glossaryLabel('Parity pts', glossary, 'parity_pts')} <small class="muted">(${d.scores?.total?.percent ?? '—'}%)</small> ${progressBar(d.scores?.total?.percent)}</div>
        <div class="stat"><strong>${d.disparity?.honestyPercent}%</strong> ${glossaryLabel('Honesty', glossary, 'honesty')} ${progressBar(d.disparity?.honestyPercent)}</div>
        <div class="stat">${antutuStatHtml(d, glossary)}</div>
        <div class="stat">${sensorSumStatHtml(d)}${sensorSourceNote(d) ? `<br><small>${sensorSourceNote(d)}</small>` : ''}</div>
        ${d.value?.parityPerUsd ? `<div class="stat"><strong>${d.value.parityPerUsd}</strong> Parity/$</div>` : ''}
      </div>
      ${renderScoreTrend(history, glossary)}
      <p>Format picker honesty: ${d.formatPickerHonestyScore ?? '—'}%</p>
      ${specLinks ? `<p class="spec-links">${specLinks}</p>` : ''}
      <p><small>Share: <input readonly value="${shareDeviceUrl(d.slug)}" style="width:100%;max-width:400px"></small></p>
    </div>
    ${apiToggleBar()}
    <div class="api-panel api-panel-camera2">
    ${renderResolutionBetrayalPanel(d, glossary)}
    ${renderVideoPanel(d)}
    </div>
    ${renderCameraXPanel(d)}
    <section class="device-card">
      <h3>Camera sensors</h3>
      ${renderSensorSvg(d)}
      <p class="software-line">Combined rear area (front/selfie excluded): <strong>${sensorSumLabel(d)} mm²</strong>${sensorSourceNote(d) ? ` · ${sensorSourceNote(d)}` : ''}</p>
      ${renderRearLensTable(d)}
    </section>
    ${rawPanel}
    <section class="accordion">${categories}</section>
    <p><a href="https://github.com/edwardlthompson/point-and-shoot/issues/new?template=leaderboard_device_request.md">Request a device</a> ·
       <a href="https://github.com/edwardlthompson/point-and-shoot/issues/new?template=leaderboard_dispute.md">Report incorrect entry</a></p>`;
}

export function attachSparkline(container, history) {
  const svg = container.querySelector('.sparkline');
  const points = historyTrendPoints(history);
  if (svg && points.length) drawSparkline(svg, points);
}

export function attachDeviceDetailApiToggle(container) {
  const buttons = container.querySelectorAll('.api-toggle-btn');
  if (!buttons.length) return;
  const show = (api) => {
    container.querySelectorAll('.api-panel-camera2').forEach((el) => {
      el.classList.toggle('hidden', api !== 'camera2');
    });
    container.querySelectorAll('.api-panel-camerax').forEach((el) => {
      el.classList.toggle('hidden', api !== 'camerax');
    });
    buttons.forEach((b) => b.classList.toggle('active', b.dataset.api === api));
  };
  buttons.forEach((btn) => {
    btn.addEventListener('click', () => show(btn.dataset.api || 'camera2'));
  });
  show('camera2');
}
