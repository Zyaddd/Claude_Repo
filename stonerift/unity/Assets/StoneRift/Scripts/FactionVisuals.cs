using System.Collections;
using StoneRift.Core;
using UnityEngine;

namespace StoneRift.App
{
    /// <summary>
    /// The look of each faction: placeholder construct shapes, the always-on aura, and attack effects.
    /// Glow comes from additive particles with a soft round texture made in code.
    /// Real 3D models replace the shapes later (Resources/Models/{ModelId}); auras and effects stay.
    /// </summary>
    public static class FactionVisuals
    {
        public static Color Main(Faction f)
        {
            switch (f)
            {
                case Faction.Magma: return new Color(1f, 0.42f, 0.08f);
                case Faction.Tide: return new Color(0.2f, 0.75f, 1f);
                case Faction.Volt: return new Color(1f, 0.9f, 0.25f);
                case Faction.Crystal: return new Color(0.85f, 0.7f, 1f);
                case Faction.Gravity: return new Color(0.6f, 0.3f, 1f);
                default: return new Color(0.8f, 0.8f, 0.8f);
            }
        }

        static Color Body(Faction f)
        {
            switch (f)
            {
                case Faction.Magma: return new Color(0.18f, 0.12f, 0.1f);
                case Faction.Tide: return new Color(0.15f, 0.45f, 0.7f, 0.55f);
                case Faction.Volt: return new Color(0.55f, 0.58f, 0.62f);
                case Faction.Crystal: return new Color(0.75f, 0.85f, 1f, 0.6f);
                case Faction.Gravity: return new Color(0.1f, 0.06f, 0.16f);
                default: return Color.gray;
            }
        }

        // ------------------------------------------------------------------ shared glow resources

        static Texture2D _glow;
        static Material _additive;

        /// <summary>White centre fading to transparent edges.</summary>
        public static Texture2D GlowTexture
        {
            get
            {
                if (_glow) return _glow;
                const int n = 64;
                _glow = new Texture2D(n, n, TextureFormat.RGBA32, false) { wrapMode = TextureWrapMode.Clamp };
                for (int y = 0; y < n; y++)
                    for (int x = 0; x < n; x++)
                    {
                        float d = new Vector2(x - n / 2f + 0.5f, y - n / 2f + 0.5f).magnitude / (n / 2f);
                        float a = Mathf.Clamp01(1f - d);
                        a = a * a * (3f - 2f * a);
                        _glow.SetPixel(x, y, new Color(1, 1, 1, a));
                    }
                _glow.Apply();
                return _glow;
            }
        }

        public static Material Additive
        {
            get
            {
                if (_additive) return _additive;
                var saved = Resources.Load<Material>("StoneRiftMaterials/Additive");
                var shader = saved != null ? saved.shader : Shader.Find("Legacy Shaders/Particles/Additive") ?? Shader.Find("Sprites/Default");
                _additive = new Material(shader) { mainTexture = GlowTexture };
                if (_additive.HasProperty("_TintColor")) _additive.SetColor("_TintColor", new Color(0.5f, 0.5f, 0.5f, 0.5f));
                return _additive;
            }
        }

        // ------------------------------------------------------------------ construct shapes

