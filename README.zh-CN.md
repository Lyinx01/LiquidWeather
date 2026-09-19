# 琉璃天气 (Liquid Weather)

[English](README.md) · **简体中文**

一款以 **Liquid Glass（液态玻璃）** 为设计语言的安卓天气应用——磨砂玻璃卡片实时折射背后的动态天空，同时提供桌面小部件、四种天气数据源和四种界面语言。

<p align="left">
  <img alt="Platform" src="https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white">
  <img alt="UI" src="https://img.shields.io/badge/UI-XML%20Views%20%2B%20Material%203-757575">
  <img alt="Glass" src="https://img.shields.io/badge/Glass-Liquid--Glass--Android-4A9EE8">
</p>

## 截图

| 白天 | 夜间 | 小部件 |
| :---: | :---: | :---: |
| _（待补充截图）_ | _（待补充截图）_ | _（待补充截图）_ |

## 功能

**天气**
- 实时天气：温度、体感、天气现象、今日最高/最低温
- 逐小时预报（48 小时，含降水概率）
- 每日预报（最多 15 天，含渐变温度区间条）
- 详细信息网格：湿度、风、气压、云量、紫外线、舒适度、降水强度、日出/日落
- 空气质量卡片（AQI + PM2.5 / PM10 / O₃ / NO₂ / SO₂ / CO）
- 气象预警卡片，按预警级别自动给玻璃染色（点击展开全文）
- 离线缓存——无网络时展示上次成功获取的数据

**设计**
- 液态玻璃卡片实时折射背后天空，带色散（边缘彩虹）与传感器高光
- 随天气与昼夜变化的动态天空：云层飘移、夜间星点闪烁、雨丝下落、晴天光晕呼吸
- 悬浮控件采用 iOS 式按压反馈：放大 + 高光淡入 + 回弹
- iOS 风格矢量图标集（类 SF Symbols 的饱满形状），无位图资源
- 当前天气信息块采用 iOS 式居中布局

**桌面小部件**
- iOS 风格布局：城市名 + 定位箭头、大号温度、天气状况、最高/最低，底部六列逐小时
- 渐变背景随天气切换（晴 / 夜 / 多云 / 雨 / 雪 / 雾）
- 日出与日落所在时段用具体时刻与专属图标替换小时标签
- 文本自适应字号，适配任意桌面网格（已在 ColorOS 4×6 网格验证）
- 通过 WorkManager 每 30 分钟刷新，添加部件时立即拉取，应用内数据更新时同步刷新

**城市与设置**
- GPS 定位基于 `LocationManager`（不依赖 Google Play Services）+ 内置 190+ 中国城市库
- 多城市保存与快速切换，长按城市标签删除
- 长按定位按钮可立即添加/切换到当前位置
- 可切换数据源、API Key、温度单位（°C/°F）与语言

**多语言**
- 简体中文（默认）、繁體中文、English、日本語——覆盖界面文案、天气描述、风向、AQI 等级、错误提示与时间格式
- 天气接口按所选语言请求，描述与界面语言保持一致
- Android 13+ 支持系统级「按应用设置语言」

## 天气数据源

| 数据源 | 覆盖范围 | 免费额度 | 说明 |
| --- | --- | --- | --- |
| **彩云天气**（默认） | 中国及亚太 | 每日有限调用 | 每次刷新只请求一次合并接口 `weather.json`；含空气质量与预警 |
| **和风天气** | 中国 | 1000 次/日 | 实时 + 7 天 + 24 小时 + 预警；新版控制台账号需填写专属 API Host |
| **AccuWeather** | 全球 | 50 次/日 | 实时 + 12 小时 + 5 日；每个城市首次解析一次位置 Key 后缓存 |
| **OpenWeather** | 全球 | 1000 次/日 | 实时 + 5 天/3 小时；每日数据由 3 小时步长聚合得到 |

所有 Key 仅保存在本机 `SharedPreferences`，且只发送给对应的天气服务商。

## 快速开始

### 环境要求
- Android Studio（内置 JDK 17）
- Android SDK 36，`minSdk` 26（Android 8.0）
- Android 8.0+ 的设备或模拟器

