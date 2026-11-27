'use strict'

/**
 * 桌面客户端的本地服务：
 *  1) 在 127.0.0.1 上以静态文件方式托管 app-dist（即 frontend/dist 的构建产物）；
 *  2) 把 /api/** 反向代理到 SpringBoot 后端，保持与浏览器里完全一致的请求方式；
 *  3) 提供 /__status 与 /__offline 供「后端未启动」时做联机提示。
 */
const http = require('http')
const https = require('https')
const fs = require('fs')
const path = require('path')
const { URL } = require('url')

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.htm': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.ico': 'image/x-icon',
  '.webp': 'image/webp',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.otf': 'font/otf',
  '.eot': 'application/vnd.ms-fontobject',
  '.map': 'application/json; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
  '.xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
}

const OFFLINE_HTML = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8" />
<title>等待后端服务</title>
<style>
  * { box-sizing: border-box; }
  body {
    margin: 0; height: 100vh; display: flex; align-items: center; justify-content: center;
    background: linear-gradient(160deg, #eef4ff 0%, #f7f9fc 55%, #eef7f2 100%);
    font-family: "Microsoft YaHei", "PingFang SC", system-ui, sans-serif; color: #1f2937;
  }
  .card {
    width: 560px; max-width: 88vw; padding: 40px 44px; background: #fff; border-radius: 18px;
    box-shadow: 0 18px 48px rgba(31, 62, 122, .12); text-align: center;
  }
  .badge {
    display: inline-flex; align-items: center; gap: 8px; padding: 6px 14px; border-radius: 999px;
    background: #fff4e5; color: #b45309; font-size: 13px; font-weight: 600;
  }
  .dot { width: 8px; height: 8px; border-radius: 50%; background: #f59e0b; animation: pulse 1.2s infinite; }
  @keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: .25; } }
  h1 { margin: 22px 0 10px; font-size: 22px; }
  p { margin: 6px 0; font-size: 14px; line-height: 1.9; color: #64748b; }
  code {
    background: #f1f5f9; padding: 2px 8px; border-radius: 6px; font-size: 13px; color: #0f172a;
    font-family: Consolas, "Courier New", monospace;
  }
  .steps { text-align: left; margin: 22px 0 6px; padding: 18px 22px; background: #f8fafc; border-radius: 12px; }
  .steps div { font-size: 13.5px; color: #475569; line-height: 2.1; }
  .target { margin-top: 16px; font-size: 12.5px; color: #94a3b8; word-break: break-all; }
  button {
    margin-top: 22px; padding: 11px 34px; border: 0; border-radius: 10px; cursor: pointer;
    background: #2563eb; color: #fff; font-size: 14px; font-weight: 600; font-family: inherit;
    transition: background .2s;
  }
  button:hover { background: #1d4ed8; }
  button:disabled { background: #94a3b8; cursor: default; }
</style>
</head>
<body>
  <div class="card">
    <span class="badge"><span class="dot"></span><span id="state">正在连接后端服务…</span></span>
    <h1>后端服务暂未启动</h1>
    <p>本程序只负责界面，数据读写由 SpringBoot 服务端完成。</p>
    <div class="steps">
      <div>1. 先双击项目根目录的 <code>启动系统.bat</code>，等待出现 <code>Started CampusVehicleApplication</code></div>
      <div>2. 确认 MySQL80 服务已运行（脚本会自动尝试启动）</div>
      <div>3. 服务端就绪后，本页面会自动进入系统，无需手动操作</div>
    </div>
    <div class="target">当前后端地址：<span id="target">-</span></div>
    <button id="retry" disabled>正在自动重试…</button>
  </div>
<script>
  var btn = document.getElementById('retry');
  var state = document.getElementById('state');
  var tries = 0;
  function check() {
    tries++;
    fetch('/__status?t=' + Date.now(), { cache: 'no-store' })
      .then(function (r) { return r.json(); })
      .then(function (d) {
        document.getElementById('target').textContent = d.target || '-';
        if (d.ok) {
          state.textContent = '已连接，正在进入系统…';
          btn.disabled = true;
          btn.textContent = '正在进入…';
          setTimeout(function () { location.replace('/'); }, 300);
          return;
        }
        throw new Error('offline');
      })
      .catch(function () {
        state.textContent = '等待连接（第 ' + tries + ' 次检测）';
        btn.disabled = false;
        btn.textContent = '立即重试';
      });
  }
  btn.addEventListener('click', function () { btn.disabled = true; btn.textContent = '检测中…'; check(); });
  check();
  setInterval(check, 3000);
</script>
</body>
</html>`

function sendJson(res, status, obj) {
  const body = Buffer.from(JSON.stringify(obj), 'utf8')
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': body.length,
    'Cache-Control': 'no-store'
  })
  res.end(body)
}

function proxyToBackend(req, res, target) {
  let t
  try {
    t = new URL(target)
  } catch (e) {
    return sendJson(res, 502, { code: 502, message: '后端地址配置有误：' + target, data: null })
  }
  const isHttps = t.protocol === 'https:'
  const mod = isHttps ? https : http
  const headers = Object.assign({}, req.headers)
  headers.host = t.host
  delete headers.connection
  delete headers['accept-encoding'] // 避免压缩流在本机再次解压带来的兼容问题

  const proxyReq = mod.request(
    {
      protocol: t.protocol,
      hostname: t.hostname,
      port: t.port || (isHttps ? 443 : 80),
      method: req.method,
      path: req.url,
      headers
    },
    (proxyRes) => {
      const outHeaders = Object.assign({}, proxyRes.headers)
      delete outHeaders.connection
      res.writeHead(proxyRes.statusCode || 502, outHeaders)
      proxyRes.pipe(res)
    }
  )

  proxyReq.setTimeout(180000, () => proxyReq.destroy(new Error('后端响应超时')))
  proxyReq.on('error', (err) => {
    if (res.headersSent) {
      res.end()
      return
    }
    sendJson(res, 502, {
      code: 502,
      message: '后端服务不可达（' + target + '）：' + err.message,
      data: null
    })
  })
  req.pipe(proxyReq)
}

function serveStatic(req, res, root) {
  const safeRoot = path.resolve(root)
  let urlPath
  try {
    urlPath = decodeURIComponent((req.url || '/').split('?')[0].split('#')[0])
  } catch (e) {
    urlPath = '/'
  }
  if (urlPath === '/' || urlPath === '') urlPath = '/index.html'

  let filePath = path.resolve(path.join(safeRoot, urlPath))
  if (filePath !== safeRoot && !filePath.startsWith(safeRoot + path.sep)) {
    res.writeHead(403, { 'Content-Type': 'text/plain; charset=utf-8' })
    return res.end('403 Forbidden')
  }

  let stat = null
  try {
    stat = fs.statSync(filePath)
  } catch (e) {
    stat = null
  }
  if (stat && stat.isDirectory()) {
    filePath = path.join(filePath, 'index.html')
    try {
      stat = fs.statSync(filePath)
    } catch (e) {
      stat = null
    }
  }
  // SPA：未命中的路径统一回落到 index.html（路由为 hash 模式，正常不会走到这里）
  if (!stat || !stat.isFile()) {
    filePath = path.join(safeRoot, 'index.html')
    try {
      stat = fs.statSync(filePath)
    } catch (e) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' })
      return res.end('404 Not Found')
    }
  }

  const ext = path.extname(filePath).toLowerCase()
  const headers = {
    'Content-Type': MIME[ext] || 'application/octet-stream',
    'Content-Length': stat.size,
    // 本地客户端更新频繁，不能让 Chromium 把旧 JS 按 immutable 缓存一年。
    'Cache-Control': 'no-cache'
  }
  res.writeHead(200, headers)
  if (req.method === 'HEAD') return res.end()
  fs.createReadStream(filePath).on('error', () => res.end()).pipe(res)
}

function checkBackend(target, timeoutMs = 2000) {
  return new Promise((resolve) => {
    let settled = false
    const done = (ok, detail) => {
      if (settled) return
      settled = true
      resolve({ ok, detail })
    }
    let t
    try {
      t = new URL('/api/health', target)
    } catch (e) {
      return done(false, '地址无效')
    }
    const isHttps = t.protocol === 'https:'
    const mod = isHttps ? https : http
    const req = mod.get(
      {
        protocol: t.protocol,
        hostname: t.hostname,
        port: t.port || (isHttps ? 443 : 80),
        path: t.pathname,
        timeout: timeoutMs,
        headers: { 'User-Agent': 'CampusVehicleDesktop' }
      },
      (res) => {
        let body = ''
        res.setEncoding('utf8')
        res.on('data', (c) => { body += c })
        res.on('end', () => done(res.statusCode >= 200 && res.statusCode < 400, 'HTTP ' + res.statusCode))
      }
    )
    req.on('timeout', () => req.destroy(new Error('timeout')))
    req.on('error', (err) => done(false, err.message))
  })
}

function createHandler({ root, getTarget }) {
  return function handler(req, res) {
    const url = req.url || '/'
    if (url.startsWith('/api/') || url === '/api') {
      return proxyToBackend(req, res, getTarget())
    }
    if (url === '/__status' || url.startsWith('/__status?')) {
      return checkBackend(getTarget(), 1500).then((r) => sendJson(res, 200, { ok: r.ok, detail: r.detail, target: getTarget() }))
    }
    if (url === '/__offline' || url.startsWith('/__offline?')) {
      const body = Buffer.from(OFFLINE_HTML, 'utf8')
      res.writeHead(200, {
        'Content-Type': 'text/html; charset=utf-8',
        'Content-Length': body.length,
        'Cache-Control': 'no-store'
      })
      return res.end(body)
    }
    return serveStatic(req, res, root)
  }
}

function listen(server, port) {
  return new Promise((resolve, reject) => {
    const onError = (err) => {
      server.removeListener('listening', onListening)
      reject(err)
    }
    const onListening = () => {
      server.removeListener('error', onError)
      resolve(server.address().port)
    }
    server.once('error', onError)
    server.once('listening', onListening)
    server.listen(port, '127.0.0.1')
  })
}

async function startServer({ root, target, preferredPort = 17650, tryCount = 20 }) {
  const server = http.createServer(createHandler({ root, getTarget: () => target }))
  server.keepAliveTimeout = 5000
  let port = 0
  for (let i = 0; i < tryCount; i++) {
    try {
      port = await listen(server, preferredPort + i)
      break
    } catch (err) {
      if (err.code !== 'EADDRINUSE' || i === tryCount - 1) throw err
    }
  }
  return {
    port,
    origin: `http://127.0.0.1:${port}`,
    close: () => new Promise((r) => server.close(() => r()))
  }
}

module.exports = { startServer, checkBackend, OFFLINE_HTML }
