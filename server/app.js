const express = require('express');
const db = require('./db');

const app = express();
app.use(express.json({ limit: '10mb' }));

// 跨域，安卓访问必备
app.use((req, res, next) => {
  res.header('Access-Control-Allow-Origin', '*');
  res.header('Access-Control-Allow-Methods', 'GET,POST,DELETE,OPTIONS');
  res.header('Access-Control-Allow-Headers', 'Content-Type');
  if (req.method === 'OPTIONS') return res.sendStatus(200);
  next();
});

// ============ 健康检查 ============
app.get('/api/ping', (req, res) => {
  res.json({ code: 0, msg: 'pong' });
});

// ============ 1. 备份上传（幂等 UPSERT） ============
app.post('/api/notes/backup', (req, res) => {
  const { deviceId, notes } = req.body || {};
  if (!deviceId || !Array.isArray(notes)) {
    return res.status(400).json({ code: 400, msg: 'deviceId 和 notes 必填' });
  }
  const now = Date.now();
  const stmt = db.prepare(`
    INSERT INTO notes_backup
      (device_id, note_uuid, title, content, created_at, modified_at, backup_at)
    VALUES (?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(device_id, note_uuid) DO UPDATE SET
      title       = excluded.title,
      content     = excluded.content,
      created_at  = excluded.created_at,
      modified_at = excluded.modified_at,
      backup_at   = excluded.backup_at
  `);

  try {
    const runBatch = db.transaction((list) => {
      let ok = 0;
      for (const n of list) {
        stmt.run(
          deviceId,
          n.uuid || '',
          n.title || '',
          n.content || '',
          n.createdAt || 0,
          n.updatedAt || 0,
          now
        );
        ok++;
      }
      return ok;
    });
    const count = runBatch(notes);
    res.json({ code: 0, msg: '备份成功', count: count, backupAt: now });
  } catch (err) {
    console.error(err);
    res.status(500).json({ code: 500, msg: '写入失败: ' + err.message });
  }
});

// ============ 2. 拉取备份 ============
app.get('/api/notes/restore', (req, res) => {
  const deviceId = req.query.deviceId;
  if (!deviceId) {
    return res.status(400).json({ code: 400, msg: 'deviceId 必填' });
  }
  try {
    const stmt = db.prepare(`
      SELECT note_uuid AS uuid, title, content, created_at AS createdAt,
             modified_at AS updatedAt, backup_at AS backupAt
      FROM notes_backup WHERE device_id = ? ORDER BY modified_at DESC
    `);
    const rows = stmt.all(deviceId);
    res.json({ code: 0, data: rows });
  } catch (err) {
    console.error(err);
    res.status(500).json({ code: 500, msg: err.message });
  }
});

// ============ 3. 删除单条备份 ============
app.delete('/api/notes/backup/:uuid', (req, res) => {
  const { uuid } = req.params;
  const deviceId = req.query.deviceId;
  if (!deviceId) {
    return res.status(400).json({ code: 400, msg: 'deviceId 必填' });
  }
  try {
    const stmt = db.prepare('DELETE FROM notes_backup WHERE device_id = ? AND note_uuid = ?');
    const result = stmt.run(deviceId, uuid);
    res.json({ code: 0, deleted: result.changes });
  } catch (err) {
    console.error(err);
    res.status(500).json({ code: 500, msg: err.message });
  }
});

// 临时统计接口
app.get('/api/count', (req, res) => {
  try {
    const stmt = db.prepare(`select device_id, count(*) as cnt from notes_backup group by device_id;`);
    const list = stmt.all();
    res.json({code:0, data:list});
  } catch(err) {
    res.status(500).json({code:500, msg:err.message});
  }
});

// ============ 启动 ============
const PORT = 3000;
app.listen(PORT, '0.0.0.0', () => {
  console.log(`✅ NotePad backup server running at http://0.0.0.0:${PORT}`);
  console.log(`   模拟器访问: http://10.0.2.2:${PORT}`);
  console.log(`   真机访问:   http://<你的电脑局域网IP>:${PORT}`);
});
