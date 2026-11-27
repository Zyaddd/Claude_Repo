using StoneRift.Core;
using Xunit;
using static StoneRift.Core.Tests.TestKit;

namespace StoneRift.Core.Tests
{
    public class VoiceTests
    {
        /// <summary>Player 0: Magron + Wameed. Player 1: Mirror Warden + Quarton. Player 0's turn, all can act.</summary>
        static (Game g, CreatureState magron, CreatureState wameed, CreatureState warden, CreatureState quarton) Board()
        {
            var g = EmptyMatch();
            var magron = Summon(g, 0, "magron");
            var wameed = Summon(g, 0, "wameed");
            EndTurn(g);
            var warden = Summon(g, 1, "mirror_warden");
            var quarton = Summon(g, 1, "quarton");
            EndTurn(g); EndTurn(g); EndTurn(g); // The warden's protection is over.
            return (g, magron, wameed, warden, quarton);
        }

        [Theory]
        [InlineData("يا ماجرون اهجم على حارس المرايا")]
        [InlineData("يا ماجِرون، اهجم على حارس المرايا!")]
        [InlineData("ماجرون هجوم على الحارس")]
        [InlineData("اهجم يا ماجرون على حارس المرايا")]
        [InlineData("يا تنين الحمم اضرب الحارس")]
        [InlineData("Magron attack the warden")]
        [InlineData("يا ماجران اهجم على حارس المراية")] // Small recognition slips.
        public void AttackCommands(string heard)
        {
            var (g, magron, _, warden, _) = Board();
            var cmd = VoiceCommandParser.Parse(g, 0, heard);
            Assert.Equal(VoiceIntent.Attack, cmd.Intent);
            Assert.Same(magron, cmd.Actor);
            Assert.Same(warden, cmd.Target);
            Assert.True(g.Execute(cmd.ToAction(0)).Ok);
        }

        [Fact]
        public void AttackingTheOtherCreature()
        {
            var (g, _, wameed, _, quarton) = Board();
            var cmd = VoiceCommandParser.Parse(g, 0, "يا وميض اهجم على كوارتون");
            Assert.Same(wameed, cmd.Actor);
            Assert.Same(quarton, cmd.Target);
        }

        [Theory]
        [InlineData("يا ماجرون تحول إلى وضعية الدفاع", VoiceIntent.ToGuardPosition)]
        [InlineData("ماجرون دفاع", VoiceIntent.ToGuardPosition)]
        [InlineData("ماجرون دافع", VoiceIntent.ToGuardPosition)]
        public void GuardCommands(string heard, VoiceIntent intent)
        {
            var (g, magron, _, _, _) = Board();
            var cmd = VoiceCommandParser.Parse(g, 0, heard);
            Assert.Equal(intent, cmd.Intent);
            Assert.Same(magron, cmd.Actor);
            Assert.True(g.Execute(cmd.ToAction(0)).Ok);
            Assert.Equal(Position.Guard, magron.Position);
        }

        [Fact]
        public void BackToAttackPosition()
        {
            var (g, magron, _, _, _) = Board();
            magron.Position = Position.Guard;
            var cmd = VoiceCommandParser.Parse(g, 0, "يا ماجرون تحول لوضع الهجوم");
            Assert.Equal(VoiceIntent.ToAttackPosition, cmd.Intent);
            Assert.True(g.Execute(cmd.ToAction(0)).Ok);
            Assert.Equal(Position.Attack, magron.Position);
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
            var cmd = VoiceCommandParser.Parse(g, 0, "ماجرون اهجم");
            Assert.Equal(VoiceIntent.Attack, cmd.Intent);
            Assert.Null(cmd.ToAction(0));
            Assert.NotNull(cmd.Problem);
        }

        [Fact]
        public void DirectAttackWhenTheTableIsEmpty()
        {
            var g = EmptyMatch();
            var magron = Summon(g, 0, "magron");
            EndTurn(g); EndTurn(g);
            var cmd = VoiceCommandParser.Parse(g, 0, "اهجم");
            Assert.Same(magron, cmd.Actor); // The only creature that can attack.
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
            Assert.Equal("حارس المرايا", VoiceCommandParser.Normalize("حَارِسُ المَرَايَا"));
            Assert.Equal("اهجم", VoiceCommandParser.Normalize("إهجم"));
            Assert.Equal("مرايه", VoiceCommandParser.Normalize("مراية"));
        }
    }
}
