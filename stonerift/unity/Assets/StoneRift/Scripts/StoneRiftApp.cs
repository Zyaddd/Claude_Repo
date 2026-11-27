using System.Collections;
using System.Collections.Generic;
using System.Linq;
using StoneRift.Core;
using UnityEngine;

namespace StoneRift.App
{
    /// <summary>
    /// The whole app: menu, placing the arena, a match against the bot, touch and voice controls, and the HUD.
    /// Created automatically when the app starts, so the scene can be empty.
    /// </summary>
    public sealed class StoneRiftApp : MonoBehaviour
    {
        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        static void Boot()
        {
            if (FindAnyObjectByType<StoneRiftApp>() == null) new GameObject("StoneRift").AddComponent<StoneRiftApp>();
        }

        enum Mode { Menu, Playing }

        ArRig _rig;
        ArenaPlacer _placer;
        ArenaView _arena;
        VoiceInput _voice;
        CardDatabase _db;
        Game _game;
        SimpleBot _bot;
        Mode _mode = Mode.Menu;
        string _playerDeck = "magma_volt";

        int _selectedCreature = -1;
        int _selectedHandCard = -1;
        int _targetingTechnique = -1;
        bool _busy, _botRunning, _typing;
        string _typed = "";
        string _message = "";
        Vector2 _handScroll;
        string _loadError;

        const int Me = 0, Bot = 1;

        // ------------------------------------------------------------------ setup

        void Awake()
        {
            Application.targetFrameRate = 60;
            Screen.sleepTimeout = SleepTimeout.NeverSleep;
            gameObject.AddComponent<Sfx>();
            _voice = gameObject.AddComponent<VoiceInput>();
            _voice.Heard += OnHeard;

            try { _db = LoadDatabase(); }
            catch (System.Exception e) { _loadError = e.Message; Debug.LogException(e); }
            if (_db != null)
                _voice.Init(_db.Cards.Where(c => c.Type == CardType.Creature)
                    .SelectMany(c => c.SpokenNames()).Where(ArabicShaper.HasArabic));

            _rig = ArRig.Create();
            var arenaRoot = new GameObject("Arena Root").transform;
            _arena = ArenaView.Create(arenaRoot);
            _placer = gameObject.AddComponent<ArenaPlacer>();
            _placer.Rig = _rig;
            _placer.ArenaRoot = arenaRoot;
            _placer.IsOverUi = IsOverUi;
            _placer.enabled = false;
        }

        static CardDatabase LoadDatabase()
        {
            string cards = null, decks = null;
#if UNITY_EDITOR
            cards = UnityEditor.AssetDatabase.LoadAssetAtPath<TextAsset>("Packages/com.stonerift.core/Data/cards.json")?.text;
            decks = UnityEditor.AssetDatabase.LoadAssetAtPath<TextAsset>("Packages/com.stonerift.core/Data/decks.json")?.text;
#endif
            cards = cards ?? Resources.Load<TextAsset>("StoneRiftData/cards")?.text;
            decks = decks ?? Resources.Load<TextAsset>("StoneRiftData/decks")?.text;
            if (cards == null || decks == null) throw new System.Exception("Card data is missing from the build.");
            return CardDatabase.FromJson(cards, decks);
        }

        void StartMatch()
        {
            string botDeck = _playerDeck == "magma_volt" ? "crystal_tide" : "magma_volt";
            int seed = System.Environment.TickCount;
            _game = Game.Create(_db.Expand(_db.GetDeck(_playerDeck)), _db.Expand(_db.GetDeck(botDeck)), seed,
                name0: "أنت", name1: "الكمبيوتر");
            _bot = new SimpleBot(seed ^ 0x5bd1);
            _selectedCreature = _selectedHandCard = _targetingTechnique = -1;
            _busy = _botRunning = false;
            _mode = Mode.Playing;
            _placer.enabled = true;
            _arena.Sync(_game);
            Say(_game.ActivePlayer == Me ? "العملة وقعت لصالحك، ابدأ!" : "الكمبيوتر هيبدأ");
        }

        // ------------------------------------------------------------------ frame loop

