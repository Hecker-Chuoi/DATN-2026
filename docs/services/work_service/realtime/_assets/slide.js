// Điều khiển slide dùng chung: ← → / Space / Home / End, F = toàn màn hình, T = đổi sáng/tối.
// Thêm "?all" vào URL để xem tất cả slide liền nhau (tiện in PDF).
(function () {
  const slides = Array.from(document.querySelectorAll('.slide'));
  if (!slides.length) return;

  if (new URLSearchParams(location.search).has('all')) {
    document.body.classList.add('all');
    return;
  }

  const controls = document.createElement('div');
  controls.className = 'controls';
  controls.innerHTML =
    '<button type="button" data-act="prev" aria-label="Slide trước">← Trước</button>' +
    '<div class="dots" role="tablist"></div><span class="spacer"></span>' +
    '<span class="counter"></span>' +
    '<button type="button" data-act="theme" title="Đổi sáng/tối (T)">◐</button>' +
    '<button type="button" data-act="next" aria-label="Slide sau">Sau →</button>';
  document.body.appendChild(controls);

  const dots = controls.querySelector('.dots');
  const counter = controls.querySelector('.counter');
  slides.forEach((s, i) => {
    const d = document.createElement('span');
    d.title = s.dataset.title || 'Slide ' + (i + 1);
    d.addEventListener('click', () => go(i));
    dots.appendChild(d);
  });

  let cur = Math.min(Math.max(parseInt(location.hash.slice(1), 10) - 1 || 0, 0), slides.length - 1);

  function go(i) {
    cur = Math.min(Math.max(i, 0), slides.length - 1);
    slides.forEach((s, k) => s.classList.toggle('active', k === cur));
    Array.from(dots.children).forEach((d, k) => d.classList.toggle('on', k === cur));
    counter.textContent = (cur + 1) + ' / ' + slides.length;
    slides[cur].scrollTop = 0;
    history.replaceState(null, '', '#' + (cur + 1));
  }

  function toggleTheme() {
    const root = document.documentElement;
    const dark = root.dataset.theme
      ? root.dataset.theme === 'dark'
      : window.matchMedia('(prefers-color-scheme: dark)').matches;
    root.dataset.theme = dark ? 'light' : 'dark';
  }

  controls.addEventListener('click', (e) => {
    const act = e.target.closest('button')?.dataset.act;
    if (act === 'prev') go(cur - 1);
    if (act === 'next') go(cur + 1);
    if (act === 'theme') toggleTheme();
  });

  document.addEventListener('keydown', (e) => {
    if (e.target.closest('input, textarea, .demo')) return;
    if (['ArrowRight', 'PageDown', ' '].includes(e.key)) { e.preventDefault(); go(cur + 1); }
    if (['ArrowLeft', 'PageUp'].includes(e.key)) { e.preventDefault(); go(cur - 1); }
    if (e.key === 'Home') go(0);
    if (e.key === 'End') go(slides.length - 1);
    if (e.key === 'f' || e.key === 'F') {
      if (document.fullscreenElement) document.exitFullscreen();
      else document.documentElement.requestFullscreen?.();
    }
    if (e.key === 't' || e.key === 'T') toggleTheme();
  });

  go(cur);
})();
