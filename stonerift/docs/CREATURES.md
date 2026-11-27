# تصميم كائنات ستون ريفت (StoneRift)

الملف ده مرجع الشكل لكل كائن. استخدمه علشان تعمل صور تصميم (Concept Art) بأي أداة صور، وبعدين تحوّلها لموديلات 3D.
كل وصف معاه **Prompt بالإنجليزي** جاهز تنسخه.

## قواعد الستايل (لكل الكائنات)

- **أنمي ملحمي**: خطوط حادة، ووقفات قوية، وإحساس بالحجم والقوة. شبه وحوش يوجيو في الهيبة، بس **بتصميم جديد تماماً**.
- **كائنات مصنوعة من العناصر**، مش حيوانات ولا بشر: صخر، أو مية، أو معدن، أو بلور، أو طاقة. ممكن يبقى ليها «عيون» منوّرة كحسّاسات، بس **من غير وش بشري**.
- **قلب منوّر** في كل كائن: نواة طاقة باينة من جوه الجسم (من شقوق، أو من جسم شفاف، أو بين الدروع).
- **بيطيروا فوق الكارت**: الكائنات دي طاقة، فمش لازم تقف على رجلين. ده كمان بيخلّي تحريكها في اللعبة أسهل كتير.
- **ممنوع**: أي رموز دينية، أو أصنام، أو أشكال آلهة، أو طابع فرعوني أو عربي قديم، أو أشباح وأرواح، أو جماجم وشياطين.
- **صورة واضحة من بعيد**: الموبايل بيعرض الكائن صغير، فالشكل العام لازم يتعرف حتى لو الكائن صغير.

### ألوان الفصائل
| الفصيل | اللون الأساسي | لون الجسم | الإحساس |
|---|---|---|---|
| 🔥 الماجما | برتقالي ناري `#FF6B14` | صخر أسود مشقق | غضب وقوة خام |
| 🌊 التيار | أزرق سماوي `#33BFFF` | مية شفافة وجليد | هدوء خطير |
| ⚡ البرق | أصفر كهربائي `#FFE640` | معدن فضي | سرعة وحِدّة |
| 💎 البلور | بنفسجي فاتح `#D9B3FF` | كريستال شفاف | صلابة ونقاء |
| 🌀 الجاذبية | بنفسجي غامق `#994DFF` | معدن داكن جداً | تُقل وغموض |

### الـ Prompt الأساسي
ضيفه في **أول** كل prompt:
```
epic anime trading card game creature concept art, elemental construct made of [ELEMENT], glowing energy core visible inside the body, hovering above the ground, dramatic pose, sharp angular design, full body, centered, plain dark background, highly detailed, game asset reference sheet
```
وضيف ده كـ **Negative prompt** لو الأداة بتدعمه:
```
human, human face, realistic animal, religious symbol, idol, deity, skull, demon, ghost, egyptian, pharaoh, hieroglyphs, arabic calligraphy, text, watermark, cute chibi
```

---

## 🔥 فصيل الماجما

### ماجرون، تنين الحمم: Magron, Lava Wyrm
**الدور:** المهاجم الأقوى في الفصيل. لما يدمّر كائن، الخصم يخسر نقاط زيادة.
**الشكل:** ثعبان-تنين ضخم، جسمه **ألواح صخر بركاني متداخلة** زي الدروع، وبين الألواح **حمم منوّرة** بتنبض. ملوش أجنحة ريش، لكن **زعانف صخرية حادة** على ضهره. راسه مثلث حاد، وعينيه شقين برتقالي منوّر. ديله طويل وبيلف حوالين نفسه.
**التأثيرات:** جمر طالع من الشقوق على طول. وقت الهجوم الألواح بتتفتح شوية والنور بيزيد، وبعدين **نفَس حمم** متجه للهدف. ولما يتدمّر الألواح بتتفكك وتقع وهي منوّرة.
```
... made of interlocking volcanic rock plates with molten lava glowing between the cracks, serpentine dragon-like construct without feathers, jagged rock fins along the spine, triangular head with two glowing orange slit eyes, long coiled tail, embers rising from cracks
```

