package modstudio.ui.editors;

import javafx.scene.Node;
import modstudio.core.*;
import modstudio.core.Elements.*;
import modstudio.ui.Workspace;

/** Creates the editor for an element and sets sensible defaults on new elements. */
public final class Editors {
    private Editors() {}

    public static Node create(Ctx c, Element e) {
        EditorBase<?> ed = switch (e) {
            case Item it -> new ItemEditor(c, it);
            case Block b -> new BlockEditor(c, b);
            case ArmorMaterial am -> new ArmorEditor(c, am);
            case StatusEffect se -> new GameplayEditors.StatusEffectEditor(c, se);
            case Potion po -> new GameplayEditors.PotionEditor(c, po);
            case Enchantment en -> new GameplayEditors.EnchantmentEditor(c, en);
            case DamageType dt -> new GameplayEditors.DamageTypeEditor(c, dt);
            case Painting pa -> new GameplayEditors.PaintingEditor(c, pa);
            case Song so -> new GameplayEditors.SongEditor(c, so);
            case Elements.Model m -> new ModelEditor(c, m);
            case Systems.Shader sh -> new ShaderEditor(c, sh);
            case CreativeTab t -> new SimpleEditors.TabEditor(c, t);
            case Sound s -> new SimpleEditors.SoundEditor(c, s);
            case Recipe r -> new RecipeEditor(c, r);
            case LootTable l -> new LootTableEditor(c, l);
            case Advancement a -> new AdvancementEditor(c, a);
            case Tag t -> new SimpleEditors.TagEditor(c, t);
            case Function fn -> new SimpleEditors.FunctionEditor(c, fn);
            case Procedure p -> new SimpleEditors.ProcedureEditor(c, p);
            case Elements.Command cmd -> new SimpleEditors.CommandEditor(c, cmd);
            case CodeFile cf -> new SimpleEditors.CodeFileEditor(c, cf);
            case Systems.Mob m -> new SystemEditors.MobEditor(c, m);
            case Systems.Effect fx -> new SystemEditors.EffectEditor(c, fx);
            case Systems.Structure st -> new SystemEditors.StructureEditor(c, st);
            case Systems.Biome bi -> new SystemEditors.BiomeEditor(c, bi);
            case Systems.Dimension d -> new SystemEditors.DimensionEditor(c, d);
            case Systems.Menu m -> new InterfaceEditors.MenuEditor(c, m);
            case Systems.Hud h -> new InterfaceEditors.HudEditor(c, h);
            case Systems.Theme th -> new ThemeEditor(c, th);
            case Systems.IconFont fo -> new ScreenEditors.IconFontEditor(c, fo);
            case Systems.CustomScreen sc -> new ScreenEditors.ScreenEditor(c, sc);
            case Systems.ApiRequest r -> new InterfaceEditors.ApiRequestEditor(c, r);
            case Systems.Resource r -> new MachineEditors.ResourceEditor(c, r);
            case Systems.Machine m -> new MachineEditors.MachineEditor(c, m);
            case Systems.Multiblock mb -> new MachineEditors.MultiblockEditor(c, mb);
            case Systems.Cutscene cs -> new CutsceneEditor(c, cs);
            case Elements.Predicate pr -> new DataEditors.PredicateEditor(c, pr);
            case Elements.ItemModifier im -> new DataEditors.ItemModifierEditor(c, im);
            case Systems.Dialog d -> new DataEditors.DialogEditor(c, d);
            case Systems.Trader tr -> new DataEditors.TraderEditor(c, tr);
            case Systems.KeyBind k -> new DataEditors.KeyBindEditor(c, k);
            default -> throw new IllegalArgumentException(e.type().id);
        };
        return ed.build();
    }

