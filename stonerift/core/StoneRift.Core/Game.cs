using System;
using System.Collections.Generic;
using System.Linq;

namespace StoneRift.Core
{
    public sealed class GameConfig
    {
        public int StartingLife = 4000;
        public int StartingHand = 5;
        public int MaxEnergy = 10;
        public int MaxHand = 8;
        public int FatigueDamage = 500;
    }

    /// <summary>
    /// The whole rules engine. It knows nothing about AR, Unity or the network:
    /// you give it actions and it returns the events that happened.
    /// The host phone owns the real Game; the other phone sends it actions.
    /// </summary>
    public sealed class Game
    {
        public GameConfig Config { get; }
        public PlayerState[] Players { get; } = new PlayerState[2];
        public int ActivePlayer { get; private set; }
        /// <summary>Counts every turn of both players, starting at 1.</summary>
        public int TurnNumber { get; private set; }
        public Phase Phase { get; private set; } = Phase.Main;
        /// <summary>-1 while the game is running.</summary>
        public int Winner { get; private set; } = -1;
        public FieldState Field { get; private set; }
        /// <summary>Every event since the game started.</summary>
        public List<GameEvent> Log { get; } = new List<GameEvent>();

        readonly DeterministicRandom _rng;
        int _nextInstanceId = 1;
        List<GameEvent> _sink;

        Game(GameConfig config, int seed)
        {
            Config = config ?? new GameConfig();
            _rng = new DeterministicRandom(seed);
        }

        /// <summary>Starts a match. firstPlayer = -1 picks one at random (the coin flip).</summary>
        public static Game Create(IList<CardDefinition> deck0, IList<CardDefinition> deck1, int seed,
            int firstPlayer = -1, string name0 = "Player 1", string name1 = "Player 2", GameConfig config = null)
        {
            var g = new Game(config, seed);
            g._sink = g.Log;
            var decks = new[] { deck0, deck1 };
            var names = new[] { name0, name1 };
            for (int p = 0; p < 2; p++)
            {
                var ps = new PlayerState { Index = p, Name = names[p], Life = g.Config.StartingLife };
                foreach (var def in decks[p])
                    ps.Deck.Add(new CardInstance { InstanceId = g._nextInstanceId++, Def = def, Owner = p });
                g._rng.Shuffle(ps.Deck);
                g.Players[p] = ps;
            }

            int first = firstPlayer >= 0 ? firstPlayer : g._rng.Next(2);
            g.Emit(GameEventType.GameStarted, first);
            for (int p = 0; p < 2; p++)
                for (int i = 0; i < g.Config.StartingHand; i++) g.Draw(p);
            g.StartTurn(first);
            g._sink = null;
            return g;
        }

        // ------------------------------------------------------------------ queries

        public PlayerState Opponent(int player) => Players[1 - player];

        public CreatureState FindCreature(int instanceId) =>
            Players.SelectMany(p => p.LivingCreatures).FirstOrDefault(c => c.InstanceId == instanceId);

        /// <summary>Finds any card in the match by instance id, wherever it is.</summary>
        public CardInstance FindCard(int instanceId)
        {
            if (Field != null && Field.Card.InstanceId == instanceId) return Field.Card;
            foreach (var p in Players)
            {
                var c = p.LivingCreatures.FirstOrDefault(x => x.InstanceId == instanceId)?.Card
                     ?? p.Supports.FirstOrDefault(x => x != null && x.InstanceId == instanceId)
                     ?? p.Hand.FirstOrDefault(x => x.InstanceId == instanceId)
                     ?? p.Graveyard.FirstOrDefault(x => x.InstanceId == instanceId)
                     ?? p.Deck.FirstOrDefault(x => x.InstanceId == instanceId);
                if (c != null) return c;
            }
            return null;
        }

        public int EffectiveAttack(CreatureState c)
        {
            int atk = c.Def.Attack + c.TempAttack;
            var boost = Field?.Card.Def.Get(EffectKind.FactionBoost);
            if (boost != null && boost.Faction == c.Def.Faction) atk += boost.Amount;
            return Math.Max(0, atk);
        }

