# :ocr_module —— OCR 独立模块

把屏幕 OCR 识别能力从主应用（`:app`）中剥离出来的独立 library 模块。
主 module **不直接依赖本模块的类**，仅通过 `:ocr_api` 定义的接口 + 滴滴 DRouter
在运行时发现和调用，做到「主 module 不集成本模块也能独立编译运行」。

## 职责边界

- 持有并管理 `OcrEngine`（基于 PaddleOCR4Android / PP-OCRv4 的离线推理引擎）
- 接收 `Bitmap`，返回结构化识别结果 `OcrResult`（文本、置信度、包围盒）
- 实现 `ocr_api` 中的 `IOcrService` 接口，以 DRouter `@Router` 服务形式对外暴露
- 打包 OCR 模型资源 `assets/models/`（ch_PP-OCRv4 / ch_PP-OCRv2）

**不包含**：键盘监听、定位、上传、UI —— 这些全部留在 `:app`。

## 模块依赖关系

```
:app  ──(编译期依赖 :ocr_api)──►  :ocr_api   (IOcrService / OcrResult / OcrContextHolder)
:ocr_module ──(编译期依赖 :ocr_api)──► :ocr_api
:ocr_module ──(依赖)──► :PaddleOCR4Android
:app  ──(useOcr=true 时才依赖 :ocr_module)──► :ocr_module
:app  ──(运行时 DRouter.build(path).getService())──► :ocr_module 的 OcrServiceImpl
```

关键点：`IOcrService`、`OcrResult`、`OcrContextHolder` 都定义在 `:ocr_api`，
`:app` 与 `:ocr_module` 只共享 `:ocr_api`，互不 import 对方的具体实现类。

## DRouter 服务定义

- 接口（契约，在 `:ocr_api`）：`com.android.batteryoptimization.ocr.api.IOcrService`
  - 路由路径常量：`IOcrService.PATH = "/ocr/service"`
  - 方法：`loadModels()` / `isReady` / `runInference(bitmap)` / `destroy()`
- 实现（本模块）：`com.android.batteryoptimization.ocr.OcrServiceImpl`
  - 注解：`@Router(path = IOcrService.PATH)`
  - 由 DRouter 以单例形式管理

主 module 取实例：

```kotlin
val ocr: IOcrService? = OcrServiceLocator.get()   // 内部 = DRouter.build(IOcrService.PATH).getService()
```

> `OcrServiceLocator.get()` 对「找不到路由」做了 null 兜底：
> 当 `useOcr=false`（不集成本模块）时返回 null，调用方需 null-safe 处理，OCR 优雅降级关闭。

## 独立调试

1. **作为 OCR 模块单独验证**：确保 `gradle.properties` 中 `useOcr=true`，
   `:app` 会依赖本模块，DRouter 能发现 `OcrServiceImpl`，OCR 正常工作。
2. **验证主 module 独立运行**：把 `useOcr=false`，重新 sync + build，
   此时 `:app` 不再依赖本模块，DRouter 找不到 `/ocr/service` 返回 null，
   键盘监听 / 定位 / 上传等其余功能不受影响，OCR 部分自动关闭。

## 模型资源

```
ocr_module/src/main/assets/models/
├── ch_PP-OCRv4/   (cls.nb, det.nb, rec.nb)   ← 默认使用
└── ch_PP-OCRv2/   (cls.nb, det_db.nb, rec_crnn.nb)
```

由 `OcrEngine` 在 `loadModels()` 时从 `assets` 拷贝到内部存储后加载。

## 初始化顺序（重要）

主 module 的 `App.onCreate()` 中必须：

```kotlin
OcrContextHolder.init(this)   // 注入 Application Context 给 OCR 模块
DRouter.init(this)            // 初始化 DRouter，使其能发现 @Router 服务
```

`OcrContextHolder.init` 必须在任何 `OcrServiceLocator.get()` 之前调用，
否则 OCR 模块拿不到 Context，推理会失败。
