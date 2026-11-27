using System.Collections.Generic;
using System.Linq;

namespace StoneRift.Core
{
    public interface IBot
    {
        GameAction ChooseAction(Game game, int player);
    }

    public enum BotLevel { Easy, Normal, Hard }

    public static class Bots
    {
        public static IBot Create(BotLevel level, int seed)
        {
            switch (level)
            {
                case BotLevel.Easy: return new SloppyBot(seed);
                case BotLevel.Hard: return new SmartBot(seed);
                default: return new SimpleBot(seed);
            }
        }
    }

    /// <summary>Easy: plays like the normal bot but often makes a random (legal) move instead.</summary>
    public sealed class SloppyBot : IBot
    {
        readonly SimpleBot _inner;
        readonly DeterministicRandom _rng;

        public SloppyBot(int seed)
        {
            _inner = new SimpleBot(seed);
            _rng = new DeterministicRandom(seed * 7 + 3);
        }

        public GameAction ChooseAction(Game game, int player)
        {
            var legal = game.LegalActions(player);
            if (legal.Count == 0) return null;
            if (_rng.Next(100) < 40)
            {
                // Random, but never end the turn by accident while something else is possible half the time.
                var pick = legal[_rng.Next(legal.Count)];
                if (!(pick is EndTurn) || _rng.Next(2) == 0) return pick;
            }
            return _inner.ChooseAction(game, player);
        }
    }

    /// <summary>
    /// Hard: for every possible move it asks "if I did this and then ended my turn, how would things look
    /// after my opponent's best reply?" on a copy of the match, and plays the move with the best answer.
    /// It keeps choosing moves this way until ending the turn is the best option.
    /// </summary>
    public sealed class SmartBot : IBot
    {
        readonly SimpleBot _opponentModel;

        public SmartBot(int seed) { _opponentModel = new SimpleBot(seed * 13 + 1); }

        public GameAction ChooseAction(Game game, int player)
        {
            var legal = game.LegalActions(player);
            if (legal.Count == 0) return null;

            GameAction best = null;
            double bestScore = double.NegativeInfinity;
            foreach (var action in legal)
            {
                var sim = game.Clone();
                if (!sim.Execute(action).Ok) continue;
                if (sim.Phase == Phase.Main && sim.ActivePlayer == player) sim.Execute(new EndTurn { Player = player });
                double score = ScoreAfterOpponentReply(sim, player);
                if (action is EndTurn) score -= 1; // On a tie, prefer doing something.
                if (score > bestScore)
                {
                    bestScore = score;
                    best = action;
                }
            }
            return best ?? legal.OfType<EndTurn>().First();
        }

        /// <summary>Lets the opponent play its whole turn with the normal bot, then scores the result.</summary>
        double ScoreAfterOpponentReply(Game sim, int player)
        {
            int opponent = 1 - player;
            for (int i = 0; i < 30 && sim.Phase == Phase.Main && sim.ActivePlayer == opponent; i++)
            {
                var reply = _opponentModel.ChooseAction(sim, opponent);
                if (reply == null || !sim.Execute(reply).Ok) break;
            }
            return Evaluate(sim, player);
        }

        /// <summary>How good the match looks for this player: life, the board, cards in hand, traps.</summary>
        public static double Evaluate(Game g, int player)
        {
            if (g.Phase == Phase.GameOver) return g.Winner == player ? 1e7 : -1e7;
            return Side(g, player) - Side(g, 1 - player);
        }

        static double Side(Game g, int p)
        {
            var me = g.Players[p];
            double score = me.Life * 1.0;
            foreach (var c in me.LivingCreatures)
            {
                double value = g.EffectiveAttack(c) * 0.6 + g.EffectiveDefense(c) * 0.4 + c.Def.Cost * 120;
                if (g.IsFrozen(c)) value *= 0.75;
                score += value;
            }
            score += me.Hand.Count * 180;
            score += me.Supports.Count(s => s != null) * 250;
            if (g.Field != null && g.Field.Owner == p) score += 150;
            if (me.Deck.Count == 0) score -= 400;
            return score;
        }
    }
}
