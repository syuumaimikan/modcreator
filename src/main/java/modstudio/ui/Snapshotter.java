package modstudio.ui;

import modstudio.core.TextureAsset;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;
import modstudio.core.Images;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Development helper: opens the sample project, visits every page and saves screenshots. */
public final class Snapshotter {
    private final MainWindow w;
    private final Path dir;
    private final List<Runnable> steps = new ArrayList<>();
    private final List<String> checks = new ArrayList<>();
    private int index;

    public Snapshotter(MainWindow w, Path dir) {
        this.w = w;
        this.dir = dir;
    }

    public void start() {
        add(() -> shot(w.scene(), "00-home"));
        dialogShot(() -> SettingsDialog.show(w), "80-settings");
        dialogShot(() -> NewProjectDialog.show(w), "81-new-project");
        add(w::openSample);
        String[] pages = {"overview", "textures", "translations", "item", "block", "creative_tab", "sound", "recipe",
                "loot_table", "advancement", "tag", "function", "procedure", "command", "code_file", "model", "ai", "build", "play", "problems"};
        int n = 1;
        for (String page : pages) {
            int num = n++;
            add(() -> {
                WorkspaceView v = w.workspaceView();
                v.show(page);
                var elems = w.workspace().project.elements.stream()
                        .filter(e -> e.type().id.equals(page)).toList();
                if (!elems.isEmpty()) v.openElement(elems.get(0).uid);
            });
            add(() -> {
                if ("build".equals(page)) {
                    for (javafx.scene.Node node : w.scene().getRoot().lookupAll(".tab-pane")) {
                        if (node instanceof javafx.scene.control.TabPane tp && tp.getTabs().size() > 1) {
                            tp.getSelectionModel().select(1);
                        }
                    }
                }
                shot(w.scene(), String.format("%02d-%s", num, page));
            });
        }
        // Create and verify ToolMaterial live + test AiAssistantPage quick prompt + MCP tool schema selection
        add(() -> {
            var v = w.workspaceView();
            v.show("ai");
            for (javafx.scene.Node node : w.scene().getRoot().lookupAll(".button")) {
                if (node instanceof javafx.scene.control.Button b && b.getStyleClass().contains("chip")) {
                    b.fire();
                    break;
                }
            }
            for (javafx.scene.Node node : w.scene().getRoot().lookupAll(".combo-box")) {
                if (node instanceof javafx.scene.control.ComboBox<?> cb && !cb.getItems().isEmpty()) {
                    cb.getSelectionModel().select(Math.min(2, cb.getItems().size() - 1));
                    break;
                }
            }
        });
        add(() -> {
            shot(w.scene(), "34-ai-active");
            modstudio.core.Elements.ToolMaterial tm = new modstudio.core.Elements.ToolMaterial();
            tm.name = "ruby_alloy";
            tm.displayName = "Ruby Alloy";
            tm.durability = 1400;
            tm.speed = 8.5;
            tm.attackDamageBonus = 3.5;
            tm.enchantability = 18;
            tm.miningLevel = 3;
            tm.repairItem = w.workspace().project.meta.modId + ":ruby";
            w.workspace().project.elements.add(tm);
            w.workspace().changed();
            w.workspaceView().rebuildAfterExternalMutation();
            w.workspaceView().openElement(tm.uid);
        });
        add(() -> shot(w.scene(), "35-tool_material-ruby_alloy"));
        add(() -> {
            var ws = w.workspace();
            var v = w.workspaceView();
            v.show("procedure");
            ws.project.elements.stream().filter(e -> e.name.equals("ruby_power")).findFirst().ifPresent(e -> v.openElement(e.uid));
        });
        add(() -> shot(w.scene(), "30-procedure-if"));
        for (String mode : new String[]{"graph", "script"}) {
            add(() -> {
                for (javafx.scene.Node node : w.scene().getRoot().lookupAll(".logic-editor")) {
                    if (!(node instanceof modstudio.ui.controls.LogicEditor le)) continue;
                    le.setMode(mode);
                    for (javafx.scene.Parent p = le.getParent(); p != null; p = p.getParent()) {
                        if (p instanceof javafx.scene.control.ScrollPane sp) {
                            sp.setVvalue(sp.getVmax());
                            break;
                        }
                    }
                }
            });
            add(() -> shot(w.scene(), "30-procedure-" + mode));
        }
        add(() -> modstudio.ui.controls.LogicEditor.setDefaultMode("blocks"));
        String[][] named = {{"command", "ruby"}, {"menu", "ruby_shop"}, {"hud", "arena_timer"}, {"hud", "ruby_board"},
                {"api_request", "hypixel_player"}, {"secrets", ""}, {"resource", "mana"}, {"machine", "moon_altar"},
                {"machine", "millstone"}, {"multiblock", "moon_altar"}, {"compat", ""}, {"shader", "ms_effects"}, {"textures", ""}, {"mob", "ruby_golem"},
                {"armor_material", "ruby"}, {"status_effect", "ruby_rush"}, {"enchantment", "ruby_edge"}, {"damage_type", "ruby_shard"},
                {"painting", "ruby_sunset"}, {"song", "ruby_waltz"}, {"block", "ruby_berry_bush"}, {"cutscene", "ruby_intro"},
                {"model", "ruby_golem"}, {"multiblock", "ruby_crusher"}, {"model", "ruby_crusher"},
                {"predicate", "ruby_night"}, {"item_modifier", "ruby_blessing"}, {"dialog", "ruby_welcome"}, {"trader", "ruby_merchant"},
                {"keybind", "ruby_dash"}, {"recipe", "ruby_rush_brewing"}, {"loot_table", "dungeon_rubies"},
                {"screen", "ruby_menu"}, {"screen", "ruby_hud"}, {"icon_font", "ruby_icons"}, {"theme", "ruby_theme"}, {"potion", "ruby_vigor"}, {"shader", "ruby_shimmer"}};
        int k = 40;
        for (String[] pn : named) {
            int num = k++;
            add(() -> {
                var v = w.workspaceView();
                v.show(pn[0]);
                w.workspace().project.elements.stream().filter(e -> e.type().id.equals(pn[0]) && e.name.equals(pn[1])).findFirst().ifPresent(e -> v.openElement(e.uid));
            });
            add(() -> {
                if ("armor_material".equals(pn[0])) verifyScrollIsolation("armor_material:" + pn[1]);
                shot(w.scene(), num + "-" + pn[0] + (pn[1].isEmpty() ? "" : "-" + pn[1]));
            });
        }
        // the bone and animation sections of the animated model, further down the editor (non-colliding file names)
        for (double pos : new double[]{0.55, 0.8}) {
            add(() -> {
                for (javafx.scene.Node node : w.scene().getRoot().lookupAll(".scroll-pane")) {
                    if (node instanceof javafx.scene.control.ScrollPane sp && sp.getContent() != null
                            && sp.getContent().lookup(".model-view") != null) sp.setVvalue(pos);
                }
            });
            add(() -> shot(w.scene(), (pos < 0.7 ? "85" : "86") + "-model-rig"));
        }
        // the background, opening animation and sounds of the shop menu
        add(() -> {
            var v = w.workspaceView();
            v.show("menu");
            w.workspace().project.elements.stream().filter(e -> e.name.equals("ruby_shop")).findFirst().ifPresent(e -> v.openElement(e.uid));
        });
        add(() -> {
            for (javafx.scene.Node node : w.scene().getRoot().lookupAll(".scroll-pane")) {
                if (node instanceof javafx.scene.control.ScrollPane sp && sp.getContent() != null
                        && sp.getContent().lookup(".mc-menu") != null) sp.setVvalue(0.75);
            }
        });
        add(() -> shot(w.scene(), "87-menu-look"));
        // the assembly, processing and recipe sections of the crusher
        add(() -> {
            var v = w.workspaceView();
            v.show("multiblock");
            w.workspace().project.elements.stream().filter(e -> e.name.equals("ruby_crusher") && e.type().id.equals("multiblock")).findFirst()
                    .ifPresent(e -> v.openElement(e.uid));
        });
        for (double pos : new double[]{0.45, 0.7}) {
            add(() -> {
                for (javafx.scene.Node node : w.scene().getRoot().lookupAll(".scroll-pane")) {
                    if (node instanceof javafx.scene.control.ScrollPane sp && sp.getContent() != null
                            && sp.getContent().lookup(".voxel-editor") != null) sp.setVvalue(pos);
                }
            });
            add(() -> shot(w.scene(), (pos < 0.6 ? "88" : "89") + "-multiblock-assembly"));
        }
        // the node graph and the live preview of the shimmer shader, further down its editor
        add(() -> {
            var v = w.workspaceView();
            v.show("shader");
            w.workspace().project.elements.stream().filter(e -> e.name.equals("ruby_shimmer")).findFirst().ifPresent(e -> v.openElement(e.uid));
        });
        for (String part : new String[]{".shader-graph", ".shader-preview"}) {
            add(() -> scrollTo(w.scene(), part));
            add(() -> shot(w.scene(), (part.contains("graph") ? "78" : "79") + "-shader-" + part.substring(8)));
        }
        add(() -> {
            var ws = w.workspace();
            var v = w.workspaceView();
            v.show("recipe");
            ws.project.elements.stream().filter(e -> e.name.equals("ruby_from_smelting")).findFirst().ifPresent(e -> v.openElement(e.uid));
        });
        add(() -> shot(w.scene(), "31-recipe-smelting"));
        add(() -> {
            var ws = w.workspace();
            Scene s = modstudio.ui.controls.TextureEditor.sceneFor(w.stage, ws.project.textures.get(0));
            javafx.stage.Stage st = new javafx.stage.Stage();
            st.setScene(s);
            st.show();
            PauseTransition pt = new PauseTransition(Duration.millis(600));
            pt.setOnFinished(e -> {
                shot(s, "32-texture-editor");
                st.close();
            });
            pt.play();
        });
        add(() -> {
            var ws = w.workspace();
            TextureAsset glow = ws.project.textureByName("ruby_glow");
            if (glow == null) return;
            Scene s = modstudio.ui.controls.TextureEditor.sceneFor(w.stage, glow);
            javafx.stage.Stage st = new javafx.stage.Stage();
            st.setScene(s);
            st.show();
            PauseTransition pt = new PauseTransition(Duration.millis(600));
            pt.setOnFinished(e -> {
                // show the animation settings at the bottom of the side panel
                for (javafx.scene.Node node : s.getRoot().lookupAll(".scroll-pane")) {
                    if (node instanceof javafx.scene.control.ScrollPane sp) sp.setVvalue(sp.getVmax());
                }
                s.getRoot().layout();
                shot(s, "33-texture-animation");
                st.close();
            });
            pt.play();
        });
        // Git dialog screenshot
        dialogShot(() -> GitDialog.show(w.stage, w.workspace().dir()), "84-git-dialog");
        // RpgSampleProject walkthrough covering advanced mod elements (entity, container, portal, world_feature, profession, etc.)
        add(w::openRpgSample);
        String[] rpgPages = {"entity", "container", "portal", "world_feature", "profession", "mod_config", "mixin", "armor_trim", "banner_pattern", "recipe_type"};
        int rn = 90;
        for (String rp : rpgPages) {
            int rnum = rn++;
            add(() -> {
                var v = w.workspaceView();
                v.show(rp);
                w.workspace().project.elements.stream().filter(e -> e.type().id.equals(rp)).findFirst().ifPresent(e -> v.openElement(e.uid));
            });
            add(() -> {
                if ("entity".equals(rp)) verifyScrollIsolation("rpg-entity");
                shot(w.scene(), String.format("%02d-rpg-%s", rnum, rp));
            });
        }
        // the new project dialog with a data pack chosen: no Java package, a namespace instead of a mod id
        add(() -> {
            PauseTransition pt = new PauseTransition(Duration.millis(500));
            pt.setOnFinished(e -> {
                for (javafx.stage.Window win : new ArrayList<>(javafx.stage.Window.getWindows())) {
                    if (win == w.stage || win.getScene() == null || win.getScene().getRoot().lookup(".dialog-pane") == null) continue;
                    for (javafx.scene.Node node : win.getScene().getRoot().lookupAll(".toggle-button")) {
                        if (node instanceof javafx.scene.control.ToggleButton tb && "datapack".equals(tb.getUserData())) tb.setSelected(true);
                    }
                    PauseTransition later = new PauseTransition(Duration.millis(400));
                    later.setOnFinished(x -> {
                        shot(win.getScene(), "82-new-datapack");
                        win.hide();
                    });
                    later.play();
                }
            });
            pt.play();
            Platform.runLater(() -> NewProjectDialog.show(w));
        });
        add(() -> {
        });
        // a data pack project: the sidebar leaves out what data packs cannot make
        add(() -> {
            modstudio.core.Project dp = new modstudio.core.Project();
            dp.meta.name = "My Data Pack";
            dp.meta.modId = "mydatapack";
            dp.meta.targets = new ArrayList<>(List.of("datapack", "resourcepack"));
            dp.meta.mcVersions = new ArrayList<>(List.of("1.21.1"));
            dp.meta.mcVersion = "1.21.1";
            try {
                Path file = dir.resolve("datapack-test").resolve("mydatapack.msproj");
                Files.createDirectories(file.getParent());
                modstudio.core.ProjectIO.save(dp, file);
                w.openWorkspace(dp, file);
            } catch (java.io.IOException ex) {
                ex.printStackTrace();
            }
        });
        add(() -> shot(w.scene(), "83-datapack-workspace"));
        add(() -> {
        });
        add(() -> {
            try {
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("verify-report.txt"), String.join("\n", checks) + "\n");
            } catch (java.io.IOException ex) {
                ex.printStackTrace();
            }
            System.out.println("Snapshots written to " + dir);
            Platform.exit();
        });
        next();
    }

    private void verifyScrollIsolation(String label) {
        for (javafx.scene.Node mv : w.scene().getRoot().lookupAll(".model-view")) {
            for (javafx.scene.Parent cur = mv.getParent(); cur != null; cur = cur.getParent()) {
                if (cur instanceof javafx.scene.control.ScrollPane sp) {
                    sp.setVvalue(0.25);
                    double before = sp.getVvalue();
                    javafx.scene.input.ScrollEvent se = new javafx.scene.input.ScrollEvent(
                            javafx.scene.input.ScrollEvent.SCROLL, 50, 50, 50, 50,
                            false, false, false, false, false, false,
                            0, -80, 0, -80,
                            javafx.scene.input.ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                            javafx.scene.input.ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, null);
                    javafx.event.Event.fireEvent(mv, se);
                    double after = sp.getVvalue();
                    boolean isolated = Math.abs(after - before) < 0.0001;
                    sp.setVvalue(0.0);
                    String line = (isolated ? "PASS " : "FAIL ") + "scroll_isolation[" + label + "]: before=" + before + " after=" + after;
                    checks.add(line);
                    System.out.println(line);
                    return;
                }
            }
        }
    }

    /** Opens a modal dialog, captures it once laid out and closes it again. */
    private void dialogShot(Runnable open, String name) {
        add(() -> {
            PauseTransition pt = new PauseTransition(Duration.millis(500));
            pt.setOnFinished(e -> {
                for (javafx.stage.Window win : new ArrayList<>(javafx.stage.Window.getWindows())) {
                    if (win == w.stage || win.getScene() == null || win.getScene().getRoot().lookup(".dialog-pane") == null) continue;
                    shot(win.getScene(), name);
                    win.hide();
                }
            });
            pt.play();
            // showAndWait is not allowed inside the animation callback that runs the steps
            Platform.runLater(open);
        });
        add(() -> {
        });
    }

    private void add(Runnable r) {
        steps.add(r);
    }

    private void next() {
        if (index >= steps.size()) return;
        Runnable r = steps.get(index++);
        try {
            r.run();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        PauseTransition pt = new PauseTransition(Duration.millis(700));
        pt.setOnFinished(e -> next());
        pt.play();
    }

    /** Scrolls the editor so the first node matching the selector is at the top of the view. */
    private static void scrollTo(Scene scene, String selector) {
        for (javafx.scene.Node node : scene.getRoot().lookupAll(".scroll-pane")) {
            if (!(node instanceof javafx.scene.control.ScrollPane sp) || sp.getContent() == null) continue;
            javafx.scene.Node target = sp.getContent().lookup(selector);
            if (target == null || target == sp.getContent()) continue;
            double y = sp.getContent().sceneToLocal(target.localToScene(0, 0)).getY();
            double range = sp.getContent().getLayoutBounds().getHeight() - sp.getViewportBounds().getHeight();
            if (range > 0) sp.setVvalue(Math.max(0, Math.min(1, (y - 60) / range)));
        }
    }

    private void shot(Scene scene, String name) {
        try {
            WritableImage img = scene.snapshot(null);
            int wd = (int) img.getWidth(), ht = (int) img.getHeight();
            int[] px = new int[wd * ht];
            img.getPixelReader().getPixels(0, 0, wd, ht, PixelFormat.getIntArgbInstance(), px, 0, wd);
            Files.createDirectories(dir);
            Files.write(dir.resolve(name + ".png"), Images.png(px, wd, ht));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
