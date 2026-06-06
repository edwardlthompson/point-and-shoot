import { fmtNum, progressBar } from './theme.js?v=20260606g';

export function renderOemIndex(site, devices, oemAccountability) {
  const rankings = oemAccountability?.oems?.length ? oemAccountability.oems : (site?.oemRankings || []);
  const subtitle = oemAccountability?.subtitle || 'Rankings reflect Camera2/CameraX access for third-party apps, not OEM camera app quality.';
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
      <td>${o.opennessPercent ?? '—'}% openness ${progressBar(o.opennessPercent)}</td>
      <td>${o.avgResolutionBetrayal ?? '—'} avg res betrayal</td>
      <td>${o.breakthroughCount ?? 0} full-MP breakthrough(s)</td>
      <td>${o.totalShipBlockers ?? o.withheldFeatureCount ?? 0} ship-blockers</td>
      <td>${o.deviceCount ?? 0} device(s)</td>
    </tr>`).join('');

  const worst = rankings.flatMap((o) =>
    (o.worstOffenders || []).map((w) => `<li><strong>${o.manufacturer}</strong>: ${w.catalogId} (${w.deviceCount} devices)</li>`)
  ).slice(0, 15).join('');

  const shameList = rankings.slice(0, 8).map((o) => {
    const devs = devices.filter((d) => d.identity?.manufacturer === o.manufacturer);
    const withheld = devs.flatMap((d) => d.withheldFeatures || []).slice(0, 5);
    return `<details><summary>${o.manufacturer} — Restriction Index ${o.restrictionIndex}</summary>
      <ul>${withheld.map((w) => `<li>${w.displayName || w.catalogId} (${w.gap || 'gap'})</li>`).join('') || '<li>No withheld features logged</li>'}</ul>
    </details>`;
  }).join('');

  return `
    <p><a href="#/">&larr; Leaderboard</a></p>
    <h2>Aftermarket Camera2 openness</h2>
    <p>${subtitle}</p>
    <div class="data-table-wrap">
      <table class="data-table">
        <thead><tr><th>#</th><th>OEM</th><th>Index</th><th>Openness</th><th>Res betrayal</th><th>Breakthroughs</th><th>Ship-blockers</th><th>Devices</th></tr></thead>
        <tbody>${rows || '<tr><td colspan="8">No OEM data yet</td></tr>'}</tbody>
      </table>
    </div>
    ${worst ? `<h3>Top withheld catalog features (fleet aggregate)</h3><ul>${worst}</ul>` : ''}
    <h3>Per-OEM detail</h3>
    <div class="accordion">${shameList}</div>`;
}
