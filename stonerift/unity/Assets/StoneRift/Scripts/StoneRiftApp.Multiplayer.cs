using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using StoneRift.Core;
using StoneRift.Core.Net;
using UnityEngine;

namespace StoneRift.App
{
    /// <summary>
    /// Playing with a friend on the same Wi-Fi or phone hotspot:
    /// one phone hosts, the other finds it in a list (or types its address) and joins.
    /// Each phone places the arena on the table itself and sees the match from its own side.
    /// </summary>
    public sealed partial class StoneRiftApp
    {
        MatchSession _session;
        TcpHost _tcpHost;
        LanBeacon _beacon;
        LanBrowser _browser;
        Task<TcpTransport> _connecting;
        string _hostAddress;
        int _hostPort;
        float _nextReconnect;
        string _lobbyStatus = "";
        string _manualAddress = "";
        string _playerName;
        Vector2 _lobbyScroll;
#if UNITY_ANDROID && !UNITY_EDITOR
        AndroidJavaObject _multicastLock;
#endif

        const string NameKey = "player_name";

        Dictionary<string, int> DeckCards(string id) => new Dictionary<string, int>(_db.GetDeck(id).Cards);

        void OpenLobby()
        {
            if (string.IsNullOrEmpty(_playerName)) _playerName = PlayerPrefs.GetString(NameKey, "لاعب");
            _mode = Mode.Lobby;
            _lobbyStatus = "";
            AcquireMulticastLock();
            try { if (_browser == null) _browser = new LanBrowser(); }
            catch (Exception e)
            {
                _lobbyStatus = "مش قادر أدوّر على ماتشات قريبة. اكتب عنوان صاحبك بدل كده.";
                Debug.LogWarning("[StoneRift] Discovery unavailable: " + e.Message);
            }
        }

        void HostMatch()
        {
            SaveName();
            EndSession();
            try { _tcpHost = new TcpHost(Protocol.DefaultPort); }
            catch (Exception) { _tcpHost = new TcpHost(0); } // Default port busy: let the system choose.
            try { _beacon = new LanBeacon(_playerName, _tcpHost.Port); }
            catch (Exception e) { Debug.LogWarning("[StoneRift] Could not announce the match: " + e.Message); }

            _session = MatchSession.CreateHost(_db, _playerName, DeckCards(_playerDeck), Environment.TickCount);
            Subscribe(_session);
            _lobbyStatus = "مستني صاحبك يدخل…";
        }

        void JoinMatch(string address, int port)
        {
            SaveName();
            if (_session == null || _session.IsHost || _session.State == SessionState.Failed)
            {
                EndSession();
                _session = MatchSession.CreateGuest(_db, _playerName, DeckCards(_playerDeck));
                Subscribe(_session);
            }
            _hostAddress = address;
            _hostPort = port;
            _lobbyStatus = $"بتصل بـ {address}…";
            Connect();
        }

        void Connect()
        {
            string address = _hostAddress;
            int port = _hostPort;
            _connecting = Task.Run(() => TcpTransport.Connect(address, port));
        }

        void Subscribe(MatchSession s)
        {
            s.Started += () =>
            {
                _game = s.Game;
                _beacon?.Dispose();
                _beacon = null;
                BeginPlaying();
                Say($"إنت ضد {s.OpponentName}. " + (_game.ActivePlayer == Me ? "العملة وقعت لصالحك، ابدأ!" : s.OpponentName + " هيبدأ"));
            };
            s.ActionApplied += (_, events) => _eventQueue.Enqueue(events);
            s.Rebuilt += () =>
            {
                _game = s.Game;
                _eventQueue.Clear();
                _needsSync = true;
                Say("رجعنا! الماتش متزامن");
            };
            s.RequestRejected += e => Say(ErrorText(e));
        }

