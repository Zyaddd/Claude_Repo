using System.Linq;
using Binders.Core;
using Xunit;
using static Binders.Core.Tests.TestKit;

namespace Binders.Core.Tests
{
    public class DataTests
    {
        [Fact]
        public void AllStarterDecksAreLegal()
        {
            Assert.NotEmpty(Db.Decks);
            foreach (var d in Db.Decks) Assert.Empty(Db.ValidateDeck(d));
        }

        [Fact]
        public void EveryCardIsComplete()
        {
            foreach (var c in Db.Cards)
            {
                Assert.False(string.IsNullOrWhiteSpace(c.NameAr), c.Id);
                Assert.False(string.IsNullOrWhiteSpace(c.TextAr), c.Id);
                Assert.InRange(c.Cost, 0, 10);
                if (c.Type == CardType.Creature) Assert.True(c.Attack > 0 && c.Defense > 0, c.Id);
                else Assert.NotEmpty(c.Effects);
            }
        }

        [Fact]
        public void SpokenNamesDoNotCollideBetweenCreatures()
        {
            var seen = new System.Collections.Generic.Dictionary<string, string>();
            foreach (var c in Db.Cards.Where(c => c.Type == CardType.Creature))
                foreach (var n in c.SpokenNames().Select(VoiceCommandParser.Normalize).Distinct())
                {
                    Assert.False(seen.TryGetValue(n, out var other) && other != c.Id, $"'{n}' is used by {other} and {c.Id}");
                    seen[n] = c.Id;
                }
        }
    }

    public class TurnTests
    {
        [Fact]
        public void MatchStartsWithFiveCardsAndFirstPlayerSkipsDraw()
        {
            var deck = Db.Expand(Db.GetDeck("flame_storm"));
            var g = Game.Create(deck, deck, seed: 7, firstPlayer: 0);
            Assert.Equal(5, g.Players[0].Hand.Count);
            Assert.Equal(5, g.Players[1].Hand.Count);
            Assert.Equal(1, g.Players[0].Energy);
            Assert.Equal(1, g.TurnNumber);

            g.Execute(new EndTurn { Player = 0 });
            Assert.Equal(6, g.Players[1].Hand.Count);
            Assert.Equal(1, g.Players[1].Energy);
            g.Execute(new EndTurn { Player = 1 });
            Assert.Equal(2, g.Players[0].Energy);
        }

        [Fact]
        public void EnergyStopsAtTen()
        {
            var deck = Db.Expand(Db.GetDeck("river_guard"));
            var g = Game.Create(deck, deck, seed: 3, firstPlayer: 0);
            for (int i = 0; i < 30; i++) g.Execute(new EndTurn { Player = g.ActivePlayer });
            Assert.Equal(10, g.Players[0].MaxEnergy);
        }

        [Fact]
        public void CannotActOnOpponentsTurn()
        {
            var g = EmptyMatch();
            Assert.Equal(ActionError.NotYourTurn, g.Execute(new EndTurn { Player = 1 }).Error);
        }

        [Fact]
        public void SummonCostsEnergyAndNeedsEnough()
        {
            var g = EmptyMatch();
            g.Players[0].Energy = 4;
            var dragon = g.DebugAddToHand(0, Card("saeer_dune_dragon"));
            Assert.Equal(ActionError.NotEnoughEnergy, g.Execute(new SummonCreature { Player = 0, CardId = dragon.InstanceId }).Error);
            g.Players[0].Energy = 5;
            Assert.True(g.Execute(new SummonCreature { Player = 0, CardId = dragon.InstanceId }).Ok);
            Assert.Equal(0, g.Players[0].Energy);
            Assert.Empty(g.Players[0].Hand);
        }

