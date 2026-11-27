using System;
using System.Linq;
using StoneRift.Core;
using Xunit;
using static StoneRift.Core.Tests.TestKit;

namespace StoneRift.Core.Tests
{
    public class DeckBuilderTests
    {
        [Fact]
        public void FollowsTheRules()
        {
            var b = new DeckBuilder(Db);
            Assert.True(b.Add("magron"));
            Assert.True(b.Add("magron"));
            Assert.True(b.Add("magron"));
            Assert.False(b.Add("magron"));
            Assert.NotNull(b.CannotAddReason("magron"));
            Assert.Equal(3, b.CountOf("magron"));
            Assert.True(b.Remove("magron"));
            Assert.Equal(2, b.CountOf("magron"));
            Assert.False(b.Remove("wahaj"));
            Assert.False(b.IsComplete);
            Assert.Throws<System.Collections.Generic.KeyNotFoundException>(() => b.Add("no_such_card"));
        }

        [Fact]
        public void StopsAtThirtyCards()
        {
            var b = new DeckBuilder(Db);
            foreach (var c in Db.Cards) for (int i = 0; i < 3; i++) b.Add(c.Id);
            Assert.Equal(30, b.Count);
            Assert.True(b.IsComplete);
            Assert.NotNull(b.CannotAddReason("wahaj"));
        }

        [Fact]
        public void StatsDescribeTheDeck()
        {
            var b = new DeckBuilder(Db, Db.GetDeck("magma_volt"));
            var s = b.Stats();
            Assert.Equal(30, s.CostCurve.Sum());
            Assert.Equal(30, s.Types.Values.Sum());
            Assert.True(s.Factions[Faction.Magma] > 0);
            Assert.InRange(s.AverageCost, 1.0, 6.0);
        }

        [Theory]
        [InlineData("magron")]
        [InlineData("madar")]   // Gravity has few cards: auto-fill must still reach 30.
        [InlineData("wameed")]
        [InlineData(null)]      // Empty deck.
        public void AutoFillMakesALegalDeckThatPlays(string start)
        {
            var b = new DeckBuilder(Db);
            if (start != null) b.Add(start);
            b.AutoFill(seed: 5);
            Assert.True(b.IsComplete, string.Join("; ", b.Problems));
            if (start != null) Assert.True(b.CountOf(start) >= 1);

            var deck = Db.Expand(b.ToDeckList());
            var g = Game.Create(deck, Db.Expand(Db.GetDeck("crystal_tide")), 3);
            var bot = new SimpleBot(1);
            for (int i = 0; i < 3000 && g.Phase != Phase.GameOver; i++) Assert.True(g.Execute(bot.ChooseAction(g, g.ActivePlayer)).Ok);
            Assert.Equal(Phase.GameOver, g.Phase);
        }

        [Fact]
        public void SavedDecksComeBack()
        {
            var b = new DeckBuilder(Db) { Name = "ديكي" };
            b.AutoFill(9);
            var json = DeckBuilder.Serialize(new[] { b.ToDeckList() });
            var back = Assert.Single(DeckBuilder.Deserialize(Db, json));
            Assert.Equal("ديكي", back.NameAr);
            Assert.Equal(b.ToDeckList().Cards.OrderBy(k => k.Key), back.Cards.OrderBy(k => k.Key));
        }

        [Fact]
        public void BrokenSavesAreIgnored()
        {
            Assert.Empty(DeckBuilder.Deserialize(Db, "garbage"));
            Assert.Empty(DeckBuilder.Deserialize(Db, "[{\"Id\":\"x\",\"NameAr\":\"y\",\"Cards\":{\"removed_card\":30}}]"));
        }
    }
}
