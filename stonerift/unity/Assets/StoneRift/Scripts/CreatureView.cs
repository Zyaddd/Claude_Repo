using StoneRift.Core;
using UnityEngine;

namespace StoneRift.App
{
    /// <summary>
    /// A creature standing on its card. Uses Resources/Models/{ModelId} when a real model exists,
    /// otherwise builds a placeholder figure from simple shapes in the faction's colours.
    /// </summary>
    public sealed class CreatureView : MonoBehaviour
    {
        /// <summary>Which player this phone is, so our constructs get the friendly label colour.</summary>
        public static int LocalPlayer;

        public int InstanceId { get; private set; }
        public int Owner { get; private set; }
        public CardDefinition Def { get; private set; }
        public Transform Model { get; private set; }

        TextMesh _label;
        ParticleSystem _aura;
        public float Height { get; private set; }
        Renderer[] _renderers;
        Color[] _baseColors;
        GameObject _shield, _ice, _ring;
        float _bobPhase;
        bool _selected;
        Position _position = Position.Attack;

        public static Color FactionColor(Faction f) => FactionVisuals.Main(f);

        public static CreatureView Create(CardInstance card, Transform parent)
        {
            var go = new GameObject($"Creature {card.Def.NameEn}");
            go.transform.SetParent(parent, false);
            var v = go.AddComponent<CreatureView>();
            v.InstanceId = card.InstanceId;
            v.Owner = card.Owner;
            v.Def = card.Def;
            v.Build();
            return v;
        }

        void Build()
        {
            float height = 0.05f + Def.Cost * 0.006f;
            Height = height;

            var prefab = string.IsNullOrEmpty(Def.ModelId) ? null : Resources.Load<GameObject>("Models/" + Def.ModelId);
            if (prefab != null)
            {
                Model = Instantiate(prefab, transform).transform;
                float size = Model.GetComponentInChildren<Renderer>()?.bounds.size.y ?? 1f;
                Model.localScale *= height / Mathf.Max(0.001f, size);
            }
            else
            {
                Model = new GameObject("Model").transform;
                Model.SetParent(transform, false);
                FactionVisuals.BuildConstruct(Def, Model, height);
            }
            _aura = FactionVisuals.AddAura(Def.Faction, Model, height);

            var col = gameObject.AddComponent<BoxCollider>();
            col.size = new Vector3(0.07f, height * 1.3f, 0.07f);
            col.center = new Vector3(0, height * 0.6f, 0);

            _shield = Materials.Primitive(PrimitiveType.Cube, transform, new Vector3(0, height * 0.45f, 0.03f),
                new Vector3(0.06f, height * 0.7f, 0.006f), Materials.Transparent(new Color(0.4f, 0.7f, 1f, 0.45f)));
            _shield.SetActive(false);
            _ice = Materials.Primitive(PrimitiveType.Sphere, transform, new Vector3(0, height * 0.5f, 0),
                new Vector3(0.075f, height * 1.15f, 0.075f), Materials.Transparent(new Color(0.7f, 0.95f, 1f, 0.35f)));
            _ice.SetActive(false);
            _ring = Materials.Primitive(PrimitiveType.Cylinder, transform, new Vector3(0, 0.002f, 0),
                new Vector3(0.085f, 0.001f, 0.085f), Materials.Transparent(new Color(1f, 0.9f, 0.3f, 0.6f)));
            _ring.SetActive(false);

            var labelGo = new GameObject("Label");
            labelGo.transform.SetParent(transform, false);
            labelGo.transform.localPosition = new Vector3(0, height * 1.35f + 0.012f, 0);
            _label = labelGo.AddComponent<TextMesh>();
            _label.font = Fonts.Default;
            labelGo.GetComponent<MeshRenderer>().sharedMaterial = Fonts.Default.material;
            _label.fontSize = 48;
            _label.characterSize = 0.0022f;
            _label.anchor = TextAnchor.LowerCenter;
            _label.alignment = TextAlignment.Center;
            _label.richText = true;

            // Only solid parts get tinted; glows, rings and particles keep their own colours.
            _renderers = System.Array.FindAll(Model.GetComponentsInChildren<Renderer>(),
                r => r is MeshRenderer && r.GetComponent<FactionVisuals.Billboard>() == null && r.sharedMaterial != null && r.sharedMaterial.HasProperty("_Color"));
            _baseColors = new Color[_renderers.Length];
            for (int i = 0; i < _renderers.Length; i++)
            {
                _renderers[i].material = new Material(_renderers[i].sharedMaterial);
                _baseColors[i] = _renderers[i].material.color;
            }
            _bobPhase = Random.value * 6f;
        }

        public void Refresh(Game game)
        {
            var c = game.FindCreature(InstanceId);
            if (c == null) return;
            _position = c.Position;
            _shield.SetActive(c.Position == Position.Guard);
            bool frozen = game.IsFrozen(c);
            _ice.SetActive(frozen);
            if (_aura != null)
            {
                // A frozen construct's aura dims; one in guard glows softer.
                var em = _aura.emission;
                em.rateOverTimeMultiplier = frozen ? 0.2f : c.Position == Position.Guard ? 0.6f : 1f;
            }
            int atk = game.EffectiveAttack(c), def = game.EffectiveDefense(c);
            // The stat that matters in the current position is bold and coloured.
            string stats = c.Position == Position.Attack
                ? $"<b><color=#ff9a5a>{atk}</color></b> / {def}"
                : $"{atk} / <b><color=#7ec8ff>{def}</color></b>";
            _label.text = ArabicShaper.Fix(Def.NameAr.Split('،')[0]) + "\n" + stats;
            _label.color = c.Owner == LocalPlayer ? new Color(0.85f, 1f, 0.85f) : new Color(1f, 0.85f, 0.85f);
        }

        public void SetSelected(bool on)
        {
            _selected = on;
            _ring.SetActive(on);
        }

        public void Tint(Color c, float amount)
        {
            for (int i = 0; i < _renderers.Length; i++)
                if (_renderers[i]) _renderers[i].material.color = Color.Lerp(_baseColors[i], c, amount);
        }

        void LateUpdate()
        {
            // Idle breathing, a little hop when selected, crouch in guard, and the label faces the camera.
            float t = Time.time * 2f + _bobPhase;
            float crouch = _position == Position.Guard ? 0.8f : 1f;
            Model.localScale = new Vector3(1f, crouch * (1f + Mathf.Sin(t) * 0.025f), 1f);
            float hover = 0.012f + Mathf.Sin(t * 0.8f) * 0.004f;
            Model.localPosition = new Vector3(0, hover + (_selected ? Mathf.Abs(Mathf.Sin(t * 2f)) * 0.01f : 0f), 0);
            var cam = Camera.main;
            if (cam != null && _label != null)
                _label.transform.rotation = Quaternion.LookRotation(_label.transform.position - cam.transform.position, cam.transform.up);
        }
    }

    public static class Fonts
    {
        static Font _default;
        public static Font Default => _default ? _default : _default = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
    }
}
