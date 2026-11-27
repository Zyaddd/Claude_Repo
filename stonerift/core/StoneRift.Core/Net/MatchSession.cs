using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using Newtonsoft.Json.Linq;

namespace StoneRift.Core.Net
{
    public enum SessionState
    {
        WaitingForGuest,  // Host: arena is ready, waiting for a friend to join.
        Connecting,       // Guest: talking to the host for the first time.
        Playing,
        Reconnecting,     // Connection dropped mid-match; the match is kept and resumes when it returns.
        Failed,           // Could not join (different version or cards, match full...). See FailureReason.
        Ended,            // Someone left.
    }

    /// <summary>
    /// A two-phone match on the local network.
    ///
    /// Both phones hold the same <see cref="Game"/>: it starts from the same seed and decks, and both apply
    /// the same actions in the same order. The host decides that order: it checks every action, numbers it
    /// and sends it to the guest, together with a fingerprint of the result. If the guest's match ever looks
    /// different, it asks for the full history and rebuilds. A dropped connection only pauses the match.
    ///
    /// Call <see cref="Poll"/> every frame on the game thread; all events fire from inside Poll or Submit.
    /// </summary>
    public sealed class MatchSession : IDisposable
    {
        public bool IsHost { get; }
        public int LocalPlayer => IsHost ? 0 : 1;
        public int RemotePlayer => 1 - LocalPlayer;
        public Game Game { get; private set; }
        public SessionState State { get; private set; }
        public string MatchId { get; private set; }
        public string FailureReason { get; private set; }
        public string OpponentName { get; private set; }
        /// <summary>Guest only: a request was sent and the host has not answered yet.</summary>
        public bool AwaitingHost { get; private set; }
        public bool Connected => _transport != null && _transport.IsOpen;
        public int ActionCount => _history.Count;

        /// <summary>The match was created (both phones). The UI can build the arena now.</summary>
        public event Action Started;
        /// <summary>An action happened, by either player, with the events to animate.</summary>
        public event Action<GameAction, List<GameEvent>> ActionApplied;
        /// <summary>The match was rebuilt from history (after a reconnect or a mismatch): redraw everything.</summary>
        public event Action Rebuilt;
        /// <summary>Guest only: the host refused our action.</summary>
        public event Action<ActionError> RequestRejected;

        public const double PingInterval = 2.0;
        public const double Timeout = 10.0;

        readonly CardDatabase _db;
        readonly string _localName;
        readonly Dictionary<string, int> _localDeck;
        readonly List<JObject> _history = new List<JObject>();
        readonly Func<double> _clock;
        readonly int _seed;
        ITransport _transport;
        Dictionary<string, int> _hostDeck, _guestDeck;
        string _hostName, _guestName;
        double _lastHeard, _lastPing;

        MatchSession(bool host, CardDatabase db, string name, Dictionary<string, int> deck, int seed, Func<double> clock)
        {
            IsHost = host;
            _db = db;
            _localName = name;
            _localDeck = new Dictionary<string, int>(deck);
            _seed = seed;
            if (clock == null)
            {
                var sw = Stopwatch.StartNew();
                clock = () => sw.Elapsed.TotalSeconds;
            }
            _clock = clock;
            State = host ? SessionState.WaitingForGuest : SessionState.Connecting;
        }

        public static MatchSession CreateHost(CardDatabase db, string name, Dictionary<string, int> deck, int seed, Func<double> clock = null) =>
            new MatchSession(true, db, name, deck, seed, clock);

        public static MatchSession CreateGuest(CardDatabase db, string name, Dictionary<string, int> deck, Func<double> clock = null) =>
            new MatchSession(false, db, name, deck, 0, clock);

        /// <summary>
        /// Uses a new connection: the first one, or a fresh one after a drop.
        /// The guest introduces itself; if it was already in this match it asks for what it missed.
        /// </summary>
        public void Attach(ITransport transport)
        {
            if (_transport != null && _transport != transport) _transport.Close();
            _transport = transport;
            _lastHeard = _lastPing = _clock();
            if (!IsHost)
            {
                Send(new NetMessage
                {
                    Type = MessageType.Hello,
                    ProtocolVersion = Protocol.Version,
                    Name = _localName,
                    Deck = _localDeck,
                    DataHash = _db.ContentHash().ToString("x16"),
                    MatchId = MatchId,
                    Seq = _history.Count,
                });
            }
        }

        // ------------------------------------------------------------------ local moves

