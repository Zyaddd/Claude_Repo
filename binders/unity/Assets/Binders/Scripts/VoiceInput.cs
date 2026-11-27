using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using UnityEngine;
#if UNITY_ANDROID
using UnityEngine.Android;
#endif

namespace Binders.App
{
    /// <summary>
    /// Push-to-talk speech recognition using Android's built-in recogniser (free, no server of ours).
    /// Results arrive as several guesses, best first.
    /// </summary>
    public sealed class VoiceInput : MonoBehaviour
    {
        public string Language = "ar-EG";
        public bool Listening { get; private set; }
        public string Status { get; private set; } = "";
        public event Action<List<string>> Heard;

        readonly ConcurrentQueue<Action> _mainThread = new ConcurrentQueue<Action>();

#if UNITY_ANDROID && !UNITY_EDITOR
        AndroidJavaObject _activity;
        AndroidJavaObject _recognizer;
#endif

        void Awake()
        {
            // Referencing the microphone makes Unity add the RECORD_AUDIO permission to the app.
            _ = Microphone.devices;
        }

        public static bool Supported =>
#if UNITY_ANDROID && !UNITY_EDITOR
            true;
#else
            false;
#endif

        public void StartListening()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            if (!Permission.HasUserAuthorizedPermission(Permission.Microphone))
            {
                var callbacks = new PermissionCallbacks();
                callbacks.PermissionGranted += _ => _mainThread.Enqueue(StartListening);
                callbacks.PermissionDenied += _ => _mainThread.Enqueue(() => Status = "محتاج إذن المايك علشان الأوامر الصوتية");
                Permission.RequestUserPermission(Permission.Microphone, callbacks);
                return;
            }
            if (Listening) return;
            Listening = true;
            Status = "بسمعك…";
            try
            {
                if (_activity == null)
                    _activity = new AndroidJavaClass("com.unity3d.player.UnityPlayer").GetStatic<AndroidJavaObject>("currentActivity");
                _activity.Call("runOnUiThread", new AndroidJavaRunnable(StartOnUiThread));
            }
            catch (Exception e)
            {
                Listening = false;
                Status = "التعرف على الصوت مش شغال: " + e.Message;
            }
#else
            Status = "الصوت شغال على الموبايل بس. اكتب الأمر بدل كده.";
#endif
        }

#if UNITY_ANDROID && !UNITY_EDITOR
        void StartOnUiThread()
        {
            try
            {
                if (_recognizer == null)
                {
                    var sr = new AndroidJavaClass("android.speech.SpeechRecognizer");
                    if (!sr.CallStatic<bool>("isRecognitionAvailable", _activity))
                    {
                        _mainThread.Enqueue(() => { Listening = false; Status = "الموبايل مفيهوش خدمة تعرف على الصوت (Google)"; });
                        return;
                    }
                    _recognizer = sr.CallStatic<AndroidJavaObject>("createSpeechRecognizer", _activity);
                    _recognizer.Call("setRecognitionListener", new Listener(this));
                }
                var intent = new AndroidJavaObject("android.content.Intent", "android.speech.action.RECOGNIZE_SPEECH");
                intent.Call<AndroidJavaObject>("putExtra", "android.speech.extra.LANGUAGE_MODEL", "free_form");
                intent.Call<AndroidJavaObject>("putExtra", "android.speech.extra.LANGUAGE", Language);
                intent.Call<AndroidJavaObject>("putExtra", "android.speech.extra.LANGUAGE_PREFERENCE", Language);
                intent.Call<AndroidJavaObject>("putExtra", "android.speech.extra.MAX_RESULTS", 5);
                _recognizer.Call("startListening", intent);
            }
            catch (Exception e)
            {
                _mainThread.Enqueue(() => { Listening = false; Status = "خطأ في الصوت: " + e.Message; });
            }
        }

        /// <summary>Receives Android's RecognitionListener callbacks (on the Android UI thread).</summary>
        sealed class Listener : AndroidJavaProxy
        {
            readonly VoiceInput _owner;
            public Listener(VoiceInput owner) : base("android.speech.RecognitionListener") { _owner = owner; }

            public override AndroidJavaObject Invoke(string methodName, AndroidJavaObject[] args)
            {
                switch (methodName)
                {
                    case "onResults":
                    {
                        var results = new List<string>();
                        try
                        {
                            var list = args[0].Call<AndroidJavaObject>("getStringArrayList", "results_recognition");
                            if (list != null)
                            {
                                int n = list.Call<int>("size");
                                for (int i = 0; i < n; i++) results.Add(list.Call<string>("get", i));
                            }
                        }
                        catch (Exception) { }
                        _owner._mainThread.Enqueue(() =>
                        {
                            _owner.Listening = false;
                            _owner.Status = results.Count > 0 ? "سمعت: " + results[0] : "مسمعتش حاجة";
                            if (results.Count > 0) _owner.Heard?.Invoke(results);
                        });
                        return null;
                    }
                    case "onError":
                    {
                        int code = 0;
                        try { code = args[0].Call<int>("intValue"); } catch (Exception) { }
                        _owner._mainThread.Enqueue(() => { _owner.Listening = false; _owner.Status = ErrorText(code); });
                        return null;
                    }
                    case "onReadyForSpeech":
                        _owner._mainThread.Enqueue(() => _owner.Status = "اتكلم دلوقتي…");
                        return null;
                    case "onEndOfSpeech":
                        _owner._mainThread.Enqueue(() => _owner.Status = "بفكّر…");
                        return null;
                    case "onBeginningOfSpeech":
                    case "onRmsChanged":
                    case "onBufferReceived":
                    case "onPartialResults":
                    case "onEvent":
                    case "onSegmentResults":
                    case "onEndOfSegmentedSession":
                    case "onLanguageDetection":
                        return null;
                    default:
                        return base.Invoke(methodName, args);
                }
            }
        }

        static string ErrorText(int code)
        {
            switch (code)
            {
                case 7: return "مسمعتش أمر واضح، جرّب تاني"; // ERROR_NO_MATCH
                case 6: return "مسمعتش حاجة، اضغط واتكلم";   // ERROR_SPEECH_TIMEOUT
                case 2: return "التعرف على الصوت محتاج نت";    // ERROR_NETWORK
                case 9: return "محتاج إذن المايك";             // ERROR_INSUFFICIENT_PERMISSIONS
                case 8: return "المايك مشغول، استنى ثانية";     // ERROR_RECOGNIZER_BUSY
                default: return "مشكلة في الصوت (" + code + ")";
            }
        }
#endif

        void Update()
        {
            while (_mainThread.TryDequeue(out var a)) a();
        }

        void OnDestroy()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            if (_recognizer != null && _activity != null)
            {
                var r = _recognizer;
                _activity.Call("runOnUiThread", new AndroidJavaRunnable(() => r.Call("destroy")));
            }
#endif
        }
    }
}
