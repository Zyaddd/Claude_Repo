using StoneRift.Core;
using Xunit;
using static StoneRift.Core.Tests.TestKit;

namespace StoneRift.Core.Tests
{
    /// <summary>Playing cards from the hand by voice.</summary>
    public class VoicePlayTests
    {
        static (Game g, CardInstance card) WithInHand(string id)
        {
            var g = EmptyMatch();
            return (g, g.DebugAddToHand(0, Card(id)));
        }

        static VoiceCommand Parse(Game g, string heard) => VoiceCommandParser.Parse(g, 0, heard);

        [Theory]
        [InlineData("انزل يا ماجرون")]
        [InlineData("استدعي ماجرون")]
        [InlineData("نزّل تنين الحمم")]
        [InlineData("ماجرون")]
        [InlineData("summon magron")]
        public void SummonByName(string heard)
        {
            var (g, card) = WithInHand("magron");
            var cmd = Parse(g, heard);
            Assert.Equal(VoiceIntent.Summon, cmd.Intent);
            Assert.Same(card, cmd.HandCard);
            Assert.Equal(Position.Attack, cmd.SummonPosition);
            Assert.True(g.Execute(cmd.ToAction(0)).Ok);
            Assert.NotNull(g.FindCreature(card.InstanceId));
        }

        [Theory]
        [InlineData("انزل يا حارس المرايا في وضع الدفاع")]
        [InlineData("حارس المرايا دفاع")]
        public void SummonInGuard(string heard)
        {
            var (g, card) = WithInHand("mirror_warden");
            var cmd = Parse(g, heard);
            Assert.Equal(VoiceIntent.Summon, cmd.Intent);
            Assert.Equal(Position.Guard, cmd.SummonPosition);
            Assert.True(g.Execute(cmd.ToAction(0)).Ok);
            Assert.Equal(Position.Guard, g.FindCreature(card.InstanceId).Position);
        }

        [Fact]
        public void SameNameOnTableAndInHand()
        {
            var g = EmptyMatch();
            var onTable = Summon(g, 0, "magron");
            g.DebugAddToHand(0, Card("magron"));
            EndTurn(g);
            var enemy = Summon(g, 1, "quarton");
            EndTurn(g);

            // A verb about fighting means the one on the table...
            var attack = Parse(g, "ماجرون اهجم على كوارتون");
            Assert.Equal(VoiceIntent.Attack, attack.Intent);
            Assert.Same(onTable, attack.Actor);
            Assert.Same(enemy, attack.Target);

            // ...a summoning verb means the one in hand.
            var summon = Parse(g, "انزل يا ماجرون");
            Assert.Equal(VoiceIntent.Summon, summon.Intent);
        }

        [Theory]
        [InlineData("استخدم شعاع النواة على تيتانوكس")]
        [InlineData("شعاع النواة على تيتانوكس")]
        [InlineData("على تيتانوكس استخدم شعاع النواة")]
        public void TechniqueOnANamedTarget(string heard)
        {
            var g = EmptyMatch();
            EndTurn(g);
            Summon(g, 1, "quarton");
            var titan = Summon(g, 1, "titanox");
            EndTurn(g);
            var beam = g.DebugAddToHand(0, Card("core_beam"));
            var cmd = Parse(g, heard);
            Assert.Equal(VoiceIntent.UseTechnique, cmd.Intent);
            Assert.Same(beam, cmd.HandCard);
            Assert.Same(titan, cmd.Target);
            Assert.True(g.Execute(cmd.ToAction(0)).Ok);
            Assert.Null(g.FindCreature(titan.InstanceId));
        }

        [Fact]
        public void TechniqueWithOnlyOnePossibleTarget()
        {
            var g = EmptyMatch();
            var wahaj = Summon(g, 0, "wahaj");
            g.DebugAddToHand(0, Card("overcharge"));
            var cmd = Parse(g, "استخدم الشحن الزائد");
            Assert.Same(wahaj, cmd.Target); // Overcharge targets your own construct, and there is only one.
            Assert.True(g.Execute(cmd.ToAction(0)).Ok);
        }

        [Fact]
        public void TechniqueAsksForATargetWhenUnclear()
        {
            var g = EmptyMatch();
            EndTurn(g);
            Summon(g, 1, "quarton");
            Summon(g, 1, "titanox");
            EndTurn(g);
            g.DebugAddToHand(0, Card("core_beam"));
            var cmd = Parse(g, "استخدم شعاع النواة");
            Assert.Equal(VoiceIntent.UseTechnique, cmd.Intent);
            Assert.Null(cmd.ToAction(0));
            Assert.NotNull(cmd.Problem);
        }

        [Fact]
        public void TechniqueWithoutTarget()
        {
            var (g, card) = WithInHand("energy_crystal");
            var cmd = Parse(g, "استخدم بلورة طاقة");
            Assert.Equal(VoiceIntent.UseTechnique, cmd.Intent);
            Assert.True(g.Execute(cmd.ToAction(0)).Ok);
        }

        [Theory]
        [InlineData("حط مرآة البلور")]
        [InlineData("جهّز المراية")]
        [InlineData("حط الفخ")]
        public void SetTrap(string heard)
        {
            var (g, card) = WithInHand("crystal_mirror");
            var cmd = Parse(g, heard);
            Assert.Equal(VoiceIntent.SetTrap, cmd.Intent);
            Assert.Same(card, cmd.HandCard);
            Assert.True(g.Execute(cmd.ToAction(0)).Ok);
        }

        [Theory]
        [InlineData("العب قلب البركان")]
        [InlineData("حوّل الحلبة لقلب البركان")]
        public void PlayField(string heard)
        {
            var (g, card) = WithInHand("volcano_heart");
            var cmd = Parse(g, heard);
            Assert.Equal(VoiceIntent.PlayField, cmd.Intent);
            Assert.True(g.Execute(cmd.ToAction(0)).Ok);
            Assert.Same(card, g.Field.Card);
        }

        [Fact]
        public void SummonWithoutANameAsks()
        {
            var (g, _) = WithInHand("magron");
            g.DebugAddToHand(0, Card("wahaj"));
            var cmd = Parse(g, "انزل");
            Assert.Null(cmd.ToAction(0));
            Assert.NotNull(cmd.Problem);
        }

        [Fact]
        public void CardsNotInHandAreIgnored()
        {
            var g = EmptyMatch();
            var cmd = Parse(g, "انزل يا ماجرون");
            Assert.Null(cmd.ToAction(0));
        }
    }
}
