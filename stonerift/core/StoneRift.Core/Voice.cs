using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace StoneRift.Core
{
    public enum VoiceIntent { Unknown, Attack, ToAttackPosition, ToGuardPosition, EndTurn, Summon, UseTechnique, SetTrap, PlayField }

    public sealed class VoiceCommand
    {
        public VoiceIntent Intent;
        public CreatureState Actor;
        public CreatureState Target;
        public bool Direct;
        /// <summary>The card from the hand to play (summon, technique, trap or field).</summary>
        public CardInstance HandCard;
        public Position SummonPosition = Position.Attack;
        /// <summary>Why the command could not be turned into an action, for the on-screen hint.</summary>
        public string Problem;

        /// <summary>The engine action, or null if the command is incomplete.</summary>
        public GameAction ToAction(int player)
        {
            switch (Intent)
            {
                case VoiceIntent.EndTurn:
                    return new EndTurn { Player = player };
                case VoiceIntent.Attack when Actor != null && (Target != null || Direct):
                    return new Attack { Player = player, AttackerId = Actor.InstanceId, TargetId = Target?.InstanceId };
                case VoiceIntent.ToAttackPosition when Actor != null:
                    return new ChangePosition { Player = player, CreatureId = Actor.InstanceId, To = Position.Attack };
                case VoiceIntent.ToGuardPosition when Actor != null:
                    return new ChangePosition { Player = player, CreatureId = Actor.InstanceId, To = Position.Guard };
                case VoiceIntent.Summon when HandCard != null:
                    return new SummonCreature { Player = player, CardId = HandCard.InstanceId, Position = SummonPosition };
                case VoiceIntent.UseTechnique when HandCard != null && (Target != null || !Game.TechniqueNeedsTarget(HandCard.Def)):
                    return new UseTechnique { Player = player, CardId = HandCard.InstanceId, TargetId = Target?.InstanceId };
                case VoiceIntent.SetTrap when HandCard != null:
                    return new SetTrap { Player = player, CardId = HandCard.InstanceId };
                case VoiceIntent.PlayField when HandCard != null:
                    return new PlayField { Player = player, CardId = HandCard.InstanceId };
                default:
                    return null;
            }
        }
    }

    /// <summary>
    /// Turns what the speech recogniser heard ("يا ماجرون اهجم على حارس المرايا") into a game action.
    /// Works on Egyptian/Modern Standard Arabic and English, and tolerates small recognition mistakes
    /// because it only has to choose among the few creatures on the table.
    /// </summary>
    public static class VoiceCommandParser
    {
        static readonly string[] AttackWords = { "هجوم", "اهجم", "هاجم", "هجم", "اضرب", "اضربه", "ضرب", "اكتسح", "دمر", "attack", "hit", "strike" };
        static readonly string[] GuardWords = { "دفاع", "دافع", "احمي", "احم", "اتحامي", "defend", "defense", "guard" };
        static readonly string[] PositionWords = { "وضع", "وضعيه", "حاله", "تحول", "اتحول", "حول", "تحويل", "mode", "position", "switch" };
        static readonly string[] DirectWords = { "مباشر", "مباشره", "لاعب", "خصم", "عدو", "direct", "player", "opponent" };
        static readonly string[] PlayWords = { "انزل", "نزل", "نزلي", "استدعي", "استدعاء", "اطلع", "طلع", "اظهر", "العب", "حط", "استخدم", "شغل", "فعل", "جهز", "summon", "play", "use", "call", "set" };
        static readonly string[] TrapWords = { "فخ", "trap" };
        static readonly string[] Fillers = { "يا", "ايها", "على", "علي", "عل", "الى", "الي", "لـ", "the", "on", "to", "at", "and", "و", "قم", "ب", "يلا", "go" };

        /// <summary>Lower-case, no diacritics, unified alef/yaa/taa marbuta, punctuation removed.</summary>
        public static string Normalize(string text)
        {
            if (string.IsNullOrEmpty(text)) return "";
            var sb = new StringBuilder(text.Length);
            foreach (var ch0 in text.ToLowerInvariant())
            {
                char ch = ch0;
                if (ch >= 'ً' && ch <= 'ْ' || ch == 'ـ' || ch == 'ٰ') continue; // tashkeel, tatweel
                switch (ch)
                {
                    case 'أ': case 'إ': case 'آ': case 'ٱ': ch = 'ا'; break;
                    case 'ة': ch = 'ه'; break;
                    case 'ى': ch = 'ي'; break;
                    case 'ؤ': ch = 'و'; break;
                    case 'ئ': ch = 'ي'; break;
                }
                sb.Append(char.IsLetterOrDigit(ch) ? ch : ' ');
            }
            return string.Join(" ", sb.ToString().Split(new[] { ' ' }, StringSplitOptions.RemoveEmptyEntries));
        }

        /// <summary>Normalised words with the article "ال" removed, so "الهجوم" and "هجوم" match.</summary>
        public static List<string> Tokens(string text) =>
            Normalize(text).Split(new[] { ' ' }, StringSplitOptions.RemoveEmptyEntries)
                .Select(StripArticle).ToList();

        static string StripArticle(string w)
        {
            if (w.Length > 4 && (w.StartsWith("وال") || w.StartsWith("بال") || w.StartsWith("فال"))) return w.Substring(3);
            if (w.Length > 3 && w.StartsWith("ال")) return w.Substring(2);
            if (w.Length > 3 && w.StartsWith("لل")) return w.Substring(2);
            return w;
        }

        public static VoiceCommand Parse(Game game, int player, string heard)
        {
            var tokens = Tokens(heard);
            var cmd = new VoiceCommand();
            var me = game.Players[player];
            var foe = game.Opponent(player);

            if (tokens.Count == 0) return Unknown(cmd, "مسمعتش حاجة");

            // End turn: "انهي الدور" / "خلصت دوري" / "end turn".
            bool mentionsTurn = tokens.Any(t => t == "دور" || t == "دوري" || t == "turn");
            if (mentionsTurn && tokens.Any(t => IsAny(t, "انهي", "انهاء", "خلص", "خلصت", "انتهي", "انهيت", "end", "finish", "done")))
            {
                cmd.Intent = VoiceIntent.EndTurn;
                return cmd;
            }

            int attackAt = IndexOfAny(tokens, AttackWords);
            int guardAt = IndexOfAny(tokens, GuardWords);
            bool positionPhrase = IndexOfAny(tokens, PositionWords) >= 0;

            var handPlay = ParseHandPlay(game, player, tokens, attackAt, guardAt);
            if (handPlay != null) return handPlay;

            int actionAt;
            if (guardAt >= 0 && (attackAt < 0 || positionPhrase && guardAt >= 0))
            {
                cmd.Intent = VoiceIntent.ToGuardPosition;
                actionAt = guardAt;
            }
            else if (attackAt >= 0 && positionPhrase)
            {
                cmd.Intent = VoiceIntent.ToAttackPosition;
                actionAt = attackAt;
            }
            else if (attackAt >= 0)
            {
                cmd.Intent = VoiceIntent.Attack;
                actionAt = attackAt;
            }
            else
            {
                return Unknown(cmd, "مفهمتش: قول هجوم أو دفاع أو انهي الدور");
            }

            // Names said before the verb are usually the actor, names after it the target,
            // but people also say "اهجم يا ماجرون على حارس المرايا", so search both sides.
            var mine = FindMentions(tokens, me.LivingCreatures);
            var theirs = FindMentions(tokens, foe.LivingCreatures);

            cmd.Actor = mine.OrderBy(m => m.Index < actionAt ? 0 : 1).ThenBy(m => m.Distance).Select(m => m.Creature).FirstOrDefault();
            if (cmd.Actor == null)
            {
                var candidates = me.LivingCreatures.Where(c => cmd.Intent != VoiceIntent.Attack || CanAttackNow(game, c)).ToList();
                if (candidates.Count == 1) cmd.Actor = candidates[0];
                else return Problem(cmd, candidates.Count == 0 ? "مفيش كائن عندك يقدر يعمل كده" : "أنهي كائن؟ قول اسمه");
            }

            if (cmd.Intent != VoiceIntent.Attack) return cmd;

            cmd.Target = theirs.Where(m => m.Creature != cmd.Actor)
                .OrderBy(m => m.Index > actionAt ? 0 : 1).ThenBy(m => m.Distance).Select(m => m.Creature).FirstOrDefault();
            if (cmd.Target != null) return cmd;

            var attackable = foe.LivingCreatures.Where(c => !game.IsProtected(c)).ToList();
            if (tokens.Any(t => DirectWords.Contains(t)) || attackable.Count == 0)
            {
                cmd.Direct = true;
                return cmd;
            }
            if (attackable.Count == 1)
            {
                cmd.Target = attackable[0];
                return cmd;
            }
            return Problem(cmd, "تهاجم مين؟ قول اسم كائن الخصم");
        }

        /// <summary>
        /// Commands that play a card from the hand: "انزل يا ماجرون"، "استخدم شعاع النواة على تيتانوكس"،
        /// "حط مرآة البلور"، "العب قلب البركان". Returns null when the sentence is about creatures on the table.
        /// </summary>
        static VoiceCommand ParseHandPlay(Game game, int player, List<string> tokens, int attackAt, int guardAt)
        {
            var me = game.Players[player];
            var foe = game.Opponent(player);
            bool playVerb = tokens.Any(t => PlayWords.Contains(t));
            bool boardVerb = attackAt >= 0 || guardAt >= 0;

            var inHand = FindNameMentions(tokens, me.Hand, c => c.Def).OrderBy(m => m.Index).ThenBy(m => m.Distance).ToList();
            var onBoard = FindMentions(tokens, me.LivingCreatures);

            foreach (var m in inHand)
            {
                var card = m.Item;
                var cmd = new VoiceCommand { HandCard = card };
                switch (card.Def.Type)
                {
                    case CardType.Creature:
                        // "ماجرون اهجم" means the one on the table if there is one; "انزل يا ماجرون" means the one in hand.
                        bool alsoOnBoard = onBoard.Any(b => b.Creature.Def.Id == card.Def.Id);
                        if (!playVerb && (alsoOnBoard || attackAt >= 0)) continue;
                        cmd.Intent = VoiceIntent.Summon;
                        cmd.SummonPosition = guardAt >= 0 ? Position.Guard : Position.Attack;
                        return cmd;

                    case CardType.Trap:
                        cmd.Intent = VoiceIntent.SetTrap;
                        return cmd;

                    case CardType.Field:
                        cmd.Intent = VoiceIntent.PlayField;
                        return cmd;

                    case CardType.Technique:
                        cmd.Intent = VoiceIntent.UseTechnique;
                        if (!Game.TechniqueNeedsTarget(card.Def)) return cmd;
                        var side = Game.TechniqueTargetsEnemy(card.Def) ? foe : me;
                        var nameEnd = m.Index + m.Length;
                        cmd.Target = FindMentions(tokens, side.LivingCreatures)
                            .Where(t => t.Index >= nameEnd || t.Index + 1 <= m.Index)
                            .OrderBy(t => t.Index >= nameEnd ? 0 : 1).ThenBy(t => t.Distance)
                            .Select(t => t.Creature).FirstOrDefault();
                        if (cmd.Target == null)
                        {
                            var options = side.LivingCreatures.ToList();
                            if (options.Count == 1) cmd.Target = options[0];
                            else return Problem(cmd, options.Count == 0 ? "مفيش هدف للتقنية دي" : "تستخدمها على مين؟ قول اسم الكائن");
                        }
                        return cmd;
                }
            }

            // "حط الفخ" with a single trap in hand needs no name.
            if (tokens.Any(t => TrapWords.Contains(t)))
            {
                var traps = me.Hand.Where(c => c.Def.Type == CardType.Trap).ToList();
                if (traps.Count == 1) return new VoiceCommand { Intent = VoiceIntent.SetTrap, HandCard = traps[0] };
                return Problem(new VoiceCommand { Intent = VoiceIntent.SetTrap },
                    traps.Count == 0 ? "مفيش فخ في إيدك" : "أنهي فخ؟ قول اسمه");
            }

            if (playVerb && !boardVerb)
            {
                var creatures = me.Hand.Where(c => c.Def.Type == CardType.Creature).ToList();
                return Problem(new VoiceCommand { Intent = VoiceIntent.Summon },
                    creatures.Count == 0 ? "مفيش كائنات في إيدك" : "أنهي كارت؟ قول اسمه");
            }
            return null;
        }

        static bool CanAttackNow(Game g, CreatureState c) =>
            g.Validate(new Attack { Player = c.Owner, AttackerId = c.InstanceId, TargetId = null }) is var e &&
            e != ActionError.InGuardPosition && e != ActionError.Frozen && e != ActionError.AlreadyAttacked &&
            e != ActionError.SummonedThisTurn && e != ActionError.CreatureNotFound;

        static VoiceCommand Unknown(VoiceCommand c, string why) { c.Intent = VoiceIntent.Unknown; c.Problem = why; return c; }
        static VoiceCommand Problem(VoiceCommand c, string why) { c.Problem = why; return c; }

        static bool IsAny(string token, params string[] words) => words.Contains(token);

        static int IndexOfAny(List<string> tokens, string[] words)
        {
            for (int i = 0; i < tokens.Count; i++)
                if (words.Any(w => w == tokens[i] || (w.Length >= 4 && Levenshtein(w, tokens[i]) <= 1))) return i;
            return -1;
        }

        struct Mention { public CreatureState Creature; public int Index; public int Distance; }

        struct NameMention<T> { public T Item; public int Index; public int Length; public int Distance; }

        /// <summary>Finds where each creature's name (or alias) is said, allowing small spelling slips.</summary>
        static List<Mention> FindMentions(List<string> tokens, IEnumerable<CreatureState> creatures) =>
            FindNameMentions(tokens, creatures, c => c.Def)
                .Select(m => new Mention { Creature = m.Item, Index = m.Index, Distance = m.Distance }).ToList();

        static List<NameMention<T>> FindNameMentions<T>(List<string> tokens, IEnumerable<T> items, Func<T, CardDefinition> def)
        {
            var found = new List<NameMention<T>>();
            var seenDefs = new HashSet<string>();
            foreach (var item in items)
            {
                var d = def(item);
                if (!seenDefs.Add(d.Id) && typeof(T) == typeof(CardInstance)) continue; // Copies in hand: the first is enough.
                NameMention<T>? best = null;
                foreach (var name in d.SpokenNames())
                {
                    var nameTokens = Tokens(name).Where(t => !Fillers.Contains(t)).ToList();
                    if (nameTokens.Count == 0) continue;
                    string joined = string.Join(" ", nameTokens);
                    for (int i = 0; i + nameTokens.Count <= tokens.Count; i++)
                    {
                        string window = string.Join(" ", tokens.Skip(i).Take(nameTokens.Count));
                        int dist = Levenshtein(joined, window);
                        if (dist <= Allowed(joined.Length) && (best == null || dist < best.Value.Distance))
                            best = new NameMention<T> { Item = item, Index = i, Length = nameTokens.Count, Distance = dist };
                    }
                }
                if (best != null) found.Add(best.Value);
            }
            return found;
        }

        static int Allowed(int length) => length >= 8 ? 2 : length >= 4 ? 1 : 0;

        public static int Levenshtein(string a, string b)
        {
            if (a == b) return 0;
            var prev = new int[b.Length + 1];
            var cur = new int[b.Length + 1];
            for (int j = 0; j <= b.Length; j++) prev[j] = j;
            for (int i = 1; i <= a.Length; i++)
            {
                cur[0] = i;
                for (int j = 1; j <= b.Length; j++)
                {
                    int cost = a[i - 1] == b[j - 1] ? 0 : 1;
                    cur[j] = Math.Min(Math.Min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                }
                (prev, cur) = (cur, prev);
            }
            return prev[b.Length];
        }
    }
}
