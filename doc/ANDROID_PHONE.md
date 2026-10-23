# 安卓手机版交付与当前验证状态

本次基于 `faysal-X-N/deskpet` main 的 `e96679fab754e94f787fbd4bfd695a622383ba81` 修改；只向用户 fork `hhjjfgh60/deskpet` 保存，不向原作者推送。

## 当前云端状态（2026-10-23）

- 工作目录：`/workspace/deskpet`。Git clone 因云端代理无法连接而失败；已通过 GitHub 接口取回全部文本源码与小文件，在该目录初始化了本地 Git 快照。该本地快照不是上游完整 Git 历史；远端保存基于 fork 的实际 main tree，不会重写历史。
- 全部 Kotlin 源码、资源 XML、测试、构建脚本、原项目 LICENSE 和 Gradle Wrapper JAR 已在本地。
- `yi-bu/spritesheet.webp` 已核对 Git blob SHA-1、字节数（860912），Pillow 可解码为 1536×1872 RGBA；不再下载它。
- 本地龙娘 PNG 为 0 字节，尚未取得真实素材。原项目6个示例 WebP 也为0字节：feibi-2、feixue、furina、hutao、ikun、jiyi，`test_pets` 中有相同缺失副本。完整清单见 `LOCAL_FILE_STATUS.md`。
- 已有龙娘原始 `pet.json`、FORMAT、LICENSE、RIGHTS、ATTRIBUTION；适配的 Android `pet.json` 也已准备好。**没有占位龙娘图，也不宣称完整龙娘版已完成。**
- GitHub Contents 接口对超过1MB的文件未返回有效文件内容；`fetch_blob` 把二进制当 UTF-8，报 UnicodeDecodeError / GitHub Fetch only accepts UTF-8 text。
- Git clone / curl 报 `Failed to connect to proxy port 8080`；备用 raw.githubusercontent.com 和 Git Blobs API 的 Python 下载报 `[Errno 1] Operation not permitted`。未再请求网络权限或循环调用已失败的接口。
- Gradle Wrapper 可执行，但下载 Gradle 8.13 报 `java.net.SocketException: Operation not permitted`。本地没有 Android SDK 和已安装的 Gradle，因此未完成 Kotlin 编译、单元测试或真机测试。
- 已完成 Python 语法检查、YAML 手动触发和 artifact 名称检查、Android XML 解析，以及适配元数据与上游9种动作逐项对照。完整图片校验按预期因素材缺失失败，绝不标成通过。

## 改动

- 保留8×9旧宠物默认规则，新增可配置行列与逐帧时长。龙娘采用8×11，每格192×208，9种动作的帧数为6/8/8/4/5/8/6/6/6；最后两行16个方向姿态保留，不作为动作帧播放。
- 缩略图和悬浮渲染共用切帧规则；动作完整播放后回到待机。新增回归测试检查精确切帧几何、眨眼时长、旧格式兼容与错误配置拒绝。
- 完整素材构建首次安装默认选中白发 GPT 龙娘；升级不覆盖现有选择。无效或空图不加入宠物列表。
- 支持点击招手、长按拖动、双指缩放和大小按钮；修复缩放不能小于100%的问题。拖动及缩放后延迟250毫秒保存位置和大小，仓库写入互斥防止覆盖。
- 增加安卓13通知权限、悬浮窗、电池及应用详情设置入口。使用前台服务、持续通知及通知关闭按钮；从最近任务划掉 Activity 后继续运行。设备厂商省电策略仍需在手机设置里放行，强行停止后需要手动重开。
- 不添加 AI 聊天、模型密钥、服务器或 INTERNET 权限。构建时下载依赖与素材不代表安装后的应用需要联网。
- Gradle 使用官方仓库和发行地址；Actions 加入 workflow_dispatch、固定 SDK、单元测试、lint、debug APK 构建、签名验证和 artifact 上传。每次构建的调试签名用于测试；不同运行生成的调试密钥可能不同，覆盖安装失败时需卸载旧包（先备份宠物数据）。

## 素材与许可证

原项目 `LICENSE` 保留，代码继续按其 GPLv3-or-later 条款；原有示例素材的单独权利声明不变。

