'use strict';

const $ = (id) => document.getElementById(id);

const sourceFileInput = $('sourceFile');
const outputInput = $('outputDir');
const convertBtn = $('convertBtn');
const logEl = $('log');
const outputsEl = $('outputs');
const jarStatusEl = $('jarStatus');
const pickFileBtn = $('pickFileBtn');
const filePicker = $('filePicker');

const dialog = $('dialog');
const dialogPath = $('dialogPath');
const dialogList = $('dialogList');
const goUp = $('goUp');
const dialogOk = $('dialogOk');
const dialogCancel = $('dialogCancel');

let dialogTarget = null; // 当前对话框要回填的输入框
let dialogCurrentPath = '';
let dialogParentPath = '';

/** 启动：读取配置，设置默认路径，刷新输出列表 */
async function init() {
  try {
    const res = await fetch('/api/config');
    const cfg = await res.json();
    if (!sourceFileInput.value) sourceFileInput.value = cfg.defaultSourceFile;
    if (!outputInput.value) outputInput.value = cfg.defaultOutput;
    if (!cfg.jarExists) {
      jarStatusEl.textContent = '警告：未找到 ' + cfg.jar + '，请先在项目根目录执行 mvn package';
      jarStatusEl.className = 'status warn';
    } else if (!cfg.defaultSourceExists) {
      jarStatusEl.textContent = '提示：默认源文件不存在，请通过"浏览…"选择你的华为导出 JSON（motion path detail data.json）';
      jarStatusEl.className = 'status warn';
    } else {
      jarStatusEl.textContent = '环境正常：jar 已就绪';
      jarStatusEl.className = 'status ok';
    }
    refreshOutputs();
  } catch (e) {
    jarStatusEl.textContent = '无法连接服务: ' + e.message;
    jarStatusEl.className = 'status warn';
  }
}

/** 日志输出 */
function appendLog(text, stream) {
  const span = document.createElement('span');
  span.textContent = text;
  span.className = stream === 'stderr' ? 'err' : 'out';
  logEl.appendChild(span);
  logEl.scrollTop = logEl.scrollHeight;
}

/** 清空日志 */
function clearLog() {
  logEl.textContent = '';
}

/** 执行整包导出转换：POST /api/export，fetch 流式解析 SSE */
async function runExport() {
  const sourceFile = sourceFileInput.value.trim();
  const outputDir = outputInput.value.trim();
  if (!sourceFile || !outputDir) {
    appendLog('请先填写源文件和输出目录\n', 'stderr');
    return;
  }

  clearLog();
  appendLog(`源文件: ${sourceFile}\n输出目录: ${outputDir}\n\n`, 'stdout');
  convertBtn.disabled = true;
  convertBtn.textContent = '转换中…';

  try {
    const res = await fetch('/api/export', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sourceFile, outputDir }),
    });
    if (!res.ok || !res.body) {
      const err = await res.json().catch(() => ({}));
      appendLog((err.error || `请求失败 (${res.status})`) + '\n', 'stderr');
      return;
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buf = '';
    let exitCode = null;

    // 逐行解析 SSE: "event: xxx\ndata: {...}\n\n"
    const handleLine = (line) => {
      if (line.startsWith('data: ')) {
        try {
          const evt = JSON.parse(line.slice(6));
          if (evt.stream) {
            appendLog(evt.text, evt.stream);
          } else if (evt.code !== undefined) {
            exitCode = evt.code;
          }
        } catch {
          /* 忽略无法解析的行 */
        }
      }
    };

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buf += decoder.decode(value, { stream: true });
      const lines = buf.split('\n');
      buf = lines.pop();
      lines.forEach(handleLine);
    }
    if (buf.trim()) handleLine(buf);

    const ok = exitCode === 0;
    appendLog(`\n${ok ? '✔ 转换完成' : '✘ 转换异常退出，退出码 ' + exitCode}\n`, ok ? 'stdout' : 'stderr');
    refreshOutputs();
  } catch (e) {
    appendLog('请求失败: ' + e.message + '\n', 'stderr');
  } finally {
    convertBtn.disabled = false;
    convertBtn.textContent = '开始转换';
  }
}

