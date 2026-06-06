import { fmtNum, progressBar, formatApiLevel, gsmarenaLinkHtml } from './theme.js?v=20260606g';
import { drawRadar, heatmapColor } from './charts.js?v=20260606g';
import { lensStripHtml, videoOneLiner } from './leaderboard.js?v=20260606g';

function radarAxes(d) {
  const antutuNorm = Math.min(100, ((d.antutu?.total ?? 0) / 4000000) * 100);
  const sensorNorm = Math.min(100, ((d.sensors?.sensorSumMm2 ?? 0) / 200) * 100);
  return [
    { label: 'Perf', value: antutuNorm },
    { label: 'Sensor', value: sensorNorm },
    { label: 'Features', value: d.scores?.features?.percent ?? 0 },
    { label: 'Res', value: d.scores?.resolutions?.percent ?? 0 },
    { label: 'Open', value: d.disparity?.honestyPercent ?? 0 },
  ];
}

function heatmapHtml(devices) {
  const cats = new Set();
  devices.forEach((d) => Object.keys(d.cellsByCategory || {}).forEach((c) => cats.add(c)));
  const catList = [...cats].sort();
  const header = `<div></div>${devices.map((d) => `<div><strong>${d.identity?.marketingName?.slice(0, 12)}</strong></div>`).join('')}`;
  const rows = catList.map((cat) => {
    const cells = devices.map((d) => {
      const list = d.cellsByCategory?.[cat] || [];
      const proven = list.filter((c) => c.provenOk).length;
      const adv = list.filter((c) => c.advertised).length;
      const pct = list.length ? Math.round(100 * proven / list.length) : 0;
      const bg = proven === list.length && list.length ? '#bbf7d0' : adv ? '#fde68a' : '#f3f4f6';
      return `<div class="heatmap-cell" style="background:${bg}" title="${pct}% proven">${pct}%</div>`;
    }).join('');
    return `<div>${cat}</div>${cells}`;
  }).join('');
  return `<div class="heatmap" style="grid-template-columns:120px repeat(${devices.length},1fr)">${header}${rows}</div>`;
}

export function renderCompare(devices) {
  if (devices.length < 2) {
    return `<p>Select at least 2 devices from the leaderboard compare checkboxes.</p><p><a href="#/">Back</a></p>`;
  }
  const trustWarning = new Set(devices.map((d) => d.meta?.trustTier)).size > 1
    ? '<p class="pill pill-info">Mixed trust tiers — interpret with care.</p>' : '';
  const romWarning = new Set(devices.map((d) => d.software?.romFlavor)).size > 1
    ? '<p class="pill pill-engineering">Mixed ROM flavors — compare Camera2 on same ROM when possible.</p>' : '';
  const wishIds = ['raw.dng', 'video.hfr', 'face.eye_af', 'video.4k'];
  const matrixHeader = `<tr><th>Feature</th>${devices.map((d) => `<th>${d.identity?.marketingName?.slice(0, 14)}</th>`).join('')}</tr>`;
  const matrixRows = wishIds.map((id) => {
    const cells = devices.map((d) => {
      let cell = null;
      for (const cat of Object.values(d.cellsByCategory || {})) {
        cell = cat.find((c) => c.catalogId === id || c.catalogId?.startsWith(id));
        if (cell) break;
      }
      if (!cell) return '<td>—</td>';
      return `<td>${cell.provenOk ? '✓ proven' : cell.advertised ? '✗ gap' : 'n/a'}</td>`;
    }).join('');
    return `<tr><td>${id}</td>${cells}</tr>`;
  }).join('');
  const cols = devices.map((d) => `
    <div class="device-card">
      <h3>${d.identity?.marketingName}</h3>
      ${gsmarenaLinkHtml(d) ? `<p>${gsmarenaLinkHtml(d)}</p>` : ''}
      <p>Parity ${d.scores?.total?.percent}% · Honesty ${d.disparity?.honestyPercent}%</p>
      <p>Tested ${formatApiLevel(d) || '—'}</p>
      <p>AnTuTu ${fmtNum(d.antutu?.total)} · ${fmtNum(d.sensors?.sensorSumMm2)} mm²</p>
      <p style="font-size:0.8rem">${videoOneLiner(d)}</p>
      ${lensStripHtml(d)}
      <canvas class="radar-canvas" width="280" height="280" data-slug="${d.slug}"></canvas>
    </div>`).join('');

  return `
    <p><a href="#/">&larr; Back</a></p>
    <h2>Compare ${devices.length} devices</h2>
    ${trustWarning}
    ${romWarning}
    <button type="button" class="btn" id="export-png">Export compare PNG</button>
    <div id="compare-export-target">
      <div class="compare-grid">${cols}</div>
      <h3>Wishlist feature matrix</h3>
      <table class="data-table"><thead>${matrixHeader}</thead><tbody>${matrixRows}</tbody></table>
      <h3>Category heatmap</h3>
      ${heatmapHtml(devices)}
    </div>`;
}

export function attachCompareCharts(container, devices) {
  container.querySelectorAll('.radar-canvas').forEach((canvas) => {
    const slug = canvas.dataset.slug;
    const d = devices.find((x) => x.slug === slug);
    if (d) drawRadar(canvas, radarAxes(d));
  });
}

export async function exportComparePng(targetEl) {
  if (typeof html2canvas === 'undefined') {
    await new Promise((resolve, reject) => {
      const s = document.createElement('script');
      s.src = 'https://cdn.jsdelivr.net/npm/html2canvas@1.4.1/dist/html2canvas.min.js';
      s.onload = resolve;
      s.onerror = reject;
      document.head.appendChild(s);
    });
  }
  const canvas = await html2canvas(targetEl, { backgroundColor: getComputedStyle(document.body).backgroundColor });
  const a = document.createElement('a');
  a.download = 'pns-leaderboard-compare.png';
  a.href = canvas.toDataURL('image/png');
  a.click();
}
