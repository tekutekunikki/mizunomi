# mizunomi 設計・コードレビュー（2026-07-20）

- 対象: `claude/issue-visibility-dpwnu4` ブランチ（base: main `5abd7d1` + Issue #26 対応コミット `b6ff901`）
- レビュー範囲: 全Kotlinソース、Room データ層、Gradle 設定、GitHub Actions workflow、AndroidManifest
- 目的: Codex が修正PRを作れるように、指摘ごとに修正方針・受け入れ条件・投げる文をまとめる

---

## 総評

データ層（Room / DAO / Repository）は小さく整理されており、`Clock` を注入できる設計・純関数として書かれた集計/パースロジックなど、良い土台がある。一方で、**UI と全ロジックが `MainActivity.kt` 1,558行に集中**し、**テストが1件もなく**、**Compose の状態管理に実害のあるバグが数件**ある。また、**Issue 間の依存関係に矛盾**（通知が未実装なのに「実装済み」前提のIssueがある）が見つかった。

## 指摘サマリー

| ID | 優先度 | 分類 | 内容 |
|----|--------|------|------|
| A-1 | P0 | 設計 | 通知機能（Issue #20）が未実装なのに、Issue #31 が「通知は実装済み」を前提にしている |
| B-1 | P0 | バグ | Flow が毎リコンポジションで再生成され、日付を跨いでも「今日」が更新されない |
| B-2 | P1 | バグ | `selectedTab` が `remember` のため画面回転でホームに戻る（Issue #24 仕様違反） |
| B-3 | P1 | バグ | ペース表示の時刻がリコンポジション依存で、長時間表示しっぱなしだと古くなる |
| B-4 | P1 | バグ | 履歴タブが「今日の直近6件」しか表示せず、7件目以降の編集・削除ができない |
| B-5 | P2 | バグ | 音声入力の量に妥当性チェックがない（「13時に300」→ 13ml 誤認、上限なし） |
| B-6 | P2 | UI | 下タブのアイコンが文字記号（⌂ ≡ ⚙）で端末により豆腐化・表示差が出る |
| A-2 | P1 | 設計 | `MainActivity.kt` 1,558行の一枚岩。ViewModel 不在。回転時に書き込み Coroutine がキャンセルされ記録消失の可能性 |
| A-3 | P1 | 品質 | テストが0件。テスト対象になる純関数がすべて `private` で `MainActivity.kt` 内にある。CI にもテストステップがない |
| C-1 | P2 | 品質 | `\uXXXX` エスケープと生日本語の混在、UI文言の日英混在、strings.xml 未使用 |
| C-2 | P2 | 品質 | ハードコード色が50箇所以上。テーマ未整備・ダークモード未考慮 |
| C-3 | P2 | 品質 | 飲み物種類の文字列が DB 値・UI表示・判定ロジックに直書きで散在 |
| D-1 | P1 | CI | CI が `assembleDebug` のみで `test` を実行していない |
| D-2 | P2 | ドキュメント | 固定署名APKへの初回切替時に一度アンインストールが必要な点が README に未記載 |
| D-3 | P3 | 将来 | release buildType / release 署名は未整備（別Issueで扱う） |

---

## 詳細

### A-1. Issue の依存関係矛盾: 通知は未実装（P0・実装前に整理必須）

**現状**: リポジトリに WorkManager・通知チャンネル・`POST_NOTIFICATIONS` 権限のコードは一切存在しない。Issue #20（水分補給アラート通知）は OPEN のまま。しかし Issue #31（設定タブ Phase 1）は本文で「通知機能はすでに実装済みのため」と書かれており、「OFFの場合、Workerが通知を出さない」という受け入れ条件を持つ。

**問題**: このまま Issue #31 に着手すると、存在しない Worker を制御する設定を作ることになる。

**修正方針**: 実装順を **Issue #20（通知）→ Issue #31（通知ON/OFF設定）** に固定する。Issue #20 実装時には以下が必要になる:

- `AndroidManifest.xml` に `POST_NOTIFICATIONS` 権限追加（現状権限なし: `app/src/main/AndroidManifest.xml`）
- WorkManager 依存追加（`androidx.work:work-runtime-ktx`）
- ペース判定は既存の `buildPaceStatus` / `paceTargets`（`MainActivity.kt:1304-1338, 1454-1462`）を再利用できるが、現在 `private` なので共有可能な場所へ移動が必要（A-2/A-3 のリファクタと連動）

