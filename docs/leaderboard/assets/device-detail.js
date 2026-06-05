import { trustBadge, fmtNum, progressBar, cellChip, formatApiLevel, apiLevelBadge, sensorSumLabel, sensorSourceNote, renderRearLensTable, renderSensorSvg, gsmarenaLinkHtml, specLinksHtml, glossaryLabel } from './theme.js';
import { lensStripHtml, withheldPills, videoOneLiner } from './leaderboard.js';
import { drawSparkline } from './charts.js';
import { shareDeviceUrl } from './router.js';

function maxMpFromEntry(r) {
  const sizes = [r.defaultJpeg, r.highResJpeg, r.maxResMapJpeg, r.multiResJpeg, r.defaultRawSensor, r.highResRawSensor, r.maxResMapRawSensor];
  return sizes.reduce((m, s) => Math.max(m, s?.mp ?? s?.width * s?.height / 1e6 ?? 0), 0);
}

function defaultMpFromEntry(r) {
  return r.defaultJpeg?.mp ?? r.defaultRawSensor?.mp ?? 0;
}

function renderResolutionBetrayalPanel(d, glossary) {
  const entries = d.stillResolutionHonesty || d.resolutionBetrayal?.entries || [];
  if (!entries.length && d.resolutionBetrayal?.index == null) return '';
  const idx = d.resolutionBetrayal?.index ?? '—';
  const rows = entries.map((r) => {
    const def = fmtNum(defaultMpFromEntry(r));
    const max = fmtNum(maxMpFromEntry(r));
    const proven = r.hasLargerThanDefault ? 'hidden high-res on alt map' : 'default path only';
    return `<tr><td>${r.cameraId}</td><td>${def} MP</td><td>${max} MP</td><td>${proven}</td></tr>`;
  }).join('');
  return `
    <section class="device-card resolution-panel">
      <h3>${glossaryLabel('Resolution withholding', glossary, 'resolution_betrayal')}</h3>
      <p class="software-line">Betrayal index: <strong>${idx}</strong> (higher = more cameras with HAL high-res maps above Camera2 default)</p>
      <p>OEM stream maps may expose higher resolutions to the stock camera app via alternate maps; Camera2 default sessions may be capped lower.</p>
      <table class="data-table"><thead><tr><th>Camera</th><th>Default MP</th><th>Max advertised MP</th><th>Notes</th></tr></thead><tbody>${rows || '<tr><td colspan="4">No data</td></tr>'}</tbody></table>
    </section>`;
}

function renderOemLossPanel(d) {
  const o = d.oemLossSummary;
  if (!o) return '';
  const losses = (o.topLosses || []).map((x) => {
    const cls = x.consumerImpact === 'SHIP_BLOCKER' ? 'pill pill-ship-blocker' : 'pill';
    return `<span class="${cls}">${x.displayName || x.catalogId}</span>`;
  }).join(' ');
  return `
    <section class="device-card">
      <h3>OEM app vs Camera2</h3>
      <p><em>OEM camera app not tested.</em> This device was measured via Camera2 sessions only.</p>
      <p>Ship-blockers: <strong>${o.shipBlockerCount ?? 0}</strong> · Delivery mismatches: <strong>${o.deliveryMismatchCount ?? 0}</strong></p>
      <div class="pills">${losses || '<span class="pill pill-info">No major gaps logged</span>'}</div>
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

function renderExternalScores(d) {
  const scores = d.identity?.externalScores || [];
  if (!scores.length) return '';
  const links = scores.map((s) =>
    `<li><a href="${s.url}" target="_blank" rel="noopener">${s.source}</a>${s.score ? `: ${s.score}` : ''} <small>${s.note || ''}</small></li>`
  ).join('');
  return `<section class="device-card"><h3>External reviews</h3><p><em>Third-party lab scores measure OEM app output, not Camera2 parity.</em></p><ul>${links}</ul></section>`;
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
    <div class="device-card">
      <h1>${d.identity?.marketingName}</h1>
      <p class="sub">${d.identity?.manufacturer} ${d.identity?.model}${gsmLink ? ` · ${gsmLink}` : ''}</p>
      <div class="badges">${trustBadge(d.meta?.trustTier, d.software?.romFlavor)} ${apiLevelBadge(d)}</div>
      <p class="software-line"><strong>Tested on:</strong> ${formatApiLevel(d) || '—'}${d.software?.buildDisplay ? ` · ${d.software.buildDisplay}` : ''}${d.software?.securityPatch ? ` · patch ${d.software.securityPatch}` : ''}</p>
      <p style="font-size:0.85rem">${videoOneLiner(d)}</p>
      ${lensStripHtml(d)}
      ${withheldPills(d)}
      <div class="stat-row">
        <div class="stat"><strong>#${d.scores?.rank ?? '—'}</strong> Rank</div>
        <div class="stat"><strong>${d.scores?.total?.score ?? '—'}</strong> ${glossaryLabel('Parity pts', glossary, 'parity_pts')} <small class="muted">(${d.scores?.total?.percent ?? '—'}%)</small> ${progressBar(d.scores?.total?.percent)}</div>
        <div class="stat"><strong>${d.disparity?.honestyPercent}%</strong> ${glossaryLabel('Honesty', glossary, 'honesty')} ${progressBar(d.disparity?.honestyPercent)}</div>
        <div class="stat"><strong>${fmtNum(d.antutu?.total)}</strong> AnTuTu</div>
        <div class="stat"><strong>${sensorSumLabel(d)}</strong> mm² ${sensorSourceNote(d) ? `<br><small>${sensorSourceNote(d)}</small>` : ''}</div>
        ${d.value?.parityPerUsd ? `<div class="stat"><strong>${d.value.parityPerUsd}</strong> Parity/$</div>` : ''}
      </div>
      ${history?.length ? `<p>Trend: <svg class="sparkline" width="160" height="36"></svg></p>` : ''}
      <p>Format picker honesty: ${d.formatPickerHonestyScore ?? '—'}%</p>
      ${specLinks ? `<p class="spec-links">${specLinks}</p>` : ''}
      <p><small>Share: <input readonly value="${shareDeviceUrl(d.slug)}" style="width:100%;max-width:400px"></small></p>
    </div>
    ${renderOemLossPanel(d)}
    ${apiToggleBar()}
    <div class="api-panel api-panel-camera2">
    ${renderResolutionBetrayalPanel(d, glossary)}
    ${renderVideoPanel(d)}
    </div>
    ${renderCameraXPanel(d)}
    <section class="device-card">
      <h3>Rear sensor sizes</h3>
      ${renderSensorSvg(d)}
      <p class="software-line">Combined rear area: <strong>${sensorSumLabel(d)} mm²</strong>${sensorSourceNote(d) ? ` · ${sensorSourceNote(d)}` : ''}</p>
      ${renderRearLensTable(d)}
    </section>
    ${rawPanel}
    ${renderExternalScores(d)}
    <section class="accordion">${categories}</section>
    <p><a href="https://github.com/edwardlthompson/point-and-shoot/issues/new?template=leaderboard_device_request.md">Request a device</a> ·
       <a href="https://github.com/edwardlthompson/point-and-shoot/issues/new?template=leaderboard_dispute.md">Report incorrect entry</a></p>`;
}

export function attachSparkline(container, history) {
  const svg = container.querySelector('.sparkline');
  if (svg && history?.length) drawSparkline(svg, history);
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
