/**
 * FIT 转换器本地服务（零依赖，仅 Node 内置模块）。
 *
 * 为什么不是 Electron：macOS 企业管控机（MDM）会由 AppleSystemPolicy 强制
 * SIGKILL 未公证的 Electron 二进制，本服务用 http/fs/child_process 起本地
 * HTTP 服务，浏览器访问即可，不受该策略影响。
 *
 * 接口：
 *   GET  /                 → index.html
 *   GET  /api/config       → jar 是否存在 + 默认路径
 *   GET  /api/list?path=   → 目录浏览（用于选择源文件/输出目录）
 *   POST /api/export       → 执行 java -jar export，SSE 流式返回日志
 *   GET  /api/outputs?path=→ 输出目录下的 .fit 文件列表
 *   GET  /api/file?path=   → 下载输出文件
 *   POST /api/upload?name=  → 接收浏览器上传的源 JSON，存到 input/real/huawei/
 */
'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

const PORT = 8123;
const HOST = '127.0.0.1';
const PROJECT_ROOT = path.resolve(__dirname, '..');
const JAR = path.join(PROJECT_ROOT, 'target', 'fit-converter.jar');

const DEFAULT_SOURCE_FILE = path.join(PROJECT_ROOT, 'input', 'real', 'huawei', 'motion path detail data.json');
const DEFAULT_OUTPUT = path.join(PROJECT_ROOT, 'output', 'real', 'fit');

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.fit': 'application/octet-stream',
};

/** 仅允许访问 ui/ 目录内的静态资源，防目录遍历 */
const STATIC_FILES = new Set(['/', '/index.html', '/renderer.js', '/styles.css']);

function sendJson(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(body);
}

function sendSse(res, event, data) {
  res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', (c) => {
      body += c;
      if (body.length > 1e6) reject(new Error('请求体过大'));
    });
    req.on('end', () => resolve(body));
    req.on('error', reject);
  });
}

/** GET /api/config —— jar 状态与默认路径 */
function handleConfig(res) {
  sendJson(res, 200, {
    jar: JAR,
    jarExists: fs.existsSync(JAR),
    defaultSourceFile: DEFAULT_SOURCE_FILE,
    defaultSourceExists: fs.existsSync(DEFAULT_SOURCE_FILE),
    defaultOutput: DEFAULT_OUTPUT,
    projectRoot: PROJECT_ROOT,
  });
}

/** GET /api/list?path= —— 目录浏览（目录在前，按名称排序） */
function handleList(res, url) {
  const q = new URL(url, 'http://localhost');
  let dir = q.searchParams.get('path');
  if (!dir) dir = process.env.HOME || '/';
  dir = path.resolve(dir);
  try {
    const stat = fs.statSync(dir);
    if (!stat.isDirectory()) return sendJson(res, 400, { error: '不是目录: ' + dir });
  } catch {
    return sendJson(res, 404, { error: '目录不存在: ' + dir });
  }
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch (e) {
    return sendJson(res, 403, { error: '无权限读取: ' + e.message });
  }
  const items = entries
    .map((e) => {
      const full = path.join(dir, e.name);
      let isDir;
      try {
        isDir = fs.statSync(full).isDirectory();
      } catch {
        return null; // 跳过无权限/失效链接
      }
      return { name: e.name, path: full, isDir };
    })
    .filter(Boolean)
    .sort((a, b) => (b.isDir - a.isDir) || a.name.localeCompare(b.name, 'zh-CN'));
  sendJson(res, 200, { path: dir, parent: path.dirname(dir), items });
}

/** POST /api/export —— 流式执行整包导出转换，SSE 推送 stdout/stderr/exit */
async function handleExport(req, res) {
  let body;
  try {
    body = JSON.parse((await readBody(req)) || '{}');
  } catch {
    return sendJson(res, 400, { error: '请求体不是合法 JSON' });
  }
  const sourceFile = body.sourceFile || DEFAULT_SOURCE_FILE;
  const outputDir = body.outputDir || DEFAULT_OUTPUT;

  if (!fs.existsSync(JAR)) {
    return sendJson(res, 500, { error: `未找到 ${JAR}，请先在项目根目录执行 mvn package` });
  }
  if (!fs.existsSync(sourceFile) || !fs.statSync(sourceFile).isFile()) {
    return sendJson(res, 400, { error: '源文件不存在或不是文件: ' + sourceFile });
  }
  try {
    fs.mkdirSync(outputDir, { recursive: true });
  } catch (e) {
    return sendJson(res, 400, { error: '无法创建输出目录: ' + e.message });
  }

  res.writeHead(200, {
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
  });
  sendSse(res, 'start', { command: ['java', '-jar', JAR, 'export', sourceFile, outputDir] });

  const child = spawn(
    'java',
    ['-Dfile.encoding=UTF-8', '-jar', JAR, 'export', sourceFile, outputDir],
    { cwd: PROJECT_ROOT }
  );

  let finished = false;
  const finish = (code) => {
    if (finished) return;
    finished = true;
    sendSse(res, 'exit', { code });
    res.end();
  };

  child.stdout.on('data', (d) => sendSse(res, 'log', { stream: 'stdout', text: d.toString() }));
  child.stderr.on('data', (d) => sendSse(res, 'log', { stream: 'stderr', text: d.toString() }));
  child.on('error', (e) => {
    sendSse(res, 'log', { stream: 'stderr', text: '无法启动 java: ' + e.message + '\n' });
    finish(-1);
  });
  child.on('close', (code) => finish(code));

  // 客户端中断时终止转换进程，避免孤儿 java 进程
  req.on('close', () => {
    if (!finished) child.kill();
  });
}