        /// <summary>
        /// Plays a move for the local player. The host applies it straight away; the guest sends it to the host
        /// and the result arrives through <see cref="ActionApplied"/> (or <see cref="RequestRejected"/>).
        /// Returns an error immediately for moves that are clearly not allowed.
        /// </summary>
        public ActionError Submit(GameAction action)
        {
            if (Game == null || State != SessionState.Playing || !Connected) return ActionError.NotConnected;
            if (action.Player != LocalPlayer) return ActionError.NotYourTurn;
            if (!IsHost && AwaitingHost) return ActionError.NotConnected;
            var err = Game.Validate(action);
            if (err != ActionError.None) return err;

            if (IsHost) ApplyAndBroadcast(action);
            else
            {
                AwaitingHost = true;
                Send(new NetMessage { Type = MessageType.Request, Action = ActionCodec.Encode(action) });
            }
            return ActionError.None;
        }

        void ApplyAndBroadcast(GameAction action)
        {
            var result = Game.Execute(action);
            if (!result.Ok) return;
            var json = ActionCodec.Encode(action);
            _history.Add(json);
            Send(new NetMessage { Type = MessageType.Accepted, Seq = _history.Count, Action = json, StateHash = Game.StateHash().ToString("x16") });
            ActionApplied?.Invoke(action, result.Events);
        }

        // ------------------------------------------------------------------ incoming

        public void Poll()
        {
            if (_transport == null) return;
            double now = _clock();

            while (_transport.TryReceive(out var line))
            {
                _lastHeard = now;
                var msg = NetMessage.Decode(line);
                if (msg == null) continue;
                if (IsHost) HandleAsHost(msg);
                else HandleAsGuest(msg);
                if (_transport == null) return;
            }

            if (_transport.IsOpen)
            {
                if (now - _lastPing >= PingInterval)
                {
                    _lastPing = now;
                    Send(new NetMessage { Type = MessageType.Ping });
                }
                if (now - _lastHeard > Timeout) _transport.Close(); // Silent for too long: treat as dropped.
            }

            if (!_transport.IsOpen && State == SessionState.Playing)
            {
                State = SessionState.Reconnecting;
                AwaitingHost = false;
            }
        }

        void HandleAsHost(NetMessage msg)
        {
            switch (msg.Type)
            {
                case MessageType.Hello:
                    if (msg.ProtocolVersion != Protocol.Version) { Refuse("نسخة اللعبة مختلفة، حدّثوا الاتنين لنفس النسخة"); return; }
                    if (msg.DataHash != _db.ContentHash().ToString("x16")) { Refuse("الكروت مختلفة بين الموبايلين، حدّثوا الاتنين لنفس النسخة"); return; }
                    if (Game == null)
                    {
                        if (msg.Deck == null || _db.ValidateDeck(new DeckList { Id = "guest", Cards = msg.Deck }).Count > 0) { Refuse("الديك بتاعك مش سليم"); return; }
                        MatchId = Guid.NewGuid().ToString("N");
                        _hostDeck = _localDeck;
                        _guestDeck = new Dictionary<string, int>(msg.Deck);
                        _hostName = _localName;
                        _guestName = string.IsNullOrWhiteSpace(msg.Name) ? "صاحبك" : msg.Name;
                        OpponentName = _guestName;
                        Game = CreateGame(_seed, _hostDeck, _guestDeck, _hostName, _guestName);
                        SendWelcome(0);
                        State = SessionState.Playing;
                        Started?.Invoke();
                    }
                    else if (msg.MatchId == MatchId)
                    {
                        // The same guest coming back: send what it missed.
                        SendWelcome(msg.Seq >= 0 && msg.Seq <= _history.Count ? msg.Seq : 0);
                        State = SessionState.Playing;
                    }
                    else Refuse("فيه ماتش شغال بالفعل مع لاعب تاني");
                    break;

                case MessageType.Request:
                {
                    var action = ActionCodec.Decode(msg.Action);
                    if (action == null || action.Player != RemotePlayer || Game == null)
                    {
                        Send(new NetMessage { Type = MessageType.Rejected, Error = ActionError.InvalidTarget.ToString() });
                        return;
                    }
                    var err = Game.Validate(action);
                    if (err != ActionError.None) Send(new NetMessage { Type = MessageType.Rejected, Error = err.ToString() });
                    else ApplyAndBroadcast(action);
                    break;
                }

                case MessageType.Resync:
                    if (Game != null) SendWelcome(0);
                    break;
                case MessageType.Ping:
                    Send(new NetMessage { Type = MessageType.Pong });
                    break;
                case MessageType.Leave:
                    State = SessionState.Ended;
                    _transport.Close();
                    break;
            }
        }

