package modstudio.ui.pages;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import modstudio.core.*;
import modstudio.i18n.I18n;
import modstudio.ui.Ui;
import modstudio.ui.WorkspaceView;
import modstudio.ui.controls.Form;
import modstudio.ui.controls.TextureField;
import modstudio.ui.controls.VersionPicker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Project settings: identity, platforms, toolchain versions and a content summary. */
public final class OverviewPage extends StackPane implements WorkspaceView.Page {
    private final WorkspaceView view;
    private final Project p;
    private final Form f;
    private final GridPane stats = new GridPane();
    private final VBox versions = new VBox(12);
    private final Label targetWarning = Ui.help("");
    private Label idLabel = new Label();

    /** Mod ID, plugin ID or namespace, depending on what the project makes. */
    private String idLabel() {
        return switch (ProjectScope.kind(p)) {
            case MOD, MIXED -> t("field.mod_id");
            case PLUGIN -> t("field.plugin_id");
            default -> t("field.namespace");
        };
    }

    public OverviewPage(WorkspaceView view) {
        this.view = view;
        this.p = view.ws.project;
        this.f = new Form(() -> {
            view.ws.changed();
            view.updateHeader();
        });
        build();
        getChildren().add(Ui.scroll(f));
    }

    private static String t(String k, Object... a) {
        return I18n.t(k, a);
    }

    @Override
    public void onShow() {
        updateStats();
    }

    private void build() {
        f.addTop(Ui.vbox(2, Ui.label(t("page.overview"), "title-2"), Ui.muted(t("overview.subtitle"))));

        // Contents summary
        stats.setHgap(10);
        stats.setVgap(10);
        f.section(t("overview.contents"));
        f.add(stats);
        updateStats();

        // Identity
        f.section(t("overview.identity"));
        f.text(t("field.project_name"), () -> p.meta.name, v -> p.meta.name = v);
        modIdRow();
        f.text(t("field.version"), () -> p.meta.version, v -> p.meta.version = v.trim(), t("help.version"));
        f.area(t("field.description"), () -> p.meta.description, v -> p.meta.description = v, 2, null);
        f.text(t("field.authors"), () -> p.meta.authors, v -> p.meta.authors = v);
        f.text(t("field.license"), () -> p.meta.license, v -> p.meta.license = v);
        f.text(t("field.website"), () -> p.meta.website, v -> p.meta.website = v.trim());
        TextField pkg = f.text(t("field.package"), () -> p.meta.packageName, v -> p.meta.packageName = v.trim(), t("help.package"));
        pkg.textProperty().addListener((o, a, b) -> Ui.setDanger(pkg, !Ids.PACKAGE.matcher(b.trim()).matches()));
        f.showIf(() -> ProjectScope.hasCode(p.targets()));
        f.row(t("field.icon"), new TextureField(view.ws, view.window(), p.meta.icon, () -> "icon", "gem", v -> {
            p.meta.icon = v;
            f.changed();
        }), t("help.icon"));

        // Platforms
        f.section(t("overview.platforms"), t("overview.platforms_help"));
        VersionPicker picker = new VersionPicker(p.mcVersions());
        picker.selectedProperty().addListener((o, a, b) -> {
            p.meta.mcVersions = new ArrayList<>(picker.ids());
            if (!p.meta.mcVersions.contains(p.meta.mcVersion)) p.meta.mcVersion = p.meta.mcVersions.get(p.meta.mcVersions.size() - 1);
            p.normalizeVersions();
            refreshTargets();
            rebuildVersions();
            f.changed();
        });
        f.row(t("field.mc_versions"), picker, t("help.mc_versions"));
        GridPane tg = new GridPane();
        tg.setHgap(16);
        tg.setVgap(8);
        int row = 0;
        List<CheckBox> boxes = new ArrayList<>();
        for (Target t : Target.values()) {
            CheckBox cb = new CheckBox(t("target." + t.id));
            cb.setSelected(p.meta.targets.contains(t.id));
            cb.setUserData(t);
            cb.selectedProperty().addListener((o, a, b) -> {
                List<String> list = new ArrayList<>();
                for (CheckBox x : boxes) if (x.isSelected()) list.add(((Target) x.getUserData()).id);
                p.meta.targets = list;
                refreshTargets();
                rebuildVersions();
                f.changed();
                updateStats();
                idLabel.setText(idLabel());
                view.updateNav();
            });
            boxes.add(cb);
            Label kind = Ui.label(t("kind_target." + t.kind.name().toLowerCase()), "chip");
            tg.add(cb, 0, row);
            tg.add(kind, 1, row);
            tg.add(Ui.help(t("target." + t.id + ".desc")), 2, row);
            row++;
        }
        f.row(t("field.targets"), new VBox(8, tg, targetWarning));
        f.row(t("overview.upgrade"), Ui.hbox(10,
                Ui.button(t("overview.upgrade_btn"), "mdi2a-arrow-up-bold-circle-outline", this::openUpgradeDialog),
                Ui.help(t("overview.upgrade_help", p.meta.sourceMcVersion.isBlank() ? p.meta.mcVersion : p.meta.sourceMcVersion))));
        refreshTargets();

        // Versions
        f.section(t("overview.toolchain"), t("overview.toolchain_help"));
        f.add(versions);
        f.sectionShowIf(() -> ProjectScope.hasCode(p.targets()) || p.targets().contains(Target.BEDROCK));
        rebuildVersions();

        // Dependencies
        VBox depsBox = new VBox(8);
        f.section(t("overview.dependencies"), t("overview.dependencies_help"));
        f.add(depsBox);
        f.add(Ui.button(t("overview.add_dependency"), "mdi2p-plus", () -> {
            p.meta.dependencies.add(new ModElements.ModDependency());
            rebuildDeps(depsBox);
            f.changed();
        }));
        f.sectionShowIf(() -> ProjectScope.hasCode(p.targets()));
        rebuildDeps(depsBox);

        // Maven Repositories, Libraries & Custom Gradle
        VBox reposBox = new VBox(8);
        VBox libsBox = new VBox(8);
        f.section(t("overview.java_build"), t("overview.java_build_help"));
        f.add(reposBox);
        f.add(Ui.button(t("overview.add_maven"), "mdi2p-plus", () -> {
            p.meta.mavenRepos.add("");
            rebuildRepos(reposBox);
            f.changed();
        }));
        f.add(libsBox);
        f.add(Ui.button(t("overview.add_library"), "mdi2p-plus", () -> {
            p.meta.libraries.add(new ModElements.GradleDep());
            rebuildLibs(libsBox);
            f.changed();
        }));
        f.text(t("field.jvm_args"), () -> p.meta.jvmArgs, v -> p.meta.jvmArgs = v, t("help.jvm_args"));
        f.area(t("field.custom_gradle"), () -> p.meta.customGradle, v -> p.meta.customGradle = v, 5, t("help.custom_gradle"));
        f.sectionShowIf(() -> ProjectScope.hasCode(p.targets()));
        rebuildRepos(reposBox);
        rebuildLibs(libsBox);
    }

