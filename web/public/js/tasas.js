/* ======================================================
   DivTrack Web — JS de tasas.html
   Simulación en tiempo real de tasas y brecha
   ====================================================== */
(() => {
  'use strict';

  const fmt = (n) => n.toLocaleString('es-VE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  const oficialEl = document.getElementById('rate-oficial');
  const paraleloEl = document.getElementById('rate-paralelo');
  const trendOficialEl = document.getElementById('trend-oficial');
  const trendParaleloEl = document.getElementById('trend-paralelo');
  const brechaEl = document.getElementById('brecha');
  const btnRefresh = document.getElementById('btn-refresh-oficial');

  let oficial = 876.7976;
  let paralelo = 974.34;

  const ahoraStr = () => {
    const d = new Date();
    return d.toLocaleTimeString('es-VE', { hour: '2-digit', minute: '2-digit' });
  };

  const animarValor = (el, actual, destino) => {
    const pasos = 24;
    let i = 0;
    const paso = (destino - actual) / pasos;
    el.classList.add('pulse-glow');
    const timer = setInterval(() => {
      i += 1;
      el.textContent = fmt(actual + paso * i);
      if (i >= pasos) {
        el.textContent = fmt(destino);
        clearInterval(timer);
        el.classList.remove('pulse-glow');
      }
    }, 35);
  };

  const actualizar = () => {
    const nOficial = oficial + (Math.random() - 0.5) * 3;
    const nParalelo = paralelo + (Math.random() - 0.5) * 4;
    const dOficial = nOficial - oficial;
    const dParalelo = nParalelo - paralelo;

    animarValor(oficialEl, oficial, nOficial);
    animarValor(paraleloEl, paralelo, nParalelo);

    if (trendOficialEl) {
      trendOficialEl.innerHTML = (dOficial >= 0 ? '▲ ' : '▼ ') + Math.abs(dOficial).toFixed(2) + '%';
      trendOficialEl.style.color = dOficial >= 0 ? 'var(--success)' : 'var(--danger)';
    }
    if (trendParaleloEl) {
      trendParaleloEl.innerHTML = (dParalelo >= 0 ? '▲ ' : '▼ ') + Math.abs(dParalelo).toFixed(2) + '%';
      trendParaleloEl.style.color = dParalelo >= 0 ? 'var(--success)' : 'var(--danger)';
    }

    oficial = nOficial;
    paralelo = nParalelo;
    const b = (((paralelo - oficial) / oficial) * 100).toFixed(1);
    brechaEl.textContent = b + '%';
    brechaEl.classList.add('pulse-glow');
    setTimeout(() => brechaEl.classList.remove('pulse-glow'), 600);
  };

  setInterval(actualizar, 7000);

  if (btnRefresh) {
    btnRefresh.addEventListener('click', () => {
      btnRefresh.textContent = '⏳ Actualizando…';
      btnRefresh.disabled = true;
      actualizar();
      setTimeout(() => {
        btnRefresh.textContent = '🔄 Refrescar oficial';
        btnRefresh.disabled = false;
      }, 900);
    });
  }
})();
