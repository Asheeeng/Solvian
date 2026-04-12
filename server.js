const express = require('express');
const path = require('path');
const app = express();
const PORT = process.env.PORT || 8080;

app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(express.static(path.join(__dirname)));
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));

const apiRoutes = require('./api/routes');
app.use('/api', apiRoutes);

app.get('/', (_, res) => res.sendFile(path.join(__dirname, 'index.html')));

app.listen(PORT, () => console.log(`\n  🚀 Solvian: http://127.0.0.1:${PORT}\n`));
