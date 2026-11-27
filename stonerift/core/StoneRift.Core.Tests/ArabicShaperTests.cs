using StoneRift.Core;
using Xunit;

namespace StoneRift.Core.Tests
{
    public class ArabicShaperTests
    {
        [Fact]
        public void LettersTakeTheirJoinedForms()
        {
            // "كبير" = kaf(initial) baa(medial) yaa(medial) raa(final).
            Assert.Equal("ﻛﺒﻴﺮ", ArabicShaper.Shape("كبير"));
        }

        [Fact]
        public void NonJoiningLettersBreakTheWord()
        {
            // "دار": every letter isolated, because dal and alef never join to the letter after them.
            Assert.Equal("ﺩﺍﺭ", ArabicShaper.Shape("دار"));
        }

        [Fact]
        public void LamAlefBecomesALigature()
        {
            Assert.Equal("ﻻ", ArabicShaper.Shape("لا"));
            // "سلام": seen(initial) lam-alef(final) meem(isolated).
            Assert.Equal("ﺳﻼﻡ", ArabicShaper.Shape("سلام"));
        }

        [Fact]
        public void DiacriticsAreDropped()
        {
            Assert.Equal(ArabicShaper.Shape("كبير"), ArabicShaper.Shape("كَبِير"));
        }

        [Fact]
        public void VisualOrderKeepsNumbersReadable()
        {
            string fixedText = ArabicShaper.Fix("هجوم 2400");
            Assert.StartsWith("2400 ", fixedText);
        }

        [Fact]
        public void LatinTextIsUntouched()
        {
            Assert.Equal("Storm Crow 900/500", ArabicShaper.Fix("Storm Crow 900/500"));
        }

        [Fact]
        public void EveryCardNameShapesWithoutLeavingBaseLetters()
        {
            foreach (var c in TestKit.Db.Cards)
                foreach (var ch in ArabicShaper.Shape(c.NameAr))
                    Assert.False(ch >= 'ء' && ch <= 'ي', $"{c.Id}: unshaped letter U+{(int)ch:X4}");
        }
    }
}
