using System.Collections;
using Unity.XR.CoreUtils;
using UnityEngine;
using UnityEngine.InputSystem;
using UnityEngine.InputSystem.XR;
using UnityEngine.XR.ARFoundation;
using UnityEngine.XR.ARSubsystems;

namespace StoneRift.App
{
    /// <summary>
    /// Builds the AR camera rig in code (session, origin, camera, plane detection) so the scene file stays empty.
    /// On a phone without ARCore it switches to a fixed camera so the game can still be played.
    /// </summary>
    public sealed class ArRig : MonoBehaviour
    {
        public Camera Cam { get; private set; }
        public ARRaycastManager Raycaster { get; private set; }
        public ARPlaneManager Planes { get; private set; }
        public ARSession Session { get; private set; }
        /// <summary>Recognises printed cards. Off until the scanner is opened.</summary>
        public ARTrackedImageManager Images { get; private set; }
        /// <summary>True once we know AR works on this device.</summary>
        public bool ArReady { get; private set; }
        /// <summary>True when AR is not available and the board is shown in front of a fixed camera.</summary>
        public bool Fallback { get; private set; }
        public string Status { get; private set; } = "Starting AR…";

        public static ArRig Create()
        {
            var go = new GameObject("AR Rig");
            var rig = go.AddComponent<ArRig>();
            rig.Build();
            return rig;
        }

        void Build()
        {
            var sessionGo = new GameObject("AR Session");
            sessionGo.transform.SetParent(transform, false);
            Session = sessionGo.AddComponent<ARSession>();
            sessionGo.AddComponent<ARInputManager>();

            var originGo = new GameObject("XR Origin");
            originGo.SetActive(false); // Configure everything before any OnEnable runs.
            originGo.transform.SetParent(transform, false);

            var offset = new GameObject("Camera Offset");
            offset.transform.SetParent(originGo.transform, false);

            var camGo = new GameObject("AR Camera") { tag = "MainCamera" };
            camGo.transform.SetParent(offset.transform, false);
            Cam = camGo.AddComponent<Camera>();
            Cam.clearFlags = CameraClearFlags.SolidColor;
            Cam.backgroundColor = Color.black;
            Cam.nearClipPlane = 0.01f;
            Cam.farClipPlane = 30f;
            camGo.AddComponent<AudioListener>();
            camGo.AddComponent<ARCameraManager>();
            camGo.AddComponent<ARCameraBackground>();

            var pose = camGo.AddComponent<TrackedPoseDriver>();
            var position = new InputAction("Device Position", binding: "<HandheldARInputDevice>/devicePosition");
            position.AddBinding("<XRHMD>/centerEyePosition");
            var rotation = new InputAction("Device Rotation", binding: "<HandheldARInputDevice>/deviceRotation");
            rotation.AddBinding("<XRHMD>/centerEyeRotation");
            pose.positionInput = new InputActionProperty(position);
            pose.rotationInput = new InputActionProperty(rotation);
            pose.trackingType = TrackedPoseDriver.TrackingType.RotationAndPosition;
            pose.updateType = TrackedPoseDriver.UpdateType.UpdateAndBeforeRender;

            var origin = originGo.AddComponent<XROrigin>();
            origin.Camera = Cam;
            origin.CameraFloorOffsetObject = offset;

            Raycaster = originGo.AddComponent<ARRaycastManager>();
            Planes = originGo.AddComponent<ARPlaneManager>();
            Planes.planePrefab = MakePlaneTemplate();
            Planes.requestedDetectionMode = PlaneDetectionMode.Horizontal;
            originGo.AddComponent<ARAnchorManager>();
            Images = originGo.AddComponent<ARTrackedImageManager>();
            Images.enabled = false;

            originGo.SetActive(true);

            var light = new GameObject("Sun").AddComponent<Light>();
            light.type = LightType.Directional;
            light.intensity = 1.1f;
            light.shadows = LightShadows.None;
            light.transform.rotation = Quaternion.Euler(55f, -30f, 0f);
            light.transform.SetParent(transform, true);
            RenderSettings.ambientLight = new Color(0.55f, 0.55f, 0.6f);
        }

        GameObject MakePlaneTemplate()
        {
            // Kept under an inactive holder: the template itself is "active", so ARFoundation's copies are visible.
            var holder = new GameObject("Templates");
            holder.SetActive(false);
            holder.transform.SetParent(transform, false);

            var plane = new GameObject("Detected Plane");
            plane.transform.SetParent(holder.transform, false);
            plane.AddComponent<MeshFilter>();
            var mr = plane.AddComponent<MeshRenderer>();
            mr.sharedMaterial = Materials.Transparent(new Color(0.55f, 0.85f, 1f, 0.18f));
            plane.AddComponent<ARPlane>();
            plane.AddComponent<ARPlaneMeshVisualizer>();
            return plane;
        }

        IEnumerator Start()
        {
            if (Application.isEditor)
            {
                EnableFallback("No AR in the editor");
                yield break;
            }

            if (ARSession.state == ARSessionState.None || ARSession.state == ARSessionState.CheckingAvailability)
                yield return ARSession.CheckAvailability();

            if (ARSession.state == ARSessionState.Unsupported)
            {
                EnableFallback("This phone does not support ARCore");
                yield break;
            }

            // NeedsInstall: the session asks the user to install Google Play Services for AR.
            float timeout = Time.time + 25f;
            while (ARSession.state < ARSessionState.SessionInitializing && Time.time < timeout)
            {
                Status = ARSession.state == ARSessionState.Installing ? "Installing ARCore…" : "Starting AR…";
                yield return null;
            }
            if (ARSession.state < ARSessionState.SessionInitializing)
            {
                EnableFallback("AR could not start");
                yield break;
            }
            ArReady = true;
            Status = "";
        }

        /// <summary>Plays without AR: fixed camera looking down at the board.</summary>
        public void EnableFallback(string reason)
        {
            Fallback = true;
            ArReady = false;
            Status = reason;
            Session.enabled = false;
            Planes.enabled = false;
            Raycaster.enabled = false;
            Images.enabled = false;
            var bg = Cam.GetComponent<ARCameraBackground>();
            if (bg) bg.enabled = false;
            var pose = Cam.GetComponent<TrackedPoseDriver>();
            if (pose) pose.enabled = false;
            Cam.clearFlags = CameraClearFlags.SolidColor;
            Cam.backgroundColor = new Color(0.07f, 0.06f, 0.1f);
            Cam.transform.SetPositionAndRotation(new Vector3(0f, 0.55f, -0.5f), Quaternion.Euler(48f, 0f, 0f));
        }

        public void SetPlanesVisible(bool visible)
        {
            if (Fallback) return;
            foreach (var p in Planes.trackables) p.gameObject.SetActive(visible);
        }
    }
}
