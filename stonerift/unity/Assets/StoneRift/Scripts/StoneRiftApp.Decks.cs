using System.Collections.Generic;
using System.Linq;
using StoneRift.Core;
using UnityEngine;

namespace StoneRift.App
{
    /// <summary>Choosing a deck in the menu, and building your own decks (saved on the phone).</summary>
    public sealed partial class StoneRiftApp
    {
        const string DecksKey = "custom_decks";

        List<DeckList> _customDecks = new List<DeckList>();
        DeckBuilder _builder;
        bool _builderSaved;
        int _filter = -1; // -1 = all factions, otherwise (int)Faction.
        Vector2 _builderScroll, _deckListScroll;
        string _builderMessage = "";

        void LoadCustomDecks()
        {
            _customDecks = DeckBuilder.Deserialize(_db, PlayerPrefs.GetString(DecksKey, ""));
        }

        void SaveCustomDecks()
        {
            PlayerPrefs.SetString(DecksKey, DeckBuilder.Serialize(_customDecks));
            PlayerPrefs.Save();
        }

        IEnumerable<DeckList> AllDecks => _db.Decks.Concat(_customDecks);

        /// <summary>A starter deck or one the player built; falls back to the first starter deck.</summary>
        DeckList ResolveDeck(string id) =>
            _customDecks.FirstOrDefault(d => d.Id == id) ?? _db.Decks.FirstOrDefault(d => d.Id == id) ?? _db.Decks.First();

        /// <summary>The bot picks the starter deck that contrasts with the player's main factions.</summary>
        string BotDeckFor(DeckList player)
        {
            var stats = new DeckBuilder(_db, player).Stats();
            int hot = stats.Factions.Where(kv => kv.Key == Faction.Magma || kv.Key == Faction.Volt).Sum(kv => kv.Value);
            int cold = stats.Factions.Where(kv => kv.Key == Faction.Tide || kv.Key == Faction.Crystal || kv.Key == Faction.Gravity).Sum(kv => kv.Value);
            return hot >= cold ? "crystal_tide" : "magma_volt";
        }

        void OpenBuilder(DeckList existing)
        {
            _builder = existing == null ? new DeckBuilder(_db) : new DeckBuilder(_db, existing);
            _builderSaved = existing != null && _customDecks.Any(d => d.Id == existing.Id);
            _builderMessage = existing == null ? "اختار 30 كارت، وأقصى حاجة 3 نسخ من كل كارت" : "";
            _filter = -1;
            _mode = Mode.Decks;
        }

        // ------------------------------------------------------------------ menu deck list

        /// <summary>Starter and custom decks as a scrolling list; returns the height used.</summary>
        float DrawDeckChooser(float x, float y, float w)
        {
            var decks = AllDecks.ToList();
            const float rowH = 62, listH = 190;
            _deckListScroll = GUI.BeginScrollView(new Rect(x, y, w, listH), _deckListScroll, new Rect(0, 0, w - 20, Mathf.Max(listH, decks.Count * (rowH + 6))));
            for (int i = 0; i < decks.Count; i++)
            {
                var d = decks[i];
                bool custom = _customDecks.Contains(d);
                float bw = custom ? w - 130 : w - 20;
                GUI.backgroundColor = _playerDeck == d.Id ? new Color(1f, 0.8f, 0.3f) : Color.white;
                if (GUI.Button(new Rect(0, i * (rowH + 6), bw, rowH), T(d.NameAr + (custom ? "" : " (جاهز)")), _button)) _playerDeck = d.Id;
                GUI.backgroundColor = Color.white;
                if (custom && GUI.Button(new Rect(bw + 6, i * (rowH + 6), 104, rowH), T("تعديل"), _button)) OpenBuilder(d);
            }
            GUI.EndScrollView();
            GUI.backgroundColor = new Color(0.8f, 0.7f, 1f);
            if (GUI.Button(new Rect(x, y + listH + 8, w, 52), T("ابني ديك جديد"), _button)) OpenBuilder(null);
            GUI.backgroundColor = Color.white;
            return listH + 70;
        }

