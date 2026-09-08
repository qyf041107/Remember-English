// 生成考研词组/短语 assets：app/src/main/assets/word_phrases.json
// 数据源：ECDICT（https://github.com/skywind3000/ECDICT，MIT 许可）StarDict 版
// 筛选口径（用户 2026-09-08 要求"高频考频词组"）：构成词全部属于考研词表
// 或基础功能词，按词组长度取前 2500 条
// 用法：node tools/build-phrases.mjs [stardict 目录]
import { readFileSync, writeFileSync, readdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const defaultDir = resolve(process.env.LOCALAPPDATA ?? '.', 'tmp-dl', 'ecdict', 'stardict-ecdict-2.4.2');
const dir = process.argv[2] ?? defaultDir;
const outPath = resolve(root, 'app/src/main/assets/word_phrases.json');

const dict = JSON.parse(readFileSync(resolve(root, 'app/src/main/assets/words_kaoyan.json'), 'utf8'));
const kaoyan = new Set(dict.words.map((w) => w.word));
// 真题词频（词组按构成词词频之和排序，越常用越靠前）
const freq = JSON.parse(readFileSync(resolve(root, 'app/src/main/assets/word_freq.json'), 'utf8'));

// 词组中允许出现的功能词（多为虚词，不在考纲单词表里）
const STOPWORDS = new Set([
  'a', 'an', 'the', 'of', 'to', 'in', 'on', 'for', 'with', 'at', 'by', 'up',
  'down', 'out', 'off', 'into', 'onto', 'over', 'and', 'or', 'nor', 'but',
  'as', 'from', 'about', 'be', 'is', 'are', 'was', 'were', 'been', 'do',
  'does', 'did', 'one', "one's", 'one’s', 'it', 'its', 'no', 'not', 'so',
  'such', 'that', 'this', 'than', 'then', 'there', 'here', 'what', 'which',
  'who', 'whom', 'how', 'when', 'where', 'if', 'all', 'any', 'each', 'other',
  'one another', 'per', 'via', 'you', 'your', 'i', 'me', 'my', 'we', 'us',
  'our', 'they', 'them', 'he', 'she', 'his', 'her', 'him', 'am',
]);

// ---- 解析 StarDict idx：word\0 + offset(4B BE) + size(4B BE) ----
const idxName = readdirSync(dir).find((f) => f.endsWith('.idx'));
const dictName = readdirSync(dir).find((f) => f.endsWith('.dict'));
if (!idxName || !dictName) throw new Error(`目录缺少 .idx/.dict：${dir}`);
const idx = readFileSync(resolve(dir, idxName));
const dic = readFileSync(resolve(dir, dictName));

const phrases = new Map(); // phrase → meanings[]
let p = 0;
while (p < idx.length) {
  let end = p;
  while (end < idx.length && idx[end] !== 0) end++;
  const word = idx.toString('utf8', p, end);
  const offset = idx.readUInt32BE(end + 1);
  const size = idx.readUInt32BE(end + 5);
  p = end + 9;

  // 只看词组：含空格、纯字母/空格/连字符/撇号，且无游离连字符
  if (!word.includes(' ')) continue;
  if (!/^[A-Za-z][A-Za-z' -]*$/.test(word)) continue;
  if (/(^|\s)-|-(\s|$)/.test(word)) continue;
  if (word.length > 28) continue;
  const lower = word.toLowerCase();

  // 构成词全部属于考研词表或功能词（保证"考纲内词组"）
  const tokens = lower.split(/[\s-]+/).filter(Boolean);
  if (tokens.length < 2 || tokens.length > 4) continue;
  if (tokens.some((t) => t.length === 1 && !STOPWORDS.has(t))) continue;
  const ok = tokens.every((t) => kaoyan.has(t) || STOPWORDS.has(t) || kaoyan.has(t.replace(/'s$/, '')));
  if (!ok) continue;
  // 排口语/俚语式条目：功能词过多，或首尾都是功能词（如 "and what have you"）
  const swCount = tokens.filter((t) => STOPWORDS.has(t)).length;
  if (swCount >= 3 || (STOPWORDS.has(tokens[0]) && STOPWORDS.has(tokens[tokens.length - 1]))) continue;

  // 释义：dict 区文本按行取中文释义（滤音标/方括号标记/全角字母数字行）
  const text = dic.toString('utf8', offset, offset + size);
  const meanings = text
    .split(/\r?\n/)
    .map((s) => s.trim())
    .filter((s) => s.length >= 2 && s.length <= 80)
    .filter((s) => !/^[*(\[【]/.test(s))
    .filter((s) => !/[Ａ-Ｚａ-ｚ０-９]/.test(s))
    .filter((s) => /[一-鿿]/.test(s))
    .slice(0, 3);
  if (meanings.length === 0) continue;
  if (!phrases.has(lower)) phrases.set(lower, { word: lower, meanings });
}

// 排序：构成词真题词频之和（功能词给基准值），越常见越靠前；取前 2500 条控制包体
const list = [...phrases.values()]
  .map((it) => ({
    ...it,
    rank: it.word
      .split(/[\s-]+/)
      .reduce((sum, t) => sum + (STOPWORDS.has(t) ? 800 : freq[t] ?? 0), 0),
  }))
  .sort((a, b) => b.rank - a.rank || (a.word < b.word ? -1 : 1))
  .slice(0, 2500)
  .map(({ rank, ...rest }) => rest);

writeFileSync(outPath, JSON.stringify(list), 'utf8');
console.log(`词组生成完毕：候选 ${phrases.size} → 输出 ${list.length} 条`);
console.log(`输出：${outPath}`);
