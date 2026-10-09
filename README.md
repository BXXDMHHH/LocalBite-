# LocalBite（拾味）— 纯本地 Android 点餐 MVP

一个不需要账号、不需要服务器、不需要网络的 Android 点餐 App。数据保存在安装它的那台手机上。

## 已实现

- Kotlin + Jetpack Compose + Material 3 的中文界面
- Room 本地数据库：菜品、订单、店铺设置
- 点餐首页：分类筛选、搜索、菜品卡片、数量增减
- 购物车：实时计算总价、提交订单
- 订单历史：本机保存订单时间、菜品摘要和总额
- 订单详情：查看菜品明细，支持状态流转（已下单 → 制作中 → 已完成，可取消），删除订单有二次确认
- 菜单管理：新增、编辑、删除菜品
- 图片定制：从系统图片选择器选择图片，复制到 App 私有目录
- 店铺定制：名称、欢迎语、主题色
- ZIP 数据备份与恢复：菜品图片一并打包，恢复后自动重建图片路径；兼容旧版纯 JSON 备份
- 首次启动时自动插入演示菜品

## 环境要求

- Android Studio Ladybug 或更新版本（建议最新版）
- JDK 17
- Android SDK Platform 35
- Android Gradle Plugin 8.7.3（Gradle Sync 时由 Android Studio 下载）

## 如何运行

1. 解压 `LocalBite.zip`。
2. Android Studio 选择 **Open**，打开 `LocalBite` 文件夹（不是只打开 `app` 子目录）。
3. 等待 Gradle Sync 完成；首次同步需要联网下载 Gradle 和 Maven 依赖。App 本身运行时不需要网络。
4. 用 USB 连接 Android 手机并开启开发者选项 / USB 调试，或启动 Android Emulator。
5. 点击工具栏的 Run 按钮。

## 如何生成 APK

在 Android Studio 菜单选择 **Build → Build Bundle(s) / APK(s) → Build APK(s)**。

Debug APK 通常生成在：

`app/build/outputs/apk/debug/app-debug.apk`

将 `app-debug.apk` 发给需求方，在 Android 手机上打开安装即可。部分手机需要允许当前文件管理器“安装未知来源应用”。如果要公开分发或长期交付，建议在 Android Studio 使用 **Build → Generate Signed Bundle / APK** 生成签名 APK，并妥善保存签名密钥。

## 离线与数据说明

- App 没有联网权限声明，也不调用远程 API。
- 每台手机的数据互相独立；不会自动跨设备同步。
- 卸载 App 可能删除本地数据库和图片，请先导出备份。
- 备份为 ZIP 文件，内含 `backup.json` 与 `images/` 目录；恢复时图片会解压回 App 私有目录并自动改写路径，旧版纯 JSON 备份（不含图片）同样可以恢复。
- 恢复备份会覆盖当前菜品、订单和店铺设置，请恢复前先导出当前数据。

## 建议的手工验收

1. 首次打开首页能看到演示菜品。
2. 搜索菜品、切换分类均能正常工作。
3. 添加两种菜品，进入购物车，修改数量，检查总价。
4. 提交订单后，订单页显示订单记录；强制关闭并重开 App，记录仍存在。
5. 在管理页新增菜品，选择本地图片并保存；首页可看到菜品和图片。
6. 修改店铺名、欢迎语和主题色，返回点餐页检查变化。
7. 导出 ZIP 备份，卸载或清空 App 数据后恢复备份，检查菜品、图片与订单（含订单状态）全部回来。
8. 在订单页点开一笔订单，推进状态（已下单 → 制作中 → 已完成），再删除它并确认二次弹窗。
9. 开启飞行模式后重复点餐，确认核心功能仍可使用。

## 目录结构

```text
LocalBite/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/localbite/app/
│       │   ├── MainActivity.kt
│       │   └── data/
│       │       ├── AppDatabase.kt
│       │       └── Entities.kt
│       └── res/values/styles.xml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## MVP 暂未实现

- 订单支付、打印小票、多人同步
- 销量统计、库存管理
- 自动化 UI 测试与签名发布包