### شفرة الجمر: Ember Blade
**الدور:** سريع، يهاجم أول ما ينزل.
**الشكل:** **تلات شفرات** صخرية منصهرة بتلف بسرعة حوالين نواة نار صغيرة، زي مروحة سلاح. الشفرات حافتها برتقالي منوّر.
**التأثيرات:** دوامة شرر وهو بيلف. وقت الهجوم بيتحول لقرص نار طاير بيعدّي على الهدف.
```
... three molten-edged obsidian blades spinning around a small fire core like a weapon fan, motion blur sparks, aggressive and fast
```

### وَهَج: Wahaj
**الدور:** أرخص كائن في الماجما، بينزل بدري.
**الشكل:** كورة لهب صغيرة جوه **قشرة صخر مكسورة** نصّين، والنار باينة من بين النصّين.
**التأثيرات:** النار بتتنفس، والقشرة بتتفتح وتتقفل.
```
... small sphere of fire inside a cracked rock shell split in two halves, light bursting from the gap, compact and lively
```

---

## 🌊 فصيل التيار

### عُباب، عملاق المحيط: Ubab, Ocean Colossus
**الدور:** كائن ضخم بهجوم ودفاع عاليين.
**الشكل:** كائن شبه **المانتا العملاقة**، بس جسمه **مية شفافة متجسّدة** شايلة جواها تيارات نور أزرق بتجري. أطراف زعانفه **دروع جليد** حادة. ملوش ملامح حيوان حقيقي، والعينين نقطتين نور أبيض.
**التأثيرات:** نقط مية طايرة حواليه. وقت الهجوم **موجة** بتطلع منه وبتتجمد لحظة ما تخبط الهدف.
```
... colossal manta-shaped construct made of translucent living water, glowing blue currents flowing inside its body, wing edges armored with sharp ice plates, two white light points as eyes, floating droplets around it
```

### تيّار: Tayyar
**الدور:** لما ينزل بيجمّد أقوى كائن عند الخصم.
**الشكل:** **لفّة لولبية** من نور متجمد، بتتحرك زي ثعبان مية في الهوا، وعليها حلقات تلج.
**التأثيرات:** شَبورة باردة. وقت النزول بيطلع منه **شعاع تجميد** على الهدف.
```
... spiraling coil of frozen light moving like a water current in the air, ice rings along its body, cold mist trail
```

---

## ⚡ فصيل البرق

### فولتيكس: Voltix
**الدور:** سريع وقوي.
**الشكل:** **طائرة هجومية** معدنية فضي بشكل صقر مجرد. **أجنحتها برق** بدل الريش، وفي صدرها نواة صفرا بتنبض.
**التأثيرات:** شرارات بتجري على جسمه على طول. وقت الهجوم **صاعقة متسلسلة** بتطلع من الأجنحة.
```
... sleek silver metal aerial construct with an abstract falcon silhouette, wings made of crackling lightning instead of feathers, pulsing yellow core in the chest, electric arcs running along the body
```

### وميض: Wameed
**الدور:** سريع ورخيص.
**الشكل:** **درون** صغير على شكل مثلث، أطرافه شرارات، وبيتحرك حركات مفاجئة.
**التأثيرات:** بيختفي ويظهر في مكان قريب (Blink)، وبيسيب ورا خط نور.
```
... tiny triangular drone construct made of sparks and thin metal, flickering, leaves a light trail, agile
```

---

## 💎 فصيل البلور

### تيتانوكس: Titanox
**الدور:** أتقل وأقوى كائن في الدفاع.
**الشكل:** **عملاق كريستال** ضخم، جسمه أعمدة بلور سداسية طالعة من بعض، ونور بنفسجي-وردي جوه. كتافه عريضة جداً، وإيديه كتل بلور.
**التأثيرات:** لمعات بتظهر وتختفي على السطح. وقت الهجوم **وابل شظايا** بلور. ولما يتدمّر بيتكسر بشعاع نور.
```
... massive crystal titan made of hexagonal crystal pillars growing out of each other, violet-pink light inside, very broad shoulders, crystal block fists, sparkling surface
```