---

### B-1. Flow の再生成と「今日」の固定（P0）

**場所**: `MainActivity.kt:117-122`

```kotlin
val todayTotalMl by repository.observeTotalAmountForDay(LocalDate.now())
    .collectAsState(initial = 0)
val todayRecords by repository.observeTodayRecords()
    .collectAsState(initial = emptyList())
val weeklyRecords by repository.observeRecentRecords(WeeklyTrendDays)
    .collectAsState(initial = emptyList())
```

**問題**:
1. Composable 本体で Flow を生成しているため、リコンポジションのたびに**新しい Flow インスタンスが作られ、Room への購読が張り直される**（`collectAsState` は Flow インスタンスをキーに再収集する）。無駄な再クエリが発生する。
2. `LocalDate.now()` / クエリ範囲が Flow 生成時に固定されるため、**アプリを開いたまま日付を跨ぐと「今日の摂取量」「今日の記録」「7日間推移」が前日のまま**になる。

**修正方針**:
- 表示対象日を `mutableStateOf(LocalDate.now())` として持ち、`remember(date) { repository.observe...(date) }` で Flow を記憶する。
- `LifecycleEventObserver` で `ON_RESUME` 時に `LocalDate.now()` と比較して日付 state を更新する（アプリ復帰時に日替わりを反映）。日付を跨いだ瞬間のリアルタイム更新までは必須にしない。

**受け入れ条件**:
- リコンポジションで Room の購読が張り直されない（Flow が `remember` されている）
- アプリをバックグラウンド→翌日にフォアグラウンド復帰したとき、今日の値が 0 からスタートする
- 既存の記録追加・編集・削除で表示が即時更新される（現状動作を壊さない）

---

### B-2. `selectedTab` が画面回転で失われる（P1）

**場所**: `MainActivity.kt:170`

```kotlin
var selectedTab by remember { mutableStateOf(AppTab.Home) }
```

**問題**: Issue #24 の実装方針は `rememberSaveable` を明示していたが、`remember` で実装されている。画面回転・プロセス再生成で選択タブがホームに戻る。`selectedDrinkType`（`MainActivity.kt:166`）も同様。

**修正方針**: `selectedTab` と `selectedDrinkType` を `rememberSaveable` にする。`AppTab` は enum なので `rememberSaveable { mutableStateOf(AppTab.Home) }` がそのまま動く（enum は autoSaver で保存可能）。

**受け入れ条件**: 記録タブ選択中に画面回転しても記録タブのままである。選択中の飲み物種類も維持される。

---

### B-3. ペース表示の時刻が更新されない（P1）

**場所**: `MainActivity.kt:185`

```kotlin
val paceStatus = buildPaceStatus(todayTotalMl, LocalTime.now())
```

**問題**: `LocalTime.now()` はリコンポジション時にしか評価されない。記録を追加せず画面を放置すると、時間帯が変わっても目安量（12:00→15:00 など）が切り替わらない。

**修正方針**: 現在時刻を state 化し、`LaunchedEffect` で1分ごと（粗くて良い）に更新する。B-1 の `ON_RESUME` 更新と合わせて復帰時にも更新する。

**受け入れ条件**: アプリを開いたまま目安時刻の境界（例 12:00）を跨ぐと、1分以内にペースカードの目安量が切り替わる。

---

### B-4. 履歴タブの表示件数制限（P1)

**場所**: `MainActivity.kt:419` `items(todayRecords.take(6), ...)`

**問題**: 履歴タブ（見出し「最近の記録と7日間の変化」）だが、実際は**今日の記録のうち新しい6件**しか出ない。1日に7件以上記録すると、古い記録の編集・削除がUI上不可能になる（Issue #17 の機能が実質制限される）。

**修正方針**: `take(6)` を外し、今日の記録を全件表示する（もともと今日分のみで件数は有限。`LazyColumn` なのでスクロールは問題ない）。「今日以外の過去日の履歴表示」は対象外（将来Issue）。

**受け入れ条件**: 1日に7件以上記録しても、全件が履歴タブに表示され、各件の編集・削除ができる。

---

### B-5. 音声入力の量の妥当性チェック不足（P2）

