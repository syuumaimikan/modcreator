package modstudio.core;

import modstudio.core.Elements.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Pure (toolkit-independent) sensible defaults for newly created {@link Element} instances.
 * Shared by the UI editor factory and MCP creation tools so elements created programmatically
 * match elements created in the GUI.
 */
public final class ElementDefaults {
    private ElementDefaults() {}

    public static void init(Project p, Element e) {
        if (p == null || e == null) return;
        String ns = p.meta.modId;
        String rawName = e.name == null ? "" : e.name;
        String title = Ids.titleCase(rawName.contains("/") ? rawName.substring(rawName.lastIndexOf('/') + 1) : rawName);
        switch (e) {
            case Item it -> {
                it.displayName = title;
                firstTab(p).ifPresent(t -> it.creativeTab = t);
            }
            case Block b -> {
                b.displayName = title;
                firstTab(p).ifPresent(t -> b.creativeTab = t);
            }
            case Fluid fl -> fl.displayName = title;
            case ToolMaterial tm -> tm.displayName = title;
            case ModElements.Container ct -> ct.displayName = title;
            case ModElements.CustomEntity ce -> {
                ce.displayName = title;
                firstTab(p).ifPresent(t -> ce.creativeTab = t);
            }
            case ModElements.ModConfig cfg -> {
                cfg.displayName = title;
                if (cfg.entries.isEmpty()) cfg.entries.add(new ModElements.ConfigEntry());
            }
            case ModElements.MixinSpec mx -> mx.displayName = title;
            case ModElements.Portal pt -> {
                pt.displayName = title;
                firstTab(p).ifPresent(t -> pt.creativeTab = t);
            }
            case ModElements.WorldFeature wf -> wf.displayName = title;
            case ModElements.Profession pf -> pf.displayName = title;
            case ModElements.ArmorTrim at -> {
                at.displayName = title;
                firstTab(p).ifPresent(t -> at.creativeTab = t);
            }
            case ModElements.BannerPattern bp -> bp.displayName = title;
            case ModElements.CustomRecipeType rt -> rt.displayName = title;
            case CreativeTab t -> {
                t.displayName = title;
                p.all(Item.class).stream().findFirst().ifPresent(i -> t.icon = ns + ":" + i.name);
            }
            case Elements.Model m -> {
                if (m.cubes.isEmpty() && m.bones.isEmpty()) Models.applyTemplate(m, "block");
                if (m.textures.isEmpty()) m.textures.put("0", "minecraft:block/stone");
            }
            case Sound s -> s.subtitle = title;
            case StatusEffect se -> se.displayName = title;
            case Potion po -> {
                po.displayName = title;
                if (po.effects.isEmpty()) po.effects.add(new PotionEffect());
            }
            case Enchantment en -> en.displayName = title;
            case DamageType dt -> dt.deathMessage = "%1$s was struck by " + title.toLowerCase(Locale.ROOT);
            case Painting pa -> pa.title = title;
            case Song so -> so.description = title;
            case Systems.Structure st -> {
                st.palette = new ArrayList<>(List.of("minecraft:cobblestone", "minecraft:oak_planks", "minecraft:glass", "minecraft:air"));
                st.resize(5, 4, 5);
                for (int x = 0; x < 5; x++) {
                    for (int z = 0; z < 5; z++) {
                        st.set(x, 0, z, 0);
                        st.set(x, 3, z, 1);
                        for (int y = 1; y < 3; y++) {
                            boolean wall = x == 0 || z == 0 || x == 4 || z == 4;
                            st.set(x, y, z, wall ? (y == 2 && (x == 2 || z == 2) ? 2 : 1) : 3);
                        }
                    }
                }
                st.set(2, 1, 0, 3);
                st.set(2, 2, 0, 3);
            }
            case Systems.Biome bi -> {
                if (bi.spawns.isEmpty()) bi.spawns.add(new Systems.MobSpawn());
            }
            case Systems.Mob m -> {
                m.displayName = title;
                if (m.onSpawn.isEmpty()) m.onSpawn.add(Logic.newStep("particle"));
            }
            case Systems.Menu m -> {
                m.title = "&8" + title;
                if (m.slots.isEmpty()) {
                    Systems.MenuSlot close = new Systems.MenuSlot();
                    close.slot = m.size() - 5;
                    close.item = "minecraft:barrier";
                    close.name = "&cClose";
                    close.action = "close";
                    m.slots.add(close);
                }
            }
            case Systems.Hud h -> {
                h.title = "&e&l" + title;
                if (h.lines.isEmpty()) h.lines.addAll(List.of("&7Time left: &f{min}:{sec}", "&7Score: &a{score:points}"));
                h.timer = true;
            }
            case Systems.ApiRequest r -> {
                r.url = "https://api.example.com/players/{uuid}";
                if (r.extract.isEmpty()) {
                    Systems.Extract x = new Systems.Extract();
                    x.path = "data.name";
                    x.key = "name";
                    r.extract.add(x);
                }
                if (r.onFailure.isEmpty()) r.onFailure.add(Logic.newStep("message"));
            }
            case Systems.Resource r -> r.displayName = title;
            case Systems.Shader sh -> {
                sh.kind = "include";
                sh.program = e.name;
                sh.fragment = "// " + title + ": helper functions for core shaders.\n"
                        + "// Use it in a core shader with  #moj_import <" + e.name + ".glsl>  (1.21.4+: <minecraft:" + e.name + ".glsl>)\n\n"
                        + Shaders.SNIPPETS.get("grayscale") + "\n" + Shaders.SNIPPETS.get("pulse");
            }
            case Systems.Machine m -> {
                m.block = "minecraft:lodestone";
                m.particle = "minecraft:end_rod";
                if (m.io.isEmpty()) {
                    p.all(Systems.Resource.class).stream().findFirst().ifPresent(r -> {
                        Systems.MachineIO io = new Systems.MachineIO();
                        io.resource = r.name;
                        m.io.add(io);
                    });
                }
            }
            case Systems.Multiblock mb -> {
                mb.resize(3, 2, 3);
                for (int x = 0; x < 3; x++)
                    for (int z = 0; z < 3; z++) mb.set(x, 0, z, x == 1 && z == 1 ? 2 : 0);
                for (int x = 0; x < 3; x += 2)
                    for (int z = 0; z < 3; z += 2) mb.set(x, 1, z, 1);
            }
            case Systems.Cutscene cs -> {
                if (cs.keys.isEmpty()) {
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
            }
            case Elements.Predicate pr -> {
                if (pr.terms.isEmpty()) {
                    PredicateTerm term = new PredicateTerm();
                    term.kind = "time";
                    Logic.defaults(Logic.PREDICATE_TERMS.get(term.kind), term.p);
                    pr.terms.add(term);
                }
            }
            case Elements.ItemModifier im -> {
                if (im.functions.isEmpty()) {
                    ModifierFunction fn = new ModifierFunction();
                    fn.kind = "set_name";
                    Logic.defaults(Logic.MODIFIER_FUNCTIONS.get(fn.kind), fn.p);
                    fn.p.put("text", title);
                    im.functions.add(fn);
                }
            }
            case Systems.IconFont fo -> {
                if (fo.icons.isEmpty()) fo.icons.add(new Systems.Icon());
            }
            case Systems.CustomScreen sc -> {
                sc.title = title;
                if (sc.widgets.isEmpty()) {
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
                    sc.widgets.addAll(List.of(panel, head, close));
                }
            }
            case Systems.Dialog d -> {
                d.title = title;
                d.body = "";
                d.kind = "notice";
                if (d.buttons.isEmpty()) {
                    Systems.DialogButton ok = new Systems.DialogButton();
                    ok.label = "OK";
                    d.buttons.add(ok);
                }
            }
            case Systems.KeyBind k -> {
                k.displayName = title;
                if (k.steps.isEmpty()) {
                    Step msg = Logic.newStep("message");
                    msg.p.put("text", title);
                    k.steps.add(msg);
                }
            }
            case Systems.Trader tr -> {
                tr.displayName = title;
                if (tr.trades.isEmpty()) tr.trades.add(new Systems.Trade());
            }
            case Advancement a -> {
                a.title = title;
                a.description = "";
            }
            case LootTable l -> {
                if (l.pools.isEmpty()) {
                    LootPool pool = new LootPool();
                    pool.entries.add(new LootEntry());
                    l.pools.add(pool);
                }
            }
            case Function f -> f.code = "# " + title + "\nsay Hello from " + ns + ":" + e.name + "\n";
            case Procedure pr -> {
                pr.trigger = "player_join";
                if (pr.steps.isEmpty()) pr.steps.add(Logic.newStep("message"));
            }
            case Elements.Command c -> {
                c.description = title;
                if (c.steps.isEmpty()) c.steps.add(Logic.newStep("message"));
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

    private static Optional<String> firstTab(Project p) {
        return p.all(CreativeTab.class).stream().findFirst().map(t -> "tab:" + t.name);
    }
}

