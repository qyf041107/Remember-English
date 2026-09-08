# Remember-English 记英语

简约的考研英语背单词 App（Android）。内置 2025 版考研红宝书词库（约 6700 词，含音标与中文释义）、考研真题词频表、时态变形表（约 2.1 万条）与考纲内高频词组（2500 条），支持拍照/相册 OCR 扫词添加生词（含云端手写识别）、模糊搜索、三键记分背诵、每日目标与未完成推送督促。无账号，学习数据全部本地存储。

## 功能

### 今日学习
- 每日目标（默认背会 20 词，可设置 5~100），进度条实时显示
- **加权随机**抽词：之前"我不会/不清楚"的词出现最勤，已掌握的词一笔带过（出现率 ×0.25），避免固定队列的枯燥感
- 先只显示英文，点击屏幕显示释义，三键自评：**我知道 +1 分｜不清楚 +0.5 分｜我不会 +0 分**，一单词满 **5 分**即视为已掌握
- 答错/不清楚时，中文释义**停留 3 秒**（点击卡片可跳过），看清意思再进下一个词

### 词库
- 考研红宝书全量词 + 考纲内高频词组/短语（2500 条，按真题词频排序）
- **模糊搜索**：输错 1~2 个字母也能搜到；匹配质量分档（精确 > 前缀 > 包含 > 编辑距离），最佳匹配置顶，同档内真题词频高的在前
- 搜时态变形词（went/wolves…）时同时显示**原形**词条
- 本地未收录时**自动联网查询**兜底（有道词典公开接口，无 key），结果可一键加入"我要背"
- 详情页含音标、完整释义、真题词频、词组标签

### 我要背
- 全部 / 新词（只添加还没背）/ 学习中（未满 5 分）/ 已掌握（满 5 分）筛选
- 点击单词直接进入背诵卡（只显英文 → 点屏显义 → 三键记分）
- 左滑移出（Snackbar 可撤销）

### 扫词添加
- **全屏相机实时取景**识别英文单词，底部紧凑候选面板实时显示释义与考频标注，点行勾选批量加入
- 点画面任意位置**暂停/继续**识别
- **拍照 / 相册**静态识别——录入手写单词的主路径
- **手写增强**（可开关）：灰度 + 对比度拉伸预处理，提升离线识别对手写的敏感度
- **云端手写识别（可选）**：接入百度智能云手写文字识别，手写正确率显著更高；失败自动回退本地识别
- 候选词按**真题词频**降序排列，未命中词库的作为自定义词入库

### 督促提醒
- 每日固定时间（默认 20:00 可设置）检查当日目标完成度，未完成则发送 heads-up 系统通知督促；已完成或当天已提醒过则静默
- 通知点击直达学习页；Android 13+ 动态申请通知权限

## 快速上手

1. **加词**：在"词库"搜索加入，或用扫词页拍照/实时识别添加
2. **背诵**：进"今日"点"随机提问"开始会话；平时可在"我要背"点单词单独背
3. **坚持**：设好每日目标与提醒时间，当天没背完会收到通知

## 云端手写识别（可选）

离线识别对印刷体效果很好，手写建议开启云端识别：

1. 打开 [百度智能云](https://cloud.baidu.com/product/ocr/handwriting) 注册并完成**个人实名认证**
2. 进入 [OCR 控制台](https://console.bce.baidu.com/ai/#/ai/ocr/overview/index) 创建应用（勾选**手写文字识别**），复制 **API Key** 与 **Secret Key**
3. App"我的"页 → 云端手写识别 → 打开开关 → 粘贴两个 Key → 保存

密钥仅保存在本机 DataStore，不会随应用或项目分发。个人认证每月有免费调用额度（以控制台显示为准）；云端失败时自动回退本地识别，不影响使用。

## 构建

```bash
# 环境：JDK 17，Android SDK（platform-35、build-tools 35.0.0、platform-tools）
./gradlew :app:assembleDebug
./gradlew :app:installDebug   # 安装到 USB 连接的真机
./gradlew :app:testDebugUnitTest   # 单元测试
```

技术栈：Kotlin + Jetpack Compose（Material 3）、Room、WorkManager、DataStore、CameraX、ML Kit 离线 OCR、Hilt。

## 项目结构

```
app/src/main/java/com/qyf/rememberenglish/
├── ui/        # Compose 界面（study 今日学习 / library 词库 / mine 我要背 / profile 我的 / add 扫词 / components）
├── data/      # db(Room)、repository、seed(词库预填充)、settings(DataStore)、worker(提醒)、ocr、online、freq、di
├── domain/    # 纯 Kotlin：model、srs(分数模型)、select(加权抽词)、search(模糊匹配)、ocr(文本提取)
tools/         # Node 数据管线：词库/词频/词形/词组 JSON 生成脚本
```

## 重新生成数据（可选）

词库/词频/词形/词组 JSON 已打包在 `app/src/main/assets/`。如需重新生成：

```bash
node tools/build-dict.mjs     # 词库 words_kaoyan.json
node tools/build-freq.mjs     # 真题词频 word_freq.json（需先下载 NETEMVocabulary 源数据）
node tools/build-forms.mjs    # 时态变形表 word_forms.json
node tools/build-phrases.mjs  # 词组 word_phrases.json（需先下载 ECDICT StarDict 源数据）
```

## 数据来源与许可

- 词库数据整理自开源项目 [qwerty-learner](https://github.com/RealKai42/qwerty-learner) 的 2025 版考研红宝书词库（`public/dicts/2025KaoYanHongBaoShu.json`），该项目采用 **GPL-3.0** 许可，本项目同样以 GPL-3.0 发布并在应用"关于"页标注来源。
- 真题词频数据来自 [exam-data/NETEMVocabulary](https://github.com/exam-data/NETEMVocabulary)（考研英语真题词汇词频排序表），采用 **CC BY-NC-SA 4.0** 许可（仅限非商业使用），已在应用"关于"页标注来源。
- 词组释义取自 [ECDICT](https://github.com/skywind3000/ECDICT)（开源英汉词典），采用 **MIT** 许可，已在应用"关于"页标注来源；仅保留所有单词都在考研大纲内的词组。
- OCR 使用 Google ML Kit（离线模型）；联网查词仅在本地词库未收录时调用有道词典公开接口兜底。
- 云端手写识别调用百度智能云 API，需用户自行注册并填入密钥（可选功能，不填完全不影响离线使用）。
