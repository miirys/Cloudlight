using Microsoft.Win32;
using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Navigation;

namespace OpenNow.Playnite
{
    public partial class OpenNowSettingsView : UserControl
    {
        public OpenNowSettingsView()
        {
            InitializeComponent();
        }

        private void BrowseButton_Click(object sender, RoutedEventArgs e)
        {
            var dialog = new OpenFileDialog
            {
                Filter = "Cloudlight|Cloudlight.exe;OpenNOW.exe|Executables|*.exe|All files|*.*",
                Title = "Select Cloudlight executable",
            };

            if (dialog.ShowDialog() == true && DataContext is OpenNowSettingsViewModel viewModel)
            {
                viewModel.Settings.OpenNowExecutablePath = dialog.FileName;
            }
        }

        private void HelpLink_RequestNavigate(object sender, RequestNavigateEventArgs e)
        {
            Process.Start(new ProcessStartInfo(e.Uri.AbsoluteUri) { UseShellExecute = true });
            e.Handled = true;
        }
    }
}
