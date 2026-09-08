// 生成考研真题词频 assets：app/src/main/assets/word_freq.json
// 数据源：exam-data/NETEMVocabulary（CC BY-NC-SA 4.0，来源与许可见 README.md）
// 用法：node tools/build-freq.mjs [源文件路径]
// 默认源：%LOCALAPPDATA%/tmp-dl/netem_full_list.json
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const sourcePath = process.argv[2]
  ?? resolve(process.env.LOCALAPPDATA ?? '.', 'tmp-dl', 'netem_full_list.json');
const outPath = resolve(root, 'app/src/main/assets/word_freq.json');

const raw = JSON.parse(readFileSync(sourcePath, 'utf8'));
const table = raw['5530考研词汇词频排序表'];
if (!Array.isArray(table)) {
  throw new Error(`源数据格式不对：找不到 "5530考研词汇词频排序表" 数组（${sourcePath}）`);
}

/** 拆分"其他拼写"变体（可能是 /、,、空格等分隔） */
function variants(cell) {
  if (typeof cell !== 'string') return [];
  return cell
    .split(/[\/、,，;；\s]+/)
    .map((w) => w.trim().toLowerCase())
    .filter((w) => /^[a-z][a-z'-]*$/.test(w));
}

const freq = new Map(); // 小写词 → 词频（保持词频降序插入）
let entryCount = 0;
for (const row of table) {
  const count = row['词频'];
  const headword = String(row['单词'] ?? '').trim().toLowerCase();
  if (typeof count !== 'number' || !/^[a-z][a-z'-]*$/.test(headword)) continue;
  entryCount++;
  if (!freq.has(headword)) freq.set(headword, count);
  for (const v of variants(row['其他拼写'])) {
    if (!freq.has(v)) freq.set(v, count);
  }
}

const out = Object.fromEntries(freq);
mkdirSync(dirname(outPath), { recursive: true });
writeFileSync(outPath, JSON.stringify(out), 'utf8');
console.log(`词频表生成完毕：${entryCount} 个词条 → ${Object.keys(out).length} 个词（含变体）`);
console.log(`输出：${outPath}`);
