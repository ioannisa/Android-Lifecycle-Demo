# Android Launch Lifecycle Demo

A small Android app that shows **what happens to an app process during its whole life**: you open the app
(cold start), send it to the background, open it again (hot start), and finally kill it. It covers every
step, from the launcher tap, through the system and Zygote, to the first frame, and to the moment the
process dies.

The app writes its own log lines (tag `LaunchDemo`) at each step. One Logcat filter mixes these lines with
the system's own lines. You see the whole story in one place, in order.

## How to run it

1. Open the project in Android Studio and run the app once. This installs it.
2. Paste the [Logcat filter](#the-logcat-filter) into the Logcat filter field.
3. Stop the app, so the next start is a cold start:

   ```
   adb shell am force-stop eu.anifantakis.myapplication
   ```

4. Clear Logcat (the trash-can icon in the Logcat toolbar).
5. **Tap the app icon** on the home screen. This is a **cold start**.
6. Press the **Home** button. The app goes to the background.
7. **Tap the app icon** again. This is a **hot start**.
8. Press the **Recents** button and **swipe the app away**. The process is killed.

> **Tip:** Tap the icon. Do not use the Run button. The Run button reinstalls the app, force-stops it,
> and starts it from the shell (`adb shell am start`), not from the launcher.

## The Logcat filter

```
(tag=:StatsLog & message~:'LAUNCHER_APP_LAUNCH_TAP|LAUNCHER_TASK_DISMISS') | (tag=:ActivityTaskManager & message~:'START u\d+|Displayed|Fully drawn|Force removing') | (tag=:ActivityManager & message~:'Start proc|Force stopping|Killing|Got obituary') | tag=:Zygote | (tag=:libprocessgroup & message:'/apps/') | (tag=:WindowManager & message~:'setClientSurface|WIN DEATH') | message:CollectorType | (tag=:nativeloader & message:'Configuring clns')
```

This filter uses only lines that Android itself writes, so it works for **any app**.

To also see this app's own marks (every lifecycle callback, with its call path), add this to the end:

```
 | tag=:LaunchDemo
```

> Keep the Logcat level at Verbose (the default). The `StatsLog` lines come from Pixel Launcher
> (Google emulators and Pixel phones). Other launchers may not write them.

## What you will see

Real output from one session on an Android 17 (API 37) emulator, with `| tag=:LaunchDemo` added.
All four screenshots come from the same boot and the same app process, so the PIDs are the same:

| PID | Process |
|---|---|
| `653` | `system_server`. It holds ActivityTaskManager (ATMS), ActivityManager (AMS) and WindowManager. |
| `433` | `zygote64`. The parent of every app process. |
| `2390` | The launcher (home screen, Recents). |
| `20980` | The app process. It gets a new pid on every cold start. |

### 1. Cold start: tap the icon

The process does not exist. Zygote creates it, then the app goes through the five steps of a cold start.
See [Cold start, step by step](#cold-start-step-by-step).

<details>
<summary><b>Show the Logcat output</b></summary>

![Cold start: from the launcher tap to "Fully drawn", marked step by step](docs/cold-start-steps.png)

| Mark | Meaning |
|---|---|
| **1–5** (red) | The five steps of a cold start. |
| **3** (amber, solid) | Step 3 has no log line of its own. The proof is the first part of the call path under `Application.attachBaseContext`. |
| **3** (amber, dashed) | The same main loop again, under `Activity.onCreate`. It is not a new step. |
| **✓** (green) | The app is on screen: the window gets its surface, then `Displayed` and `Fully drawn`. |

</details>

### 2. Press Home

The app goes to the background. The process stays alive.
See [Pressing Home](#pressing-home).

<details>
<summary><b>Show the Logcat output</b></summary>

![The Home button sends the app to the background: HOME, PAUSE, STOP](docs/home-steps.png)

**HOME**: the system starts the launcher. **PAUSE**: the app loses the foreground. **STOP**: the app is
no longer visible.

</details>

### 3. Open it again: hot start

The same process and the same Activity come back to the front.
See [Opening the app again (hot start)](#opening-the-app-again-hot-start).

<details>
<summary><b>Show the Logcat output</b></summary>

![The app comes back from the background: TAP, SNAP, RESUME](docs/hot-start-steps.png)

**TAP**: the launcher asks ATMS to start the app. **SNAP**: the system shows a snapshot of the app's last
screen. **RESUME**: the same Activity comes back: `onRestart` → `onStart` → `onResume`. **✓**: the window
gets a new surface and the focus.

</details>

### 4. Swipe it away in Recents

The task is removed and the process is killed.
See [Swiping the app away](#swiping-the-app-away).

<details>
<summary><b>Show the Logcat output</b></summary>

![The app is swiped away in Recents and its process is killed: RECENTS, SWIPE, KILL, GONE](docs/kill-steps.png)

**RECENTS**: Recents opens, the app loses focus. **SWIPE**: the app is paused, stopped and its task is
removed. **KILL**: AMS kills the process. **GONE**: the system cleans up and reports the death.

</details>

## Cold start, step by step

### Step 1: Launch request (ATMS)

- **`START u0 {… cmp=eu.anifantakis.myapplication/.MainActivity …}`** — The launcher calls
  `startActivity()`. The call crosses into `system_server` through Binder, and ATMS writes this line.
  - At the end of the line, ATMS names the caller: `from uid 10196 (com.google.android.apps.nexuslauncher)`.
    Binder always tells the system who is calling.
  - `cmp=…/.MainActivity`: ATMS found the Activity because the app's Manifest declares it.
- **`LAUNCHER_APP_LAUNCH_TAP`** — The launcher's own line. It comes right *after* `START` (in the same
  millisecond, or 1 ms later), because `startActivity()` is a synchronous Binder call: the launcher
  waits for ATMS first. Logcat shows when a line was *written*, not when the work *started*.
- *Not in the log:* ATMS sees that the app has no process yet. It asks AMS for one.

### Step 2: Create the process (AMS + Zygote)

- *Not in the log:* AMS prepares the new process (uid, groups, SELinux label, name, entry point
  `android.app.ActivityThread`). It sends this to Zygote through a socket.
- **`Forked child process 20980`** — Zygote (pid `433`) forks itself, 6 ms after `START`. The child shares
  Zygote's memory (Copy-on-Write), so this is fast.
- **`Created cgroup /sys/fs/cgroup/apps/uid_10233/pid_20980`** — Look at the PID column: now it is
  `20980`. The new process writes this line itself. This is *specialization*: the copy of Zygote gets its
  own identity (uid, SELinux label, its own view of storage, its name). The cgroup lets the system track
  the process and limit its CPU and memory. (On the first launch after a reboot, you also see
  `Created cgroup …/uid_10233`: the group for the app's uid is created once.)
- **`Start proc 20980:…/u0a233 for next-top-activity`** — AMS writes this only when Zygote sends back the
  new pid. In this run, it came 33 ms after the fork, even after the child's own first line. Each process
  writes its lines on its own, so their order can change between runs.
  `u0a233` means user 0, app 233, so uid `10233`. `for next-top-activity` is the reason: ATMS asked for it.
- **`Process 20980 created for eu.anifantakis.myapplication`** — Specialization is done. It is the same
  process (same pid), not a second one.
- **`setClientSurface … Splash Screen`** — The system draws the splash screen while the process is
  being created. Its position changes between runs, because the two things happen at the same time.
- **`Using generational CollectorTypeCMC GC.`** — The tag is now the app's own name (`s.myapplication`).
  The Android runtime that came from Zygote is now set up for this app.
- **`PROCESS STARTED (20980)`** — A marker from Android Studio, not from the device.

From `START` to `Process … created for` takes only about 40 ms. Most of a cold start is what comes after.

### Step 3: The heartbeat (the main thread)

No system line shows this step. The call path under `Application.attachBaseContext` proves it:

- **`ZygoteInit.main`** — The first line of every path. The app's main thread was Zygote's main thread.
  After the fork, the process continues inside Zygote's `main()` method. It never loads a new program.
- **`Method.invoke` → `ActivityThread.main`** — Zygote calls the app's entry point through reflection.
- **`Looper.loop` → `Handler.dispatchMessage` → `ActivityThread$H.handleMessage`** — The main thread runs
  a loop and waits for messages. `H` is a `Handler` that decides what each message means.
- *Not in the log:* `ActivityThread` tells AMS, through Binder: "I'm alive, here's how to call me".
  AMS answers with `bindApplication()`.

There are **no Binder frames** in the path. The Binder call arrives on a separate Binder thread. That
thread only puts a message in the main thread's queue. The main thread runs it from its loop.

### Step 4: The DNA (bindApplication)

- **`Configuring clns-9 for other apk …/base.apk`** — The class loader for the app's APK is set up.
  Zygote preloaded the Android framework. Only now is the app's own code loaded.
- **`▶ Application.attachBaseContext(base = ContextImpl@e6aa586)`** — The system creates the `Application`
  and plugs in its `Context` (`ContextImpl`). The path shows the message `H` received:
  `handleBindApplication` → `LoadedApk.makeApplicationInner` → `Instrumentation.newApplication` →
  `Application.attach`. The system creates the `Application`. The app never calls its constructor.
- **`▶ ContentProvider.onCreate — before Application.onCreate!`** — Content providers start *between*
  `attachBaseContext` and `onCreate`. Libraries such as App Startup and Firebase use this to start
  themselves before the app's code runs.
- **`▶ Application.onCreate`** — The app's own initialization runs here, before any Activity exists.
  It runs every time the process starts, also for a background job or a push message.

### Step 5: The face (launch the Activity)

- *Not in the log:* AMS tells ATMS that the process is ready. ATMS sends a `ClientTransaction` to the app
  through Binder (`LaunchActivityItem` + `ResumeActivityItem`).
- **`▶ Activity.attachBaseContext(base = ContextImpl@c6d262f)`** — A **new** `ContextImpl`, with a
  different hash from the Application's (`@e6aa586`). Every Activity gets its own `Context`.
- **`▶ Activity.onCreate`** — The path starts the same way as in Step 4, then it splits at `H.handleMessage`:
  `TransactionExecutor.execute` → `LaunchActivityItem.execute` → `performLaunchActivity` →
  `Instrumentation.callActivityOnCreate` → `MainActivity.onCreate`. Both callbacks are messages on the same
  loop, on the same thread.
- **`▶ Activity.onStart`** and **`▶ Activity.onResume — window attached? false`** — The Activity is
  resumed, but it is not on screen yet.
- **`setClientSurface … MainActivity`** — The Activity's window gets its surface, about 320 ms
  after `onResume()`.
- **`Displayed … +705ms`** — The first frame is on screen. ATMS measures it from `START`
  (TTID, *time to initial display*).
- **`▶ onWindowFocusChanged(true)`** — The window gets input focus. The user can interact now.
- **`Fully drawn … +1s139ms`** — The app pretends to load data for 400 ms, then calls
  `ReportDrawnWhen { loaded }`. This is the time until the app says "my content is ready"
  (TTFD, *time to full display*).

## Pressing Home

- **HOME — `START u0 {… cat=[android.intent.category.HOME] …} with LAUNCH_SINGLE_TASK from uid 0 … result code=2`**
  — Pressing Home is just another Activity start. The system itself (uid `0`) starts the launcher's
  HOME Activity. The launcher process is already running, so there is no `Start proc` and no fork.
  `result code=2` means "an existing task was brought to the front". A new start ends with `result code=0`.
- **PAUSE — `▶ Activity.onPause`** — 3 ms later. The app is no longer in the foreground.
- **PAUSE — `setClientSurface … NexusLauncherActivity`** — The launcher's window gets its surface back.
- **PAUSE — `▶ onWindowFocusChanged(false)`** — The app has no input focus anymore.
- **STOP — `▶ Activity.onStop — surface will be released`** — About **1 second** later.
  `onPause()` means "you lost the foreground". `onStop()` means "you are no longer visible".
  The app's window stays visible while the "go home" animation plays. The full Logcat output shows the
  animation ending (`Finish Transition`) and the window becoming invisible (`visibilityChanged … false`)
  just before `onStop()`.

What is **missing** is the point: no `Killing`, no `Removed cgroup`, no Zygote line. The process stays
alive in the background, with the same pid (`20980`).

The process also becomes less important to the system. You can see its importance score (`adj`)
with this command. Run it once while the app is on screen, and once after Home:

```
adb shell 'cat /proc/$(pidof eu.anifantakis.myapplication)/oom_score_adj'
```

On screen, it prints `0`. After Home, it prints a higher number, often `700` ("the previous app").
The exact value can change. Higher numbers are killed first when memory is low.

## Opening the app again (hot start)

- **TAP — `START u0 {… cat=[android.intent.category.LAUNCHER] …}`** and **`LAUNCHER_APP_LAUNCH_TAP`** —
  The same request as in a cold start (Step 1).
- **SNAP — `setClientSurface Surface(name=SnapshotStartingWindow for taskId=65 - task-snapshot-surface …)`**
  — Instead of a splash screen, the system shows a **snapshot**: a picture of the app's last screen,
  saved when the app went to the background. The user sees the app at once.
- **RESUME — `▶ Activity.onRestart`** — Same pid (`20980`), same Activity. The path starts with the same
  main loop. Then `TransactionExecutor.executeLifecycleItem` → `cycleToPath` → `performLifecycleSequence`:
  ATMS asked for the state *resumed*, and the Activity is *stopped*. `TransactionExecutor` walks it through
  every state in between: `onRestart` → `onStart` → `onResume`.
- **RESUME — `▶ Activity.onStart`** and **`▶ Activity.onResume — window attached? true`** — All three
  callbacks run in the same millisecond. This time the window is **attached**: it still exists from
  before. `onStop()` only released its surface.
- **✓ — `setClientSurface … MainActivity`** — The window gets a new surface, 6 ms after `onResume()`.
- **✓ — `▶ onWindowFocusChanged(true)`** — About 200 ms after the tap, the user can interact again.

**Skipped:** no `Start proc`, no Zygote, no cgroup, no `Application` lines, no `onCreate()`. In this run,
there is also no `Displayed` and no `Fully drawn` line.

### Cold, warm and hot starts

Android uses three names for a start:

| Start | The process | The Activity | What you see in this demo |
|---|---|---|---|
| **Cold** | Does not exist. Zygote creates it. | Created (`onCreate`) | Steps 1–5 above |
| **Warm** | Exists | Created again (`onCreate`, with its saved state) | No `Start proc` and no `Application` lines, but `Activity.onCreate` runs again |
| **Hot** | Exists | Exists. It is only brought to the front. | `onRestart` → `onStart` → `onResume` |

To see a **warm start**, turn on **Developer options › Don't keep activities**. Then the system destroys
the Activity as soon as you leave it, but the process stays alive. Press Home, then tap the icon.

> On Android 12 and higher, the Back button no longer destroys the app's first (root) Activity. It sends the
> app to the background, like Home. So Back followed by a tap is also a hot start.

## Swiping the app away

- **RECENTS — `setClientSurface … NexusLauncherActivity`** — The Recents button was pressed. The launcher,
  which shows Recents, is on top again.
- **RECENTS — `▶ onWindowFocusChanged(false)`** — The app loses focus, but there is **no `onPause()`**.
  In Recents, the app is still resumed, as a live tile.
- **SWIPE — `▶ Activity.onPause`** — The app was swiped away (here, 19 seconds later).
- **SWIPE — `▶ Activity.onStop — surface will be released`** — ATMS stops the Activity.
- **SWIPE — `LAUNCHER_TASK_DISMISS_SWIPE_UP`** — The launcher's own line about the swipe. It asks ATMS,
  through Binder, to remove the app's task. (In other runs this line comes before `onStop()`. Each process
  writes its lines on its own.)
- **KILL — `Killing 20980:…/u0a233 (adj 905): remove task`** — The process has nothing left, so AMS kills
  it, about 0.3 s after `onStop()`. `adj` is the importance score that AMS keeps for every process: `0`
  while the app is on screen, `900` and higher when it is *cached*. Cached processes are killed first.
- **KILL — `PROCESS ENDED (20980)`** — Android Studio's marker.
- **GONE — `Removed cgroup …/pid_20980`** — `system_server` removes the cgroup that the process created
  in Step 2.
- **GONE — `WIN DEATH: Window{… MainActivity …}`** — Binder tells WindowManager that the process that
  owned this window is gone.
- **GONE — `Process 20980 exited due to signal 9 (Killed)`** — Zygote did not kill the app. Zygote is the
  *parent* (pid `433`, the same pid that forked the process), and the kernel tells the parent when a
  child dies.

And notice what is **missing: `onDestroy()`**. The process was killed while the Activity was stopped.
`onStop()` can be the last callback your app ever gets.

### A force stop is different

Try the Stop button in Android Studio, or `adb shell am force-stop eu.anifantakis.myapplication`,
while the app is on screen:

- There are **no `LaunchDemo` lines**: no `onPause()`, no `onStop()`. The process is killed at once
  (`Killing … (adj 0)`).
- The order is the opposite of a swipe. AMS kills first (`Force stopping`, `Killing`). Then ATMS removes
  the Activity (`Force removing ActivityRecord … app died, no saved state`).
- Three separate lines report the same death: `Got obituary` (Binder tells AMS), `WIN DEATH`
  (Binder tells WindowManager), and `exited due to signal 9` (the kernel tells Zygote).
- A force stop also puts the app in the *stopped* state. Alarms, jobs and broadcasts cannot wake the
  app until the user opens it again. A swipe does not do this.
- The Run button force-stops the app before every launch. You see `Force stopping` lines then, even
  when nothing is running.

## Project layout

| File | What it does |
|---|---|
| [LaunchTrace.kt](app/src/main/java/eu/anifantakis/myapplication/LaunchTrace.kt) | The logger: `mark()`, `markOnce()`, `callPath()` and `Context.describe()`. |
| [MyApplication.kt](app/src/main/java/eu/anifantakis/myapplication/MyApplication.kt) | The `Application`. It marks `attachBaseContext` (with call path) and `onCreate`. |
| [DemoInitProvider.kt](app/src/main/java/eu/anifantakis/myapplication/DemoInitProvider.kt) | An empty `ContentProvider`. It shows that providers run before `Application.onCreate()`. |
| [MainActivity.kt](app/src/main/java/eu/anifantakis/myapplication/MainActivity.kt) | The `Activity`. It marks each lifecycle callback. Its Compose UI fakes a 400 ms load, then reports "fully drawn". |
| [AndroidManifest.xml](app/src/main/AndroidManifest.xml) | Registers `MyApplication`, `MainActivity` and `DemoInitProvider`. |

Requirements: minSdk 28, compileSdk and targetSdk 37.

## How the report is made: LaunchTrace

All `LaunchDemo` lines come from [LaunchTrace.kt](app/src/main/java/eu/anifantakis/myapplication/LaunchTrace.kt).

`mark(step, showPath = false)` writes one line to Logcat:

```
▶ <step>   [pid <process id> · <thread name> · +<time>ms]
```

- `+<time>ms` is the time since the system asked for the process (`Process.getStartRequestedUptimeMillis()`,
  API 33+). So it is almost on the same clock as `Displayed +…ms`. On older versions it starts at
  `Process.getStartUptimeMillis()`, which is later, so the numbers are smaller.
- With `showPath = true`, it also writes the call path below the line.
- `markOnce(step)` writes a step only the first time.
- `Context.describe()` returns `ClassName@hash`, for example `ContextImpl@e6aa586`. Use it to see if two
  `Context` objects are the same object.

### `callPath()` — "who called me?"

```kotlin
private fun callPath(): String = Throwable().stackTrace
  .filter { f -> keep.any { f.className.startsWith(it) } && !f.className.startsWith(LaunchTrace::class.java.name) }
  .map { "${it.className.substringAfterLast('.')}.${it.methodName}" }
  .distinct()
  .reversed()
  .joinToString("\n") { "      ↳ $it" }
```

1. **`Throwable().stackTrace`** — makes a `Throwable`, but does not throw it. We only want the list of
   method calls that are running now (newest first).
2. **`filter { … }`** — keeps only the frames whose class starts with a prefix in `keep`, and removes
   `LaunchTrace` itself.
3. **`map { … }`** — turns each frame into `ShortClassName.methodName`.
4. **`distinct()`** — removes repeated lines (for example, two overloads of `Activity.performCreate`).
5. **`reversed()`** — puts the oldest call (`ZygoteInit.main`) first, so you read the path in the order
   the calls happened.
6. **`joinToString(…)`** — one frame per line, with `↳` in front.

| Prefix in `keep` | What it shows |
|---|---|
| `com.android.internal.os.` | `ZygoteInit` and `RuntimeInit`. Every app process starts here, after the Zygote fork. |
| `java.lang.reflect.Method` | `Method.invoke`. Zygote calls `ActivityThread.main()` through reflection. |
| `android.os.Looper`, `android.os.Handler` | The main thread's message loop. |
| `android.app.` | `ActivityThread`, `LoadedApk`, `Instrumentation`, `Application`, `Activity`, `TransactionExecutor`, `LaunchActivityItem`. |
| `android.view.Choreographer`, `android.view.ViewRootImpl` | Frame and drawing callbacks. |
| `androidx.compose.ui.platform.AbstractComposeView` | The View that hosts Compose. |
| `eu.anifantakis.` | The app's own code. |

`ActivityThread.-$$Nest$mhandleBindApplication` in the path is a helper method that the compiler adds.
It lets the inner class `H` call a private method of `ActivityThread`. You can ignore it.

## Notes

- **The timings include debug overhead.** This is a debug build. Android Studio also puts a small agent
  in the app's `code_cache` folder, and the app loads it on every start. To remove it:

  ```
  adb shell run-as eu.anifantakis.myapplication rm -rf code_cache/startup_agents code_cache/.studio
  ```

  Android Studio adds it again the next time you press Run.
- **Timings change between runs**, especially on an emulator. Look at the *order* of the lines, not the
  exact numbers.
- **The `ContextImpl` hashes repeat.** In our runs, every cold start showed the same two hashes
  (`@e6aa586` and `@c6d262f`), even with a new pid. They changed only after the emulator rebooted
  (before that, they were `@67975ee` and `@33db577`). A likely reason: every app process starts as a
  copy of the same Zygote, so the counter that makes these hashes starts from the same state each time.
- **The call-path frames change between Android versions.** These come from API 37.
- **`Throwable().stackTrace` is slow.** It runs only when `showPath = true`. This is demo code.
  Do not ship it in a production app.