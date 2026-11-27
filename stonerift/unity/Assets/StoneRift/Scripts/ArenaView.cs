using System.Collections;
using System.Collections.Generic;
using System.Linq;
using StoneRift.Core;
using UnityEngine;

namespace StoneRift.App
{
    /// <summary>
    /// The board on the table: card slots, creatures, face-down traps and the field.
    /// Plays engine events as animations, then Sync() makes the visuals match the game exactly.
    /// Player 0 (this phone) sits on the -Z side, facing +Z.
    /// </summary>
    public sealed class ArenaView : MonoBehaviour
    {
        const float SlotSpacing = 0.12f;
        const float CreatureRow = 0.09f;
        const float SupportRow = 0.2f;

        readonly Dictionary<int, CreatureView> _creatures = new Dictionary<int, CreatureView>();
        readonly Dictionary<int, GameObject> _traps = new Dictionary<int, GameObject>();
        readonly TextMesh[] _life = new TextMesh[2];
        Renderer _board;
        GameObject _fieldGlow;
        static readonly Color BoardColor = new Color(0.16f, 0.14f, 0.2f);

        public static ArenaView Create(Transform parent)
        {
            var go = new GameObject("Arena");
            go.transform.SetParent(parent, false);
            var a = go.AddComponent<ArenaView>();
            a.BuildBoard();
            return a;
        }

        void BuildBoard()
        {
            _board = Materials.Primitive(PrimitiveType.Cube, transform, new Vector3(0, -0.005f, 0),
                new Vector3(0.44f, 0.01f, 0.6f), Materials.Instance(BoardColor)).GetComponent<Renderer>();
            Materials.Primitive(PrimitiveType.Cube, transform, new Vector3(0, 0.0005f, 0),
                new Vector3(0.42f, 0.001f, 0.004f), Materials.Lit(new Color(0.75f, 0.6f, 0.25f)));

            for (int p = 0; p < 2; p++)
            {
                var slotColor = p == 0 ? new Color(0.25f, 0.45f, 0.35f) : new Color(0.5f, 0.25f, 0.25f);
                for (int i = 0; i < 3; i++)
                {
                    Materials.Primitive(PrimitiveType.Cube, transform, CreatureSlot(p, i) + Vector3.up * 0.0006f,
                        new Vector3(0.08f, 0.001f, 0.11f), Materials.Lit(slotColor));
                    Materials.Primitive(PrimitiveType.Cube, transform, SupportSlot(p, i) + Vector3.up * 0.0006f,
                        new Vector3(0.07f, 0.001f, 0.095f), Materials.Lit(Color.Lerp(slotColor, Color.black, 0.45f)));
                }

                var lifeGo = new GameObject($"Life {p}");
                lifeGo.transform.SetParent(transform, false);
                lifeGo.transform.localPosition = new Vector3(0.17f * (p == 0 ? 1 : -1), 0.004f, p == 0 ? -0.27f : 0.27f);
                lifeGo.transform.localRotation = Quaternion.Euler(90, p == 0 ? 0 : 180, 0);
                _life[p] = lifeGo.AddComponent<TextMesh>();
                _life[p].font = Fonts.Default;
                lifeGo.GetComponent<MeshRenderer>().sharedMaterial = Fonts.Default.material;
                _life[p].fontSize = 64;
                _life[p].characterSize = 0.0028f;
                _life[p].anchor = TextAnchor.MiddleCenter;
                _life[p].color = p == 0 ? new Color(0.6f, 1f, 0.7f) : new Color(1f, 0.6f, 0.6f);
            }

            _fieldGlow = Materials.Primitive(PrimitiveType.Cylinder, transform, new Vector3(0, -0.004f, 0),
                new Vector3(0.75f, 0.002f, 0.75f), Materials.Transparent(new Color(1, 1, 1, 0.25f)));
            _fieldGlow.SetActive(false);
        }

        public static Vector3 CreatureSlot(int player, int slot) =>
            new Vector3((slot - 1) * SlotSpacing * (player == 0 ? 1 : -1), 0, player == 0 ? -CreatureRow : CreatureRow);

