/* ======================================================
   DivTrack Web — JS de dashboard.html
   Gráfico de líneas Canvas, simulación en tiempo real, simulador
   ====================================================== */
(() => {
  'use strict';

  const fmt = (n) => n.toLocaleString('es-VE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  const puntos = 40;
  const ahora = Date.now();
  const intervalo = 3600000;

  let oficialBase = 876.7976;
  let paraleloBase = 974.34;

  const generarSerie = (base) => {
    const serie = [];
    for (let i = 0; i < puntos; i++) {
      const t = ahora - (puntos - 1 - i) * intervalo;
      let v = base + (Math.sin(i / 5) * 15) + (Math.random() - 0.5) * 8;
      base = v;
      serie.push({ t, v });
    }
    return serie;
  };

  let serieOficial = generarSerie(oficialBase);
  let serieParalelo = generarSerie(paraleloBase);

  const oficialEl = document.getElementById('dash-oficial');
  const paraleloEl = document.getElementById('dash-paralelo');
  const brechaEl = document.getElementById('dash-brecha');
  const syncEl = document.getElementById('dash-sync');
  const canvas = document.getElementById('chart-tasas');

  const simMonto = document.getElementById('sim-monto');
  const simUsd2bs = document.getElementById('sim-usd2bs');
  const simBs2usd = document.getElementById('sim-bs2usd');
  const simResultado = document.getElementById('sim-resultado');
  const simUnidad = document.getElementById('sim-unidad');
  let simDir = true;

  const tasasSim = () => ({
    oficial: serieOficial[serieOficial.length - 1].v,
    paralelo: serieParalelo[serieParalelo.length - 1].v
  });

  const actualizarSim = () => {
    const t = tasasSim();
    const tasa = simDir ? t.oficial : t.paralelo;
    const valor = parseFloat(simMonto.value) || 0;
    const total = simDir ? valor * tasa : valor / tasa;
    simResultado.textContent = fmt(total);
    simUnidad.textContent = simDir ? 'Bs' : 'USD';
  };

  simUsd2bs.addEventListener('click', () => {
    simDir = true;
    simUsd2bs.classList.add('active');
    simBs2usd.classList.remove('active');
    actualizarSim();
  });
  simBs2usd.addEventListener('click', () => {
    simDir = false;
    simBs2usd.classList.add('active');
    simUsd2bs.classList.remove('active');
    actualizarSim();
  });
  simMonto.addEventListener('input', actualizarSim);

  const ctx = canvas.getContext('2d');
  let dpr = window.devicePixelRatio || 1;
  let width = canvas.parentElement.clientWidth;
  let height = 320;

  canvas.width = width * dpr;
  canvas.height = height * dpr;
  ctx.scale(dpr, dpr);

  const dibujar = () => {
    width = canvas.parentElement.clientWidth;
    canvas.width = width * dpr;
    canvas.height = height * dpr;
    ctx.scale(dpr, dpr);

    ctx.clearRect(0, 0, width, height);

    const padding = 40;
    const plotW = width - padding * 2;
    const plotH = height - padding * 2;

    const todos = [...serieOficial, ...serieParalelo];
    const minV = Math.min(...todos.map(p => p.v)) * 0.98;
    const maxV = Math.max(...todos.map(p => p.v)) * 1.02;

    const escalaX = (i) => padding + (i / (puntos - 1)) * plotW;
    const escalaY = (v) => height - padding - ((v - minV) / (maxV - minV)) * plotH;

    const gradOficial = ctx.createLinearGradient(0, 0, 0, height);
    gradOficial.addColorStop(0, 'rgba(34, 211, 238, 0.5)');
    gradOficial.addColorStop(1, 'rgba(34, 211, 238, 0)');

    const gradParalelo = ctx.createLinearGradient(0, 0, 0, height);
    gradParalelo.addColorStop(0, 'rgba(251, 113, 133, 0.5)');
    gradParalelo.addColorStop(1, 'rgba(251, 113, 133, 0)');

    const dibujarSerie = (serie, color, grad) => {
      ctx.beginPath();
      serie.forEach((p, i) => {
        const x = escalaX(i);
        const y = escalaY(p.v);
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });
      ctx.lineTo(escalaX(puntos - 1), height - padding);
      ctx.lineTo(padding, height - padding);
      ctx.closePath();
      ctx.fillStyle = grad;
      ctx.fill();

      ctx.beginPath();
      serie.forEach((p, i) => {
        const x = escalaX(i);
        const y = escalaY(p.v);
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });
      ctx.strokeStyle = color;
      ctx.lineWidth = 3;
      ctx.lineJoin = 'round';
      ctx.stroke();

      const last = serie[serie.length - 1];
      ctx.beginPath();
      ctx.arc(escalaX(puntos - 1), escalaY(last.v), 6, 0, Math.PI * 2);
      ctx.fillStyle = color;
      ctx.fill();
      ctx.strokeStyle = '#02040a';
      ctx.lineWidth = 2;
      ctx.stroke();
    };

    dibujarSerie(serieOficial, '#22d3ee', gradOficial);
    dibujarSerie(serieParalelo, '#fb7185', gradParalelo);

    ctx.fillStyle = '#94a3b8';
    ctx.font = '12px ui-sans-serif, system-ui';
    ctx.textAlign = 'left';
    ctx.fillText(fmt(minV).toString(), 10, height - padding + 20);
    ctx.fillText(fmt(maxV).toString(), 10, padding + 10);

    ctx.textAlign = 'right';
    ctx.fillText('Hace ' + (puntos - 1) + 'h', width - padding, height - padding + 20);
    ctx.fillText('Ahora', width - padding, height - padding + 20);
  };

  dibujar();
  window.addEventListener('resize', dibujar);

  const actualizarTasas = () => {
    const sOficial = generarSerie(serieOficial[serieOficial.length - 1].v);
    const sParalelo = generarSerie(serieParalelo[serieParalelo.length - 1].v);
    serieOficial = sOficial;
    serieParalelo = sParalelo;

    const o = serieOficial[serieOficial.length - 1].v;
    const p = serieParalelo[serieParalelo.length - 1].v;
    const b = (((p - o) / o) * 100).toFixed(1);

    oficialEl.textContent = fmt(o);
    paraleloEl.textContent = fmt(p);
    brechaEl.textContent = b + '%';
    syncEl.textContent = new Date().toLocaleTimeString('es-VE', { hour: '2-digit', minute: '2-digit' });

    oficialEl.classList.add('pulse-glow');
    paraleloEl.classList.add('pulse-glow');
    setTimeout(() => {
      oficialEl.classList.remove('pulse-glow');
      paraleloEl.classList.remove('pulse-glow');
    }, 600);

    dibujar();
    actualizarSim();
  };

  setInterval(actualizarTasas, 6000);
  actualizarSim();
})();
