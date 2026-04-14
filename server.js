const express = require('express');
const path = require('path');
const app = express();
const PORT = process.env.PORT || 8080;

app.use(express.json());
app.use(express.urlencoded({ extended: true }));

app.get('/', (_, res) => {
  res.sendFile(path.join(__dirname, 'login.html'));
});

app.get(/^\/home(?:\.html)?$/, (_, res) => {
  res.redirect('/');
});

app.use(express.static(path.join(__dirname), { index: false }));
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));

const apiRoutes = require('./api/routes');
app.use('/api', apiRoutes);

app.listen(PORT, () => console.log(`\n  🚀 Solvian: http://127.0.0.1:${PORT}\n`));