        void Update()
        {
            if (_mode != Mode.Playing || _game == null || !_placer.Placed) return;

            if (_game.Phase == Phase.Main && _game.ActivePlayer == Bot && !_busy && !_botRunning)
                StartCoroutine(BotTurn());

            var input = TouchInput.Current;
            if (input.Tapped && !_busy && !IsOverUi(input.TapPosition) && _game.ActivePlayer == Me && _game.Phase == Phase.Main)
                HandleTap(input.TapPosition);

            foreach (var v in _arena.AllViews) v.SetSelected(v.InstanceId == _selectedCreature);
        }

        void HandleTap(Vector2 screenPos)
        {
            var ray = _rig.Cam.ScreenPointToRay(screenPos);
            CreatureView hit = null;
            if (Physics.Raycast(ray, out var info, 20f)) hit = info.collider.GetComponentInParent<CreatureView>();

            if (hit == null)
            {
                _selectedCreature = -1;
                return;
            }

            if (_targetingTechnique >= 0)
            {
                int technique = _targetingTechnique;
                _targetingTechnique = -1;
                Run(new UseTechnique { Player = Me, CardId = technique, TargetId = hit.InstanceId });
                return;
            }

            if (hit.Owner == Me)
            {
                _selectedCreature = _selectedCreature == hit.InstanceId ? -1 : hit.InstanceId;
                _selectedHandCard = -1;
                if (_selectedCreature >= 0) Say(hit.Def.NameAr + ": المس كائن للخصم علشان تهاجمه");
            }
            else if (_selectedCreature >= 0)
            {
                Run(new Attack { Player = Me, AttackerId = _selectedCreature, TargetId = hit.InstanceId });
                _selectedCreature = -1;
            }
            else
            {
                Say(hit.Def.NameAr + ": " + hit.Def.TextAr);
            }
        }

        bool Run(GameAction action)
        {
            if (_busy) return false;
            var r = _game.Execute(action);
            if (!r.Ok)
            {
                Say(ErrorText(r.Error));
                return false;
            }
            StartCoroutine(Play(r.Events));
            return true;
        }

        IEnumerator Play(List<GameEvent> events)
        {
            _busy = true;
            foreach (var e in events)
            {
                Narrate(e);
                yield return _arena.Animate(e, _game);
            }
            _arena.Sync(_game);
            if (_selectedCreature >= 0 && _game.FindCreature(_selectedCreature) == null) _selectedCreature = -1;
            if (_selectedHandCard >= 0 && !_game.Players[Me].Hand.Any(c => c.InstanceId == _selectedHandCard)) _selectedHandCard = -1;
            _busy = false;
        }

        IEnumerator BotTurn()
        {
            _botRunning = true;
            yield return new WaitForSeconds(0.8f);
            for (int i = 0; i < 40 && _game.Phase == Phase.Main && _game.ActivePlayer == Bot; i++)
            {
                var action = _bot.ChooseAction(_game, Bot);
                if (action == null) break;
                var r = _game.Execute(action);
                if (!r.Ok) { _game.Execute(new EndTurn { Player = Bot }); break; }
                yield return Play(r.Events);
                yield return new WaitForSeconds(action is EndTurn ? 0.1f : 0.55f);
            }
            _botRunning = false;
        }

        // ------------------------------------------------------------------ voice

        void OnHeard(List<string> guesses)
        {
            if (_game == null || _mode != Mode.Playing) return;
            if (_game.ActivePlayer != Me || _busy) { Say("استنى لحد ما ييجي دورك"); return; }

            VoiceCommand first = null;
            foreach (var g in guesses)
            {
                var cmd = VoiceCommandParser.Parse(_game, Me, g);
                first = first ?? cmd;
                var action = cmd.ToAction(Me);
                if (action != null && _game.Validate(action) == ActionError.None)
                {
                    Say("«" + g + "»");
                    Run(action);
                    return;
                }
            }
            var fallback = first?.ToAction(Me);
            if (fallback != null) Say(ErrorText(_game.Validate(fallback)));
            else Say("«" + guesses[0] + "»: " + (first?.Problem ?? "مفهمتش"));
        }

        // ------------------------------------------------------------------ text

        void Say(string text) => _message = text;

        string Name(int? instanceId)
        {
            if (!instanceId.HasValue) return "";
            var c = _game.FindCard(instanceId.Value);
            return c == null ? "" : c.Def.NameAr.Split('،')[0];
        }

