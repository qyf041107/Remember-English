// 生成单词变形映射表：app/src/main/assets/word_forms.json（变形词 → 原形）
// 用途：搜索复数/时态词（went、wolves）时显示原形（用户 2026-09-08 要求）
// 规则变形 + 常用不规则动词表；只覆盖考研词表内的词
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const dict = JSON.parse(readFileSync(resolve(root, 'app/src/main/assets/words_kaoyan.json'), 'utf8'));
const words = new Set(dict.words.map((w) => w.word));

// 常用不规则动词/名词变形（base → 额外变形）
const IRREGULAR = {
  be: ['was', 'were', 'been', 'being', 'am', 'is', 'are'],
  have: ['had', 'having', 'has'],
  do: ['did', 'done', 'doing', 'does'],
  go: ['went', 'gone', 'going', 'goes'],
  say: ['said', 'saying', 'says'],
  get: ['got', 'gotten', 'getting', 'gets'],
  make: ['made', 'making', 'makes'],
  know: ['knew', 'known', 'knowing', 'knows'],
  think: ['thought', 'thinking', 'thinks'],
  take: ['took', 'taken', 'taking', 'takes'],
  see: ['saw', 'seen', 'seeing', 'sees'],
  come: ['came', 'coming', 'comes'],
  find: ['found', 'finding', 'finds'],
  give: ['gave', 'given', 'giving', 'gives'],
  tell: ['told', 'telling', 'tells'],
  feel: ['felt', 'feeling', 'feels'],
  become: ['became', 'becoming', 'becomes'],
  leave: ['left', 'leaving', 'leaves'],
  put: ['put', 'putting', 'puts'],
  mean: ['meant', 'meaning', 'means'],
  keep: ['kept', 'keeping', 'keeps'],
  let: ['let', 'letting', 'lets'],
  begin: ['began', 'begun', 'beginning', 'begins'],
  show: ['showed', 'shown', 'showing', 'shows'],
  hear: ['heard', 'hearing', 'hears'],
  run: ['ran', 'run', 'running', 'runs'],
  write: ['wrote', 'written', 'writing', 'writes'],
  sit: ['sat', 'sitting', 'sits'],
  stand: ['stood', 'standing', 'stands'],
  lose: ['lost', 'losing', 'loses'],
  pay: ['paid', 'paying', 'pays'],
  meet: ['met', 'meeting', 'meets'],
  speak: ['spoke', 'spoken', 'speaking', 'speaks'],
  read: ['read'],
  spend: ['spent', 'spending', 'spends'],
  grow: ['grew', 'grown', 'growing', 'grows'],
  win: ['won', 'winning', 'wins'],
  buy: ['bought', 'buying', 'buys'],
  send: ['sent', 'sending', 'sends'],
  fall: ['fell', 'fallen', 'falling', 'falls'],
  raise: ['rose', 'risen', 'rising', 'raises'],
  sell: ['sold', 'selling', 'sells'],
  wear: ['wore', 'worn', 'wearing', 'wears'],
  break: ['broke', 'broken', 'breaking', 'breaks'],
  bring: ['brought', 'bringing', 'brings'],
  build: ['built', 'building', 'builds'],
  catch: ['caught', 'catching', 'catches'],
  choose: ['chose', 'chosen', 'choosing', 'chooses'],
  cost: ['cost', 'costing', 'costs'],
  cut: ['cut', 'cutting', 'cuts'],
  draw: ['drew', 'drawn', 'drawing', 'draws'],
  drive: ['drove', 'driven', 'driving', 'drives'],
  eat: ['ate', 'eaten', 'eating', 'eats'],
  fly: ['flew', 'flown', 'flying', 'flies'],
  forget: ['forgot', 'forgotten', 'forgetting', 'forgets'],
  forgive: ['forgave', 'forgiven'],
  freeze: ['froze', 'frozen', 'freezing', 'freezes'],
  hide: ['hid', 'hidden', 'hiding', 'hides'],
  hold: ['held', 'holding', 'holds'],
  hurt: ['hurt', 'hurting', 'hurts'],
  lead: ['led', 'leading', 'leads'],
  learn: ['learnt', 'learned', 'learning', 'learns'],
  lie: ['lay', 'lain', 'lying', 'lies'],
  ride: ['rode', 'ridden', 'riding', 'rides'],
  ring: ['rang', 'rung', 'ringing', 'rings'],
  rise: ['rose', 'risen', 'rising', 'rises'],
  seek: ['sought', 'seeking', 'seeks'],
  shake: ['shook', 'shaken', 'shaking', 'shakes'],
  shoot: ['shot', 'shooting', 'shoots'],
  shut: ['shut', 'shutting', 'shuts'],
  sing: ['sang', 'sung', 'singing', 'sings'],
  sleep: ['slept', 'sleeping', 'sleeps'],
  spread: ['spread', 'spreading', 'spreads'],
  steal: ['stole', 'stolen', 'stealing', 'steals'],
  strike: ['struck', 'striking', 'strikes'],
  sweep: ['swept', 'sweeping', 'sweeps'],
  swim: ['swam', 'swum', 'swimming', 'swims'],
  teach: ['taught', 'teaching', 'teaches'],
  tear: ['tore', 'torn', 'tearing', 'tears'],
  throw: ['threw', 'thrown', 'throwing', 'throws'],
  understand: ['understood', 'understanding', 'understands'],
  wake: ['woke', 'woken', 'waking', 'wakes'],
  fight: ['fought', 'fighting', 'fights'],
  bite: ['bit', 'bitten', 'biting', 'bites'],
  blow: ['blew', 'blown', 'blowing', 'blows'],
  burn: ['burnt', 'burned', 'burning', 'burns'],
  dig: ['dug', 'digging', 'digs'],
  feed: ['fed', 'feeding', 'feeds'],
  hang: ['hung', 'hanged', 'hanging', 'hangs'],
  lay: ['laid', 'laying', 'lays'],
  mistake: ['mistook', 'mistaken', 'mistaking'],
  child: ['children'],
  man: ['men'],
  woman: ['women'],
  person: ['people'],
  foot: ['feet'],
  tooth: ['teeth'],
  mouse: ['mice'],
  life: ['lives'],
  leaf: ['leaves'],
  knife: ['knives'],
  wife: ['wives'],
  self: ['selves'],
};