**場所**: `MainActivity.kt:1370-1384` `parseVoiceIntake`

**問題**:
- 文字列中の**最初の数字**を無条件に ml として採用する。「13時に300飲んだ」→ 13ml、「水3000000」→ 3,000,000ml がそのまま候補になる。
- 上限・下限チェックがない。

**修正方針**:
- 抽出した数値のうち「妥当な範囲（例: 50〜2,000ml）」に収まる**最初の**数値を採用する。範囲内の数値がなければ候補なし（`null`）として既存の手動入力誘導に落とす。
- 判定範囲は定数化する（例: `VoiceAmountMinMl = 50`, `VoiceAmountMaxMl = 2000`）。

**受け入れ条件**:
- 「水300」→ 水 / 300ml
- 「13時に300飲んだ」→ 300ml（13 を採用しない）
- 「水3000000」→ 候補なし（手動入力誘導）
- 既存の確認ダイアログ・修正フローが壊れない

---

### B-6. 下タブアイコンの文字記号（P2）

**場所**: `MainActivity.kt:1486-1494` `AppTab(symbol = "⌂" / "+" / "≡" / "⚙")`

**問題**: Unicode 記号はフォント依存で、端末によっては豆腐（□）や絵文字スタイルになる。

**修正方針**: material3 が推移的に含む `material-icons-core` の `Icons.Filled.Home` / `Icons.Filled.Add` / `Icons.AutoMirrored.Filled.List`（または `Icons.Filled.DateRange`）/ `Icons.Filled.Settings` に置き換える（新規外部ライブラリ追加は不要。`icons-extended` は追加しない）。`AppTab` の `symbol: String` を `icon: ImageVector` に変更する。

**受け入れ条件**: 4タブすべてが Material アイコンで表示され、選択状態の色が現状と同等に機能する。

---

### A-2. MainActivity.kt の一枚岩構造と書き込みの生存性（P1）

**場所**: `MainActivity.kt` 全体（1,558行）、特に `MainActivity.kt:123, 129-151`

**問題**:
1. 画面4タブ・ダイアログ3種・集計/パース/ペース判定ロジック・データクラス群が1ファイルに同居しており、変更のたびに巨大 diff になる。Codex/人間どちらのレビュー効率も悪い。
2. README は MVVM を推奨しているが ViewModel が存在しない。DB 書き込みが `rememberCoroutineScope().launch { ... }` で行われるため、**タップ直後に画面回転すると Coroutine がキャンセルされ、記録が保存されない可能性**がある。

**修正方針**（振る舞いを変えないリファクタ）:
- `MainViewModel : AndroidViewModel` を追加し、Flow の公開（B-1 の修正込み）と add/update/delete を `viewModelScope` に移す。DI ライブラリ（Hilt）は導入しない。
- ファイル分割（パッケージ案）:
  - `ui/theme/MizunomiColors.kt` … 色定数（C-2 と連動）
  - `ui/home/HomeTab.kt`, `ui/record/RecordTab.kt`, `ui/history/HistoryTab.kt`, `ui/settings/SettingsTab.kt`
  - `ui/dialogs/`（Edit / Delete / VoiceIntake）
  - `domain/PaceCalculator.kt` … `buildPaceStatus`, `paceTargets`, `PaceStatus`, `PaceState`
  - `domain/VoiceIntakeParser.kt` … `parseVoiceIntake`, `classifyVoiceDrinkType`, `normalizeDigits`, `VoiceIntakeCandidate`
  - `domain/DrinkNotices.kt` … `buildDrinkNotices`, カテゴリ集合
  - `domain/WeeklyTrend.kt` … `buildWeeklyTrend`, `DailyIntake`
- 移動する関数・型は `private` → `internal` にしてテスト可能にする（A-3 の前提）。
- `PaceStatus` が Compose の `Color` を持っている点は、`domain` に移す際に `PaceState` だけ返して色対応は UI 側で行う形に変える（domain 層から Compose 依存を外す）。

**受け入れ条件**:
- 全タブ・全ダイアログ・音声入力の見た目と動作が変わらない
- 記録追加中に画面回転しても記録が保存される（ViewModel スコープ）
- `MainActivity.kt` が概ね200行以下になる

---

### A-3. テストゼロ + CI にテストステップなし（P1）

