# Remember-English

简约的考研英语背单词 App（Android）。内置 2025 版考研红宝书词库（约 6700 词，含音标与中文释义），支持拍照 OCR 扫词添加生词、间隔重复（SRS）记忆调度、每日目标与未完成推送督促。完全离线，无账号。

## 功能

- **今日学习**：每日目标（默认 20 新词，可设置）+ 到期复习，卡片翻转、认识/模糊/不认识三键评分，SRS 自动安排下次复习
- **词库**：考研红宝书全量词库搜索、详情、TTS 发音、一键加入"我要背"
- **我要背**：自定义生词管理（新词/学习中/已掌握），支持添加词库外的自定义词
- **扫词添加**：相机实时取景识别英文单词并**实时显示释义**，勾选批量加入；也支持拍照、相册选图、手动输入
- **督促提醒**：每日固定时间检查当日目标完成度，未完成则发送系统通知督促（已通知过/已完成当天不打扰）

## 构建

```bash
# 环境：JDK 17，Android SDK（platform-35、build-tools 35.0.0、platform-tools）
./gradlew :app:assembleDebug
./gradlew :app:installDebug   # 安装到 USB 连接的真机
```

## 重新生成词库（可选）

词库 JSON 已打包在 `app/src/main/assets/words_kaoyan.json`。如需重新生成：

```bash
node tools/build-dict.mjs
```

## 词库来源与许可

词库数据整理自开源项目 [qwerty-learner](https://github.com/RealKai42/qwerty-learner) 的 2025 版考研红宝书词库（`public/dicts/2025KaoYanHongBaoShu.json`），该项目采用 **GPL-3.0** 许可，本项目同样以 GPL-3.0 发布并在应用"关于"页标注来源。OCR 使用 Google ML Kit（离线模型）。
