using System.Collections.Generic;
using UnityEngine;

namespace Binders.App
{
    /// <summary>Shared materials made in code. The build script keeps these shaders in the APK.</summary>
    public static class Materials
    {
        static readonly Dictionary<Color, Material> _lit = new Dictionary<Color, Material>();
        static readonly Dictionary<Color, Material> _transparent = new Dictionary<Color, Material>();
        static Shader _litShader, _transparentShader;

        // The build script saves a material for each shader under Resources so the shaders ship in the APK.
        static Shader LitShader => _litShader ? _litShader : _litShader = Load("Lit", "Standard");
        static Shader TransparentShader => _transparentShader ? _transparentShader : _transparentShader = Load("Transparent", "Sprites/Default");

        static Shader Load(string material, string shader)
        {
            var m = Resources.Load<Material>("BindersMaterials/" + material);
            if (m != null && m.shader != null) return m.shader;
            return Shader.Find(shader) ?? Shader.Find("Unlit/Color");
        }

        /// <summary>Shared lit material. Don't modify; use Instance() for per-object colours.</summary>
        public static Material Lit(Color c)
        {
            if (_lit.TryGetValue(c, out var m)) return m;
            m = new Material(LitShader) { color = c };
            if (m.HasProperty("_Glossiness")) m.SetFloat("_Glossiness", 0.35f);
            _lit[c] = m;
            return m;
        }

        public static Material Transparent(Color c)
        {
            if (_transparent.TryGetValue(c, out var m)) return m;
            m = new Material(TransparentShader) { color = c };
            _transparent[c] = m;
            return m;
        }

        /// <summary>A material this object owns, so tinting it doesn't affect others.</summary>
        public static Material Instance(Color c) => new Material(LitShader) { color = c };

        public static GameObject Primitive(PrimitiveType type, Transform parent, Vector3 localPos, Vector3 localScale, Material mat, bool keepCollider = false)
        {
            var go = GameObject.CreatePrimitive(type);
            if (!keepCollider) Object.Destroy(go.GetComponent<Collider>());
            go.transform.SetParent(parent, false);
            go.transform.localPosition = localPos;
            go.transform.localScale = localScale;
            go.GetComponent<Renderer>().sharedMaterial = mat;
            return go;
        }
    }
}
