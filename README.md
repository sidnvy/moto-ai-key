# AI Key

Moto Edge 70 的 ChatGPT 按键助手。无需 root。

[下载 0.9.7 APK](https://github.com/sidnvy/moto-ai-key/releases/latest) · [版本说明](CHANGELOG.md)

## 按键行为

| 场景 | 单击 | 双击 |
| --- | --- | --- |
| 其他应用 | 打开 ChatGPT live 语音 | 将当前页面截图带入 ChatGPT，开始听写 |
| ChatGPT 编辑界面 | 开始听写 | 不操作 |
| ChatGPT 听写中 | 完成听写，保留编辑 | 取消听写 |

不会自动点击发送。截图只在双击时触发；锁屏或息屏时不尝试绕过。

## 设置

1. 安装 ChatGPT 和本项目 APK，打开 AI Key。
2. 点击单击、双击演示了解效果，再开启 AI Key 辅助功能。
3. 在 Motorola AI Key 设置中，将单击分配给 AI Key 的 LaunchActivity，双击分配给 CancelActivity。需要设备固件支持双击启动应用。

不要求 ChatGPT 为默认助手。原生系统助手浮层未接入本版本。

目前仅在 Moto Edge 70 验证，不保证其他型号可用。ChatGPT 界面或入口更新可能影响功能。

## 构建

需要 Android SDK platform 35、build-tools 35.0.1、JDK 和 Bash。设置 ANDROID_SDK_ROOT 后执行 `bash build.sh`。APK 输出为 `build/probe.apk`。

本地构建会在不存在时生成本地签名密钥；该密钥不能覆盖已安装的官方签名 APK。公开仓库不包含官方签名私钥。

## 0.9.7 验证范围

已验证真实单击打开 live、听写开始/完成，以及双击截图提问/取消。一次静默进入并退出 live 的测试中，可见最近聊天列表未新增条目；这不是所有场景下不会生成空会话的保证，不包含语音回答质量验证。

Release 同时提供同签名回滚 APK。回滚版本号高于正式版本以支持保留数据的原位安装。

本项目与 Motorola、OpenAI 无隶属关系。暂未选择开源许可证。