**現状**: `app/src/test/`・`app/src/androidTest/` が存在しない。`android-debug-apk.yml` は `assembleDebug` のみ。テスト用依存（JUnit）も未宣言。

**修正方針**（A-2 のファイル分割後に実施）:
- `testImplementation("junit:junit:4.13.2")` を追加
- 以下の純関数にユニットテストを追加:
  - `VoiceIntakeParser`: 発話例（水300 / ポカリ500 / コーヒー牛乳200 / 13時に300 / 全角数字 / 数字なし / 範囲外）
  - `PaceCalculator`: 各時刻境界（8:00前 / 12:00 / 22:00以降）と3状態の判定、`expectedMl < 300` 時に厳しくしない特例
  - `DrinkNotices`: 3種の注意表示の条件境界（500ml 丁度、1,000ml 丁度など）
  - `WeeklyTrend`: 7日分生成・データなし日の0埋め・isToday
  - `IntakeRecordRepository` の日付範囲計算（`Clock` 固定で検証）
- `android-debug-apk.yml` の `assembleDebug` の前に `./gradlew testDebugUnitTest` ステップを追加

**受け入れ条件**: `./gradlew testDebugUnitTest` がローカル/CI で成功し、CI が test → build の順で走る。

---

### C-1. 文字列の混沌（P2）

**問題**:
- 同一ファイル内で `"水"` エスケープと `"ホーム"` 生日本語が混在（例: `MainActivity.kt:77` vs `MainActivity.kt:349`）。生日本語がビルドできている以上、エスケープは不要で可読性を落とすだけ。
- UI 文言が日英混在: 「Today's intake / Goal / Quick add / Recent records / Edit record / Save / Cancel」と「今日のペース / 保存 / 修正 / 削除」。
- すべてハードコードで `strings.xml` 未使用。

**修正方針**:
1. まず全 `\uXXXX` を生の日本語リテラルに一括変換（機械的・振る舞い不変）。
2. UI 文言を日本語に統一（例: Today's intake → 今日の水分量、Save → 保存、Cancel → キャンセル、Edit/Delete → 編集/削除）。
3. `strings.xml` への移行は任意（やるなら別PR）。DB に保存する drinkType 文字列（水/お茶/…）は**変更しない**こと（既存データとの互換のため）。

**受け入れ条件**: ソース中に `\uXXXX` 形式の日本語エスケープが残っていない。画面上の文言言語が統一されている。既存 DB の記録が正しく表示され続ける。

---

### C-2. 色のハードコード（P2）

**問題**: `Color(0xFF116DAE)` 等が50箇所以上に直書き。同系色の微妙な揺れ（`0xFF0F6FAE` / `0xFF116DAE` / `0xFF1683D8`）があり、統一変更が困難。

**修正方針**: `ui/theme/MizunomiColors.kt` に named 定数（`TextPrimary`, `TextSecondary`, `AccentBlue`, `AchievedGreen`, `WarningAmber`, `DangerRed`, `ScreenBackground`, `CardBackground` など）として集約し、全参照を置換する。ダークモード対応はしない（現状のライト固定のまま。将来Issue）。

**受け入れ条件**: Composable 内に `Color(0x...)` の直書きが残らない。見た目が現状と同一。

---

### C-3. 飲み物種類の文字列散在（P2）

**問題**: `"アルコール"` が `buildDrinkNotices`（`MainActivity.kt:1418`）に直書き、`DrinkTypes` / `SweetDrinkTypes` / `WaterTeaDrinkTypes` / `classifyVoiceDrinkType` の返り値が同じ文字列を別々に持つ。1箇所直し忘れるとサイレントに判定が壊れる。

**修正方針**: `domain/DrinkTypes.kt` に定数（`const val DrinkWater = "水"` など）として一元化し、リスト・集合・判定・パーサすべてが定数を参照する。**文字列の値自体は変えない**（DB 互換）。

**受け入れ条件**: 飲み物種類のリテラルが定義ファイル1箇所にしか現れない。既存の注意表示・音声分類・集計の動作が不変。

---

### D-1. CI にテストがない（P1）

A-3 に含めて対応。`android-debug-apk.yml` に `testDebugUnitTest` ステップ追加。

### D-2. 固定署名APKの初回切替に関する README 追記（P2）

