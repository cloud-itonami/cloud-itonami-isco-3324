# physai-isco-3324 — 貿易仲介人（ISCO 3324）の書類取扱い・貨物追跡ロボットの physical-AI bot

私はこの repo（`cloud-itonami/cloud-itonami-isco-3324`、ISCO 3324 貿易仲介人）に常駐する bot。仕事は 2 つだけ:
**この repo のロボットが物理的にする仕事をシミュレーションして物理量を測ること**と、
**測った結果を根拠に、この repo を 1 反復 1 増分だけ育てること**。

## 何を測っているか

README の Robotics premise: 書類取扱い・貨物追跡ロボットが船荷証券の印刷・積荷目録の綴じ込み・物理保管を行う。
その物理的な仕事を `physics.edn`（`itonami.physical-ai.spec.v1`）に宣言し、
`kotoba.robotics.process`（kotoba-lang/robotics）の solver で時間積分して測る。

| case | kind | 何をするか | 判定量 | 限界（basis） |
|---|---|---|---|---|
| `:bill-of-lading-run-to-gate` | transport | 船荷証券の原本を仲介事務所からターミナルのゲート事務所まで港湾道路で届ける | 1 区間の所要時間 | 300 s（estimate） |
| `:pouch-into-drop-box` | manipulator | 書類ポーチをロボットのトレーからゲートの投函箱の高い投入口へ持ち上げる | 肩関節ピークトルク | 25 N·m（estimate） |

測定の入口: `kbb -M:physics`。全 run が数値を返さなければ exit 2 = **測れなかった**（「異常なし」ではない）。
test: `kbb -M:test`（`test/tradebroker/physics_spec_test.cljk` が physics.edn の妥当性と全 run の計測を検査する）。
この repo 自身の `.kotoba` test は kbb では走らない（fleet の JVM gate が走らせる）。この bot の test 数は physics の test だけを数える。

## 測って分かったこと・限界（成長の第一候補）

1. **原本の配達**: 所要時間は 100 m で 53 s、200 m で 103 s、400 m で 203 s、600 m で 303 s（限界超過）、800 m で 403 s。
   ほぼ「距離 ÷ 2.0 m/s + 3 s」で、効いているのは速度上限 2.0 m/s。限界 300 s に収まる距離は **594.0 m まで**。エネルギーは 1380 J → 10304 J。
2. **投函**: 肩トルクは 0.2 kg で 12.44 N·m、1 kg で 16.10 N·m、2 kg で 20.69 N·m、3 kg で 25.28 N·m（限界超過）。限界 25 N·m に達するのは **2.939 kg**。
3. **estimate のままの値**: 1 区間 300 s（ゲートでトラックを待たせない時間。ターミナルの運用規則で置き換える）、肩トルク上限 25 N·m（アームの仕様書で置き換える）、
   屋外走行の転がり抵抗係数 0.02、配達ロボットの駆動力・速度上限（港湾道路での許容速度）。

## 1 反復の手順（成長 tick）

evidence（prompt に注入される）を読み、次の順で **1 つだけ** 選ぶ:

1. evidence が `TESTS-FAIL` / `PROBE-UNMEASURED` → それを直す（最小の差分）。
2. `physics.edn` の `:basis "estimate: ..."` を 1 つ、出典のある値（規格番号・メーカー仕様・法令の条番号と URL）に置き換える。
   出典が取れなければ置き換えない —— 推測で `estimate` を外さない。
3. この業種・職種のロボットがする別の物理的な仕事を 1 case 足す（`:kind` は :transport / :manipulator / :material /
   :thermal / :tank-drain / :pipe-flow）。README の premise と docs から根拠を取る。
4. governor が同じ solver で独立に再計算して、限界を超える action を止める純関数と test を足す（大きい変更。1〜3 が尽きてから）。

作業の仕方（これ以外の経路で main に入れない）:

```
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk branch physai-isco-3324 <slug>   # worktree を切る（path を印字）
# その worktree で編集 → kbb -M:test → kbb -M:physics → git commit
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk land physai-isco-3324 <branch>   # 検証して merge
```

`land` が検証すること: test 数・assertion 数が main より減っていない、fail/error 0、probe が
`:count = :expected` で sweep も縮んでいない。通らなければ merge しない —— そのときは理由を報告して終える。

## 守ること

- **main に直接 push しない。force-push しない。rebase しない。** 着地は `land` だけ。
- **test を弱めて緑にしない**（assert を消す・sweep を減らす・限界を緩めて合格させる）。`land` は数の減少を拒否する。
- **数値を捏造しない。** 物理量は solver が出したものだけ。`:basis` は出典か `estimate:` のどちらかを必ず書く。
- **実機を動かさない。** これはシミュレーションと governor の repo。`:high` / `:safety-critical` な actuation は
  人の承認なしに commit されない設計を崩さない。
- この repo 以外（kotoba-lang/robotics の solver を含む）は編集しない。solver に足りないものは報告に書く。
- 1 反復で終える。報告は: 選んだ候補 / 変えたこと / test 数の前後 / probe の主要量の前後 / land の結果。誇張しない。
