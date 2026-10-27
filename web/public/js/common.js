/* ======================================================
   DivTrack Web — JS común (todas las páginas)
   Tema claro/oscuro, menú móvil, header, partículas,
   revelado al scroll, sin errores
   ====================================================== */
(() => {
  'use strict';

  // ---------- Tema ----------
  const htmlEl = document.documentElement;
  const themeToggle = document.getElementById('theme-toggle');
  const savedTheme = localStorage.getItem('divtrack-theme');

  const setTheme = (t) => {
    if (t === 'light') {
      htmlEl.classList.remove('dark');
      htmlEl.classList.add('light');
      localStorage.setItem('divtrack-theme', 'light');
      if (themeToggle) themeToggle.textContent = '☀️';
    } else {
      htmlEl.classList.remove('light');
      htmlEl.classList.add('dark');
      localStorage.setItem('divtrack-theme', 'dark');
      if (themeToggle) themeToggle.textContent = '🌙';
    }
  };

  if (savedTheme) {
    setTheme(savedTheme);
  } else {
    setTheme('dark');
  }

  if (themeToggle) {
    themeToggle.addEventListener('click', () => {
      const isDark = htmlEl.classList.contains('dark');
      setTheme(isDark ? 'light' : 'dark');
    });
  }

  // ---------- Menú móvil ----------
  const toggle = document.getElementById('nav-toggle');
  const menu = document.getElementById('mobile-menu');
  const links = document.querySelectorAll('.nav-links a, .mobile-menu a');

  const closeMenu = () => {
    if (menu) menu.classList.remove('open');
    if (toggle) toggle.setAttribute('aria-expanded', 'false');
  };

  if (toggle && menu) {
    toggle.addEventListener('click', () => {
      const open = !menu.classList.contains('open');
      menu.classList.toggle('open');
      toggle.setAttribute('aria-expanded', String(open));
    });
  }

  links.forEach((a) => {
    a.addEventListener('click', closeMenu);
  });

  // ---------- Enlace activo ----------
  const path = window.location.pathname.split('/').pop() || 'index.html';
  links.forEach((a) => {
    const href = a.getAttribute('href');
    if (href === path) {
      a.classList.add('active');
    }
  });

  // ---------- Header sombra al scroll ----------
  const header = document.getElementById('cabecera');
  if (header) {
    let ticking = false;
    const updateHeader = () => {
      if (window.scrollY > 24) {
        header.style.boxShadow = '0 24px 48px -16px rgba(0,0,0,0.55)';
      } else {
        header.style.boxShadow = 'none';
      }
      ticking = false;
    };
    window.addEventListener('scroll', () => {
      if (!ticking) {
        window.requestAnimationFrame(updateHeader);
        ticking = true;
      }
    }, { passive: true });
  }

  // ---------- Revelado al scroll ----------
  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible');
        observer.unobserve(entry.target);
      }
    });
  }, { threshold: 0.1, rootMargin: '0px 0px -60px 0px' });

  document.querySelectorAll('.reveal').forEach((el) => observer.observe(el));
})();
