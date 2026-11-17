package modstudio.ui.editors;

import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import modstudio.core.CommandSpec;
import modstudio.core.Elements;
import modstudio.core.Elements.*;
import modstudio.core.Ids;
import modstudio.core.Logic;
import modstudio.core.McVersion;
import modstudio.core.Target;
import modstudio.gen.BridgeGen;
import modstudio.gen.GenContext;
import modstudio.gen.InterfaceGen;
import modstudio.ui.Ui;
import modstudio.ui.controls.*;

import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/** Editors for the smaller element kinds. */
final class SimpleEditors {
    private SimpleEditors() {}

    // ------------------------------------------------------------------ creative tab

    static final class TabEditor extends EditorBase<CreativeTab> {
        TabEditor(Ctx c, CreativeTab e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.creative_tab"));
            nameRow();
            f.text(t("field.display_name"), () -> e.displayName, v -> e.displayName = v);
            localized("name", t("field.display_name"), () -> e.displayName);
            f.id(t("field.icon"), IdField.Kind.ITEM, () -> e.icon, v -> e.icon = v, env());
            long members = c.p.all(Item.class).stream().filter(i -> i.creativeTab.equals("tab:" + e.name)).count()
                    + c.p.all(Block.class).stream().filter(b -> b.creativeTab.equals("tab:" + e.name)).count();
            f.add(Ui.help(t("editor.tab_members", members)));
        }
    }

    // ------------------------------------------------------------------ sound

    static final class SoundEditor extends EditorBase<Sound> {
        private final ListView<SoundFile> files = new ListView<>();

        SoundEditor(Ctx c, Sound e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"));
            nameRow();
            f.text(t("field.subtitle"), () -> e.subtitle, v -> e.subtitle = v, t("help.subtitle"));
            localized("subtitle", t("field.subtitle"), () -> e.subtitle);
            f.choice(t("field.sound_category"), List.of("master", "music", "record", "weather", "block", "hostile", "neutral", "player", "ambient", "voice"),
                    x -> t("choice." + x), () -> e.category, v -> e.category = v);
            f.check(t("field.stream"), () -> e.stream, v -> e.stream = v, t("help.stream"));

            f.section(t("section.sound_files"), t("help.sound_files"));
            files.getItems().setAll(e.files);
            files.setPrefHeight(160);
            files.setPlaceholder(Ui.muted(t("sound.no_files")));
            files.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(SoundFile s, boolean empty) {
                    super.updateItem(s, empty);
                    if (empty || s == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    long bytes = (long) (s.data.length() * 0.75);
                    setText(s.fileName + "   " + (bytes / 1024) + " KB");
                    setGraphic(Ui.icon("mdi2m-music-note-outline"));
                }
            });
            Button add = Ui.button(t("sound.import"), "mdi2i-import", this::importFiles);
            Button remove = Ui.button(t("action.remove"), "mdi2d-delete-outline", () -> {
                SoundFile s = files.getSelectionModel().getSelectedItem();
                if (s == null) return;
                e.files.remove(s);
                files.getItems().setAll(e.files);
                f.changed();
            });
            remove.disableProperty().bind(files.getSelectionModel().selectedItemProperty().isNull());
            f.add(files);
            f.add(Ui.hbox(8, add, remove));
        }