        void Narrate(GameEvent e)
        {
            switch (e.Type)
            {
                case GameEventType.TurnStarted: Say(e.Player == Me ? "دورك!" : "دور الكمبيوتر…"); break;
                case GameEventType.CreatureSummoned: Say(Name(e.Target) + " نزل الساحة"); break;
                case GameEventType.AttackDeclared:
                    Say(Name(e.Source) + " بيهاجم " + (e.Target.HasValue ? Name(e.Target) : (e.Player == Me ? "الكمبيوتر مباشرة" : "إنت مباشرة")));
                    break;
                case GameEventType.TrapTriggered: Say("فخ! " + Name(e.Source)); break;
                case GameEventType.CreatureDestroyed: Say(Name(e.Target) + " اتدمّر"); break;
                case GameEventType.TechniqueUsed: Say("تقنية: " + Name(e.Source)); break;
                case GameEventType.FieldPlayed: Say("الساحة اتحولت: " + Name(e.Source)); break;
                case GameEventType.CreatureFrozen: Say(Name(e.Target) + " اتجمّد"); break;
                case GameEventType.GameOver: Say(e.Player == Me ? "كسبت!" : "خسرت المرة دي"); break;
            }
        }

        static string ErrorText(ActionError e)
        {
            switch (e)
            {
                case ActionError.NotYourTurn: return "مش دورك";
                case ActionError.NotEnoughEnergy: return "طاقتك مش كفاية";
                case ActionError.NoFreeSlot: return "مفيش مكان فاضي";
                case ActionError.NoAttacksOnFirstTurn: return "مفيش هجوم في أول دور في الماتش";
                case ActionError.SummonedThisTurn: return "الكائن لسه نازل، يهاجم الدور الجاي";
                case ActionError.AlreadyAttacked: return "الكائن ده هاجم خلاص الدور ده";
                case ActionError.InGuardPosition: return "الكائن في وضع الدفاع، غيّر وضعه الأول";
                case ActionError.Frozen: return "الكائن متجمّد";
                case ActionError.TargetProtected: return "الكائن ده محمي الدور ده";
                case ActionError.MustAttackCreaturesFirst: return "لازم تهاجم كائنات الخصم الأول";
                case ActionError.AlreadyChangedPosition: return "غيّرت وضعه خلاص الدور ده";
                case ActionError.AlreadyInPosition: return "هو أصلاً في الوضع ده";
                case ActionError.TargetRequired: return "اختار هدف";
                case ActionError.InvalidTarget: return "الهدف ده مينفعش";
                case ActionError.NothingToRevive: return "مفيش كائن اتدمّر عندك لسه";
                case ActionError.GameOver: return "الماتش خلص";
                default: return e.ToString();
            }
        }

        static string TypeName(CardType t) =>
            t == CardType.Creature ? "كائن" : t == CardType.Technique ? "تقنية" : t == CardType.Trap ? "فخ" : "ساحة";

        // ------------------------------------------------------------------ HUD (IMGUI keeps it asset-free)

        const float VirtualHeight = 900f;
        float _ui = 1f;
        float VW => Screen.width / _ui;
        readonly List<Rect> _uiRects = new List<Rect>();
        GUIStyle _label, _big, _button, _card, _box;

        bool IsOverUi(Vector2 screenPos)
        {
            var p = new Vector2(screenPos.x / _ui, (Screen.height - screenPos.y) / _ui);
            return _uiRects.Any(r => r.Contains(p));
        }

        static string T(string s) => ArabicShaper.Fix(s);

        void Styles()
        {
            if (_label != null) return;
            _label = new GUIStyle(GUI.skin.label) { fontSize = 20, wordWrap = true, alignment = TextAnchor.MiddleCenter };
            _label.normal.textColor = Color.white;
            _big = new GUIStyle(_label) { fontSize = 34, fontStyle = FontStyle.Bold };
            _button = new GUIStyle(GUI.skin.button) { fontSize = 20, wordWrap = true };
            _card = new GUIStyle(GUI.skin.button) { fontSize = 15, wordWrap = true, alignment = TextAnchor.UpperCenter, padding = new RectOffset(4, 4, 6, 4) };
            _box = new GUIStyle(GUI.skin.box);
            var bg = new Texture2D(1, 1);
            bg.SetPixel(0, 0, new Color(0, 0, 0, 0.55f));
            bg.Apply();
            _box.normal.background = bg;
        }