    public static void initDefaults(Workspace ws, Element e) {
        String ns = ws.project.meta.modId;
        String title = Ids.titleCase(e.name.contains("/") ? e.name.substring(e.name.lastIndexOf('/') + 1) : e.name);
        switch (e) {
            case Item it -> {
                it.displayName = title;
                firstTab(ws).ifPresent(t -> it.creativeTab = t);
            }
            case Block b -> {
                b.displayName = title;
                firstTab(ws).ifPresent(t -> b.creativeTab = t);
            }
            case CreativeTab t -> {
                t.displayName = title;
                ws.project.all(Item.class).stream().findFirst().ifPresent(i -> t.icon = ns + ":" + i.name);
            }
            case Elements.Model m -> {
                modstudio.core.Models.applyTemplate(m, "block");
                m.textures.put("0", "minecraft:block/stone");
            }
            case Sound s -> s.subtitle = title;
            case StatusEffect se -> se.displayName = title;
            case Potion po -> {
                po.displayName = title;
                PotionEffect first = new PotionEffect();
                po.effects.add(first);
            }
            case Enchantment en -> en.displayName = title;
            case DamageType dt -> dt.deathMessage = "%1$s was struck by " + title.toLowerCase(java.util.Locale.ROOT);
            case Painting pa -> pa.title = title;
            case Song so -> so.description = title;
            case Systems.Structure st -> {
                // a small stone hut: floor, walls with a doorway, roof
                st.palette = new java.util.ArrayList<>(java.util.List.of("minecraft:cobblestone", "minecraft:oak_planks", "minecraft:glass", "minecraft:air"));
                st.resize(5, 4, 5);
                for (int x = 0; x < 5; x++)
                    for (int z = 0; z < 5; z++) {
                        st.set(x, 0, z, 0);
                        st.set(x, 3, z, 1);
                        for (int y = 1; y < 3; y++) {
                            boolean wall = x == 0 || z == 0 || x == 4 || z == 4;
                            st.set(x, y, z, wall ? (y == 2 && (x == 2 || z == 2) ? 2 : 1) : 3);
                        }
                    }
                st.set(2, 1, 0, 3);
                st.set(2, 2, 0, 3);
            }
            case Systems.Biome bi -> {
                Systems.MobSpawn sp = new Systems.MobSpawn();
                bi.spawns.add(sp);
            }
            case Systems.Mob m -> {
                m.displayName = title;
                m.onSpawn.add(Logic.newStep("particle"));
            }
            case Systems.Menu m -> {
                m.title = "&8" + title;
                Systems.MenuSlot close = new Systems.MenuSlot();
                close.slot = m.size() - 5;
                close.item = "minecraft:barrier";
                close.name = "&cClose";
                close.action = "close";
                m.slots.add(close);
            }
            case Systems.Hud h -> {
                h.title = "&e&l" + title;
                h.lines.addAll(java.util.List.of("&7Time left: &f{min}:{sec}", "&7Score: &a{score:points}"));
                h.timer = true;
            }
            case Systems.ApiRequest r -> {
                r.url = "https://api.example.com/players/{uuid}";
                Systems.Extract x = new Systems.Extract();
                x.path = "data.name";
                x.key = "name";
                r.extract.add(x);
                r.onFailure.add(Logic.newStep("message"));
            }
            case Systems.Resource r -> r.displayName = title;
            case Systems.Shader sh -> {
                // A helper include works on every version; core programs start from the game's own source (Import).
                sh.kind = "include";
                sh.program = e.name;
                sh.fragment = "// " + title + ": helper functions for core shaders.\n"
                        + "// Use it in a core shader with  #moj_import <" + e.name + ".glsl>  (1.21.4+: <minecraft:" + e.name + ".glsl>)\n\n"
                        + Shaders.SNIPPETS.get("grayscale") + "\n" + Shaders.SNIPPETS.get("pulse");
            }
            case Systems.Machine m -> {
                m.block = "minecraft:lodestone";
                m.particle = "minecraft:end_rod";
                ws.project.all(Systems.Resource.class).stream().findFirst().ifPresent(r -> {
                    Systems.MachineIO io = new Systems.MachineIO();
                    io.resource = r.name;
                    m.io.add(io);
                });
            }
            case Systems.Multiblock mb -> {
                // gold ring with the core block in the middle and amethyst pillars on the corners
                mb.resize(3, 2, 3);
                for (int x = 0; x < 3; x++)
                    for (int z = 0; z < 3; z++) mb.set(x, 0, z, x == 1 && z == 1 ? 2 : 0);
                for (int x = 0; x < 3; x += 2)
                    for (int z = 0; z < 3; z += 2) mb.set(x, 1, z, 1);
            }
            case Systems.Cutscene cs -> {
                // a slow orbit around the start position, looking at it
                double[][] pts = {{0, 6, -8}, {8, 5, 0}, {0, 4, 8}, {-8, 5, 0}};
                for (int i = 0; i < pts.length; i++) {
                    Systems.CameraKey k = new Systems.CameraKey();
                    k.time = i * 2.5;
                    k.x = pts[i][0];
                    k.y = pts[i][1];
                    k.z = pts[i][2];
                    k.lookAt = true;
                    k.ty = 1;
                    cs.keys.add(k);
                }
                Systems.CameraEvent ev = new Systems.CameraEvent();
                ev.time = 0.5;
                ev.text = title;
                ev.color = "gold";
                ev.duration = 3;
                cs.events.add(ev);
            }
            case Elements.Predicate pr -> {
                PredicateTerm term = new PredicateTerm();
                term.kind = "time";
                Logic.defaults(Logic.PREDICATE_TERMS.get(term.kind), term.p);
                pr.terms.add(term);
            }
            case Elements.ItemModifier im -> {
                ModifierFunction fn = new ModifierFunction();
                fn.kind = "set_name";
                Logic.defaults(Logic.MODIFIER_FUNCTIONS.get(fn.kind), fn.p);
                fn.p.put("text", title);
                im.functions.add(fn);
            }
            case Systems.IconFont fo -> fo.icons.add(new Systems.Icon());
            case Systems.CustomScreen sc -> {
                sc.title = title;
                Systems.ScreenWidget panel = new Systems.ScreenWidget();
                panel.kind = "panel";
                panel.width = 176;
                panel.height = 100;
                panel.x = -88;
                panel.y = -50;
                panel.color = "#C6C6C6";
                panel.color2 = "#373737";
                panel.animation = "pop";
                Systems.ScreenWidget head = new Systems.ScreenWidget();
                head.text = title;
                head.color = "#404040";
                head.shadow = false;
                head.align = "center";
                head.x = -88;
                head.y = -42;
                head.width = 176;
                head.height = 10;
                head.animation = "fade_in";
                Systems.ScreenWidget close = new Systems.ScreenWidget();
                close.kind = "button";
                close.text = "OK";
                close.color2 = "#6B6B6B";
                close.x = -40;
                close.y = 22;
                close.animation = "fade_in";
                sc.widgets.addAll(java.util.List.of(panel, head, close));
            }
            case Systems.Dialog d -> {
                d.title = title;
                d.body = "";
                Systems.DialogButton ok = new Systems.DialogButton();
                ok.label = "OK";
                d.kind = "notice";
                d.buttons.add(ok);
            }
            case Systems.KeyBind k -> {
                k.displayName = title;
                Elements.Step msg = Logic.newStep("message");
                msg.p.put("text", title);
                k.steps.add(msg);
            }
            case Systems.Trader tr -> {
                tr.displayName = title;
                tr.trades.add(new Systems.Trade());
            }
            case Advancement a -> {
                a.title = title;
                a.description = "";
            }
            case LootTable l -> {
                LootPool p = new LootPool();
                p.entries.add(new LootEntry());
                l.pools.add(p);
            }
            case Function f -> f.code = "# " + title + "\nsay Hello from " + ns + ":" + e.name + "\n";
            case Procedure p -> {
                p.trigger = "player_join";
                p.steps.add(Logic.newStep("message"));
            }
            case Elements.Command c -> {
                c.description = title;
                c.steps.add(Logic.newStep("message"));
            }
            case CodeFile cf -> {
                String cls = Ids.toPascal(e.name);
                cf.target = "mods";
                cf.path = "src/main/java/{package}/custom/" + cls + ".java";
                cf.content = "package {package_name}.custom;\n\npublic final class " + cls + " {\n    private " + cls
                        + "() {}\n\n    public static void hello() {\n        System.out.println(\"Hello from {modid}\");\n    }\n}\n";
            }
            default -> {
            }
        }
    }

    private static java.util.Optional<String> firstTab(Workspace ws) {
        return ws.project.all(CreativeTab.class).stream().findFirst().map(t -> "tab:" + t.name);
    }
}
