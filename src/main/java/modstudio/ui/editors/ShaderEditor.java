package modstudio.ui.editors;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import modstudio.build.GameDirs;
import modstudio.core.McVersion;
import modstudio.core.ShaderNodes;
import modstudio.core.Shaders;
import modstudio.core.Systems.Shader;
import modstudio.ui.Ui;
import modstudio.ui.controls.CodeEditor;
import modstudio.ui.controls.ShaderGraphView;
import modstudio.ui.controls.ShaderPreview;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Shader editor: picks the vanilla core program (or include file) to replace, the Minecraft versions it is written
 * for, and edits the GLSL with highlighting, helper snippets, quick checks and an import of the original source
 * from a Minecraft client installed on this computer. In node mode the colour is built from a node graph instead,
 * with a live preview; a core program then needs its original source, which the graph is applied to.
 */
final class ShaderEditor extends EditorBase<Shader> {
    private final CodeEditor fragment = new CodeEditor("glsl");
    private final CodeEditor vertex = new CodeEditor("glsl");
    private final TabPane tabs = new TabPane();
    private final Tab vertexTab = new Tab();
    private final Tab fragmentTab = new Tab();
    private final VBox checks = new VBox(4);
    private final Label usage = Ui.help("");
    private final CodeEditor generated = new CodeEditor("glsl");
    private final VBox graphChecks = new VBox(4);
    private ShaderGraphView graph;
    private ShaderPreview preview;
    private ComboBox<String> program;
    private ComboBox<String> minBox;
    private ComboBox<String> maxBox;

    ShaderEditor(Ctx c, Shader e) {
        super(c, e);
    }

    @Override
    protected void fill() {
        f.section(t("section.general"), t("help.shader"));
        nameRow();
        f.choice(t("field.shader_kind"), Shaders.KINDS, x -> t("shader.kind." + x), () -> e.kind, v -> {
            e.kind = v;
            program.getItems().setAll(Shaders.names(c.p, v));
            updateTabs();
        });
        f.choice(t("field.shader_mode"), List.of("code", "nodes"), x -> t("shader.mode." + x), () -> e.mode, v -> {
            // An include leaving node mode without code of its own keeps the generated GLSL to go on with by hand.
            if (v.equals("code") && e.graph() && e.kind.equals("include") && e.fragment.isBlank()) fragment.setText(ShaderNodes.fragment(e));
            e.mode = v;
            if (e.graph() && ShaderNodes.output(e.nodes) == null) {
                e.nodes.clear();
                e.nodes.addAll(ShaderNodes.starter());
                if (graph != null) graph.rebuild();
            }
        }, t("help.shader_mode"));
        program = MachineEditors.ref(f, t("field.shader_program"), Shaders.names(c.p, e.kind), false, () -> e.program, v -> e.program = v,
                t("help.shader_program"));
        List<String> versions = new ArrayList<>();
        versions.add("");
        for (McVersion v : McVersion.values()) versions.add(v.id);
        minBox = f.choice(t("field.shader_min"), versions, x -> x.isEmpty() ? t("shader.any_version") : x, () -> e.minVersion, v -> e.minVersion = v);
        maxBox = f.choice(t("field.shader_max"), versions, x -> x.isEmpty() ? t("shader.any_version") : x, () -> e.maxVersion, v -> e.maxVersion = v,
                t("help.shader_versions"));

        f.section(t("section.shader_graph"), t("help.shader_graph"));
        f.sectionShowIf(e::graph);
        if (e.graph() && ShaderNodes.output(e.nodes) == null) e.nodes.addAll(ShaderNodes.starter());
        graph = f.add(new ShaderGraphView(e.nodes, f::changed));
        f.add(graphChecks);

        f.section(t("section.shader_preview"), t("help.shader_preview"));
        f.sectionShowIf(e::graph);
        preview = f.add(new ShaderPreview(c.p, e.nodes));

        f.section(t("section.shader_generated"), t("help.shader_generated"));
        f.sectionShowIf(e::graph);
        generated.setEditable(false);
        generated.setPrefHeight(300);
        f.add(generated);

        f.section(t("section.code"), t("help.shader_code"));
        f.sectionShowIf(() -> !e.graph() || !e.kind.equals("include"));
        f.add(Ui.help(t("shader.base_help")));
        f.showIf(e::graph);
        MenuButton imp = new MenuButton(t("shader.import"));
        imp.setGraphic(Ui.icon("mdi2i-import"));
        imp.setOnShowing(ev -> fillImportMenu(imp));
        imp.getItems().add(new MenuItem("..."));
        MenuButton snippets = new MenuButton(t("shader.snippet"));
        snippets.setGraphic(Ui.icon("mdi2c-code-braces"));
        for (String s : Shaders.SNIPPETS.keySet()) {
            MenuItem mi = new MenuItem(t("shader.snippet." + s));
            mi.setOnAction(ev -> {
                CodeEditor target = tabs.getSelectionModel().getSelectedItem() == vertexTab ? vertex : fragment;
                target.setText(Shaders.insertSnippet(target.getText(), s));
            });
            snippets.getItems().add(mi);
        }
        HBox bar = Ui.hbox(8, imp, snippets, Ui.spacer(), usage);
        bar.setAlignment(Pos.CENTER_LEFT);
        f.add(bar);

        fragment.setText(e.fragment);
        vertex.setText(e.vertex);
        fragment.setPrefHeight(440);
        vertex.setPrefHeight(440);
        fragment.setOnTextChanged(v -> {
            e.fragment = v;
            f.changed();
        });
        vertex.setOnTextChanged(v -> {
            e.vertex = v;
            f.changed();
        });
        vertexTab.setContent(vertex);
        fragmentTab.setContent(fragment);
        for (Tab tb : List.of(fragmentTab, vertexTab)) tb.setClosable(false);
        tabs.getTabs().setAll(fragmentTab, vertexTab);
        f.add(tabs);
        f.add(checks);
        updateTabs();
    }

