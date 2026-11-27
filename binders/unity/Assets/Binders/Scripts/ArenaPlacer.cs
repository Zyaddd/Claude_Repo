using System.Collections.Generic;
using UnityEngine;
using UnityEngine.XR.ARFoundation;
using UnityEngine.XR.ARSubsystems;

namespace Binders.App
{
    /// <summary>
    /// Finds the table, shows a target ring where the arena would go, and places it on tap.
    /// After that two fingers resize and turn the arena.
    /// </summary>
    public sealed class ArenaPlacer : MonoBehaviour
    {
        public ArRig Rig;
        public Transform ArenaRoot;          // Holds the ArenaView; moved and scaled here.
        public System.Func<Vector2, bool> IsOverUi = _ => false;

        public bool Placed { get; private set; }
        public bool HasTarget { get; private set; }

        static readonly List<ARRaycastHit> Hits = new List<ARRaycastHit>();
        GameObject _reticle;
        ARAnchor _anchor;

        void Start()
        {
            _reticle = new GameObject("Reticle");
            Materials.Primitive(PrimitiveType.Cylinder, _reticle.transform, Vector3.zero,
                new Vector3(0.44f, 0.001f, 0.44f), Materials.Transparent(new Color(1f, 0.85f, 0.3f, 0.35f)));
            Materials.Primitive(PrimitiveType.Cube, _reticle.transform, new Vector3(0, 0.001f, -0.2f),
                new Vector3(0.04f, 0.002f, 0.04f), Materials.Transparent(new Color(0.4f, 1f, 0.5f, 0.8f)));
            _reticle.SetActive(false);
            ArenaRoot.gameObject.SetActive(false);
        }

        public void ResetPlacement()
        {
            Placed = false;
            ArenaRoot.gameObject.SetActive(false);
            if (_anchor) Destroy(_anchor);
            _anchor = null;
            Rig.SetPlanesVisible(true);
        }

        void Update()
        {
            if (Rig.Fallback)
            {
                if (!Placed) PlaceAt(new Pose(Vector3.zero, Quaternion.identity));
                HandleResize();
                return;
            }
            if (!Rig.ArReady) return;

            if (!Placed)
            {
                Vector2 center = new Vector2(Screen.width * 0.5f, Screen.height * 0.5f);
                HasTarget = Rig.Raycaster.Raycast(center, Hits, TrackableType.PlaneWithinPolygon);
                _reticle.SetActive(HasTarget);
                if (!HasTarget) return;

                var pose = Hits[0].pose;
                pose.rotation = FacingCamera(pose.position);
                _reticle.transform.SetPositionAndRotation(pose.position, pose.rotation);

                var input = TouchInput.Current;
                if (input.Tapped && !IsOverUi(input.TapPosition)) PlaceAt(pose);
                return;
            }
            HandleResize();
        }

        Quaternion FacingCamera(Vector3 position)
        {
            Vector3 away = position - Rig.Cam.transform.position;
            away.y = 0;
            return away.sqrMagnitude < 1e-6f ? Quaternion.identity : Quaternion.LookRotation(away.normalized, Vector3.up);
        }

        void PlaceAt(Pose pose)
        {
            Placed = true;
            _reticle.SetActive(false);
            ArenaRoot.SetPositionAndRotation(pose.position, pose.rotation);
            ArenaRoot.gameObject.SetActive(true);
            if (!Rig.Fallback)
            {
                // An anchor keeps the arena glued to the same spot on the table as tracking improves.
                _anchor = ArenaRoot.gameObject.GetComponent<ARAnchor>();
                if (_anchor == null) _anchor = ArenaRoot.gameObject.AddComponent<ARAnchor>();
                Rig.SetPlanesVisible(false);
            }
        }

        void HandleResize()
        {
            var input = TouchInput.Current;
            if (!input.Pinching || IsOverUi(input.PinchCenter)) return;
            float s = Mathf.Clamp(ArenaRoot.localScale.x * input.ScaleFactor, 0.4f, 3f);
            ArenaRoot.localScale = Vector3.one * s;
            ArenaRoot.Rotate(Vector3.up, -input.TwistDegrees, Space.World);
        }
    }
}