        // ------------------------------------------------------------------ builder screen

        void DrawDeckBuilder()
        {
            var b = _builder;
            float w = Mathf.Min(VW - 20, 640), x = (VW - w) / 2;
            GUI.Box(new Rect(0, 0, VW, VirtualHeight), GUIContent.none, _box);

            b.Name = GUI.TextField(new Rect(x, 12, w * 0.62f, 52), b.Name ?? "", 24, _button);
            var countStyle = new GUIStyle(_big) { fontSize = 28 };
            countStyle.normal.textColor = b.Count == CardDatabase.DeckSize ? new Color(0.5f, 1f, 0.6f) : Color.white;
            GUI.Label(new Rect(x + w * 0.64f, 12, w * 0.36f, 52), $"{b.Count} / {CardDatabase.DeckSize}", countStyle);

            var s = b.Stats();
            int Of(CardType t) => s.Types.TryGetValue(t, out var n) ? n : 0;
            GUI.Label(new Rect(x, 70, w, 34),
                T($"كائنات {Of(CardType.Creature)}   تقنيات {Of(CardType.Technique)}   فخاخ {Of(CardType.Trap)}   ساحات {Of(CardType.Field)}   متوسط الطاقة {s.AverageCost:0.0}"),
                _label);
            DrawCurve(new Rect(x, 106, w, 46), s.CostCurve);

            string[] tabs = { "الكل", "ماجما", "تيار", "برق", "بلور", "جاذبية" };
            float tw = w / tabs.Length;
            for (int i = 0; i < tabs.Length; i++)
            {
                int f = i == 0 ? -1 : i; // Faction enum: None=0, Magma=1, Tide=2, Volt=3, Crystal=4, Gravity=5.
                GUI.backgroundColor = _filter == f ? new Color(1f, 0.8f, 0.3f) : (f > 0 ? Color.Lerp(FactionVisuals.Main((Faction)f), Color.white, 0.4f) : Color.white);
                if (GUI.Button(new Rect(x + i * tw, 158, tw - 4, 44), T(tabs[i]), _button)) _filter = f;
            }
            GUI.backgroundColor = Color.white;

            var cards = _db.Cards
                .Where(c => _filter < 0 || (int)c.Faction == _filter)
                .OrderBy(c => c.Faction).ThenBy(c => c.Type).ThenBy(c => c.Cost).ToList();
            const float rowH = 96;
            float listTop = 210, listH = VirtualHeight - listTop - 175;
            _builderScroll = GUI.BeginScrollView(new Rect(x, listTop, w, listH), _builderScroll, new Rect(0, 0, w - 20, cards.Count * (rowH + 6)));
            for (int i = 0; i < cards.Count; i++) DrawBuilderRow(cards[i], new Rect(0, i * (rowH + 6), w - 20, rowH));
            GUI.EndScrollView();

            float by = VirtualHeight - 160, bw = w / 3f;
            if (GUI.Button(new Rect(x, by, bw - 6, 66), T("كمّل تلقائي"), _button))
            {
                b.AutoFill(System.Environment.TickCount);
                _builderMessage = "كمّلت الديك. تقدر تغيّر فيه براحتك";
            }
            if (GUI.Button(new Rect(x + bw, by, bw - 6, 66), T("امسح الكل"), _button)) { b.Clear(); _builderMessage = ""; }
            GUI.backgroundColor = b.IsComplete ? new Color(0.4f, 1f, 0.5f) : new Color(0.7f, 0.7f, 0.7f);
            if (GUI.Button(new Rect(x + bw * 2, by, bw, 66), T("حفظ"), _button)) SaveBuilderDeck();
            GUI.backgroundColor = Color.white;

            by += 74;
            if (_builderSaved)
            {
                GUI.backgroundColor = new Color(1f, 0.55f, 0.55f);
                if (GUI.Button(new Rect(x, by, bw - 6, 60), T("احذف الديك"), _button))
                {
                    _customDecks.RemoveAll(d => d.Id == b.Id);
                    SaveCustomDecks();
                    if (_playerDeck == b.Id) _playerDeck = _db.Decks.First().Id;
                    _mode = Mode.Menu;
                }
                GUI.backgroundColor = Color.white;
            }
            GUI.Label(new Rect(x + bw, by, bw * 2 - 120, 60), T(_builderMessage), _label);
            if (GUI.Button(new Rect(x + w - 112, by, 112, 60), T("رجوع"), _button)) _mode = Mode.Menu;
        }

