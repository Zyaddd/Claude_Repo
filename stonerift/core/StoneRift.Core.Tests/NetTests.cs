using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using StoneRift.Core;
using StoneRift.Core.Net;
using Xunit;
using static StoneRift.Core.Tests.TestKit;

namespace StoneRift.Core.Tests
{
    public class NetTests
    {
        static Dictionary<string, int> Deck(string id) => new Dictionary<string, int>(Db.GetDeck(id).Cards);

        /// <summary>A fake clock so timeouts can be tested without waiting.</summary>
        sealed class Clock { public double Now; public double Read() => Now; }

        static (MatchSession host, MatchSession guest, LoopbackTransport h, LoopbackTransport g) Pair(int seed = 11, Clock clock = null)
        {
            Func<double> time = clock != null ? clock.Read : (Func<double>)null;
            var host = MatchSession.CreateHost(Db, "Host", Deck("magma_volt"), seed, time);
            var guest = MatchSession.CreateGuest(Db, "Guest", Deck("crystal_tide"), time);
            var (h, g) = LoopbackTransport.CreatePair();
            host.Attach(h);
            guest.Attach(g);
            PollBoth(host, guest);
            return (host, guest, h, g);
        }

        static void PollBoth(MatchSession host, MatchSession guest)
        {
            for (int i = 0; i < 4; i++) { host.Poll(); guest.Poll(); }
        }

        /// <summary>Lets bots on each phone play until the match ends or maxActions is reached.</summary>
        static void PlayOut(MatchSession host, MatchSession guest, int maxActions = 5000, Action<int> afterAction = null)
        {
            var bots = new[] { new SimpleBot(1), new SimpleBot(2) };
            int actions = 0;
            while (host.Game.Phase != Phase.GameOver && actions < maxActions)
            {
                PollBoth(host, guest);
                if (guest.ActionCount != host.ActionCount || guest.AwaitingHost) continue;
                var s = host.Game.ActivePlayer == host.LocalPlayer ? host : guest;
                var action = bots[s.LocalPlayer].ChooseAction(s.Game, s.LocalPlayer);
                Assert.Equal(ActionError.None, s.Submit(action));
                actions++;
                PollBoth(host, guest);
                afterAction?.Invoke(actions);
            }
            PollBoth(host, guest);
        }

        static void AssertSame(MatchSession host, MatchSession guest)
        {
            Assert.Equal(host.ActionCount, guest.ActionCount);
            Assert.Equal(host.Game.StateHash(), guest.Game.StateHash());
        }

        [Fact]
        public void GuestJoinsAndBothPhonesStartTheSameMatch()
        {
            var (host, guest, _, _) = Pair();
            Assert.Equal(SessionState.Playing, host.State);
            Assert.Equal(SessionState.Playing, guest.State);
            Assert.Equal("Guest", host.OpponentName);
            Assert.Equal("Host", guest.OpponentName);
            Assert.Equal(0, host.LocalPlayer);
            Assert.Equal(1, guest.LocalPlayer);
            AssertSame(host, guest);
        }

        [Fact]
        public void FullMatchStaysIdenticalOnBothPhones()
        {
            for (int seed = 1; seed <= 20; seed++)
            {
                var (host, guest, _, _) = Pair(seed);
                int hostEvents = 0, guestEvents = 0;
                host.ActionApplied += (_, ev) => hostEvents += ev.Count;
                guest.ActionApplied += (_, ev) => guestEvents += ev.Count;
                PlayOut(host, guest);
                Assert.Equal(Phase.GameOver, host.Game.Phase);
                Assert.Equal(Phase.GameOver, guest.Game.Phase);
                Assert.Equal(host.Game.Winner, guest.Game.Winner);
                Assert.Equal(hostEvents, guestEvents);
                AssertSame(host, guest);
            }
        }

        [Fact]
        public void PhonesWithDifferentCardsAreRefused()
        {
            var otherCards = Db.Cards.Select(c => Newtonsoft.Json.JsonConvert.DeserializeObject<CardDefinition>(Newtonsoft.Json.JsonConvert.SerializeObject(c))).ToList();
            otherCards.First(c => c.Id == "magron").Attack += 100;
            var otherDb = new CardDatabase(otherCards, Db.Decks);

            var host = MatchSession.CreateHost(Db, "Host", Deck("magma_volt"), 1);
            var guest = MatchSession.CreateGuest(otherDb, "Guest", Deck("crystal_tide"));
            var (h, g) = LoopbackTransport.CreatePair();
            host.Attach(h);
            guest.Attach(g);
            PollBoth(host, guest);
            Assert.Equal(SessionState.Failed, guest.State);
            Assert.NotNull(guest.FailureReason);
            Assert.Null(host.Game);
        }