**問題**: 固定署名APK（Issue #26 対応）に切り替える**最初の1回**は、既存インストールが別の debug 署名なので上書きできず、アンインストールが必要。README にこの注意がない。また versionName が常に `1.0` 固定。

**修正方針**: README の「テスト配布用の上書きアップデート対応 APK」節に「初回のみ既存アプリのアンインストールが必要（署名が変わるため）。以後は上書き更新できる」と追記。あわせて `-PVERSION_NAME` でも versionName を上書きできるようにし、workflow から `1.0.<run_number>` を渡す（任意）。

### D-3. release ビルド（P3・将来Issue）

release buildType の minify 設定・release 署名・Play 配布は未整備。今回は対象外とし、必要になったら新規Issueを立てる。

---

## 推奨実施順と作業パッケージ

依存関係を考慮した推奨順:

1. **PR-1: 状態管理バグ修正**（B-1, B-2, B-3, B-4）— 小さく、ユーザー影響が最大
2. **PR-2: リファクタ + テスト + CI**（A-2, A-3, D-1, C-3）— 以後の全Issueの土台
3. **PR-3: UI品質**（B-6, C-1, C-2）— 機械的置換中心
4. **PR-4: 音声入力堅牢化**（B-5）— PR-2 のテスト基盤の上で
5. その後、**Issue #20（通知）→ Issue #31（設定 Phase 1）** の順で機能開発を再開（A-1）

---

## Codexに投げる文

### PR-1: 状態管理バグ修正

```text
Repository: tekutekunikki/mizunomi

docs/code-review-2026-07-20.md の B-1 / B-2 / B-3 / B-4 を修正してください。

やること：
- MainActivity.kt の MizunomiApp で、repository の Flow（observeTotalAmountForDay / observeTodayRecords / observeRecentRecords）を remember で記憶し、リコンポジションごとに Flow が再生成されないようにしてください。
- 表示対象日を state として持ち、LifecycleEventObserver の ON_RESUME で LocalDate.now() と比較して更新し、日付を跨いだ後の復帰時に「今日」の表示が新しい日付に切り替わるようにしてください。
- 現在時刻を state 化し、LaunchedEffect で1分ごとに更新して、ペースカードの目安量が時間経過で切り替わるようにしてください。
- selectedTab と selectedDrinkType を remember から rememberSaveable に変更し、画面回転で失われないようにしてください。
- 履歴タブの todayRecords.take(6) を外し、今日の記録を全件表示・編集・削除できるようにしてください。

やらないこと：
- ファイル分割や ViewModel 導入（別PRで行います）
- 過去日の履歴表示
- 外部ライブラリの追加（androidx.lifecycle の既存推移的依存の範囲で実装してください）

確認：
- ./gradlew assembleDebug が通ること
- 記録追加・編集・削除で表示が即時更新されること（現状動作を壊さない）
- 画面回転後も選択タブ・選択中の飲み物種類が維持されること
- 1日に7件以上記録しても履歴タブに全件表示されること

ブランチ名：
codex/review-pr1-state-management-fixes

PR本文には Summary / Testing / Known limitations / Next steps を書いてください。
```

### PR-2: リファクタ + テスト + CI

