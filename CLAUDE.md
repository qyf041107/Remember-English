# Remember-English 项目规范（实现锚点）

> **工作约定：每步实现前重读本文件，对照第一节需求与第六节验收清单，发现偏差立即向用户提出。**

## 一、项目定位与需求（用户原文，不得偏离）

1. 这是一个考研英语单词背记软件，旨在给考生一个简约的背单词软件
2. 背单词不再局限于5500词，而是可以自己拍照添加单词，并实时显示单词意思，可以添加到“我要背”里面。
3. 每天可以设置学习目标，如果当天任务没完成，发送推送窗口提醒完成。
4. 安卓平台
5. ui设计简约大方，清晰明显，易于操作。
6. 生成之后经我检阅没有问题之后，上传到我的GitHub库此文件夹里面。

## 二、范围边界

- **离线优先**：无后端、无账号，词库/词频/词形/词组全部打包进 assets；联网仅**三处**：① **联网查词兜底**——本地词库+词组未收录时调用有道公开接口（jsonapi，无 key），用户 2026-09-08 批准；② **云端手写识别（可选）**——拍照/相册静态图调百度智能云手写文字识别（`data/online/BaiduHandwritingClient.kt`），密钥由用户在"我的"页填入、DataStore 本地存储**不入仓库**，失败自动回退本地 ML Kit，用户 2026-09-08 批准；③ **联网拼写纠错**——本地与联网精确查**都失败**时调有道拼写建议（`data/online/SpellSuggestClient.kt`，无 key），用户 2026-09-16 批准；其余任何网络请求均不允许（INTERNET 权限已声明）
- **语音助手"打开应用"不做适配**（用户 2026-09-16 反馈"小爱同学唤不出"）：小爱同学对"打开X"**只做应用名匹配**，第三方应用**没有任何公开 API** 能注册读音别名、语音触发词或 App Actions（`res/xml/shortcuts.xml` + `android.app.shortcuts` 属 Google Assistant 体系，国行 HyperOS 不消费它）。故**不写**这类看起来能修实则无效的配置，只在 App 侧提供两样东西：① 两个 deep link（`rememberenglish://study` / `rememberenglish://add`）② "我的 → 语音打开"引导卡（一键打开小爱同学，`getLaunchIntentForPackage` 取启动 Intent 不硬编码 Activity 名；一键复制 deep link 供用户在小爱里建自定义指令）。这是唯一可靠路径，属平台限制而非配置问题
- UI 文案一律**中文**；界面克制：Material3 默认组件，**不加装饰性图片/动效**。动画只服务于交互反馈与可读性（行尾按钮点击回弹、细滚动条），不做炫技动效
- 技术栈（已与用户确认）：Kotlin + Jetpack Compose、ML Kit 离线 OCR、三键分数模型（用户 2026-09-07 由 SM-2 SRS 改定）、开源词库打包

## 三、锁定版本（升级需先修改此处并说明理由）

| 组件 | 版本 |
|---|---|
| JDK | 17 |
| Gradle | 8.13 |
| AGP | 8.7.3 |
| Kotlin | 2.0.21（Compose 编译器插件随 Kotlin 2.0） |
| Compose BOM | 2024.12.01 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |
| Room | 2.6.1 |
| WorkManager | 2.10.0 |
| DataStore | 1.1.1 |
| CameraX | 1.4.1 |
| ML Kit text-recognition（离线拉丁模型） | 16.0.1 |
| Hilt | 2.52 |

**版本号约定**：`versionName` 与即将推送的 git tag 一致（当前 `1.0.3`），`versionCode` 每次发版递增（当前 3）。此前两者长期停留在 `0.1.0`/`1`，导致 v1.0.1、v1.0.2 的 APK 安装后显示的版本号是 0.1.0——发版前记得同步改。CI 只按 tag 出 Release，不注入版本号，故只能手改。

## 四、代码结构约定