        /// <summary>Runs every frame: accepts connections, finishes connection attempts, reads messages, retries after a drop.</summary>
        void UpdateNetwork()
        {
            if (_session == null) return;

            if (_tcpHost != null && _tcpHost.TryAccept(out var incoming)) _session.Attach(incoming);

            if (_connecting != null && _connecting.IsCompleted)
            {
                if (_connecting.Status == TaskStatus.RanToCompletion) _session.Attach(_connecting.Result);
                else if (_mode == Mode.Lobby) _lobbyStatus = "معرفتش أوصل للماتش. اتأكد إنكم على نفس الواي فاي.";
                _connecting = null;
            }

            _session.Poll();

            // The guest keeps trying to get back into a dropped match.
            if (!_session.IsHost && _session.State == SessionState.Reconnecting && _connecting == null &&
                _hostAddress != null && Time.time >= _nextReconnect)
            {
                _nextReconnect = Time.time + 3f;
                Connect();
            }

            if (_session.State == SessionState.Failed && _mode == Mode.Lobby) _lobbyStatus = _session.FailureReason ?? "معرفتش أدخل الماتش";
        }

        void SaveName()
        {
            _playerName = string.IsNullOrWhiteSpace(_playerName) ? "لاعب" : _playerName.Trim();
            PlayerPrefs.SetString(NameKey, _playerName);
        }

        /// <summary>Closes the match and everything networked.</summary>
        void EndSession()
        {
            _session?.Dispose();
            _session = null;
            _tcpHost?.Dispose();
            _tcpHost = null;
            _beacon?.Dispose();
            _beacon = null;
            _connecting = null;
            _hostAddress = null;
        }

        void BackToMenu()
        {
            StopAllCoroutines();
            EndSession();
            _browser?.Dispose();
            _browser = null;
            ReleaseMulticastLock();
            _game = null;
            _busy = _botRunning = false;
            _eventQueue.Clear();
            _mode = Mode.Menu;
            _placer.ResetPlacement();
            _placer.enabled = false;
        }

        void OnDestroy()
        {
            EndSession();
            _browser?.Dispose();
            ReleaseMulticastLock();
        }

        // ------------------------------------------------------------------ Android: receive broadcasts

        /// <summary>Many phones drop broadcast packets to save battery unless an app asks for them.</summary>
        void AcquireMulticastLock()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            if (_multicastLock != null) return;
            try
            {
                var activity = new AndroidJavaClass("com.unity3d.player.UnityPlayer").GetStatic<AndroidJavaObject>("currentActivity");
                var context = activity.Call<AndroidJavaObject>("getApplicationContext");
                var wifi = context.Call<AndroidJavaObject>("getSystemService", "wifi");
                _multicastLock = wifi.Call<AndroidJavaObject>("createMulticastLock", "stonerift");
                _multicastLock.Call("acquire");
            }
            catch (Exception e) { Debug.LogWarning("[StoneRift] Multicast lock: " + e.Message); }
#endif
        }