        public int EffectiveDefense(CreatureState c) => Math.Max(0, c.Def.Defense + c.TempDefense);

        public bool IsFrozen(CreatureState c) => TurnNumber <= c.FrozenThroughTurn;
        public bool IsProtected(CreatureState c) => TurnNumber <= c.ProtectedThroughTurn;

        // ------------------------------------------------------------------ validation

        public ActionError Validate(GameAction action)
        {
            if (Phase == Phase.GameOver) return ActionError.GameOver;
            if (action.Player != ActivePlayer) return ActionError.NotYourTurn;
            var me = Players[action.Player];

            switch (action)
            {
                case SummonCreature s:
                {
                    var err = CheckHandCard(me, s.CardId, CardType.Creature, out _);
                    if (err != ActionError.None) return err;
                    return CheckSlot(me.Creatures, s.Slot);
                }
                case SetTrap t:
                {
                    var err = CheckHandCard(me, t.CardId, CardType.Trap, out _);
                    if (err != ActionError.None) return err;
                    return CheckSlot(me.Supports, t.Slot);
                }
                case PlayField f:
                    return CheckHandCard(me, f.CardId, CardType.Field, out _);
                case UseTechnique cs:
                {
                    var err = CheckHandCard(me, cs.CardId, CardType.Technique, out var card);
                    if (err != ActionError.None) return err;
                    return CheckTechniqueTarget(me, card.Def, cs.TargetId);
                }
                case Attack a:
                    return ValidateAttack(me, a);
                case ChangePosition cp:
                {
                    var c = FindCreature(cp.CreatureId);
                    if (c == null || c.Owner != me.Index) return ActionError.CreatureNotFound;
                    if (IsFrozen(c)) return ActionError.Frozen;
                    if (c.HasAttacked) return ActionError.AlreadyAttacked;
                    if (c.ChangedPosition) return ActionError.AlreadyChangedPosition;
                    if (cp.To.HasValue && cp.To.Value == c.Position) return ActionError.AlreadyInPosition;
                    return ActionError.None;
                }
                case EndTurn _:
                    return ActionError.None;
                default:
                    throw new ArgumentException($"Unknown action {action.GetType().Name}");
            }
        }

        ActionError CheckHandCard(PlayerState me, int cardId, CardType type, out CardInstance card)
        {
            card = me.Hand.FirstOrDefault(c => c.InstanceId == cardId);
            if (card == null) return ActionError.CardNotInHand;
            if (card.Def.Type != type) return ActionError.WrongCardType;
            if (card.Def.Cost > me.Energy) return ActionError.NotEnoughEnergy;
            return ActionError.None;
        }

        static ActionError CheckSlot<T>(T[] slots, int slot) where T : class
        {
            if (slot == -1) return Array.FindIndex(slots, s => s == null) < 0 ? ActionError.NoFreeSlot : ActionError.None;
            if (slot < 0 || slot >= slots.Length) return ActionError.InvalidSlot;
            return slots[slot] != null ? ActionError.SlotOccupied : ActionError.None;
        }

        public static bool TechniqueNeedsTarget(CardDefinition def) =>
            def.Has(EffectKind.DestroyTargetCreature) || def.Has(EffectKind.FreezeTarget) || def.Has(EffectKind.BuffAttack);

        /// <summary>True if the technique's target must be an enemy creature, false if it must be your own.</summary>
        public static bool TechniqueTargetsEnemy(CardDefinition def) => !def.Has(EffectKind.BuffAttack);

        ActionError CheckTechniqueTarget(PlayerState me, CardDefinition def, int? targetId)
        {
            if (def.Has(EffectKind.Revive))
            {
                if (!me.Graveyard.Any(c => c.Def.Type == CardType.Creature)) return ActionError.NothingToRevive;
                if (me.FreeCreatureSlot() < 0) return ActionError.NoFreeSlot;
            }
            if (!TechniqueNeedsTarget(def)) return ActionError.None;
            if (targetId == null) return ActionError.TargetRequired;
            var t = FindCreature(targetId.Value);
            if (t == null) return ActionError.InvalidTarget;
            bool enemy = t.Owner != me.Index;
            return enemy == TechniqueTargetsEnemy(def) ? ActionError.None : ActionError.InvalidTarget;
        }

