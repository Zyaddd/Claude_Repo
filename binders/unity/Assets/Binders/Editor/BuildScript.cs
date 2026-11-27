using System;
using System.IO;
using System.Reflection;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.Build.Reporting;
using UnityEditor.SceneManagement;
using UnityEditor.XR.Management;
using UnityEditor.XR.Management.Metadata;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.XR.Management;

namespace Binders.EditorTools
{
    /// <summary>
    /// Sets the project up for Android + ARCore and builds the APK.
    /// GameCI calls BuildAndroid on GitHub; on a computer use the BINDERS menu.
    /// Everything is configured here in code so the repository needs no hand-edited Unity settings files.
    /// </summary>
    public static class BuildScript
    {
        const string ScenePath = "Assets/Binders/Main.unity";
        const string DataFolder = "Assets/Resources/BindersData";
        const string MaterialsFolder = "Assets/Resources/BindersMaterials";
        const string XrSettingsPath = "Assets/XR/XRGeneralSettingsPerBuildTarget.asset";

        [MenuItem("BINDERS/Prepare project")]
        public static void Prepare()
        {
            CopyCardData();
            CreateMaterials();
            EnsureScene();
            ConfigurePlayer();
            ConfigureXr();
            ConfigureArCore();
            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh();
            Debug.Log("[BINDERS] Project prepared.");
        }

        [MenuItem("BINDERS/Build Android APK")]
        public static void BuildAndroid()
        {
            Prepare();

            string path = Arg("customBuildPath");
            if (string.IsNullOrEmpty(path)) path = "Builds/Android/BINDERS.apk";
            if (!path.EndsWith(".apk", StringComparison.OrdinalIgnoreCase)) path = Path.Combine(path, "BINDERS.apk");
            Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(path)));

            if (int.TryParse(Arg("androidVersionCode"), out int code) && code > 0) PlayerSettings.Android.bundleVersionCode = code;
            string version = Arg("buildVersion");
            if (!string.IsNullOrEmpty(version) && version != "none") PlayerSettings.bundleVersion = version;

