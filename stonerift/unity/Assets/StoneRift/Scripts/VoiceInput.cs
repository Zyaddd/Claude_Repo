using System;
using System.Collections.Generic;
using System.Linq;
using UnityEngine;
using Whisper;
#if UNITY_ANDROID
using UnityEngine.Android;
#endif

namespace StoneRift.App
{
    /// <summary>
    /// Push-to-talk voice commands recognised on the phone itself with Whisper (whisper.cpp, MIT licence).
    /// No Google services, no internet, and no audio ever leaves the device.
    /// Tap once to start talking, tap again when done (it also stops by itself after a few seconds).
    /// </summary>
    public sealed class VoiceInput : MonoBehaviour
    {
        /// <summary>Model file under StreamingAssets. The build downloads it; it is not kept in git.</summary>
        public const string ModelFile = "Whisper/ggml-base-q5_1.bin";
        const int SampleRate = 16000;
        const int MaxSeconds = 5;

        public bool Listening { get; private set; }
        public bool Thinking { get; private set; }
        public string Status { get; private set; } = "";
        public bool Ready => _whisper != null && _whisper.IsLoaded;
        public event Action<List<string>> Heard;

        WhisperManager _whisper;
        AudioClip _clip;
        string _device;
        float _startedAt;

        /// <summary>
        /// Loads the model. The prompt lists the words players will say (construct names and commands),
        /// which steers Whisper towards them.
        /// </summary>
        public void Init(IEnumerable<string> vocabulary)
        {
            var go = new GameObject("Whisper");
            go.SetActive(false); // Configure before the manager's Awake starts loading.
            go.transform.SetParent(transform, false);
            _whisper = go.AddComponent<WhisperManager>();
            _whisper.ModelPath = ModelFile;
            _whisper.IsModelPathInStreamingAssets = true;
            _whisper.language = "ar";
            _whisper.translateToEnglish = false;
            _whisper.noContext = true;
            _whisper.singleSegment = true;
            _whisper.initialPrompt = "أوامر لعبة: انزل، استدعي، استخدم، حط الفخ، العب، اهجم، هجوم، دفاع، وضع الهجوم، وضع الدفاع، انهي الدور. "
                                     + string.Join("، ", vocabulary.Distinct().Take(60)) + ".";
            go.SetActive(true);
            Status = "بجهّز التعرف على الصوت…";
        }

        void Update()
        {
            if (!Listening && !Thinking && _whisper != null && !_whisper.IsLoading && Status == "بجهّز التعرف على الصوت…")
                Status = Ready ? "" : "ملف التعرف على الصوت مش موجود في النسخة دي";
            if (Listening && Time.time - _startedAt >= MaxSeconds) StopListening();
        }

        /// <summary>Starts recording, or stops and recognises if already recording.</summary>
        public void Toggle()
        {
            if (Listening) StopListening();
            else StartListening();
        }

        public void StartListening()
        {
            if (Thinking) return;
            if (!Ready)
            {
                Status = _whisper != null && _whisper.IsLoading ? "لسه بجهّز الصوت، ثانية واحدة…" : "التعرف على الصوت مش جاهز، استخدم «اكتب»";
                return;
            }
#if UNITY_ANDROID && !UNITY_EDITOR
            if (!Permission.HasUserAuthorizedPermission(Permission.Microphone))
            {
                var callbacks = new PermissionCallbacks();
                callbacks.PermissionGranted += _ => Status = "تمام، دوس «اتكلم» تاني";
                callbacks.PermissionDenied += _ => Status = "محتاج إذن المايك علشان الأوامر الصوتية";
                Permission.RequestUserPermission(Permission.Microphone, callbacks);
                return;
            }
#endif
            if (Microphone.devices.Length == 0) { Status = "مفيش مايك"; return; }
            _device = Microphone.devices[0];
            _clip = Microphone.Start(_device, false, MaxSeconds + 1, SampleRate);
            _startedAt = Time.time;
            Listening = true;
            Status = "بسمعك… دوس تاني لما تخلص";
        }

        public async void StopListening()
        {
            if (!Listening) return;
            int samples = Microphone.GetPosition(_device);
            Microphone.End(_device);
            Listening = false;

            if (_clip == null || samples < SampleRate / 3)
            {
                Status = "مسمعتش حاجة، دوس واتكلم";
                return;
            }

            var data = new float[samples * _clip.channels];
            _clip.GetData(data, 0);
            Thinking = true;
            Status = "بفكّر…";
            try
            {
                var result = await _whisper.GetTextAsync(data, _clip.frequency, _clip.channels);
                string text = result?.Result?.Trim() ?? "";
                // Whisper writes non-speech as bracketed tags like [music]; drop them.
                text = System.Text.RegularExpressions.Regex.Replace(text, @"\[[^\]]*\]|\([^)]*\)", "").Trim();
                Status = text.Length > 0 ? "سمعت: " + text : "مسمعتش أمر واضح، جرّب تاني";
                if (text.Length > 0) Heard?.Invoke(new List<string> { text });
            }
            catch (Exception e)
            {
                Status = "مشكلة في التعرف على الصوت";
                Debug.LogException(e);
            }
            finally
            {
                Thinking = false;
            }
        }
    }
}
