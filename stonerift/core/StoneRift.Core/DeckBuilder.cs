using System;
using System.Collections.Generic;
using System.Linq;
using Newtonsoft.Json;

namespace StoneRift.Core
{
    /// <summary>Numbers that help a player judge a deck while building it.</summary>
    public sealed class DeckStats
    {
        /// <summary>Cards per cost: index 0 = cost 0-1, 1 = 2, 2 = 3, 3 = 4, 4 = 5, 5 = 6 or more.</summary>
        public int[] CostCurve = new int[6];
        public Dictionary<Faction, int> Factions = new Dictionary<Faction, int>();
        public Dictionary<CardType, int> Types = new Dictionary<CardType, int>();
        public double AverageCost;
    }

    /// <summary>
    /// Edits a deck within the rules (30 cards, at most 3 copies of each) and can fill the rest automatically.
    /// </summary>
    public sealed class DeckBuilder
    {
        readonly CardDatabase _db;
        readonly Dictionary<string, int> _cards;

        public string Id { get; }
        public string Name { get; set; }

        public DeckBuilder(CardDatabase db, DeckList start = null, string id = null)
        {
            _db = db;
            Id = id ?? start?.Id ?? "custom_" + Guid.NewGuid().ToString("N").Substring(0, 8);
            Name = start?.NameAr ?? "ديك جديد";
            _cards = start == null ? new Dictionary<string, int>() : start.Cards.Where(kv => kv.Value > 0).ToDictionary(kv => kv.Key, kv => kv.Value);
        }

        public int Count => _cards.Values.Sum();
        public int CountOf(string cardId) => _cards.TryGetValue(cardId, out var n) ? n : 0;
        public bool IsComplete => Count == CardDatabase.DeckSize && Problems.Count == 0;
        public List<string> Problems => _db.ValidateDeck(ToDeckList());

        /// <summary>Why the card can't be added, or null if it can.</summary>
        public string CannotAddReason(string cardId)
        {
            if (Count >= CardDatabase.DeckSize) return $"الديك كامل ({CardDatabase.DeckSize} كارت)";
            if (CountOf(cardId) >= CardDatabase.MaxCopies) return $"أقصى حاجة {CardDatabase.MaxCopies} نسخ من نفس الكارت";
            return null;
        }

        public bool Add(string cardId)
        {
            _db.Get(cardId); // Throws for unknown cards.
            if (CannotAddReason(cardId) != null) return false;
            _cards[cardId] = CountOf(cardId) + 1;
            return true;
        }

        public bool Remove(string cardId)
        {
            int n = CountOf(cardId);
            if (n == 0) return false;
            if (n == 1) _cards.Remove(cardId);
            else _cards[cardId] = n - 1;
            return true;
        }

        public void Clear() => _cards.Clear();

        public DeckList ToDeckList() => new DeckList { Id = Id, NameAr = Name, Cards = new Dictionary<string, int>(_cards) };

        public DeckStats Stats()
        {
            var s = new DeckStats();
            int total = 0, costSum = 0;
            foreach (var kv in _cards)
            {
                var def = _db.Get(kv.Key);
                int n = kv.Value;
                s.CostCurve[Math.Min(5, Math.Max(0, def.Cost - 1))] += n;
                s.Factions[def.Faction] = (s.Factions.TryGetValue(def.Faction, out var f) ? f : 0) + n;
                s.Types[def.Type] = (s.Types.TryGetValue(def.Type, out var t) ? t : 0) + n;
                total += n;
                costSum += def.Cost * n;
            }
            s.AverageCost = total == 0 ? 0 : costSum / (double)total;
            return s;
        }

        /// <summary>
        /// Completes the deck: favours the factions already chosen (plus neutral cards),
        /// aims for about 18 constructs, and fills gaps in the cost curve first.
        /// </summary>
        public void AutoFill(int seed = 1)
        {
            var rng = new DeterministicRandom(seed);
            var chosen = Stats().Factions.Where(kv => kv.Key != Faction.None).OrderByDescending(kv => kv.Value).Select(kv => kv.Key).Take(2).ToList();
            if (chosen.Count == 0) chosen.Add((Faction)(1 + rng.Next(5)));
            if (chosen.Count == 1)
            {
                var others = Enum.GetValues(typeof(Faction)).Cast<Faction>().Where(f => f != Faction.None && f != chosen[0]).ToList();
                chosen.Add(others[rng.Next(others.Count)]);
            }

            bool Fits(CardDefinition c) => chosen.Contains(c.Faction) || c.Faction == Faction.None;
            int creatureTarget = 18;

            int guard = 0;
            while (Count < CardDatabase.DeckSize && guard++ < 500)
            {
                var stats = Stats();
                int creatures = stats.Types.TryGetValue(CardType.Creature, out var cc) ? cc : 0;
                bool wantCreature = creatures < creatureTarget;
                int[] idealCurve = { 6, 7, 6, 5, 4, 2 };
                int gapBucket = Enumerable.Range(0, 6).OrderByDescending(i => idealCurve[i] - stats.CostCurve[i]).First();

                var pool = _db.Cards.Where(c => CannotAddReason(c.Id) == null).ToList();
                var preferred = pool.Where(Fits).ToList();
                if (preferred.Count == 0) preferred = pool;
                if (preferred.Count == 0) break;

                var pick = preferred
                    .OrderByDescending(c => (c.Type == CardType.Creature) == wantCreature ? 2 : 0)
                    .ThenByDescending(c => Math.Min(5, Math.Max(0, c.Cost - 1)) == gapBucket ? 1 : 0)
                    .ThenBy(c => rng.Next(1000))
                    .First();
                Add(pick.Id);
            }
        }

        // ------------------------------------------------------------------ saving

        public static string Serialize(IEnumerable<DeckList> decks) => JsonConvert.SerializeObject(decks.ToList());

        /// <summary>Reads saved decks, dropping any that no longer match the card list.</summary>
        public static List<DeckList> Deserialize(CardDatabase db, string json)
        {
            if (string.IsNullOrWhiteSpace(json)) return new List<DeckList>();
            try
            {
                var decks = JsonConvert.DeserializeObject<List<DeckList>>(json) ?? new List<DeckList>();
                return decks.Where(d => d?.Cards != null && db.ValidateDeck(d).Count == 0).ToList();
            }
            catch (JsonException) { return new List<DeckList>(); }
        }
    }
}
