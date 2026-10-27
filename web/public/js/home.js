/* ======================================================
   DivTrack Web — JS de home.js
   Animaciones del hero y tarjetas reveladas
   ====================================================== */
(() => {
  'use strict';

  const fmt = (n) => n.toLocaleString('es-VE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  let oficial = 876.7976;
  let paralelo = 974.34;

  const oficialEl = document.getElementById('rate-oficial');
  const paraleloEl = document.getElementById('rate-paralelo');
  const brechaEl = document.getElementById('brecha');

  const animar = (el, actual, destino) => {
    const pasos = 24;
    let i = 0;
    const paso = (destino - actual) / pasos;
    const timer = setInterval(() => {
      i += 1;
      el.textContent = fmt(actual + paso * i);
      if (i >= pasos) {
        el.textContent = fmt(destino);
        clearInterval(timer);
        el.classList.add('pulse-glow');
        setTimeout(() => el.classList.remove('pulse-glow'), 600);
      }
    }, 35);
  };

  const actualizar = () => {
    const nOficial = oficial + (Math.random() - 0.5) * 2.5;
    const nParalelo = paralelo + (Math.random() - 0.5) * 3.5;
    animar(oficialEl, oficial, nOficial);
    animar(paraleloEl, paralelo, nParalelo);
    oficial = nOficial;
    paralelo = nParalelo;
    const b = (((paralelo - oficial) / oficial) * 100).toFixed(1);
    brechaEl.textContent = b + '%';
    brechaEl.classList.add('pulse-glow');
    setTimeout(() => brechaEl.classList.remove('pulse-glow'), 600);
  };

  setInterval(actualizar, 7000);
})();
