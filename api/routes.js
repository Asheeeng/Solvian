const express = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const router = express.Router();

const DB = path.join(__dirname, '..', 'data', 'db.json');

function readDB() {
  try {
    if (!fs.existsSync(DB)) {
      const dir = path.dirname(DB);
      if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
      fs.writeFileSync(DB, JSON.stringify({ students: [], diagnoses: [] }, null, 2));
    }
    return JSON.parse(fs.readFileSync(DB, 'utf-8'));
  } catch {
    return { students: [], diagnoses: [] };
  }
}

function writeDB(d) {
  const dir = path.dirname(DB);
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(DB, JSON.stringify(d, null, 2));
}

const upDir = path.join(__dirname, '..', 'uploads');
if (!fs.existsSync(upDir)) fs.mkdirSync(upDir, { recursive: true });
const storage = multer.diskStorage({
  destination: (_, __, cb) => cb(null, upDir),
  filename: (_, file, cb) => cb(null, `${Date.now()}-${Math.random().toString(36).slice(2, 8)}${path.extname(file.originalname)}`)
});
const upload = multer({ storage, limits: { fileSize: 20 * 1024 * 1024 } });

router.get('/students', (_, res) => {
  res.json(readDB().students);
});

router.post('/students', (req, res) => {
  const db = readDB();
  const s = {
    id: req.body.id || 's' + Date.now().toString(36),
    sid: req.body.sid || req.body.id,
    name: req.body.name,
    cls: req.body.cls || '数学A班',
    works: []
  };
  db.students.push(s);
  writeDB(db);
  res.json(s);
});

router.delete('/students/:id', (req, res) => {
  const db = readDB();
  db.students = db.students.filter(s => s.id !== req.params.id);
  writeDB(db);
  res.json({ ok: true });
});

router.post('/upload', upload.single('image'), (req, res) => {
  const db = readDB();
  const stu = db.students.find(s => s.id === req.body.studentId);
  if (!stu) return res.status(404).json({ error: '学生不存在' });
  const now = new Date();
  const time = `${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
  const w = {
    id: 'w' + Date.now().toString(36),
    file: req.file.originalname,
    time,
    status: 'pending',
    url: `/uploads/${req.file.filename}`
  };
  stu.works.unshift(w);
  writeDB(db);
  res.json(w);
});

router.post('/diagnose', upload.none(), async (req, res) => {
  try {
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 🔌 这里调用你的大模型 API
    //
    // const result = await yourDiagnoseFunction(req.body.image_url);
    //
    // 返回格式:
    // {
    //   ocr: [{ label, text }],
    //   steps: [{
    //     n, title, ok,
    //     formula, formulaOk, note,
    //     errors: [{ loc, wrote, correct, explain, suggest }],
    //     boxes: [{ x, y, w, h, label }]
    //   }]
    // }
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    const result = {
      ocr: [{ label: '题目', text: 'M = [3 -3 -3 3; -3 -1 0 -2; 2 3 -4 3; 2 5 0 -1]' }],
      steps: [
        { n:1, title:'写出原始矩阵', ok:true, formula:'3 -3 -3 3\n-3 -1 0 -2\n2 3 -4 3\n2 5 0 -1', note:'正确' },
        { n:2, title:'R1→2R1', ok:false,
          formula:'6 -6 -6 6\n<ERR>0</ERR> -1 0 -2\n2 3 -4 3\n2 5 0 -1',
          formulaOk:'6 -6 -6 6\n-3 -1 0 -2\n2 3 -4 3\n2 5 0 -1',
          note:'第二行被误改',
          errors:[{loc:'第2行第1列',wrote:'0',correct:'-3',
            explain:'R1→2R1只影响第一行，第二行不应修改。',
            suggest:'行变换只改目标行，做完逐行检查。'}],
          boxes:[{x:10,y:35,w:16,h:12,label:'误改'}]
        }
      ]
    };

    const db = readDB();
    db.diagnoses.push({ id:'d' + Date.now().toString(36), studentId:req.body.student_id, workId:req.body.work_id, time:new Date().toISOString(), result });
    const stu = db.students.find(s => s.id === req.body.student_id);
    if (stu) {
      const w = stu.works.find(w => w.id === req.body.work_id);
      if (w) w.status = 'done';
    }
    writeDB(db);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: e.message });
  }
});

router.post('/reset', (_, res) => {
  writeDB({ students: [], diagnoses: [] });
  if (fs.existsSync(upDir)) fs.readdirSync(upDir).forEach(f => fs.unlinkSync(path.join(upDir, f)));
  res.json({ ok: true });
});

module.exports = router;
