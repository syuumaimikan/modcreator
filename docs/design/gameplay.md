# 防具・ステータス効果・エンチャント・作物

ゲームプレイ系の要素 (防具、ステータス効果、エンチャント、ダメージタイプ、絵画、レコードの曲、作物) を、Mod・プラグイン・データパックのそれぞれでどう出力するかをまとめます。実装は主に `gen/JavaCommon.java` (Mod のコード)、`gen/GameplayGen.java` (レジストリデータと再現用の関数)、`gen/ItemSpec.java` (データパックとプラグインのアイテム) です。

## 防具

防具素材 (`ArmorMaterial`) が防御力などの値と着用テクスチャを持ち、種類がヘルメット・チェストプレート・レギンス・ブーツのアイテムがその素材を参照します。

| バージョン | Mod での登録方法 |
| --- | --- |
| 1.20.1, 1.20.4 | `ArmorMaterial` インターフェースを実装した enum。テクスチャは `textures/models/armor/<ns>_<素材>_layer_N.png` |
| 1.20.6, 1.21.1 | `ArmorMaterial` レコードをレジストリに登録し、`Holder` で参照。アイテムは遅延生成 |
| 1.21.4 | `equipment.ArmorMaterial` と `new ArmorItem(素材, ArmorType, props)`。見た目は `equipment/<素材>.json` |
| 1.21.8 | `Item.Properties.humanoidArmor(素材, ArmorType)` |

1.21.4 以降の修理素材は、アイテムタグ `repairs_<素材>` で表します。耐久値は部位ごとの基準値 (11 / 16 / 15 / 13) に素材の倍率を掛けた値です。

データパックとプラグインでは、革の防具をもとにしたアイテムに属性 (防御力、防具強度、ノックバック耐性) を付けて再現します。属性の書き方はバージョンで異なります。

- 1.20.1, 1.20.4: NBT の `AttributeModifiers` と UUID
- 1.20.6: `attribute_modifiers` コンポーネントと UUID・名前
- 1.21.1 以降: `id` で識別。1.21.8 はリスト形式
- 1.21.4 以降は `equippable` コンポーネントの `asset_id` で着用時の見た目も独自にできます

## ステータス効果とポーション

| バージョン | 毎ティックの処理 (`MobEffect`) |
| --- | --- |
| 1.20.1 | `void applyEffectTick(LivingEntity, int)` と `isDurationEffectTick` |
| 1.20.4 | `void applyEffectTick` と `shouldApplyEffectTickThisTick` |
| 1.20.6, 1.21.1 | `boolean applyEffectTick(LivingEntity, int)` |
| 1.21.4 以降 | `boolean applyEffectTick(ServerLevel, LivingEntity, int)` |

属性の変化は `addAttributeModifier` で登録します。1.20.4 までは UUID と `ADDITION` などの演算、1.20.6 は `Holder` と UUID、1.21.1 以降は `ResourceLocation` で識別します。間隔ごとのプロシージャは `ModProcedures.run` で mcfunction を呼びます。

データパックとプラグインには独自の効果を登録できないため、次のように再現します。

- 残り時間をスコア `<ns>.se.<名前>` で数え、対象にタグ `<ns>.se.<名前>` を付けます
- 付与時に `start`、切れたときに `end` の関数で属性を付け外しします
- 毎ティックの関数が残り時間を減らし、間隔ごとにプロシージャを実行します
- プロシージャの「効果」手順でプロジェクトの効果を指定すると、この仕組みに置き換わります

## エンチャント

1.21 以降のデータ駆動形式 (`data/<ns>/enchantment/<名前>.json`) で出力します。1.21 より前を選んだときは、出力しない旨を知らせます。

- 効果: `damage`、`damage_protection`、`attributes`、`post_attack` (相手に対して関数を実行)、`tick`
- タグ: `in_enchanting_table`、`non_treasure` または `treasure`、`curse`、`on_random_loot`、`tradeable`

## ダメージタイプ・絵画・レコードの曲

- **ダメージタイプ**: `damage_type/<名前>.json` と、`bypasses_armor`、`is_fire`、`is_projectile` などの分類タグ。死亡メッセージは `death.attack.<ns>.<名前>` と `.player` に出力します
- **絵画**: 1.21 以降は `painting_variant` (1.21.4 以降はタイトルと作者付き)。1.21 より前の Mod は `PaintingVariant` をコードで登録します。`placeable` タグに入れると、普通の絵画を置いたときに選ばれます
- **レコードの曲**: 1.21 以降の `jukebox_song`。アイテムには `jukeboxPlayable` (Mod) または `jukebox_playable` コンポーネント (データパック) を付けます

Paper のプラグインは、このようなレジストリデータを `world/datapacks/<modid>_plugin` に入れます。反映にはサーバーの再起動が 1 回必要です。

## 作物

作物は形が「作物」のブロックです。ブロックのアイテムがそのまま種になります。

- Mod では `CropBlock` を継承したクラスを生成し、`getBaseSeedId` で自分のアイテムを返します。プロパティはバニラの小麦と同じです (当たり判定なし、ランダムティック、即時破壊、`SoundType.CROP`、ピストンで壊れる)
- 成長度 0 から 7 を、登録した 1 から 8 枚のテクスチャに振り分けます。モデルの親は `minecraft:block/crop` です
- ルートテーブル: 種は常に落とし、成長度 7 のときは収穫物 (最小から最大の個数) と、幸運で増える追加の種を落とします
- タグ: `crops`、`bee_growables`、`maintains_farmland`、アイテムの `villager_plantable_seeds`
- データパック、プラグイン、統合版には出力しません (統合版の独自作物にはスクリプトが必要なため)
