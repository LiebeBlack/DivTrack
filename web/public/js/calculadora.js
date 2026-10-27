/* ======================================================
   DivTrack Web — JS de calculadora.html
   Conversión bidireccional en tiempo real
   ====================================================== */
(() => {
  'use strict';

  const fmt = (n) => n.toLocaleString('es-VE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  const montoEl = document.getElementById('monto');
  const chipUsd2bs = document.getElementById('chip-usd2bs');
  const chipBs2usd = document.getElementById('chip-bs2usd');
  const resultadoEl = document.getElementById('resultado');
  const resultadoUnidadEl = document.getElementById('resultado-unidad');
  const tasaAplicadaEl = document.getElementById('tasa-aplicada');
  const detMontoEl = document.getElementById('det-monto');
  const detTasaEl = document.getElementById('det-tasa');
  const detTotalEl = document.getElementById('det-total');
  const btnCopiar = document.getElementById('btn-copiar');
  const ejemplos = document.querySelectorAll('.ejemplo');

  let tasa = 876.7976;
  let dirUsd2bs = true;

  const actualizar = () => {
    const valor = parseFloat(montoEl.value) || 0;
    let total, unidad;

    if (dirUsd2bs) {
      total = valor * tasa;
      unidad = 'Bs';
      detTasaEl.textContent = fmt(tasa) + ' Bs/USD';
      detMontoEl.textContent = '$' + fmt(valor);
    } else {
      total = valor / tasa;
      unidad = 'USD';
      detTasaEl.textContent = fmt(1 / tasa) + ' USD/Bs';
      detMontoEl.textContent = fmt(valor) + ' Bs';
    }

    resultadoEl.textContent = fmt(total);
    resultadoUnidadEl.textContent = unidad;
    detTotalEl.textContent = fmt(total) + ' ' + unidad;
  };

  const setDir = (usd2bs) => {
    dirUsd2bs = usd2bs;
    chipUsd2bs.classList.toggle('active', usd2bs);
    chipBs2usd.classList.toggle('active', !usd2bs);
    chipUsd2bs.setAttribute('aria-pressed', String(usd2bs));
    chipBs2usd.setAttribute('aria-pressed', String(!usd2bs));
    actualizar();
  };

  chipUsd2bs.addEventListener('click', () => setDir(true));
  chipBs2usd.addEventListener('click', () => setDir(false));
  montoEl.addEventListener('input', actualizar);

  btnCopiar.addEventListener('click', async () => {
    const texto = `${resultadoEl.textContent} ${resultadoUnidadEl.textContent} a tasa ${tasaAplicadaEl.textContent}`;
    try {
      await navigator.clipboard.writeText(texto);
      const original = btnCopiar.textContent;
      btnCopiar.textContent = '✓ Copiado';
      setTimeout(() => { btnCopiar.textContent = original; }, 1400);
    } catch (e) {
      console.error('No se pudo copiar', e);
    }
  });

  ejemplos.forEach((b) => {
    b.addEventListener('click', () => {
      const v = b.getAttribute('data-monto');
      const d = b.getAttribute('data-dir');
      montoEl.value = v;
      setDir(d === 'usd');
      actualizar();
    });
  });

  actualizar();
})();
