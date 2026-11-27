using System.Collections.Generic;
using System.Text;

namespace StoneRift.Core
{
    /// <summary>
    /// Unity's simple text renderers draw Arabic letters unjoined and left-to-right.
    /// This turns logical Arabic text into joined presentation forms in visual (left-to-right) order,
    /// so "حارس المرايا" shows correctly. Latin words and numbers inside stay readable.
    /// </summary>
    public static class ArabicShaper
    {
        // Letters in Unicode order with how many presentation forms each has (1, 2 or 4),
        // laid out consecutively from U+FE80: isolated, final, [initial, medial].
        static readonly (char letter, int forms)[] Table =
        {
            ('ء', 1), ('آ', 2), ('أ', 2), ('ؤ', 2), ('إ', 2), ('ئ', 4),
            ('ا', 2), ('ب', 4), ('ة', 2), ('ت', 4), ('ث', 4), ('ج', 4),
            ('ح', 4), ('خ', 4), ('د', 2), ('ذ', 2), ('ر', 2), ('ز', 2),
            ('س', 4), ('ش', 4), ('ص', 4), ('ض', 4), ('ط', 4), ('ظ', 4),
            ('ع', 4), ('غ', 4), ('ف', 4), ('ق', 4), ('ك', 4), ('ل', 4),
            ('م', 4), ('ن', 4), ('ه', 4), ('و', 2), ('ى', 2), ('ي', 4),
        };

        static readonly Dictionary<char, (int start, int forms)> Forms = Build();

        static Dictionary<char, (int, int)> Build()
        {
            var d = new Dictionary<char, (int, int)>();
            int code = 0xFE80;
            foreach (var (letter, forms) in Table)
            {
                d[letter] = (code, forms);
                code += forms;
            }
            return d;
        }

        const char Lam = 'ل';
        const char Tatweel = 'ـ';

        static bool IsLetter(char c) => Forms.ContainsKey(c) || c == Tatweel;
        static bool JoinsForward(char c) => c == Tatweel || Forms.TryGetValue(c, out var f) && f.forms == 4;
        static bool IsDiacritic(char c) => c >= 'ً' && c <= 'ْ' || c == 'ٰ';

        static int LamAlef(char alef)
        {
            switch (alef)
            {
                case 'آ': return 0xFEF5;
                case 'أ': return 0xFEF7;
                case 'إ': return 0xFEF9;
                case 'ا': return 0xFEFB;
                default: return 0;
            }
        }

        public static bool HasArabic(string s)
        {
            if (s == null) return false;
            foreach (var c in s) if (c >= '؀' && c <= 'ۿ') return true;
            return false;
        }

        /// <summary>Shapes and reorders one line of text for a left-to-right renderer.</summary>
        public static string Fix(string text)
        {
            if (!HasArabic(text)) return text;
            var lines = text.Split('\n');
            for (int i = 0; i < lines.Length; i++) lines[i] = Visual(Shape(lines[i]));
            return string.Join("\n", lines);
        }

        /// <summary>Replaces letters with their joined forms (still in logical order).</summary>
        public static string Shape(string text)
        {
            var chars = new List<char>();
            foreach (var c in text) if (!IsDiacritic(c)) chars.Add(c);

            var sb = new StringBuilder(chars.Count);
            for (int i = 0; i < chars.Count; i++)
            {
                char c = chars[i];
                if (!Forms.TryGetValue(c, out var f)) { sb.Append(c); continue; }

                bool prevJoins = i > 0 && JoinsForward(chars[i - 1]);
                char next = i + 1 < chars.Count ? chars[i + 1] : '\0';

                if (c == Lam && LamAlef(next) != 0)
                {
                    sb.Append((char)(LamAlef(next) + (prevJoins ? 1 : 0)));
                    i++;
                    continue;
                }

                bool nextJoins = f.forms == 4 && IsLetter(next) && next != 'ء';
                int offset;
                if (f.forms == 1) offset = 0;
                else if (f.forms == 2) offset = prevJoins ? 1 : 0;
                else offset = prevJoins ? (nextJoins ? 3 : 1) : (nextJoins ? 2 : 0);
                sb.Append((char)(f.start + offset));
            }
            return sb.ToString();
        }

        static bool IsLtr(char c) => c < 0x80 && char.IsLetterOrDigit(c) || c >= '0' && c <= '9';

        /// <summary>Reverses the line for left-to-right display while keeping Latin words and numbers intact.</summary>
        static string Visual(string logical)
        {
            var reversed = new char[logical.Length];
            for (int i = 0; i < logical.Length; i++) reversed[i] = Mirror(logical[logical.Length - 1 - i]);

            // Put runs of Latin letters and digits (with dots/commas/slashes between them) back in order.
            int start = -1;
            for (int i = 0; i <= reversed.Length; i++)
            {
                bool inRun = i < reversed.Length && (IsLtr(reversed[i]) ||
                    start >= 0 && i + 1 < reversed.Length && IsLtr(reversed[i + 1]) && ".,:/-+%".IndexOf(reversed[i]) >= 0);
                if (inRun && start < 0) start = i;
                if (!inRun && start >= 0)
                {
                    System.Array.Reverse(reversed, start, i - start);
                    start = -1;
                }
            }
            return new string(reversed);
        }

        static char Mirror(char c)
        {
            switch (c)
            {
                case '(': return ')';
                case ')': return '(';
                case '[': return ']';
                case ']': return '[';
                case '«': return '»';
                case '»': return '«';
                case '<': return '>';
                case '>': return '<';
                default: return c;
            }
        }
    }
}
