# mizunomi

mizunomi は、1日に飲んだ水分の量と種類を記録し、目標量に対する進捗を見える化し、不足しそうなときに通知する Android アプリです。

## アプリの目的

mizunomi の目的は、日々の水分摂取を手軽に記録し、自分の飲水ペースを把握しやすくすることです。

- 飲んだ水分の量と種類をすばやく記録する
- 1日の目標量に対する現在の進捗を確認する
- 水分摂取が不足しそうなときにリマインドする
- 過度に複雑な健康管理ではなく、毎日の習慣化を支援する

## 医療アプリではないこと

mizunomi は医療目的のアプリではありません。

- 診断、治療、予防、医学的判断を目的としません
- 個人の体調、疾患、服薬状況に基づく医学的助言は行いません
- 表示される目標量や通知は、一般的な習慣化支援のための目安です
- 健康上の不安がある場合や水分制限が必要な場合は、医師などの専門家に相談してください

## MVP機能

最初のリリースでは、以下の最小機能を実装対象とします。

- 1日の水分摂取目標量の設定
- 飲んだ水分の記録
  - 量（ml）
  - 種類（水、お茶、コーヒー、スポーツドリンク、その他など）
  - 記録時刻
- 今日の合計摂取量の表示
- 目標量に対する進捗率の表示
- 記録の一覧表示
- 記録の削除
- 水分摂取が不足しそうな場合の通知
- ローカル保存

MVPでは、アカウント登録、クラウド同期、詳細な統計、医療的な推奨機能は対象外とします。

## 画面構成

MVP時点の想定画面は以下です。

### ホーム画面

- 今日の合計摂取量
- 1日の目標量
- 進捗バーまたは円形インジケーター
- 直近の記録
- 記録追加ボタン

### 記録追加画面

- 飲み物の種類選択
- 摂取量の入力またはプリセット選択
- 記録時刻の指定
- 保存ボタン

### 履歴画面

- 今日の記録一覧
- 各記録の量、種類、時刻
- 記録削除

### 設定画面

- 1日の目標量設定
- 通知のオン・オフ
- 通知時間帯
- 通知間隔

## 通知ロジック

通知は「現在の摂取量」と「時間帯に応じた期待進捗」を比較して、不足しそうな場合に送信します。

### 基本方針

- ユーザーが設定した通知時間帯の中だけ通知する
- 通知がオフの場合は送信しない
- 目標量を達成済みの場合は送信しない
- 短時間に連続して通知しない
- 最後の記録から一定時間が経過している場合に通知候補とする

### 期待進捗の例

通知時間帯を `8:00〜22:00`、目標量を `2000ml` とした場合、時間の経過に応じて以下のような期待摂取量を計算します。

```text
期待摂取量 = 目標量 × (通知開始時刻から現在時刻までの経過時間 / 通知対象時間帯の長さ)
```

現在の合計摂取量が期待摂取量を一定以上下回っている場合に、通知を送る候補とします。

### 通知文言の例

- 「水分補給の時間です」
- 「今日の目標まであと少しです」
- 「今のペースだと目標量に届かないかもしれません」

## 推奨技術構成

まだ Android プロジェクトは作成しませんが、実装時は以下の構成を推奨します。

- 言語: Kotlin
- UI: Jetpack Compose
- アーキテクチャ: MVVM
- 非同期処理: Kotlin Coroutines / Flow
- ローカルDB: Room
- 設定保存: DataStore
- DI: Hilt
- 通知: WorkManager + Android Notifications
- ビルド: Gradle Kotlin DSL
- 最小SDK: 実装開始時に検討
- テスト: JUnit、Compose UI Test、必要に応じて Robolectric

## 開発フェーズ

### Phase 0: READMEと方針整理

- README作成
- アプリの目的、MVP、技術方針の整理
- Android プロジェクトはまだ作成しない

### Phase 1: Androidプロジェクト作成

- Kotlin / Jetpack Compose ベースのプロジェクト作成
- 基本的なパッケージ構成の定義
- CIの土台作成

### Phase 2: MVPの基本機能

- 目標量設定
- 水分記録の追加、表示、削除
- 今日の合計と進捗表示
- ローカル保存

### Phase 3: 通知機能

- 通知設定
- 期待進捗に基づく通知判定
- WorkManager による定期チェック

### Phase 4: 品質向上

- テスト追加
- UI改善
- アクセシビリティ確認
- リリース用ビルド設定の検討

## GitHub Actionsでdebug APKを生成する方針

Android プロジェクト作成後、GitHub Actions で debug APK を生成できるようにします。

- `main` ブランチへの push と pull request で CI を実行する
- JDK と Gradle をセットアップする
- `./gradlew assembleDebug` を実行する
- 生成された debug APK を Actions artifact としてアップロードする
- まずは debug APK の生成を優先し、署名付きリリース APK / AAB は後のフェーズで検討する

想定するワークフロー名は `Android Debug APK` とします。

## Codex Development Rules

- Do not create the Android project until explicitly requested.
- For the current phase, only documentation changes are allowed unless the user asks otherwise.
- Keep README content primarily in Japanese, but keep this Codex rules section in English.
- Prefer small, focused changes that match the requested scope.
- Before editing, check repository instructions such as `AGENTS.md` if present.
- Do not introduce medical claims or medical decision-making features.
- Treat mizunomi as a habit-support hydration tracker, not a medical application.
- When implementation begins, use Kotlin and Jetpack Compose unless the project direction changes.
- Keep notification behavior explainable and user-configurable.
- Avoid adding network, account, or cloud-sync features to the MVP unless explicitly requested.
