using System.Collections.Generic;

namespace Binders.Core
{
    /// <summary>A move a player asks for. Touch, voice and the network all produce these.</summary>
    public abstract class GameAction
    {
        public int Player;
    }

    public sealed class SummonCreature : GameAction
    {
        public int CardId;          // Instance id of the card in hand.
        public int Slot = -1;       // -1 = first free slot.
        public Position Position = Position.Attack;
    }

    public sealed class SetTrap : GameAction
    {
        public int CardId;
        public int Slot = -1;
    }

    public sealed class CastSpell : GameAction
    {
        public int CardId;
        public int? TargetId;       // Creature instance id, for spells that need one.
    }

    public sealed class PlayField : GameAction
    {
        public int CardId;
    }

    public sealed class Attack : GameAction
    {
        public int AttackerId;
        public int? TargetId;       // null = attack the opponent directly.
    }

    public sealed class ChangePosition : GameAction
    {
        public int CreatureId;
        /// <summary>null = toggle.</summary>
        public Position? To;
    }

    public sealed class EndTurn : GameAction { }

    public enum ActionError
    {
        None,
        GameOver,
        NotYourTurn,
        CardNotInHand,
        WrongCardType,
        NotEnoughEnergy,
        InvalidSlot,
        SlotOccupied,
        NoFreeSlot,
        CreatureNotFound,
        InvalidTarget,
        TargetRequired,
        NoAttacksOnFirstTurn,
        SummonedThisTurn,
        AlreadyAttacked,
        InGuardPosition,
        Frozen,
        TargetProtected,
        MustAttackCreaturesFirst,
        AlreadyChangedPosition,
        AlreadyInPosition,
        NothingToRevive,
    }

    public sealed class ActionResult
    {
        public ActionError Error;
        public List<GameEvent> Events = new List<GameEvent>();
        public bool Ok => Error == ActionError.None;

        public static ActionResult Fail(ActionError e) => new ActionResult { Error = e };
    }
}