    private void rebuildRepos(VBox reposBox) {
        reposBox.getChildren().clear();
        for (int i = 0; i < p.meta.mavenRepos.size(); i++) {
            int idx = i;
            TextField url = new TextField(p.meta.mavenRepos.get(idx));
            url.setPromptText("https://maven.blamejared.com");
            url.setPrefWidth(360);
            url.textProperty().addListener((o, a, b) -> {
                p.meta.mavenRepos.set(idx, b.trim());
                f.changed();
            });
            Button del = Ui.iconButton("mdi2c-close", t("action.delete"), () -> {
                p.meta.mavenRepos.remove(idx);
                rebuildRepos(reposBox);
                f.changed();
            });
            HBox row = Ui.hbox(8, url, del);
            row.setAlignment(Pos.CENTER_LEFT);
            reposBox.getChildren().add(row);
        }
    }

    private void rebuildLibs(VBox libsBox) {
        libsBox.getChildren().clear();
        for (var lib : p.meta.libraries) {
            ComboBox<String> scope = Ui.combo(List.of("implementation", "compileOnly", "runtimeOnly", "modImplementation", "modCompileOnly", "jarJar", "localJar"), s -> s);
            scope.setValue(lib.scope == null || lib.scope.isBlank() ? "implementation" : lib.scope);
            scope.valueProperty().addListener((o, a, b) -> { lib.scope = b; f.changed(); });
            ComboBox<String> tgt = Ui.combo(List.of("all", "mods", "forge", "neoforge", "fabric", "quilt", "paper", "folia"),
                    x -> x.equals("all") ? t("target.all") : t("target." + x));
            tgt.setValue(lib.target == null || lib.target.isBlank() ? "all" : lib.target);
            tgt.valueProperty().addListener((o, a, b) -> { lib.target = b; f.changed(); });
            TextField coord = new TextField(lib.coordinate == null ? "" : lib.coordinate);
            coord.setPromptText("group:artifact:version");
            coord.setPrefWidth(260);
            coord.textProperty().addListener((o, a, b) -> { lib.coordinate = b.trim(); f.changed(); });
            Button del = Ui.iconButton("mdi2c-close", t("action.delete"), () -> {
                p.meta.libraries.remove(lib);
                rebuildLibs(libsBox);
                f.changed();
            });
            HBox row = Ui.hbox(8, scope, tgt, coord, del);
            row.setAlignment(Pos.CENTER_LEFT);
            libsBox.getChildren().add(row);
        }
    }