- 包根 `com.qyf.rememberenglish`，单模块 `:app`
- 分层：`ui/`（theme、navigation、study、library、mine、profile、add、components）、`data/`（db、repository、seed、settings、worker、notifier、ocr、online、di）、`domain/`（model、srs、select、search、ocr、usecase）
- 命名：`XxxScreen` / `XxxViewModel` / `XxxRepository` / `XxxDao` / `XxxEntity`
- 依赖只通过 `gradle/libs.versions.toml` 添加，新增库需说明理由
- **domain 层纯 Kotlin**（不 import android.*），保证可单测；分数模型常量集中在 `domain/srs/ScoreScheduler.kt`，禁止散落魔法数字

## 五、关键设计（实现依据）

### 数据（Room 3 表，当前 schema v3）
- `dict_word`：id、word(UNIQUE)、usphone、ukphone、meanings(JSON 文本)、source(0=内置红宝书/1=自定义/2=考纲内词组)、createdAt。**自定义词也进此表**（source 区分），"词库"页只显示 source=0 与 source=2
- `user_word`（我要背+背分）：wordId(FK UNIQUE)、addedAt、score(背会分数)、wrongCount、unclearCount、lastAnsweredAt(0=从未作答)、isSuspended、**isStarred**(星标，用户 2026-09-16)
- `answer_log`：userWordId、wordId、rating(0我不会/1不清楚/2我知道)、reviewedAt、prevScore、newScore、**wasStarred**(作答时星标快照)；今日背会数 = 当天 `rating != 0 且 wasStarred = 0` 的 **DISTINCT wordId** 数
  - **为什么存快照而不 JOIN `user_word` 现查**：否则"今天答了 5 个词，随手给其中 3 个打星"会让今日进度从 5 掉回 2（进度条倒退、提醒判断翻转）；也会把"左滑移出＝今日进度倒退"变成永久行为
- **迁移**：`AppDatabase` 当前 `version = 3`，`MIGRATION_2_3` 手写两条 `ALTER TABLE ... ADD COLUMN ... NOT NULL DEFAULT 0`。**保留** `fallbackToDestructiveMigrationFrom(1)`（只管 v1→v2），**不得**改成无条件 `fallbackToDestructiveMigration()`——会清空用户全部背词记录。`exportSchema = true`，`app/schemas/.../3.json` 必须随提交入库
- assets 另有：
  - `word_freq.json`（真题词频，小写词 → 出现次数），供 OCR 候选排序、搜索同档排序与详情页展示
  - `word_forms.json`（时态变形 → 原形，约 2.1 万条；规则变形 + 不规则动词表，仅考纲词），搜索变形词时同时显示原形（"went"的原形）
  - `word_phrases.json`（考纲内词组/短语 2500 条，源 ECDICT 过滤：所有单词都在考纲内，按真题词频降序取前 2500，source=2），可搜索、可加入"我要背"

### 分数模型（用户 2026-09-07 定义，替代原 SM-2 SRS）
- 三键记分：**我知道 +1 分｜不清楚 +0.5 分｜我不会 +0 分**；一单词分数 **满 5 分 = 已掌握**
- 筛选口径（"我要背"页）：新词 = 0 分且从未作答；学习中 = 已作答但未满 5 分（含答错 0 分的）；已掌握 = ≥5 分；星标 = 独立档（星标词不算已掌握，故不与"已掌握"重复）
- **「我要背」列表排序**（用户 2026-09-16 指定）：按添加时间**倒序**，新添加的在最上面。`UserWordDao.observeAllWithWord` 原先 SQL 是 `addedAt ASC` 而注释写的是"新添加在前"——**代码与注释本就矛盾**，本轮改 `DESC` 对齐
- **加权随机抽词**（`domain/select/StudyPicker.kt`，无固定队列）：权重 = `(1 + wrongCount + unclearCount) × (已掌握 ? 0.25 : 1)`——不会/不清楚过的词出现最勤，已掌握词 0.25 折一笔带过；尽量不与刚答过的词重复
- **星标**（用户 2026-09-16 选定最激进口径，`UserWord.isStarred`）三条语义：① **永不算已掌握**（`isMastered = !isStarred && score >= 5`，一处改动即让"我要背"筛选/我的页统计/背诵卡显示全部跟着对）② **不计入今日背会数**（`answer_log.wasStarred` 快照过滤）③ **抽中权重 ×3 且不受 0.25 折**（`ScoreConstants.STAR_PICK_WEIGHT`，`pickWeight` 里星标判定**必须先于**已掌握判定）
  - 星标是词卡属性，故**只对已在"我要背"的词存在**。行上点星标时若词还没在"我要背"，**先自动加入再打星**（一次点击=我要死磕这个词，Snackbar 提示）；**取消星标不会移出"我要背"**（移出是左滑的职责）
  - 星标词**不计入今日背会**会带来一个后果：未星标词数 < 每日目标时目标在数学上不可达。`DailyProgress.reachable` 标记此情形，今日页**只提示不阻断**（会话不被强行中断，用户仍可练星标词；`isDone` 不受影响，否则词少的用户会被误判"今日已完成"而再也收不到提醒）