        /// <summary>Builds the placeholder construct. Every construct hovers: they are energy, not animals.</summary>
        public static void BuildConstruct(CardDefinition def, Transform root, float h)
        {
            var f = def.Faction;
            var body = Body(f).a < 1f ? Materials.Transparent(Body(f)) : Materials.Lit(Body(f));
            var accent = Materials.Lit(Color.Lerp(Main(f), Color.black, 0.35f));
            float size = h;

            switch (f)
            {
                case Faction.Magma:
                    // Cracked rock plates around a molten core.
                    for (int i = 0; i < 6; i++)
                    {
                        float a = i * 60f;
                        var plate = Materials.Primitive(PrimitiveType.Cube, root,
                            Quaternion.Euler(0, a, 0) * new Vector3(0, size * (0.35f + 0.12f * (i % 2)), size * 0.2f),
                            new Vector3(size * 0.28f, size * 0.32f, size * 0.12f), body);
                        plate.transform.localRotation = Quaternion.Euler(-15f + 10 * (i % 3), a, 8f * i);
                    }
                    if (def.Cost >= 5)
                    {
                        // A long armoured tail behind the larger magma constructs.
                        for (int i = 1; i <= 4; i++)
                            Materials.Primitive(PrimitiveType.Cube, root, new Vector3(0, size * (0.3f - i * 0.03f), -size * (0.25f + i * 0.17f)),
                                Vector3.one * size * (0.2f - i * 0.03f), body).transform.localRotation = Quaternion.Euler(20 * i, 45, 0);
                    }
                    break;

                case Faction.Tide:
                    Materials.Primitive(PrimitiveType.Sphere, root, new Vector3(0, size * 0.45f, 0), new Vector3(size * 0.6f, size * 0.42f, size * 0.85f), body);
                    for (int s = -1; s <= 1; s += 2)
                        Materials.Primitive(PrimitiveType.Cube, root, new Vector3(s * size * 0.42f, size * 0.42f, 0), new Vector3(size * 0.45f, size * 0.03f, size * 0.32f),
                            Materials.Transparent(new Color(0.85f, 0.97f, 1f, 0.7f))).transform.localRotation = Quaternion.Euler(0, 0, s * -18f);
                    break;

                case Faction.Volt:
                    Materials.Primitive(PrimitiveType.Capsule, root, new Vector3(0, size * 0.5f, 0), new Vector3(size * 0.22f, size * 0.28f, size * 0.22f), body)
                        .transform.localRotation = Quaternion.Euler(70, 0, 0);
                    for (int s = -1; s <= 1; s += 2)
                        Materials.Primitive(PrimitiveType.Cube, root, new Vector3(s * size * 0.38f, size * 0.55f, -size * 0.05f), new Vector3(size * 0.6f, size * 0.025f, size * 0.22f), accent)
                            .transform.localRotation = Quaternion.Euler(0, s * -20f, s * -12f);
                    break;

                case Faction.Crystal:
                    for (int i = 0; i < 5; i++)
                    {
                        float a = i * 72f;
                        float tall = size * (i == 0 ? 0.95f : 0.55f + 0.08f * i);
                        var shard = Materials.Primitive(PrimitiveType.Cube, root,
                            i == 0 ? new Vector3(0, tall * 0.5f, 0) : Quaternion.Euler(0, a, 0) * new Vector3(0, tall * 0.45f, size * 0.16f),
                            new Vector3(size * 0.16f, tall, size * 0.16f), body);
                        shard.transform.localRotation = Quaternion.Euler(i == 0 ? 0 : 14, a + 45, i == 0 ? 0 : 10);
                    }
                    break;

                case Faction.Gravity:
                    Materials.Primitive(PrimitiveType.Sphere, root, new Vector3(0, size * 0.55f, 0), Vector3.one * size * 0.42f, body);
                    var ringA = Ring(root, new Vector3(0, size * 0.55f, 0), size * 0.42f, Main(f), size);
                    ringA.transform.localRotation = Quaternion.Euler(70, 0, 0);
                    ringA.gameObject.AddComponent<Spin>().Speed = new Vector3(0, 0, 90);
                    var ringB = Ring(root, new Vector3(0, size * 0.55f, 0), size * 0.55f, Main(f), size);
                    ringB.transform.localRotation = Quaternion.Euler(20, 40, 0);
                    ringB.gameObject.AddComponent<Spin>().Speed = new Vector3(0, -60, 0);
                    break;

                default:
                    Materials.Primitive(PrimitiveType.Sphere, root, new Vector3(0, size * 0.5f, 0), Vector3.one * size * 0.5f, body);
                    break;
            }

            // The glowing heart every construct has.
            var core = Glow(root, new Vector3(0, size * (f == Faction.Crystal ? 0.5f : 0.5f), 0), Main(f), size * (f == Faction.Gravity ? 0.5f : 0.75f));
            core.gameObject.AddComponent<Pulse>();
        }

