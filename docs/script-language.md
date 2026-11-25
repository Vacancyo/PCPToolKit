# PCP 脚本语言

脚本采用“顺序步骤 + 内置逻辑”的混合 DSL。默认按行执行，支持变量、表达式、条件、循环和图像识别。脚本不使用 `endif`、`endfor`，块由缩进结束。

## 基本规则

- 每个语句占一行。
- 块语句以 `:` 结尾，下一行开始缩进。
- 推荐使用 4 个空格缩进。制表符按 4 个空格计算。
- `#` 开始注释，可写整行注释或行尾注释。
- 所有屏幕坐标和识别区域使用相对坐标，范围为 `[0, 1]`。
- `x = 0` 表示屏幕左边缘，`x = 1` 表示右边缘。
- `y = 0` 表示屏幕上边缘，`y = 1` 表示下边缘。

## 值类型

| 类型 | 示例 |
| --- | --- |
| 整数 | `123` |
| 浮点数 | `1.05` |
| 字符串 | `"text"` |
| 布尔值 | `true`、`false` |
| 变量 | `$var` |
| 数组 | `["A", "B"]` |
| 图像对象 | `&MAIN.DAILY` |

数字统一按浮点数计算，输出时省略无意义的小数位。

## 表达式

支持的运算：

```text
+  -  *  /
==  !=  <  <=  >  >=
and  or  not
&&  ||  !
```

`+` 在任一操作数是字符串时执行拼接；两个数组相加时执行数组合并。

运算符两侧空格不是必需。优先级从高到低：

```text
*  /
+  -
==  !=  <  <=  >  >=
!  not
&&  and
||  or
```

可以使用括号控制优先级：

```pcp
let $value = (1 + 2) * 3

if ($a == 1) && ($b != 3):
    print "both"

if !($a < 5):
    print "not less"

if $a<5:
    print "no spaces"
```

条件按以下规则判断真假：`false`、`0`、空字符串、空数组为假，其余为真。

## 语句

```text
# 注释
let $name = <expr>
print <expr>
wait <ms>
```

`let` 同时用于创建和更新变量。内置变量只读。

## 图像识别

```text
exist rect <obj> <x> <y> <w> <h>
exist coord <obj> <x0> <y0> <x1> <y1>
match rect <obj> <x> <y> <w> <h> <timeout>
match coord <obj> <x0> <y0> <x1> <y1> <timeout>
```

- `exist` 立即识别一次。
- `match` 在超时时间内重复识别，每两次识别之间间隔最多 100 ms。
- `rect` 的后四个参数是 `x y w h`，其中 `w`、`h` 是相对宽高。
- `coord` 的后四个参数是 `x y right bottom`，适合直接写矩形两个角。
- `timeout` 为毫秒。`0` 表示只识别一次。
- 区域必须位于屏幕内，宽高必须为正数。
- `match coord` 使用左上角和右下角坐标，不额外指定宽高。

识别结果写入以下内置变量：

| 变量 | 含义 |
| --- | --- |
| `$FOUND` | 最近一次识别是否命中 |
| `$MATCH_X` | 命中模板左上角 x，相对坐标 |
| `$MATCH_Y` | 命中模板左上角 y，相对坐标 |
| `$MATCH_CX` | 命中模板中心 x，相对坐标 |
| `$MATCH_CY` | 命中模板中心 y，相对坐标 |
| `$MATCH_W` | 命中模板相对宽度 |
| `$MATCH_H` | 命中模板相对高度 |
| `$MATCH_SCORE` | 最近一次匹配相似度，范围 `[0, 1]` |

未命中时，坐标和相似度均重置为 `0`。

## 操作

```text
tap <x> <y>
swipe <x1> <y1> <x2> <y2> <duration>
```

坐标使用相对坐标。`duration` 为毫秒。

## 控制流

```text
if <expr>:
    ...
elif <expr>:
    ...
else:
    ...
```

```text
for $item in $list:
    ...

while <expr>:
    ...
```

`break`、`continue` 只能出现在循环内。`stop` 立即结束脚本。

## 图像对象

图像对象名由图片目录和文件名决定。点号分隔路径，名称不区分大小写。

```text
&MAIN.DAILY
```

对应资源路径：

```text
images/main/daily.png
```

## 示例

```text
exist rect &MAIN.DAILY 0 0 1 1
if $FOUND:
    print "daily found, score=" + $MATCH_SCORE
    tap $MATCH_CX $MATCH_CY
else:
    print "daily not found"
```

## 当前实现边界

- 词法分析、语法分析、解释执行和模板匹配核心均由 C++17 实现，位于 `libs/script/`。
- Android 通过 JNI 门面 `com.pcptoolkit.app.feature.script.ScriptEngine` 调用 C++ 核心。
- 模板匹配通过 `com.pcptoolkit.app.feature.script.ScriptVision` 的 JNI 门面调用。
- 已接入 `&MAIN.DAILY` 资源路径规则和 `daily.png` 原生单元测试。
- 已接入悬浮窗、系统文件夹脚本读取、无障碍截屏、点击和滑动手势。
- 单个脚本最多执行 1,000,000 个语句，防止无限循环卡死。
- 当前模板匹配不自动多尺度搜索；模板和游戏画面的显示缩放需一致。

## Android 运行链路

- 主页“开启悬浮窗”需要系统悬浮窗权限。
- 悬浮球展开后选择“我的脚本”文件夹中的 `.pcs` 文件，并点击“运行脚本”。
- 运行脚本还需要启用“PCP 脚本自动化”无障碍服务，用于截屏和模拟手势。
- 脚本运行要求 Android 11 或更高版本。
- 运行开始后面板自动收起；悬浮球仍可重新展开并点击“停止”。
- 脚本坐标始终相对当前游戏画面。PvZ2 脚本需在横屏游戏状态下运行，竖屏时会拒绝启动。
- “打开日志”会显示半透明日志控制台：横屏固定在右下角，竖屏显示为底部通栏。
- 悬浮窗面板整体可上下滚动；日志控制台可按住日志标题移动到任意位置。
- 每次识别截屏前会短暂隐藏 PCP 悬浮层，避免把悬浮球或日志窗口识别进游戏画面。

## C++ 构建与测试

```powershell
cmake -S . -B build/script -DBUILD_TESTING=ON
cmake --build build/script --parallel
ctest --test-dir build/script --output-on-failure
```