- 学习卡片：先只显示英文，界面提示"如果不会，请点击屏幕"，点卡片显示释义；下方三键 我知道/我不会/不清楚；**作答后都停留展示释义**——答对 1.5 秒确认（用户 2026-09-10），答错/不清楚 3 秒看清（用户 2026-09-08），点击卡片可跳过（常量 `StudyViewModel.KNOW_WAIT_MS/WRONG_WAIT_MS`）
- 两个学习入口（用户 2026-09-07 指定）：① "今日"页**随机提问**按钮 → 加权随机会话（达成今日目标即结束）；② "我要背"列表**点单词** → 单词背诵卡（同一交互，单次记分后返回）；列表**左滑单词** → 移出我要背（Snackbar 可撤销）
- 每日目标 = 当天**背会词数**（当天 rating≠0 的不同单词数，默认 20 可设置）
- 完成判定 = 当天背会词数 ≥ 目标；达成即结束本轮并当天不再提醒
- 常量集中在 `domain/srs/ScoreScheduler.kt` 的 `ScoreConstants`，禁止散落魔法数字

### 提醒机制
- WorkManager 周期任务（unique "daily_reminder"、`ExistingPeriodicWorkPolicy.UPDATE`、初始延迟对齐提醒时间，默认 20:00 可设置）；不用 AlarmManager 精确闹钟（用户 2026-09-10 确认维持，只加引导页方案）
- 触发时检查完成度：未完成 → IMPORTANCE_HIGH 渠道（remind_urgent）heads-up 通知（今日已背会/目标/还差数 + 进度条 + "去背单词"快捷按钮）；已完成或 `lastNotifiedDay == 今天` → 静默
- **可靠性引导**（我的页，用户 2026-09-10 反馈清后台收不到提醒）：提醒卡内引导块——① 允许自启动（MIUI 深链 `com.miui.securitycenter/...AutoStartManagementActivity`，失败回退应用详情）② 省电策略无限制 ③ 允许悬浮通知（跳 remind_urgent 渠道设置）；各厂商"灵动岛/焦点通知"无公开第三方 API，不做适配
- 点击 deep link 直达学习页；Android 13+ `POST_NOTIFICATIONS` 运行时申请，拒绝后引导跳系统设置

