'use strict'

const { app, BrowserWindow, Menu, shell, session, dialog, nativeImage } = require('electron')
const path = require('path')
const fs = require('fs')
const os = require('os')
const { startServer, checkBackend } = require('./server')

const APP_TITLE = '校园车辆出入登记管理系统'
const DEFAULT_SERVER = 'http://192.144.235.177:8088'
const CONFIG_VERSION = 2
const LEGACY_DEFAULT_SERVERS = new Set(['http://127.0.0.1:8080', 'http://localhost:8080'])
const isSelftest = process.argv.includes('--selftest')

let mainWindow = null
let localServer = null

/* ------------------------------ 后端地址配置 ------------------------------ */
function readConfig() {
  const file = path.join(app.getPath('userData'), 'config.json')
  let cfg = {}
  try {
    if (fs.existsSync(file)) cfg = JSON.parse(fs.readFileSync(file, 'utf8')) || {}
  } catch (e) {
    cfg = {}
  }
  const argServer = (process.argv.find((a) => a.startsWith('--server=')) || '').split('=')[1]
  const explicitServer = (argServer || process.env.CAMPUS_SERVER || '').trim()
  const savedServer = (cfg.server || '').trim().replace(/\/+$/, '')
  const savedIsLegacy = LEGACY_DEFAULT_SERVERS.has(savedServer.toLowerCase())
  // 旧版本会把默认值 127.0.0.1:8080 写进 config.json，导致升级后仍连本机。
  // 没有 configVersion 的旧配置自动迁移到服务器地址；用户手动改过的地址保留。
  const needsMigration = !savedServer || (savedIsLegacy && cfg.configVersion !== CONFIG_VERSION)
  const server = (explicitServer || (needsMigration ? DEFAULT_SERVER : savedServer))
    .trim()
    .replace(/\/+$/, '')

  // 首次运行或旧配置迁移时落盘；命令行/环境变量临时覆盖不写盘。
  if (!explicitServer) {
    try {
      if (!fs.existsSync(file) || cfg.configVersion !== CONFIG_VERSION || cfg.server !== server) {
        fs.writeFileSync(file, JSON.stringify({ configVersion: CONFIG_VERSION, server }, null, 2), 'utf8')
      }
    } catch (e) {
      /* 忽略只读目录 */
    }
  }
  return { server, file }
}

/* ------------------------------ 下载（Excel 导出） ------------------------------ */
function uniquePath(p) {
  if (!fs.existsSync(p)) return p
  const ext = path.extname(p)
  const base = p.slice(0, p.length - ext.length)
  let i = 2
  while (fs.existsSync(`${base} (${i})${ext}`)) i++
  return `${base} (${i})${ext}`
}

function setupDownloads() {
  session.defaultSession.on('will-download', (event, item) => {
    const name = item.getFilename() || 'campus-download'
    const savePath = uniquePath(path.join(app.getPath('downloads'), name))
    item.setSavePath(savePath)
    item.once('done', (e, state) => {
      if (state === 'completed' && mainWindow && !mainWindow.isDestroyed()) {
        const r = dialog.showMessageBoxSync(mainWindow, {
          type: 'info',
          title: '导出完成',
          message: `文件已保存到：\n${savePath}`,
          buttons: ['打开所在文件夹', '关闭'],
          defaultId: 1,
          cancelId: 1,
          noLink: true
        })
        if (r === 0) shell.showItemInFolder(savePath)
      }
    })
  })
}

/* ------------------------------ 权限（摄像头扫码） ------------------------------ */
function setupPermissions(localOrigin) {
  const s = session.defaultSession
  s.setPermissionRequestHandler((wc, permission, callback) => {
    if (permission === 'media') return callback(true) // 门岗扫码需要摄像头
    if (permission === 'clipboard-read' || permission === 'clipboard-sanitized-write' || permission === 'notifications') {
      return callback(true)
    }
    return callback(false)
  })
  s.setPermissionCheckHandler((wc, permission) => {
    if (!localOrigin) return permission === 'media'
    const url = (wc && wc.getURL ? wc.getURL() : '') || ''
    const sameOrigin = url.startsWith(localOrigin) || url.startsWith('devtools://')
    if (!sameOrigin) return false
    return permission === 'media' || permission === 'clipboard-sanitized-write' || permission === 'clipboard-read'
  })
}

