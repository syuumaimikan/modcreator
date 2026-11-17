package modstudio.ui;

import modstudio.ui.controls.VersionPicker;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import modstudio.core.*;
import modstudio.i18n.I18n;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Collects the basics of a new project. */
public final class NewProjectDialog {
    private NewProjectDialog() {}

    public record Result(Project project, Path file) {}

    /** What the project makes, with the targets each choice selects ("custom" keeps the user's own choice). */
    private static final Map<String, List<Target>> KINDS = new LinkedHashMap<>();

    static {
        KINDS.put("mod", List.of(Target.FABRIC, Target.NEOFORGE));
        KINDS.put("datapack", List.of(Target.DATAPACK, Target.RESOURCEPACK));
        KINDS.put("resourcepack", List.of(Target.RESOURCEPACK));
        KINDS.put("plugin", List.of(Target.PAPER, Target.RESOURCEPACK));
        KINDS.put("bedrock", List.of(Target.BEDROCK));
        KINDS.put("custom", List.of());
    }

    private static String defaultName(ProjectScope.Kind k) {
        return switch (k) {
            case DATAPACK -> "My Data Pack";
            case RESOURCEPACK -> "My Resource Pack";
            case PLUGIN -> "My Plugin";
            case BEDROCK -> "My Add-on";
            default -> "My Mod";
        };
    }