        Rect Ui(Rect r) { _uiRects.Add(r); return r; }

        void OnGUI()
        {
            Styles();
            _ui = Screen.height / VirtualHeight;
            GUI.matrix = Matrix4x4.Scale(new Vector3(_ui, _ui, 1));
            if (Event.current.type == EventType.Layout) _uiRects.Clear();

            if (_loadError != null)
            {
                GUI.Label(new Rect(20, 300, VW - 40, 200), "Error: " + _loadError, _label);
                return;
            }
            if (_mode == Mode.Menu) { DrawMenu(); return; }
            if (!_placer.Placed) { DrawPlacement(); return; }

            DrawTopBar();
            if (_game.Phase == Phase.GameOver) { DrawGameOver(); return; }
            DrawActions();
            DrawHand();
        }

        void DrawMenu()
        {
            float w = Mathf.Min(VW - 40, 520), x = (VW - w) / 2;
            GUI.Box(new Rect(0, 0, VW, VirtualHeight), GUIContent.none, _box);
            GUI.Label(new Rect(x, 90, w, 60), "STONERIFT", _big);
            GUI.Label(new Rect(x, 150, w, 40), T("ستون ريفت: صدع الحجر"), _big);
            GUI.Label(new Rect(x, 230, w, 40), T("اختار الديك بتاعك:"), _label);

            string[] decks = { "magma_volt", "crystal_tide" };
            for (int i = 0; i < decks.Length; i++)
            {
                var d = _db.GetDeck(decks[i]);
                GUI.backgroundColor = _playerDeck == d.Id ? new Color(1f, 0.8f, 0.3f) : Color.white;
                if (GUI.Button(new Rect(x, 280 + i * 90, w, 75), T(d.NameAr), _button)) _playerDeck = d.Id;
            }
            GUI.backgroundColor = new Color(0.4f, 1f, 0.5f);
            if (GUI.Button(new Rect(x, 480, w, 90), T("ابدأ ماتش ضد الكمبيوتر"), _button)) StartMatch();
            GUI.backgroundColor = Color.white;
            GUI.Label(new Rect(x, 600, w, 120), T("اللعب مع صاحبك على نفس الترابيزة جاي في التحديث الجاي."), _label);
        }

        void DrawPlacement()
        {
            float w = VW - 40;
            var panel = Ui(new Rect(20, 40, w, 170));
            GUI.Box(panel, GUIContent.none, _box);
            string text = !string.IsNullOrEmpty(_rig.Status) ? _rig.Status
                : _placer.HasTarget ? "المس الشاشة علشان تحط الساحة هنا"
                : "وجّه الكاميرا للترابيزة وحرّك الموبايل ببطء لحد ما تظهر الدائرة";
            GUI.Label(new Rect(30, 50, w - 20, 100), T(text), _label);
            if (!_rig.Fallback && GUI.Button(Ui(new Rect(20 + w / 2 - 150, 150, 300, 50)), T("العب من غير AR"), _button))
                _rig.EnableFallback("");
        }

        void DrawTopBar()
        {
            var bar = Ui(new Rect(0, 0, VW, 92));
            GUI.Box(bar, GUIContent.none, _box);
            var me = _game.Players[Me];
            var bot = _game.Players[Bot];
            float third = VW / 3;
            GUI.Label(new Rect(0, 4, third, 44), T($"إنت: {me.Life}") , _label);
            GUI.Label(new Rect(0, 44, third, 40), T($"طاقة {me.Energy}/{me.MaxEnergy}  ديك {me.Deck.Count}"), _label);
            string turn = _game.Phase == Phase.GameOver ? "انتهى" : _game.ActivePlayer == Me ? "دورك" : "دور الكمبيوتر";
            GUI.Label(new Rect(third, 4, third, 44), T(turn), _big);
            GUI.Label(new Rect(third * 2, 4, third, 44), T($"الكمبيوتر: {bot.Life}"), _label);
            GUI.Label(new Rect(third * 2, 44, third, 40), T($"طاقة {bot.Energy}/{bot.MaxEnergy}  إيد {bot.Hand.Count}"), _label);

            var msg = !string.IsNullOrEmpty(_voice.Status) && (_voice.Listening || _voice.Thinking) ? _voice.Status : _message;
            if (!string.IsNullOrEmpty(msg))
            {
                var r = Ui(new Rect(10, 96, VW - 20, 56));
                GUI.Box(r, GUIContent.none, _box);
                GUI.Label(r, T(msg), _label);
            }
        }

