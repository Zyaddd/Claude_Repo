using System.Collections.Generic;
using System.Linq;

namespace StoneRift.Core
{
    /// <summary>One physical copy of a card during a match.</summary>
    public sealed class CardInstance
    {
        public int InstanceId;
        public CardDefinition Def;
        public int Owner;

        public override string ToString() => $"{Def.NameEn}#{InstanceId}";
    }

    /// <summary>A creature standing on the table.</summary>
    public sealed class CreatureState
    {
        public CardInstance Card;
        public int Slot;
        public Position Position;
        public int SummonedTurn;
        public bool HasAttacked;
        public bool ChangedPosition;
        /// <summary>The creature cannot attack or change position while TurnNumber &lt;= this.</summary>
        public int FrozenThroughTurn;
        /// <summary>The creature cannot be attacked while TurnNumber &lt;= this.</summary>
        public int ProtectedThroughTurn;
        public int TempAttack;
        public int TempDefense;

        public int InstanceId => Card.InstanceId;
        public int Owner => Card.Owner;
        public CardDefinition Def => Card.Def;
    }

    public sealed class PlayerState
    {
        public const int CreatureSlots = 3;
        public const int SupportSlots = 3;

        public int Index;
        public string Name;
        public int Life;
        public int Energy;
        public int MaxEnergy;
        public List<CardInstance> Deck = new List<CardInstance>();
        public List<CardInstance> Hand = new List<CardInstance>();
        public List<CardInstance> Graveyard = new List<CardInstance>();
        public CreatureState[] Creatures = new CreatureState[CreatureSlots];
        /// <summary>Face-down traps.</summary>
        public CardInstance[] Supports = new CardInstance[SupportSlots];

        public IEnumerable<CreatureState> LivingCreatures => Creatures.Where(c => c != null);
        public bool HasCreatures => Creatures.Any(c => c != null);
        public int FreeCreatureSlot() => System.Array.FindIndex(Creatures, c => c == null);
        public int FreeSupportSlot() => System.Array.FindIndex(Supports, c => c == null);
    }

    /// <summary>The field card is shared: the whole table turns into that place for both players.</summary>
    public sealed class FieldState
    {
        public CardInstance Card;
        public int Owner => Card.Owner;
    }

    public enum Phase { Main, GameOver }
}