        [Fact]
        public void OnlyThreeCreatureSlots()
        {
            var g = EmptyMatch();
            for (int i = 0; i < 3; i++) Summon(g, 0, "flame_wolf");
            var extra = g.DebugAddToHand(0, Card("flame_wolf"));
            Assert.Equal(ActionError.NoFreeSlot, g.Execute(new SummonCreature { Player = 0, CardId = extra.InstanceId }).Error);
        }

        [Fact]
        public void EmptyDeckCostsLifeInsteadOfLosing()
        {
            var g = EmptyMatch();
            g.Players[1].Deck.Clear();
            g.Execute(new EndTurn { Player = 0 });
            Assert.Equal(4000 - 500, g.Players[1].Life);
        }

        [Fact]
        public void FullHandBurnsTheDrawnCard()
        {
            var g = EmptyMatch();
            for (int i = 0; i < 8; i++) g.DebugAddToHand(1, Card("flame_wolf"));
            var r = g.Execute(new EndTurn { Player = 0 });
            Assert.True(r.Has(GameEventType.CardBurned));
            Assert.Equal(8, g.Players[1].Hand.Count);
        }
    }

    public class BattleTests
    {
        /// <summary>Player 0 summons the attacker, player 1 the defender, then it is player 0's turn 3.</summary>
        static (Game g, CreatureState atk, CreatureState def) Setup(string attacker, string defender, Position defPos = Position.Attack)
        {
            var g = EmptyMatch();
            var a = Summon(g, 0, attacker);
            EndTurn(g);
            var d = Summon(g, 1, defender, defPos);
            EndTurn(g);
            return (g, a, d);
        }

        [Fact]
        public void NoAttacksOnTheVeryFirstTurn()
        {
            var g = EmptyMatch();
            var crow = Summon(g, 0, "storm_crow"); // Haste, but still turn 1.
            Assert.Equal(ActionError.NoAttacksOnFirstTurn, Attack(g, crow, null).Error);
        }

        [Fact]
        public void NewCreaturesWaitUnlessTheyHaveHaste()
        {
            var g = EmptyMatch();
            EndTurn(g);
            var wolf = Summon(g, 1, "flame_wolf");
            var crow = Summon(g, 1, "storm_crow");
            Assert.Equal(ActionError.SummonedThisTurn, Attack(g, wolf, null).Error);
            Assert.True(Attack(g, crow, null).Ok);
            Assert.Equal(4000 - 900, g.Players[0].Life);
        }

        [Fact]
        public void StrongerAttackerDestroysAndDealsTheDifference()
        {
            var (g, atk, def) = Setup("thunder_falcon", "deep_crocodile"); // 1700 vs 1600
            // The crocodile froze the falcon when it arrived; wait out the freeze.
            Assert.Equal(ActionError.Frozen, Attack(g, atk, def).Error);
            EndTurn(g); EndTurn(g);
            var r = Attack(g, atk, def);
            Assert.True(r.Ok);
            Assert.Null(g.FindCreature(def.InstanceId));
            Assert.Equal(4000 - 100, g.Players[1].Life);
        }

        [Fact]
        public void WeakerAttackerIsDestroyed()
        {
            var (g, atk, def) = Setup("storm_crow", "thunder_falcon"); // 900 vs 1700
            Attack(g, atk, def);
            Assert.Null(g.FindCreature(atk.InstanceId));
            Assert.Equal(4000 - 800, g.Players[0].Life);
        }

        [Fact]
        public void EqualAttackDestroysBoth()
        {
            var (g, atk, def) = Setup("storm_crow", "storm_crow");
            Attack(g, atk, def);
            Assert.Null(g.FindCreature(atk.InstanceId));
            Assert.Null(g.FindCreature(def.InstanceId));
            Assert.Equal(4000, g.Players[0].Life);
            Assert.Equal(4000, g.Players[1].Life);
        }

        [Fact]
        public void GuardBlocksDamageToItsOwner()
        {
            var (g, atk, def) = Setup("thunder_falcon", "stone_pup", Position.Guard); // 1700 vs 1200 DEF
            Attack(g, atk, def);
            Assert.Null(g.FindCreature(def.InstanceId));
            Assert.Equal(4000, g.Players[1].Life);
        }

