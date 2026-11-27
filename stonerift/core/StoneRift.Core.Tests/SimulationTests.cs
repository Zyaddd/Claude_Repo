using System.Linq;
using StoneRift.Core;
using Xunit;
using Xunit.Abstractions;
using static StoneRift.Core.Tests.TestKit;

namespace StoneRift.Core.Tests
{
    /// <summary>Bots play hundreds of full matches to catch crashes, endless games and broken balance.</summary>
    public class SimulationTests
    {
        readonly ITestOutputHelper _out;
        public SimulationTests(ITestOutputHelper output) { _out = output; }

        static Game PlayOut(string deckA, string deckB, int seed, out int actions)
        {
            var g = Game.Create(Db.Expand(Db.GetDeck(deckA)), Db.Expand(Db.GetDeck(deckB)), seed);
            var bots = new[] { new SimpleBot(seed * 31 + 1), new SimpleBot(seed * 17 + 5) };
            actions = 0;
            while (g.Phase != Phase.GameOver && actions < 5000)
            {
                var a = bots[g.ActivePlayer].ChooseAction(g, g.ActivePlayer);
                var r = g.Execute(a);
                Assert.True(r.Ok, $"Bot chose an illegal action: {a.GetType().Name} → {r.Error}");
                actions++;
            }
            return g;
        }

        [Fact]
        public void BotMatchesAlwaysFinish()
        {
            int[] wins = new int[2];
            int totalTurns = 0, firstPlayerWins = 0;
            const int games = 400;
            for (int seed = 1; seed <= games; seed++)
            {
                bool swap = seed % 2 == 0;
                var g = PlayOut(swap ? "crystal_tide" : "magma_volt", swap ? "magma_volt" : "crystal_tide", seed, out int actions);
                Assert.Equal(Phase.GameOver, g.Phase);
                Assert.InRange(g.Winner, 0, 1);
                Assert.True(g.TurnNumber < 120, $"Seed {seed} took {g.TurnNumber} turns");
                // Index 0 = magma_volt, 1 = crystal_tide.
                wins[swap ? 1 - g.Winner : g.Winner]++;
                totalTurns += g.TurnNumber;
                if (g.Winner == g.Log.First(e => e.Type == GameEventType.GameStarted).Player) firstPlayerWins++;

                foreach (var p in g.Players)
                {
                    int cards = p.Deck.Count + p.Hand.Count + p.Graveyard.Count
                              + p.LivingCreatures.Count() + p.Supports.Count(s => s != null)
                              + (g.Field != null && g.Field.Owner == p.Index ? 1 : 0);
                    Assert.Equal(30, cards); // No card is lost or duplicated.
                }
            }
            double magmaRate = wins[0] / (double)games;
            _out.WriteLine($"magma_volt wins {wins[0]}, crystal_tide wins {wins[1]}, average {totalTurns / games} turns, first player wins {firstPlayerWins}");
            // Starter decks should be roughly fair against each other.
            Assert.InRange(magmaRate, 0.35, 0.65);
        }

        [Fact]
        public void SameSeedReplaysTheSameMatch()
        {
            var a = PlayOut("magma_volt", "crystal_tide", 42, out _);
            var b = PlayOut("magma_volt", "crystal_tide", 42, out _);
            Assert.Equal(a.Log.Select(e => e.ToString()), b.Log.Select(e => e.ToString()));
        }
    }
}
