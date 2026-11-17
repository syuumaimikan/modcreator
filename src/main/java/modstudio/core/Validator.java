package modstudio.core;

import modstudio.core.Elements.*;
import modstudio.i18n.I18n;

import java.util.*;

/** Checks a project for errors and platform limitations. */
public final class Validator {
    private Validator() {}

    public enum Severity { ERROR, WARNING, INFO }

    public record Problem(Severity severity, String elementUid, String message) {}

    public static List<Problem> validate(Project p) {
        List<Problem> r = new ArrayList<>();
        String ns = p.meta.modId;
        if (!Ids.MODID.matcher(ns).matches()) r.add(new Problem(Severity.ERROR, null, I18n.t("problem.modid_invalid", ns)));
        if (!Ids.PACKAGE.matcher(p.meta.packageName).matches()) r.add(new Problem(Severity.ERROR, null, I18n.t("problem.package_invalid", p.meta.packageName)));
        List<Target> targets = p.targets();
        if (targets.isEmpty()) r.add(new Problem(Severity.ERROR, null, I18n.t("problem.no_targets")));
        for (String t : p.meta.targets) {
            Target tt = Target.of(t);
            if (tt == null) continue;
            for (McVersion v : p.mcVersions()) {
                if (!tt.supports(v)) r.add(new Problem(Severity.WARNING, null, I18n.t("problem.version_unsupported", I18n.t("target." + t), v.id)));
            }
        }

        Map<String, Element> seen = new HashMap<>();
        for (Element e : p.elements) {
            String typeName = I18n.t("type." + e.type().id);
            if (e.name.isBlank()) {
                r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.name_empty", typeName)));
                continue;
            }
            boolean valid = e.type() == ElementType.CODE_FILE || (e.type().allowsPath() ? Ids.PATH : Ids.NAME).matcher(e.name).matches();
            if (!valid) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.name_invalid", e.name)));
            String key = e.type().registryGroup() + "|" + e.name;
            if (seen.containsKey(key)) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.name_duplicate", e.name)));
            else seen.put(key, e);
        }

        Set<String> texNames = new HashSet<>();
        for (TextureAsset t : p.textures) {
            if (!Ids.NAME.matcher(t.name).matches()) r.add(new Problem(Severity.ERROR, null, I18n.t("problem.texture_name_invalid", t.name)));
            if (!texNames.add(t.name)) r.add(new Problem(Severity.ERROR, null, I18n.t("problem.texture_name_duplicate", t.name)));
        }

        boolean modTarget = targets.stream().anyMatch(Target::isMod);
        for (Element e : p.elements) {
            switch (e) {
                case Item it -> {
                    if (!it.model.isBlank() && p.model(it.model) == null)
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.model_missing", it.name, it.model)));
                    else if (it.model.isBlank() && p.texture(it.texture) == null)
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.texture_missing", it.name)));
                    if (it.creativeTab.startsWith("tab:") && p.byName(ElementType.CREATIVE_TAB, it.creativeTab.substring(4)) == null)
                        r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.unknown_tab", it.creativeTab.substring(4))));
                    if (!it.song.isBlank() && p.byName(ElementType.SONG, it.song) == null)
                        r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.song_missing", it.name, it.song)));
                    if (it.isArmor() && !(p.byName(ElementType.ARMOR_MATERIAL, it.armorMaterial) instanceof Elements.ArmorMaterial))
                        r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.armor_material_missing", it.name, it.armorMaterial)));
                    if (!it.isArmor() && (p.has(Target.PAPER) || p.has(Target.DATAPACK)) && !Vanilla.CMD_BASES.containsKey(Ids.full(it.baseItem)))
                        r.add(new Problem(Severity.INFO, e.uid, I18n.t("problem.base_item_model", it.baseItem)));
                }
                case StatusEffect se -> {
                    if (p.texture(se.icon) == null) r.add(new Problem(Severity.INFO, e.uid, I18n.t("problem.effect_icon", se.name)));
                }
                case Systems.Theme th -> {
                    for (Systems.TextureOverride o : th.textures) {
                        if (o.path.isBlank() || p.texture(o.texture) == null)
                            r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.theme_texture", th.name, o.path)));
                    }
                    if (th.glint.equals("texture") && p.texture(th.glintTexture) == null)
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.theme_glint", th.name)));
                    for (Systems.ScreenButton b : th.buttons) {
                        if (b.screen.equals("title") && b.action.equals("command"))
                            r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.theme_title_command", th.name, b.label)));
                        if (b.action.equals("url") && !b.value.trim().matches("https?://.+"))
                            r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.theme_url", th.name, b.label)));
                    }
                    for (Element other : p.elements) {
                        if (other == th || !(other instanceof Systems.Theme o2) || p.elements.indexOf(other) > p.elements.indexOf(th)) continue;
                        if (!th.glint.equals("keep") && !o2.glint.equals("keep") || !th.splashes.isBlank() && !o2.splashes.isBlank()
                                || th.textures.stream().anyMatch(x -> o2.textures.stream().anyMatch(y -> y.path.equals(x.path))))
                            r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.theme_overlap", th.name, o2.name)));
                    }
                }
                case Potion po -> {
                    if (po.effects.isEmpty()) r.add(new Problem(Severity.INFO, e.uid, I18n.t("problem.potion_empty", po.name)));
                    boolean old = p.mcVersions().stream().anyMatch(v -> !v.components());
                    for (PotionEffect pe : po.effects) {
                        String f = Ids.full(pe.effect.trim());
                        String path = Ids.path(f);
                        boolean known = Ids.ns(f).equals("minecraft") ? Vanilla.EFFECTS.contains(f) || Vanilla.NEW_EFFECTS.contains(f)
                                : Ids.ns(f).equals(p.meta.modId) && p.byName(ElementType.STATUS_EFFECT, path) != null;
                        if (!known) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.potion_effect", po.name, pe.effect)));
                        else if (old && Ids.ns(f).equals("minecraft") && Vanilla.NEW_EFFECTS.contains(f))
                            r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.potion_new_effect", po.name, pe.effect)));
                    }
                    if (p.all(StatusEffect.class).stream().anyMatch(se -> se.potion && se.name.equals(po.name)))
                        r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.potion_clash", po.name)));
                }
                case Enchantment en -> {
                    if (p.mcVersions().stream().anyMatch(v -> !v.atLeast(McVersion.V1_21_1)))
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.needs_121", en.name)));
                    if (en.supportedItems.isBlank()) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.enchant_items", en.name)));
                    if (en.damagePerLevel == 0 && en.protectionPerLevel == 0 && (en.attribute.isBlank() || en.attributePerLevel == 0)
                            && en.onHit.isEmpty() && en.onTick.isEmpty())
                        r.add(new Problem(Severity.INFO, e.uid, I18n.t("problem.enchant_no_effect", en.name)));
                }
                case Painting pa -> {
                    TextureAsset tex = p.texture(pa.texture);
                    if (tex == null) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.texture_missing", pa.name)));
                    else if (tex.width * pa.height != tex.frameHeight() * pa.width)
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.painting_ratio", pa.name, pa.width, pa.height)));
                }
                case Song so -> {
                    if (so.sound.isBlank()) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.song_sound", so.name)));
                    if (p.mcVersions().stream().anyMatch(v -> !v.atLeast(McVersion.V1_21_1)))
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.needs_121", so.name)));
                }
                case Elements.ArmorMaterial am -> {
                    if (p.texture(am.layer1) == null || p.texture(am.layer2) == null)
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.armor_layers", am.name)));
                    TextureAsset l1 = p.texture(am.layer1);
                    if (l1 != null && (l1.width != 2 * l1.height))
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.armor_layer_size", am.name)));
                    if (!am.repairItem.isBlank()) checkRef(p, r, e, am.repairItem);
                    if (p.all(Item.class).stream().noneMatch(i -> i.isArmor() && i.armorMaterial.equals(am.name)))
                        r.add(new Problem(Severity.INFO, e.uid, I18n.t("problem.armor_no_pieces", am.name)));
                }
                case Block b -> {
                    boolean missing = switch (b.shape) {
                        case "cube_bottom_top" -> p.texture(b.textureTop) == null || p.texture(b.textureBottom) == null || p.texture(b.textureSide) == null;
                        case "pillar" -> p.texture(b.textureTop) == null || p.texture(b.textureSide) == null;
                        case "model" -> false;
                        default -> p.texture(b.texture) == null;
                    };
                    if (missing) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.texture_missing", b.name)));
                    if (b.shape.equals("model") && p.model(b.model) == null)
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.model_missing", b.name, b.model)));
                    if (b.drop.equals("item") && !b.isCrop()) checkRef(p, r, e, b.dropItem);
                    if (b.isCrop()) {
                        if (b.stageTextures.isEmpty() || b.stageTextures.stream().anyMatch(id -> p.texture(id) == null))
                            r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.crop_stages", b.name)));
                        if (b.stageTextures.size() > 8)
                            r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.crop_too_many", b.name)));
                        if (!b.cropProduce.isBlank()) checkRef(p, r, e, b.cropProduce);
                        if (b.cropProduceMax < b.cropProduceMin)
                            r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.crop_range", b.name)));
                    }
                    if (b.creativeTab.startsWith("tab:") && p.byName(ElementType.CREATIVE_TAB, b.creativeTab.substring(4)) == null)
                        r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.unknown_tab", b.creativeTab.substring(4))));
                    for (Target t : targets) {
                        if (t == Target.PAPER || t == Target.DATAPACK || t == Target.RESOURCEPACK)
                            r.add(new Problem(Severity.INFO, e.uid, I18n.t("problem.block_unsupported", b.name, I18n.t("target." + t.id))));
                    }
                }
                case Recipe rc -> {
                    if (rc.result.isBlank()) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.recipe_no_result", rc.name)));
                    boolean empty = switch (rc.recipeType) {
                        case "shaped", "shapeless" -> rc.grid.stream().allMatch(String::isBlank);
                        case "smithing" -> rc.template.isBlank() || rc.base.isBlank() || rc.addition.isBlank();
                        case "brewing" -> rc.input.isBlank() || rc.addition.isBlank();
                        default -> rc.input.isBlank();
                    };
                    if (empty) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.recipe_empty", rc.name)));
                    if (rc.recipeType.equals("brewing")) {
                        brewing(p, r, e, rc);
                        break;
                    }
                    for (String s : rc.grid) checkRef(p, r, e, s);
                    for (String s : List.of(rc.input, rc.result, rc.template, rc.base, rc.addition)) checkRef(p, r, e, s);
                }
                case LootTable lt -> {
                    if (lt.pools.isEmpty()) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.loot_empty", lt.name)));
                    if (lt.inject && lt.replaces.isBlank()) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.loot_inject_target", lt.name)));
                    for (LootPool pool : lt.pools) {
                        for (LootEntry en : pool.entries) if (en.kind.equals("item")) checkRef(p, r, e, en.name);
                        checkPredicate(p, r, e, pool.predicate);
                    }
                }
                case Elements.Predicate pr -> {
                    if (pr.terms.isEmpty()) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.predicate_empty", pr.name)));
                    boolean oldBiomes = p.mcVersions().stream().anyMatch(v -> !v.components());
                    for (PredicateTerm t : pr.terms) {
                        switch (t.kind) {
                            case "equipment" -> checkRef(p, r, e, t.get("item"));
                            case "block_below" -> checkRef(p, r, e, t.get("block"));
                            case "predicate" -> {
                                if (t.get("predicate").trim().equals(pr.name))
                                    r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.predicate_self", pr.name)));
                                else checkPredicate(p, r, e, t.get("predicate"));
                            }
                            case "raw" -> checkJson(r, e, t.get("json"));
                            case "biome" -> {
                                if (oldBiomes && t.get("biome").trim().startsWith("#"))
                                    r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.biome_tag_old", pr.name)));
                            }
                            case "score" -> {
                                if (t.get("objective").isBlank()) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.predicate_objective", pr.name)));
                            }
                            default -> {
                            }
                        }
                    }
                }
                case Elements.ItemModifier im -> {
                    if (im.functions.isEmpty()) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.modifier_empty", im.name)));
                    for (ModifierFunction fn : im.functions) if (fn.kind.equals("raw")) checkJson(r, e, fn.get("json"));
                }
                case Advancement a -> {
                    checkRef(p, r, e, a.icon);
                    if (!a.parent.isBlank()) {
                        String par = a.parent.contains(":") ? a.parent : ns + ":" + a.parent;
                        if (par.startsWith(ns + ":") && p.byName(ElementType.ADVANCEMENT, par.substring(ns.length() + 1)) == null)
                            r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.adv_parent", a.parent)));
                    }
                }
                case Tag t -> {
                    if (t.registry.equals("item") || t.registry.equals("block")) for (String v : t.values) checkRef(p, r, e, v);
                }
                case Procedure pr -> {
                    Logic.Def d = Logic.TRIGGERS.get(pr.trigger);
                    boolean needsValue = d != null && !d.params().isEmpty() && List.of("block_break", "item_right_click", "item_crafted").contains(pr.trigger);
                    if (needsValue && pr.triggerValue.isBlank()) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.trigger_value", pr.name)));
                    if (pr.trigger.equals("cron")) {
                        if (!WebTest.cronValid(pr.triggerValue)) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.cron_invalid", pr.name, pr.triggerValue)));
                    } else {
                        checkRef(p, r, e, pr.triggerValue);
                    }
                    checkSteps(p, r, e, pr.steps);
                    List<Target> sup = Logic.triggerSupport(pr.trigger);
                    for (Target t : targets) {
                        if (t == Target.RESOURCEPACK) continue;
                        if (!sup.contains(t)) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.trigger_unsupported", I18n.t("trigger." + pr.trigger), I18n.t("target." + t.id))));
                    }
                }
                case Elements.Command c -> {
                    if (!Ids.NAME.matcher(c.name).matches()) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.command_name", c.name)));
                    checkSteps(p, r, e, c.steps);
                    for (CommandBranch b : c.branches) {
                        try {
                            CommandSpec.parse(b.path);
                        } catch (CommandSpec.SpecException ex) {
                            r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.command_path", c.name, b.path, ex.getMessage())));
                        }
                        checkSteps(p, r, e, b.steps);
                        if (p.mcVersions().contains(McVersion.V1_20_1) && usesMacro(b.steps))
                            r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.macro_old", "/" + c.name + " " + b.path)));
                    }
                }
                case Systems.Menu m -> {
                    for (Systems.MenuSlot s : m.slots) {
                        checkSteps(p, r, e, s.steps);
                        checkRef(p, r, e, s.item);
                        if (s.action.equals("menu") && p.byName(ElementType.MENU, s.target.trim()) == null)
                            r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.unknown_element", m.name, I18n.t("type.menu"), s.target)));
                    }
                    if (p.has(Target.DATAPACK)) r.add(new Problem(Severity.INFO, e.uid, I18n.t("problem.menu_chat", m.name)));
                    if (!m.background.equals("none") && !m.layout.equals("chest"))
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.menu_art_layout", m.name)));
                    else if (m.background.equals("texture") && p.texture(m.backgroundTexture) == null)
                        r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.menu_art_texture", m.name)));
                    for (Systems.MenuSlot s : m.slots) for (String fr : s.frames) checkRef(p, r, e, fr);
                }
                case Systems.Hud h -> {
                    checkSteps(p, r, e, h.onFinish);
                    boolean dynamic = h.lines.stream().anyMatch(l -> l.contains("{")) || h.title.contains("{");
                    if (h.kind.equals("sidebar") && dynamic && p.mcVersions().contains(McVersion.V1_20_1))
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.sidebar_static", h.name)));
                }
                case Systems.ApiRequest a -> {
                    for (List<Step> l : List.of(a.onSuccess, a.onClientError, a.onServerError, a.onFailure)) checkSteps(p, r, e, l);
                    if (a.schedule.equals("cron") && !WebTest.cronValid(a.cron))
                        r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.cron_invalid", a.name, a.cron)));
                    if (p.has(Target.DATAPACK)) r.add(new Problem(Severity.INFO, e.uid, I18n.t("problem.http_datapack", a.name)));
                }
                case Systems.Resource res -> {
                    checkSteps(p, r, e, res.onPollution);
                    boolean used = p.all(Systems.Machine.class).stream().anyMatch(m -> m.io.stream().anyMatch(io -> io.resource.trim().equals(res.name)));
                    if (!used) r.add(new Problem(Severity.INFO, e.uid, I18n.t("problem.resource_unused", res.name)));
                }
                case Systems.Machine m -> {
                    for (List<Step> l : List.of(m.onCycle, m.onPlace, m.onBreak, m.onOverload)) checkSteps(p, r, e, l);
                    if (m.block.isBlank()) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.machine_block", m.name)));
                    else {
                        checkRef(p, r, e, m.block.contains("[") ? m.block.substring(0, m.block.indexOf('[')) : m.block);
                        if (Ids.full(m.block).startsWith(p.meta.modId + ":") && (p.has(Target.DATAPACK) || p.has(Target.PAPER)))
                            r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.machine_project_block", m.name)));
                    }
                    for (Systems.MachineIO io : m.io) {
                        if (p.byName(ElementType.RESOURCE, io.resource.trim()) == null)
                            r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.unknown_element", m.name, I18n.t("type.resource"), io.resource.isBlank() ? "-" : io.resource)));
                    }
                    if (!m.multiblock.isBlank() && p.byName(ElementType.MULTIBLOCK, m.multiblock.trim()) == null)
                        r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.unknown_element", m.name, I18n.t("type.multiblock"), m.multiblock)));
                    if (m.io.isEmpty() && m.onCycle.isEmpty()) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.machine_idle", m.name)));
                }
                case Systems.Mob mob -> {
                    if (!mob.rig.isBlank()) {
                        Elements.Model md = p.model(mob.rig.trim());
                        if (md == null) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.rig_missing", mob.name, mob.rig)));
                        else if (md.bones.isEmpty()) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.rig_no_bones", mob.name, md.name)));
                        else {
                            for (String an : List.of(mob.idleAnim, mob.walkAnim, mob.hurtAnim, mob.attackAnim, mob.deathAnim)) {
                                if (an != null && !an.isBlank() && md.animation(an.trim()) == null) {
                                    r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.rig_animation", mob.name, an)));
                                }
                            }
                            Elements.Animation death = md.animation(mob.deathAnim.trim());
                            if (death != null && death.length > 1) r.add(new Problem(Severity.INFO, e.uid, I18n.t("problem.rig_death_long", mob.name)));
                        }
                    }
                }
                case Elements.Model md -> {
                    if (!md.bones.isEmpty()) {
                        if (Rig.reach(md) > 96) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.rig_reach", md.name)));
                        Set<String> boneNames = new HashSet<>();
                        for (Elements.Bone b : md.bones) {
                            if (b.name == null || b.name.isBlank() || !boneNames.add(Ids.toId(b.name))) {
                                r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.bone_name", md.name, b.name == null ? "" : b.name)));
                            }
                        }
                    }
                    Set<String> anims = new HashSet<>();
                    for (Elements.Animation a : md.animations) {
                        if (a.name == null || a.name.isBlank() || !anims.add(Ids.toId(a.name))) {
                            r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.animation_name", md.name, a.name == null ? "" : a.name)));
                        }
                    }
                    if (!md.animations.isEmpty() && md.bones.isEmpty()) {
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.animation_no_bones", md.name)));
                    }
                }
                case Systems.Dialog d -> {
                    if (d.kind.equals("multi_action") && d.buttons.isEmpty())
                        r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.dialog_no_buttons", d.name)));
                    int used = d.effectiveButtons().size();
                    if (d.buttons.size() > used) r.add(new Problem(Severity.INFO, e.uid, I18n.t("problem.dialog_extra_buttons", d.name, used)));
                    for (Systems.DialogButton b : d.effectiveButtons()) {
                        String target = b.target.trim();
                        switch (b.action) {
                            case "run" -> checkSteps(p, r, e, b.steps);
                            case "dialog" -> {
                                if (!target.contains(":") && p.byName(ElementType.DIALOG, target) == null)
                                    r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.unknown_element", d.name, I18n.t("type.dialog"), target.isEmpty() ? "-" : target)));
                            }
                            case "url" -> {
                                if (!target.startsWith("https://") && !target.startsWith("http://"))
                                    r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.dialog_url", d.name, b.label)));
                            }
                            case "suggest", "copy" -> {
                                if (target.isEmpty()) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.dialog_target", d.name, b.label)));
                            }
                            default -> {
                            }
                        }
                    }
                }
                case Systems.Trader tr -> {
                    if (tr.trades.isEmpty()) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.trader_empty", tr.name)));
                    if (tr.entity.equals("villager") && (tr.profession.equals("none") || tr.profession.equals("nitwit")))
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.trader_profession", tr.name)));
                    for (Systems.Trade td : tr.trades) {
                        for (String id : List.of(td.buy, td.buyB, td.sell)) checkRef(p, r, e, id);
                        if (td.buy.isBlank() || td.sell.isBlank()) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.trade_incomplete", tr.name)));
                        if (Ids.isTag(td.buy.trim()) || Ids.isTag(td.buyB.trim()) || Ids.isTag(td.sell.trim()))
                            r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.trade_tag", tr.name)));
                    }
                }
                case Systems.KeyBind k -> {
                    checkSteps(p, r, e, k.steps);
                    if (k.steps.isEmpty()) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.key_empty", k.name)));
                    for (Element o : p.elements) {
                        if (o instanceof Systems.KeyBind k2 && k2 != k && k2.key.equals(k.key) && !k.key.equals("key.keyboard.unknown")
                                && p.elements.indexOf(o) < p.elements.indexOf(e)) {
                            r.add(new Problem(Severity.INFO, e.uid, I18n.t("problem.key_duplicate", k.name, k2.name)));
                        }
                    }
                }
                case Systems.Cutscene cs -> {
                    checkSteps(p, r, e, cs.onEnd);
                    if (cs.keys.size() < 2) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.cutscene_keys", cs.name)));
                    if (cs.length() > 600) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.cutscene_long", cs.name)));
                }
                case Systems.Multiblock mb -> {
                    mb.ensure();
                    if (mb.coreX >= mb.sx || mb.coreY >= mb.sy || mb.coreZ >= mb.sz)
                        r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.multiblock_core", mb.name)));
                    if (mb.count() == 0) r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.multiblock_empty", mb.name)));
                    for (String b : mb.palette) if (!b.isBlank()) checkRef(p, r, e, b.contains("[") ? b.substring(0, b.indexOf('[')) : b);
                    if (mb.assemble) assembly(p, r, e, mb);
                }
                case Systems.Shader sh -> {
                    String prog = sh.program.trim();
                    boolean include = sh.kind.equals("include");
                    if (prog.isEmpty() || !prog.matches("[a-z0-9_./-]+"))
                        r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.shader_program", sh.name, prog)));
                    if (sh.fragment.isBlank() && (include || sh.vertex.isBlank()))
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.shader_empty", sh.name)));
                    McVersion min = McVersion.orNull(sh.minVersion), max = McVersion.orNull(sh.maxVersion);
                    if (min != null && max != null && !max.atLeast(min))
                        r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.shader_range", sh.name)));
                    if (p.mcVersions().stream().noneMatch(v -> Shaders.inRange(sh, v)))
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.shader_no_version", sh.name)));
                    List<String> issues = new ArrayList<>(Shaders.check(sh.fragment, !include));
                    if (!include) issues.addAll(Shaders.check(sh.vertex, true));
                    for (String k : new LinkedHashSet<>(issues))
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.shader_code", sh.name, I18n.t(k))));
                    if (!include) {
                        for (McVersion v : p.mcVersions()) {
                            List<String> known = Shaders.programs(v);
                            if (Shaders.inRange(sh, v) && !known.isEmpty() && !prog.isEmpty() && !known.contains(prog))
                                r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.shader_unknown", sh.name, prog, v.id)));
                        }
                    }
                }
                case CodeFile cf -> {
                    if (cf.path.isBlank()) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.codefile_path", cf.name)));
                }
                case CreativeTab tab -> {
                    checkRef(p, r, e, tab.icon);
                    if (!modTarget) r.add(new Problem(Severity.INFO, e.uid, I18n.t("problem.tab_mods_only", tab.name)));
                }
                default -> {
                }
            }
        }
        if (p.elements.stream().anyMatch(e -> e instanceof Item) && (p.has(Target.PAPER) || p.has(Target.DATAPACK)) && !p.has(Target.RESOURCEPACK)) {
            r.add(new Problem(Severity.INFO, null, I18n.t("problem.need_resourcepack")));
        }
        r.sort(Comparator.comparing(Problem::severity));
        return r;
    }

