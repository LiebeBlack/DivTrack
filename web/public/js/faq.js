/* ======================================================
   DivTrack Web — JS de faq.html
   Acordeón de preguntas frecuentes interactivo
   ====================================================== */
(() => {
  'use strict';

  const preguntas = document.querySelectorAll('.faq-item');

  preguntas.forEach((item) => {
    const btn = item.querySelector('.faq-question');
    btn.addEventListener('click', () => {
      const open = item.classList.contains('open');
      preguntas.forEach((p) => p.classList.remove('open'));
      if (!open) {
        item.classList.add('open');
      }
    });
  });
})();