### OCR 扫词（"实时显示单词意思"；用户 2026-09-08 重构交互）
- 主交互：**全屏相机取景**（无顶部标题栏，左上角返回胶囊按钮），CameraX `ImageAnalysis`(STRATEGY_KEEP_ONLY_LATEST) + ML Kit 流式识别，节流 ~500ms + 结果去抖；**点击画面任意位置暂停/继续识别**（顶部按钮区与底部候选面板除外，用户 2026-09-08 要求扩展点击范围）
- 底部候选面板：紧凑悬浮面板（最高 ~300dp），行点击勾选（选中高亮+对勾），显示释义与考频标注，批量加入"我要背"；**无手动输入**（词库页已有搜索，用户 2026-09-08 指定移除）
- **拍照/相册静态识别**（手写词录入主路径）：顶部胶囊按钮 拍照（ImageCapture）/相册（PhotoPicker，`data/ocr/PhotoDecoder.kt` 解码含 EXIF 旋转与降采样）；结果整批替换候选列表并自动暂停实时流
- **云端手写识别（可选，用户 2026-09-08 要求）**：离线 ML Kit 手写正确率不足（实测 8 词中 2），开关开启且密钥已填时拍照/相册优先调百度手写 OCR（token 内存缓存 30 天、失效自动刷新重试一次，图片压长边 1600 JPEG 上传），失败静默回退本地并提示"云端识别失败，已改用本地识别"；**实时扫词始终本地**（省流量与配额）；响应解析在 `BaiduOcrParser`（纯 Kotlin，有单测）
- **手写增强**（`data/ocr/ImageEnhancer.kt`，用户 2026-09-08 要求）：灰度 + 亮度 2%~98% 分位对比度拉伸，放大墨迹与纸面灰度差；顶部开关只作用于**本地**识别（云端自有预处理）（不引入额外训练，ML Kit 离线模型无训练接口，无过度训练风险）
- 候选词排序（用户 2026-09-07 要求按考频优先；2026-09-10 扩展）：有真题词频的按词频降序在前 → 词库命中但无词频 → **有联网释义的未收录词** → 纯自定义词；组内保持画面出现顺序（`candidateOrder`，联网释义到达后重排上浮）
- 提取（`domain/ocr/WordExtractor.kt` 纯 Kotlin）：正则 `[A-Za-z][A-Za-z'-]+` → 小写 → 滤单字符/含数字/含空格 → 保序去重
- 命中词库/词组显示释义；未命中 → 自定义词（source=1）。拍照/相册/实时流走同一解析管线
- **候选联网释义**（用户 2026-09-08 要求；2026-09-10 扩展到实时流）：拍照/相册/云端/实时流中未收录的词，识别出即自动调有道 jsonapi 查释义显示在候选行（并发限 4、会话内缓存、查不到也缓存防重查，逐词只查一次防逐帧刷请求；标签"在线"）；词仍按自定义词入库
- **词黑名单**（用户 2026-09-10 引入为"忽略伪词"，2026-09-16 升级为正式黑名单）：候选行尾 ✕ / 词库行 ✕ 拉黑，DataStore `ocr_ignored_words` 持久化（**key 不变，改名会丢已有忽略词**），Snackbar 可撤销；黑名单同时过滤**扫词候选**与**词库搜索结果**（`WordRepository.search` 与在线兜底都查黑名单，否则词库行的"加入黑名单"看起来毫无作用）；"我的 → 生词管理 → 词黑名单"可查看与恢复
- **行尾三件套**（用户 2026-09-16）：词库行与在线结果行统一为 **黑名单 | 星标 | 加入我要背**，同一套紧凑热区（图标 20dp、热区 34dp，`WordRow.kt` 的 `RowTailAction`），避免尺寸不一互相"打架"；扫词候选行此处没有"加入"按钮（加词走勾选+底部批量），故为 状态标注 + 黑名单 + 星标
- **加入/取消加入是切换**（用户 2026-09-16：词库里误点了 + 要能再点一次撤回）：加词按钮**始终可点**，再点即 `WordRepository.toggleMine` / `toggleMineByText` 移除。**不弹提示、不询问、不撤销**（用户明确只用它撤销"刚误加"，此场景无进度可丢）；`contentDescription` 用**动作**文案（`detail_remove`）而非状态文案。注意 `WordDetailScreen` 的"移出我要背"仍是硬删无撤销，与本次口径不同，属已知差异
- **"已添加/已星标"标记必须从数据库读，不能靠本地标志**（用户 2026-09-16 实测踩到）：`DictWordDao.getAllHeads()` 只取 `source IN (0,2)`，**不含自定义词**——凡是当年经"在线结果/拼写建议"加入过（`source=1`）的词，再搜时本地查不到、走联网、在线行会**错误显示 +**；照旧逻辑点 + 再点 ✓ 就会删掉一张**有分数**的词卡。故搜索流算出结果后用 `findMineWords` / `findStarredWords` **按文本回填** `onlineAdded/onlineStarred/suggestionAdded/suggestionStarred`，且**每轮搜索都要把四个标记全部重建**（原先只清 online 两项，导致建议行的 ✓/★ 跨查询残留）
- **拉黑必须触发重搜才会消失**：搜索流是 `combine(query.debounce(250).distinctUntilChanged(), refreshTick)`，`debounce` 只作用在 query 上、tick 立刻穿透。三条拉黑路径（词库本地行 / 在线结果行 / 拼写建议行）**都必须 `refreshTick++`**——曾漏掉在线结果行那条，用户实测"拉黑了还显示在上面"
- **图标集**：项目只依赖 `material-icons-core`（49 个图标），**没有** `Bookmark`/`Block`/`StarBorder`/`material-icons-extended`。星标用 `Icons.Filled.Star`（实心）/`Icons.Outlined.Star`（描边）、"我要背"tab 用 `Icons.Filled.Favorite`、黑名单用**自绘矢量图** `res/drawable/ic_block.xml`（🚫 圆圈加斜杠，24dp/24 视口单 path，照 `ic_notification.xml` 的写法）；不使用彩色 emoji 字符（无法 tint，与单色图标风格打架）。新增图标前先确认在核心集内，或说明引入 extended（约 10MB）的理由
- **已添加标注**（用户 2026-09-10）：候选行「已添加」（tertiary 色）标注已在我要背的词且不可勾选（`UserWordDao.findMineWords` JOIN 查询，会话内每词只查一次）；加词成功后候选移除
- **有道解析**（2026-09-10 实测接口结构变更）：`ec.word[k].trs[].tr[].l.i[]` 新结构为主 → 兼容旧 `ec.trs`（`tr.tr[].line`）→ `fanyi` → `web_trans` 同 key 网络释义兜底；样本固化在 `OnlineDictClientTest`