        ActionError ValidateAttack(PlayerState me, Attack a)
        {
            if (TurnNumber == 1) return ActionError.NoAttacksOnFirstTurn;
            var attacker = FindCreature(a.AttackerId);
            if (attacker == null || attacker.Owner != me.Index) return ActionError.CreatureNotFound;
            if (attacker.Position == Position.Guard) return ActionError.InGuardPosition;
            if (IsFrozen(attacker)) return ActionError.Frozen;
            if (attacker.HasAttacked) return ActionError.AlreadyAttacked;
            if (attacker.SummonedTurn == TurnNumber && !attacker.Def.Has(EffectKind.Haste)) return ActionError.SummonedThisTurn;

            var foe = Opponent(me.Index);
            if (a.TargetId == null)
                return foe.LivingCreatures.Any(c => !IsProtected(c)) ? ActionError.MustAttackCreaturesFirst : ActionError.None;

            var target = FindCreature(a.TargetId.Value);
            if (target == null || target.Owner == me.Index) return ActionError.InvalidTarget;
            if (IsProtected(target)) return ActionError.TargetProtected;
            return ActionError.None;
        }

        // ------------------------------------------------------------------ execution

        public ActionResult Execute(GameAction action)
        {
            var err = Validate(action);
            if (err != ActionError.None) return ActionResult.Fail(err);

            var result = new ActionResult();
            _sink = result.Events;
            try
            {
                var me = Players[action.Player];
                switch (action)
                {
                    case SummonCreature s: DoSummon(me, s); break;
                    case SetTrap t: DoSetTrap(me, t); break;
                    case PlayField f: DoPlayField(me, f); break;
                    case UseTechnique cs: DoUseTechnique(me, cs); break;
                    case Attack a: DoAttack(me, a); break;
                    case ChangePosition cp: DoChangePosition(cp); break;
                    case EndTurn _: DoEndTurn(me); break;
                }
            }
            finally
            {
                _sink = null;
            }
            Log.AddRange(result.Events);
            return result;
        }

        CardInstance TakeFromHand(PlayerState me, int cardId)
        {
            var card = me.Hand.First(c => c.InstanceId == cardId);
            me.Hand.Remove(card);
            if (card.Def.Cost > 0)
            {
                me.Energy -= card.Def.Cost;
                Emit(GameEventType.EnergyChanged, me.Index, amount: me.Energy);
            }
            return card;
        }

        void DoSummon(PlayerState me, SummonCreature s)
        {
            var card = TakeFromHand(me, s.CardId);
            int slot = s.Slot >= 0 ? s.Slot : me.FreeCreatureSlot();
            var c = new CreatureState { Card = card, Slot = slot, Position = s.Position, SummonedTurn = TurnNumber };
            me.Creatures[slot] = c;
            Emit(GameEventType.CreatureSummoned, me.Index, target: card.InstanceId, amount: slot, cardId: card.Def.Id);

            if (card.Def.Has(EffectKind.ProtectedOnSummon)) c.ProtectedThroughTurn = TurnNumber + 1;
            if (card.Def.Has(EffectKind.FreezeOnSummon))
            {
                var strongest = Opponent(me.Index).LivingCreatures
                    .OrderByDescending(EffectiveAttack).ThenBy(x => x.Slot).FirstOrDefault();
                if (strongest != null) Freeze(strongest, card.InstanceId);
            }
        }

        void DoSetTrap(PlayerState me, SetTrap t)
        {
            var card = TakeFromHand(me, t.CardId);
            int slot = t.Slot >= 0 ? t.Slot : me.FreeSupportSlot();
            me.Supports[slot] = card;
            // CardId is included for the owner's phone; the network layer must hide it from the opponent.
            Emit(GameEventType.TrapSet, me.Index, target: card.InstanceId, amount: slot, cardId: card.Def.Id);
        }

