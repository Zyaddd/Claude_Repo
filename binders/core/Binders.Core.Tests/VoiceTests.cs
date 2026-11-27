using Binders.Core;
using Xunit;
using static Binders.Core.Tests.TestKit;

namespace Binders.Core.Tests
{
    public class VoiceTests
    {
        /// <summary>Player 0: Saeer + Storm Crow. Player 1: Sphinx + Stone Pup. Player 0's turn, all can act.</summary>
        static (Game g, CreatureState saeer, CreatureState crow, CreatureState sphinx, CreatureState pup) Board()
        {
            var g = EmptyMatch();
            var saeer = Summon(g, 0, "saeer_dune_dragon");
            var crow = Summon(g, 0, "storm_crow");
            EndTurn(g);
            var sphinx = Summon(g, 1, "sphinx_guardian");
            var pup = Summon(g, 1, "stone_pup");
            EndTurn(g); EndTurn(g); EndTurn(g); // Sphinx protection is over.
            return (g, saeer, crow, sphinx, pup);
        }

        [Theory]
        [InlineData("يا سعير اهجم على أبو الهول")]
        [InlineData("يا سَعِير، اهجم على أبو الهول!")]
        [InlineData("سعير هجوم على ابو الهول")]
        [InlineData("اهجم يا سعير على أبو الهول")]
        [InlineData("يا تنين الكثبان اضرب الحارس")]
        [InlineData("Saeer attack the sphinx")]
        [InlineData("يا سعيد اهجم على ابو الهول")] // Recogniser misheard "سعير".
        public void AttackCommands(string heard)
        {
            var (g, saeer, _, sphinx, _) = Board();
            var cmd = VoiceCommandParser.Parse(g, 0, heard);
            Assert.Equal(VoiceIntent.Attack, cmd.Intent);
            Assert.Same(saeer, cmd.Actor);
            Assert.Same(sphinx, cmd.Target);
            Assert.True(g.Execute(cmd.ToAction(0)).Ok);
        }

        [Fact]
        public void AttackingTheOtherCreature()
        {
            var (g, _, crow, _, pup) = Board();
            var cmd = VoiceCommandParser.Parse(g, 0, "يا غراب العاصفة اهجم على جرو الصخر");
            Assert.Same(crow, cmd.Actor);
            Assert.Same(pup, cmd.Target);
        }

        [Theory]
        [InlineData("يا سعير تحول إلى وضعية الدفاع", VoiceIntent.ToGuardPosition)]
        [InlineData("سعير دفاع", VoiceIntent.ToGuardPosition)]
        [InlineData("سعير دافع", VoiceIntent.ToGuardPosition)]
        public void GuardCommands(string heard, VoiceIntent intent)
        {
            var (g, saeer, _, _, _) = Board();
            var cmd = VoiceCommandParser.Parse(g, 0, heard);
            Assert.Equal(intent, cmd.Intent);
            Assert.Same(saeer, cmd.Actor);
            Assert.True(g.Execute(cmd.ToAction(0)).Ok);
            Assert.Equal(Position.Guard, saeer.Position);
        }

        [Fact]
        public void BackToAttackPosition()
        {
            var (g, saeer, _, _, _) = Board();
            saeer.Position = Position.Guard;
            var cmd = VoiceCommandParser.Parse(g, 0, "يا سعير تحول لوضع الهجوم");
            Assert.Equal(VoiceIntent.ToAttackPosition, cmd.Intent);
            Assert.True(g.Execute(cmd.ToAction(0)).Ok);
            Assert.Equal(Position.Attack, saeer.Position);
        }

        [Theory]
        [InlineData("انهي الدور")]
        [InlineData("خلصت دوري")]
        [InlineData("end turn")]
        public void EndTurnCommands(string heard)
        {
            var (g, _, _, _, _) = Board();
            Assert.Equal(VoiceIntent.EndTurn, VoiceCommandParser.Parse(g, 0, heard).Intent);
        }

        [Fact]
        public void MissingTargetAsksWhichOne()
        {
            var (g, _, _, _, _) = Board();
            var cmd = VoiceCommandParser.Parse(g, 0, "سعير اهجم");
            Assert.Equal(VoiceIntent.Attack, cmd.Intent);
            Assert.Null(cmd.ToAction(0));
            Assert.NotNull(cmd.Problem);
        }

        [Fact]
        public void DirectAttackWhenTheTableIsEmpty()
        {
            var g = EmptyMatch();
            var saeer = Summon(g, 0, "saeer_dune_dragon");
            EndTurn(g); EndTurn(g);
            var cmd = VoiceCommandParser.Parse(g, 0, "اهجم");
            Assert.Same(saeer, cmd.Actor); // The only creature that can attack.
            Assert.True(cmd.Direct);
            Assert.True(g.Execute(cmd.ToAction(0)).Ok);
            Assert.Equal(4000 - 2400, g.Players[1].Life);
        }

        [Fact]
        public void GibberishIsUnknown()
        {
            var (g, _, _, _, _) = Board();
            var cmd = VoiceCommandParser.Parse(g, 0, "عامل ايه النهارده");
            Assert.Equal(VoiceIntent.Unknown, cmd.Intent);
            Assert.Null(cmd.ToAction(0));
        }

        [Fact]
        public void NormalizeUnifiesArabicSpelling()
        {
            Assert.Equal("ابو الهول", VoiceCommandParser.Normalize("أبُو الهَوْل"));
            Assert.Equal("مرايه", VoiceCommandParser.Normalize("مراية"));
        }
    }
}
