'use strict'
// 生成 256x256 应用图标（纯 Node 实现，无第三方依赖）
const fs = require('fs')
const path = require('path')
const zlib = require('zlib')

const SIZE = 256
const SS = 4 // 超采样倍数，用于抗锯齿

function crc32(buf) {
  let table = crc32.table
  if (!table) {
    table = crc32.table = new Int32Array(256)
    for (let n = 0; n < 256; n++) {
      let c = n
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
      table[n] = c
    }
  }
  let crc = -1
  for (let i = 0; i < buf.length; i++) crc = (crc >>> 8) ^ table[(crc ^ buf[i]) & 0xff]
  return (crc ^ -1) >>> 0
}

function chunk(type, data) {
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length, 0)
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data])
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(body), 0)
  return Buffer.concat([len, body, crc])
}

function encodePng(width, height, rgba) {
  const raw = Buffer.alloc((width * 4 + 1) * height)
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0
    rgba.copy(raw, y * (width * 4 + 1) + 1, y * width * 4, (y + 1) * width * 4)
  }
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(width, 0)
  ihdr.writeUInt32BE(height, 4)
  ihdr[8] = 8
  ihdr[9] = 6
  ihdr[10] = 0
  ihdr[11] = 0
  ihdr[12] = 0
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0))
  ])
}

function roundRect(x, y, w, h, r) {
  return { x, y, w, h, r }
}

function inRoundRect(px, py, { x, y, w, h, r }) {
  const cx = Math.min(Math.max(px, x + r), x + w - r)
  const cy = Math.min(Math.max(py, y + r), y + h - r)
  const dx = px - cx
  const dy = py - cy
  return dx * dx + dy * dy <= r * r
}

function inCircle(px, py, cx, cy, r) {
  const dx = px - cx
  const dy = py - cy
  return dx * dx + dy * dy <= r * r
}

function lerp(a, b, t) {
  return a + (b - a) * t
}

// 单点取样：返回 [r,g,b,a] 0-255
function sample(px, py) {
  const S = SIZE
  // 圆角底板（渐变：左上深蓝 → 右下青蓝）
  if (!inRoundRect(px, py, roundRect(0, 0, S, S, 56))) return [0, 0, 0, 0]
  const t = (px + py) / (2 * S)
  let r = lerp(29, 14, t)
  let g = lerp(78, 165, t)
  let b = lerp(216, 233, t)

  const white = [255, 255, 255]
  const glass = [21, 62, 175, 255]
  const inRoof = inRoundRect(px, py, roundRect(72, 74, 112, 66, 26))
  const inBody = inRoundRect(px, py, roundRect(40, 106, 176, 72, 24))
  const inGlass =
    inRoundRect(px, py, roundRect(86, 86, 38, 40, 10)) || inRoundRect(px, py, roundRect(132, 86, 40, 40, 10))
  // 车窗（镂空为深蓝）
  if (inGlass && (inRoof || inBody)) return glass
  // 车顶 + 车身
  if (inRoof || inBody) return [...white, 255]
  // 车轮
  if (inCircle(px, py, 84, 182, 26) || inCircle(px, py, 172, 182, 26)) return [15, 40, 100, 255]
  if (inCircle(px, py, 84, 182, 12) || inCircle(px, py, 172, 182, 12)) return [226, 240, 255, 255]

  return [Math.round(r), Math.round(g), Math.round(b), 255]
}

const out = Buffer.alloc(SIZE * SIZE * 4)
for (let y = 0; y < SIZE; y++) {
  for (let x = 0; x < SIZE; x++) {
    let ar = 0
    let ag = 0
    let ab = 0
    let aa = 0
    for (let sy = 0; sy < SS; sy++) {
      for (let sx = 0; sx < SS; sx++) {
        const c = sample(x + (sx + 0.5) / SS, y + (sy + 0.5) / SS)
        const a = c[3] / 255
        ar += c[0] * a
        ag += c[1] * a
        ab += c[2] * a
        aa += c[3]
      }
    }
    const n = SS * SS
    const alpha = aa / n
    const idx = (y * SIZE + x) * 4
    if (alpha > 0) {
      out[idx] = Math.round((ar / n) / (alpha / 255))
      out[idx + 1] = Math.round((ag / n) / (alpha / 255))
      out[idx + 2] = Math.round((ab / n) / (alpha / 255))
    }
    out[idx + 3] = Math.round(alpha)
  }
}

const target = path.join(__dirname, 'icon.png')
fs.writeFileSync(target, encodePng(SIZE, SIZE, out))
console.log('icon written:', target, fs.statSync(target).size, 'bytes')