        public static Vector3 SupportSlot(int player, int slot) =>
            new Vector3((slot - 1) * SlotSpacing * (player == 0 ? 1 : -1), 0, player == 0 ? -SupportRow : SupportRow);

        static Vector3 PlayerSide(int player) => new Vector3(0, 0.03f, player == 0 ? -0.29f : 0.29f);

        public CreatureView ViewFor(int instanceId) => _creatures.TryGetValue(instanceId, out var v) ? v : null;

        public IEnumerable<CreatureView> AllViews => _creatures.Values;

        Vector3 World(Vector3 local) => transform.TransformPoint(local);
        float Scale => transform.lossyScale.x;

        // ------------------------------------------------------------------ sync

        public void Sync(Game game)
        {
            var alive = new HashSet<int>();
            foreach (var p in game.Players)
                foreach (var c in p.LivingCreatures)
                {
                    alive.Add(c.InstanceId);
                    var v = ViewFor(c.InstanceId) ?? Spawn(c.Card);
                    v.transform.localPosition = CreatureSlot(c.Owner, c.Slot);
                    v.transform.localRotation = Quaternion.Euler(0, c.Owner == 0 ? 0 : 180, 0);
                    v.Refresh(game);
                    v.Tint(Color.white, 0);
                }
            foreach (var id in _creatures.Keys.Where(k => !alive.Contains(k)).ToList())
            {
                Destroy(_creatures[id].gameObject);
                _creatures.Remove(id);
            }

            var traps = new HashSet<int>();
            foreach (var p in game.Players)
                for (int i = 0; i < p.Supports.Length; i++)
                {
                    var t = p.Supports[i];
                    if (t == null) continue;
                    traps.Add(t.InstanceId);
                    if (!_traps.ContainsKey(t.InstanceId))
                        _traps[t.InstanceId] = Materials.Primitive(PrimitiveType.Cube, transform, SupportSlot(p.Index, i) + Vector3.up * 0.003f,
                            new Vector3(0.065f, 0.004f, 0.09f), Materials.Instance(new Color(0.35f, 0.15f, 0.5f)));
                }
            foreach (var id in _traps.Keys.Where(k => !traps.Contains(k)).ToList())
            {
                Destroy(_traps[id]);
                _traps.Remove(id);
            }

            for (int p = 0; p < 2; p++) _life[p].text = game.Players[p].Life.ToString();

            var field = game.Field?.Card.Def;
            _board.material.color = field == null ? BoardColor : Color.Lerp(BoardColor, CreatureView.FactionColor(field.Faction), 0.45f);
            _fieldGlow.SetActive(field != null);
            if (field != null) _fieldGlow.GetComponent<Renderer>().material.color = CreatureView.FactionColor(field.Faction) * new Color(1, 1, 1, 0.3f);
        }

        CreatureView Spawn(CardInstance card)
        {
            var v = CreatureView.Create(card, transform);
            _creatures[card.InstanceId] = v;
            return v;
        }

        // ------------------------------------------------------------------ animations

