using System.Collections.Generic;
using StoneRift.Core;
using UnityEngine;
using UnityEngine.XR.ARFoundation;
using UnityEngine.XR.ARSubsystems;

namespace StoneRift.App
{
    /// <summary>
    /// The card scanner: point the camera at printed StoneRift cards and each one's construct
    /// appears standing on it (techniques, traps and fields show their glow and name).
    /// First step towards playing matches with real cards on the table.
    /// </summary>
    public sealed partial class StoneRiftApp
    {
        readonly Dictionary<TrackableId, GameObject> _holograms = new Dictionary<TrackableId, GameObject>();
        string _scanStatus = "";

        void OpenScanner()
        {
            if (_rig.Fallback)
            {
                _scanStatus = "المسح محتاج AR، والموبايل ده مبيدعمهوش";
                _mode = Mode.Scan;
                return;
            }
            var library = Resources.Load<XRReferenceImageLibrary>("StoneRiftCardLibrary");
            if (library == null)
            {
                _scanStatus = "مكتبة صور الكروت مش موجودة في النسخة دي";
                _mode = Mode.Scan;
                return;
            }
            _rig.Images.referenceLibrary = library;
            _rig.Images.requestedMaxNumberOfMovingImages = 4;
            _rig.Images.enabled = true;
            _rig.SetPlanesVisible(false);
            _scanStatus = "حط كارت مطبوع على الترابيزة ووجّه الكاميرا عليه";
            _mode = Mode.Scan;
        }

        void CloseScanner()
        {
            if (_rig.Images != null) _rig.Images.enabled = false;
            foreach (var h in _holograms.Values) if (h) Destroy(h);
            _holograms.Clear();
            _rig.SetPlanesVisible(true);
            _mode = Mode.Menu;
        }

        /// <summary>Keeps one hologram per visible card, hidden while the camera loses sight of it.</summary>
        void UpdateScanner()
        {
            if (_mode != Mode.Scan || _rig.Images == null || !_rig.Images.enabled) return;
            int visible = 0;
            foreach (var image in _rig.Images.trackables)
            {
                bool tracking = image.trackingState == TrackingState.Tracking;
                if (!_holograms.TryGetValue(image.trackableId, out var hologram) || hologram == null)
                {
                    if (!tracking) continue;
                    hologram = CreateHologram(image);
                    if (hologram == null) continue;
                    _holograms[image.trackableId] = hologram;
                }
                hologram.SetActive(tracking);
                if (tracking) visible++;
            }
            _scanStatus = visible == 0 ? "حط كارت مطبوع على الترابيزة ووجّه الكاميرا عليه"
                        : visible == 1 ? "لقيت كارت!" : $"لقيت {visible} كروت";
        }

        GameObject CreateHologram(ARTrackedImage image)
        {
            string id = image.referenceImage.name;
            CardDefinition def;
            try { def = _db.Get(id); }
            catch (KeyNotFoundException) { return null; }

            var root = new GameObject("Hologram " + id);
            root.transform.SetParent(image.transform, false);
            if (def.Type == CardType.Creature)
            {
                var view = CreatureView.Create(new CardInstance { InstanceId = -1, Def = def, Owner = 0 }, root.transform);
                view.ShowCardInfo();
                Sfx.Play(Sfx.Kind.Summon);
                Fx.Burst(root.transform.position, FactionVisuals.Main(def.Faction), 1f, 60);
            }
            else
            {
                float h = 0.05f;
                FactionVisuals.Glow(root.transform, new Vector3(0, h, 0), FactionVisuals.Main(def.Faction), h * 1.2f).gameObject.AddComponent<FactionVisuals.Pulse>();
                FactionVisuals.AddAura(def.Faction, root.transform, h);
                Sfx.Play(Sfx.Kind.Technique);
                var label = new GameObject("Label");
                label.transform.SetParent(root.transform, false);
                label.transform.localPosition = new Vector3(0, h * 2.2f, 0);
                var tm = label.AddComponent<TextMesh>();
                tm.font = Fonts.Default;
                label.GetComponent<MeshRenderer>().sharedMaterial = Fonts.Default.material;
                tm.text = ArabicShaper.Fix(def.NameAr);
                tm.fontSize = 48;
                tm.characterSize = 0.0022f;
                tm.anchor = TextAnchor.LowerCenter;
                label.AddComponent<FactionVisuals.Billboard>();
            }
            return root;
        }

        void DrawScanner()
        {
            float w = VW - 40;
            var panel = Ui(new Rect(20, 30, w, 110));
            GUI.Box(panel, GUIContent.none, _box);
            GUI.Label(new Rect(30, 40, w - 20, 90), T(_scanStatus), _label);
            if (GUI.Button(Ui(new Rect(20, VirtualHeight - 100, w, 75)), T("رجوع"), _button)) CloseScanner();
        }
    }
}
