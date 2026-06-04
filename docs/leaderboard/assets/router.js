export function parseRoute() {
  const hash = location.hash.replace(/^#/, '') || '/';
  const q = new URLSearchParams(location.search);
  if (hash.startsWith('/device/')) {
    return { view: 'device', slug: hash.split('/')[2], compare: q.get('compare')?.split(',') || [] };
  }
  if (hash.startsWith('/compare')) {
    return { view: 'compare', slugs: q.get('devices')?.split(',').filter(Boolean) || [] };
  }
  if (hash.startsWith('/oem')) return { view: 'oem', slugs: [] };
  return {
    view: 'home',
    device: q.get('device'),
    compare: q.get('compare')?.split(',').filter(Boolean) || [],
  };
}

export function navTo(path) {
  if (path.startsWith('?')) {
    history.pushState(null, '', path);
  } else {
    location.hash = path;
  }
  window.dispatchEvent(new Event('pns-route'));
}

export function shareDeviceUrl(slug) {
  const base = location.href.split(/[?#]/)[0];
  return `${base}?device=${encodeURIComponent(slug)}`;
}

export function shareCompareUrl(slugs) {
  const base = location.href.split(/[?#]/)[0];
  return `${base}?compare=${slugs.map(encodeURIComponent).join(',')}`;
}
