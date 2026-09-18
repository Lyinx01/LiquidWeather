# 琉璃天气 (LiquidWeather)

一款 **Liquid Glass（液态玻璃）风格** 的安卓天气应用。

- **天气数据**：多数据源，设置页可切换
  - 彩云天气 API v2.6（weather.json 合并接口，中国及亚太，含 AQI 与预警）
  - AccuWeather（全球城市，免费档：当前 + 12 小时 + 5 日预报，每日 50 次调用）
- **UI 库**：[QWEA0/Liquid-Glass-Android](https://github.com/QWEA0/Liquid-Glass-Android)（JitPack：`com.github.QWEA0:liquidglass:v2.0.10`）
- **架构参考**：开源项目 [breezy-weather](https://github.com/breezy-weather/breezy-weather)（数据源 → 领域模型 → 卡片式主页的分层思路）

## 功能

- 实时天气：温度、体感、天气现象、最高/最低温
- 逐小时预报（48 小时，含降水概率）与 15 日预报（含温度区间条）
- 详细信息网格：湿度、风、气压、云量、紫外线、舒适度、降水强度、日出/日落
- 空气质量卡片（AQI + PM2.5/PM10/O₃/NO₂/SO₂/CO）
- 气象预警卡片（按预警级别自动给玻璃染色，点击展开全文）
- 城市管理：GPS 定位（LocationManager，不依赖 Google 服务）+ 内置 190+ 中国城市搜索，多城市管理
- 天气/昼夜感知的动态天空背景（云层飘移动画），玻璃卡片实时折射背景
- 天气缓存：无网络时展示上次数据

## 技术栈

Kotlin · XML View 体系（该玻璃库为 View 组件，非 Compose）· Material 3 · ViewBinding ·
ViewModel + LiveData · Coroutines · Retrofit + Gson · JitPack

## 构建步骤

1. 安装 [Android Studio](https://developer.android.com/studio)（JDK 17 内置）。
2. `File → Open` 打开本目录 `LiquidWeather`。
3. 首次同步会自动下载 Gradle 8.11.1 与依赖（含 JitPack 上的 liquidglass 库）。
4. 连接设备或启动模拟器（API 26+），点击 Run。
   - 命令行构建：`gradle wrapper && gradlew assembleDebug`（仓库未内置 wrapper jar，可由 Android Studio 自动生成或本机 gradle 生成一次）。

## 配置 API Key（必须，二选一或都配）

**彩云天气（默认数据源）**
1. 前往 [dashboard.caiyunapp.com](https://dashboard.caiyunapp.com/) 注册并申请 **免费 Token**。
2. 设置页粘贴 Token 并保存。

**AccuWeather（全球城市）**
1. 前往 [developer.accuweather.com](https://developer.accuweather.com/) 注册应用，获得 API Key（免费档 **每日 50 次调用**；每次刷新消耗 3 次——当前天气 + 逐小时 + 每日）。
2. 设置页粘贴 Key 并将数据源切换为 AccuWeather，保存后主页会自动刷新。
3. 首次使用某坐标时会调用一次位置解析（geoposition/search）换取 `locationKey` 并缓存到该城市，之后不再消耗。
4. AccuWeather 免费档不含空气质量与预警，对应卡片自动隐藏；图标码已映射到应用内 skycon 图标体系。

所有 Key 均保存在本机 SharedPreferences，仅用于请求对应天气 API。

## Liquid Glass 使用要点（来自该库的约束）

- 玻璃卡片（`com.example.liquidglass.LiquidGlassView`）必须叠在**有细节内容的背景之上**才能看出折射效果——本项目为此实现了带太阳/月亮/星点/云层的分层天空背景与飘移动画。
- 背景滚动或动画时必须开启 `enableDynamicBackground = true`，否则玻璃只采样一次。
- 预警卡片通过 `glassTint`（ARGB，alpha 即染色强度）按预警级别着色。

## 目录结构（参考 breezy-weather 分层）

```
app/src/main/java/com/liuli/weather/
├── MainActivity.kt              # 主页：玻璃卡片渲染、定位流程、下拉刷新
├── SettingsActivity.kt          # Token 设置
├── data/
│   ├── remote/                  # 彩云 API Retrofit 接口 + Gson DTO（v2.6）
│   ├── repository/              # WeatherRepository：请求编排 + DTO→领域映射 + 文件缓存
│   ├── location/                # LocationManager 定位封装
│   ├── city/                    # 内置城市库（assets/cities.json）
│   ├── model/                   # 领域模型（Weather / CurrentWeather / …）
│   └── prefs/                   # SettingsStore：Token、多城市、当前位置
├── ui/
│   ├── main/                    # ViewModel、UiState、小时/每日/详情/预警适配器、TempRangeBar
│   └── city/                    # 城市选择 BottomSheet + 城市列表适配器
└── util/                        # skycon→图标/文案/背景映射、时间工具
```

## 说明

- 坐标接口遵循彩云 v2.6：`GET /v2.6/{token}/{lng},{lat}/realtime.json|hourly.json|daily.json|alert.json`。
- 详细信息中的风速单位为 km/h、气压 hPa（由接口 Pa 换算）。
- 仅供学习交流使用；数据归彩云天气所有，UI 效果归 Liquid-Glass-Android 作者所有。