        void DrawGameOver()
        {
            float w = Mathf.Min(VW - 40, 500), x = (VW - w) / 2;
            var r = Ui(new Rect(x, 280, w, 300));
            GUI.Box(r, GUIContent.none, _box);
            GUI.Label(new Rect(x, 300, w, 80), T(_game.Winner == Me ? "كسبت!" : "خسرت"), _big);
            if (GUI.Button(new Rect(x + 30, 400, w - 60, 70), T("ماتش جديد"), _button)) StartMatch();
            if (GUI.Button(new Rect(x + 30, 485, w - 60, 70), T("القايمة"), _button)) { _mode = Mode.Menu; _placer.ResetPlacement(); _placer.enabled = false; }
        }

        void DrawActions()
        {
            float y = VirtualHeight - 290, h = 80;
            var area = Ui(new Rect(0, y - 10, VW, h + 20));
            GUI.Box(area, GUIContent.none, _box);
            bool myTurn = _game.ActivePlayer == Me && !_busy;
            GUI.enabled = myTurn;

            var hand = _game.Players[Me].Hand.FirstOrDefault(c => c.InstanceId == _selectedHandCard);
            var creature = _selectedCreature >= 0 ? _game.FindCreature(_selectedCreature) : null;

            if (_typing)
            {
                GUI.enabled = true;
                _typed = GUI.TextField(new Rect(10, y, VW - 230, h), _typed, _button);
                if (GUI.Button(new Rect(VW - 215, y, 100, h), T("نفّذ"), _button) && _typed.Trim().Length > 0)
                {
                    OnHeard(new List<string> { _typed });
                    _typed = "";
                    _typing = false;
                }
                if (GUI.Button(new Rect(VW - 110, y, 100, h), T("إلغاء"), _button)) _typing = false;
            }
            else if (_targetingTechnique >= 0)
            {
                GUI.Label(new Rect(10, y, VW - 140, h), T("المس الكائن اللي عايز تستخدم عليه التقنية"), _label);
                if (GUI.Button(new Rect(VW - 125, y, 115, h), T("إلغاء"), _button)) _targetingTechnique = -1;
            }
            else if (hand != null)
            {
                var def = hand.Def;
                var buttons = new List<(string, GameAction)>();
                switch (def.Type)
                {
                    case CardType.Creature:
                        buttons.Add(("استدعاء هجوم", new SummonCreature { Player = Me, CardId = hand.InstanceId, Position = Position.Attack }));
                        buttons.Add(("استدعاء دفاع", new SummonCreature { Player = Me, CardId = hand.InstanceId, Position = Position.Guard }));
                        break;
                    case CardType.Trap: buttons.Add(("حط الفخ", new SetTrap { Player = Me, CardId = hand.InstanceId })); break;
                    case CardType.Field: buttons.Add(("العب الساحة", new PlayField { Player = Me, CardId = hand.InstanceId })); break;
                    case CardType.Technique: buttons.Add(("استخدم", new UseTechnique { Player = Me, CardId = hand.InstanceId })); break;
                }
                float bw = (VW - 20) / (buttons.Count + 1);
                for (int i = 0; i < buttons.Count; i++)
                {
                    var (label, action) = buttons[i];
                    if (!GUI.Button(new Rect(10 + i * bw, y, bw - 8, h), T(label), _button)) continue;
                    if (action is UseTechnique cs && Game.TechniqueNeedsTarget(def))
                    {
                        var err = _game.Validate(new UseTechnique { Player = Me, CardId = cs.CardId, TargetId = -1 });
                        if (err == ActionError.NotEnoughEnergy) Say(ErrorText(err));
                        else { _targetingTechnique = cs.CardId; Say("المس الهدف"); }
                    }
                    else Run(action);
                    _selectedHandCard = -1;
                }
                if (GUI.Button(new Rect(10 + buttons.Count * bw, y, bw - 8, h), T("إلغاء"), _button)) _selectedHandCard = -1;
            }
            else if (creature != null)
            {
                float bw = (VW - 20) / 3;
                if (GUI.Button(new Rect(10, y, bw - 8, h), T("هجوم مباشر"), _button))
                    Run(new Attack { Player = Me, AttackerId = creature.InstanceId });
                if (GUI.Button(new Rect(10 + bw, y, bw - 8, h), T(creature.Position == Position.Attack ? "وضع الدفاع" : "وضع الهجوم"), _button))
                    Run(new ChangePosition { Player = Me, CreatureId = creature.InstanceId });
                if (GUI.Button(new Rect(10 + bw * 2, y, bw - 8, h), T("إلغاء"), _button)) _selectedCreature = -1;
            }
            else
            {
                float bw = (VW - 20) / 4;
                GUI.backgroundColor = _voice.Listening ? new Color(1f, 0.35f, 0.35f) : _voice.Ready ? new Color(1f, 0.85f, 0.4f) : new Color(0.7f, 0.7f, 0.7f);
                string micLabel = _voice.Listening ? "خلصت" : _voice.Thinking ? "بفكّر…" : "اتكلم";
                if (GUI.Button(new Rect(10, y, bw * 1.5f - 8, h), T(micLabel), _button))
                {
                    _voice.Toggle();
                    if (!_voice.Listening && !_voice.Thinking) Say(_voice.Status);
                }
                GUI.backgroundColor = Color.white;
                if (GUI.Button(new Rect(10 + bw * 1.5f, y, bw * 0.75f - 8, h), T("اكتب"), _button)) _typing = true;
                if (GUI.Button(new Rect(10 + bw * 2.25f, y, bw - 8, h), T("إنهاء الدور"), _button)) Run(new EndTurn { Player = Me });
                GUI.enabled = true;
                if (GUI.Button(new Rect(10 + bw * 3.25f, y, bw * 0.75f - 8, h), T("نقل الساحة"), _button)) _placer.ResetPlacement();
            }
            GUI.enabled = true;
        }

