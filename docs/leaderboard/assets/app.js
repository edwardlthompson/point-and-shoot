import { initTheme, initMethodology, initWishlist, getWishlistSelected } from './theme.js';
import { parseRoute, navTo } from './router.js';
import { renderHome } from './leaderboard.js';
import { renderDeviceDetail, attachSparkline } from './device-detail.js';
import { renderCompare, attachCompareCharts, exportComparePng } from './compare.js';
import { renderOemIndex } from './oem-index.js';

const state = {
  site: null,
  devices: [],
  glossary: null,
  persona: 'default',
  sortKey: 'parity',
  sortDir: 'desc',
  compareSlugs: new Set(),
  search: '',
  oem: '',
  trust: '',
};

async function loadJson(path) {
  const r = await fetch(path);
  if (!r.ok) throw new Error(path);
  return r.json();
}

async function loadDevices(slugs) {
  const list = [];
  for (const slug of slugs) {
    try {
      list.push(await loadJson(`data/devices/${slug}.json`));
    } catch (e) {
      console.warn('Missing device', slug);
    }
  }
  return list;
}

async function loadHistory(slug) {
  try {
    const text = await fetch(`data/history/${slug}.jsonl`).then((r) => r.text());
    return text.trim().split('\n').filter(Boolean).map((l) => JSON.parse(l));
  } catch {
    return [];
  }
}

function populateOemFilter() {
  const sel = document.getElementById('filter-oem');
  if (!sel) return;
  const oems = [...new Set(state.devices.map((d) => d.identity?.manufacturer).filter(Boolean))].sort();
  oems.forEach((o) => {
    const opt = document.createElement('option');
    opt.value = o;
    opt.textContent = o;
    sel.appendChild(opt);
  });
}

function bindToolbar() {
  document.querySelectorAll('[data-persona]').forEach((btn) => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('[data-persona]').forEach((b) => b.classList.remove('active'));
      btn.classList.add('active');
      state.persona = btn.dataset.persona;
      render();
    });
  });
  document.getElementById('search')?.addEventListener('input', (e) => {
    state.search = e.target.value;
    render();
  });
  document.getElementById('filter-oem')?.addEventListener('change', (e) => {
    state.oem = e.target.value;
    render();
  });
  document.getElementById('filter-trust')?.addEventListener('change', (e) => {
    state.trust = e.target.value;
    render();
  });
  initWishlist(() => render());
}

function bindTableSort(container) {
  container.querySelectorAll('th[data-sort]').forEach((th) => {
    th.addEventListener('click', () => {
      const key = th.dataset.sort;
      if (state.sortKey === key) state.sortDir = state.sortDir === 'asc' ? 'desc' : 'asc';
      else { state.sortKey = key; state.sortDir = 'desc'; }
      render();
    });
  });
}

function bindCompareCheckboxes(container) {
  container.querySelectorAll('[data-compare]').forEach((cb) => {
    cb.addEventListener('change', () => {
      const slug = cb.dataset.compare;
      if (cb.checked) {
        if (state.compareSlugs.size >= 3) { cb.checked = false; return; }
        state.compareSlugs.add(slug);
      } else state.compareSlugs.delete(slug);
      render();
    });
  });
}

async function render() {
  const app = document.getElementById('app');
  const route = parseRoute();

  if (route.device && route.view === 'home') {
    location.hash = `#/device/${route.device}`;
    return;
  }
  if (route.compare?.length && route.view === 'home') {
    route.compare.forEach((s) => state.compareSlugs.add(s));
  }

  if (route.view === 'oem') {
    app.innerHTML = renderOemIndex(state.site, state.devices);
    return;
  }

  if (route.view === 'device' && route.slug) {
    let d = state.devices.find((x) => x.slug === route.slug);
    if (!d) {
      try { d = await loadJson(`data/devices/${route.slug}.json`); } catch { /* */ }
    }
    if (!d) { app.innerHTML = '<p>Device not found.</p>'; return; }
    const history = await loadHistory(route.slug);
    app.innerHTML = renderDeviceDetail(d, history, state.glossary);
    attachSparkline(app, history);
    return;
  }

  if (route.view === 'compare') {
    const slugs = route.slugs.length ? route.slugs : [...state.compareSlugs];
    const devs = state.devices.filter((d) => slugs.includes(d.slug));
    app.innerHTML = renderCompare(devs);
    attachCompareCharts(app, devs);
    document.getElementById('export-png')?.addEventListener('click', () => {
      const t = document.getElementById('compare-export-target');
      if (t) exportComparePng(t);
    });
    return;
  }

  app.innerHTML = renderHome(state.devices, {
    persona: state.persona,
    search: state.search,
    oem: state.oem,
    trust: state.trust,
    wishlist: getWishlistSelected(),
    sortKey: state.sortKey,
    sortDir: state.sortDir,
    compareSlugs: state.compareSlugs,
  });
  bindTableSort(app);
  bindCompareCheckboxes(app);
}

async function boot() {
  initTheme();
  initMethodology();
  try {
    state.site = await loadJson('data/site.json');
    state.devices = await loadDevices(state.site.deviceSlugs || []);
    try { state.glossary = await loadJson('data/glossary.json'); } catch { /* */ }
  } catch (e) {
    document.getElementById('app').innerHTML = `<div class="cta-box"><p>Leaderboard data not published yet. Run <code>pns_leaderboard_site_publish.ps1</code>.</p></div>`;
    return;
  }
  populateOemFilter();
  bindToolbar();
  window.addEventListener('pns-route', render);
  window.addEventListener('hashchange', render);
  await render();
}

boot();
