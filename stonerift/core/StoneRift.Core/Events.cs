namespace StoneRift.Core
{
    /// <summary>
    /// What happened, in order. The AR layer plays these as animations and sounds;
    /// the network sends them to the other phone.
    /// </summary>
    public enum GameEventType
    {
        GameStarted,
        TurnStarted,
        CardDrawn,
        CardBurned,         // Drawn with a full hand: goes straight to the graveyard.
        FatigueDamage,      // Tried to draw from an empty deck.
        EnergyChanged,
        CreatureSummoned,
        TrapSet,
        TechniqueUsed,
        FieldPlayed,
        FieldReplaced,
        AttackDeclared,
        TrapTriggered,
        AttackNegated,
        BattleDamage,       // Life lost from combat.
        EffectDamage,       // Life lost from a card effect.
        Healed,
        CreatureDestroyed,
        PositionChanged,
        CreatureFrozen,
        AttackBuffed,
        DefenseBuffed,
        CreatureRevived,
        TurnEnded,
        GameOver,
    }

    public sealed class GameEvent
    {
        public GameEventType Type;
        /// <summary>The player this event is about (who drew, who lost life, who won...).</summary>
        public int Player;
        public int? Source;     // Card instance id that caused it.
        public int? Target;     // Card instance id it happened to.
        public int Amount;
        public string CardId;   // Card definition id, handy for the AR layer.

        public override string ToString() =>
            $"{Type} p{Player} src={Source} tgt={Target} amt={Amount} {CardId}";
    }
}
