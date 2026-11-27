# BINDERS: ختم الأرواح

لعبة كروت بالواقع المعزز (AR) للصحاب في نفس المكان: تحددوا ترابيزة، والوحوش تطلع 3D فوق الكروت، وتتحكموا فيها بالصوت.

- القواعد وكل الكروت: [`docs/RULES.md`](docs/RULES.md)

## حالة المشروع

| المرحلة | الحالة |
|---|---|
| **1. محرك القواعد** (كروت، أدوار، معارك، فخاخ، أوامر صوتية، بوت) | ✅ جاهز ومتختبر |
| 2. مشروع Unity + AR (تحديد الساحة، ظهور الوحوش) | ⏳ الجاية |
| 3. اللعب الجماعي على الواي فاي المحلي | ⏳ |
| 4. الأوامر الصوتية على الموبايل (Vosk) | ⏳ |
| 5. بناء APK أوتوماتيك (GameCI) | ⏳ |

## تنظيم الملفات

```
binders/
├── core/
│   ├── Binders.Core/          محرك القواعد (C#، مفيهوش أي حاجة من Unity)
│   │   ├── Data/cards.json    كل الكروت ← عدّل هنا
│   │   ├── Data/decks.json    الديكات المبدئية
│   │   ├── Game.cs            القواعد: الأدوار والطاقة والمعارك والفخاخ والسحر
│   │   ├── Voice.cs           فهم الأوامر الصوتية بالعربي والإنجليزي
│   │   ├── SimpleBot.cs       خصم كمبيوتر (للتدريب واختبار التوازن)
│   │   ├── package.json       ده بيخلّي الفولدر Unity package جاهز
│   │   └── Binders.Core.asmdef
│   └── Binders.Core.Tests/    55 اختبار، منهم 400 ماتش بين بوتين
├── docs/RULES.md              القواعد والكروت (بتتولّد من الـ JSON)
└── tools/gen_rules_doc.py     يحدّث RULES.md بعد تعديل الكروت
```

## إزاي المحرك شغال

الواجهة (اللمس، الصوت، أو الموبايل التاني على الشبكة) بتبعت **Action**، والمحرك بيرجّع **Events** بالترتيب. طبقة الـ AR بتعرض الـ Events دي كأنيميشن وأصوات.

```csharp
var db   = CardDatabase.FromJson(cardsJson, decksJson);
var game = Game.Create(db.Expand(db.GetDeck("flame_storm")), db.Expand(db.GetDeck("river_guard")), seed: 1234);

var cmd    = VoiceCommandParser.Parse(game, player: 0, heard: "يا سعير اهجم على أبو الهول");
var result = game.Execute(cmd.ToAction(0));
foreach (var e in result.Events) { /* AttackDeclared → TrapTriggered → CreatureDestroyed → BattleDamage ... */ }
```

- موبايل اللي عمل الساحة هو صاحب الـ `Game` الحقيقي، والموبايل التاني بيبعتله Actions.
- نفس الـ seed بيدّي نفس ترتيب الكروت على أي جهاز.

## تشغيل الاختبارات

محتاج [.NET 8 SDK](https://dotnet.microsoft.com/download) (مجاني):

```bash
cd binders/core
dotnet test
```

بيشتغلوا أوتوماتيك على GitHub Actions مع كل push يعدّل `binders/core`.

## تعديل الكروت
1. عدّل `core/Binders.Core/Data/cards.json` أو `decks.json`.
2. شغّل `dotnet test`. اختبار المحاكاة بيتأكد إن الديكين متوازنين (كل ديك يكسب بين 35% و 65%).
3. شغّل `python3 tools/gen_rules_doc.py` علشان تحدّث `docs/RULES.md`.
