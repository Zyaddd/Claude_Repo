using System;
using System.Collections.Generic;
using System.Linq;
using Newtonsoft.Json;
using Newtonsoft.Json.Converters;

namespace Binders.Core
{
    public enum CardType { Creature, Spell, Trap, Field }

    public enum Faction { None, Flame, Tide, Stone, Storm, Shadow }

    public enum Position { Attack, Guard }

    /// <summary>Everything a card can do. Each card lists one or more of these with an amount.</summary>
    public enum EffectKind
    {
        None,

        // Creature abilities
        Haste,                  // Can attack the turn it is summoned.
        BonusDamageOnKill,      // When it destroys a creature in battle, the opponent loses Amount more.
        ProtectedOnSummon,      // Cannot be attacked during the opponent's next turn.
        FreezeOnSummon,         // Freezes the strongest enemy creature for its controller's next turn.

        // Spells
        DestroyAllDefenseBelow, // Destroys every creature (both sides) whose defense is below Amount.
        DestroyTargetCreature,  // Destroys one enemy creature.
        DamageOpponent,         // Opponent loses Amount life.
        Heal,                   // You gain Amount life.
        BuffAttack,             // One of your creatures gains Amount attack until end of turn.
        DrawCards,              // Draw Amount cards.
        Revive,                 // Return the last creature from your graveyard to the field.
        FreezeTarget,           // Freeze one enemy creature for its controller's next turn.

        // Traps (trigger automatically when an enemy attacks)
        ReflectAttack,          // Negate the attack; the attacker's controller loses half the attacker's attack.
        FortifyDefender,        // The attacked creature switches to Guard and gains Amount defense this turn.
        WhirlpoolTrap,          // Negate the attack and freeze the attacker.

        // Field
        FactionBoost,           // All creatures of Faction gain Amount attack, on both sides of the table.
    }

    public sealed class EffectDef
    {
        [JsonConverter(typeof(StringEnumConverter))]
        public EffectKind Kind;
        public int Amount;
        [JsonConverter(typeof(StringEnumConverter))]
        public Faction Faction;
    }

    public sealed class CardDefinition
    {
        public string Id;
        public string NameAr;
        public string NameEn;
        /// <summary>Extra names a player can say out loud for this card.</summary>
        public string[] Aliases = Array.Empty<string>();
        [JsonConverter(typeof(StringEnumConverter))]
        public CardType Type;
        [JsonConverter(typeof(StringEnumConverter))]
        public Faction Faction;
        public int Cost;
        public int Attack;
        public int Defense;
        public List<EffectDef> Effects = new List<EffectDef>();
        public string TextAr;
        /// <summary>Name of the 3D model / prefab the AR layer shows for this card.</summary>
        public string ModelId;

        public bool Has(EffectKind kind) => Effects.Any(e => e.Kind == kind);
        public EffectDef Get(EffectKind kind) => Effects.FirstOrDefault(e => e.Kind == kind);

        /// <summary>All the names a player may use: Arabic name, English name and aliases.</summary>
        public IEnumerable<string> SpokenNames()
        {
            if (!string.IsNullOrWhiteSpace(NameAr)) yield return NameAr;
            if (!string.IsNullOrWhiteSpace(NameEn)) yield return NameEn;
            foreach (var a in Aliases ?? Array.Empty<string>()) yield return a;
        }
    }

    public sealed class DeckList
    {
        public string Id;
        public string NameAr;
        /// <summary>Card id → number of copies.</summary>
        public Dictionary<string, int> Cards = new Dictionary<string, int>();

        public int Count => Cards.Values.Sum();
    }

    /// <summary>The catalogue of every card and starter deck, loaded from JSON.</summary>
    public sealed class CardDatabase
    {
        public const int DeckSize = 30;
        public const int MaxCopies = 3;

        readonly Dictionary<string, CardDefinition> _cards;
        readonly Dictionary<string, DeckList> _decks;

        public CardDatabase(IEnumerable<CardDefinition> cards, IEnumerable<DeckList> decks = null)
        {
            _cards = new Dictionary<string, CardDefinition>();
            foreach (var c in cards)
            {
                if (string.IsNullOrWhiteSpace(c.Id)) throw new FormatException("A card has no id.");
                if (_cards.ContainsKey(c.Id)) throw new FormatException($"Duplicate card id '{c.Id}'.");
                _cards[c.Id] = c;
            }
            _decks = (decks ?? Enumerable.Empty<DeckList>()).ToDictionary(d => d.Id);
        }

        public static CardDatabase FromJson(string cardsJson, string decksJson = null)
        {
            var cards = JsonConvert.DeserializeObject<List<CardDefinition>>(cardsJson);
            var decks = decksJson == null ? null : JsonConvert.DeserializeObject<List<DeckList>>(decksJson);
            return new CardDatabase(cards, decks);
        }

        public IReadOnlyCollection<CardDefinition> Cards => _cards.Values;
        public IReadOnlyCollection<DeckList> Decks => _decks.Values;

        public CardDefinition Get(string id) =>
            _cards.TryGetValue(id, out var c) ? c : throw new KeyNotFoundException($"Unknown card '{id}'.");

        public DeckList GetDeck(string id) =>
            _decks.TryGetValue(id, out var d) ? d : throw new KeyNotFoundException($"Unknown deck '{id}'.");

        /// <summary>Returns the problems with a deck; an empty list means it is legal.</summary>
        public List<string> ValidateDeck(DeckList deck)
        {
            var problems = new List<string>();
            if (deck.Count != DeckSize) problems.Add($"Deck '{deck.Id}' has {deck.Count} cards, needs {DeckSize}.");
            foreach (var kv in deck.Cards)
            {
                if (!_cards.ContainsKey(kv.Key)) problems.Add($"Deck '{deck.Id}' uses unknown card '{kv.Key}'.");
                if (kv.Value > MaxCopies) problems.Add($"Deck '{deck.Id}' has {kv.Value} copies of '{kv.Key}' (max {MaxCopies}).");
            }
            return problems;
        }

        public List<CardDefinition> Expand(DeckList deck)
        {
            var list = new List<CardDefinition>();
            foreach (var kv in deck.Cards.OrderBy(k => k.Key, StringComparer.Ordinal))
                for (int i = 0; i < kv.Value; i++) list.Add(Get(kv.Key));
            return list;
        }
    }
}
