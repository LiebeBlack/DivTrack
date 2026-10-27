/* ======================================================
   DivTrack Web — JS de api.html
   Simulación de prueba de endpoint API en vivo
   ====================================================== */
(() => {
  'use strict';

  const btn = document.getElementById('btn-probar-api');
  const resultado = document.getElementById('api-resultado');

  if (btn && resultado) {
    btn.addEventListener('click', async () => {
      btn.textContent = '⏳ Consultando…';
      btn.disabled = true;
      resultado.style.display = 'none';

      try {
        await new Promise((r) => setTimeout(r, 1200));

        const data = {
          tasa: 876.7976,
          moneda: 'USD/VES',
          proveedor: 'DolarAPI',
          actualizado: new Date().toISOString(),
          fuente: 'BCV',
          estado: 'operativa'
        };

        resultado.textContent = JSON.stringify(data, null, 2);
        resultado.style.display = 'block';
        resultado.classList.add('pulse-glow');
        setTimeout(() => resultado.classList.remove('pulse-glow'), 600);

        btn.textContent = '✓ Consultado';
        setTimeout(() => { btn.textContent = '🧪 Probar endpoint en vivo'; btn.disabled = false; }, 1500);
      } catch (err) {
        resultado.textContent = 'Error al consultar la API: ' + err.message;
        resultado.style.color = 'var(--danger)';
        resultado.style.display = 'block';
        btn.textContent = '🧪 Probar endpoint en vivo';
        btn.disabled = false;
      }
    });
  }
})();
