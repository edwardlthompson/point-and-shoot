import { fmtNum, progressBar } from './theme.js';

export function renderOemIndex(site, devices) {
  const rankings = site?.oemRankings || [];
  const rows = rankings.map((o, i) => `
    <tr>
      <td>${i + 1}</td>
      <td><strong>${o.manufacturer}</strong></td>
      <td>
        <div class="gauge" style="border-color: var(--${o.restrictionIndex > 50 ? 'red' : 'green'})">
          ${o.restrictionIndex}
        </div>
        <small>Restriction Index</small>
      </td>
      <td>${o.opennessPercent}% openness ${progressBar(o.opennessPercent)}</td>
      <td>${o.gateHonestyPercent}% gate honesty</td>
      <td>${o.withheldFeatureCount} withheld</td>
      <td>${o.deviceCount} device(s)</td>
    </tr>`).join('');

  const shameList = rankings.slice(0, 5).map((o) => {
    const devs = devices.filter((d) => d.identity?.manufacturer === o.manufacturer);
    const withheld = devs.flatMap((d) => d.withheldFeatures || []).slice(0, 5);
    return `<details><summary>${o.manufacturer} — Restriction Index ${o.restrictionIndex}</summary>
      <ul>${withheld.map((w) => `<li>${w.displayName || w.catalogId}</li>`).join('') || '<li>No withheld features logged</li>'}</ul>
    </details>`;
  }).join('');

  return `
    <p><a href="#/">&larr; Leaderboard</a></p>
    <h2>OEM Restriction Index</h2>
    <p>Higher = more advertised capabilities withheld from third-party Camera2 apps.</p>
    <div class="data-table-wrap">
      <table class="data-table">
        <thead><tr><th>#</th><th>OEM</th><th>Index</th><th>Openness</th><th>Gates</th><th>Withheld</th><th>Devices</th></tr></thead>
        <tbody>${rows || '<tr><td colspan="7">No OEM data yet</td></tr>'}</tbody>
      </table>
    </div>
    <h3>Commonly withheld (top OEMs)</h3>
    <div class="accordion">${shameList}</div>`;
}
