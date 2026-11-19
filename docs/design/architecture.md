# 全体の構成

Mod Studio は「1 つのプロジェクトを、たくさんの出力先とバージョンに変換するコンパイラ」と、その入力を作るための JavaFX のエディタでできています。

## データの流れ

```
エディタ (modstudio.ui)
   |  編集するたびに Workspace.changed()
   v
Project (modstudio.core)  ── Validator: 入力中に検証
   |                     └─ Compat: 出力先ごとの対応状況
   v
Generators.generate(project, target, version)  (modstudio.gen)
   |  GenContext に FileMap (パス -> バイト列) を作る
   v
Exporter: フォルダ / zip / jar に書き出す
   |
   v
BuildService (Gradle Tooling API): ビルド、runClient、runServer
```

## プロジェクトの形式

- `<modid>.msproj` に Gson で JSON として保存します。
- 要素はすべて `Element` を継承します。種類は `ElementType` で表し、要素ごとに `type` を記録します。
  - 例: Item、Block、Model、Shader、Procedure、Mob、Machine
- テクスチャとサウンドは base64 でプロジェクトに埋め込みます。1 ファイルで持ち運べるようにするためです。
- 翻訳は要素ごとに `loc[フィールド][言語コード]` で持ちます。その他のキーは `Project.translations` に入れます。
- API キーなどのシークレットはプロジェクトファイルに入れません。`secrets.local.properties` に保存し、出力にも埋め込みません (外部 API の設計を参照)。

## 生成

`Generators` が出力先ごとに次のクラスを呼びます。

| クラス | 担当 |
| --- | --- |
| `FabricGen` / `ForgeLikeGen` | Mod の Gradle プロジェクトと Java ソース (Forge/NeoForge と Fabric/Quilt は共通部分を共有し、`26.1` は非難読化 Loom に対応) |
| `AdvancedModGen` | 高度な Mod 要素 (`Container`, `CustomEntity`, `ModConfig`, `MixinSpec`, `Portal`, `Profession`, `CustomRecipeType`, ネットワークペイロード) |
| `PaperGen` | Paper / Folia (`folia-supported: true` とリージョンスケジューラ) 対応プラグイン Gradle プロジェクトとデータパック同梱 |
| `PackGen` | データパックとリソースパック |
| `BedrockGen` | ビヘイビアパックとリソースパック、Script API |
| `DataGen` | レシピ、ルートテーブル、進捗、タグ、関数、プロシージャ (全 Java 版で共通) |
| `ProcCompiler` | プロシージャの手順と条件を mcfunction にコンパイル |
| `SystemsGen` / `InterfaceGen` / `MachineGen` | Mob、構造物、GUI、HUD、外部 API、機械、マルチブロック |
| `AssetGen` | モデル、テクスチャ、アニメーション、サウンド、言語ファイル、シェーダー |
| `BridgeGen` | Mod とプラグインにだけある機能 (HTTP など) をデータパック側から呼ぶための橋渡し |

ロジックはできるだけ mcfunction に統一しています。そうすると、Mod・プラグイン・データパックで同じ動作になります。Java でしかできないこと (HTTP、インベントリ GUI、イベントの一部) だけを、各ローダーの Java コードで実装し、データパック側から呼び出します。

## バージョンの違いの吸収

`McVersion` が機能ごとのフラグを持ちます。

| フラグ | 対象 |
| --- | --- |
| `components()` | 1.20.6 からのデータコンポーネント |
| `singular()` | 1.21 からの単数形のフォルダ名 |
| `registryKeys()` | 1.21.4 からのレジストリキー |
| `snbtText()` | 1.21.5 からの SNBT テキスト |
| `minorPackFormat()` | 1.21.9 からの `[major, minor]` 形式 `pack.mcmeta` (`min_format` / `max_format`) |
| `unobfuscated()` | 26.1 からの Mojang 非難読化構成・Java 25 |
| `macros()` | 1.20.2 からの関数マクロ |

生成コードは必ずこれらのフラグで分岐します。バージョン番号を直接比較することは避けています。

## 画面

- **`WorkspaceView`**
  - 左のサイドバー、要素の一覧、エディタの 3 列でできています
- **ページ**: `ui.pages`
  - 概要、テクスチャ、翻訳、シークレット、互換性、ビルド、テストプレイ、問題
- **エディタ**: `ui.editors`
  - `Form` で行を並べる共通の作りです
  - 3D ビュー、ボクセル、ノードグラフ、コードエディタなどの部品は `ui.controls` にあります
- **翻訳**
  - `src/main/resources/modstudio/i18n/messages_{en,ja,zh,ko}.properties` にあります
  - 引数のある文字列だけを `MessageFormat` で整形します
- **確認用のスクリーンショット**
  - `--snapshot=<dir>` で主な画面を自動で開いて撮影し、終了します
