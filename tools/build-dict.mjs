#!/usr/bin/env node
// 词库数据管线（CLAUDE.md 第七节）：下载 → 规范化 → 去重 → 写入 assets
// 数据来源：RealKai42/qwerty-learner 的 2025 版考研红宝书词库（GPL-3.0，README 与 App 关于页须标注）
// 用法：node tools/build-dict.mjs
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const SOURCES = [
  'https://raw.githubusercontent.com/RealKai42/qwerty-learner/master/public/dicts/2025KaoYanHongBaoShu.json',
  'https://gitee.com/RealKai/qwerty-learner/raw/master/public/dicts/2025KaoYanHongBaoShu.json',
]

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const outPath = path.join(root, 'app/src/main/assets/words_kaoyan.json')

async function fetchDict() {
  for (const url of SOURCES) {
    try {
      console.log(`下载 ${url}`)
      const res = await fetch(url)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return JSON.parse(await res.text())
    } catch (err) {
      console.warn(`  失败：${err.message}，尝试下一源`)
    }
  }
  throw new Error('所有下载源均失败，请检查网络后重试')
}

/** 兼容 word_list 数组或对象包裹两种结构，以及 trans 为数组或字符串两种字段形态 */
function toItems(raw) {
  const list = Array.isArray(raw) ? raw : (raw.word_list ?? raw.words ?? [])
  return list
    .map((item) => {
      const word = String(item.name ?? item.word ?? '').trim().toLowerCase()
      const trans = item.trans ?? item.translation ?? item.meanings ?? []
      const meanings = (Array.isArray(trans) ? trans : [trans])
        .map((s) => String(s).trim())
        .filter(Boolean)
      return {
        word,
        usphone: String(item.usphone ?? '').trim(),
        ukphone: String(item.ukphone ?? '').trim(),
        meanings,
      }
    })
    // 规范化（CLAUDE.md 第五节）：纯英文单词、长度≥2、去含数字/空格条目
    .filter((e) => /^[a-z][a-z'-]*$/.test(e.word) && e.word.length >= 2)
}

async function main() {
  const raw = await fetchDict()
  const items = toItems(raw)

  // 去重：保留首个（源顺序即红宝书顺序）
  const seen = new Set()
  const words = items.filter((e) => (seen.has(e.word) ? false : seen.add(e.word)))

  const output = {
    version: 1,
    source: 'RealKai42/qwerty-learner 2025KaoYanHongBaoShu (GPL-3.0)',
    generatedAt: new Date().toISOString(),
    count: words.length,
    words,
  }

  await mkdir(path.dirname(outPath), { recursive: true })
  await writeFile(outPath, JSON.stringify(output, null, 1), 'utf8')

  const noMeaning = words.filter((e) => e.meanings.length === 0).length
  console.log(`✓ 已写入 ${outPath}`)
  console.log(`  词数：${words.length}（去重前 ${items.length}，无释义 ${noMeaning}）`)
  console.log(`  示例：${JSON.stringify(words[0])}`)
}

main().catch((err) => {
  console.error(`✗ ${err.message}`)
  process.exit(1)
})
