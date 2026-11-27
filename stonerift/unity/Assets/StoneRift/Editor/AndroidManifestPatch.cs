#if UNITY_ANDROID
using System.IO;
using System.Xml;
using UnityEditor.Android;
using UnityEngine;

namespace StoneRift.EditorTools
{
    /// <summary>
    /// Adds the permissions local-network play needs to the generated Android manifest:
    /// receiving the host's broadcast announcements (multicast lock) and reading the Wi-Fi state.
    /// </summary>
    public sealed class AndroidManifestPatch : IPostGenerateGradleAndroidProject
    {
        const string AndroidNs = "http://schemas.android.com/apk/res/android";
        static readonly string[] Permissions =
        {
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_MULTICAST_STATE",
            "android.permission.RECORD_AUDIO",
        };

        public int callbackOrder => 100;

        public void OnPostGenerateGradleAndroidProject(string path)
        {
            string manifestPath = Path.Combine(path, "src", "main", "AndroidManifest.xml");
            if (!File.Exists(manifestPath)) { Debug.LogWarning("[StoneRift] Manifest not found at " + manifestPath); return; }

            var doc = new XmlDocument();
            doc.Load(manifestPath);
            var manifest = doc.DocumentElement;
            foreach (var permission in Permissions)
            {
                if (doc.SelectSingleNode($"/manifest/uses-permission[@*[local-name()='name']='{permission}']") != null) continue;
                var node = doc.CreateElement("uses-permission");
                node.SetAttribute("name", AndroidNs, permission);
                manifest.PrependChild(node);
            }
            doc.Save(manifestPath);
            Debug.Log("[StoneRift] Manifest permissions added for local multiplayer and voice.");
        }
    }
}
#endif