        [Fact]
        public void HittingAStrongGuardHurtsTheAttacker()
        {
            var (g, atk, def) = Setup("storm_crow", "granite_golem", Position.Guard); // 900 vs 2900 DEF
            Attack(g, atk, def);
            Assert.NotNull(g.FindCreature(atk.InstanceId));
            Assert.Equal(4000 - 2000, g.Players[0].Life);
        }

        [Fact]
        public void MustClearCreaturesBeforeDirectAttack()
        {
            var (g, atk, _) = Setup("storm_crow", "stone_pup");
            Assert.Equal(ActionError.MustAttackCreaturesFirst, Attack(g, atk, null).Error);
        }

        [Fact]
        public void GuardPositionCannotAttackAndEachCreatureAttacksOnce()
        {
            var g = EmptyMatch();
            var pup = Summon(g, 0, "stone_pup", Position.Guard);
            var wolf = Summon(g, 0, "flame_wolf");
            EndTurn(g); EndTurn(g);
            Assert.Equal(ActionError.InGuardPosition, Attack(g, pup, null).Error);
            Assert.True(Attack(g, wolf, null).Ok);
            Assert.Equal(ActionError.AlreadyAttacked, Attack(g, wolf, null).Error);
        }

        [Fact]
        public void PositionChangesOncePerTurnAndNotAfterAttacking()
        {
            var g = EmptyMatch();
            var wolf = Summon(g, 0, "flame_wolf");
            Assert.True(g.Execute(new ChangePosition { Player = 0, CreatureId = wolf.InstanceId }).Ok);
            Assert.Equal(Position.Guard, wolf.Position);
            Assert.Equal(ActionError.AlreadyChangedPosition, g.Execute(new ChangePosition { Player = 0, CreatureId = wolf.InstanceId }).Error);
            EndTurn(g); EndTurn(g);
            g.Execute(new ChangePosition { Player = 0, CreatureId = wolf.InstanceId, To = Position.Attack });
            EndTurn(g); EndTurn(g);
            Attack(g, wolf, null);
            Assert.Equal(ActionError.AlreadyAttacked, g.Execute(new ChangePosition { Player = 0, CreatureId = wolf.InstanceId }).Error);
        }

        [Fact]
        public void KillBonusAddsExtraDamage()
        {
            var (g, atk, def) = Setup("saeer_dune_dragon", "storm_crow"); // 2400 vs 900, +300
            Attack(g, atk, def);
            Assert.Equal(4000 - 1500 - 300, g.Players[1].Life);
        }

        [Fact]
        public void ProtectedCreatureCannotBeAttackedOnTheNextTurn()
        {
            var g = EmptyMatch();
            var crow = Summon(g, 0, "storm_crow");
            EndTurn(g);
            var sphinx = Summon(g, 1, "sphinx_guardian");
            EndTurn(g);
            Assert.Equal(ActionError.TargetProtected, Attack(g, crow, sphinx).Error);
            // With only protected creatures in the way, a direct attack is allowed.
            Assert.True(Attack(g, crow, null).Ok);
            EndTurn(g); EndTurn(g);
            Assert.True(Attack(g, crow, sphinx).Ok);
        }

        [Fact]
        public void ReducingLifeToZeroEndsTheGame()
        {
            var g = EmptyMatch();
            var dragon = Summon(g, 0, "saeer_dune_dragon");
            EndTurn(g); EndTurn(g);
            g.Players[1].Life = 2000;
            var r = Attack(g, dragon, null);
            Assert.True(r.Has(GameEventType.GameOver));
            Assert.Equal(0, g.Winner);
            Assert.Equal(ActionError.GameOver, g.Execute(new EndTurn { Player = 0 }).Error);
        }
    }

