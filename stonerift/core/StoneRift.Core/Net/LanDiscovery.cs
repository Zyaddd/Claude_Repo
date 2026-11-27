using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using Newtonsoft.Json;

namespace StoneRift.Core.Net
{
    /// <summary>A match someone is hosting on the same Wi-Fi.</summary>
    public sealed class FoundHost
    {
        public string Name;
        public string Address;
        public int Port;
        public DateTime LastSeenUtc;
        public override string ToString() => $"{Name} ({Address}:{Port})";
    }

    /// <summary>What a host announces on the network.</summary>
    sealed class BeaconInfo
    {
        public string Game { get; set; }
        public int V { get; set; }
        public string Name { get; set; }
        public int Port { get; set; }
    }

    /// <summary>
    /// The host phone announces itself on the local network once a second; other phones listen
    /// and show it in a list, so nobody has to type an IP address.
    /// </summary>
    public sealed class LanBeacon : IDisposable
    {
        readonly UdpClient _udp;
        readonly byte[] _payload;
        readonly Timer _timer;
        readonly int _discoveryPort;

        public LanBeacon(string hostName, int gamePort, int discoveryPort = Protocol.DiscoveryPort)
        {
            _discoveryPort = discoveryPort;
            _udp = new UdpClient { EnableBroadcast = true };
            _payload = Encoding.UTF8.GetBytes(JsonConvert.SerializeObject(new BeaconInfo { Game = "StoneRift", V = Protocol.Version, Name = hostName, Port = gamePort }));
            _timer = new Timer(_ => Announce(), null, 0, 1000);
        }

        void Announce()
        {
            foreach (var target in BroadcastAddresses())
            {
                try { _udp.Send(_payload, _payload.Length, new IPEndPoint(target, _discoveryPort)); }
                catch (Exception) { /* Some interfaces refuse broadcast; try the others. */ }
            }
        }

        /// <summary>The general broadcast address plus each Wi-Fi/hotspot subnet's own broadcast address.</summary>
        static IEnumerable<IPAddress> BroadcastAddresses()
        {
            yield return IPAddress.Broadcast;
            NetworkInterface[] nics;
            try { nics = NetworkInterface.GetAllNetworkInterfaces(); }
            catch (Exception) { yield break; }
            foreach (var nic in nics)
            {
                if (nic.OperationalStatus != OperationalStatus.Up) continue;
                UnicastIPAddressInformationCollection addresses;
                try { addresses = nic.GetIPProperties().UnicastAddresses; }
                catch (Exception) { continue; }
                foreach (var a in addresses)
                {
                    if (a.Address.AddressFamily != AddressFamily.InterNetwork || IPAddress.IsLoopback(a.Address) || a.IPv4Mask == null) continue;
                    var ip = a.Address.GetAddressBytes();
                    var mask = a.IPv4Mask.GetAddressBytes();
                    var b = new byte[4];
                    for (int i = 0; i < 4; i++) b[i] = (byte)(ip[i] | ~mask[i]);
                    yield return new IPAddress(b);
                }
            }
        }

        public void Dispose()
        {
            _timer.Dispose();
            _udp.Close();
        }
    }

    /// <summary>Listens for hosts' announcements.</summary>
    public sealed class LanBrowser : IDisposable
    {
        readonly UdpClient _udp;
        readonly Dictionary<string, FoundHost> _hosts = new Dictionary<string, FoundHost>();
        readonly object _lock = new object();
        volatile bool _running = true;

        public LanBrowser(int discoveryPort = Protocol.DiscoveryPort)
        {
            _udp = new UdpClient();
            _udp.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
            _udp.Client.Bind(new IPEndPoint(IPAddress.Any, discoveryPort));
            new Thread(Listen) { IsBackground = true, Name = "StoneRift discovery" }.Start();
        }

        void Listen()
        {
            while (_running)
            {
                try
                {
                    var from = new IPEndPoint(IPAddress.Any, 0);
                    var data = _udp.Receive(ref from);
                    var b = JsonConvert.DeserializeObject<BeaconInfo>(Encoding.UTF8.GetString(data));
                    if (b?.Game != "StoneRift" || b.V != Protocol.Version || b.Port <= 0) continue;
                    var key = from.Address + ":" + b.Port;
                    lock (_lock)
                        _hosts[key] = new FoundHost { Name = b.Name ?? "?", Address = from.Address.ToString(), Port = b.Port, LastSeenUtc = DateTime.UtcNow };
                }
                catch (Exception) { if (!_running) return; }
            }
        }

        /// <summary>Hosts heard from in the last few seconds.</summary>
        public List<FoundHost> Hosts
        {
            get
            {
                lock (_lock)
                {
                    var cutoff = DateTime.UtcNow.AddSeconds(-4);
                    return _hosts.Values.Where(h => h.LastSeenUtc > cutoff).OrderBy(h => h.Name).ToList();
                }
            }
        }

        public void Dispose()
        {
            _running = false;
            _udp.Close();
        }
    }
}

namespace StoneRift.Core.Net
{
    public static class NetInfo
    {
        /// <summary>This device's IPv4 addresses on Wi-Fi or hotspot, to show the host's address on screen.</summary>
        public static List<string> LocalIPv4Addresses()
        {
            var result = new List<string>();
            try
            {
                foreach (var nic in NetworkInterface.GetAllNetworkInterfaces())
                {
                    if (nic.OperationalStatus != OperationalStatus.Up) continue;
                    foreach (var a in nic.GetIPProperties().UnicastAddresses)
                        if (a.Address.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(a.Address))
                            result.Add(a.Address.ToString());
                }
            }
            catch (Exception) { /* Not allowed on some phones; fall back below. */ }

            if (result.Count == 0)
            {
                // Asking the system which local address it would use for an outside address sends no packets.
                try
                {
                    using (var s = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp))
                    {
                        s.Connect("10.255.255.255", 9);
                        if (s.LocalEndPoint is IPEndPoint ep && !IPAddress.IsLoopback(ep.Address)) result.Add(ep.Address.ToString());
                    }
                }
                catch (Exception) { }
            }
            return result.Distinct().ToList();
        }
    }
}