    private static void checkSteps(Project p, List<Problem> r, Element e, List<Step> steps) {
        for (Step s : steps) {
            for (String k : List.of("item", "block")) checkRef(p, r, e, s.get(k));
            checkElementRefs(p, r, e, Logic.STEPS.get(s.kind), s.p);
            if (s.cond != null) {
                checkRef(p, r, e, s.cond.get("item"));
                checkRef(p, r, e, s.cond.get("block"));
                checkElementRefs(p, r, e, Logic.CONDITIONS.get(s.cond.kind), s.cond.p);
            }
            checkSteps(p, r, e, s.then);
            checkSteps(p, r, e, s.orElse);
        }
    }

    /** Steps and conditions that name project elements (mobs, menus, requests...) must point to existing ones. */
    private static void checkElementRefs(Project p, List<Problem> r, Element e, Logic.Def def, Map<String, String> values) {
        if (def == null) return;
        for (Logic.Param prm : def.params()) {
            if (prm.type() != Logic.PType.REF) continue;
            String v = values.getOrDefault(prm.key(), "").trim();
            ElementType t = ElementType.of(prm.choices().get(0));
            if (t == null || p.byName(t, v) != null) continue;
            r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.unknown_element", e.name, I18n.t("type." + t.id), v.isEmpty() ? "-" : v)));
        }
    }

