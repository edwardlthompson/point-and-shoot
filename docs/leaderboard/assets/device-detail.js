import { trustBadge, fmtNum, progressBar, cellChip, formatApiLevel, apiLevelBadge } from './theme.js';
import { lensStripHtml, withheldPills, videoOneLiner } from './leaderboard.js';
import { drawSparkline } from './charts.js';
import { shareDeviceUrl } from './router.js';

export function renderDeviceDetail(d, history, glossary) {
  const resHonesty = (d.stillResolutionHonesty || []).map((r) => {
    const mp = r.defaultJpeg?.mp ?? r.defaultRawSensor?.mp;
    return `<li>Camera ${r.cameraId}: ${fmtNum(mp)} MP default${r.hasLargerThanDefault ? ' (hidden high-res advertised)' : ''}</li>`;
  }).join('');

  const rawPanel = `
    <section class="device-card">
      <h3>RAW / Pro photo</h3>
      <p>DNG proven: <strong>${d.rawSummary?.dngProven ? 'Yes' : 'No'}</strong></p>
      <p>RAW formats: ${(d.rawSummary?.rawFormats || []).join(', ') || '—'}</p>
      <p>Max RAW MP: ${fmtNum(d.rawSummary?.maxRawMp)}</p>
    </section>`;

  const categories = Object.entries(d.cellsByCategory || {}).map(([cat, cells]) => `
    <details class="accordion-item">
      <summary>${cat} (${cells.length})</summary>
      <div class="body"><ul>${cells.map((c) =>
        `<li>${cellChip(c.advertised, c.provenOk, c.gap)} <strong>${c.displayName || c.catalogId}</strong> ${c.failReason ? `<em>(${c.failReason})</em>` : ''}</li>`
      ).join('')}</ul></div>
    </details>`).join('');

  const specLinks = (d.identity?.specLinks || []).map((l) =>
    `<a href="${l.url}" target="_blank" rel="noopener">${l.label}</a>`
  ).join(' · ');

  return `
    <p><a href="#/">&larr; Back</a></p>
    <div class="device-card">
      <h1>${d.identity?.marketingName}</h1>
      <p class="sub">${d.identity?.manufacturer} ${d.identity?.model}</p>
      <div class="badges">${trustBadge(d.meta?.trustTier, d.software?.romFlavor)} ${apiLevelBadge(d)}</div>
      <p class="software-line"><strong>Tested on:</strong> ${formatApiLevel(d) || '—'}${d.software?.securityPatch ? ` · security patch ${d.software.securityPatch}` : ''}</p>
      <p style="font-size:0.85rem">${videoOneLiner(d)}</p>
      ${lensStripHtml(d)}
      ${withheldPills(d)}
      <div class="stat-row">
        <div class="stat"><strong>${d.scores?.total?.percent}%</strong> Parity ${progressBar(d.scores?.total?.percent)}</div>
        <div class="stat"><strong>${d.disparity?.honestyPercent}%</strong> Honesty ${progressBar(d.disparity?.honestyPercent)}</div>
        <div class="stat"><strong>${fmtNum(d.antutu?.total)}</strong> AnTuTu</div>
        <div class="stat"><strong>${fmtNum(d.sensors?.sensorSumMm2)}</strong> mm²</div>
      </div>
      ${history?.length ? `<p>Trend: <svg class="sparkline" width="120" height="32"></svg></p>` : ''}
      <p>Format picker honesty: ${d.formatPickerHonestyScore ?? '—'}%</p>
      <p>${specLinks}</p>
      <p><small>Share: <input readonly value="${shareDeviceUrl(d.slug)}" style="width:100%;max-width:400px"></small></p>
    </div>
    <section class="device-card">
      <h3>Resolution honesty</h3>
      <ul>${resHonesty || '<li>No data</li>'}</ul>
    </section>
    ${rawPanel}
    <section class="accordion">${categories}</section>
    <p><a href="https://github.com/edwardlthompson/point-and-shoot/issues/new?template=leaderboard_dispute.md">Report incorrect entry</a></p>`;
}

export function attachSparkline(container, history) {
  const svg = container.querySelector('.sparkline');
  if (svg && history?.length) drawSparkline(svg, history);
}
