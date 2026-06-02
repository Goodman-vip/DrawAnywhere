# DrawAnywhere 重构计划

## 架构目标

**混合架构**：原生 View 画布 + Compose 工具条。
- 画布层：`NativeDrawCanvasView extends View`，`onDraw` 渲染 + `onTouchEvent` 手势处理
- 工具条层：保留 Compose（`DrawToolbar` 及其子组件不变）
- 理由：Canvas 直接 `invalidate()` 无 snapshot 开销；`MotionEvent` 同步处理无协程延迟

## 已决问题

1. **步骤 0c**：跳过。TileService 暂时能用。
2. **步骤 1 画布背景**：不刷，依赖 overlay 窗口自带透明 `PixelFormat.TRANSLUCENT`。
3. **步骤 3 CustomLifecycleOwner**：精简为仅 LifecycleOwner，移除 ViewModelStoreOwner 和 SavedStateRegistryOwner。
4. **步骤 5 PathWrapper.id**：直接删除，从未使用。

---

## 步骤清单

### 步骤 0a：修复 `DrawController.updateLatestPath()` penConfig 初始化检查

**文件**：`DrawController.kt:105-115`

**问题**：`updateLatestPath()` 直接访问 `penConfig.penType`，但 `penConfig` 是 `lateinit var`。如果在 `setPenConfig()` 之前被调用会抛 `UninitializedPropertyAccessException`。`createPath()` 在第 118 行有 `isInitialized` 检查，但 `updateLatestPath()` 没有。

**方案**：在 `updateLatestPath` 开头加 `if (!this::penConfig.isInitialized) throw IllegalStateException(...)`，与 `createPath` 完全一致。宁愿崩溃暴露调用链 bug，不悄悄吞错误。

---

### 步骤 0b：修复 `MainService.onCreate()` 中 `runBlocking` 阻塞主线程

**文件**：`MainService.kt:68`

**问题**：`runBlocking { preferencesMgr.getSavedUiState() to preferencesMgr.getSavedServiceState() }` 在 `onCreate`（主线程）阻塞等待 DataStore I/O，可能导致 ANR。

**方案**：用 `UiState()` 和 `ServiceState()` 默认值启动 ViewModel。然后在协程中异步加载已保存的状态，通过 `viewModel` 方法更新。

---

### 步骤 1：创建 `NativeDrawCanvasView`

**文件**：新增 `app/src/main/java/com/shezik/drawanywhere/view/canvas/NativeDrawCanvasView.kt`

**内容**：
- `extends View`，构造函数接收 `DrawController`
- `onDraw(canvas)`：遍历 `controller.pathList`，对每个 `PathWrapper` 调用 `canvas.drawPath()`。支持 viewport 缩放变换（`canvas.scale/translate`）。**不做 background fill**（overlay 窗口自带透明）。
- `onTouchEvent(event: MotionEvent)`：
  - 通过 `event.getToolType()` 区分 `TOOL_TYPE_STYLUS` / `TOOL_TYPE_FINGER`
  - 通过 `event.getButtonState()` 检测 `BUTTON_STYLUS_PRIMARY/SECONDARY`
  - 通过 `event.getPointerCount()` 判断单指/多指
  - 单指：调用 `DrawController.createPath/updateLatestPath/finishPath`（与现有 `StylusAwareDrawing` 逻辑一致）
  - 双指+：骨架占位（后续接入手势系统），当前返回 `true` 但不做缩放
- 坐标转换：触摸坐标是屏幕坐标，需通过 `CanvasViewport.screenToCanvas()` 转换（`CanvasViewport` 初值 scale=1，后续步骤接入）

**不添加的内容**：
- `InputSource`/`GestureMapping` 等 model 层类型（步骤 5+ 才需要）
- `GestureDispatcher`（当前只有一个手势模式，不需要分发）

---

### 步骤 2：修改 `MainService` 接入 `NativeDrawCanvasView`

**文件**：`MainService.kt`