    /**
     * True when a step needs function macros. A whole argument in a number field ({@code $(amount)} as a score
     * value, condition bound or timer length) compiles to a score operation instead and works on every version.
     */
    private static boolean usesMacro(List<Step> steps) {
        for (Step s : steps) {
            if (needsMacro(s.p)) return true;
            if (s.cond != null && needsMacro(s.cond.p)) return true;
            if (usesMacro(s.then) || usesMacro(s.orElse)) return true;
        }
        return false;
    }

    static boolean needsMacro(Map<String, String> params) {
        for (Map.Entry<String, String> e : params.entrySet()) {
            String v = e.getValue();
            if (v == null || !v.contains("$(")) continue;
            boolean numeric = switch (e.getKey()) {
                case "seconds", "value", "min", "max" -> true;
                default -> false;
            };
            if (!numeric || !v.trim().matches("\\$\\([A-Za-z0-9_]+\\)")) return true;
        }
        return false;
    }

    /** Checks of a multiblock that is assembled with a tool into a machine. */
    private static void assembly(Project p, List<Problem> r, Element e, Systems.Multiblock mb) {
        if (mb.tool.isBlank()) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.multiblock_tool", mb.name)));
        else {
            checkRef(p, r, e, mb.tool.trim());
            String tool = Ids.full(mb.tool.trim());
            String base = p.itemLike(tool) instanceof Elements.Item it ? Ids.full(it.baseItem.isBlank() ? "minecraft:paper" : it.baseItem) : tool;
            if (p.has(Target.DATAPACK) && !base.equals("minecraft:carrot_on_a_stick") && !base.equals("minecraft:warped_fungus_on_a_stick"))
                r.add(new Problem(Severity.INFO, e.uid, I18n.t("problem.multiblock_tool_pack", mb.name)));
        }
        int idx = mb.coreX < mb.sx && mb.coreY < mb.sy && mb.coreZ < mb.sz ? mb.get(mb.coreX, mb.coreY, mb.coreZ) : -1;
        String core = idx >= 0 && idx < mb.palette.size() ? mb.palette.get(idx).trim() : "";
        if (core.isEmpty() || core.endsWith("air")) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.multiblock_core_block", mb.name)));
        if (!mb.model.isBlank()) {
            Elements.Model md = p.model(mb.model.trim());
            if (md == null) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.unknown_element", mb.name, I18n.t("type.model"), mb.model)));
            else {
                for (String a : List.of(mb.idleAnimation, mb.workAnimation)) {
                    if (!a.isBlank() && md.animation(a.trim()) == null)
                        r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.multiblock_animation", mb.name, a)));
                }
            }
        }
        if (!mb.resource.isBlank() && !(p.byName(ElementType.RESOURCE, mb.resource.trim()) instanceof Systems.Resource res && !res.model.equals("stress")))
            r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.multiblock_resource", mb.name, mb.resource)));
        boolean old = p.mcVersions().stream().anyMatch(v -> !v.components());
        for (Systems.MultiblockRecipe rc : mb.recipes) {
            for (String id : List.of(rc.input, rc.output, rc.secondary)) if (!id.isBlank()) checkRef(p, r, e, id.trim());
            if (rc.input.isBlank() || rc.output.isBlank())
                r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.multiblock_recipe", mb.name)));
            else if (old && rc.input.trim().startsWith("#"))
                r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.multiblock_tag_input", mb.name, rc.input)));
        }
    }

