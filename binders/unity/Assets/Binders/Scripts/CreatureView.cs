using Binders.Core;
using UnityEngine;

namespace Binders.App
{
    /// <summary>
    /// A creature standing on its card. Uses Resources/Models/{ModelId} when a real model exists,
    /// otherwise builds a placeholder figure from simple shapes in the faction's colours.
    /// </summary>
    public sealed class CreatureView : MonoBehaviour
    {
        public int InstanceId { get; private set; }
        public int Owner { get; private set; }
        public CardDefinition Def { get; private set; }
        public Transform Model { get; private set; }

        TextMesh _label;
        Renderer[] _renderers;
        Color[] _baseColors;
        GameObject _shield, _ice, _ring;
        float _bobPhase;
        bool _selected;
        Position _position = Position.Attack;

        public static Color FactionColor(Faction f)
        {
            switch (f)
            {
                case Faction.Flame: return new Color(0.95f, 0.38f, 0.12f);
                case Faction.Tide: return new Color(0.1f, 0.6f, 0.75f);
                case Faction.Stone: return new Color(0.55f, 0.52f, 0.47f);
                case Faction.Storm: return new Color(0.98f, 0.85f, 0.2f);
                case Faction.Shadow: return new Color(0.42f, 0.22f, 0.6f);
                default: return Color.gray;
            }
        }

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
                BuildPlaceholder(Model, height);
            }

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

            _renderers = Model.GetComponentsInChildren<Renderer>();
            _baseColors = new Color[_renderers.Length];
            for (int i = 0; i < _renderers.Length; i++)
            {
                _renderers[i].material = Materials.Instance(_renderers[i].sharedMaterial.color);
                _baseColors[i] = _renderers[i].material.color;
            }
            _bobPhase = Random.value * 6f;
        }

        void BuildPlaceholder(Transform root, float h)
        {
            var main = Materials.Lit(FactionColor(Def.Faction));
            var dark = Materials.Lit(Color.Lerp(FactionColor(Def.Faction), Color.black, 0.55f));
            var eye = Materials.Lit(new Color(1f, 0.95f, 0.6f));

            switch (Def.Faction)
            {
                case Faction.Stone:
                    Materials.Primitive(PrimitiveType.Cube, root, new Vector3(0, h * 0.4f, 0), new Vector3(h * 0.75f, h * 0.8f, h * 0.6f), main);
                    Materials.Primitive(PrimitiveType.Cube, root, new Vector3(0, h * 0.92f, 0.003f), new Vector3(h * 0.45f, h * 0.35f, h * 0.45f), dark);
                    Materials.Primitive(PrimitiveType.Cube, root, new Vector3(-h * 0.5f, h * 0.45f, 0), new Vector3(h * 0.22f, h * 0.6f, h * 0.25f), dark);
                    Materials.Primitive(PrimitiveType.Cube, root, new Vector3(h * 0.5f, h * 0.45f, 0), new Vector3(h * 0.22f, h * 0.6f, h * 0.25f), dark);
                    break;
                case Faction.Tide:
                    Materials.Primitive(PrimitiveType.Capsule, root, new Vector3(0, h * 0.25f, 0), new Vector3(h * 0.5f, h * 0.45f, h * 0.5f), main)
                        .transform.localRotation = Quaternion.Euler(90, 0, 0);
                    Materials.Primitive(PrimitiveType.Sphere, root, new Vector3(0, h * 0.55f, h * 0.35f), new Vector3(h * 0.45f, h * 0.4f, h * 0.55f), dark);
                    break;
                default:
                    Materials.Primitive(PrimitiveType.Capsule, root, new Vector3(0, h * 0.4f, 0), new Vector3(h * 0.55f, h * 0.4f, h * 0.55f), main);
                    Materials.Primitive(PrimitiveType.Sphere, root, new Vector3(0, h * 0.88f, 0.004f), new Vector3(h * 0.48f, h * 0.45f, h * 0.48f), dark);
                    break;
            }

            float eyeY = Def.Faction == Faction.Tide ? h * 0.62f : Def.Faction == Faction.Stone ? h * 0.95f : h * 0.92f;
            float eyeZ = Def.Faction == Faction.Tide ? h * 0.62f : h * 0.24f;
            Materials.Primitive(PrimitiveType.Sphere, root, new Vector3(-h * 0.1f, eyeY, eyeZ), Vector3.one * h * 0.1f, eye);
            Materials.Primitive(PrimitiveType.Sphere, root, new Vector3(h * 0.1f, eyeY, eyeZ), Vector3.one * h * 0.1f, eye);

            switch (Def.Faction)
            {
                case Faction.Flame: // horns and a tail
                    Materials.Primitive(PrimitiveType.Cube, root, new Vector3(-h * 0.14f, h * 1.15f, 0), new Vector3(h * 0.06f, h * 0.25f, h * 0.06f), dark)
                        .transform.localRotation = Quaternion.Euler(0, 0, 20);
                    Materials.Primitive(PrimitiveType.Cube, root, new Vector3(h * 0.14f, h * 1.15f, 0), new Vector3(h * 0.06f, h * 0.25f, h * 0.06f), dark)
                        .transform.localRotation = Quaternion.Euler(0, 0, -20);
                    Materials.Primitive(PrimitiveType.Cube, root, new Vector3(0, h * 0.2f, -h * 0.4f), new Vector3(h * 0.1f, h * 0.1f, h * 0.45f), main);
                    break;
                case Faction.Storm: // wings
                    Materials.Primitive(PrimitiveType.Cube, root, new Vector3(-h * 0.45f, h * 0.55f, -h * 0.05f), new Vector3(h * 0.6f, h * 0.04f, h * 0.3f), dark)
                        .transform.localRotation = Quaternion.Euler(0, 0, 25);
                    Materials.Primitive(PrimitiveType.Cube, root, new Vector3(h * 0.45f, h * 0.55f, -h * 0.05f), new Vector3(h * 0.6f, h * 0.04f, h * 0.3f), dark)
                        .transform.localRotation = Quaternion.Euler(0, 0, -25);
                    break;
                case Faction.Shadow: // hood spikes
                    Materials.Primitive(PrimitiveType.Cube, root, new Vector3(0, h * 1.12f, -h * 0.05f), new Vector3(h * 0.08f, h * 0.3f, h * 0.08f), dark)
                        .transform.localRotation = Quaternion.Euler(-25, 0, 0);
                    break;
            }
        }

        public void Refresh(Game game)
        {
            var c = game.FindCreature(InstanceId);
            if (c == null) return;
            _position = c.Position;
            _shield.SetActive(c.Position == Position.Guard);
            _ice.SetActive(game.IsFrozen(c));
            int atk = game.EffectiveAttack(c), def = game.EffectiveDefense(c);
            // The stat that matters in the current position is bold and coloured.
            string stats = c.Position == Position.Attack
                ? $"<b><color=#ff9a5a>{atk}</color></b> / {def}"
                : $"{atk} / <b><color=#7ec8ff>{def}</color></b>";
            _label.text = ArabicShaper.Fix(Def.NameAr.Split('،')[0]) + "\n" + stats;
            _label.color = c.Owner == 0 ? new Color(0.85f, 1f, 0.85f) : new Color(1f, 0.85f, 0.85f);
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
            Model.localPosition = new Vector3(0, _selected ? Mathf.Abs(Mathf.Sin(t * 2f)) * 0.01f : 0f, 0);
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
