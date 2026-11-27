using System;
using System.Collections.Generic;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace StoneRift.Core.Net
{
    /// <summary>
    /// Everything the two phones say to each other. Sent as one JSON object per line.
    /// The host owns the order of actions; both phones replay the same actions on the same seed,
    /// so both always hold the same match.
    /// </summary>
    public static class Protocol
    {
        public const int Version = 1;
        public const int DefaultPort = 47800;
        public const int DiscoveryPort = 47801;
    }

    public enum MessageType
    {
        Hello,      // guest → host: who I am, my deck, which match I'm returning to
        Welcome,    // host → guest: match setup (seed, decks) and any actions the guest missed
        Request,    // guest → host: please do this action
        Accepted,   // host → guest: action number N happened (with a state fingerprint)
        Rejected,   // host → guest: your request was not allowed
        Refused,    // host → guest: you can't join (wrong version, wrong cards, match full)
        Ping,
        Pong,
        Resync,     // guest → host: my match differs from yours, send the whole history
        Leave,
    }

    public sealed class NetMessage
    {
        public MessageType Type;
        public int ProtocolVersion;
        public string Name;
        public Dictionary<string, int> Deck;
        public string MatchId;
        public int Seq;
        public string DataHash;   // Hex, so 64-bit values survive any JSON reader.
        public string StateHash;
        public int Seed;
        public string HostName;
        public string GuestName;
        public Dictionary<string, int> HostDeck;
        public Dictionary<string, int> GuestDeck;
        public JObject Action;
        public List<JObject> History;
        public int HistoryStart;
        public string Error;

        static readonly JsonSerializerSettings Settings = new JsonSerializerSettings
        {
            NullValueHandling = NullValueHandling.Ignore,
            DefaultValueHandling = DefaultValueHandling.Ignore,
            Formatting = Formatting.None,
        };

        public string Encode() => JsonConvert.SerializeObject(this, Settings);

        /// <summary>Parses a line from the other phone; returns null for anything malformed.</summary>
        public static NetMessage Decode(string line)
        {
            try { return JsonConvert.DeserializeObject<NetMessage>(line, Settings); }
            catch (JsonException) { return null; }
        }
    }

    /// <summary>
    /// Converts actions to and from JSON by hand: only the known action kinds can be created
    /// from network data, nothing else.
    /// </summary>
    public static class ActionCodec
    {
        public static JObject Encode(GameAction a)
        {
            var o = new JObject { ["player"] = a.Player };
            switch (a)
            {
                case SummonCreature s:
                    o["type"] = "summon"; o["card"] = s.CardId; o["slot"] = s.Slot; o["guard"] = s.Position == Position.Guard;
                    break;
                case SetTrap t:
                    o["type"] = "trap"; o["card"] = t.CardId; o["slot"] = t.Slot;
                    break;
                case UseTechnique u:
                    o["type"] = "technique"; o["card"] = u.CardId;
                    if (u.TargetId.HasValue) o["target"] = u.TargetId.Value;
                    break;
                case PlayField f:
                    o["type"] = "field"; o["card"] = f.CardId;
                    break;
                case Attack at:
                    o["type"] = "attack"; o["attacker"] = at.AttackerId;
                    if (at.TargetId.HasValue) o["target"] = at.TargetId.Value;
                    break;
                case ChangePosition cp:
                    o["type"] = "position"; o["creature"] = cp.CreatureId;
                    if (cp.To.HasValue) o["guard"] = cp.To.Value == Position.Guard;
                    break;
                case EndTurn _:
                    o["type"] = "end";
                    break;
                default:
                    throw new ArgumentException("Unknown action " + a.GetType().Name);
            }
            return o;
        }

        /// <summary>Returns null if the JSON is not a valid action.</summary>
        public static GameAction Decode(JObject o)
        {
            if (o == null) return null;
            try
            {
                int player = (int)o["player"];
                if (player < 0 || player > 1) return null;
                int? Opt(string k) => o[k] == null || o[k].Type == JTokenType.Null ? (int?)null : (int)o[k];
                switch ((string)o["type"])
                {
                    case "summon":
                        return new SummonCreature { Player = player, CardId = (int)o["card"], Slot = Opt("slot") ?? -1,
                            Position = (bool?)o["guard"] == true ? Position.Guard : Position.Attack };
                    case "trap": return new SetTrap { Player = player, CardId = (int)o["card"], Slot = Opt("slot") ?? -1 };
                    case "technique": return new UseTechnique { Player = player, CardId = (int)o["card"], TargetId = Opt("target") };
                    case "field": return new PlayField { Player = player, CardId = (int)o["card"] };
                    case "attack": return new Attack { Player = player, AttackerId = (int)o["attacker"], TargetId = Opt("target") };
                    case "position":
                        return new ChangePosition { Player = player, CreatureId = (int)o["creature"],
                            To = o["guard"] == null ? (Position?)null : (bool)o["guard"] ? Position.Guard : Position.Attack };
                    case "end": return new EndTurn { Player = player };
                    default: return null;
                }
            }
            catch (Exception) { return null; }
        }
    }
}