/* ------------------------------ 主窗口 ------------------------------ */
function createWindow(origin, backendOk) {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1080,
    minHeight: 680,
    show: false,
    title: APP_TITLE,
    backgroundColor: '#f4f7fb',
    autoHideMenuBar: true,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      spellcheck: false,
      webSecurity: true,
      backgroundThrottling: false
    }
  })

  mainWindow.once('ready-to-show', () => {
    mainWindow.show()
    mainWindow.focus()
  })

  mainWindow.on('page-title-updated', (e) => {
    e.preventDefault()
    mainWindow.setTitle(APP_TITLE)
  })

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:\/\//i.test(url)) shell.openExternal(url)
    return { action: 'deny' }
  })

  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (!url.startsWith(origin)) {
      event.preventDefault()
      if (/^https?:\/\//i.test(url)) shell.openExternal(url)
    }
  })

  // F12 / Ctrl+Shift+I 开发者工具，Ctrl+R / F5 刷新
  mainWindow.webContents.on('before-input-event', (event, input) => {
    if (input.type !== 'keyDown') return
    const key = (input.key || '').toLowerCase()
    if (key === 'f12' || (input.control && input.shift && key === 'i')) {
      mainWindow.webContents.toggleDevTools()
      event.preventDefault()
    } else if (key === 'f5' || (input.control && key === 'r')) {
      mainWindow.webContents.reload()
      event.preventDefault()
    }
  })

  mainWindow.on('closed', () => {
    mainWindow = null
  })

  const url = backendOk ? `${origin}/` : `${origin}/__offline`
  mainWindow.loadURL(url)
}

