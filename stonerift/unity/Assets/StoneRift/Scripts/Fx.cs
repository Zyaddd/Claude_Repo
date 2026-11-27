using System.Collections;
using UnityEngine;

namespace StoneRift.App
{
    /// <summary>Particle bursts, floating numbers and simple synthesised sounds. No asset files needed.</summary>
    public static class Fx
    {
        public static void Burst(Vector3 pos, Color color, float scale = 1f, int count = 40)
        {
            var go = new GameObject("Burst");
            go.transform.position = pos;
            var ps = go.AddComponent<ParticleSystem>();
            ps.Stop(true, ParticleSystemStopBehavior.StopEmittingAndClear);

            var main = ps.main;
            main.duration = 0.5f;
            main.loop = false;
            main.startLifetime = new ParticleSystem.MinMaxCurve(0.35f, 0.8f);
            main.startSpeed = new ParticleSystem.MinMaxCurve(0.08f * scale, 0.35f * scale);
            main.startSize = new ParticleSystem.MinMaxCurve(0.004f * scale, 0.012f * scale);
            main.startColor = new ParticleSystem.MinMaxGradient(color, Color.Lerp(color, Color.white, 0.6f));
            main.gravityModifier = 0.15f;
            main.simulationSpace = ParticleSystemSimulationSpace.World;
            main.stopAction = ParticleSystemStopAction.Destroy;

            var emission = ps.emission;
            emission.rateOverTime = 0;
            emission.SetBursts(new[] { new ParticleSystem.Burst(0f, (short)count) });

            var shape = ps.shape;
            shape.shapeType = ParticleSystemShapeType.Sphere;
            shape.radius = 0.01f * scale;

            var col = ps.colorOverLifetime;
            col.enabled = true;
            var g = new Gradient();
            g.SetKeys(new[] { new GradientColorKey(Color.white, 0), new GradientColorKey(Color.white, 1) },
                      new[] { new GradientAlphaKey(1, 0), new GradientAlphaKey(0, 1) });
            col.color = g;

            var r = go.GetComponent<ParticleSystemRenderer>();
            r.sharedMaterial = FactionVisuals.Additive;
            ps.Play();
        }

        public static void FloatingText(MonoBehaviour host, Vector3 pos, string text, Color color, float size = 1f)
        {
            var go = new GameObject("FloatingText");
            go.transform.position = pos;
            var tm = go.AddComponent<TextMesh>();
            tm.font = Fonts.Default;
            go.GetComponent<MeshRenderer>().sharedMaterial = Fonts.Default.material;
            tm.text = StoneRift.Core.ArabicShaper.Fix(text);
            tm.fontSize = 64;
            tm.characterSize = 0.003f * size;
            tm.anchor = TextAnchor.MiddleCenter;
            tm.color = color;
            host.StartCoroutine(Rise(go.transform, tm, color));
        }

        static IEnumerator Rise(Transform t, TextMesh tm, Color color)
        {
            float start = Time.time;
            Vector3 p0 = t.position;
            while (Time.time - start < 1.2f)
            {
                float k = (Time.time - start) / 1.2f;
                t.position = p0 + Vector3.up * (0.06f * k);
                var cam = Camera.main;
                if (cam) t.rotation = Quaternion.LookRotation(t.position - cam.transform.position, cam.transform.up);
                tm.color = new Color(color.r, color.g, color.b, 1f - k * k);
                yield return null;
            }
            Object.Destroy(t.gameObject);
        }
    }

    /// <summary>Tiny synthesiser so the game has sound before real audio is made.</summary>
    public sealed class Sfx : MonoBehaviour
    {
        public enum Kind { Summon, Attack, Hit, Destroy, Trap, Technique, Win, Lose, Click }

        AudioSource _source;
        readonly System.Collections.Generic.Dictionary<Kind, AudioClip> _clips = new System.Collections.Generic.Dictionary<Kind, AudioClip>();
        public static Sfx Instance { get; private set; }

        void Awake()
        {
            Instance = this;
            _source = gameObject.AddComponent<AudioSource>();
            _source.playOnAwake = false;
        }

        public static void Play(Kind k) { if (Instance) Instance.PlayClip(k); }

        void PlayClip(Kind k)
        {
            if (!_clips.TryGetValue(k, out var clip)) _clips[k] = clip = Make(k);
            _source.PlayOneShot(clip, 0.8f);
        }

        static AudioClip Make(Kind k)
        {
            const int rate = 22050;
            float len;
            switch (k)
            {
                case Kind.Win: case Kind.Lose: len = 0.9f; break;
                case Kind.Click: len = 0.05f; break;
                default: len = 0.35f; break;
            }
            int n = (int)(rate * len);
            var data = new float[n];
            var rng = new System.Random(1);
            for (int i = 0; i < n; i++)
            {
                float t = i / (float)rate, u = t / len;
                float env = Mathf.Clamp01(t * 60f) * (1f - u);
                float s;
                switch (k)
                {
                    case Kind.Summon: s = Mathf.Sin(2 * Mathf.PI * (220 + 600 * u) * t); break;
                    case Kind.Attack: s = ((float)rng.NextDouble() * 2 - 1) * Mathf.Sin(Mathf.PI * u) * 0.8f; break;
                    case Kind.Hit: s = Mathf.Sin(2 * Mathf.PI * (120 - 60 * u) * t) + ((float)rng.NextDouble() - 0.5f) * (1 - u); break;
                    case Kind.Destroy: s = ((float)rng.NextDouble() * 2 - 1) * (1 - u) + Mathf.Sin(2 * Mathf.PI * 70 * t) * 0.6f; break;
                    case Kind.Trap: s = Mathf.Sin(2 * Mathf.PI * (u < 0.5f ? 660 : 440) * t) * 0.6f; break;
                    case Kind.Technique: s = Mathf.Sin(2 * Mathf.PI * 880 * t + Mathf.Sin(2 * Mathf.PI * 7 * t) * 4) * 0.5f; break;
                    case Kind.Win: s = Mathf.Sin(2 * Mathf.PI * (u < 0.33f ? 523 : u < 0.66f ? 659 : 784) * t) * 0.5f; break;
                    case Kind.Lose: s = Mathf.Sin(2 * Mathf.PI * (u < 0.33f ? 392 : u < 0.66f ? 311 : 262) * t) * 0.5f; break;
                    default: s = Mathf.Sin(2 * Mathf.PI * 1200 * t) * 0.4f; break;
                }
                data[i] = s * env * 0.6f;
            }
            var clip = AudioClip.Create(k.ToString(), n, 1, rate, false);
            clip.SetData(data, 0);
            return clip;
        }
    }
}
