#if UNITY_ANDROID
using System.IO;
using System.Xml;
using UnityEditor.Android;
using UnityEngine;

namespace Binders.EditorTools
{
    /// <summary>
    /// Adds what Android's speech recogniser needs to the generated manifest:
    /// the microphone permission and, for Android 11+, visibility of the recognition service.
    /// </summary>
    public sealed class AndroidManifestPatch : IPostGenerateGradleAndroidProject
    {
        const string AndroidNs = "http://schemas.android.com/apk/res/android";
        public int callbackOrder => 100;

        public void OnPostGenerateGradleAndroidProject(string path)
        {
            string manifestPath = Path.Combine(path, "src", "main", "AndroidManifest.xml");
            if (!File.Exists(manifestPath)) { Debug.LogWarning("[BINDERS] Manifest not found at " + manifestPath); return; }

            var doc = new XmlDocument();
            doc.Load(manifestPath);
            var manifest = doc.DocumentElement;

            if (doc.SelectSingleNode("/manifest/uses-permission[@*[local-name()='name']='android.permission.RECORD_AUDIO']") == null)
            {
                var perm = doc.CreateElement("uses-permission");
                perm.SetAttribute("name", AndroidNs, "android.permission.RECORD_AUDIO");
                manifest.PrependChild(perm);
            }

            if (doc.SelectSingleNode("/manifest/queries/intent/action[@*[local-name()='name']='android.speech.RecognitionService']") == null)
            {
                var queries = doc.SelectSingleNode("/manifest/queries") as XmlElement ?? (XmlElement)manifest.AppendChild(doc.CreateElement("queries"));
                var intent = doc.CreateElement("intent");
                var action = doc.CreateElement("action");
                action.SetAttribute("name", AndroidNs, "android.speech.RecognitionService");
                intent.AppendChild(action);
                queries.AppendChild(intent);
            }

            doc.Save(manifestPath);
            Debug.Log("[BINDERS] Manifest patched for speech recognition.");
        }
    }
}
#endif