### 词库搜索（用户 2026-09-08 要求）
- **模糊匹配**（`domain/search/FuzzyMatcher.kt` 纯 Kotlin，全量 ~6700 词逐词打分）：精确 > 前缀 > 包含 > 编辑距离 ≤2（≤3 字母词容 1），同档内按真题词频降序；输错几个字母也能搜到，最佳匹配置顶
- **变形词与原形**：查 `word_forms.json`，搜到的变形词结果上方附带原形词条（note "「went」的原形"）
- **一键清空**：搜索框右侧小叉
- **联网查词兜底**（`data/online/OnlineDictClient.kt`，用户 2026-09-08 批准）：本地词库+词组**无精确匹配**时（2026-09-10 扩展触发条件，模糊近似命中也联网），延迟 250ms 防抖后调用有道 jsonapi（HttpURLConnection，无新依赖，5s 超时），在线结果**置顶**显示并可加入"我要背"（入库为自定义词 source=1）；行尾 黑名单 / 星标 / + 图标与本地结果行（WordRow）完全一致（用户 2026-09-10 要求界面协调，2026-09-16 统一为三件套）
- **在线结果可点开详情**（用户 2026-09-16 反馈"点不开"）：点击时先 `ensureCustomWord` 落库拿 `wordId`，再**复用现有 `WordDetailScreen`**——布局/加入我要背/星标全部现成，也不必处理加载态与重查失败。**不新建"在线词详情"页面**。代价是浏览过的在线词在 `dict_word` 留一行 source=1（词库页不显示该来源）
- 查询词本身在黑名单里时：搜索结果只显示一行说明（"可在我的→词黑名单恢复"），**不吞掉** they/their/there 这类模糊结果，也不联网兜底
- **联网拼写纠错**（用户 2026-09-16 批准，第三处联网接口）：触发时机是**最后兜底**——本地模糊无精确匹配 **且** `OnlineDictClient.lookup` 也返回 null 之后，才调 `SpellSuggestClient.suggest`（`https://dict.youdao.com/suggest?num=5&ver=3.0&doctype=json&cache=false&le=en&q=`），不干扰正常查询。返回的 `entry`=词、`explain`=释义但**带 "..." 截断且无音标**，故**必须**再 `lookup` 一次回填完整释义才能展示/入库
  - 准确度门槛见 `domain/search/SpellSuggestionFilter.kt`（纯 Kotlin 可单测）：只收**单个纯英文单词**（滤掉 "quarantine area" 这类短语与含数字/中文条目）、编辑距离 ≤2、**保持有道自己的相关度排序**不重排、最多取 3 条。距离取 2 而非 1：最常见的**相邻字母换位**（recieve→receive）经典 Levenshtein 计 2，取 1 会把这类全滤掉
  - 界面为「「%s」没有找到，你是不是想找：」区块，行尾同样三件套；建议词可点开详情（与在线结果同一路径）
  - **已知可优化**：`jsonapi` 对拼错词本身会返回 `typos.typo[]`（`word`/`trans`），用它可以省掉一次 suggest 请求。当前实现未读该字段，走的是已批准的 suggest 接口