    private void rebuildDeps(VBox depsBox) {
        depsBox.getChildren().clear();
        for (var dep : p.meta.dependencies) {
            TextField id = new TextField(dep.modId);
            id.setPromptText("modid");
            id.setPrefWidth(160);
            id.textProperty().addListener((o, a, b) -> { dep.modId = b.trim(); f.changed(); });
            TextField ver = new TextField(dep.version);
            ver.setPromptText("*");
            ver.setPrefWidth(120);
            ver.textProperty().addListener((o, a, b) -> { dep.version = b.trim(); f.changed(); });
            CheckBox req = new CheckBox(t("field.required"));
            req.setSelected(dep.required);
            req.selectedProperty().addListener((o, a, b) -> { dep.required = b; f.changed(); });
            Button del = Ui.iconButton("mdi2c-close", t("action.delete"), () -> {
                p.meta.dependencies.remove(dep);
                rebuildDeps(depsBox);
                f.changed();
            });
            HBox row = Ui.hbox(8, id, ver, req, del);
            row.setAlignment(Pos.CENTER_LEFT);
            depsBox.getChildren().add(row);
        }
    }

    private void modIdRow() {
        TextField tf = new TextField(p.meta.modId);
        Label err = Ui.errorLabel();
        Runnable commit = () -> {
            String n = tf.getText().trim();
            if (n.equals(p.meta.modId)) return;
            if (!Ids.MODID.matcher(n).matches()) {
                err.setText(t("problem.modid_invalid", n));
                Ui.setDanger(tf, true);
                return;
            }
            err.setText("");
            Ui.setDanger(tf, false);
            String old = p.meta.modId;
            Refactor.renameNamespace(p, old, n);
            p.meta.modId = n;
            view.ws.structureChanged();
            view.invalidateAllExceptCurrent();
        };
        tf.textProperty().addListener((o, a, b) -> {
            boolean bad = !Ids.MODID.matcher(b.trim()).matches();
            Ui.setDanger(tf, bad);
            err.setText(bad ? t("problem.modid_invalid", b.trim()) : "");
        });
        tf.setOnAction(e -> commit.run());
        tf.focusedProperty().addListener((o, a, b) -> {
            if (!b) commit.run();
        });
        Node r = f.row(idLabel(), new VBox(4, tf, err), t("help.mod_id_change"));
        if (r instanceof HBox box && box.getChildren().get(0) instanceof Label l) idLabel = l;
    }

    private void refreshTargets() {
        List<String> notes = new ArrayList<>();
        for (String id : p.meta.targets) {
            Target t = Target.of(id);
            if (t == null) continue;
            for (McVersion v : p.mcVersions()) {
                if (!t.supports(v)) notes.add(t("problem.version_unsupported", t("target." + id), v.id));
            }
        }
        if (p.meta.targets.isEmpty()) notes.add(t("problem.no_targets"));
        targetWarning.setText(String.join("\n", notes));
        targetWarning.getStyleClass().remove("warning-text");
        if (!notes.isEmpty()) targetWarning.getStyleClass().add("warning-text");
        updateStats();
        view.updateHeader();
        view.invalidatePages("build", "play", "compat");
    }