### 构建
```bash
git clone https://github.com/Lyinx01/LiquidWeather.git
cd LiquidWeather
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
```
或用 Android Studio 打开项目直接 Run。首次同步会自动下载 Gradle 8.11.1 与全部依赖（含 JitPack 上的玻璃库）。

### 配置 API Key
应用至少需要一个数据源的 Key。进入**设置 → 数据源**，选择服务商、粘贴 Key 并保存。

- **彩云天气** — 在 [dashboard.caiyunapp.com](https://dashboard.caiyunapp.com/) 申请免费 Token
- **和风天气** — 在 [console.qweather.com](https://console.qweather.com/) 申请免费 Key。2024 年后注册的账号还需填写专属 **API Host**（控制台 → 设置 → API Host），形如 `xxxxxxxx.re.qweatherapi.com`。
- **AccuWeather** — 在 [developer.accuweather.com](https://developer.accuweather.com/) 申请免费 Key
- **OpenWeather** — 在 [home.openweathermap.org](https://home.openweathermap.org/api_keys) 申请免费 Key

## 技术栈

Kotlin · XML View 体系（该玻璃库提供的是 View 组件，非 Compose）· Material 3 · ViewBinding ·
ViewModel + LiveData · Coroutines · Retrofit + Gson · WorkManager · JitPack

## 架构

分层思路参考 [breezy-weather](https://github.com/breezy-weather/breezy-weather)：数据源 → 领域模型 → 卡片式 UI。

```
app/src/main/java/com/liuli/weather/
├── App.kt                        # Application：初始化 AppCtx
├── MainActivity.kt               # 主页：玻璃卡片渲染、定位流程、下拉刷新
├── SettingsActivity.kt           # 数据源 / Key / 单位 / 语言设置
├── data/
│   ├── remote/                   # 四个服务商的 Retrofit 接口与 Gson DTO
│   ├── source/                   # WeatherSource 抽象 + 各服务商实现
│   ├── repository/               # WeatherRepository：数据源分发 + 按源隔离缓存
│   ├── location/                 # LocationManager 定位封装
│   ├── city/                     # 内置城市库（assets/cities.json）
│   ├── model/                    # 领域模型（Weather、CurrentWeather 等）
│   └── prefs/                    # SettingsStore：Key、城市、单位、语言
├── ui/
│   ├── main/                     # ViewModel、UiState、小时/每日/详情/预警适配器
│   ├── city/                     # 城市选择 BottomSheet
│   └── common/                   # GlassPressEffect：iOS 式按压动画
├── widget/                       # 桌面小部件 + WorkManager 刷新任务
└── util/                         # 天气代码映射、时间工具、单位换算、API 语言
```

## 液态玻璃的接入要点

玻璃库会捕获 `LiquidGlassView` 背后的视图并做折射。三条规则决定了本项目的实现方式：

1. **背景必须有细节。** 纯色与平滑渐变看不出折射，因此应用绘制了分层天空——渐变底色、柔光径向云层、星点、日月光晕——作为捕获源。
2. **采样按角色拆分。** 悬浮控件采样整个根布局（因此能折射滚动到下方的内容，类似 iOS 导航栏），且只在滚动期间或按住时开启；内容卡片采样静态天空层，按低频定时器刷新。这样滚动保持流畅，卡片又能跟随飘移的云层。
3. **按压反馈用 `glassTint`。** 直接叠加颜色层会干扰折射画面，因此按压高光通过 ARGB 染色淡入实现，同时视图放大。

## 多语言

| 语言 | 资源目录 |
| --- | --- |
| 简体中文（默认） | `values/strings.xml` |
| English | `values-en/strings.xml` |
| 繁體中文 | `values-zh-rTW/strings.xml` |
| 日本語 | `values-ja/strings.xml` |

`ApiLang` 会把应用语言映射为各服务商的 `lang` 参数，因此天气描述与界面语言一致。新增一种语言只需添加一个 `values-<tag>/strings.xml` 并在 `res/xml/locales_config.xml` 中登记。

## 说明

- 风速单位为 km/h，气压为 hPa（按需从接口原始单位换算）。
- 只需填写当前选用数据源的 Key。
- 仅供学习与个人使用。天气数据归各服务商所有，玻璃效果归 [Liquid-Glass-Android](https://github.com/QWEA0/Liquid-Glass-Android) 作者所有。