/** 规则变形猜测 */
function regularForms(w) {
  const out = new Set();
  if (/(s|x|z|ch|sh)$/.test(w)) out.add(w + 'es');
  else if (/[^aeiou]y$/.test(w)) out.add(w.slice(0, -1) + 'ies');
  else out.add(w + 's');
  if (/e$/.test(w)) {
    out.add(w + 'd');
    out.add(w.slice(0, -1) + 'ing');
  } else {
    out.add(w + 'ed');
    out.add(w + 'ing');
    // 极短 CVC 词尾双写：run→running、stop→stopped（≤4 字母，避免 visit 之类的误双写）
    if (w.length <= 4 && /[aeiou][bcdfgklmnprstvz]$/.test(w)) {
      out.add(w + w.slice(-1) + 'ed');
      out.add(w + w.slice(-1) + 'ing');
    }
  }
  return out;
}

const formToBase = new Map();
function add(form, base) {
  if (form && form !== base && /^[a-z][a-z'-]*$/.test(form) && !formToBase.has(form)) {
    formToBase.set(form, base);
  }
}

for (const w of words) {
  for (const f of regularForms(w)) add(f, w);
}
for (const [base, forms] of Object.entries(IRREGULAR)) {
  if (!words.has(base)) continue; // 只映射词表内的词
  for (const f of forms) add(f, base);
}

const out = Object.fromEntries([...formToBase.entries()].sort(([a], [b]) => (a < b ? -1 : 1)));
writeFileSync(resolve(root, 'app/src/main/assets/word_forms.json'), JSON.stringify(out), 'utf8');
console.log(`变形表生成完毕：${Object.keys(out).length} 个变形词`);