        void DrawHand()
        {
            float h = 190, y = VirtualHeight - h;
            var area = Ui(new Rect(0, y, VW, h));
            GUI.Box(area, GUIContent.none, _box);
            var me = _game.Players[Me];
            const float cw = 125, gap = 8;
            float contentW = Mathf.Max(VW, me.Hand.Count * (cw + gap) + gap);
            _handScroll = GUI.BeginScrollView(new Rect(0, y + 5, VW, h - 5), _handScroll, new Rect(0, 0, contentW, h - 25));
            for (int i = 0; i < me.Hand.Count; i++)
            {
                var c = me.Hand[i];
                var def = c.Def;
                bool affordable = def.Cost <= me.Energy;
                GUI.backgroundColor = Color.Lerp(CreatureView.FactionColor(def.Faction), Color.white, _selectedHandCard == c.InstanceId ? 0.7f : 0.25f);
                GUI.enabled = _game.ActivePlayer == Me && !_busy;
                string stats = def.Type == CardType.Creature ? $"{def.Attack} / {def.Defense}" : T(TypeName(def.Type));
                string text = T(def.NameAr.Split('،')[0]) + "\n" + T($"طاقة {def.Cost}") + "\n" + stats;
                var content = new GUIContent(text, T(def.TextAr));
                var style = new GUIStyle(_card);
                style.normal.textColor = affordable ? Color.white : new Color(1, 1, 1, 0.45f);
                if (GUI.Button(new Rect(gap + i * (cw + gap), 0, cw, h - 30), content, style))
                {
                    _selectedHandCard = _selectedHandCard == c.InstanceId ? -1 : c.InstanceId;
                    _selectedCreature = -1;
                    if (_selectedHandCard >= 0) Say(def.NameAr + ": " + def.TextAr);
                }
            }
            GUI.EndScrollView();
            GUI.backgroundColor = Color.white;
            GUI.enabled = true;
        }
    }
}
