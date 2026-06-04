export function drawRadar(canvas, axes) {
  const ctx = canvas.getContext('2d');
  const w = canvas.width;
  const h = canvas.height;
  const cx = w / 2;
  const cy = h / 2;
  const r = Math.min(w, h) * 0.38;
  const n = axes.length;
  ctx.clearRect(0, 0, w, h);
  ctx.strokeStyle = getComputedStyle(document.documentElement).getPropertyValue('--border').trim() || '#ccc';
  ctx.fillStyle = getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() || '#2563eb';

  for (let ring = 0.25; ring <= 1; ring += 0.25) {
    ctx.beginPath();
    for (let i = 0; i <= n; i++) {
      const a = (Math.PI * 2 * i) / n - Math.PI / 2;
      const x = cx + Math.cos(a) * r * ring;
      const y = cy + Math.sin(a) * r * ring;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();
  }

  ctx.beginPath();
  axes.forEach((ax, i) => {
    const a = (Math.PI * 2 * i) / n - Math.PI / 2;
    const v = Math.min(100, Math.max(0, ax.value)) / 100;
    const x = cx + Math.cos(a) * r * v;
    const y = cy + Math.sin(a) * r * v;
    if (i === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  });
  ctx.closePath();
  ctx.globalAlpha = 0.35;
  ctx.fill();
  ctx.globalAlpha = 1;
  ctx.stroke();

  ctx.fillStyle = getComputedStyle(document.documentElement).getPropertyValue('--text').trim() || '#000';
  ctx.font = '11px system-ui';
  ctx.textAlign = 'center';
  axes.forEach((ax, i) => {
    const a = (Math.PI * 2 * i) / n - Math.PI / 2;
    const x = cx + Math.cos(a) * (r + 16);
    const y = cy + Math.sin(a) * (r + 16);
    ctx.fillText(ax.label, x, y + 4);
  });
}

export function drawSparkline(svg, points) {
  if (!points.length) return;
  const w = 120;
  const h = 32;
  const vals = points.map((p) => p.totalPercent ?? p.honestyPercent ?? 0);
  const min = Math.min(...vals);
  const max = Math.max(...vals);
  const range = max - min || 1;
  const coords = vals.map((v, i) => {
    const x = (i / Math.max(1, vals.length - 1)) * w;
    const y = h - ((v - min) / range) * (h - 4) - 2;
    return `${x},${y}`;
  }).join(' ');
  svg.innerHTML = `<polyline fill="none" stroke="var(--accent)" stroke-width="2" points="${coords}"/>`;
  svg.setAttribute('viewBox', `0 0 ${w} ${h}`);
}

export function heatmapColor(advertised, proven) {
  if (proven) return '#bbf7d0';
  if (advertised) return '#fde68a';
  return '#f3f4f6';
}