    private void updateTabs() {
        boolean include = e.kind.equals("include");
        fragmentTab.setText(include ? t("shader.source") + " (.glsl)" : t("shader.fragment") + " (.fsh)");
        vertexTab.setText(t("shader.vertex") + " (.vsh)");
        if (include) tabs.getTabs().remove(vertexTab);
        else if (!tabs.getTabs().contains(vertexTab)) tabs.getTabs().add(vertexTab);
    }

    /** Lists the client jars of the local launcher; picking one loads the original source of the program. */
    private void fillImportMenu(MenuButton menu) {
        menu.getItems().clear();
        List<Shaders.GameJar> jars = Shaders.installed(GameDirs.javaDefault());
        if (jars.isEmpty()) {
            MenuItem none = new MenuItem(t("shader.no_game"));
            none.setDisable(true);
            menu.getItems().add(none);
            return;
        }
        for (Shaders.GameJar j : jars) {
            MenuItem mi = new MenuItem(t("shader.import_from", j.version()));
            mi.setOnAction(ev -> importFrom(j.jar(), j.version()));
            menu.getItems().add(mi);
        }
    }

    private void importFrom(Path jar, String version) {
        String name = e.program.trim();
        if (name.isEmpty()) return;
        boolean hasCode = !e.fragment.isBlank() || !e.vertex.isBlank();
        if (hasCode && !Ui.confirm(c.window(), t("shader.import_replace_title"), t("shader.import_replace_text"))) return;
        try {
            String frag = Shaders.readFromJar(jar, Shaders.gamePath(e.kind, name, "fsh"));
            String vert = e.kind.equals("include") ? null : Shaders.readFromJar(jar, Shaders.gamePath(e.kind, name, "vsh"));
            if (frag == null && vert == null) {
                Ui.info(c.window(), t("shader.import_missing_title"), t("shader.import_missing", name, version));
                return;
            }
            if (frag != null) fragment.setText(frag);
            if (vert != null) vertex.setText(vert);
            McVersion v = McVersion.orNull(version);
            if (v != null && e.minVersion.isEmpty() && e.maxVersion.isEmpty()) {
                // The original source belongs to this version; keep the shader to it until the user widens the range.
                minBox.setValue(v.id);
                maxBox.setValue(v.id);
            }
            f.changed();
        } catch (Exception ex) {
            Ui.error(c.window(), t("shader.import_failed"), ex);
        }
    }

    @Override
    protected void refresh() {
        checks.getChildren().clear();
        graphChecks.getChildren().clear();
        boolean include = e.kind.equals("include");
        if (e.graph()) {
            preview.recompile();
            generated.setText(ShaderNodes.fragment(e));
            for (String k : ShaderNodes.check(e)) graphChecks.getChildren().add(Ui.label(t(k), "warning-text"));
        }
        if (!e.graph() || !include) addChecks(include ? t("shader.source") : t("shader.fragment"), Shaders.check(e.fragment, !include));
        if (!include) addChecks(t("shader.vertex"), Shaders.check(e.vertex, true));
        int n = 0;
        for (McVersion v : c.p.mcVersions()) if (Shaders.inRange(e, v)) n++;
        usage.setText(t("shader.exported_for", n, c.p.mcVersions().size()));
        if (!include) {
            List<String> missing = new ArrayList<>();
            for (McVersion v : c.p.mcVersions()) {
                if (Shaders.inRange(e, v) && !Shaders.programs(v).isEmpty() && !Shaders.programs(v).contains(e.program.trim())) missing.add(v.id);
            }
            if (!missing.isEmpty()) checks.getChildren().add(Ui.label(t("shader.not_in_version", e.program.trim(), String.join(", ", missing)), "warning-text"));
        }
    }

    private void addChecks(String part, List<String> problems) {
        for (String k : problems) checks.getChildren().add(Ui.label(part + ": " + t(k), "warning-text"));
    }
}