        [Fact]
        public void IllegalDecksAreRefused()
        {
            var host = MatchSession.CreateHost(Db, "Host", Deck("magma_volt"), 1);
            var cheat = new Dictionary<string, int> { ["magron"] = 30 };
            var guest = MatchSession.CreateGuest(Db, "Guest", cheat);
            var (h, g) = LoopbackTransport.CreatePair();
            host.Attach(h);
            guest.Attach(g);
            PollBoth(host, guest);
            Assert.Equal(SessionState.Failed, guest.State);
        }

        [Fact]
        public void GuestCannotPlayForTheHost()
        {
            var (host, guest, _, g) = Pair();
            Assert.Equal(ActionError.NotYourTurn, guest.Submit(new EndTurn { Player = 0 }));

            // A modified app sending the host's moves directly is rejected by the host.
            ulong before = host.Game.StateHash();
            g.Send(new NetMessage { Type = MessageType.Request, Action = ActionCodec.Encode(new EndTurn { Player = 0 }) }.Encode());
            ActionError? rejected = null;
            guest.RequestRejected += e => rejected = e;
            PollBoth(host, guest);
            Assert.NotNull(rejected);
            Assert.Equal(before, host.Game.StateHash());
        }

        [Fact]
        public void GuestCannotActOnHostsTurn()
        {
            var (host, guest, _, _) = Pair(seed: 3);
            var waiting = host.Game.ActivePlayer == 0 ? guest : host;
            Assert.Equal(ActionError.NotYourTurn, waiting.Submit(new EndTurn { Player = waiting.LocalPlayer }));
        }

        [Fact]
        public void DroppedConnectionResumesWhereItStopped()
        {
            var (host, guest, h, g) = Pair(seed: 5);
            PlayOut(host, guest, maxActions: 15);
            AssertSame(host, guest);

            // The host makes a move, but the connection dies before the guest reads it.
            if (host.Game.ActivePlayer != host.LocalPlayer)
            {
                guest.Submit(new EndTurn { Player = guest.LocalPlayer });
                PollBoth(host, guest);
            }
            host.Submit(new SimpleBot(9).ChooseAction(host.Game, host.LocalPlayer));
            h.Close();
            host.Poll();
            Assert.Equal(SessionState.Reconnecting, host.State);
            Assert.Equal(ActionError.NotConnected, host.Submit(new EndTurn { Player = 0 }));
            Assert.True(guest.ActionCount < host.ActionCount); // The guest never read that move.

            bool rebuilt = false;
            guest.Rebuilt += () => rebuilt = true;
            var (h2, g2) = LoopbackTransport.CreatePair();
            host.Attach(h2);
            guest.Attach(g2);
            PollBoth(host, guest);
            Assert.True(rebuilt);
            Assert.Equal(SessionState.Playing, guest.State);
            AssertSame(host, guest);

            PlayOut(host, guest);
            Assert.Equal(Phase.GameOver, guest.Game.Phase);
            AssertSame(host, guest);
        }

        [Fact]
        public void MismatchIsDetectedAndRepaired()
        {
            var (host, guest, _, _) = Pair(seed: 8);
            PlayOut(host, guest, maxActions: 10);
            guest.Game.Players[1].Life -= 1234; // Something went wrong on the guest's phone.
            Assert.NotEqual(host.Game.StateHash(), guest.Game.StateHash());

            PlayOut(host, guest, maxActions: 3);
            AssertSame(host, guest);
        }

        [Fact]
        public void SilentConnectionTimesOut()
        {
            var clock = new Clock();
            var (host, guest, _, _) = Pair(seed: 2, clock: clock);
            clock.Now = MatchSession.Timeout + 1;
            host.Poll(); // The guest stopped answering (nobody polled it).
            Assert.Equal(SessionState.Reconnecting, host.State);
        }

        [Fact]
        public void SecondGuestIsTurnedAway()
        {
            var (host, _, _, _) = Pair();
            var intruder = MatchSession.CreateGuest(Db, "Other", Deck("crystal_tide"));
            var (h, g) = LoopbackTransport.CreatePair();
            host.Attach(h);
            intruder.Attach(g);
            for (int i = 0; i < 4; i++) { host.Poll(); intruder.Poll(); }
            Assert.Equal(SessionState.Failed, intruder.State);
        }

