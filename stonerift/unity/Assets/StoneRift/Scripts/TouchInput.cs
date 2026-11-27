using UnityEngine;
#if ENABLE_INPUT_SYSTEM
using UnityEngine.InputSystem;
using UnityEngine.InputSystem.EnhancedTouch;
using ETouch = UnityEngine.InputSystem.EnhancedTouch.Touch;
#endif

namespace StoneRift.App
{
    /// <summary>Taps and two-finger pinch/twist, from the touchscreen or the mouse in the editor.</summary>
    public static class TouchInput
    {
        public struct Frame
        {
            public bool Tapped;
            public Vector2 TapPosition;   // Screen pixels, origin bottom-left.
            public bool Pinching;
            public float ScaleFactor;     // >1 fingers moving apart.
            public float TwistDegrees;
            public Vector2 PinchCenter;
        }

        const float MaxTapSeconds = 0.4f;
        const float MaxTapMovement = 35f;

        static int _lastFrame = -1;
        static Frame _frame;

#if !ENABLE_INPUT_SYSTEM
        static float _touchStart;
        static Vector2 _touchStartPos;
#endif

        public static Frame Current
        {
            get
            {
                if (_lastFrame != Time.frameCount)
                {
                    _lastFrame = Time.frameCount;
                    _frame = Read();
                }
                return _frame;
            }
        }

        static Frame Read()
        {
            var f = new Frame { ScaleFactor = 1f };
#if ENABLE_INPUT_SYSTEM
            if (!EnhancedTouchSupport.enabled) EnhancedTouchSupport.Enable();
            var touches = ETouch.activeTouches;
            if (touches.Count == 1)
            {
                var t = touches[0];
                if (t.phase == UnityEngine.InputSystem.TouchPhase.Ended &&
                    t.time - t.startTime < MaxTapSeconds &&
                    (t.screenPosition - t.startScreenPosition).magnitude < MaxTapMovement)
                {
                    f.Tapped = true;
                    f.TapPosition = t.screenPosition;
                }
            }
            else if (touches.Count == 2)
            {
                Pinch(touches[0].screenPosition, touches[0].delta, touches[1].screenPosition, touches[1].delta, ref f);
            }
            var mouse = Mouse.current;
            if (touches.Count == 0 && mouse != null)
            {
                if (mouse.leftButton.wasReleasedThisFrame)
                {
                    f.Tapped = true;
                    f.TapPosition = mouse.position.ReadValue();
                }
                float scroll = mouse.scroll.ReadValue().y;
                if (Mathf.Abs(scroll) > 0.01f)
                {
                    f.Pinching = true;
                    f.ScaleFactor = 1f + Mathf.Sign(scroll) * 0.05f;
                    f.PinchCenter = mouse.position.ReadValue();
                }
            }
#else
            if (Input.touchCount == 1)
            {
                var t = Input.GetTouch(0);
                if (t.phase == UnityEngine.TouchPhase.Began) { _touchStart = Time.time; _touchStartPos = t.position; }
                if (t.phase == UnityEngine.TouchPhase.Ended && Time.time - _touchStart < MaxTapSeconds &&
                    (t.position - _touchStartPos).magnitude < MaxTapMovement)
                {
                    f.Tapped = true;
                    f.TapPosition = t.position;
                }
            }
            else if (Input.touchCount == 2)
            {
                var a = Input.GetTouch(0);
                var b = Input.GetTouch(1);
                Pinch(a.position, a.deltaPosition, b.position, b.deltaPosition, ref f);
            }
            else if (Input.GetMouseButtonUp(0))
            {
                f.Tapped = true;
                f.TapPosition = Input.mousePosition;
            }
#endif
            return f;
        }

        static void Pinch(Vector2 a, Vector2 da, Vector2 b, Vector2 db, ref Frame f)
        {
            Vector2 prevA = a - da, prevB = b - db;
            float prevDist = (prevB - prevA).magnitude;
            float dist = (b - a).magnitude;
            if (prevDist < 1f) return;
            f.Pinching = true;
            f.ScaleFactor = dist / prevDist;
            f.TwistDegrees = Vector2.SignedAngle(prevB - prevA, b - a);
            f.PinchCenter = (a + b) * 0.5f;
        }
    }
}