- **导航过渡**（用户 2026-09-10 反馈默认 ~700ms 渐隐太慢）：NavHost 统一 fadeIn(tween(180)) / fadeOut(tween(120)) 四向过渡（`RememberEnglishAppUi.kt`）

### 界面通用（用户 2026-09-16）
- **细滚动条**（`ui/components/ThinScrollbar.kt`）：所有可下拉界面右侧一条 3dp 圆角细条。Compose **没有**现成滚动条组件（material3/foundation 均无），故自绘
  - 实现为 **Modifier 扩展** `Modifier.thinScrollbar(state)`，内部用 `drawWithContent` **在绘制阶段才读滚动状态**——滚动只触发重绘、不触发整屏重组；也因此不必给现有界面加一层 Box 缩进
  - 两个重载：`ScrollState`（`Column`+`verticalScroll`，视口高取 `ScrollState.viewportSize`，1.7.6 已有该公开 API）与 `LazyListState`（懒列表拿不到内容总高度，用"可见条目数 / 总条目数"近似；本项目行高接近一致，误差可忽略）
  - **颜色不硬编码两套 RGB**：取 `MaterialTheme.colorScheme.onSurfaceVariant` + 透明度。App 的深色模式是**换主题**，主题色会自动跟着切，任何主题调整下都协调；写死颜色反而会在以后改主题时脱节
  - 使用时放在 `.padding(...)` **之前**（更靠近滚动容器），滚动条才贴容器右缘；横向的 `LazyRow`（我要背筛选 chips）不加
- **行尾按钮点击动画**（`ui/components/WordRow.kt` 的 `RowTailAction`，用户 2026-09-16 要求仿 B 站点赞/投币/收藏）：点击时缩放到 `pressedScale` 再回弹到 1.0，用 `spring(DampingRatioMediumBouncy)` 过冲；tint 另走 `animateColorAsState` 平滑过渡（描边星↔实心星、+↔✓ 不硬切）。**加入传 1.35（弹大）、取消传 0.75（先缩，读起来是"被取走"）**。全部 11 个调用点共用这一个组件，改一处即全覆盖
- **Snackbar 三个坑**（都踩过，别再踩）：
  1. **带 `actionLabel` 时 Material3 的默认 `duration` 是 `Indefinite`**——不会自动消失，这就是"提示显示时间太长"的根因。而它只有 `Short`(4s)/`Long`(10s) 两档、**没有 3 秒**，要精确控时就用 `withTimeoutOrNull(3_000) { showSnackbar(..., duration = Indefinite) }`：超时会取消 `showSnackbar`，其 `finally` 清掉数据、Snackbar 随之收起
  2. **`consumeXxx()` 必须放在 `showSnackbar` 之后**：它清的往往就是这个 `LaunchedEffect` 的 key，放在前面会因 key 变化重启协程、把刚挂上的 Snackbar **自己取消掉**（表现为"提示根本不显示"）
  3. 拉黑/星标这类会触发重搜的动作，**`consume` 与 `refreshTick` 不要和 Snackbar 抢同一个 key**；拉黑提示统一 3 秒（`BLACKLIST_SNACKBAR_MS`）
- **写入与重搜的顺序**：`SettingsRepository.addToBlacklist` 是挂起的 DataStore 写。若在它完成前就 `refreshTick++`，重搜会读到**旧集合** → 该词没被过滤，出现"顶部提示说已在黑名单、词却还在列表里"的自相矛盾（实测踩到）。**必须 `launch { 写入; refreshTick++ }`**，让写入先落地
- 点星标时若顺带加入"我要背"，**不弹任何提示**（用户 2026-09-16：那句话没有意义——星标变实心、加号变对勾本身已说明结果）。`starNotice` 相关字段与字符串已全部移除

## 六、里程碑验收清单（完成打勾）