    public class CardEffectTests
    {
        [Fact]
        public void ShadowMirrorNegatesAndPunishes()
        {
            var g = EmptyMatch();
            var dragon = Summon(g, 0, "saeer_dune_dragon");
            EndTurn(g);
            SetTrap(g, 1, "shadow_mirror");
            EndTurn(g);
            var r = Attack(g, dragon, null);
            Assert.True(r.Has(GameEventType.TrapTriggered));
            Assert.True(r.Has(GameEventType.AttackNegated));
            Assert.Equal(4000, g.Players[1].Life);
            Assert.Equal(4000 - 1200, g.Players[0].Life);
            Assert.All(g.Players[1].Supports, s => Assert.Null(s));
        }

        [Fact]
        public void GraniteShieldTurnsTheTargetToGuard()
        {
            var g = EmptyMatch();
            var attacker = Summon(g, 0, "storm_crow");
            EndTurn(g);
            var crow = Summon(g, 1, "storm_crow");
            SetTrap(g, 1, "granite_shield");
            EndTurn(g);
            Attack(g, attacker, crow); // 900 vs 500 + 1000 DEF
            Assert.NotNull(g.FindCreature(crow.InstanceId));
            Assert.Equal(Position.Guard, crow.Position);
            Assert.Equal(4000 - 600, g.Players[0].Life);
            EndTurn(g);
            Assert.Equal(500, g.EffectiveDefense(crow)); // The bonus lasts one turn.
        }

        [Fact]
        public void GraniteShieldIgnoresDirectAttacks()
        {
            var g = EmptyMatch();
            var dragon = Summon(g, 0, "saeer_dune_dragon");
            EndTurn(g);
            SetTrap(g, 1, "granite_shield");
            EndTurn(g);
            var r = Attack(g, dragon, null);
            Assert.False(r.Has(GameEventType.TrapTriggered));
            Assert.NotNull(g.Players[1].Supports[0]);
        }

        [Fact]
        public void WhirlpoolFreezesTheAttackerThroughItsNextTurn()
        {
            var g = EmptyMatch();
            var dragon = Summon(g, 0, "saeer_dune_dragon");
            EndTurn(g);
            SetTrap(g, 1, "whirlpool");
            EndTurn(g);
            Attack(g, dragon, null);
            Assert.Equal(4000, g.Players[1].Life);
            EndTurn(g); EndTurn(g);
            Assert.Equal(ActionError.Frozen, Attack(g, dragon, null).Error);
            EndTurn(g); EndTurn(g);
            Assert.True(Attack(g, dragon, null).Ok);
        }

        [Fact]
        public void ArenaQuakeHitsBothSides()
        {
            var g = EmptyMatch();
            var wolf = Summon(g, 0, "flame_wolf");     // DEF 300
            var golem = Summon(g, 0, "granite_golem"); // DEF 3000
            EndTurn(g);
            var crow = Summon(g, 1, "storm_crow");     // DEF 600
            var quake = g.DebugAddToHand(1, Card("arena_quake"));
            Assert.True(g.Execute(new CastSpell { Player = 1, CardId = quake.InstanceId }).Ok);
            Assert.Null(g.FindCreature(wolf.InstanceId));
            Assert.Null(g.FindCreature(crow.InstanceId));
            Assert.NotNull(g.FindCreature(golem.InstanceId));
            Assert.Contains(g.Players[1].Graveyard, c => c.InstanceId == quake.InstanceId);
        }

        [Fact]
        public void TargetedSpellsCheckTheirTarget()
        {
            var g = EmptyMatch();
            var mine = Summon(g, 0, "flame_wolf");
            EndTurn(g);
            var theirs = Summon(g, 1, "stone_pup");
            EndTurn(g);
            var meteor = g.DebugAddToHand(0, Card("shard_meteor"));
            Assert.Equal(ActionError.TargetRequired, g.Execute(new CastSpell { Player = 0, CardId = meteor.InstanceId }).Error);
            Assert.Equal(ActionError.InvalidTarget, g.Execute(new CastSpell { Player = 0, CardId = meteor.InstanceId, TargetId = mine.InstanceId }).Error);
            Assert.True(g.Execute(new CastSpell { Player = 0, CardId = meteor.InstanceId, TargetId = theirs.InstanceId }).Ok);
            Assert.Null(g.FindCreature(theirs.InstanceId));
        }