        void DoPlayField(PlayerState me, PlayField f)
        {
            var card = TakeFromHand(me, f.CardId);
            if (Field != null)
            {
                Players[Field.Owner].Graveyard.Add(Field.Card);
                Emit(GameEventType.FieldReplaced, Field.Owner, target: Field.Card.InstanceId, cardId: Field.Card.Def.Id);
            }
            Field = new FieldState { Card = card };
            Emit(GameEventType.FieldPlayed, me.Index, source: card.InstanceId, cardId: card.Def.Id);
        }

        void DoUseTechnique(PlayerState me, UseTechnique cs)
        {
            var card = TakeFromHand(me, cs.CardId);
            Emit(GameEventType.TechniqueUsed, me.Index, source: card.InstanceId, target: cs.TargetId, cardId: card.Def.Id);
            var target = cs.TargetId.HasValue ? FindCreature(cs.TargetId.Value) : null;
            var foe = Opponent(me.Index);

            foreach (var e in card.Def.Effects)
            {
                if (Phase == Phase.GameOver) break;
                switch (e.Kind)
                {
                    case EffectKind.DestroyAllDefenseBelow:
                        foreach (var c in Players.SelectMany(p => p.LivingCreatures).Where(c => EffectiveDefense(c) < e.Amount).ToList())
                            Destroy(c, card.InstanceId);
                        break;
                    case EffectKind.DestroyTargetCreature:
                        if (target != null) Destroy(target, card.InstanceId);
                        break;
                    case EffectKind.FreezeTarget:
                        if (target != null) Freeze(target, card.InstanceId);
                        break;
                    case EffectKind.DamageOpponent:
                        LoseLife(foe, e.Amount, GameEventType.EffectDamage, card.InstanceId);
                        break;
                    case EffectKind.Heal:
                        me.Life += e.Amount;
                        Emit(GameEventType.Healed, me.Index, source: card.InstanceId, amount: e.Amount);
                        break;
                    case EffectKind.BuffAttack:
                        if (target != null)
                        {
                            target.TempAttack += e.Amount;
                            Emit(GameEventType.AttackBuffed, me.Index, source: card.InstanceId, target: target.InstanceId, amount: e.Amount);
                        }
                        break;
                    case EffectKind.DrawCards:
                        for (int i = 0; i < e.Amount && Phase != Phase.GameOver; i++) Draw(me.Index);
                        break;
                    case EffectKind.Revive:
                        Revive(me, card.InstanceId);
                        break;
                }
            }
            me.Graveyard.Add(card);
        }

        void Revive(PlayerState me, int source)
        {
            var dead = me.Graveyard.LastOrDefault(c => c.Def.Type == CardType.Creature);
            int slot = me.FreeCreatureSlot();
            if (dead == null || slot < 0) return;
            me.Graveyard.Remove(dead);
            me.Creatures[slot] = new CreatureState { Card = dead, Slot = slot, Position = Position.Attack, SummonedTurn = TurnNumber };
            Emit(GameEventType.CreatureRevived, me.Index, source: source, target: dead.InstanceId, amount: slot, cardId: dead.Def.Id);
        }