        void DrawBuilderRow(CardDefinition c, Rect r)
        {
            var b = _builder;
            int n = b.CountOf(c.Id);
            GUI.backgroundColor = Color.Lerp(FactionVisuals.Main(c.Faction), Color.black, n > 0 ? 0.2f : 0.55f);
            GUI.Box(r, GUIContent.none, _button);
            GUI.backgroundColor = Color.white;

            string stats = c.Type == CardType.Creature ? $"{c.Attack} / {c.Defense}" : T(TypeName(c.Type));
            var title = new GUIStyle(_label) { alignment = TextAnchor.UpperRight, fontSize = 19, fontStyle = FontStyle.Bold };
            var body = new GUIStyle(_label) { alignment = TextAnchor.UpperRight, fontSize = 14 };
            float textW = r.width - 190;
            GUI.Label(new Rect(r.x + 180, r.y + 6, textW, 30), T(c.NameAr.Split('،')[0]) + "   " + stats + "   " + T($"طاقة {c.Cost}"), title);
            GUI.Label(new Rect(r.x + 180, r.y + 38, textW, 54), T(c.TextAr), body);

            GUI.enabled = n > 0;
            if (GUI.Button(new Rect(r.x + 8, r.y + 18, 56, 60), "-", _button)) { b.Remove(c.Id); _builderMessage = ""; }
            GUI.enabled = true;
            GUI.Label(new Rect(r.x + 66, r.y + 18, 44, 60), n.ToString(), _big);
            string reason = b.CannotAddReason(c.Id);
            GUI.enabled = reason == null;
            if (GUI.Button(new Rect(r.x + 112, r.y + 18, 56, 60), "+", _button)) b.Add(c.Id);
            GUI.enabled = true;
        }

        void DrawCurve(Rect r, int[] curve)
        {
            string[] labels = { "1", "2", "3", "4", "5", "6+" };
            float cw = r.width / curve.Length;
            int max = Mathf.Max(1, curve.Max());
            for (int i = 0; i < curve.Length; i++)
            {
                float h = (r.height - 16) * curve[i] / max;
                GUI.backgroundColor = new Color(1f, 0.8f, 0.35f);
                if (curve[i] > 0) GUI.Box(new Rect(r.x + i * cw + cw * 0.3f, r.y + r.height - 16 - h, cw * 0.4f, h), GUIContent.none, _button);
                GUI.backgroundColor = Color.white;
                GUI.Label(new Rect(r.x + i * cw, r.y + r.height - 18, cw, 18), labels[i] + " (" + curve[i] + ")", new GUIStyle(_label) { fontSize = 12 });
            }
        }

        void SaveBuilderDeck()
        {
            var b = _builder;
            if (!b.IsComplete)
            {
                _builderMessage = b.Count != CardDatabase.DeckSize ? $"الديك لازم يبقى {CardDatabase.DeckSize} كارت بالظبط" : string.Join("، ", b.Problems);
                return;
            }
            if (string.IsNullOrWhiteSpace(b.Name)) b.Name = "ديكي";
            var deck = b.ToDeckList();
            _customDecks.RemoveAll(d => d.Id == deck.Id);
            _customDecks.Add(deck);
            SaveCustomDecks();
            _builderSaved = true;
            _playerDeck = deck.Id;
            _builderMessage = "اتحفظ، واتختار للماتش الجاي";
        }
    }
}