龙娘来源：<https://github.com/tqlearning/ai-girl-chatgpt-pets>，固定提交 `3afe14603b40e24c75e87e097cbfee1bfc10290d`。

该素材仓库的 MIT 仅适用于列明的文档和布局元数据，**不涵盖 PNG/GIF 美术和角色设定**。美术授权未确认，不能把龙娘图称为 MIT/GPL 开源素材；进一步分发或发布前应确认所需授权。完整原文保存在 `doc/gpt-dragon/`，并随 APK 打包到 `assets/notices/gpt-dragon/`。来源署名记录将原角色形象归于 ZipZipPipe，非 OpenAI 官方项目。

## 手机运行 GitHub Actions 并下载 APK

1. 安卓浏览器登录 GitHub，打开 <https://github.com/hhjjfgh60/deskpet/actions>。页面功能被折叠时，使用浏览器的「桌面版网站」。
2. 如果 fork 显示工作流被停用，点击 **I understand my workflows, go ahead and enable them**。
3. 选择 **Build GPT Dragon Android APK**，点击 **Run workflow**，分支选择 **main**，再次点击 **Run workflow**。
4. 打开最新运行，等待绿色勾。工作流会先补齐缺失图片（仅下载缺失或校验错误的文件），解码并验证图片实际像素、透明格、帧布局和时长，再编译 APK。
5. 成功后在运行页面底部 **Artifacts** 点击 **gpt-dragon-android-apk**。下载的是 ZIP，用手机文件管理器解压得到 `app-debug.apk`。
6. 点击 APK；按系统提示允许当前浏览器/文件管理器「安装未知应用」，完成安装。
7. 打开「GPT 龙娘桌宠」，允许悬浮窗及通知；电池设为「不限制」，按厂商设置允许后台活动/自启动，然后点击「开启」。返回桌面或其他 App 即可看到龙娘。长按拖动、双指缩放、单击招手；通知栏可返回设置或关闭。

只有成功运行并出现 artifact 后才有可下载 APK。若 Actions 尚未成功，此文档描述的是操作路径，不代表已经生成安装包。

## 若 Actions 也无法下载素材：手机补齐

无需电脑，也不必重传已经完整的源码。只需先补龙娘这一张图片：

1. 浏览器打开原始图片：<https://raw.githubusercontent.com/tqlearning/ai-girl-chatgpt-pets/3afe14603b40e24c75e87e097cbfee1bfc10290d/assets/gpt-dragon/spritesheet.png>，长按保存原图，勿截图或经聊天软件压缩。文件应为2602295字节，1536×2288 RGBA PNG。
2. 将下载的 PNG 直接上传到当前对话，我可以放入预留目录；或者在你的 fork 中进入 `app/src/main/assets/pets/gpt-dragon/`，用 **Add file → Upload files** 上传为 `spritesheet.png` 并提交到 main（必要时切换桌面版网站）。
3. SHA-256 必须是 `58ff74425fa8d1e6cf1359532312d39d60735f9515c00df4633b75390485c843`，脚本会自动校验，无需你在手机手算。
4. 原项目6张示例 WebP 已在 GitHub fork 的原始提交中，Actions checkout 通常无需下载它们。如果其校验失败，查看 `Restore missing original assets...` 步骤；脚本会列出准确路径、固定提交和两种下载地址。可从上游同路径保存原图后上传到对话，不要用其他图替换。
5. 补齐后再运行第3步的工作流。如果失败的是依赖下载或编译，请发送该运行页面链接，可继续定位；不要把失败运行当成可用 APK。

## 云端/开发验证命令

```sh
python3 scripts/prepare_assets.py --offline  # 仅盘点，不联网；缺图返回非零
python3 scripts/prepare_assets.py            # raw 及 Git Blobs 两条路径，固定版本/哈希
python3 scripts/verify_assets.py             # 需要 Pillow；检查真实像素和动画元数据
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Gradle `preBuild` 也会强制校验真实龙娘 PNG 的长度与 SHA-256，不能用0字节文件绕过。Actions 使用的 `Pillow==11.3.0` 仅用于构建验证，不进入 APK。
