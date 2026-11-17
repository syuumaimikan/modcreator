package modstudio;

import javafx.application.Application;
import javafx.application.HostServices;
import javafx.stage.Stage;
import modstudio.i18n.I18n;
import modstudio.ui.AppSettings;
import modstudio.ui.MainWindow;
import modstudio.ui.Snapshotter;
import modstudio.ui.Ui;

import java.nio.file.Files;
import java.nio.file.Path;

/** JavaFX application. Options: [project.msproj] --lang=xx --theme=light|dark --snapshot=dir */
public class App extends Application {
    private static String[] launchArgs = new String[0];
    private static App instance;

    public static void launchApp(String[] args) {
        launchArgs = args;
        Application.launch(App.class);
    }

    public static HostServices hosts() {
        return instance.getHostServices();
    }

    @Override
    public void start(Stage stage) {
        instance = this;
        String snapshot = null, lang = null, theme = null, file = null;
        for (String a : launchArgs) {
            if (a.startsWith("--snapshot=")) snapshot = a.substring(11);
            else if (a.startsWith("--lang=")) lang = a.substring(7);
            else if (a.startsWith("--theme=")) theme = a.substring(8);
            else if (!a.startsWith("--")) file = a;
        }
        AppSettings settings = AppSettings.load();
        if (snapshot != null) settings.transientMode = true;
        if (lang != null) settings.language = lang;
        if (theme != null) settings.theme = theme;
        I18n.setLanguage(settings.language);
        Ui.applyTheme(settings.theme);

        MainWindow window = new MainWindow(stage, settings);
        window.show();
        if (snapshot != null) {
            new Snapshotter(window, Path.of(snapshot)).start();
        } else if (file != null && Files.exists(Path.of(file))) {
            window.open(Path.of(file));
        }
    }
}