**修改**：
1. `canvasView` 类型从 `View` 改为 `NativeDrawCanvasView`
2. 移除 `canvasView = ComposeView(this).apply { setContent { DrawCanvas(...) } }`
3. 改为 `canvasView = NativeDrawCanvasView(this, drawController)`
4. `toolbarView` 的 ComposeView 保持不动
5. 修复 toolbar 位置校验 Bug：用 `toolbarView.post { ... }` 延迟到 layout 完成后执行首次位置校验，替代当前 `positionValidated` 标志的二次校验机制
6. 统一 `CoroutineScope`：创建 `private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())`，所有协程通过它启动，`onDestroy` 中 `serviceScope.cancel()`
7. Canvas 的 `LayoutParams` 中移除 `FLAG_NOT_TOUCHABLE` 默认值（原生 View 需要通过 `onTouchEvent` 接收触摸），透传模式通过 `canvasView.isTouchEnabled = false` 或覆写 `onInterceptTouchEvent` 实现

---

### 步骤 3：删除旧代码，精简生命周期

**删除**：
- `DrawCanvas.kt` — 被 `NativeDrawCanvasView` 替代
- `StylusAwareDrawing.kt` — 手势逻辑移入 `NativeDrawCanvasView.onTouchEvent`
- `StrokeModifier` enum — 逻辑移入 `NativeDrawCanvasView` 内部

**精简**：
- `CustomLifecycleOwner.kt` → `ToolbarLifecycleOwner.kt`
  - 仅实现 `LifecycleOwner`（有 `LifecycleRegistry`）
  - 移除 `ViewModelStoreOwner` 和 `SavedStateRegistryOwner`（toolbar ComposeView 不需要）
  - 方法：`start()` → `RESUMED`，`stop()` → `DESTROYED`
- `MainService.kt` 中对应的调用改为 `toolbarLifecycleOwner.start()` / `stop()`

---

### 步骤 4：拆分 `DrawToolbar.kt`

**文件**：当前 1061 行 → 拆为以下文件（均在 `view/toolbar/` 下）：

| 文件 | 内容 | 来源行 |
|---|---|---|
| `ToolbarButton.kt` | `ToolbarButton` data class + `createAllToolbarButtons()` | 70-81, 951-1061 |
| `ToolbarCard.kt` | `DraggableToolbarCard` Composable | 154-196 |
| `ToolbarUi.kt` | `DrawToolbar` + `ToolbarButtonsContainer` + `RenderButton` | 88-389 |
| `ToolbarButtons.kt` | `AnimatedToolbarButton` + `PopupToolbarButton` + `ToolbarExpandButton` | 391-566 |
| `ToolbarLayout.kt` | 消除 HORIZONTAL/VERTICAL 重复的辅助函数 | 重构 |
| `PenTypeSelector.kt` | `PenTypeSelector` Composable | 568-616 |
| `ColorPicker.kt` | `ColorSwatchButton` + `ColorPicker` | 618-690 |
| `PenControls.kt` | `PenControls` + `SliderControl` | 692-724, 860-901 |
| `ToolbarSettings.kt` | `ToolbarControls` + `CheckboxControl` + `AboutScreen` | 726-948 |
| `ToolbarOrientation.kt` | `ToolbarOrientation` enum（从 `DrawToolbar.kt` 和 `DrawViewModel.kt` 共同引用） | 83-85 |

**不变**：
- 所有 Composable 签名和参数不变
- 所有可见行为不变

---

### 步骤 5：`PathWrapper` → `DrawObject.Stroke` sealed class

**文件**：`DrawController.kt` → 新增 `model/DrawObject.kt`

**内容**：
```kotlin
sealed class DrawObject {
    data class Stroke(
        val points: SnapshotStateList<Offset>,
        val color: Color,
        val width: Float,
        val alpha: Float,
        val transform: ObjectTransform = ObjectTransform(),
        private var _cachedPath: MutableState<Path?> = mutableStateOf(null),
        private var cachedPathInvalid: MutableState<Boolean> = mutableStateOf(true),
    ) : DrawObject() {
        // cachedPath getter 逻辑不变
        // rebuildPath 改为返回 Path（非 MutableState<Path>）
        // releasePath / invalidatePath 逻辑不变
    }
    // Shape: 先不实现，后续步骤补充
}
data class ObjectTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)
```