```text
Repository: tekutekunikki/mizunomi

docs/code-review-2026-07-20.md の A-2 / A-3 / C-3 / D-1 を実施してください。振る舞いを変えないリファクタです。

やること：
- MainViewModel を追加し、Flow の公開と記録の追加・更新・削除を viewModelScope で行うようにしてください（Hilt などの DI ライブラリは導入しない）。
- MainActivity.kt を分割してください。目安：
  - domain/DrinkTypes.kt（飲み物種類の文字列定数。値は既存 DB と互換のため変更しない）
  - domain/VoiceIntakeParser.kt（parseVoiceIntake / classifyVoiceDrinkType / normalizeDigits / VoiceIntakeCandidate）
  - domain/PaceCalculator.kt（buildPaceStatus / paceTargets。Compose の Color 依存は外し、PaceState を返して色は UI 側で解決する）
  - domain/DrinkNotices.kt（buildDrinkNotices とカテゴリ集合）
  - domain/WeeklyTrend.kt（buildWeeklyTrend / DailyIntake）
  - ui/ 配下にタブ別・ダイアログ別の Composable
- 移動した関数・型は private ではなく internal にしてテスト可能にしてください。
- testImplementation("junit:junit:4.13.2") を追加し、app/src/test/ に以下のユニットテストを追加してください：
  - VoiceIntakeParser（水300 / ポカリ500 / コーヒー牛乳200 / 全角数字 / 数字なし）
  - PaceCalculator（時刻境界と3状態の判定）
  - DrinkNotices（3種の注意表示の閾値境界：500ml丁度・1,000ml丁度）
  - WeeklyTrend（7日分生成・0埋め・isToday）
  - IntakeRecordRepository の日付範囲計算（Clock.fixed を使用）
- .github/workflows/android-debug-apk.yml の assembleDebug の前に ./gradlew testDebugUnitTest ステップを追加してください。

やらないこと：
- UIの見た目・文言・動作の変更
- 新機能追加
- Hilt / Navigation Compose などの導入

確認：
- ./gradlew testDebugUnitTest と ./gradlew assembleDebug が通ること
- GitHub Actions が test → build の順で成功すること
- 全タブ・全ダイアログ・音声入力の動作が変わっていないこと

ブランチ名：
codex/review-pr2-refactor-and-tests

PR本文には Summary / Testing / Known limitations / Next steps を書いてください。
```

### PR-3: UI品質（アイコン・文言・色）

```text
Repository: tekutekunikki/mizunomi

docs/code-review-2026-07-20.md の B-6 / C-1 / C-2 を実施してください。

やること：
- 下タブの文字記号（⌂ + ≡ ⚙）を material-icons-core の Material アイコン（Home / Add / List または DateRange / Settings）に置き換えてください。icons-extended は追加しないでください。
- ソース中の \uXXXX 形式の日本語エスケープをすべて生の日本語リテラルに変換してください。
- UI 文言を日本語に統一してください（例：Today's intake → 今日の水分量、Quick add → クイック追加、Recent records → 最近の記録、Save → 保存、Cancel → キャンセル、Edit/Delete → 編集/削除、7-day trend → 7日間の推移、By drink type → 飲み物別、Goal → 目標）。
- ハードコードされた Color(0x...) を ui/theme/MizunomiColors.kt の named 定数に集約してください。近似色（0xFF0F6FAE / 0xFF116DAE など）は主要な1色に寄せて構いません。
- DB に保存される drinkType の文字列値は変更しないでください。

やらないこと：
- ダークモード対応
- strings.xml への移行（将来検討）
- レイアウト構造の変更

確認：
- ./gradlew assembleDebug が通ること（PR-2 適用後なら testDebugUnitTest も）
- ソースに \uXXXX の日本語エスケープが残っていないこと
- 画面上の文言が日本語で統一されていること
- 既存の記録が正しく表示され続けること

ブランチ名：
codex/review-pr3-ui-polish

PR本文には Summary / Testing / Known limitations / Next steps を書いてください。
```

### PR-4: 音声入力の妥当性チェック

```text
Repository: tekutekunikki/mizunomi

docs/code-review-2026-07-20.md の B-5 を実施してください。

やること：
- parseVoiceIntake で抽出する数値に妥当範囲（50〜2,000ml、定数化）を設け、範囲内に収まる最初の数値を量として採用してください。
- 範囲内の数値が1つもない場合は候補なし（null）とし、既存の「うまく読み取れませんでした」フローに落としてください。
- ユニットテストを追加してください：
  - 「水300」→ 水 / 300ml
  - 「13時に300飲んだ」→ 300ml（13 を採用しない）
  - 「水3000000」→ 候補なし
  - 「水30」→ 候補なし（下限未満）

確認：
- ./gradlew testDebugUnitTest と ./gradlew assembleDebug が通ること
- 既存の確認ダイアログ・修正フローが壊れていないこと

ブランチ名：
codex/review-pr4-voice-input-validation

PR本文には Summary / Testing / Known limitations / Next steps を書いてください。
```

### README 追記（D-2、軽微・PR-1〜4 のどれかに同乗可）

```text
README の「テスト配布用の上書きアップデート対応 APK」節に以下を追記してください：

- 固定署名APKに切り替える最初の1回だけは、既存アプリ（従来の debug 署名）を
  アンインストールしてからインストールする必要がある（署名が変わるため）。
  以後の更新は上書きインストールできる。
```
