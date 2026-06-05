import { initTheme, initMethodology, initWishlist, getWishlistSelected } from './theme.js';
import { parseRoute, navTo } from './router.js';
import { renderHome } from './leaderboard.js';
import { renderDeviceDetail, attachSparkline, attachDeviceDetailApiToggle } from './device-detail.js';
import { renderCompare, attachCompareCharts, exportComparePng } from './compare.js';
import { renderOemIndex } from './oem-index.js';
import { renderProductGroup } from './product.js';

const state = {
  site: null,
  devices: [],
  productGroups: [],
  oemAccountability: null,
  glossary: null,
  persona: 'most_capable',
  preset: 'most_capable',
  sortKey: 'parity',
  sortDir: 'desc',
  compareSlugs: new Set(),
  search: '',
  oem: '',
  trust: '',
  hideShipBlockers: false,
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

function devicesBySlug(devices) {
  return Object.fromEntries(devices.map((d) => [d.slug, d]));
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

function applyPreset(preset) {
  state.preset = preset;
  state.persona = preset;
  state.sortKey = preset === 'best_value' ? 'value' : 'parity';
  state.sortDir = 'desc';
  state.trust = preset === 'custom_rom' ? 'custom_lane' : state.trust === 'custom_lane' ? '' : state.trust;
  state.hideShipBlockers = preset === 'custom_rom';
  document.querySelectorAll('[data-preset]').forEach((b) => {
    b.classList.toggle('active', b.dataset.preset === preset);
  });
}

function bindToolbar() {
  document.querySelectorAll('[data-preset]').forEach((btn) => {
    btn.addEventListener('click', () => {
      applyPreset(btn.dataset.preset);
      render();
    });
  });
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
  document.getElementById('filter-ship-safe')?.addEventListener('change', (e) => {
    state.hideShipBlockers = e.target.checked;
    render();
  });
  initWishlist(() => render());
}

function updateFooterStaleNote(site) {
  const footer = document.querySelector('.site-footer .disclaimer');
  if (!footer || !site) return;
  let extra = '';
  if (site.gsmarenaSpecsStale) extra += ' GSMArena advertised specs may be stale (rate-limited scrape).';
  if (site.gsmarenaSpecsFromCache) extra += ' Some GSMArena rows use cached sensor data.';
  if (extra) footer.textContent += extra;
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
    app.innerHTML = renderOemIndex(state.site, state.devices, state.oemAccountability);
    return;
  }

  if (route.view === 'product' && route.groupId) {
    const group = state.productGroups.find((g) => g.groupId === route.groupId);
    app.innerHTML = renderProductGroup(group, devicesBySlug(state.devices));
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
    attachDeviceDetailApiToggle(app);
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
    hideShipBlockers: state.hideShipBlockers,
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
    try { state.productGroups = (await loadJson('data/product_groups.json')).groups || []; } catch { /* */ }
    try { state.oemAccountability = await loadJson('data/oem_accountability.json'); } catch { /* */ }
    try { state.glossary = await loadJson('data/glossary.json'); } catch { /* */ }
  } catch (e) {
    document.getElementById('app').innerHTML = `<div class="cta-box"><p>Leaderboard data not published yet. Run <code>pns_leaderboard_site_publish.ps1</code>.</p></div>`;
    return;
  }
  populateOemFilter();
  bindToolbar();
  updateFooterStaleNote(state.site);
  applyPreset('most_capable');
  window.addEventListener('pns-route', render);
  window.addEventListener('hashchange', render);
  await render();
}

boot();
