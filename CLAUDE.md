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

- **纯离线**：无后端、无账号、AndroidManifest **不声明 INTERNET 权限**（词库打包进 assets）
- UI 文案一律**中文**；界面克制：Material3 默认组件，不加装饰性图片/动画
- 技术栈（已与用户确认）：Kotlin + Jetpack Compose、ML Kit 离线 OCR、间隔重复 SRS、开源词库打包

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
- 分层：`ui/`（theme、navigation、study、library、mine、profile、add、components）、`data/`（db、repository、seed、settings、worker、notifier、di）、`domain/`（model、srs、select、ocr、usecase）
- 命名：`XxxScreen` / `XxxViewModel` / `XxxRepository` / `XxxDao` / `XxxEntity`
- 依赖只通过 `gradle/libs.versions.toml` 添加，新增库需说明理由
- **domain 层纯 Kotlin**（不 import android.*），保证可单测；SRS 常量集中在 `domain/srs/SrsScheduler.kt`，禁止散落魔法数字

## 五、关键设计（实现依据）

### 数据（Room 4 表）
- `dict_word`：id、word(UNIQUE)、usphone、ukphone、meanings(JSON 文本)、source(0=内置红宝书/1=自定义)、createdAt。**自定义词也进此表**（source 区分），"词库"页只显示 source=0
- `user_word`（我要背+SRS 状态）：wordId(FK UNIQUE)、addedAt、state(0新词/1学习中/2复习)、ease(默认2.5)、intervalDays、reps、lapses、streak、dueAt、isSuspended、isMastered
- `review_log`：userWordId、wordId、rating(0不认识/1模糊/2认识)、reviewedAt、prevInterval、newInterval
- `daily_stat`：day(主键 yyyy-MM-dd)、newLearned、reviewsDone、reviewsDueAtDayStart（当日首次启动快照）

### SRS 公式（三键：认识/模糊/不认识）
- 学习态：不认识→interval=0 当天内重现、streak=0｜模糊→interval=1｜认识→interval=1(streak=0) 或 2(streak≥1)，interval≥2 进入复习态
- 复习态：不认识→ease−0.2、interval=1、回学习态、lapses++｜模糊→ease−0.15、interval=max(2, ceil(int×1.2))｜认识→ease+0.1、interval=ceil(int×ease)
- clamp：ease∈[1.3, 3.0]、interval∈[0, 365]；interval≥21 天 → isMastered
- 每日队列 = 到期复习（dueAt≤今日24:00，升序，复习优先）+ 新词（state=0 按 addedAt 升序，数量=剩余目标，默认 20 可设置）
- 完成判定 = `newLearned ≥ 目标 AND reviewsDone ≥ reviewsDueAtDayStart`

### 提醒机制
- WorkManager 周期任务（unique "daily_reminder"、`ExistingPeriodicWorkPolicy.UPDATE`、初始延迟对齐提醒时间，默认 20:00 可设置）；不用 AlarmManager 精确闹钟
- 触发时检查完成度：未完成 → IMPORTANCE_HIGH 渠道（remind_urgent）heads-up 通知（剩余新词/复习数）；已完成或 `lastNotifiedDay == 今天` → 静默
- 点击 deep link 直达学习页；Android 13+ `POST_NOTIFICATIONS` 运行时申请，拒绝后引导跳系统设置

### OCR 扫词（"实时显示单词意思"）
- 主交互：CameraX `ImageAnalysis`(STRATEGY_KEEP_ONLY_LATEST) + ML Kit 流式识别，节流 ~500ms + 结果去抖，候选词 chip 列表实时显示释义，勾选批量加入"我要背"。候选词排序（用户 2026-09-07 要求按考频优先）：命中考研词库的排前面，未命中的自定义词排后面，组内保持画面出现顺序
- 提取（`domain/ocr/WordExtractor.kt` 纯 Kotlin）：正则 `[A-Za-z][A-Za-z'-]+` → 小写 → 滤单字符/含数字/含空格 → 保序去重
- 命中词库显示释义；未命中 → 自定义词（source=1）。拍照/相册/手动输入走同一解析管线

## 六、里程碑验收清单（完成打勾）

- [ ] M0 环境搭建：SDK（platform-35 / build-tools 35.0.0 / platform-tools）装齐，gradle wrapper 可用
- [ ] M0 CLAUDE.md + README.md（含词库来源与 GPL-3.0 声明）
- [ ] M1 骨架：`gradlew :app:assembleDebug` 通过，4 tab（今日/词库/我要背/我的）空应用
- [ ] M1 词库：assets 词库 JSON（≈6705 词）；Room 预填充；词库搜索/词详情/我要背列表（用户 2026-09-07 决定：不做 TTS 发音，相关按钮已移除）
- [ ] M2 学习：SRS/选词单测全绿；真机完成一次 20 词学习会话；进度条正确
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
```

## 八、提交约定

- 每完成一个里程碑 commit 一次（信息用中文简述内容）
- `push origin main` 必须先经用户检阅确认
- 词库来源：RealKai42/qwerty-learner（GPL-3.0）——README 与 App"关于"页必须标注来源与许可