        public IEnumerator Animate(GameEvent e, Game game)
        {
            switch (e.Type)
            {
                case GameEventType.CreatureSummoned:
                case GameEventType.CreatureRevived:
                {
                    var card = game.FindCard(e.Target ?? 0);
                    var c = game.FindCreature(e.Target ?? 0);
                    if (card == null || c == null) yield break;
                    var v = ViewFor(card.InstanceId) ?? Spawn(card);
                    v.transform.localPosition = CreatureSlot(c.Owner, c.Slot);
                    v.transform.localRotation = Quaternion.Euler(0, c.Owner == 0 ? 0 : 180, 0);
                    v.Refresh(game);
                    Sfx.Play(Sfx.Kind.Summon);
                    Fx.Burst(v.transform.position, CreatureView.FactionColor(card.Def.Faction), Scale, 60);
                    StartCoroutine(LightPillar(v.transform.position, CreatureView.FactionColor(card.Def.Faction)));
                    yield return Grow(v.transform);
                    break;
                }
                case GameEventType.AttackDeclared:
                {
                    var a = ViewFor(e.Source ?? 0);
                    if (a == null) yield break;
                    Vector3 from = a.transform.localPosition;
                    Vector3 to = e.Target.HasValue && ViewFor(e.Target.Value) != null
                        ? ViewFor(e.Target.Value).transform.localPosition
                        : PlayerSide(1 - a.Owner);
                    Sfx.Play(Sfx.Kind.Attack);
                    // Rise and lean in, fire the faction's attack, then settle back on the card.
                    Vector3 windUp = Vector3.Lerp(from, to, 0.25f) + Vector3.up * 0.03f;
                    yield return Move(a.transform, from, windUp, 0.2f);
                    Vector3 origin = a.transform.position + Vector3.up * a.Height * 0.5f * Scale;
                    Vector3 impact = World(to) + Vector3.up * 0.03f * Scale;
                    yield return FactionVisuals.Attack(this, a.Def.Faction, origin, impact, Scale);
                    Sfx.Play(Sfx.Kind.Hit);
                    var hitView = e.Target.HasValue ? ViewFor(e.Target.Value) : null;
                    if (hitView != null) StartCoroutine(Shake(hitView.transform));
                    yield return Move(a.transform, a.transform.localPosition, from, 0.22f);
                    break;
                }
                case GameEventType.TrapTriggered:
                {
                    Sfx.Play(Sfx.Kind.Trap);
                    var def = game.FindCard(e.Source ?? 0)?.Def;
                    if (_traps.TryGetValue(e.Source ?? 0, out var trap))
                    {
                        trap.GetComponent<Renderer>().material.color = new Color(0.9f, 0.5f, 1f);
                        yield return Move(trap.transform, trap.transform.localPosition, trap.transform.localPosition + Vector3.up * 0.04f, 0.15f);
                        Fx.Burst(trap.transform.position, new Color(0.8f, 0.4f, 1f), Scale, 50);
                    }
                    if (def != null) Fx.FloatingText(this, World(SupportSlot(e.Player, e.Amount) + Vector3.up * 0.06f), "فخ! " + def.NameAr, new Color(0.9f, 0.6f, 1f), Scale);
                    yield return new WaitForSeconds(0.5f);
                    break;
                }
                case GameEventType.AttackNegated:
                    Fx.FloatingText(this, World(new Vector3(0, 0.08f, 0)), "الهجوم اتلغى", Color.white, Scale);
                    yield return new WaitForSeconds(0.3f);
                    break;
                case GameEventType.CreatureDestroyed:
                {
                    var v = ViewFor(e.Target ?? 0);
                    if (v == null) yield break;
                    Sfx.Play(Sfx.Kind.Destroy);
                    v.Tint(Color.black, 0.6f);
                    Fx.Burst(v.transform.position + Vector3.up * 0.03f * Scale, CreatureView.FactionColor(v.Def.Faction), Scale * 1.3f, 90);
                    Fx.Burst(v.transform.position + Vector3.up * 0.03f * Scale, new Color(0.35f, 0.35f, 0.35f), Scale, 40);
                    yield return Shrink(v.transform);
                    _creatures.Remove(v.InstanceId);
                    Destroy(v.gameObject);
                    break;
                }
                case GameEventType.BattleDamage:
                case GameEventType.EffectDamage:
                case GameEventType.FatigueDamage:
                    Fx.FloatingText(this, World(PlayerSide(e.Player)), "-" + e.Amount, new Color(1f, 0.35f, 0.3f), Scale * 1.3f);
                    _life[e.Player].text = game.Players[e.Player].Life.ToString();
                    yield return new WaitForSeconds(0.35f);
                    break;
                case GameEventType.Healed:
                    Fx.FloatingText(this, World(PlayerSide(e.Player)), "+" + e.Amount, new Color(0.4f, 1f, 0.5f), Scale * 1.3f);
                    yield return new WaitForSeconds(0.3f);
                    break;
                case GameEventType.TechniqueUsed:
                {
                    Sfx.Play(Sfx.Kind.Technique);
                    var def = game.FindCard(e.Source ?? 0)?.Def;
                    if (def != null) Fx.FloatingText(this, World(new Vector3(0, 0.08f, e.Player == 0 ? -0.1f : 0.1f)), def.NameAr, new Color(0.6f, 0.85f, 1f), Scale);
                    Fx.Burst(World(new Vector3(0, 0.05f, 0)), new Color(0.5f, 0.8f, 1f), Scale * 1.5f, 50);
                    yield return new WaitForSeconds(0.5f);
                    break;
                }
                case GameEventType.FieldPlayed:
                {
                    Sfx.Play(Sfx.Kind.Technique);
                    var def = game.FindCard(e.Source ?? 0)?.Def;
                    if (def != null)
                    {
                        Fx.FloatingText(this, World(new Vector3(0, 0.1f, 0)), def.NameAr, CreatureView.FactionColor(def.Faction), Scale * 1.4f);
                        for (int i = 0; i < 6; i++)
                            Fx.Burst(World(new Vector3(Random.Range(-0.2f, 0.2f), 0.01f, Random.Range(-0.28f, 0.28f))), CreatureView.FactionColor(def.Faction), Scale, 30);
                    }
                    Sync(game);
                    yield return new WaitForSeconds(0.6f);
                    break;
                }
                case GameEventType.TrapSet:
                    Sync(game);
                    yield return new WaitForSeconds(0.15f);
                    break;
                case GameEventType.CreatureFrozen:
                case GameEventType.PositionChanged:
                case GameEventType.AttackBuffed:
                case GameEventType.DefenseBuffed:
                {
                    var v = ViewFor(e.Target ?? 0);
                    if (v != null)
                    {
                        v.Refresh(game);
                        var color = e.Type == GameEventType.CreatureFrozen ? new Color(0.6f, 0.95f, 1f)
                                  : e.Type == GameEventType.AttackBuffed ? new Color(1f, 0.6f, 0.2f) : new Color(0.5f, 0.7f, 1f);
                        Fx.Burst(v.transform.position + Vector3.up * 0.03f * Scale, color, Scale, 30);
                    }
                    yield return new WaitForSeconds(0.25f);
                    break;
                }
                case GameEventType.GameOver:
                    Sfx.Play(e.Player == 0 ? Sfx.Kind.Win : Sfx.Kind.Lose);
                    break;
            }
        }