        void ReleaseMulticastLock()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try { _multicastLock?.Call("release"); } catch (Exception) { }
            _multicastLock = null;
#endif
        }

        // ------------------------------------------------------------------ screens

        void DrawLobby()
        {
            float w = Mathf.Min(VW - 40, 560), x = (VW - w) / 2, y = 60;
            GUI.Box(new Rect(0, 0, VW, VirtualHeight), GUIContent.none, _box);
            GUI.Label(new Rect(x, y, w, 50), T("العب مع صاحبك"), _big);
            y += 60;
            GUI.Label(new Rect(x, y, w, 50), T("لازم تكونوا على نفس الواي فاي، أو واحد فيكم يشغّل هوت سبوت والتاني يدخل عليه."), _label);
            y += 70;

            GUI.Label(new Rect(x, y, w * 0.35f, 50), T("اسمك:"), _label);
            _playerName = GUI.TextField(new Rect(x + w * 0.35f, y, w * 0.65f, 50), _playerName ?? "", 20, _button);
            y += 60;
            GUI.Label(new Rect(x, y, w, 40), T("الديك: " + _db.GetDeck(_playerDeck).NameAr), _label);
            y += 55;

            bool hosting = _session != null && _session.IsHost;
            if (hosting)
            {
                var addresses = NetInfo.LocalIPv4Addresses();
                string addr = addresses.Count > 0 ? string.Join("  أو  ", addresses.Select(a => a + ":" + _tcpHost.Port)) : "?";
                GUI.Label(new Rect(x, y, w, 50), T("الماتش بتاعك ظاهر لصحابك القريبين."), _label);
                y += 50;
                GUI.Label(new Rect(x, y, w, 60), T("لو مش ظاهر عندهم، يكتبوا العنوان ده:") + "\n" + addr, _label);
                y += 80;
            }
            else
            {
                GUI.backgroundColor = new Color(0.45f, 1f, 0.55f);
                if (GUI.Button(new Rect(x, y, w, 75), T("اعمل ماتش وخلّي صاحبك يدخل"), _button)) HostMatch();
                GUI.backgroundColor = Color.white;
                y += 90;

                GUI.Label(new Rect(x, y, w, 40), T("أو ادخل ماتش قريب:"), _label);
                y += 45;
                var hosts = _browser?.Hosts ?? new List<FoundHost>();
                float listH = 170;
                _lobbyScroll = GUI.BeginScrollView(new Rect(x, y, w, listH), _lobbyScroll, new Rect(0, 0, w - 20, Mathf.Max(listH, hosts.Count * 70)));
                if (hosts.Count == 0) GUI.Label(new Rect(0, 0, w - 20, 60), T("بدوّر…"), _label);
                for (int i = 0; i < hosts.Count; i++)
                    if (GUI.Button(new Rect(0, i * 70, w - 20, 62), T("ادخل ماتش " + hosts[i].Name), _button))
                        JoinMatch(hosts[i].Address, hosts[i].Port);
                GUI.EndScrollView();
                y += listH + 10;

                GUI.Label(new Rect(x, y, w, 40), T("أو اكتب العنوان اللي ظاهر عند صاحبك:"), _label);
                y += 45;
                _manualAddress = GUI.TextField(new Rect(x, y, w * 0.68f, 60), _manualAddress, 40, _button);
                if (GUI.Button(new Rect(x + w * 0.7f, y, w * 0.3f, 60), T("ادخل"), _button) && TryParseAddress(_manualAddress, out var host, out var port))
                    JoinMatch(host, port);
                y += 75;
            }

            if (!string.IsNullOrEmpty(_lobbyStatus)) GUI.Label(new Rect(x, y, w, 70), T(_lobbyStatus), _label);

            if (GUI.Button(new Rect(x, VirtualHeight - 95, w, 70), T(hosting ? "إلغاء الماتش" : "رجوع"), _button))
            {
                if (hosting) { EndSession(); _lobbyStatus = ""; }
                else BackToMenu();
            }
        }

        static bool TryParseAddress(string text, out string host, out int port)
        {
            host = null;
            port = Protocol.DefaultPort;
            if (string.IsNullOrWhiteSpace(text)) return false;
            var parts = text.Trim().Split(':');
            host = parts[0].Trim();
            if (parts.Length > 1 && !int.TryParse(parts[1], out port)) return false;
            return System.Net.IPAddress.TryParse(host, out _);
        }

        /// <summary>Shown over the match while the friend is away. Returns true when it covers the controls.</summary>
        bool DrawConnectionBanner()
        {
            if (_session == null || _session.State == SessionState.Playing) return false;
            float w = Mathf.Min(VW - 40, 520), x = (VW - w) / 2;
            var r = Ui(new Rect(x, VirtualHeight - 300, w, 220));
            GUI.Box(r, GUIContent.none, _box);
            string text;
            switch (_session.State)
            {
                case SessionState.Reconnecting:
                    text = _session.IsHost ? $"الاتصال بـ {_session.OpponentName} اتقطع. الماتش محفوظ، مستنيينه يرجع…"
                                           : "الاتصال اتقطع. الماتش محفوظ، بحاول أرجع…";
                    break;
                case SessionState.Ended: text = $"{OpponentName} خرج من الماتش"; break;
                case SessionState.Failed: text = _session.FailureReason ?? "حصلت مشكلة في الاتصال"; break;
                default: text = "بتصل…"; break;
            }
            GUI.Label(new Rect(x + 10, r.y + 15, w - 20, 110), T(text), _label);
            if (GUI.Button(new Rect(x + 30, r.y + 135, w - 60, 65), T("اخرج من الماتش"), _button)) BackToMenu();
            return true;
        }
    }
}