    public static Optional<Result> show(MainWindow main) {
        Dialog<Result> d = new Dialog<>();
        Ui.styleDialog(d, main.stage);
        d.setTitle(I18n.t("newproject.title"));
        d.setHeaderText(I18n.t("newproject.title"));

        TextField name = new TextField("My Mod");
        TextField modId = new TextField("mymod");
        TextField pkg = new TextField("com.example.mymod");
        TextField author = new TextField(System.getProperty("user.name", ""));
        VersionPicker mc = new VersionPicker(List.of(McVersion.V1_21_1));
        TextField location = new TextField(AppSettings.projectsDir().toString());
        Button browse = Ui.button(I18n.t("action.browse"), "mdi2f-folder-open", () -> {
            DirectoryChooser dc = new DirectoryChooser();
            Path cur = Path.of(location.getText());
            if (Files.isDirectory(cur)) dc.setInitialDirectory(cur.toFile());
            var f = dc.showDialog(d.getDialogPane().getScene().getWindow());
            if (f != null) location.setText(f.getAbsolutePath());
        });
        HBox locRow = new HBox(8, location, browse);
        HBox.setHgrow(location, Priority.ALWAYS);

        Map<Target, CheckBox> targets = new LinkedHashMap<>();
        GridPane tgrid = new GridPane();
        tgrid.setHgap(16);
        tgrid.setVgap(6);
        ColumnConstraints tc0 = new ColumnConstraints();
        tc0.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints tc1 = new ColumnConstraints();
        tc1.setHgrow(Priority.ALWAYS);
        tgrid.getColumnConstraints().addAll(tc0, tc1);
        int i = 0;
        for (Target t : Target.values()) {
            CheckBox cb = new CheckBox(I18n.t("target." + t.id));
            cb.setSelected(t == Target.NEOFORGE || t == Target.FABRIC);
            cb.setTooltip(new Tooltip(I18n.t("target." + t.id + ".desc")));
            targets.put(t, cb);
            cb.setMinWidth(Region.USE_PREF_SIZE);
            Label desc = Ui.help(I18n.t("target." + t.id + ".desc"));
            desc.setWrapText(true);
            tgrid.add(cb, 0, i);
            tgrid.add(desc, 1, i);
            i++;
        }
        Runnable syncTargets = () -> {
            for (var e : targets.entrySet()) {
                boolean ok = mc.selected().stream().anyMatch(v -> e.getKey().supports(v));
                e.getValue().setDisable(!ok);
                if (!ok) e.getValue().setSelected(false);
            }
        };
        mc.selectedProperty().addListener((o, a, b) -> syncTargets.run());
        syncTargets.run();

        // What the project makes: picks the targets and the wording of the fields below.
        ToggleGroup kindGroup = new ToggleGroup();
        javafx.scene.layout.FlowPane kindBox = new javafx.scene.layout.FlowPane(6, 6);
        Label kindHelp = Ui.help("");
        boolean[] applyingKind = {false};
        for (var k : KINDS.entrySet()) {
            ToggleButton tb = new ToggleButton(I18n.t("newproject.kind." + k.getKey()));
            tb.setUserData(k.getKey());
            tb.setToggleGroup(kindGroup);
            tb.setSelected(k.getKey().equals("mod"));
            kindBox.getChildren().add(tb);
        }
        kindHelp.setText(I18n.t("newproject.kind.mod.desc"));
        kindGroup.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) {
                // keep one choice selected
                if (a != null) a.setSelected(true);
                return;
            }
            String k = (String) b.getUserData();
            kindHelp.setText(I18n.t("newproject.kind." + k + ".desc"));
            List<Target> pick = KINDS.get(k);
            if (pick.isEmpty()) return;
            applyingKind[0] = true;
            for (var e : targets.entrySet()) if (!e.getValue().isDisabled()) e.getValue().setSelected(pick.contains(e.getKey()));
            applyingKind[0] = false;
            syncTargets.run();
        });
        // choosing targets by hand switches to the custom choice
        targets.values().forEach(cb -> cb.selectedProperty().addListener((o, a, b) -> {
            if (applyingKind[0]) return;
            for (Toggle tg : kindGroup.getToggles()) if ("custom".equals(tg.getUserData())) tg.setSelected(true);
        }));

        // Keep mod id and package in sync with the name until the user edits them.
        boolean[] modIdEdited = {false};
        boolean[] pkgEdited = {false};
        boolean[] nameEdited = {false};
        boolean[] syncing = {false};
        name.textProperty().addListener((o, a, b) -> {
            if (!syncing[0]) nameEdited[0] = true;
            if (!modIdEdited[0]) {
                syncing[0] = true;
                String id = Ids.toId(b).replace("_", "");
                if (!id.isEmpty() && Character.isDigit(id.charAt(0))) id = "m" + id;
                modId.setText(id);
                syncing[0] = false;
            }
        });
        modId.textProperty().addListener((o, a, b) -> {
            if (!syncing[0]) modIdEdited[0] = true;
            if (!pkgEdited[0]) {
                syncing[0] = true;
                String authorPart = Ids.toId(author.getText()).replace("_", "");
                if (authorPart.isEmpty() || !Character.isLetter(authorPart.charAt(0))) authorPart = "example";
                pkg.setText("com." + authorPart + "." + b);
                syncing[0] = false;
            }
        });
        pkg.textProperty().addListener((o, a, b) -> {
            if (!syncing[0]) pkgEdited[0] = true;
        });
        author.textProperty().addListener((o, a, b) -> {
            if (!pkgEdited[0]) {
                syncing[0] = true;
                String authorPart = Ids.toId(b).replace("_", "");
                if (authorPart.isEmpty() || !Character.isLetter(authorPart.charAt(0))) authorPart = "example";
                pkg.setText("com." + authorPart + "." + modId.getText());
                syncing[0] = false;
            }
        });

        Label fileLabel = Ui.help("");
        Label err = Ui.help("");
        err.getStyleClass().add("error-text");

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setMinWidth(140);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        c1.setPrefWidth(460);
        g.getColumnConstraints().addAll(c0, c1);
        int r = 0;
        r = row(g, r, I18n.t("newproject.kind"), new VBox(4, kindBox, kindHelp), null);
        r = row(g, r, I18n.t("field.project_name"), name, null);
        Label idLabel = new Label(I18n.t("field.mod_id"));
        Label idHelp = Ui.help(I18n.t("help.mod_id"));
        r = row(g, r, idLabel, new VBox(4, modId, idHelp));
        Label pkgLabel = new Label(I18n.t("field.package"));
        VBox pkgBox = new VBox(4, pkg, Ui.help(I18n.t("help.package")));
        r = row(g, r, pkgLabel, pkgBox);
        r = row(g, r, I18n.t("field.authors"), author, null);
        r = row(g, r, I18n.t("field.mc_versions"), mc, I18n.t("help.mc_versions"));
        r = row(g, r, I18n.t("field.targets"), tgrid, null);
        // the wording follows what is being made: a data pack has a namespace and no Java package
        Runnable wording = () -> {
            List<Target> chosen = new ArrayList<>();
            targets.forEach((t, cb) -> {
                if (cb.isSelected()) chosen.add(t);
            });
            ProjectScope.Kind k = ProjectScope.kind(chosen);
            switch (k) {
                case MOD, MIXED -> {
                    idLabel.setText(I18n.t("field.mod_id"));
                    idHelp.setText(I18n.t("help.mod_id"));
                }
                case PLUGIN -> {
                    idLabel.setText(I18n.t("field.plugin_id"));
                    idHelp.setText(I18n.t("help.plugin_id"));
                }
                default -> {
                    idLabel.setText(I18n.t("field.namespace"));
                    idHelp.setText(I18n.t("help.namespace"));
                }
            }
            boolean code = ProjectScope.hasCode(chosen);
            for (Node n : List.of(pkgLabel, pkgBox)) {
                n.setVisible(code);
                n.setManaged(code);
            }
            if (!nameEdited[0] && !chosen.isEmpty()) {
                syncing[0] = true;
                name.setText(defaultName(k));
                syncing[0] = false;
            }
            var scene = d.getDialogPane().getScene();
            if (scene != null && scene.getWindow() != null) scene.getWindow().sizeToScene();
        };
        targets.values().forEach(cb -> cb.selectedProperty().addListener((o, a, b) -> Platform.runLater(wording)));
        r = row(g, r, I18n.t("field.location"), new VBox(4, locRow, fileLabel), null);
        g.add(err, 1, r);

        d.getDialogPane().setContent(g);
        ButtonType create = new ButtonType(I18n.t("action.create"), ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(create, new ButtonType(I18n.t("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE));
        Node createB = d.getDialogPane().lookupButton(create);

        Runnable validate = () -> {
            String id = modId.getText().trim();
            Path file = Path.of(location.getText().trim().isEmpty() ? "." : location.getText().trim()).resolve(id).resolve(id + ".msproj");
            fileLabel.setText(I18n.t("newproject.file", file));
            String msg = null;
            if (name.getText().isBlank()) msg = I18n.t("newproject.err_name");
            else if (!Ids.MODID.matcher(id).matches()) msg = I18n.t("problem.modid_invalid", id);
            else if (hasCode(targets) && !Ids.PACKAGE.matcher(pkg.getText().trim()).matches()) msg = I18n.t("problem.package_invalid", pkg.getText());
            else if (targets.values().stream().noneMatch(CheckBox::isSelected)) msg = I18n.t("problem.no_targets");
            else if (location.getText().isBlank()) msg = I18n.t("newproject.err_location");
            else if (Files.exists(file)) msg = I18n.t("newproject.err_exists");
            err.setText(msg == null ? "" : msg);
            Ui.setDanger(modId, msg != null && !Ids.MODID.matcher(id).matches());
            Ui.setDanger(pkg, !Ids.PACKAGE.matcher(pkg.getText().trim()).matches());
            createB.setDisable(msg != null);
        };
        for (TextField tf : List.of(name, modId, pkg, author, location)) tf.textProperty().addListener((o, a, b) -> validate.run());
        mc.selectedProperty().addListener((o, a, b) -> validate.run());
        targets.values().forEach(cb -> cb.selectedProperty().addListener((o, a, b) -> validate.run()));
        validate.run();

        d.setResultConverter(bt -> {
            if (bt != create) return null;
            Project p = new Project();
            p.meta.name = name.getText().trim();
            p.meta.modId = modId.getText().trim();
            p.meta.packageName = pkg.getText().trim();
            p.meta.authors = author.getText().trim();
            p.meta.mcVersions = new ArrayList<>(mc.ids());
            p.meta.mcVersion = mc.ids().get(mc.ids().size() - 1);
            p.normalizeVersions();
            List<String> ts = new ArrayList<>();
            targets.forEach((t, cb) -> {
                if (cb.isSelected()) ts.add(t.id);
            });
            p.meta.targets = ts;
            String gameLang = switch (I18n.language()) {
                case "ja" -> "ja_jp";
                case "zh" -> "zh_cn";
                case "ko" -> "ko_kr";
                default -> null;
            };
            if (gameLang != null) p.meta.languages.add(gameLang);
            Path file = Path.of(location.getText().trim()).resolve(p.meta.modId).resolve(p.meta.modId + ".msproj");
            return new Result(p, file);
        });
        return d.showAndWait();
    }

    private static boolean hasCode(Map<Target, CheckBox> targets) {
        List<Target> chosen = new ArrayList<>();
        targets.forEach((t, cb) -> {
            if (cb.isSelected()) chosen.add(t);
        });
        return ProjectScope.hasCode(chosen);
    }

    private static int row(GridPane g, int r, Label l, Node control) {
        l.getStyleClass().add("form-label");
        l.setMinWidth(Region.USE_PREF_SIZE);
        g.add(l, 0, r);
        g.add(control, 1, r);
        GridPane.setValignment(l, javafx.geometry.VPos.TOP);
        GridPane.setMargin(l, new Insets(6, 0, 0, 0));
        return r + 1;
    }

    private static int row(GridPane g, int r, String label, Node control, String help) {
        Label l = new Label(label);
        l.getStyleClass().add("form-label");
        g.add(l, 0, r);
        if (help != null) {
            g.add(new VBox(4, control, Ui.help(help)), 1, r);
        } else {
            g.add(control, 1, r);
        }
        GridPane.setValignment(l, javafx.geometry.VPos.TOP);
        GridPane.setMargin(l, new Insets(6, 0, 0, 0));
        return r + 1;
    }
}
