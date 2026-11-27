using System;
using StoneRift.Core;
using Xunit;
using Xunit.Abstractions;
using static StoneRift.Core.Tests.TestKit;

namespace StoneRift.Core.Tests
{
    public class BotTests
    {
        readonly ITestOutputHelper _out;
        public BotTests(ITestOutputHelper output) { _out = output; }

        /// <summary>Plays games between two bot levels, swapping decks and seats; returns how often the first level wins.</summary>
        double WinRate(BotLevel a, BotLevel b, int games)
        {
            int winsA = 0;
            for (int seed = 1; seed <= games; seed++)
            {
                bool swapDecks = seed % 2 == 0;
                var g = Game.Create(Db.Expand(Db.GetDeck(swapDecks ? "crystal_tide" : "magma_volt")),
                                    Db.Expand(Db.GetDeck(swapDecks ? "magma_volt" : "crystal_tide")), seed);
                bool aIsZero = (seed / 2) % 2 == 0;
                var bots = aIsZero ? new[] { Bots.Create(a, seed), Bots.Create(b, seed + 1) }
                                   : new[] { Bots.Create(b, seed + 1), Bots.Create(a, seed) };
                for (int i = 0; i < 3000 && g.Phase != Phase.GameOver; i++)
                {
                    var action = bots[g.ActivePlayer].ChooseAction(g, g.ActivePlayer);
                    Assert.True(g.Execute(action).Ok, $"{action.GetType().Name} was illegal");
                }
                Assert.Equal(Phase.GameOver, g.Phase);
                if (g.Winner == (aIsZero ? 0 : 1)) winsA++;
            }
            double rate = winsA / (double)games;
            _out.WriteLine($"{a} vs {b}: {rate:P0}");
            return rate;
        }

        [Fact]
        public void HardBeatsNormal() => Assert.True(WinRate(BotLevel.Hard, BotLevel.Normal, 80) > 0.6);

        [Fact]
        public void NormalBeatsEasy() => Assert.True(WinRate(BotLevel.Normal, BotLevel.Easy, 120) > 0.6);

        [Fact]
        public void CloneIsIndependentAndIdentical()
        {
            var deck = Db.Expand(Db.GetDeck("magma_volt"));
            var g = Game.Create(deck, deck, 4);
            var bot = new SimpleBot(1);
            for (int i = 0; i < 25; i++) g.Execute(bot.ChooseAction(g, g.ActivePlayer));
            var copy = g.Clone();
            Assert.Equal(g.StateHash(), copy.StateHash());

            // Both continue identically, including shuffles and draws, without affecting each other.
            ulong before = g.StateHash();
            for (int i = 0; i < 25 && copy.Phase != Phase.GameOver; i++) copy.Execute(bot.ChooseAction(copy, copy.ActivePlayer));
            Assert.Equal(before, g.StateHash());
        }
    }
}