### حارس المرايا: Mirror Warden
**الدور:** محمي في أول دور بعد ما ينزل.
**الشكل:** حارس طويل ورفيع من بلور شفاف، ماسك **درع على شكل منشور** بيكسّر النور لألوان.
**التأثيرات:** وقت الحماية **قبة سداسية** بتلمع حواليه.
```
... tall slender crystal guardian holding a large prism shield that splits light into a spectrum, translucent body, calm and noble stance, hexagonal light barrier
```

### كوارتون: Quarton
**الدور:** رخيص ودفاعه عالي.
**الشكل:** **مكعب كريستال** صغير بيتفتح زي الوردة ويطلع منه درع صغير.
**التأثيرات:** بيتقفل على نفسه في وضع الدفاع.
```
... small crystal cube construct that unfolds like a flower revealing a tiny shield, cute but sturdy, faceted surfaces
```

---

## 🌀 فصيل الجاذبية

### ثِقَل، سيد الجاذبية: Thiqal, Gravity Sovereign
**الدور:** قوي، ولما يدمّر كائن الخصم بيخسر نقاط زيادة.
**الشكل:** **نواة سودا** ضخمة جواها نور بنفسجي، وحواليها **3 حلقات معدنية** بتلف في اتجاهات مختلفة. حواليه صخور صغيرة طايرة في مدار. ملوش جسم تقليدي.
**التأثيرات:** الصخور بتلف حواليه على طول. وقت الهجوم **نبضة جاذبية** بتشد الهدف وبتضغطه.
```
... huge dark sphere core with violet light inside, three metallic rings orbiting in different axes, small rocks orbiting around, no traditional body, imposing and heavy, space distortion around it
```

### مدار: Madar
**الدور:** محمي في أول دور بعد ما ينزل.
**الشكل:** نواة صغيرة حواليها **حلقة واحدة** بتلف، وبيختفي ثانية ويرجع (Phase).
**التأثيرات:** الشكل بيبقى شفاف لحظة وهو بيختفي.
```
... small glowing core with a single orbiting ring, semi-transparent, phasing in and out of visibility
```

---

## الساحات (Field)
- **قلب البركان:** الحلبة بتتحول لأرض بركانية فيها شقوق حمم وأعمدة دخان صغيرة.
- **المحيط المتجمد:** سطح جليد شفاف وتحته مية بتتحرك ونور أزرق.

---

## من الصورة للموديل 3D

1. **صورة التصميم:** اعمل صورة لكل كائن بالـ prompt بتاعه، بأي أداة صور مجانية. اختار أوضح صورة من قدام، أو جنب، أو 3/4.
2. **تحويلها لموديل 3D:** استخدم أداة تحويل صورة لـ 3D:
   - **Hunyuan3D-2** (مفتوحة المصدر، وفيه نسخة تجربة مجانية أونلاين). ⚠️ لازم نراجع شروط رخصتها قبل النشر التجاري.
   - أو الخطط المجانية لأدوات زي Meshy وTripo. ⚠️ برضه لازم نراجع شروط الاستخدام التجاري لكل أداة.
3. **التنظيف:** افتح الموديل في **Blender** (مجاني). صغّر عدد المضلعات لحوالي 10 لـ 20 ألف، واعمله Export بصيغة `.fbx` أو `.glb`.
4. **إدخاله في اللعبة:** حط الموديل في `unity/Assets/Resources/Models/` بنفس اسم الكائن، مثلاً `magron.fbx`. اللعبة هتستخدمه أوتوماتيك بدل الشكل المؤقت، والتأثيرات والهالة هتفضل شغالة عليه.
5. **الحركة:** مش محتاجين هيكل حركة (Rig) دلوقتي. الطيران والهجوم والاهتزاز كلهم معمولين بالكود.