        IEnumerator LightPillar(Vector3 at, Color c)
        {
            var pillar = FactionVisuals.Glow(null, Vector3.zero, c, 1f);
            pillar.position = at + Vector3.up * 0.08f * Scale;
            for (float k = 0; k < 1f; k += Time.deltaTime / 0.6f)
            {
                pillar.localScale = new Vector3(0.06f * (1f - k), 0.22f, 1f) * Scale;
                yield return null;
            }
            Destroy(pillar.gameObject);
        }

        static IEnumerator Shake(Transform t)
        {
            Vector3 p = t.localPosition;
            for (float k = 0; k < 1f; k += Time.deltaTime / 0.3f)
            {
                if (!t) yield break;
                t.localPosition = p + new Vector3(Mathf.Sin(k * 60f), 0, Mathf.Cos(k * 47f)) * 0.006f * (1f - k);
                yield return null;
            }
            if (t) t.localPosition = p;
        }

        static IEnumerator Grow(Transform t)
        {
            for (float k = 0; k < 1f; k += Time.deltaTime / 0.35f)
            {
                t.localScale = Vector3.one * Mathf.SmoothStep(0.05f, 1f, k);
                yield return null;
            }
            t.localScale = Vector3.one;
        }

        static IEnumerator Shrink(Transform t)
        {
            for (float k = 0; k < 1f; k += Time.deltaTime / 0.4f)
            {
                if (!t) yield break;
                t.localScale = Vector3.one * (1f - k);
                t.localRotation *= Quaternion.Euler(0, 600f * Time.deltaTime, 0);
                yield return null;
            }
        }

        static IEnumerator Move(Transform t, Vector3 from, Vector3 to, float seconds)
        {
            for (float k = 0; k < 1f; k += Time.deltaTime / seconds)
            {
                if (!t) yield break;
                t.localPosition = Vector3.Lerp(from, to, Mathf.SmoothStep(0, 1, k));
                yield return null;
            }
            if (t) t.localPosition = to;
        }
    }
}