        private void importFiles() {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Ogg Vorbis", "*.ogg"));
            var list = fc.showOpenMultipleDialog(c.window());
            if (list == null) return;
            try {
                for (var file : list) {
                    SoundFile s = new SoundFile();
                    s.fileName = Ids.toId(file.getName().replaceFirst("(?i)\\.ogg$", "")) + ".ogg";
                    s.data = Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath()));
                    e.files.add(s);
                }
                files.getItems().setAll(e.files);
                f.changed();
            } catch (Exception ex) {
                Ui.error(c.window(), t("sound.import_failed"), ex);
            }
        }
    }

    // ------------------------------------------------------------------ tag

    static final class TagEditor extends EditorBase<Tag> {
        private final ListView<String> values = new ListView<>();

        TagEditor(Ctx c, Tag e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"));
            nameRow();
            f.choice(t("field.registry"), List.of("item", "block", "entity_type", "function", "fluid"), x -> t("registry." + x),
                    () -> e.registry, v -> e.registry = v);
            TextField ns = f.text(t("field.namespace"), () -> e.namespace, v -> e.namespace = v.trim(), t("help.tag_namespace"));
            ns.setPromptText(c.ns());
            f.check(t("field.replace"), () -> e.replace, v -> e.replace = v, t("help.replace"));

            f.section(t("section.values"));
            values.getItems().setAll(e.values);
            values.setPrefHeight(240);
            values.setPlaceholder(Ui.muted(t("tag.empty")));
            values.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(String s, boolean empty) {
                    super.updateItem(s, empty);
                    if (empty || s == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    setText(s);
                    var img = c.ws.itemImage(s, 16);
                    if (img != null) {
                        ImageView iv = new ImageView(img);
                        iv.setSmooth(false);
                        setGraphic(iv);
                    } else setGraphic(null);
                }
            });
            IdField input = new IdField(env(), Catalog.forRegistry(e.registry), "", v -> {
            });
            HBox.setHgrow(input, Priority.ALWAYS);
            Button add = Ui.button(t("action.add"), "mdi2p-plus", () -> {
                String v = input.getValue();
                if (v.isEmpty()) return;
                String full = Ids.full(v);
                if (!e.values.contains(full)) e.values.add(full);
                values.getItems().setAll(e.values);
                input.setValue("");
                f.changed();
            });
            input.field().setOnAction(x -> add.fire());
            Button remove = Ui.button(t("action.remove"), "mdi2d-delete-outline", () -> {
                String s = values.getSelectionModel().getSelectedItem();
                if (s == null) return;
                e.values.remove(s);
                values.getItems().setAll(e.values);
                f.changed();
            });
            remove.disableProperty().bind(values.getSelectionModel().selectedItemProperty().isNull());
            f.add(Ui.hbox(8, input, add));
            f.add(values);
            f.add(Ui.hbox(8, remove));
            f.add(Ui.help(t("help.tag_values")));
        }
    }

    // ------------------------------------------------------------------ function

    static final class FunctionEditor extends EditorBase<Function> {
        FunctionEditor(Ctx c, Function e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"));
            nameRow();
            f.check(t("field.on_load"), () -> e.onLoad, v -> e.onLoad = v, t("help.on_load"));
            f.check(t("field.on_tick"), () -> e.onTick, v -> e.onTick = v, t("help.on_tick"));
            f.section(t("section.code"), t("help.mcfunction"));
            CodeEditor code = new CodeEditor("mcfunction");
            code.setText(e.code);
            code.setPrefHeight(420);
            code.setOnTextChanged(v -> {
                e.code = v;
                f.changed();
            });
            f.add(code);
            f.add(Ui.help(t("help.run_function", "/function " + fullId())));
        }
    }

    // ------------------------------------------------------------------ code file

    static final class CodeFileEditor extends EditorBase<CodeFile> {
        CodeFileEditor(Ctx c, CodeFile e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.code_file"));
            nameRow();
            f.choice(t("field.code_target"), List.of("mods", "forge", "neoforge", "fabric", "paper"), x -> t("target." + x),
                    () -> e.target, v -> e.target = v);
            f.text(t("field.path"), () -> e.path, v -> e.path = v, t("help.code_path"));
            f.section(t("section.code"), t("help.code_placeholders"));
            String lang = e.path.endsWith(".java") ? "java" : e.path.endsWith(".json") ? "json" : e.path.endsWith(".js") ? "js" : "plain";
            CodeEditor code = new CodeEditor(lang);
            code.setText(e.content);
            code.setPrefHeight(480);
            code.setOnTextChanged(v -> {
                e.content = v;
                f.changed();
            });
            f.add(code);
        }
    }

    // ------------------------------------------------------------------ procedure

    static final class ProcedureEditor extends EditorBase<Procedure> {
        private final Label support = Ui.help("");
        private final Label context = Ui.help("");

        ProcedureEditor(Ctx c, Procedure e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.trigger"));
            nameRow();
            IdField[] value = new IdField[1];
            f.choice(t("field.trigger"), List.copyOf(Logic.TRIGGERS.keySet()), x -> t("trigger." + x), () -> e.trigger, v -> {
                e.trigger = v;
                Logic.Def d = Logic.TRIGGERS.get(v);
                if (d != null && !d.params().isEmpty() && value[0] != null) {
                    value[0].setKind(Catalog.forParam(d.params().get(0).type()));
                    if (e.triggerValue.isBlank()) value[0].setValue(d.params().get(0).def());
                }
            });
            value[0] = f.id(t("field.trigger_value"), IdField.Kind.ITEM, () -> e.triggerValue, v -> e.triggerValue = v, env(), t("help.proc_value"));
            Logic.Def d = Logic.TRIGGERS.get(e.trigger);
            if (d != null && !d.params().isEmpty()) value[0].setKind(Catalog.forParam(d.params().get(0).type()));
            f.showIf(() -> {
                Logic.Def x = Logic.TRIGGERS.get(e.trigger);
                return x != null && !x.params().isEmpty();
            });
            f.integer(t("field.interval"), 1, 72000, () -> e.interval, v -> e.interval = v, t("help.interval"));
            f.showIf(() -> e.trigger.equals("interval"));
            f.add(new VBox(4, context, support));

            f.section(t("section.steps"), t("help.steps"));
            f.add(new LogicEditor(env(), e.steps, f::changed, () -> t("trigger." + e.trigger)
                    + (e.triggerValue.isBlank() ? "" : "  " + e.triggerValue)));
        }

        @Override
        protected void refresh() {
            List<Target> sup = Logic.triggerSupport(e.trigger);
            String names = c.p.targets().stream().filter(sup::contains).map(x -> t("target." + x.id)).collect(Collectors.joining(", "));
            String missing = c.p.targets().stream().filter(x -> !sup.contains(x) && x != Target.RESOURCEPACK)
                    .map(x -> t("target." + x.id)).collect(Collectors.joining(", "));
            support.setText(t("proc.support", names.isEmpty() ? "-" : names) + (missing.isEmpty() ? "" : "\n" + t("proc.unsupported", missing)));
            context.setText(Logic.serverContext(e.trigger) ? t("proc.server_context") : t("proc.player_context"));
        }
    }

    // ------------------------------------------------------------------ command

    static final class CommandEditor extends EditorBase<Elements.Command> {
        private final TreeView<String> tree = new TreeView<>();
        private final CodeEditor code = new CodeEditor("java");
        private final ToggleGroup view = new ToggleGroup();

        CommandEditor(Ctx c, Elements.Command e) {
            super(c, e);
        }

        @Override
        protected void fill() {
            f.section(t("section.general"), t("help.command"));
            nameRow();
            f.text(t("field.description"), () -> e.description, v -> e.description = v);
            f.integer(t("field.permission"), 0, 4, () -> e.permission, v -> e.permission = v, t("help.permission"));
            f.section(t("section.steps"), t("help.steps_command"));
            f.add(new LogicEditor(env(), e.steps, f::changed, () -> "/" + e.name));

            f.section(t("section.command.branches"), t("help.command.branches"));
            f.add(new ListEditor<>(e.branches, CommandBranch::new,
                    (b, i) -> b.path.isBlank() ? t("command.branch_n", i + 1) : "/" + e.name + " " + b.path, (b, bf) -> {
                        Label status = Ui.help("");
                        TextField path = bf.text(t("field.command.path"), () -> b.path, v -> b.path = v, t("help.command.path"));
                        path.getStyleClass().add("mono");
                        Runnable check = () -> {
                            try {
                                var toks = CommandSpec.parse(b.path);
                                status.setText(t("command.path_ok", CommandSpec.usage(e.name, toks)));
                                status.setStyle("-fx-text-fill: #57ab5a;");
                            } catch (CommandSpec.SpecException ex) {
                                status.setText(t("command.path_error", ex.getMessage()));
                                status.setStyle("-fx-text-fill: #e5534b;");
                            }
                        };
                        path.textProperty().addListener((o, a, v) -> check.run());
                        check.run();
                        bf.add(status);
                        bf.text(t("field.description"), () -> b.description, v -> b.description = v);
                        bf.integer(t("field.command.branch_permission"), -1, 4, () -> b.permission, v -> b.permission = v, t("help.command.branch_permission"));
                        bf.add(new LogicEditor(env(), b.steps, bf::changed, () -> "/" + e.name + " " + b.path));
                    }, f::changed, t("command.add_branch")));

            f.section(t("section.command.preview"), t("help.command.preview"));
            ToggleButton treeBtn = new ToggleButton(t("command.view.tree"));
            ToggleButton javaBtn = new ToggleButton(t("command.view.java"));
            ToggleButton jsonBtn = new ToggleButton(t("command.view.json"));
            ToggleButton packBtn = new ToggleButton(t("command.view.pack"));
            for (ToggleButton b : List.of(treeBtn, javaBtn, jsonBtn, packBtn)) b.setToggleGroup(view);
            treeBtn.setSelected(true);
            view.selectedToggleProperty().addListener((o, a, b) -> {
                if (b == null) a.setSelected(true);
                refresh();
            });
            HBox seg = new HBox(0, treeBtn, javaBtn, jsonBtn, packBtn);
            seg.getStyleClass().add("segmented");
            f.add(seg);
            tree.setPrefHeight(220);
            code.setEditable(false);
            code.setPrefHeight(260);
            f.add(tree);
            f.add(code);
        }

        @Override
        protected void refresh() {
            int mode = Math.max(0, view.getToggles().indexOf(view.getSelectedToggle()));
            tree.setVisible(mode == 0);
            tree.setManaged(mode == 0);
            code.setVisible(mode != 0);
            code.setManaged(mode != 0);
            List<String> fns = InterfaceGen.branchFunctions(e);
            switch (mode) {
                case 0 -> tree.setRoot(treeRoot(fns));
                case 1 -> {
                    GenContext ctx = new GenContext(c.p, Target.FABRIC, McVersion.latest());
                    code.setText(String.join("\n", new BridgeGen(ctx).brigadier(e)));
                }
                case 2 -> code.setText(new GsonBuilder().setPrettyPrinting().create().toJson(specJson(fns)));
                default -> code.setText(packUsage(fns));
            }
        }

        private TreeItem<String> treeRoot(List<String> fns) {
            boolean base = !e.steps.isEmpty() || e.branches.isEmpty();
            TreeItem<String> root = new TreeItem<>("/" + e.name + (base ? "   ->  commands/" + e.name : ""));
            root.setExpanded(true);
            for (int i = 0; i < e.branches.size(); i++) {
                var toks = CommandSpec.tryParse(e.branches.get(i).path);
                if (toks == null || fns.get(i) == null) continue;
                TreeItem<String> cur = root;
                for (var tk : toks) {
                    TreeItem<String> next = null;
                    for (TreeItem<String> ch : cur.getChildren()) if (ch.getValue().equals(tk.display())) next = ch;
                    if (next == null) {
                        next = new TreeItem<>(tk.display());
                        next.setExpanded(true);
                        cur.getChildren().add(next);
                    }
                    cur = next;
                }
                cur.getChildren().add(new TreeItem<>("->  " + fns.get(i)));
            }
            return root;
        }

        private JsonObject specJson(List<String> fns) {
            JsonArray arr = new JsonArray();
            for (int i = 0; i < e.branches.size(); i++) {
                CommandBranch b = e.branches.get(i);
                JsonObject o = new JsonObject();
                o.addProperty("path", b.path);
                o.addProperty("function", fns.get(i) == null ? "" : c.ns() + ":" + fns.get(i));
                o.addProperty("permission", b.permission < 0 ? e.permission : b.permission);
                JsonArray nodes = new JsonArray();
                var toks = CommandSpec.tryParse(b.path);
                if (toks != null) for (var tk : toks) {
                    JsonObject a = new JsonObject();
                    a.addProperty("name", tk.name());
                    a.addProperty("type", tk.type());
                    if (tk.optional()) a.addProperty("optional", true);
                    if (tk.min() != null) a.addProperty("min", tk.min());
                    if (tk.max() != null) a.addProperty("max", tk.max());
                    if (!tk.choices().isEmpty()) {
                        JsonArray ch = new JsonArray();
                        tk.choices().forEach(ch::add);
                        a.add("choices", ch);
                    }
                    nodes.add(a);
                }
                o.add("nodes", nodes);
                arr.add(o);
            }
            JsonObject root = new JsonObject();
            root.addProperty("command", e.name);
            root.addProperty("permission", e.permission);
            root.add("branches", arr);
            return root;
        }

        private String packUsage(List<String> fns) {
            StringBuilder sb = new StringBuilder();
            sb.append("# ").append(t("command.pack_trigger", e.name)).append('\n');
            sb.append("/trigger ").append(e.name).append('\n');
            for (int i = 0; i < e.branches.size(); i++) {
                var toks = CommandSpec.tryParse(e.branches.get(i).path);
                if (toks == null || fns.get(i) == null) continue;
                if (toks.stream().allMatch(CommandSpec.Token::literal)) {
                    sb.append("/trigger ").append(e.name).append(" set ").append(i + 2).append("    # ").append(e.branches.get(i).path).append('\n');
                    continue;
                }
                StringBuilder nbt = new StringBuilder();
                for (var tk : toks) {
                    if (tk.literal()) continue;
                    if (!nbt.isEmpty()) nbt.append(',');
                    nbt.append(tk.name()).append(':').append(tk.numeric() ? "1" : "\"...\"");
                }
                sb.append("/function ").append(c.ns()).append(':').append(fns.get(i)).append(" {").append(nbt).append("}\n");
            }
            return sb.toString();
        }
    }
}
