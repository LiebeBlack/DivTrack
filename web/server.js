const express = require('express');
const path = require('path');
const morgan = require('morgan');
const compression = require('compression');
const helmet = require('helmet');

const app = express();
const PORT = process.env.PORT || 3000;
const PUBLIC = path.join(__dirname, 'public');

app.use(helmet());
app.use(compression());
app.use(morgan('combined'));
app.use(express.static(PUBLIC));

const PAGES = [
  'index', 'tasas', 'calculadora', 'dashboard', 'app', 'stack', 'offline',
  'tests', 'compilar', 'descarga', 'github', 'api', 'changelog', 'faq',
  'contacto', 'privacidad', 'terminos', 'roadmap', 'equipo', 'historia'
];

PAGES.forEach((page) => {
  app.get(`/${page}.html`, (req, res) => {
    res.sendFile(path.join(PUBLIC, `${page}.html`));
  });
  app.get(`/${page}`, (req, res) => {
    res.redirect(301, `/${page}.html`);
  });
});

app.get('/', (req, res) => {
  res.redirect(301, '/index.html');
});

app.use((req, res) => {
  res.status(404).sendFile(path.join(PUBLIC, '404.html'));
});

app.listen(PORT, () => {
  console.log(`DivTrack Web premium escuchando en http://localhost:${PORT}`);
});

module.exports = app;