**改动**：
- 删除 `PathWrapper.id`（UUID 从未使用）
- `rebuildPath()` 返回 `Path` 而非 `MutableState<Path>`，移除 `@Suppress("UNCHECKED_CAST")`
- `DrawController._pathList` 类型改为 `SnapshotStateList<DrawObject.Stroke>`
- `DrawAction` 中的 `PathWrapper` 替换为 `DrawObject.Stroke`
- 所有调用方适配新类型名

---

### 步骤 6：`DrawController` 拆出 `UndoRedoManager`

**文件**：`DrawController.kt` → 新增 `controller/UndoRedoManager.kt`

**内容**：
```kotlin
class UndoRedoManager(private val maxDepth: Int = 50) {
    private val undoStack = mutableListOf<DrawAction>()  // 从 DrawController 移出
    private val redoStack = mutableListOf<DrawAction>()

    val canUndo: StateFlow<Boolean>
    val canRedo: StateFlow<Boolean>

    fun push(action: DrawAction)     // 原 addToUndoStack
    fun pop(): DrawAction?           // 原 undo 的核心逻辑
    fun pushRedo(action: DrawAction) // 原 redo 的回推
    fun popRedo(): DrawAction?       // 原 redo 的核心逻辑
    fun clearRedo()                  // 原 redoStack.clear()
}
```

**改动**：
- `DrawController.undo()` 调用 `undoManager.pop()` 获取 action，然后执行 pathList 操作
- `DrawController.redo()` 同理
- `DrawController` 不再直接持有 undoStack/redoStack

---

### 步骤 7：`DrawViewModel` 拆分

**文件**：`DrawViewModel.kt` → 新增 `viewmodel/ToolbarViewModel.kt` + `viewmodel/CanvasViewModel.kt`

**内容**：

- `ToolbarViewModel`：`toolbarActive`、`toolbarOrientation`、`firstDrawerOpen`、`secondDrawerOpen`、`secondDrawerPinnedButtons`、`dimmingJob`、`resetToolbarTimer()`、drawer toggle/set 方法、pin 方法
- `CanvasViewModel`：`canvasVisible`、`canvasPassthrough`、`autoClearCanvas`、`visibleOnStart`、`viewport`（含 `zoomLocked`）、画布开关/透传方法
- `DrawViewModel`：保留为顶层编排，委托给 `ToolbarViewModel` 和 `CanvasViewModel`，持有 `DrawController`、stroke 方法、pen config 方法

**不变**：所有外部接口（`MainService` 使用的 `uiState` / `serviceState` / `canUndo` 等）保持原有命名和行为。

---

### 步骤 8：修复低级问题

| 文件 | 问题 | 修改 |
|---|---|---|
| `MainService.kt` | `preferencesMgr` 缩写 | 改为 `preferencesManager` |
| `DrawViewModel.kt` | `previousPenType` / `isStrokeDown` 为 public var | 改为 `private` |
| `DrawViewModel.kt` | `delay(3000L)  // 5 seconds` 注释错 | 注释改为 `// 3 seconds` |
| `DrawToolbar.kt` | `AboutScreen` 为 public，其他 Composable 均为 private | 改为 `private` |
| `DrawController.kt` | `cachedPathInvalid` 无 `_` 前缀，与 `_cachedPath` 不一致 | 加 `_` 前缀或两者统一 |
| `DrawController.kt:52` | `or` 而非 `||` | 改为 `||`（惯用短路求值） |
| `InkEraser24Px.kt:70-82` | dummy path 数据（重复 `moveTo/lineTo/quadTo(480,480)`） | 删除 dummy path 段落 |

---

## 每一步的验收标准

- 编译通过（`./gradlew assembleRelease`）
- 不引入新的 lint warning（有意的 suppress 除外）
- 功能行为与重构前一致
- Git commit 含步骤编号和一句话总结