        void DoAttack(PlayerState me, Attack a)
        {
            var attacker = FindCreature(a.AttackerId);
            var target = a.TargetId.HasValue ? FindCreature(a.TargetId.Value) : null;
            var foe = Opponent(me.Index);
            attacker.HasAttacked = true;
            Emit(GameEventType.AttackDeclared, me.Index, source: attacker.InstanceId, target: target?.InstanceId, cardId: attacker.Def.Id);

            // The defender's face-down traps answer the attack, first slot first, one trap per attack.
            for (int i = 0; i < foe.Supports.Length; i++)
            {
                var trap = foe.Supports[i];
                if (trap == null || !TrapApplies(trap.Def, target)) continue;
                foe.Supports[i] = null;
                foe.Graveyard.Add(trap);
                Emit(GameEventType.TrapTriggered, foe.Index, source: trap.InstanceId, target: attacker.InstanceId, amount: i, cardId: trap.Def.Id);

                if (trap.Def.Has(EffectKind.ReflectAttack))
                {
                    Emit(GameEventType.AttackNegated, me.Index, source: trap.InstanceId, target: attacker.InstanceId);
                    LoseLife(me, EffectiveAttack(attacker) / 2, GameEventType.EffectDamage, trap.InstanceId);
                    return;
                }
                if (trap.Def.Has(EffectKind.WhirlpoolTrap))
                {
                    Emit(GameEventType.AttackNegated, me.Index, source: trap.InstanceId, target: attacker.InstanceId);
                    Freeze(attacker, trap.InstanceId);
                    return;
                }
                var fortify = trap.Def.Get(EffectKind.FortifyDefender);
                if (fortify != null && target != null)
                {
                    if (target.Position != Position.Guard)
                    {
                        target.Position = Position.Guard;
                        Emit(GameEventType.PositionChanged, foe.Index, source: trap.InstanceId, target: target.InstanceId, amount: (int)Position.Guard);
                    }
                    target.TempDefense += fortify.Amount;
                    Emit(GameEventType.DefenseBuffed, foe.Index, source: trap.InstanceId, target: target.InstanceId, amount: fortify.Amount);
                }
                break;
            }

            if (target == null)
            {
                LoseLife(foe, EffectiveAttack(attacker), GameEventType.BattleDamage, attacker.InstanceId);
                return;
            }

            int atk = EffectiveAttack(attacker);
            if (target.Position == Position.Attack)
            {
                int other = EffectiveAttack(target);
                if (atk > other)
                {
                    Destroy(target, attacker.InstanceId);
                    LoseLife(foe, atk - other, GameEventType.BattleDamage, attacker.InstanceId);
                    KillBonus(attacker, foe);
                }
                else if (atk < other)
                {
                    Destroy(attacker, target.InstanceId);
                    LoseLife(me, other - atk, GameEventType.BattleDamage, target.InstanceId);
                }
                else
                {
                    Destroy(target, attacker.InstanceId);
                    Destroy(attacker, target.InstanceId);
                }
            }
            else
            {
                int def = EffectiveDefense(target);
                if (atk > def)
                {
                    Destroy(target, attacker.InstanceId);
                    KillBonus(attacker, foe);
                }
                else if (atk < def)
                {
                    LoseLife(me, def - atk, GameEventType.BattleDamage, target.InstanceId);
                }
            }
        }

        static bool TrapApplies(CardDefinition trap, CreatureState target) =>
            trap.Has(EffectKind.ReflectAttack) || trap.Has(EffectKind.WhirlpoolTrap) ||
            (trap.Has(EffectKind.FortifyDefender) && target != null);

        void KillBonus(CreatureState attacker, PlayerState foe)
        {
            var bonus = attacker.Def.Get(EffectKind.BonusDamageOnKill);
            if (bonus != null && Phase != Phase.GameOver)
                LoseLife(foe, bonus.Amount, GameEventType.EffectDamage, attacker.InstanceId);
        }

        void DoChangePosition(ChangePosition cp)
        {
            var c = FindCreature(cp.CreatureId);
            c.Position = cp.To ?? (c.Position == Position.Attack ? Position.Guard : Position.Attack);
            c.ChangedPosition = true;
            Emit(GameEventType.PositionChanged, c.Owner, target: c.InstanceId, amount: (int)c.Position, cardId: c.Def.Id);
        }

        void DoEndTurn(PlayerState me)
        {
            Emit(GameEventType.TurnEnded, me.Index);
            foreach (var c in Players.SelectMany(p => p.LivingCreatures))
            {
                c.TempAttack = 0;
                c.TempDefense = 0;
            }
            StartTurn(1 - me.Index);
        }

        void StartTurn(int player)
        {
            TurnNumber++;
            ActivePlayer = player;
            var me = Players[player];
            foreach (var c in me.LivingCreatures)
            {
                c.HasAttacked = false;
                c.ChangedPosition = false;
            }
            me.MaxEnergy = Math.Min(Config.MaxEnergy, me.MaxEnergy + 1);
            me.Energy = me.MaxEnergy;
            Emit(GameEventType.TurnStarted, player, amount: TurnNumber);
            Emit(GameEventType.EnergyChanged, player, amount: me.Energy);
            if (TurnNumber > 1) Draw(player); // The first player skips the first draw.
        }