/** 刷新输出文件列表 */
async function refreshOutputs() {
  const outputDir = outputInput.value.trim();
  if (!outputDir) return;
  outputsEl.textContent = '';
  try {
    const res = await fetch('/api/outputs?path=' + encodeURIComponent(outputDir));
    const data = await res.json();
    if (data.files.length === 0) {
      const li = document.createElement('li');
      li.className = 'empty';
      li.textContent = '暂无 .fit 文件';
      outputsEl.appendChild(li);
      return;
    }
    data.files.forEach((f) => {
      const li = document.createElement('li');
      const a = document.createElement('a');
      a.href = '/api/file?path=' + encodeURIComponent(f.path);
      a.textContent = f.name;
      li.appendChild(a);
      const size = document.createElement('span');
      size.className = 'size';
      size.textContent = formatSize(f.size);
      li.appendChild(size);
      outputsEl.appendChild(li);
    });
  } catch (e) {
    /* 静默失败 */
  }
}

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / 1024 / 1024).toFixed(1) + ' MB';
}

/* ---------------- 源文件选择：Finder 原生对话框 + 上传 ---------------- */

function setStatus(text, cls) {
  jarStatusEl.textContent = text;
  jarStatusEl.className = 'status ' + cls;
}

pickFileBtn.addEventListener('click', () => filePicker.click());

filePicker.addEventListener('change', async () => {
  const file = filePicker.files[0];
  filePicker.value = ''; // 允许下次再选同一文件
  if (!file) return;
  setStatus('正在上传 ' + file.name + ' …', 'warn');
  try {
    const res = await fetch('/api/upload?name=' + encodeURIComponent(file.name), {
      method: 'POST',
      body: file,
    });
    const data = await res.json();
    if (data.error) {
      setStatus('上传失败: ' + data.error, 'warn');
      return;
    }
    sourceFileInput.value = data.path;
    setStatus('已就绪: ' + data.path, 'ok');
  } catch (e) {
    setStatus('上传失败: ' + e.message, 'warn');
  }
});

/* ---------------- 目录选择对话框 ---------------- */

async function openDialog(targetInput) {
  dialogTarget = targetInput;
  dialog.classList.remove('hidden');
  await loadDir(targetInput.value.trim() || '/');
}

async function loadDir(dir) {
  dialogList.textContent = '';
  try {
    const res = await fetch('/api/list?path=' + encodeURIComponent(dir));
    const data = await res.json();
    if (data.error) {
      const li = document.createElement('li');
      li.className = 'empty';
      li.textContent = data.error;
      dialogList.appendChild(li);
      return;
    }
    dialogCurrentPath = data.path;
    dialogParentPath = data.parent;
    dialogPath.textContent = data.path;
    dialogPath.title = data.path;
    data.items.forEach((item) => {
      const li = document.createElement('li');
      li.className = item.isDir ? 'dir' : 'file';
      li.textContent = item.isDir ? '[目录] ' + item.name : item.name;
      if (item.isDir) {
        li.addEventListener('click', () => loadDir(item.path));
      }
      dialogList.appendChild(li);
    });
  } catch (e) {
    const li = document.createElement('li');
    li.className = 'empty';
    li.textContent = '加载失败: ' + e.message;
    dialogList.appendChild(li);
  }
}

function closeDialog() {
  dialog.classList.add('hidden');
  dialogTarget = null;
}

/* ---------------- 事件绑定 ---------------- */

document.querySelectorAll('[data-target]').forEach((btn) => {
  btn.addEventListener('click', () => openDialog(outputInput));
});
goUp.addEventListener('click', () => {
  if (dialogParentPath) loadDir(dialogParentPath);
});
dialogOk.addEventListener('click', () => {
  if (dialogTarget && dialogCurrentPath) {
    dialogTarget.value = dialogCurrentPath;
    if (dialogTarget === outputInput) refreshOutputs();
    closeDialog();
  }
});
dialogCancel.addEventListener('click', closeDialog);
convertBtn.addEventListener('click', runExport);
outputInput.addEventListener('change', refreshOutputs);

init();