    /** A project predicate named without a namespace has to exist. */
    private static void checkPredicate(Project p, List<Problem> r, Element e, String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return;
        if (n.startsWith(p.meta.modId + ":")) n = n.substring(p.meta.modId.length() + 1);
        else if (n.contains(":")) return;
        if (p.byName(ElementType.PREDICATE, n) == null)
            r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.unknown_element", e.name, I18n.t("type.predicate"), n)));
    }

    private static void checkJson(List<Problem> r, Element e, String json) {
        try {
            if (com.google.gson.JsonParser.parseString(json).isJsonObject()) return;
        } catch (Exception ignored) {
        }
        r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.raw_json", e.name)));
    }

    private static void brewing(Project p, List<Problem> r, Element e, Recipe rc) {
        checkRef(p, r, e, rc.addition);
        if (Ids.isTag(rc.addition.trim())) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.brew_tag", rc.name)));
        boolean old = p.mcVersions().stream().anyMatch(v -> !v.components());
        for (String id : List.of(rc.input, rc.result)) {
            if (id.isBlank()) continue;
            String f = Ids.full(id.trim());
            boolean known = Ids.ns(f).equals("minecraft") ? Vanilla.POTIONS.contains(f)
                    : Ids.ns(f).equals(p.meta.modId) && (p.all(Elements.StatusEffect.class).stream().anyMatch(se -> se.potion && se.name.equals(Ids.path(f)))
                            || p.all(Elements.Potion.class).stream().anyMatch(po -> po.name.equals(Ids.path(f))));
            if (!known) r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.brew_potion", rc.name, id)));
            else if (old && Ids.ns(f).equals("minecraft") && Vanilla.NEW_POTIONS.contains(Ids.path(f)))
                r.add(new Problem(Severity.WARNING, e.uid, I18n.t("problem.brew_new_potion", rc.name, id)));
        }
    }

    private static void checkRef(Project p, List<Problem> r, Element e, String id) {
        if (id == null || id.isBlank()) return;
        String f = id.startsWith("#") ? id.substring(1) : id;
        if (id.startsWith("#")) return;
        if (f.startsWith(p.meta.modId + ":") && p.itemLike(f) == null) {
            r.add(new Problem(Severity.ERROR, e.uid, I18n.t("problem.unknown_ref", id)));
        }
    }

    public static long count(List<Problem> list, Severity s) {
        return list.stream().filter(x -> x.severity() == s).count();
    }
}