        void Draw(int player)
        {
            var me = Players[player];
            if (me.Deck.Count == 0)
            {
                LoseLife(me, Config.FatigueDamage, GameEventType.FatigueDamage, null);
                return;
            }
            var card = me.Deck[me.Deck.Count - 1];
            me.Deck.RemoveAt(me.Deck.Count - 1);
            if (me.Hand.Count >= Config.MaxHand)
            {
                me.Graveyard.Add(card);
                Emit(GameEventType.CardBurned, player, target: card.InstanceId, cardId: card.Def.Id);
                return;
            }
            me.Hand.Add(card);
            Emit(GameEventType.CardDrawn, player, target: card.InstanceId, cardId: card.Def.Id);
        }

        void Destroy(CreatureState c, int? source)
        {
            var owner = Players[c.Owner];
            if (owner.Creatures[c.Slot] != c) return; // Already gone.
            owner.Creatures[c.Slot] = null;
            owner.Graveyard.Add(c.Card);
            Emit(GameEventType.CreatureDestroyed, c.Owner, source: source, target: c.InstanceId, cardId: c.Def.Id);
        }

        void Freeze(CreatureState c, int? source)
        {
            // Frozen through its controller's next turn (or the rest of this one if that turn is now).
            int through = c.Owner == ActivePlayer ? TurnNumber + 2 : TurnNumber + 1;
            c.FrozenThroughTurn = Math.Max(c.FrozenThroughTurn, through);
            Emit(GameEventType.CreatureFrozen, c.Owner, source: source, target: c.InstanceId, amount: c.FrozenThroughTurn);
        }

        void LoseLife(PlayerState p, int amount, GameEventType type, int? source)
        {
            if (amount <= 0 || Phase == Phase.GameOver) return;
            p.Life = Math.Max(0, p.Life - amount);
            Emit(type, p.Index, source: source, amount: amount);
            if (p.Life == 0)
            {
                Phase = Phase.GameOver;
                Winner = 1 - p.Index;
                Emit(GameEventType.GameOver, Winner);
            }
        }

        void Emit(GameEventType type, int player, int? source = null, int? target = null, int amount = 0, string cardId = null) =>
            _sink.Add(new GameEvent { Type = type, Player = player, Source = source, Target = target, Amount = amount, CardId = cardId });

        // ------------------------------------------------------------------ helpers for UI and bots

        /// <summary>
        /// An independent copy of the match, for the computer player to try moves on without touching the real one.
        /// Card instances are shared (they never change); everything that can change is copied.
        /// </summary>
        public Game Clone()
        {
            var g = new Game(Config, 1);
            g._rng.CopyFrom(_rng);
            g._nextInstanceId = _nextInstanceId;
            g.ActivePlayer = ActivePlayer;
            g.TurnNumber = TurnNumber;
            g.Phase = Phase;
            g.Winner = Winner;
            g.Field = Field == null ? null : new FieldState { Card = Field.Card };
            for (int p = 0; p < 2; p++)
            {
                var src = Players[p];
                var dst = new PlayerState
                {
                    Index = src.Index, Name = src.Name, Life = src.Life, Energy = src.Energy, MaxEnergy = src.MaxEnergy,
                    Deck = new List<CardInstance>(src.Deck),
                    Hand = new List<CardInstance>(src.Hand),
                    Graveyard = new List<CardInstance>(src.Graveyard),
                    Supports = (CardInstance[])src.Supports.Clone(),
                };
                for (int i = 0; i < src.Creatures.Length; i++)
                {
                    var c = src.Creatures[i];
                    if (c == null) continue;
                    dst.Creatures[i] = new CreatureState
                    {
                        Card = c.Card, Slot = c.Slot, Position = c.Position, SummonedTurn = c.SummonedTurn,
                        HasAttacked = c.HasAttacked, ChangedPosition = c.ChangedPosition,
                        FrozenThroughTurn = c.FrozenThroughTurn, ProtectedThroughTurn = c.ProtectedThroughTurn,
                        TempAttack = c.TempAttack, TempDefense = c.TempDefense,
                    };
                }
                g.Players[p] = dst;
            }
            return g;
        }

