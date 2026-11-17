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
    private int index;

    public Snapshotter(MainWindow w, Path dir) {
        this.w = w;
        this.dir = dir;
    }

    public void start() {
        add(() -> shot(w.scene(), "00-home"));
        add(w::openSample);
        String[] pages = {"overview", "textures", "translations", "item", "block", "creative_tab", "sound", "recipe",
                "loot_table", "advancement", "tag", "function", "procedure", "command", "code_file", "model", "build", "play", "problems"};
        int n = 1;
        for (String page : pages) {
            int num = n++;
            add(() -> {
                WorkspaceView v = w.workspaceView();
                v.show(page);
                var elems = w.workspace().project.elements.stream()
                        .filter(e -> e.type().id.equals(page)).toList();
                if (!elems.isEmpty()) v.openElement(elems.get(elems.size() > 1 && page.equals("item") ? 0 : 0).uid);
            });
            add(() -> shot(w.scene(), String.format("%02d-%s", num, page)));
        }
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
                {"painting", "ruby_sunset"}, {"song", "ruby_waltz"}, {"block", "ruby_berry_bush"}};
        int k = 40;
        for (String[] pn : named) {
            int num = k++;
            add(() -> {
                var v = w.workspaceView();
                v.show(pn[0]);
                w.workspace().project.elements.stream().filter(e -> e.type().id.equals(pn[0]) && e.name.equals(pn[1])).findFirst().ifPresent(e -> v.openElement(e.uid));
            });
            add(() -> shot(w.scene(), num + "-" + pn[0] + (pn[1].isEmpty() ? "" : "-" + pn[1])));
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
        add(() -> {
        });
        add(() -> {
            System.out.println("Snapshots written to " + dir);
            Platform.exit();
        });
        next();
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
