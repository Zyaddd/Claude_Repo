using System.Linq;
using StoneRift.Core;
using Xunit;
using static StoneRift.Core.Tests.TestKit;

namespace StoneRift.Core.Tests
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
            var deck = Db.Expand(Db.GetDeck("magma_volt"));
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
            var deck = Db.Expand(Db.GetDeck("crystal_tide"));
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
            var magron = g.DebugAddToHand(0, Card("magron"));
            Assert.Equal(ActionError.NotEnoughEnergy, g.Execute(new SummonCreature { Player = 0, CardId = magron.InstanceId }).Error);
            g.Players[0].Energy = 5;
            Assert.True(g.Execute(new SummonCreature { Player = 0, CardId = magron.InstanceId }).Ok);
            Assert.Equal(0, g.Players[0].Energy);
            Assert.Empty(g.Players[0].Hand);
        }

        [Fact]
        public void OnlyThreeCreatureSlots()
        {
            var g = EmptyMatch();
            for (int i = 0; i < 3; i++) Summon(g, 0, "wahaj");
            var extra = g.DebugAddToHand(0, Card("wahaj"));
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
            for (int i = 0; i < 8; i++) g.DebugAddToHand(1, Card("wahaj"));
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
            var wameed = Summon(g, 0, "wameed"); // Haste, but still turn 1.
            Assert.Equal(ActionError.NoAttacksOnFirstTurn, Attack(g, wameed, null).Error);
        }

        [Fact]
        public void NewCreaturesWaitUnlessTheyHaveHaste()
        {
            var g = EmptyMatch();
            EndTurn(g);
            var wahaj = Summon(g, 1, "wahaj");
            var wameed = Summon(g, 1, "wameed");
            Assert.Equal(ActionError.SummonedThisTurn, Attack(g, wahaj, null).Error);
            Assert.True(Attack(g, wameed, null).Ok);
            Assert.Equal(4000 - 900, g.Players[0].Life);
        }

        [Fact]
        public void StrongerAttackerDestroysAndDealsTheDifference()
        {
            var (g, atk, def) = Setup("voltix", "tayyar"); // 1700 vs 1600
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
            var (g, atk, def) = Setup("wameed", "voltix"); // 900 vs 1700
            Attack(g, atk, def);
            Assert.Null(g.FindCreature(atk.InstanceId));
            Assert.Equal(4000 - 800, g.Players[0].Life);
        }

        [Fact]
        public void EqualAttackDestroysBoth()
        {
            var (g, atk, def) = Setup("wameed", "wameed");
            Attack(g, atk, def);
            Assert.Null(g.FindCreature(atk.InstanceId));
            Assert.Null(g.FindCreature(def.InstanceId));
            Assert.Equal(4000, g.Players[0].Life);
            Assert.Equal(4000, g.Players[1].Life);
        }

        [Fact]
        public void GuardBlocksDamageToItsOwner()
        {
            var (g, atk, def) = Setup("voltix", "quarton", Position.Guard); // 1700 vs 1200 DEF
            Attack(g, atk, def);
            Assert.Null(g.FindCreature(def.InstanceId));
            Assert.Equal(4000, g.Players[1].Life);
        }

        [Fact]
        public void HittingAStrongGuardHurtsTheAttacker()
        {
            var (g, atk, def) = Setup("wameed", "titanox", Position.Guard); // 900 vs 2900 DEF
            Attack(g, atk, def);
            Assert.NotNull(g.FindCreature(atk.InstanceId));
            Assert.Equal(4000 - 2000, g.Players[0].Life);
        }

        [Fact]
        public void MustClearCreaturesBeforeDirectAttack()
        {
            var (g, atk, _) = Setup("wameed", "quarton");
            Assert.Equal(ActionError.MustAttackCreaturesFirst, Attack(g, atk, null).Error);
        }

        [Fact]
        public void GuardPositionCannotAttackAndEachCreatureAttacksOnce()
        {
            var g = EmptyMatch();
            var quarton = Summon(g, 0, "quarton", Position.Guard);
            var wahaj = Summon(g, 0, "wahaj");
            EndTurn(g); EndTurn(g);
            Assert.Equal(ActionError.InGuardPosition, Attack(g, quarton, null).Error);
            Assert.True(Attack(g, wahaj, null).Ok);
            Assert.Equal(ActionError.AlreadyAttacked, Attack(g, wahaj, null).Error);
        }

        [Fact]
        public void PositionChangesOncePerTurnAndNotAfterAttacking()
        {
            var g = EmptyMatch();
            var wahaj = Summon(g, 0, "wahaj");
            Assert.True(g.Execute(new ChangePosition { Player = 0, CreatureId = wahaj.InstanceId }).Ok);
            Assert.Equal(Position.Guard, wahaj.Position);
            Assert.Equal(ActionError.AlreadyChangedPosition, g.Execute(new ChangePosition { Player = 0, CreatureId = wahaj.InstanceId }).Error);
            EndTurn(g); EndTurn(g);
            g.Execute(new ChangePosition { Player = 0, CreatureId = wahaj.InstanceId, To = Position.Attack });
            EndTurn(g); EndTurn(g);
            Attack(g, wahaj, null);
            Assert.Equal(ActionError.AlreadyAttacked, g.Execute(new ChangePosition { Player = 0, CreatureId = wahaj.InstanceId }).Error);
        }

        [Fact]
        public void KillBonusAddsExtraDamage()
        {
            var (g, atk, def) = Setup("magron", "wameed"); // 2400 vs 900, +300
            Attack(g, atk, def);
            Assert.Equal(4000 - 1500 - 300, g.Players[1].Life);
        }

        [Fact]
        public void ProtectedCreatureCannotBeAttackedOnTheNextTurn()
        {
            var g = EmptyMatch();
            var wameed = Summon(g, 0, "wameed");
            EndTurn(g);
            var warden = Summon(g, 1, "mirror_warden");
            EndTurn(g);
            Assert.Equal(ActionError.TargetProtected, Attack(g, wameed, warden).Error);
            // With only protected creatures in the way, a direct attack is allowed.
            Assert.True(Attack(g, wameed, null).Ok);
            EndTurn(g); EndTurn(g);
            Assert.True(Attack(g, wameed, warden).Ok);
        }

        [Fact]
        public void ReducingLifeToZeroEndsTheGame()
        {
            var g = EmptyMatch();
            var magron = Summon(g, 0, "magron");
            EndTurn(g); EndTurn(g);
            g.Players[1].Life = 2000;
            var r = Attack(g, magron, null);
            Assert.True(r.Has(GameEventType.GameOver));
            Assert.Equal(0, g.Winner);
            Assert.Equal(ActionError.GameOver, g.Execute(new EndTurn { Player = 0 }).Error);
        }
    }

    public class CardEffectTests
    {
        [Fact]
        public void CrystalMirrorNegatesAndPunishes()
        {
            var g = EmptyMatch();
            var magron = Summon(g, 0, "magron");
            EndTurn(g);
            SetTrap(g, 1, "crystal_mirror");
            EndTurn(g);
            var r = Attack(g, magron, null);
            Assert.True(r.Has(GameEventType.TrapTriggered));
            Assert.True(r.Has(GameEventType.AttackNegated));
            Assert.Equal(4000, g.Players[1].Life);
            Assert.Equal(4000 - 1200, g.Players[0].Life);
            Assert.All(g.Players[1].Supports, s => Assert.Null(s));
        }

        [Fact]
        public void HexShieldTurnsTheTargetToGuard()
        {
            var g = EmptyMatch();
            var attacker = Summon(g, 0, "wameed");
            EndTurn(g);
            var wameed = Summon(g, 1, "wameed");
            SetTrap(g, 1, "hex_shield");
            EndTurn(g);
            Attack(g, attacker, wameed); // 900 vs 500 + 1000 DEF
            Assert.NotNull(g.FindCreature(wameed.InstanceId));
            Assert.Equal(Position.Guard, wameed.Position);
            Assert.Equal(4000 - 600, g.Players[0].Life);
            EndTurn(g);
            Assert.Equal(500, g.EffectiveDefense(wameed)); // The bonus lasts one turn.
        }

        [Fact]
        public void HexShieldIgnoresDirectAttacks()
        {
            var g = EmptyMatch();
            var magron = Summon(g, 0, "magron");
            EndTurn(g);
            SetTrap(g, 1, "hex_shield");
            EndTurn(g);
            var r = Attack(g, magron, null);
            Assert.False(r.Has(GameEventType.TrapTriggered));
            Assert.NotNull(g.Players[1].Supports[0]);
        }

        [Fact]
        public void IceVortexFreezesTheAttackerThroughItsNextTurn()
        {
            var g = EmptyMatch();
            var magron = Summon(g, 0, "magron");
            EndTurn(g);
            SetTrap(g, 1, "ice_vortex");
            EndTurn(g);
            Attack(g, magron, null);
            Assert.Equal(4000, g.Players[1].Life);
            EndTurn(g); EndTurn(g);
            Assert.Equal(ActionError.Frozen, Attack(g, magron, null).Error);
            EndTurn(g); EndTurn(g);
            Assert.True(Attack(g, magron, null).Ok);
        }

        [Fact]
        public void GravityWaveHitsBothSides()
        {
            var g = EmptyMatch();
            var wahaj = Summon(g, 0, "wahaj");     // DEF 300
            var titanox = Summon(g, 0, "titanox"); // DEF 3000
            EndTurn(g);
            var wameed = Summon(g, 1, "wameed");     // DEF 600
            var wave = g.DebugAddToHand(1, Card("gravity_wave"));
            Assert.True(g.Execute(new UseTechnique { Player = 1, CardId = wave.InstanceId }).Ok);
            Assert.Null(g.FindCreature(wahaj.InstanceId));
            Assert.Null(g.FindCreature(wameed.InstanceId));
            Assert.NotNull(g.FindCreature(titanox.InstanceId));
            Assert.Contains(g.Players[1].Graveyard, c => c.InstanceId == wave.InstanceId);
        }

        [Fact]
        public void TargetedSpellsCheckTheirTarget()
        {
            var g = EmptyMatch();
            var mine = Summon(g, 0, "wahaj");
            EndTurn(g);
            var theirs = Summon(g, 1, "quarton");
            EndTurn(g);
            var beam = g.DebugAddToHand(0, Card("core_beam"));
            Assert.Equal(ActionError.TargetRequired, g.Execute(new UseTechnique { Player = 0, CardId = beam.InstanceId }).Error);
            Assert.Equal(ActionError.InvalidTarget, g.Execute(new UseTechnique { Player = 0, CardId = beam.InstanceId, TargetId = mine.InstanceId }).Error);
            Assert.True(g.Execute(new UseTechnique { Player = 0, CardId = beam.InstanceId, TargetId = theirs.InstanceId }).Ok);
            Assert.Null(g.FindCreature(theirs.InstanceId));
        }

        [Fact]
        public void OverchargeLastsUntilEndOfTurn()
        {
            var g = EmptyMatch();
            var wahaj = Summon(g, 0, "wahaj");
            var charge = g.DebugAddToHand(0, Card("overcharge"));
            g.Execute(new UseTechnique { Player = 0, CardId = charge.InstanceId, TargetId = wahaj.InstanceId });
            Assert.Equal(1400, g.EffectiveAttack(wahaj));
            EndTurn(g);
            Assert.Equal(700, g.EffectiveAttack(wahaj));
        }

        [Fact]
        public void ReforgeRebuildsTheLastDestroyedCreature()
        {
            var g = EmptyMatch();
            var reforge = g.DebugAddToHand(0, Card("reforge"));
            Assert.Equal(ActionError.NothingToRevive, g.Execute(new UseTechnique { Player = 0, CardId = reforge.InstanceId }).Error);
            var thiqal = Summon(g, 0, "thiqal");
            EndTurn(g);
            g.Execute(new UseTechnique { Player = 1, CardId = g.DebugAddToHand(1, Card("core_beam")).InstanceId, TargetId = thiqal.InstanceId });
            EndTurn(g);
            var r = g.Execute(new UseTechnique { Player = 0, CardId = reforge.InstanceId });
            Assert.True(r.Has(GameEventType.CreatureRevived));
            Assert.Contains(g.Players[0].LivingCreatures, c => c.Def.Id == "thiqal");
        }

        [Fact]
        public void TayyarFreezesTheStrongestEnemy()
        {
            var g = EmptyMatch();
            var wahaj = Summon(g, 0, "wahaj");
            var magron = Summon(g, 0, "magron");
            EndTurn(g);
            Summon(g, 1, "tayyar");
            EndTurn(g);
            Assert.Equal(ActionError.Frozen, Attack(g, magron, null).Error);
            Assert.NotEqual(ActionError.Frozen, Attack(g, wahaj, null).Error);
        }

        [Fact]
        public void FieldBoostsItsFactionOnBothSidesAndCanBeReplaced()
        {
            var g = EmptyMatch();
            var wahaj = Summon(g, 0, "wahaj");
            var volcano = g.DebugAddToHand(0, Card("volcano_heart"));
            g.Execute(new PlayField { Player = 0, CardId = volcano.InstanceId });
            EndTurn(g);
            var enemyWahaj = Summon(g, 1, "wahaj");
            Assert.Equal(1000, g.EffectiveAttack(wahaj));
            Assert.Equal(1000, g.EffectiveAttack(enemyWahaj));

            var ocean = g.DebugAddToHand(1, Card("frozen_ocean"));
            var r = g.Execute(new PlayField { Player = 1, CardId = ocean.InstanceId });
            Assert.True(r.Has(GameEventType.FieldReplaced));
            Assert.Equal(700, g.EffectiveAttack(wahaj));
            Assert.Contains(g.Players[0].Graveyard, c => c.InstanceId == volcano.InstanceId);
        }

        [Fact]
        public void LavaBurstCanFinishTheGame()
        {
            var g = EmptyMatch();
            g.Players[1].Life = 500;
            var burst = g.DebugAddToHand(0, Card("lava_burst"));
            var r = g.Execute(new UseTechnique { Player = 0, CardId = burst.InstanceId });
            Assert.True(r.Has(GameEventType.GameOver));
            Assert.Equal(0, g.Winner);
        }
    }
}
