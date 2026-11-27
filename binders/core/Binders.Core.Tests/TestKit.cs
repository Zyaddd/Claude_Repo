using System.IO;
using System.Linq;
using Binders.Core;
using Xunit;

namespace Binders.Core.Tests
{
    /// <summary>Loads the real card data and builds small, controlled matches.</summary>
    static class TestKit
    {
        static CardDatabase _db;

        public static CardDatabase Db
        {
            get
            {
                if (_db != null) return _db;
                string dir = Path.Combine(System.AppContext.BaseDirectory, "Data");
                _db = CardDatabase.FromJson(File.ReadAllText(Path.Combine(dir, "cards.json")),
                                            File.ReadAllText(Path.Combine(dir, "decks.json")));
                return _db;
            }
        }

        public static CardDefinition Card(string id) => Db.Get(id);

        /// <summary>
        /// A match where both decks are only filler wolves and hands are emptied, so each test adds exactly
        /// the cards it needs. Player 0 starts. Both players get plenty of energy.
        /// </summary>
        public static Game EmptyMatch(int seed = 1)
        {
            var filler = Enumerable.Repeat(Card("flame_wolf"), 30).ToList();
            var g = Game.Create(filler, filler, seed, firstPlayer: 0);
            foreach (var p in g.Players) p.Hand.Clear();
            GiveEnergy(g);
            return g;
        }

        public static void GiveEnergy(Game g, int amount = 10)
        {
            foreach (var p in g.Players) { p.Energy = amount; p.MaxEnergy = amount - 1; }
        }

        /// <summary>Puts a creature on the table for the active player (must be their turn).</summary>
        public static CreatureState Summon(Game g, int player, string id, Position pos = Position.Attack)
        {
            Assert.Equal(player, g.ActivePlayer);
            var card = g.DebugAddToHand(player, Card(id));
            int energy = g.Players[player].Energy;
            g.Players[player].Energy = 10;
            var r = g.Execute(new SummonCreature { Player = player, CardId = card.InstanceId, Position = pos });
            Assert.True(r.Ok, r.Error.ToString());
            g.Players[player].Energy = energy;
            return g.FindCreature(card.InstanceId);
        }

        public static void SetTrap(Game g, int player, string id)
        {
            var card = g.DebugAddToHand(player, Card(id));
            g.Players[player].Energy = 10;
            var r = g.Execute(new SetTrap { Player = player, CardId = card.InstanceId });
            Assert.True(r.Ok, r.Error.ToString());
        }

        public static void EndTurn(Game g)
        {
            var r = g.Execute(new EndTurn { Player = g.ActivePlayer });
            Assert.True(r.Ok);
            GiveEnergy(g);
        }

        public static ActionResult Attack(Game g, CreatureState attacker, CreatureState target) =>
            g.Execute(new Attack { Player = attacker.Owner, AttackerId = attacker.InstanceId, TargetId = target?.InstanceId });

        public static bool Has(this ActionResult r, GameEventType t) => r.Events.Any(e => e.Type == t);
    }
}