            EditorUserBuildSettings.buildAppBundle = false;
            var report = BuildPipeline.BuildPlayer(new BuildPlayerOptions
            {
                scenes = new[] { ScenePath },
                locationPathName = path,
                target = BuildTarget.Android,
                targetGroup = BuildTargetGroup.Android,
                options = BuildOptions.None,
            });
            var s = report.summary;
            Debug.Log($"[BINDERS] Build {s.result}: {s.totalSize / (1024 * 1024)} MB, {s.totalErrors} errors, {s.totalTime} → {path}");
            if (s.result != BuildResult.Succeeded) throw new Exception($"[BINDERS] Build failed: {s.result}");
        }

        static string Arg(string name)
        {
            var args = Environment.GetCommandLineArgs();
            for (int i = 0; i < args.Length - 1; i++)
                if (args[i] == "-" + name) return args[i + 1].StartsWith("-") ? "" : args[i + 1];
            return null;
        }

        // ------------------------------------------------------------------ content

        /// <summary>The rules package keeps the card JSON; the player reads a copy from Resources.</summary>
        static void CopyCardData()
        {
            var info = UnityEditor.PackageManager.PackageInfo.FindForAssetPath("Packages/com.binders.core/package.json");
            string source = info != null ? Path.Combine(info.resolvedPath, "Data")
                                         : Path.GetFullPath(Path.Combine(Directory.GetCurrentDirectory(), "../core/Binders.Core/Data"));
            Directory.CreateDirectory(DataFolder);
            foreach (var name in new[] { "cards", "decks" })
            {
                string src = Path.Combine(source, name + ".json");
                if (!File.Exists(src)) throw new FileNotFoundException("[BINDERS] Card data not found", src);
                File.Copy(src, Path.Combine(DataFolder, name + ".json"), true);
            }
            AssetDatabase.Refresh();
        }

        /// <summary>Material assets make sure the shaders the game creates materials from are kept in the APK.</summary>
        static void CreateMaterials()
        {
            Directory.CreateDirectory(MaterialsFolder);
            MakeMaterial("Lit", "Standard");
            MakeMaterial("Transparent", "Sprites/Default");
        }

        static void MakeMaterial(string name, string shaderName)
        {
            string path = $"{MaterialsFolder}/{name}.mat";
            if (AssetDatabase.LoadAssetAtPath<Material>(path) != null) return;
            var shader = Shader.Find(shaderName);
            if (shader == null) { Debug.LogWarning($"[BINDERS] Shader {shaderName} not found"); return; }
            AssetDatabase.CreateAsset(new Material(shader), path);
        }

        static void EnsureScene()
        {
            if (!File.Exists(ScenePath))
            {
                // Empty on purpose: BindersApp builds the AR rig and the arena in code when the app starts.
                var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);
                EditorSceneManager.SaveScene(scene, ScenePath);
            }
            EditorBuildSettings.scenes = new[] { new EditorBuildSettingsScene(ScenePath, true) };
        }

        // ------------------------------------------------------------------ player settings

        static void ConfigurePlayer()
        {
            var android = NamedBuildTarget.Android;
            PlayerSettings.companyName = "Binders";
            PlayerSettings.productName = "BINDERS";
            PlayerSettings.SetApplicationIdentifier(android, "com.binders.ar");
            PlayerSettings.SetScriptingBackend(android, ScriptingImplementation.IL2CPP);
            PlayerSettings.SetManagedStrippingLevel(android, ManagedStrippingLevel.Minimal);
            PlayerSettings.Android.targetArchitectures = AndroidArchitecture.ARM64;
            PlayerSettings.Android.minSdkVersion = AndroidSdkVersions.AndroidApiLevel24; // ARCore minimum.
            PlayerSettings.Android.applicationEntry = AndroidApplicationEntry.Activity;
            PlayerSettings.SetUseDefaultGraphicsAPIs(BuildTarget.Android, false);
            PlayerSettings.SetGraphicsAPIs(BuildTarget.Android, new[] { GraphicsDeviceType.OpenGLES3 });
            PlayerSettings.defaultInterfaceOrientation = UIOrientation.Portrait;
            PlayerSettings.SplashScreen.showUnityLogo = false;

            // A fixed beta key, so every new APK installs as an update. Not for store releases.
            PlayerSettings.Android.useCustomKeystore = true;
            PlayerSettings.Android.keystoreName = Path.GetFullPath("beta-signing.keystore");
            PlayerSettings.Android.keystorePass = "binders-beta";
            PlayerSettings.Android.keyaliasName = "binders";
            PlayerSettings.Android.keyaliasPass = "binders-beta";

            // AR camera tracking uses the Input System; IMGUI and fallbacks use the old one. Enable both.
            var playerSettings = Unsupported.GetSerializedAssetInterfaceSingleton("PlayerSettings");
            var so = new SerializedObject(playerSettings);
            var handler = so.FindProperty("activeInputHandler");
            if (handler != null && handler.intValue != 2)
            {
                handler.intValue = 2;
                so.ApplyModifiedProperties();
                Debug.Log("[BINDERS] Active input handling set to Both.");
            }
        }

        // ------------------------------------------------------------------ XR

        static void ConfigureXr()
        {
            EditorBuildSettings.TryGetConfigObject(XRGeneralSettings.k_SettingsKey, out XRGeneralSettingsPerBuildTarget perTarget);
            if (perTarget == null)
            {
                Directory.CreateDirectory(Path.GetDirectoryName(XrSettingsPath));
                perTarget = ScriptableObject.CreateInstance<XRGeneralSettingsPerBuildTarget>();
                AssetDatabase.CreateAsset(perTarget, XrSettingsPath);
                EditorBuildSettings.AddConfigObject(XRGeneralSettings.k_SettingsKey, perTarget, true);
            }

            var settings = perTarget.SettingsForBuildTarget(BuildTargetGroup.Android);
            if (settings == null)
            {
                settings = ScriptableObject.CreateInstance<XRGeneralSettings>();
                settings.name = "Android Settings";
                perTarget.SetSettingsForBuildTarget(BuildTargetGroup.Android, settings);
                AssetDatabase.AddObjectToAsset(settings, perTarget);
            }
            if (settings.AssignedSettings == null)
            {
                var manager = ScriptableObject.CreateInstance<XRManagerSettings>();
                manager.name = "Android Providers";
                AssetDatabase.AddObjectToAsset(manager, perTarget);
                settings.AssignedSettings = manager;
            }
            settings.InitManagerOnStart = true;

            bool ok = XRPackageMetadataStore.AssignLoader(settings.AssignedSettings, "UnityEngine.XR.ARCore.ARCoreLoader", BuildTargetGroup.Android);
            Debug.Log($"[BINDERS] ARCore loader assigned: {ok}, loaders: {settings.AssignedSettings.activeLoaders.Count}");
            EditorUtility.SetDirty(settings);
            EditorUtility.SetDirty(settings.AssignedSettings);
            EditorUtility.SetDirty(perTarget);
        }

        /// <summary>
        /// ARCore "Optional", so the app also installs on phones without ARCore and offers the no-AR mode there.
        /// Done by reflection so a renamed setting cannot stop the build.
        /// </summary>
        static void ConfigureArCore()
        {
            try
            {
                var type = Type.GetType("UnityEditor.XR.ARCore.ARCoreSettings, Unity.XR.ARCore.Editor");
                var get = type?.GetMethod("GetOrCreateSettings", BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Static);
                var settings = get?.Invoke(null, null) as UnityEngine.Object;
                var prop = type?.GetProperty("requirement");
                if (settings == null || prop == null) { Debug.LogWarning("[BINDERS] ARCore settings not found; keeping defaults."); return; }
                prop.SetValue(settings, Enum.Parse(prop.PropertyType, "Optional"));
                EditorUtility.SetDirty(settings);
                Debug.Log("[BINDERS] ARCore requirement: Optional");
            }
            catch (Exception e)
            {
                Debug.LogWarning("[BINDERS] Could not set ARCore requirement: " + e.Message);
            }
        }
    }
}