        [Fact]
        public void BattleCryLastsUntilEndOfTurn()
        {
            var g = EmptyMatch();
            var wolf = Summon(g, 0, "flame_wolf");
            var cry = g.DebugAddToHand(0, Card("battle_cry"));
            g.Execute(new CastSpell { Player = 0, CardId = cry.InstanceId, TargetId = wolf.InstanceId });
            Assert.Equal(1400, g.EffectiveAttack(wolf));
            EndTurn(g);
            Assert.Equal(700, g.EffectiveAttack(wolf));
        }

        [Fact]
        public void ShadowReturnRevivesTheLastFallenCreature()
        {
            var g = EmptyMatch();
            var ret = g.DebugAddToHand(0, Card("shadow_return"));
            Assert.Equal(ActionError.NothingToRevive, g.Execute(new CastSpell { Player = 0, CardId = ret.InstanceId }).Error);
            var umbra = Summon(g, 0, "umbra_lord");
            EndTurn(g);
            g.Execute(new CastSpell { Player = 1, CardId = g.DebugAddToHand(1, Card("shard_meteor")).InstanceId, TargetId = umbra.InstanceId });
            EndTurn(g);
            var r = g.Execute(new CastSpell { Player = 0, CardId = ret.InstanceId });
            Assert.True(r.Has(GameEventType.CreatureRevived));
            Assert.Contains(g.Players[0].LivingCreatures, c => c.Def.Id == "umbra_lord");
        }

        [Fact]
        public void CrocodileFreezesTheStrongestEnemy()
        {
            var g = EmptyMatch();
            var wolf = Summon(g, 0, "flame_wolf");
            var dragon = Summon(g, 0, "saeer_dune_dragon");
            EndTurn(g);
            Summon(g, 1, "deep_crocodile");
            EndTurn(g);
            Assert.Equal(ActionError.Frozen, Attack(g, dragon, null).Error);
            Assert.NotEqual(ActionError.Frozen, Attack(g, wolf, null).Error);
        }

        [Fact]
        public void FieldBoostsItsFactionOnBothSidesAndCanBeReplaced()
        {
            var g = EmptyMatch();
            var wolf = Summon(g, 0, "flame_wolf");
            var oasis = g.DebugAddToHand(0, Card("sunset_oasis"));
            g.Execute(new PlayField { Player = 0, CardId = oasis.InstanceId });
            EndTurn(g);
            var enemyWolf = Summon(g, 1, "flame_wolf");
            Assert.Equal(1000, g.EffectiveAttack(wolf));
            Assert.Equal(1000, g.EffectiveAttack(enemyWolf));

            var valley = g.DebugAddToHand(1, Card("flooded_valley"));
            var r = g.Execute(new PlayField { Player = 1, CardId = valley.InstanceId });
            Assert.True(r.Has(GameEventType.FieldReplaced));
            Assert.Equal(700, g.EffectiveAttack(wolf));
            Assert.Contains(g.Players[0].Graveyard, c => c.InstanceId == oasis.InstanceId);
        }

        [Fact]
        public void FireCometCanFinishTheGame()
        {
            var g = EmptyMatch();
            g.Players[1].Life = 500;
            var comet = g.DebugAddToHand(0, Card("fire_meteor"));
            var r = g.Execute(new CastSpell { Player = 0, CardId = comet.InstanceId });
            Assert.True(r.Has(GameEventType.GameOver));
            Assert.Equal(0, g.Winner);
        }
    }
}
