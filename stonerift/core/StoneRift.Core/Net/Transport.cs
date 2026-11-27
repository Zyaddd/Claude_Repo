using System;
using System.Collections.Concurrent;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;

namespace StoneRift.Core.Net
{
    /// <summary>
    /// A line-based connection to the other phone. Lines can arrive on any thread;
    /// the game reads them on its own thread with TryReceive.
    /// </summary>
    public interface ITransport : IDisposable
    {
        bool IsOpen { get; }
        void Send(string line);
        bool TryReceive(out string line);
        void Close();
    }

    /// <summary>Two connected in-memory ends, for tests and for playing against yourself.</summary>
    public sealed class LoopbackTransport : ITransport
    {
        readonly ConcurrentQueue<string> _inbox = new ConcurrentQueue<string>();
        LoopbackTransport _peer;
        volatile bool _open = true;

        public static (LoopbackTransport a, LoopbackTransport b) CreatePair()
        {
            var a = new LoopbackTransport();
            var b = new LoopbackTransport();
            a._peer = b;
            b._peer = a;
            return (a, b);
        }

        public bool IsOpen => _open;
        public void Send(string line) { if (_open && _peer._open) _peer._inbox.Enqueue(line); }
        public bool TryReceive(out string line) => _inbox.TryDequeue(out line);
        public void Close() { _open = false; if (_peer != null) _peer._open = false; }
        public void Dispose() => Close();
    }

    /// <summary>One TCP connection. A background thread reads lines into a queue.</summary>
    public sealed class TcpTransport : ITransport
    {
        readonly TcpClient _client;
        readonly NetworkStream _stream;
        readonly StreamWriter _writer;
        readonly ConcurrentQueue<string> _inbox = new ConcurrentQueue<string>();
        readonly object _sendLock = new object();
        volatile bool _open = true;

        public TcpTransport(TcpClient client)
        {
            _client = client;
            _client.NoDelay = true; // Small messages; send them at once.
            _stream = client.GetStream();
            _writer = new StreamWriter(_stream, new UTF8Encoding(false)) { AutoFlush = true, NewLine = "\n" };
            var reader = new Thread(ReadLoop) { IsBackground = true, Name = "StoneRift TCP read" };
            reader.Start();
        }

        /// <summary>Connects to a host; throws on failure or timeout.</summary>
        public static TcpTransport Connect(string host, int port, int timeoutMs = 4000)
        {
            var client = new TcpClient();
            var task = client.ConnectAsync(host, port);
            if (!task.Wait(timeoutMs) || !client.Connected)
            {
                client.Close();
                throw new IOException($"Could not reach {host}:{port}");
            }
            return new TcpTransport(client);
        }

        void ReadLoop()
        {
            try
            {
                using (var reader = new StreamReader(_stream, new UTF8Encoding(false)))
                {
                    string line;
                    while (_open && (line = reader.ReadLine()) != null)
                    {
                        if (line.Length > 1_000_000) break; // Nothing legitimate is this large.
                        _inbox.Enqueue(line);
                    }
                }
            }
            catch (Exception) { /* Connection dropped. */ }
            _open = false;
        }

        public bool IsOpen => _open;

        public void Send(string line)
        {
            if (!_open) return;
            try { lock (_sendLock) _writer.WriteLine(line); }
            catch (Exception) { _open = false; }
        }

        public bool TryReceive(out string line) => _inbox.TryDequeue(out line);

        public void Close()
        {
            _open = false;
            try { _client.Close(); } catch (Exception) { }
        }

        public void Dispose() => Close();
    }

    /// <summary>Listens for the guest's phone. Keeps listening so a dropped guest can reconnect.</summary>
    public sealed class TcpHost : IDisposable
    {
        readonly TcpListener _listener;
        readonly ConcurrentQueue<TcpTransport> _accepted = new ConcurrentQueue<TcpTransport>();
        volatile bool _running = true;

        public int Port { get; }

        public TcpHost(int port = Protocol.DefaultPort)
        {
            _listener = new TcpListener(IPAddress.Any, port);
            _listener.Start();
            Port = ((IPEndPoint)_listener.LocalEndpoint).Port;
            new Thread(AcceptLoop) { IsBackground = true, Name = "StoneRift TCP accept" }.Start();
        }

        void AcceptLoop()
        {
            while (_running)
            {
                try { _accepted.Enqueue(new TcpTransport(_listener.AcceptTcpClient())); }
                catch (Exception) { if (!_running) return; Thread.Sleep(100); }
            }
        }

        public bool TryAccept(out TcpTransport transport) => _accepted.TryDequeue(out transport);

        public void Dispose()
        {
            _running = false;
            try { _listener.Stop(); } catch (Exception) { }
        }
    }
}