- [ ] M0 环境搭建：SDK（platform-35 / build-tools 35.0.0 / platform-tools）装齐，gradle wrapper 可用
- [ ] M0 CLAUDE.md + README.md（含词库来源与 GPL-3.0 声明）
- [ ] M1 骨架：`gradlew :app:assembleDebug` 通过，4 tab（今日/词库/我要背/我的）空应用
- [ ] M1 词库：assets 词库 JSON（≈6705 词）；Room 预填充；词库搜索/词详情/我要背列表（用户 2026-09-07 决定：不做 TTS 发音，相关按钮已移除）
- [ ] M2 学习：分数模型/选词单测全绿；真机完成一次学习会话；进度条正确
- [ ] M3 通知：真机提醒时间设 1 分钟后收到 heads-up；完成后当天不再收到
- [ ] M4 OCR：WordExtractorTest 全绿；真机对书本实时识别出词并加入"我要背"
- [ ] M5 发布：`gradlew build` 全量通过；深色主题/空态/图标；**用户检阅通过后** commit + push（push 前须再次确认）
- [ ] M6 第四轮反馈（2026-09-16）：① 词黑名单（升级自"忽略"，词库行也能拉黑 + 我的页管理入口可恢复）② 词库在线结果可点开详情 ③ 星标（永不算已掌握 / 不计入今日背会 / 权重 ×3；未入库时点星标=自动加入）④ 行尾统一三件套（黑名单|星标|加入）⑤ 小爱同学（结论：**平台限制，App 侧无法注册语音别名**，只做引导）⑥ 联网搜索纠错
- [ ] M7 第五轮打磨（2026-09-16）：① 我要背列表改倒序（新添加在前）② 所有可下拉界面加细滚动条（含扫词候选面板，深浅色自动适配）③ 黑名单图标改 🚫（自绘矢量图）④ 行尾三个按钮加点击回弹动画
- [ ] M8 第六轮修正（2026-09-16）：① 修"在线结果拉黑后不消失"（漏 `refreshTick++`）② 「已添加/已星标」标记改为从库按文本回填（否则自定义词误显示 +，再点 ✓ 会删掉有分数的卡）③ 加入可再点取消（切换 + 两套动画）④ 🚫 斜线方向改 ＼（`<group scaleX="-1" translateX="24">` 镜像，不用 autoMirrored）⑤ 插件余额动画改按墙上时间匀速推进
- [ ] M9 第七轮微调（2026-09-16）：① 点星标顺带加入"我要背"时不再弹提示 ② 拉黑提示固定显示 3 秒（Material3 无 3 秒档，用 `withTimeoutOrNull`）③ 修"拉黑写入未落地就重搜"的竞态 ④ 修"consume 放在 showSnackbar 之前导致提示根本不显示"

## 七、构建与验证命令

```bash
./gradlew :app:assembleDebug        # 编译 APK
./gradlew :app:testDebugUnitTest    # 单元测试
./gradlew :app:installDebug         # 装到连接的真机
adb install -r app/build/outputs/apk/debug/app-debug.apk
node tools/build-dict.mjs           # 重新生成词库 assets
node tools/build-freq.mjs           # 重新生成真题词频 assets（源：NETEMVocabulary）
node tools/build-forms.mjs          # 重新生成时态变形表 word_forms.json（考纲词）
node tools/build-phrases.mjs        # 重新生成词组 word_phrases.json（源：ECDICT StarDict，需先下载）
```

## 八、提交约定

- 每完成一个里程碑 commit 一次（信息用中文简述内容）
- `push origin main` 必须先经用户检阅确认
- 词库来源：RealKai42/qwerty-learner（GPL-3.0）——README 与 App"关于"页必须标注来源与许可
- 真题词频来源：exam-data/NETEMVocabulary（CC BY-NC-SA 4.0，仅非商业使用）——README 与 App"关于"页必须标注来源与许可
- 词组释义来源：ECDICT（MIT）——README 与 App"关于"页必须标注来源与许可
- 联网查词：有道词典公开 jsonapi（无 key），仅本地未收录时兜底；用户 2026-09-08 批准（个人使用）
- 联网拼写纠错：有道公开 suggest 接口（无 key），仅在本地与联网精确查都失败时兜底；用户 2026-09-16 批准（个人使用）