        /// <summary>
        /// Fingerprint of everything that matters in the match. Two phones playing the same match
        /// compare it after every action to catch any difference early.
        /// </summary>
        public ulong StateHash()
        {
            var sb = new System.Text.StringBuilder();
            sb.Append(TurnNumber).Append('|').Append(ActivePlayer).Append('|').Append((int)Phase).Append('|').Append(Winner).Append('|');
            sb.Append(Field?.Card.InstanceId ?? 0).Append('|');
            foreach (var p in Players)
            {
                sb.Append(p.Life).Append(',').Append(p.Energy).Append(',').Append(p.MaxEnergy).Append(';');
                foreach (var c in p.Deck) sb.Append(c.InstanceId).Append(',');
                sb.Append(';');
                foreach (var c in p.Hand) sb.Append(c.InstanceId).Append(',');
                sb.Append(';');
                foreach (var c in p.Graveyard) sb.Append(c.InstanceId).Append(',');
                sb.Append(';');
                foreach (var c in p.Creatures)
                    if (c == null) sb.Append("-,");
                    else sb.Append(c.InstanceId).Append(':').Append((int)c.Position).Append(':').Append(c.HasAttacked ? 1 : 0)
                           .Append(':').Append(c.ChangedPosition ? 1 : 0).Append(':').Append(c.FrozenThroughTurn).Append(':')
                           .Append(c.ProtectedThroughTurn).Append(':').Append(c.TempAttack).Append(':').Append(c.TempDefense).Append(',');
                sb.Append(';');
                foreach (var s in p.Supports) sb.Append(s?.InstanceId ?? 0).Append(',');
                sb.Append('#');
            }
            return Hashing.Fnv1a64(sb.ToString());
        }

        /// <summary>For tests, tutorials and debug menus: puts a new copy of a card in a player's hand.</summary>
        public CardInstance DebugAddToHand(int player, CardDefinition def)
        {
            var card = new CardInstance { InstanceId = _nextInstanceId++, Def = def, Owner = player };
            Players[player].Hand.Add(card);
            return card;
        }

        /// <summary>Every action the player could take right now. Used for UI hints and by the bot.</summary>
        public List<GameAction> LegalActions(int player)
        {
            var list = new List<GameAction>();
            if (Phase == Phase.GameOver || player != ActivePlayer) return list;
            var me = Players[player];
            var foe = Opponent(player);

            foreach (var card in me.Hand)
            {
                switch (card.Def.Type)
                {
                    case CardType.Creature:
                        list.Add(new SummonCreature { Player = player, CardId = card.InstanceId, Position = Position.Attack });
                        list.Add(new SummonCreature { Player = player, CardId = card.InstanceId, Position = Position.Guard });
                        break;
                    case CardType.Trap:
                        list.Add(new SetTrap { Player = player, CardId = card.InstanceId });
                        break;
                    case CardType.Field:
                        list.Add(new PlayField { Player = player, CardId = card.InstanceId });
                        break;
                    case CardType.Technique:
                        if (!TechniqueNeedsTarget(card.Def))
                            list.Add(new UseTechnique { Player = player, CardId = card.InstanceId });
                        else
                            foreach (var t in (TechniqueTargetsEnemy(card.Def) ? foe : me).LivingCreatures)
                                list.Add(new UseTechnique { Player = player, CardId = card.InstanceId, TargetId = t.InstanceId });
                        break;
                }
            }
            foreach (var c in me.LivingCreatures)
            {
                list.Add(new Attack { Player = player, AttackerId = c.InstanceId });
                foreach (var t in foe.LivingCreatures)
                    list.Add(new Attack { Player = player, AttackerId = c.InstanceId, TargetId = t.InstanceId });
                list.Add(new ChangePosition { Player = player, CreatureId = c.InstanceId });
            }
            list.Add(new EndTurn { Player = player });
            return list.Where(a => Validate(a) == ActionError.None).ToList();
        }
    }
}
