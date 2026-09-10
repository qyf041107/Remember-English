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

- **离线优先**：无后端、无账号，词库/词频/词形/词组全部打包进 assets；联网仅两处且均为用户 2026-09-08 批准：① **联网查词兜底**——本地词库+词组未收录时调用有道公开接口（jsonapi，无 key）；② **云端手写识别（可选）**——拍照/相册静态图调百度智能云手写文字识别（`data/online/BaiduHandwritingClient.kt`），密钥由用户在"我的"页填入、DataStore 本地存储**不入仓库**，失败自动回退本地 ML Kit；其余任何网络请求均不允许（INTERNET 权限已声明）
- UI 文案一律**中文**；界面克制：Material3 默认组件，不加装饰性图片/动画
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

## 四、代码结构约定

- 包根 `com.qyf.rememberenglish`，单模块 `:app`
- 分层：`ui/`（theme、navigation、study、library、mine、profile、add、components）、`data/`（db、repository、seed、settings、worker、notifier、ocr、online、di）、`domain/`（model、srs、select、search、ocr、usecase）
- 命名：`XxxScreen` / `XxxViewModel` / `XxxRepository` / `XxxDao` / `XxxEntity`
- 依赖只通过 `gradle/libs.versions.toml` 添加，新增库需说明理由
- **domain 层纯 Kotlin**（不 import android.*），保证可单测；分数模型常量集中在 `domain/srs/ScoreScheduler.kt`，禁止散落魔法数字

## 五、关键设计（实现依据）

### 数据（Room 3 表）
- `dict_word`：id、word(UNIQUE)、usphone、ukphone、meanings(JSON 文本)、source(0=内置红宝书/1=自定义/2=考纲内词组)、createdAt。**自定义词也进此表**（source 区分），"词库"页只显示 source=0 与 source=2
- `user_word`（我要背+背分）：wordId(FK UNIQUE)、addedAt、score(背会分数)、wrongCount、unclearCount、lastAnsweredAt(0=从未作答)、isSuspended
- `answer_log`：userWordId、wordId、rating(0我不会/1不清楚/2我知道)、reviewedAt、prevScore、newScore；今日背会数 = 当天 `rating != 0` 的 **DISTINCT wordId** 数
- assets 另有：
  - `word_freq.json`（真题词频，小写词 → 出现次数），供 OCR 候选排序、搜索同档排序与详情页展示
  - `word_forms.json`（时态变形 → 原形，约 2.1 万条；规则变形 + 不规则动词表，仅考纲词），搜索变形词时同时显示原形（"went"的原形）
  - `word_phrases.json`（考纲内词组/短语 2500 条，源 ECDICT 过滤：所有单词都在考纲内，按真题词频降序取前 2500，source=2），可搜索、可加入"我要背"

### 分数模型（用户 2026-09-07 定义，替代原 SM-2 SRS）
- 三键记分：**我知道 +1 分｜不清楚 +0.5 分｜我不会 +0 分**；一单词分数 **满 5 分 = 已掌握**
- 筛选口径（"我要背"页）：新词 = 0 分且从未作答；学习中 = 已作答但未满 5 分（含答错 0 分的）；已掌握 = ≥5 分
- **加权随机抽词**（`domain/select/StudyPicker.kt`，无固定队列）：权重 = `(1 + wrongCount + unclearCount) × (已掌握 ? 0.25 : 1)`——不会/不清楚过的词出现最勤，已掌握词 0.25 折一笔带过；尽量不与刚答过的词重复
- 学习卡片：先只显示英文，界面提示"如果不会，请点击屏幕"，点卡片显示释义；下方三键 我知道/我不会/不清楚
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
- **忽略伪词**（用户 2026-09-10，date/sun 每次 OCR 都出现）：候选行尾 ✕ 忽略，DataStore `ocr_ignored_words` 持久化，Snackbar 可撤销；不做词黑名单与管理 UI
- **已添加标注**（用户 2026-09-10）：候选行尾"已添加"（tertiary 色）标注已在我要背的词且不可勾选（`UserWordDao.findMineWords` JOIN 查询，会话内每词只查一次）；加词成功后候选移除
- **有道解析**（2026-09-10 实测接口结构变更）：`ec.word[k].trs[].tr[].l.i[]` 新结构为主 → 兼容旧 `ec.trs`（`tr.tr[].line`）→ `fanyi` → `web_trans` 同 key 网络释义兜底；样本固化在 `OnlineDictClientTest`

### 词库搜索（用户 2026-09-08 要求）
- **模糊匹配**（`domain/search/FuzzyMatcher.kt` 纯 Kotlin，全量 ~6700 词逐词打分）：精确 > 前缀 > 包含 > 编辑距离 ≤2（≤3 字母词容 1），同档内按真题词频降序；输错几个字母也能搜到，最佳匹配置顶
- **变形词与原形**：查 `word_forms.json`，搜到的变形词结果上方附带原形词条（note "「went」的原形"）
- **一键清空**：搜索框右侧小叉
- **联网查词兜底**（`data/online/OnlineDictClient.kt`，用户 2026-09-08 批准）：本地词库+词组**无精确匹配**时（2026-09-10 扩展触发条件，模糊近似命中也联网），延迟 250ms 防抖后调用有道 jsonapi（HttpURLConnection，无新依赖，5s 超时），在线结果**置顶**显示并可加入"我要背"（入库为自定义词 source=1）；行尾 + / ✓ 图标与本地结果行（WordRow）一致（用户 2026-09-10 要求界面协调）
- **导航过渡**（用户 2026-09-10 反馈默认 ~700ms 渐隐太慢）：NavHost 统一 fadeIn(tween(180)) / fadeOut(tween(120)) 四向过渡（`RememberEnglishAppUi.kt`）

## 六、里程碑验收清单（完成打勾）

- [ ] M0 环境搭建：SDK（platform-35 / build-tools 35.0.0 / platform-tools）装齐，gradle wrapper 可用
- [ ] M0 CLAUDE.md + README.md（含词库来源与 GPL-3.0 声明）
- [ ] M1 骨架：`gradlew :app:assembleDebug` 通过，4 tab（今日/词库/我要背/我的）空应用
- [ ] M1 词库：assets 词库 JSON（≈6705 词）；Room 预填充；词库搜索/词详情/我要背列表（用户 2026-09-07 决定：不做 TTS 发音，相关按钮已移除）
- [ ] M2 学习：分数模型/选词单测全绿；真机完成一次学习会话；进度条正确
- [ ] M3 通知：真机提醒时间设 1 分钟后收到 heads-up；完成后当天不再收到
- [ ] M4 OCR：WordExtractorTest 全绿；真机对书本实时识别出词并加入"我要背"
- [ ] M5 发布：`gradlew build` 全量通过；深色主题/空态/图标；**用户检阅通过后** commit + push（push 前须再次确认）

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