    private void rebuildVersions() {
        versions.getChildren().clear();
        for (Build build : p.builds()) {
            Target t = build.target();
            Map<String, String> defs = Versions.defaults(t, build.mc());
            if (defs.isEmpty()) continue;
            String vkey = Project.versionKey(t, build.mc());
            GridPane g = new GridPane();
            g.setHgap(10);
            g.setVgap(6);
            int r = 0;
            for (var en : defs.entrySet()) {
                String key = en.getKey();
                TextField tf = new TextField(p.versions.getOrDefault(vkey, Map.of()).getOrDefault(key, ""));
                tf.setPromptText(en.getValue());
                tf.setPrefColumnCount(22);
                tf.textProperty().addListener((o, a, b) -> {
                    var m = p.versions.computeIfAbsent(vkey, k -> new java.util.LinkedHashMap<>());
                    if (b.isBlank()) m.remove(key);
                    else m.put(key, b.trim());
                    if (m.isEmpty()) p.versions.remove(vkey);
                    f.changed();
                });
                Label l = Ui.label(I18n.tOr("ver." + key, key), "form-label");
                l.setMinWidth(160);
                g.addRow(r++, l, tf);
            }
            String title = t("target." + t.id) + (t.versioned() ? "  " + build.mc().id : "");
            VBox box = new VBox(6, Ui.label(title, "subsection-title"), g);
            versions.getChildren().add(box);
        }
        if (versions.getChildren().isEmpty()) versions.getChildren().add(Ui.help(t("overview.no_toolchain")));
    }

    private void updateStats() {
        stats.getChildren().clear();
        int i = 0;
        for (ElementType type : ElementType.values()) {
            if (!ProjectScope.available(p, type)) continue;
            int n = p.ofType(type).size();
            Label count = Ui.label(String.valueOf(n), "stat-number");
            Label name = Ui.label(I18n.t("types." + type.id), "text-muted", "text-small");
            VBox tile = new VBox(2, Ui.hbox(8, Ui.icon(Ui.typeIcon(type), 18), count), name);
            tile.getStyleClass().add("stat-tile");
            tile.setPadding(new Insets(10, 12, 10, 12));
            tile.setMinWidth(130);
            tile.setOnMouseClicked(e -> view.show(type.id));
            stats.add(tile, i % 6, i / 6);
            i++;
        }
        Label tex = Ui.label(String.valueOf(p.textures.size()), "stat-number");
        VBox tile = new VBox(2, Ui.hbox(8, Ui.icon("mdi2p-palette-outline", 18), tex), Ui.label(t("page.textures"), "text-muted", "text-small"));
        tile.getStyleClass().add("stat-tile");
        tile.setPadding(new Insets(10, 12, 10, 12));
        tile.setOnMouseClicked(e -> view.show("textures"));
        stats.add(tile, i % 6, i / 6);
    }

    private void openUpgradeDialog() {
        Dialog<ButtonType> d = new Dialog<>();
        Ui.styleDialog(d, view.window());
        d.setTitle(t("overview.upgrade_title"));
        d.setHeaderText(t("overview.upgrade_header", p.meta.sourceMcVersion.isBlank() ? p.meta.mcVersion : p.meta.sourceMcVersion));
        ButtonType ok = new ButtonType(t("overview.upgrade_apply"), ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, new ButtonType(t("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE));

        ComboBox<McVersion> ver = new ComboBox<>();
        ver.getItems().setAll(McVersion.values());
        ver.setValue(McVersion.latest());

        ComboBox<String> loader = Ui.combo(List.of("keep", "neoforge", "fabric", "quilt", "neoforge+fabric", "datapack"),
                x -> t("overview.upgrade_loader." + x));
        loader.setValue("keep");

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.addRow(0, new Label(t("overview.upgrade_target_mc")), ver);
        g.addRow(1, new Label(t("overview.upgrade_target_loader")), loader);
        d.getDialogPane().setContent(Ui.vbox(10, g, Ui.help(t("overview.upgrade_note"))));

        d.showAndWait().filter(bt -> bt == ok).ifPresent(bt -> {
            McVersion targetMc = ver.getValue();
            List<Target> nextTargets = switch (loader.getValue()) {
                case "neoforge" -> List.of(Target.NEOFORGE);
                case "fabric" -> List.of(Target.FABRIC);
                case "quilt" -> List.of(Target.QUILT);
                case "neoforge+fabric" -> List.of(Target.NEOFORGE, Target.FABRIC);
                case "datapack" -> List.of(Target.DATAPACK);
                default -> null;
            };
            ProjectUpgrader.Report rep = ProjectUpgrader.upgrade(p, targetMc, nextTargets);
            view.ws.structureChanged();
            view.invalidateAllExceptCurrent();
            view.show("overview");
            StringBuilder body = new StringBuilder();
            for (String c : rep.changes()) body.append("• ").append(c).append('\n');
            if (!rep.warnings().isEmpty()) {
                body.append('\n');
                for (String w : rep.warnings()) body.append("! ").append(w).append('\n');
            }
            Ui.info(view.window(), t("overview.upgrade_done", targetMc.id), body.toString());
        });
    }
}
