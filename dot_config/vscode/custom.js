// Bottom/right scroll shadows for VS Code (loaded by Custom UI Style).
// VS Code only draws top/left shadows. Its scrollables aren't native, so "more content below/right"
// is read from the custom scrollbar slider: if it doesn't reach the end of its track, there's more.
// Sets .cus-more-bottom / .cus-more-right on .monaco-scrollable-element; custom.css draws the shadow.
(() => {
  const pending = new Set();
  let scheduled = false;

  const reachesEnd = (track, slider, axis) => {
    if (!track || !slider || !track.offsetParent) return true;
    const size = axis === 'y' ? track.offsetHeight : track.offsetWidth;
    const pos = parseFloat(axis === 'y' ? slider.style.top : slider.style.left) || 0;
    const len = axis === 'y' ? slider.offsetHeight : slider.offsetWidth;
    return size === 0 || len >= size - 1 || pos + len >= size - 1;
  };

  const update = (el) => {
    const v = el.querySelector(':scope > .scrollbar.vertical');
    const h = el.querySelector(':scope > .scrollbar.horizontal');
    el.classList.toggle('cus-more-bottom', !reachesEnd(v, v?.querySelector('.slider'), 'y'));
    el.classList.toggle('cus-more-right', !reachesEnd(h, h?.querySelector('.slider'), 'x'));
  };

  const flush = () => {
    scheduled = false;
    for (const el of pending) if (el.isConnected) update(el);
    pending.clear();
  };

  const queue = (el) => {
    if (!el) return;
    pending.add(el);
    if (!scheduled) {
      scheduled = true;
      requestAnimationFrame(flush);
    }
  };

  new MutationObserver((records) => {
    for (const r of records) {
      const t = r.target;
      if (r.type === 'attributes' && t.classList?.contains('slider')) {
        queue(t.closest('.monaco-scrollable-element'));
      } else if (r.type === 'childList') {
        for (const n of r.addedNodes) {
          if (n.nodeType !== 1) continue;
          if (n.matches('.monaco-scrollable-element')) queue(n);
          n.querySelectorAll?.('.monaco-scrollable-element').forEach(queue);
        }
      }
    }
  }).observe(document.body, { subtree: true, childList: true, attributes: true, attributeFilter: ['style'] });

  addEventListener('resize', () => document.querySelectorAll('.monaco-scrollable-element').forEach(queue));
  document.querySelectorAll('.monaco-scrollable-element').forEach(queue);
})();