/** GET /api/outputs?path= —— 输出目录的 .fit 文件列表 */
function handleOutputs(res, url) {
  const q = new URL(url, 'http://localhost');
  const dir = path.resolve(q.searchParams.get('path') || DEFAULT_OUTPUT);
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return sendJson(res, 200, { path: dir, files: [] });
  }
  const files = entries
    .filter((e) => e.isFile() && e.name.toLowerCase().endsWith('.fit'))
    .map((e) => {
      const full = path.join(dir, e.name);
      return { name: e.name, path: full, size: fs.statSync(full).size };
    })
    .sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'));
  sendJson(res, 200, { path: dir, files });
}

/** GET /api/file?path= —— 下载文件 */
function handleFile(res, url) {
  const q = new URL(url, 'http://localhost');
  const file = path.resolve(q.searchParams.get('path') || '');
  if (!fs.existsSync(file)) return sendJson(res, 404, { error: '文件不存在: ' + file });
  const ext = path.extname(file).toLowerCase();
  res.writeHead(200, {
    'Content-Type': MIME[ext] || 'application/octet-stream',
    'Content-Disposition': `attachment; filename="${encodeURIComponent(path.basename(file))}"`,
  });
  fs.createReadStream(file).pipe(res);
}

/** POST /api/upload?name=xxx.json —— 浏览器原生选择器选中文件后上传，保存到 input/real/huawei/ */
function handleUpload(req, res, url) {
  const q = new URL(url, 'http://localhost');
  // basename 防目录穿越；非法/空文件名回退到默认名
  let name = path.basename(q.searchParams.get('name') || '');
  if (!name) name = 'motion path detail data.json';

  const uploadDir = path.join(PROJECT_ROOT, 'input', 'real', 'huawei');
  try {
    fs.mkdirSync(uploadDir, { recursive: true });
  } catch (e) {
    return sendJson(res, 500, { error: '无法创建上传目录: ' + e.message });
  }

  const dest = path.join(uploadDir, name);
  const ws = fs.createWriteStream(dest);
  let size = 0;
  let done = false;
  const respond = (status, obj) => {
    if (!done) {
      done = true;
      sendJson(res, status, obj);
    }
  };

  req.on('data', (c) => {
    size += c.length;
    if (size > 200 * 1024 * 1024) {
      req.destroy();
      ws.destroy();
      fs.unlink(dest, () => {});
      respond(413, { error: '文件超过 200MB 限制' });
    }
  });
  req.pipe(ws);
  ws.on('finish', () => respond(200, { path: dest }));
  ws.on('error', (e) => {
    fs.unlink(dest, () => {});
    respond(500, { error: '保存失败: ' + e.message });
  });
}

/** GET /xxx —— ui/ 目录静态资源（白名单） */
function handleStatic(res, pathname) {
  const file = pathname === '/' ? 'index.html' : pathname.slice(1);
  if (!STATIC_FILES.has(pathname)) return sendJson(res, 404, { error: 'Not Found' });
  const full = path.join(__dirname, file);
  fs.readFile(full, (err, data) => {
    if (err) return sendJson(res, 404, { error: '文件不存在: ' + file });
    res.writeHead(200, { 'Content-Type': MIME[path.extname(full)] || 'text/plain' });
    res.end(data);
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const p = url.pathname;
  if (req.method === 'GET' && (p === '/' || STATIC_FILES.has(p))) return handleStatic(res, p);
  if (req.method === 'GET' && p === '/api/config') return handleConfig(res);
  if (req.method === 'GET' && p === '/api/list') return handleList(res, req.url);
  if (req.method === 'GET' && p === '/api/outputs') return handleOutputs(res, req.url);
  if (req.method === 'GET' && p === '/api/file') return handleFile(res, req.url);
  if (req.method === 'POST' && p === '/api/export') return handleExport(req, res);
  if (req.method === 'POST' && p === '/api/upload') return handleUpload(req, res, req.url);
  return sendJson(res, 404, { error: '未知接口: ' + p });
});

server.listen(PORT, HOST, () => {
  console.log(`FIT 转换器已启动: http://${HOST}:${PORT}`);
  console.log(`jar: ${JAR} ${fs.existsSync(JAR) ? '(存在)' : '(缺失，请先 mvn package)'}`);
});