        [Fact]
        public void EveryActionKindSurvivesTheTrip()
        {
            var actions = new GameAction[]
            {
                new SummonCreature { Player = 1, CardId = 7, Slot = 2, Position = Position.Guard },
                new SetTrap { Player = 0, CardId = 3, Slot = -1 },
                new UseTechnique { Player = 1, CardId = 9, TargetId = 44 },
                new UseTechnique { Player = 1, CardId = 9 },
                new PlayField { Player = 0, CardId = 12 },
                new Attack { Player = 0, AttackerId = 5, TargetId = 61 },
                new Attack { Player = 0, AttackerId = 5 },
                new ChangePosition { Player = 1, CreatureId = 4, To = Position.Attack },
                new ChangePosition { Player = 1, CreatureId = 4 },
                new EndTurn { Player = 1 },
            };
            foreach (var a in actions)
            {
                var line = new NetMessage { Type = MessageType.Request, Action = ActionCodec.Encode(a) }.Encode();
                var back = ActionCodec.Decode(NetMessage.Decode(line).Action);
                Assert.Equal(ActionCodec.Encode(a).ToString(), ActionCodec.Encode(back).ToString());
            }
        }

        [Theory]
        [InlineData("not json")]
        [InlineData("{\"Type\":\"Request\",\"Action\":{\"type\":\"explode\",\"player\":0}}")]
        [InlineData("{\"Type\":\"Request\",\"Action\":{\"type\":\"attack\",\"player\":7,\"attacker\":1}}")]
        [InlineData("{\"Type\":\"Request\",\"Action\":{\"type\":\"attack\",\"player\":0}}")]
        public void GarbageNeverBecomesAnAction(string line)
        {
            var msg = NetMessage.Decode(line);
            Assert.Null(msg?.Action == null ? null : ActionCodec.Decode(msg.Action));
        }

        [Fact]
        public void PlaysOverRealTcp()
        {
            using var server = new TcpHost(port: 0);
            var host = MatchSession.CreateHost(Db, "Host", Deck("magma_volt"), 77);
            var guest = MatchSession.CreateGuest(Db, "Guest", Deck("crystal_tide"));
            guest.Attach(TcpTransport.Connect("127.0.0.1", server.Port));

            var deadline = DateTime.UtcNow.AddSeconds(10);
            TcpTransport accepted = null;
            while (accepted == null && DateTime.UtcNow < deadline)
                if (!server.TryAccept(out accepted)) Thread.Sleep(5);
            Assert.NotNull(accepted);
            host.Attach(accepted);

            var bots = new[] { new SimpleBot(1), new SimpleBot(2) };
            int actions = 0;
            while (host.Game?.Phase != Phase.GameOver && DateTime.UtcNow < deadline.AddSeconds(20) && actions < 3000)
            {
                host.Poll();
                guest.Poll();
                Thread.Sleep(1);
                if (host.Game == null || guest.Game == null || guest.ActionCount != host.ActionCount || guest.AwaitingHost) continue;
                var s = host.Game.ActivePlayer == 0 ? host : guest;
                Assert.Equal(ActionError.None, s.Submit(bots[s.LocalPlayer].ChooseAction(s.Game, s.LocalPlayer)));
                actions++;
            }
            var settle = DateTime.UtcNow.AddSeconds(5);
            while (guest.ActionCount != host.ActionCount && DateTime.UtcNow < settle) { host.Poll(); guest.Poll(); Thread.Sleep(2); }

            Assert.Equal(Phase.GameOver, host.Game.Phase);
            AssertSame(host, guest);
            host.Dispose();
            guest.Dispose();
        }
    }
}

namespace StoneRift.Core.Tests
{
    public class DiscoveryTests
    {
        [Fact]
        public void BrowserHearsTheHostsAnnouncement()
        {
            const int port = 47911;
            using var browser = new StoneRift.Core.Net.LanBrowser(port);
            using var beacon = new StoneRift.Core.Net.LanBeacon("زياد", 47800, port);
            var deadline = System.DateTime.UtcNow.AddSeconds(5);
            while (browser.Hosts.Count == 0 && System.DateTime.UtcNow < deadline) System.Threading.Thread.Sleep(50);
            var host = Assert.Single(browser.Hosts);
            Assert.Equal("زياد", host.Name);
            Assert.Equal(47800, host.Port);
        }
    }
}