        void HandleAsGuest(NetMessage msg)
        {
            switch (msg.Type)
            {
                case MessageType.Welcome:
                {
                    bool fresh = Game == null || msg.MatchId != MatchId || msg.HistoryStart == 0;
                    if (fresh)
                    {
                        if (msg.HostDeck == null || msg.GuestDeck == null) return;
                        bool first = Game == null;
                        MatchId = msg.MatchId;
                        _hostDeck = msg.HostDeck;
                        _guestDeck = msg.GuestDeck;
                        _hostName = msg.HostName;
                        _guestName = msg.GuestName;
                        OpponentName = _hostName;
                        Game = CreateGame(msg.Seed, _hostDeck, _guestDeck, _hostName, _guestName);
                        _history.Clear();
                        if (!Replay(msg.History)) return;
                        State = SessionState.Playing;
                        AwaitingHost = false;
                        if (first) Started?.Invoke();
                        if (!first || _history.Count > 0) Rebuilt?.Invoke();
                    }
                    else
                    {
                        if (msg.HistoryStart != _history.Count) { RequestResync(); return; }
                        if (!Replay(msg.History)) return;
                        State = SessionState.Playing;
                        AwaitingHost = false;
                        Rebuilt?.Invoke();
                    }
                    break;
                }

                case MessageType.Accepted:
                {
                    if (Game == null) return;
                    if (msg.Seq != _history.Count + 1) { RequestResync(); return; }
                    var action = ActionCodec.Decode(msg.Action);
                    var result = action == null ? null : Game.Execute(action);
                    if (result == null || !result.Ok) { RequestResync(); return; }
                    _history.Add(msg.Action);
                    if (action.Player == LocalPlayer) AwaitingHost = false;
                    ActionApplied?.Invoke(action, result.Events);
                    if (msg.StateHash != null && msg.StateHash != Game.StateHash().ToString("x16")) RequestResync();
                    break;
                }

                case MessageType.Rejected:
                    AwaitingHost = false;
                    RequestRejected?.Invoke(Enum.TryParse(msg.Error, out ActionError e) ? e : ActionError.InvalidTarget);
                    break;

                case MessageType.Refused:
                    State = SessionState.Failed;
                    FailureReason = msg.Error;
                    _transport.Close();
                    break;

                case MessageType.Ping:
                    Send(new NetMessage { Type = MessageType.Pong });
                    break;
                case MessageType.Leave:
                    State = SessionState.Ended;
                    _transport.Close();
                    break;
            }
        }

        /// <summary>Applies a list of past actions without animation. False (and asks for a resync) if any fails.</summary>
        bool Replay(List<JObject> actions)
        {
            if (actions == null) return true;
            foreach (var json in actions)
            {
                var action = ActionCodec.Decode(json);
                if (action == null || !Game.Execute(action).Ok)
                {
                    RequestResync();
                    return false;
                }
                _history.Add(json);
            }
            return true;
        }

        void RequestResync()
        {
            AwaitingHost = false;
            Send(new NetMessage { Type = MessageType.Resync });
        }

        void SendWelcome(int from)
        {
            Send(new NetMessage
            {
                Type = MessageType.Welcome,
                MatchId = MatchId,
                Seed = _seed,
                HostName = _hostName,
                GuestName = _guestName,
                HostDeck = _hostDeck,
                GuestDeck = _guestDeck,
                HistoryStart = from,
                History = _history.Skip(from).ToList(),
            });
        }

        void Refuse(string reason) => Send(new NetMessage { Type = MessageType.Refused, Error = reason });

        Game CreateGame(int seed, Dictionary<string, int> hostDeck, Dictionary<string, int> guestDeck, string hostName, string guestName) =>
            Game.Create(_db.Expand(new DeckList { Id = "host", Cards = hostDeck }),
                        _db.Expand(new DeckList { Id = "guest", Cards = guestDeck }),
                        seed, -1, hostName, guestName);

        void Send(NetMessage msg) => _transport?.Send(msg.Encode());

        /// <summary>Tells the other phone we are leaving and closes the connection.</summary>
        public void Dispose()
        {
            if (_transport != null && _transport.IsOpen) Send(new NetMessage { Type = MessageType.Leave });
            _transport?.Close();
            State = SessionState.Ended;
        }
    }
}