/* ------------------------------ 自检模式 ------------------------------ */
async function runSelftest(origin, target) {
  const http = require('http')
  const get = (p) =>
    new Promise((resolve) => {
      const req = http.get(origin + p, { timeout: 5000 }, (res) => {
        let body = ''
        res.setEncoding('utf8')
        res.on('data', (c) => { body += c })
        res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body }))
      })
      req.on('timeout', () => req.destroy(new Error('timeout')))
      req.on('error', (err) => resolve({ status: 0, error: err.message, body: '' }))
    })

  const index = await get('/index.html')
  const health = await get('/api/health')
  const offline = await get('/__offline')
  const status = await get('/__status')

  const m = index.body.match(/\.\/(assets\/[^"']+\.js)/)
  const asset = m ? await get('/' + m[1]) : { status: 0, body: '' }

  // 原始路径（不做 URL 归一化）测试目录穿越
  const raw = (rawPath) =>
    new Promise((resolve) => {
      const req = http.request({ host: '127.0.0.1', port: Number(new URL(origin).port), path: rawPath, method: 'GET' }, (res) => {
        let body = ''
        res.setEncoding('utf8')
        res.on('data', (c) => { body += c })
        res.on('end', () => resolve({ status: res.statusCode, body }))
      })
      req.on('error', (err) => resolve({ status: 0, body: err.message }))
      req.end()
    })
  const traversal = await raw('/assets/../../../../package.json')
  const fallback = await get('/login')

  // 通过本地代理登录一次，验证 POST 转发链路
  let loginCheck = { ok: false }
  try {
    const resp = await fetch(origin + '/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'admin', password: 'Admin@123' })
    })
    const json = await resp.json()
    loginCheck = { status: resp.status, code: json && json.code, hasToken: !!(json && json.data && (json.data.token || json.data.accessToken)) }
  } catch (e) {
    loginCheck = { error: e.message }
  }

  // 实际渲染页面并截图，收集渲染进程报错
  const shotPath = path.join(os.tmpdir(), 'campus-desktop-shot.png')
  const consoleErrors = []
  let render = { loaded: false }
  try {
    const w = new BrowserWindow({
      width: 1440,
      height: 900,
      show: false,
      webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true }
    })
    w.webContents.on('console-message', (e, level, message) => {
      if (level >= 2) consoleErrors.push(message)
    })
    await w.loadURL(origin + '/#/login')
    await new Promise((r) => setTimeout(r, 2500))
    const img = await w.webContents.capturePage()
    fs.writeFileSync(shotPath, img.toPNG())
    render = {
      loaded: true,
      title: w.webContents.getTitle(),
      text: (await w.webContents.executeJavaScript('document.body.innerText')).slice(0, 300),
      hasAppContent: await w.webContents.executeJavaScript('!!document.querySelector("#app") && document.querySelector("#app").children.length > 0'),
      consoleErrors: consoleErrors.slice(0, 5)
    }
    w.destroy()
  } catch (e) {
    render = { loaded: false, error: e.message }
  }

  let healthJson = null
  try { healthJson = JSON.parse(health.body) } catch (e) { healthJson = null }

  const result = {
    time: new Date().toISOString(),
    origin,
    target,
    indexHtml: { status: index.status, hasApp: index.body.indexOf('id="app"') >= 0 },
    asset: { path: m ? m[1] : null, status: asset.status, size: asset.body.length },
    proxyHealth: { status: health.status, code: healthJson && healthJson.code, status_text: healthJson && healthJson.data && healthJson.data.status },
    offlinePage: { status: offline.status, ok: offline.body.indexOf('后端服务暂未启动') >= 0 },
    statusApi: { status: status.status, body: status.body.slice(0, 200) },
    routeFallback: { status: fallback.status, isIndex: fallback.body.indexOf('id="app"') >= 0 },
    loginProxy: loginCheck,
    render,
    screenshot: shotPath,
    traversalBlocked: traversal.status !== 200 && traversal.body.indexOf('campus-vehicle-desktop') < 0,
    traversal: { status: traversal.status, leaked: traversal.body.indexOf('campus-vehicle-desktop') >= 0 },
    pass:
      index.status === 200 &&
      index.body.indexOf('id="app"') >= 0 &&
      asset.status === 200 &&
      offline.status === 200 &&
      fallback.status === 200 &&
      traversal.status !== 200 &&
      loginCheck.code === 0 &&
      !!(render && render.hasAppContent)
  }
  const file = path.join(os.tmpdir(), 'campus-desktop-selftest.json')
  fs.writeFileSync(file, JSON.stringify(result, null, 2), 'utf8')
  console.log('[selftest] ' + file)
  console.log(JSON.stringify(result, null, 2))
}

/* ------------------------------ 启动流程 ------------------------------ */
if (!app.requestSingleInstanceLock()) {
  app.quit()
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore()
      mainWindow.focus()
    }
  })

  app.whenReady().then(async () => {
    Menu.setApplicationMenu(null)
    app.setAppUserModelId('com.campus.vehicle.desktop')

    const { server: target } = readConfig()
    const root = path.join(__dirname, '..', 'app-dist')

    try {
      localServer = await startServer({ root, target })
    } catch (err) {
      dialog.showErrorBox(APP_TITLE, '本地服务启动失败：\n' + err.message)
      app.exit(1)
      return
    }

    setupPermissions(localServer.origin)
    setupDownloads()

    if (isSelftest) {
      await runSelftest(localServer.origin, target)
      await localServer.close()
      app.exit(0)
      return
    }

    const health = await checkBackend(target, 2500)
    createWindow(localServer.origin, health.ok)

    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) createWindow(localServer.origin, true)
    })
  })

  app.on('window-all-closed', () => {
    if (localServer) localServer.close()
    app.quit()
  })

  app.on('before-quit', () => {
    if (localServer) localServer.close()
  })
}
