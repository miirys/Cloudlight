using System;
using System.Collections.Generic;
using System.IO;

namespace OpenNow.Playnite.Services
{
    internal static class OpenNowPath
    {
        // Cloudlight is the current name. Installations from before the rename keep
        // their OpenNOW folder (an upgrade reuses the registered install location)
        // and may hold either executable name, so both are still searched.
        internal static readonly string[] ProductNames = { "Cloudlight", "OpenNOW" };
        internal static readonly string[] ExecutableNames = { "Cloudlight.exe", "OpenNOW.exe" };

        public static IEnumerable<string> GetDefaultCandidatePaths()
        {
            var localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            foreach (var product in ProductNames)
            {
                foreach (var candidate in GetInstalledExecutables(Path.Combine(localAppData, product)))
                {
                    yield return candidate;
                }
                foreach (var candidate in GetInstalledExecutables(Path.Combine(localAppData, "Programs", product)))
                {
                    yield return candidate;
                }
            }
            yield return Path.Combine(localAppData, "Programs", "OpenNOW", "OpenNOW.exe");
            yield return Path.Combine(localAppData, "OpenNOW", "OpenNOW.exe");

            var programFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
            foreach (var candidate in GetMachineCandidatePaths(programFiles))
            {
                yield return candidate;
            }

            var programFilesX86 = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86);
            foreach (var candidate in GetMachineCandidatePaths(programFilesX86))
            {
                yield return candidate;
            }
        }

        internal static IEnumerable<string> GetMachineCandidatePaths(string directory)
        {
            if (string.IsNullOrWhiteSpace(directory))
            {
                yield break;
            }
            foreach (var product in ProductNames)
            {
                foreach (var candidate in GetInstalledExecutables(Path.Combine(directory, product)))
                {
                    yield return candidate;
                }
                foreach (var installation in GetVersionedInstallations(directory, product))
                {
                    foreach (var candidate in GetInstalledExecutables(installation))
                    {
                        yield return candidate;
                    }
                }
                foreach (var channel in new[] { " Nightly", " Supporter" })
                {
                    foreach (var candidate in GetInstalledExecutables(Path.Combine(directory, product + channel)))
                    {
                        yield return candidate;
                    }
                }
            }
            yield return Path.Combine(directory, "OpenNOW", "OpenNOW.exe");
        }

        private static IEnumerable<string> GetInstalledExecutables(string installation)
        {
            foreach (var executable in ExecutableNames)
            {
                yield return Path.Combine(installation, "bin", executable);
            }
        }

        private static List<string> GetVersionedInstallations(string directory, string product)
        {
            var prefix = product + " ";
            var versioned = new List<KeyValuePair<Version, string>>();
            try
            {
                if (Directory.Exists(directory))
                {
                    foreach (var path in Directory.GetDirectories(directory, prefix + "*"))
                    {
                        if (Version.TryParse(Path.GetFileName(path).Substring(prefix.Length), out var version))
                        {
                            versioned.Add(new KeyValuePair<Version, string>(version, path));
                        }
                    }
                }
            }
            catch (IOException)
            {
                versioned.Clear();
            }
            catch (UnauthorizedAccessException)
            {
                versioned.Clear();
            }
            versioned.Sort((left, right) => right.Key.CompareTo(left.Key));
            var installations = new List<string>();
            foreach (var installation in versioned)
            {
                installations.Add(installation.Value);
            }
            return installations;
        }

        public static string ResolveExecutablePath(string configuredPath)
        {
            if (!string.IsNullOrWhiteSpace(configuredPath) && File.Exists(configuredPath))
            {
                return configuredPath;
            }

            foreach (var candidate in GetDefaultCandidatePaths())
            {
                if (File.Exists(candidate))
                {
                    return candidate;
                }
            }

            return null;
        }
    }
}
