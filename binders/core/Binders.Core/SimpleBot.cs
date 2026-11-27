using System.Linq;

namespace Binders.Core
{
    /// <summary>
    /// A plain greedy opponent: plays its strongest affordable cards, then makes the attacks that win trades.
    /// Used for the practice mode and for balance simulations.
    /// </summary>
    public sealed class SimpleBot
    {
        readonly DeterministicRandom _rng;

        public SimpleBot(int seed) { _rng = new DeterministicRandom(seed); }

        public GameAction ChooseAction(Game game, int player)
        {
            var legal = game.LegalActions(player);
            if (legal.Count == 0) return null;
            var me = game.Players[player];
            var foe = game.Opponent(player);

            // 1. Field and traps first: they are cheap and protect the creatures that follow.
            var field = legal.OfType<PlayField>().FirstOrDefault(f => game.Field == null || game.Field.Owner != player);
            if (field != null) return field;

            // 2. Summon the most expensive creature we can afford; weak ones go to Guard when the enemy is stronger.
            var summon = legal.OfType<SummonCreature>()
                .Select(s => (s, card: me.Hand.First(h => h.InstanceId == s.CardId)))
                .OrderByDescending(x => x.card.Def.Cost)
                .FirstOrDefault(x =>
                {
                    int strongestFoe = foe.LivingCreatures.Select(game.EffectiveAttack).DefaultIfEmpty(0).Max();
                    bool wantGuard = x.card.Def.Attack < strongestFoe && x.card.Def.Defense > x.card.Def.Attack;
                    return x.s.Position == (wantGuard ? Position.Guard : Position.Attack);
                });
            if (summon.s != null) return summon.s;

            var trap = legal.OfType<SetTrap>().FirstOrDefault();
            if (trap != null) return trap;

            // 3. Spells that clearly help.
            foreach (var cs in legal.OfType<CastSpell>())
            {
                var def = me.Hand.First(h => h.InstanceId == cs.CardId).Def;
                if (def.Has(EffectKind.DestroyTargetCreature) || def.Has(EffectKind.FreezeTarget))
                {
                    var best = foe.LivingCreatures.OrderByDescending(game.EffectiveAttack).First();
                    if (cs.TargetId == best.InstanceId) return cs;
                    continue;
                }
                if (def.Has(EffectKind.DestroyAllDefenseBelow))
                {
                    int amount = def.Get(EffectKind.DestroyAllDefenseBelow).Amount;
                    int theirs = foe.LivingCreatures.Count(c => game.EffectiveDefense(c) < amount);
                    int ours = me.LivingCreatures.Count(c => game.EffectiveDefense(c) < amount);
                    if (theirs > ours) return cs;
                    continue;
                }
                if (def.Has(EffectKind.BuffAttack)) continue; // Handled with attacks below.
                if (def.Has(EffectKind.Heal) && me.Life > 2500) continue;
                return cs;
            }

            // 4. Attacks: direct if possible, otherwise the best winning trade.
            var attacks = legal.OfType<Attack>().ToList();
            var direct = attacks.FirstOrDefault(a => a.TargetId == null);
            if (direct != null) return direct;
            Attack bestAttack = null;
            int bestScore = 0;
            foreach (var a in attacks)
            {
                var atk = game.FindCreature(a.AttackerId);
                var tgt = game.FindCreature(a.TargetId.Value);
                int mine = game.EffectiveAttack(atk);
                int theirs = tgt.Position == Position.Attack ? game.EffectiveAttack(tgt) : game.EffectiveDefense(tgt);
                int score = mine > theirs ? 1000 + tgt.Def.Cost * 100 + (mine - theirs) / 10 : -1;
                if (score > bestScore) { bestScore = score; bestAttack = a; }
            }
            if (bestAttack != null) return bestAttack;

            // 5. Occasionally reposition, otherwise end the turn.
            var reposition = legal.OfType<ChangePosition>().FirstOrDefault(cp =>
            {
                var c = game.FindCreature(cp.CreatureId);
                int strongestFoe = foe.LivingCreatures.Select(game.EffectiveAttack).DefaultIfEmpty(0).Max();
                return c.Position == Position.Attack && game.EffectiveAttack(c) < strongestFoe && c.Def.Defense > strongestFoe
                    || c.Position == Position.Guard && game.EffectiveAttack(c) > strongestFoe;
            });
            if (reposition != null && _rng.Next(4) != 0) return reposition;

            return legal.OfType<EndTurn>().First();
        }
    }
}
