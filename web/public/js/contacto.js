/* ======================================================
   DivTrack Web — JS de contacto.html
   Formulario de contacto con validación y simulación de envío
   ====================================================== */
(() => {
  'use strict';

  const form = document.getElementById('form-contacto');
  const msg = document.getElementById('form-mensaje');

  if (form) {
    form.addEventListener('submit', (e) => {
      e.preventDefault();

      const nombre = form.nombre.value.trim();
      const email = form.email.value.trim();
      const asunto = form.asunto.value;
      const mensaje = form.mensaje.value.trim();

      if (!nombre || !email || !asunto || !mensaje) {
        mostrarMensaje('Por favor completa todos los campos.', 'danger');
        return;
      }

      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailRegex.test(email)) {
        mostrarMensaje('El correo electrónico no es válido.', 'danger');
        return;
      }

      const btn = form.querySelector('button[type="submit"]');
      const textoOriginal = btn.textContent;
      btn.textContent = 'Enviando…';
      btn.disabled = true;

      setTimeout(() => {
        btn.textContent = textoOriginal;
        btn.disabled = false;
        form.reset();
        mostrarMensaje('Mensaje enviado correctamente. Te responderemos pronto.', 'success');
      }, 1500);
    });
  }

  const mostrarMensaje = (texto, tipo) => {
    if (!msg) return;
    msg.textContent = texto;
    msg.style.display = 'block';
    msg.style.background = tipo === 'success' ? 'rgba(52, 211, 153, 0.15)' : 'rgba(251, 113, 133, 0.15)';
    msg.style.color = tipo === 'success' ? 'var(--success)' : 'var(--danger)';
    msg.style.border = `1px solid ${tipo === 'success' ? 'var(--success)' : 'var(--danger)'}`;
    setTimeout(() => { msg.style.display = 'none'; }, 5000);
  };
})();
