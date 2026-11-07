# Android Launch Lifecycle Demo

A small Android app that shows **what happens when you open an app**. It covers every step, from the
launcher tap, through the system and Zygote, to the first frame and "fully drawn".

The app writes its own log lines (tag `LaunchDemo`) at each step of the launch. Two Logcat filters
mix these lines with the system's own launch lines. You see the whole launch in one place, in order.

## What you will see

This is real output from a cold start on an Android 17 (API 37) emulator.
The app was started from Android Studio, so there is no launcher-tap line at the top.
The call-path lines are shortened here (`↳ …`). See [callPath()](#callpath--who-called-me) for the full paths.

```
08:48:18.555  ActivityTaskManager  START u0 {act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] … cmp=eu.anifantakis.myapplication/.MainActivity} …
08:48:18.562  WindowManager        setClientSurface Surface(name=VRI-Splash Screen eu.anifantakis.myapplication#374) … Splash Screen eu.anifantakis.myapplication
08:48:18.572  Zygote               Forked child process 6022
08:48:18.572  ActivityManager      Start proc 6022:eu.anifantakis.myapplication/u0a231 for next-top-activity {…/eu.anifantakis.myapplication.MainActivity}
08:48:18.574  libprocessgroup      Created cgroup /sys/fs/cgroup/apps/uid_10231/pid_6022
08:48:18.577  Zygote               Process 6022 created for eu.anifantakis.myapplication
08:48:18.798  LaunchDemo           ▶ Application.attachBaseContext(base = ContextImpl@28d3483)   [pid 6022 · main · +234ms]
                                         ↳ …
08:48:18.809  LaunchDemo           ▶ ContentProvider.onCreate — before Application.onCreate!   [pid 6022 · main · +253ms]
08:48:18.810  LaunchDemo           ▶ Application.onCreate   [pid 6022 · main · +254ms]
08:48:18.834  LaunchDemo           ▶ Activity.attachBaseContext(base = ContextImpl@c12e448)   [pid 6022 · main · +278ms]
08:48:18.840  LaunchDemo           ▶ Activity.onCreate   [pid 6022 · main · +283ms]
                                         ↳ …
08:48:18.869  LaunchDemo           ▶ Activity.onStart   [pid 6022 · main · +313ms]
08:48:18.876  LaunchDemo           ▶ Activity.onResume — window attached? false   [pid 6022 · main · +321ms]
08:48:19.057  WindowManager        setClientSurface Surface(name=VRI-eu.anifantakis.myapplication/eu.anifantakis.myapplication.MainActivity#380) …
08:48:19.092  ActivityTaskManager  Displayed eu.anifantakis.myapplication/.MainActivity for user 0: +543ms
08:48:19.126  LaunchDemo           ▶ onWindowFocusChanged(true)   [pid 6022 · main · +570ms]
08:48:19.512  ActivityTaskManager  Fully drawn eu.anifantakis.myapplication/.MainActivity: +962ms
```

Things to notice:

- **The splash screen comes first.** The system gives the splash screen a surface before the app
  process exists. The system draws the splash screen while Zygote creates the process.
- **`Start proc` and the Zygote lines appear only on a cold start.** A cold start means the app
  process does not exist yet. `u0a231` means user 0, app 231. That is uid `10231`, the same uid as in
  the cgroup path.
- **Content providers run before `Application.onCreate()`.** The order is
  `Application.attachBaseContext` → `ContentProvider.onCreate` → `Application.onCreate`.
  Libraries such as App Startup and Firebase use this to start themselves.
- **The Application and the Activity get different `Context` objects.** Look at the hashes:
  `ContextImpl@28d3483` and `ContextImpl@c12e448`.
- **In `onResume()` the window is not attached yet.** The Activity gets its surface (`setClientSurface … MainActivity`)
  after `onResume()`. Then the first frame is drawn (`Displayed`).
- **`Displayed` and `Fully drawn` are different.**
  `Displayed` is the time to the first frame (TTID, *time to initial display*).
  `Fully drawn` is the time until the app says "my content is ready" (TTFD, *time to full display*).
  The app pretends to load data for 400 ms. Then it calls `ReportDrawnWhen { loaded }`.
- **All `LaunchDemo` lines run in the same process (pid 6022), on the `main` thread.**

## Project layout

| File | What it does |
|---|---|
| [LaunchTrace.kt](app/src/main/java/eu/anifantakis/myapplication/LaunchTrace.kt) | The logger: `mark()`, `markOnce()`, `callPath()` and `Context.describe()`. |
| [MyApplication.kt](app/src/main/java/eu/anifantakis/myapplication/MyApplication.kt) | The `Application`. It marks `attachBaseContext` (with call path) and `onCreate`. |
| [DemoInitProvider.kt](app/src/main/java/eu/anifantakis/myapplication/DemoInitProvider.kt) | An empty `ContentProvider`. It shows that providers run before `Application.onCreate()`. |
| [MainActivity.kt](app/src/main/java/eu/anifantakis/myapplication/MainActivity.kt) | The `Activity`. It marks each lifecycle callback. Its Compose UI fakes a 400 ms load, then reports "fully drawn". |
| [AndroidManifest.xml](app/src/main/AndroidManifest.xml) | Registers `MyApplication`, `MainActivity` and `DemoInitProvider`. |

Requirements: minSdk 28, compileSdk and targetSdk 37.

## How to run it

1. Open the project in Android Studio.
2. Run the app once on a device or emulator (API 28 or higher).
3. Add one of the [Logcat filters](#logcat-filters) below.
4. Stop the app, so the next start is a cold start:

   ```
   adb shell am force-stop eu.anifantakis.myapplication
   ```

5. Clear Logcat (the trash-can icon in the Logcat toolbar).
6. Tap the app icon on the home screen.

> **Tip:** Tap the icon. Do not use the Run button. The `LAUNCHER_APP_LAUNCH_TAP` line comes from the
> launcher. The Run button starts the app through `adb shell am start`, so there is no tap line.

**Try a warm start too.** Press Home. Then open the app again.
There are no `Start proc`, Zygote or `Application` lines, because the process is still alive.
You see `Activity.onRestart` (with its call path), then `onStart` and `onResume`.

## Logcat filters

**How to add a filter in Android Studio:**

1. Open Logcat: **View › Tool Windows › Logcat**.
2. Select your device.
3. Replace the text in the filter field (by default `package:mine`) with one of the filters below.
4. Click the star at the end of the filter field to save the filter as a favorite.

**Filter syntax:**

- `tag=:X` — the tag is exactly `X`.
- `message:X` — the message contains `X`.
- `message~:X` — the message matches the regular expression `X`.
- `&` means AND. `|` means OR. Use `( )` to group parts.

### Filter 1 — works for any app

```
(tag=:ActivityTaskManager & message~:'START u0|Displayed|Fully drawn') | (tag=:ActivityManager & message:'Start proc') | tag=:Zygote | (tag=:libprocessgroup & message:'/apps/') | (tag=:WindowManager & message:'Splash Screen')
```

This filter uses only lines that Android itself writes. You do not need any code in the app.
Open any app from a cold start and watch its launch. The filter shows the launches of all apps.

What each part catches:

- `tag=:ActivityTaskManager & message~:'START u0|Displayed|Fully drawn'`
  - `START u0` — the system got the request to start an Activity (for user 0).
  - `Displayed` — the first frame is on screen. It shows the time since `START`.
  - `Fully drawn` — the app called `reportFullyDrawn()`. Only apps that call it show this line.
- `tag=:ActivityManager & message:'Start proc'` — the system asks Zygote for a new process.
  You see it only on a cold start. It shows the pid, the package and the uid.
- `tag=:Zygote` — Zygote creates (forks) the new app process.
  Zygote is the parent process of every app. It has the Android framework already loaded,
  so a copy of it starts fast.
- `tag=:libprocessgroup & message:'/apps/'` — the kernel cgroup for the new app process.
  A cgroup (control group) puts a process in a group, so the system can track and limit its CPU and memory.
- `tag=:WindowManager & message:'Splash Screen'` — the splash screen window. The system shows it
  while the app starts.

### Filter 2 — this demo app, with the full lifecycle

```
(tag=:StatsLog & message:LAUNCHER_APP_LAUNCH_TAP) | (tag=:ActivityTaskManager & message:myapplication & message~:'START u0|Displayed|Fully drawn') | (tag=:ActivityManager & message:'Start proc' & message:myapplication) | tag=:Zygote | (tag=:libprocessgroup & message:'/apps/') | (tag=:WindowManager & message:setClientSurface & message:myapplication) | tag=:LaunchDemo
```

This filter shows only this app's system lines, plus the app's own `LaunchDemo` lines.
You get the full launch, from the tap to "fully drawn".

`myapplication` matches the package name `eu.anifantakis.myapplication`.
If you rename the package, change `myapplication` in the filter too.

What is new, compared to filter 1:

- `tag=:StatsLog & message:LAUNCHER_APP_LAUNCH_TAP` — the launcher writes this when you tap an app
  icon. It is the first moment of the launch. Some launchers may not write it.
- `& message:myapplication` on the `ActivityTaskManager` and `ActivityManager` parts — keeps only
  this app's lines.
- `tag=:WindowManager & message:setClientSurface & message:myapplication` — a window of the app gets
  its surface (the memory buffer that the window draws into). You see this line two times:
  one time for the splash screen, and one time for `MainActivity`.
- `tag=:LaunchDemo` — the app's own marks from [LaunchTrace](#how-the-report-is-made-launchtrace).

> **Note:** The `Zygote` and `libprocessgroup` parts are not limited to this app. Other apps can show
> lines there too. Use the pid from the `Start proc` line to find the correct ones.

## How the report is made: LaunchTrace

All `LaunchDemo` lines come from [LaunchTrace.kt](app/src/main/java/eu/anifantakis/myapplication/LaunchTrace.kt).

### `mark(step, showPath = false)`

`mark()` writes one line to Logcat, with the tag `LaunchDemo`:

```
▶ <step>   [pid <process id> · <thread name> · +<time>ms]
```

- `+<time>ms` is the time since the process start.
  - On API 33 and higher, the time starts at `Process.getStartRequestedUptimeMillis()`. This is the
    moment the system asked for the process, close to the `START u0` line. So the `LaunchDemo` times
    and the `Displayed +…ms` time are almost on the same clock.
  - On older versions, the time starts at `Process.getStartUptimeMillis()`. This moment is later, so
    the numbers are smaller.
- If `showPath = true`, `mark()` also writes the call path from `callPath()` below the line.

Other helpers:

- `markOnce(step)` — writes a step only the first time it is called.
- `Context.describe()` — returns `ClassName@hash`, for example `ContextImpl@28d3483`.
  Use it to see if two `Context` objects are the same object or not.

### `callPath()` — "who called me?"

`callPath()` fills in the `↳` lines of the report. It shows the chain of method calls that led to the
callback, from the start of the process down to your code.

```kotlin
private fun callPath(): String = Throwable().stackTrace
    .filter { f -> keep.any { f.className.startsWith(it) } && !f.className.startsWith(LaunchTrace::class.java.name) }
    .map { "${it.className.substringAfterLast('.')}.${it.methodName}" }
    .distinct()
    .reversed()
    .joinToString("\n") { "      ↳ $it" }
```

Step by step:

1. **`Throwable().stackTrace`** — makes a `Throwable`, but does not throw it. We only want its stack
   trace. A stack trace is the list of method calls that are running now. The newest call is first.
2. **`filter { … }`** — a full stack trace has many frames (AndroidX, Kotlin and Compose internals).
   This step keeps only the frames whose class name starts with a prefix in the `keep` list.
   It also removes the frames of `LaunchTrace` itself (`mark` and `callPath`).
3. **`map { … }`** — changes each frame to `ShortClassName.methodName`. It removes the package name.
   For example, `android.app.ActivityThread` becomes `ActivityThread`.
4. **`distinct()`** — removes lines that repeat. For example, `Activity.performCreate` has two
   overloads (two versions with different parameters) that call each other.
   Without `distinct()`, you see that line two times.
5. **`reversed()`** — the stack trace starts with the newest call. After this step, the list starts
   with the oldest call (`ZygoteInit.main`) and ends with your callback. You read it from top to
   bottom, in the order that the calls happened.
6. **`joinToString(…)`** — puts each frame on its own line, with `↳` in front.

The `keep` list:

| Prefix | What it shows |
|---|---|
| `com.android.internal.os.` | `ZygoteInit` and `RuntimeInit`. Every app process starts here, after the Zygote fork. |
| `java.lang.reflect.Method` | `Method.invoke`. Zygote calls `ActivityThread.main()` through reflection. |
| `android.os.Looper`, `android.os.Handler` | The main thread's message loop. |
| `android.app.` | `ActivityThread`, `LoadedApk`, `Instrumentation`, `Application`, `Activity`, and `android.app.servertransaction` (`TransactionExecutor`, `LaunchActivityItem`). |
| `android.view.Choreographer`, `android.view.ViewRootImpl` | Frame and drawing callbacks. |
| `androidx.compose.ui.platform.AbstractComposeView` | The View that hosts Compose. |
| `eu.anifantakis.` | The app's own code. |

To see more frames, add a prefix to `keep`.

#### Example: `Application.attachBaseContext`

```
▶ Application.attachBaseContext(base = ContextImpl@28d3483)   [pid 6022 · main · +234ms]
      ↳ ZygoteInit.main
      ↳ RuntimeInit$MethodAndArgsCaller.run
      ↳ Method.invoke
      ↳ ActivityThread.main
      ↳ Looper.loop
      ↳ Looper.loopOnce
      ↳ Handler.dispatchMessage
      ↳ Handler.dispatchMessageImpl
      ↳ ActivityThread$H.handleMessage
      ↳ ActivityThread.-$$Nest$mhandleBindApplication
      ↳ ActivityThread.handleBindApplication
      ↳ LoadedApk.makeApplicationInner
      ↳ Instrumentation.newApplication
      ↳ Application.attach
      ↳ MyApplication.attachBaseContext
```

How to read it: Zygote starts the process. `ActivityThread.main` starts the main loop. A "bind
application" message arrives. `handleBindApplication` runs. `LoadedApk` creates the `Application`.
`Application.attach` gives it its `Context`, through `attachBaseContext`.

`ActivityThread.-$$Nest$mhandleBindApplication` is a helper method that the compiler adds.
It lets the inner class `H` call a private method of `ActivityThread`. You can ignore it.

#### Example: `Activity.onCreate`

```
▶ Activity.onCreate   [pid 6022 · main · +283ms]
      ↳ ZygoteInit.main
      ↳ RuntimeInit$MethodAndArgsCaller.run
      ↳ Method.invoke
      ↳ ActivityThread.main
      ↳ Looper.loop
      ↳ Looper.loopOnce
      ↳ Handler.dispatchMessage
      ↳ Handler.dispatchMessageImpl
      ↳ ActivityThread$H.handleMessage
      ↳ TransactionExecutor.execute
      ↳ TransactionExecutor.executeTransactionItems
      ↳ TransactionExecutor.executeNonLifecycleItem
      ↳ LaunchActivityItem.execute
      ↳ ActivityThread.handleLaunchActivity
      ↳ ActivityThread.performLaunchActivity
      ↳ Instrumentation.callActivityOnCreate
      ↳ Activity.performCreate
      ↳ MainActivity.onCreate
```

How to read it: the same main loop gets a transaction from the system. `TransactionExecutor` runs a
`LaunchActivityItem`. `ActivityThread` creates the Activity (`performLaunchActivity`).
`Instrumentation` calls `Activity.performCreate`, which calls `onCreate`.

> **Note:** The exact frames change between Android versions. These examples come from API 37.

> **Warning:** `Throwable().stackTrace` is slow. It runs only when `showPath = true`.
> This is demo code. Do not ship it in a production app.