        static LineRenderer Ring(Transform parent, Vector3 localPos, float radius, Color color, float scale)
        {
            var go = new GameObject("Ring");
            go.transform.SetParent(parent, false);
            go.transform.localPosition = localPos;
            var lr = go.AddComponent<LineRenderer>();
            lr.useWorldSpace = false;
            lr.loop = true;
            lr.positionCount = 48;
            for (int i = 0; i < 48; i++)
            {
                float a = i / 48f * Mathf.PI * 2f;
                lr.SetPosition(i, new Vector3(Mathf.Cos(a) * radius, Mathf.Sin(a) * radius, 0));
            }
            lr.widthMultiplier = scale * 0.05f;
            lr.sharedMaterial = Additive;
            lr.startColor = lr.endColor = color;
            return lr;
        }

        /// <summary>A soft glowing sprite that always faces the camera.</summary>
        public static Transform Glow(Transform parent, Vector3 localPos, Color color, float size)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Quad);
            Object.Destroy(go.GetComponent<Collider>());
            go.name = "Glow";
            go.transform.SetParent(parent, false);
            go.transform.localPosition = localPos;
            go.transform.localScale = Vector3.one * size;
            var mr = go.GetComponent<MeshRenderer>();
            mr.material = Additive;
            mr.material.color = color;
            if (mr.material.HasProperty("_TintColor")) mr.material.SetColor("_TintColor", color * 0.6f);
            go.AddComponent<Billboard>();
            return go.transform;
        }

        // ------------------------------------------------------------------ aura

        /// <summary>Always-on particles: embers, droplets, sparks, glints or orbiting debris.</summary>
        public static ParticleSystem AddAura(Faction f, Transform parent, float h)
        {
            var go = new GameObject("Aura");
            go.transform.SetParent(parent, false);
            go.transform.localPosition = new Vector3(0, h * 0.45f, 0);
            var ps = go.AddComponent<ParticleSystem>();
            ps.Stop(true, ParticleSystemStopBehavior.StopEmittingAndClear);
            var main = ps.main;
            main.loop = true;
            main.playOnAwake = true;
            main.scalingMode = ParticleSystemScalingMode.Hierarchy;
            main.simulationSpace = ParticleSystemSimulationSpace.Local;
            main.startColor = new ParticleSystem.MinMaxGradient(Main(f), Color.Lerp(Main(f), Color.white, 0.5f));
            main.maxParticles = 60;
            var em = ps.emission;
            var shape = ps.shape;
            shape.shapeType = ParticleSystemShapeType.Sphere;
            shape.radius = h * 0.35f;
            var vel = ps.velocityOverLifetime;

            switch (f)
            {
                case Faction.Magma: // embers drifting up
                    main.startLifetime = new ParticleSystem.MinMaxCurve(0.6f, 1.3f);
                    main.startSize = new ParticleSystem.MinMaxCurve(h * 0.05f, h * 0.12f);
                    main.startSpeed = h * 0.4f;
                    em.rateOverTime = 14;
                    vel.enabled = true;
                    vel.y = new ParticleSystem.MinMaxCurve(h * 0.8f, h * 1.6f);
                    break;
                case Faction.Tide: // slow floating droplets
                    main.startLifetime = new ParticleSystem.MinMaxCurve(1.2f, 2f);
                    main.startSize = new ParticleSystem.MinMaxCurve(h * 0.04f, h * 0.09f);
                    main.startSpeed = h * 0.15f;
                    em.rateOverTime = 10;
                    shape.radius = h * 0.55f;
                    break;
                case Faction.Volt: // quick sparks
                    main.startLifetime = new ParticleSystem.MinMaxCurve(0.08f, 0.2f);
                    main.startSize = new ParticleSystem.MinMaxCurve(h * 0.04f, h * 0.1f);
                    main.startSpeed = new ParticleSystem.MinMaxCurve(h * 1.5f, h * 3f);
                    em.rateOverTime = 30;
                    var noise = ps.noise;
                    noise.enabled = true;
                    noise.strength = h * 2f;
                    noise.frequency = 3f;
                    break;
                case Faction.Crystal: // twinkling glints
                    main.startLifetime = new ParticleSystem.MinMaxCurve(0.3f, 0.7f);
                    main.startSize = new ParticleSystem.MinMaxCurve(h * 0.06f, h * 0.14f);
                    main.startSpeed = 0f;
                    em.rateOverTime = 8;
                    shape.radius = h * 0.5f;
                    var sol = ps.sizeOverLifetime;
                    sol.enabled = true;
                    sol.size = new ParticleSystem.MinMaxCurve(1f, new AnimationCurve(new Keyframe(0, 0), new Keyframe(0.5f, 1), new Keyframe(1, 0)));
                    break;
                case Faction.Gravity: // debris orbiting the core
                    main.startLifetime = new ParticleSystem.MinMaxCurve(1.5f, 2.5f);
                    main.startSize = new ParticleSystem.MinMaxCurve(h * 0.04f, h * 0.08f);
                    main.startSpeed = 0f;
                    em.rateOverTime = 12;
                    shape.shapeType = ParticleSystemShapeType.Circle;
                    shape.radius = h * 0.65f;
                    shape.rotation = new Vector3(90, 0, 0);
                    vel.enabled = true;
                    vel.orbitalY = new ParticleSystem.MinMaxCurve(2.5f);
                    break;
            }

            var col = ps.colorOverLifetime;
            col.enabled = true;
            var g = new Gradient();
            g.SetKeys(new[] { new GradientColorKey(Color.white, 0), new GradientColorKey(Color.white, 1) },
                      new[] { new GradientAlphaKey(0, 0), new GradientAlphaKey(1, 0.2f), new GradientAlphaKey(0, 1) });
            col.color = g;
            go.GetComponent<ParticleSystemRenderer>().sharedMaterial = Additive;
            ps.Play();
            return ps;
        }

        // ------------------------------------------------------------------ attacks

        /// <summary>The faction's attack, travelling from the attacker to the target (world space).</summary>
        public static IEnumerator Attack(MonoBehaviour host, Faction f, Vector3 from, Vector3 to, float scale)
        {
            Color c = Main(f);
            switch (f)
            {
                case Faction.Volt:
                    for (int i = 0; i < 3; i++)
                    {
                        var bolt = Lightning(from, to, c, scale);
                        yield return new WaitForSeconds(0.07f);
                        Object.Destroy(bolt);
                        yield return new WaitForSeconds(0.03f);
                    }
                    break;
                case Faction.Gravity:
                    Shockwave(to, c, scale);
                    yield return Projectile(from, to, c, scale * 1.4f, 0.25f, 0);
                    break;
                case Faction.Crystal:
                    for (int i = 0; i < 4; i++)
                        host.StartCoroutine(Projectile(from + Random.insideUnitSphere * 0.02f * scale, to, c, scale * 0.7f, 0.22f + i * 0.04f, 1));
                    yield return new WaitForSeconds(0.36f);
                    break;
                case Faction.Tide:
                    yield return Projectile(from, to, c, scale, 0.25f, 2);
                    Fx.Burst(to, new Color(0.85f, 0.97f, 1f), scale, 45);
                    break;
                default: // Magma and neutral: a stream of fire
                    yield return Projectile(from, to, c, scale * 1.2f, 0.25f, 2);
                    break;
            }
            Fx.Burst(to, c, scale * 1.2f, 70);
        }

        /// <summary>A glowing ball flying along an arc, optionally leaving a trail of particles.</summary>
        static IEnumerator Projectile(Vector3 from, Vector3 to, Color c, float scale, float seconds, int trail)
        {
            var go = new GameObject("Projectile");
            var glow = Glow(go.transform, Vector3.zero, c, 0.05f * scale);
            ParticleSystem ps = null;
            if (trail > 0)
            {
                ps = go.AddComponent<ParticleSystem>();
                ps.Stop(true, ParticleSystemStopBehavior.StopEmittingAndClear);
                var main = ps.main;
                main.startLifetime = 0.25f;
                main.startSpeed = 0f;
                main.startSize = 0.03f * scale;
                main.startColor = c;
                main.simulationSpace = ParticleSystemSimulationSpace.World;
                var em = ps.emission;
                em.rateOverTime = 120 * trail;
                var shape = ps.shape;
                shape.radius = 0.005f * scale;
                go.GetComponent<ParticleSystemRenderer>().sharedMaterial = Additive;
                ps.Play();
            }
            for (float k = 0; k < 1f; k += Time.deltaTime / seconds)
            {
                go.transform.position = Vector3.Lerp(from, to, k) + Vector3.up * Mathf.Sin(k * Mathf.PI) * 0.04f * scale;
                yield return null;
            }
            Object.Destroy(glow.gameObject);
            if (ps) ps.Stop(true, ParticleSystemStopBehavior.StopEmitting);
            Object.Destroy(go, 0.4f);
        }

        static GameObject Lightning(Vector3 from, Vector3 to, Color c, float scale)
        {
            var go = new GameObject("Lightning");
            var lr = go.AddComponent<LineRenderer>();
            const int points = 12;
            lr.positionCount = points;
            Vector3 dir = to - from;
            Vector3 side = Vector3.Cross(dir.normalized, Vector3.up);
            for (int i = 0; i < points; i++)
            {
                float k = i / (points - 1f);
                Vector3 jitter = i == 0 || i == points - 1 ? Vector3.zero
                    : (side * Random.Range(-1f, 1f) + Vector3.up * Random.Range(-0.5f, 1f)) * 0.02f * scale;
                lr.SetPosition(i, from + dir * k + jitter);
            }
            lr.widthMultiplier = 0.012f * scale;
            lr.sharedMaterial = Additive;
            lr.startColor = Color.white;
            lr.endColor = c;
            return go;
        }

        static void Shockwave(Vector3 at, Color c, float scale)
        {
            var go = new GameObject("Shockwave");
            go.transform.position = at;
            var ring = Ring(go.transform, Vector3.zero, 0.02f * scale, c, scale);
            ring.transform.localRotation = Quaternion.Euler(90, 0, 0);
            go.AddComponent<Expand>().Init(4f, 0.4f);
        }

        // ------------------------------------------------------------------ small behaviours

        public sealed class Billboard : MonoBehaviour
        {
            void LateUpdate()
            {
                var cam = Camera.main;
                if (cam) transform.rotation = Quaternion.LookRotation(transform.position - cam.transform.position, cam.transform.up);
            }
        }

        public sealed class Pulse : MonoBehaviour
        {
            Vector3 _base;
            float _phase;
            void Start() { _base = transform.localScale; _phase = Random.value * 6f; }
            void Update() => transform.localScale = _base * (1f + Mathf.Sin(Time.time * 3f + _phase) * 0.12f);
        }

        public sealed class Spin : MonoBehaviour
        {
            public Vector3 Speed;
            void Update() => transform.Rotate(Speed * Time.deltaTime, Space.Self);
        }

        sealed class Expand : MonoBehaviour
        {
            float _to, _seconds, _start;
            Vector3 _base;
            public void Init(float to, float seconds) { _to = to; _seconds = seconds; _start = Time.time; _base = transform.localScale; }
            void Update()
            {
                float k = (Time.time - _start) / _seconds;
                transform.localScale = _base * Mathf.Lerp(1f, _to, k);
                if (k >= 1f) Destroy(gameObject);
            }
        }
    }
}
