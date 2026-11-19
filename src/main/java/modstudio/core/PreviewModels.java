package modstudio.core;

import modstudio.core.Elements.*;
import modstudio.core.ModElements.CustomEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds composite 3D {@link Model} previews for entities, datapack mobs, full 3D armor sets,
 * and 3D items / worn armor pieces so every 3D-capable editor can display a live ModelView3D.
 */
public final class PreviewModels {
    private PreviewModels() {}

    @FunctionalInterface
    public interface PreviewProvider {
        Model build(Project project, Element element);
    }

    private static final Map<ElementType, PreviewProvider> PROVIDERS = new ConcurrentHashMap<>();

    /** Registers a custom 3D preview model builder for any {@link ElementType}. */
    public static void registerProvider(ElementType type, PreviewProvider provider) {
        if (type != null && provider != null) PROVIDERS.put(type, provider);
    }

    /** Generic preview dispatch supporting plugin-registered providers and built-in 3D models. */
    public static Model forElement(Project p, Element e) {
        if (p == null || e == null) return null;
        PreviewProvider custom = PROVIDERS.get(e.type());
        if (custom != null) {
            Model res = custom.build(p, e);
            if (res != null) return res;
        }
        return switch (e) {
            case CustomEntity ce -> forCustomEntity(p, ce);
            case Systems.Mob m -> forMob(p, m);
            case ArmorMaterial am -> forArmorSet(p, am);
            case Item it -> forItem3d(p, it);
            case Model md -> copy(md);
            default -> null;
        };
    }

    /** Deep-copies cubes, bones, animations, textures and UV dims of a Model. */
    public static Model copy(Model src) {
        Model m = new Model();
        if (src == null) return m;
        m.name = src.name;
        m.kind = src.kind;
        m.ambientOcclusion = src.ambientOcclusion;
        m.textureWidth = src.textureWidth;
        m.textureHeight = src.textureHeight;
        m.textures.putAll(src.textures);
        for (Bone b : src.bones) {
            Bone nb = new Bone();
            nb.name = b.name;
            nb.parent = b.parent;
            nb.pivot = b.pivot == null ? new double[]{8, 0, 8} : b.pivot.clone();
            m.bones.add(nb);
        }
        for (Cube c : src.cubes) m.cubes.add(Models.copy(c));
        for (Animation a : src.animations) {
            Animation na = new Animation();
            na.name = a.name;
            na.length = a.length;
            na.mode = a.mode;
            for (AnimTrack tr : a.tracks) {
                AnimTrack ntr = new AnimTrack();
                ntr.bone = tr.bone;
                ntr.channel = tr.channel;
                for (AnimKey k : tr.keys) {
                    AnimKey nk = new AnimKey();
                    nk.time = k.time;
                    nk.x = k.x;
                    nk.y = k.y;
                    nk.z = k.z;
                    nk.easing = k.easing;
                    ntr.keys.add(nk);
                }
                na.tracks.add(ntr);
            }
            m.animations.add(na);
        }
        return m;
    }

    /** Builds a live 3D preview model for a CustomEntity (custom model or built-in fallback). */
    public static Model forCustomEntity(Project p, CustomEntity ce) {
        PreviewProvider custom = PROVIDERS.get(ElementType.ENTITY);
        if (custom != null) {
            Model ext = custom.build(p, ce);
            if (ext != null) return ext;
        }
        Model linked = (ce.model != null && !ce.model.isBlank()) ? p.model(ce.model.trim()) : null;
        if (linked != null) {
            Model m = copy(linked);
            if (ce.texture != null && !ce.texture.isBlank()) {
                for (var en : new ArrayList<>(m.textures.entrySet())) {
                    if (en.getValue() == null || en.getValue().isBlank()) en.setValue(ce.texture);
                }
                if (m.textures.isEmpty()) m.textures.put("0", ce.texture);
            }
            return m;
        }
        Model m = new Model();
        m.kind = "entity";
        if (ce.isProjectile()) {
            m.cubes.add(Models.cube("shaft", 7, 6.5, 2, 9, 8.5, 14));
            m.cubes.add(Models.cube("head", 6, 5.5, 0, 10, 9.5, 3));
            m.cubes.add(Models.cube("fletching_h", 5.5, 7.2, 11, 10.5, 7.8, 15.5));
            m.cubes.add(Models.cube("fletching_v", 7.7, 5, 11, 8.3, 10, 15.5));
        } else if ("quadruped".equals(ce.baseAppearance)) {
            Models.applyTemplate(m, "quadruped");
            if (ce.baby) scaleModelGeometry(m, 0.55);
            addQuadrupedAnims(m);
        } else if ("slime".equals(ce.baseAppearance)) {
            m.cubes.add(Models.cube("slime_core", 5, 2, 5, 11, 8, 11));
            Cube outer = Models.cube("slime_outer", 4, 1, 4, 12, 9, 12);
            outer.inflate = 1.2;
            m.cubes.add(outer);
            if (ce.baby) scaleModelGeometry(m, 0.55);
        } else {
            Rig.biped(m);
            if (ce.baby) shrinkBabyBody(m);
            if (ce.head != null && !ce.head.isBlank()) {
                Cube helm = Models.boneCube("equipped_head", "head", 4, 24, 4, 12, 32, 12);
                helm.inflate = 0.6;
                String headTex = resolveEquipTexture(p, ce.head);
                if (!headTex.isBlank()) {
                    m.textures.put("equip_head", headTex);
                    for (Face f : helm.faces.values()) {
                        f.texture = "equip_head";
                        f.uv = new double[]{0, 0, 16, 16};
                    }
                }
                m.cubes.add(helm);
            }
            if (ce.mainhand != null && !ce.mainhand.isBlank()) {
                Cube item = Models.boneCube("equipped_hand", "right_arm", 13, 10, 1, 15, 12, 10);
                item.rotation = -22.5;
                item.axis = "x";
                item.origin = new double[]{14, 11, 8};
                String handTex = resolveEquipTexture(p, ce.mainhand);
                if (!handTex.isBlank()) {
                    m.textures.put("equip_hand", handTex);
                    for (Face f : item.faces.values()) {
                        f.texture = "equip_hand";
                        f.uv = new double[]{0, 0, 16, 16};
                    }
                }
                m.cubes.add(item);
            }
            if (ce.canFly && ce.isLivingMob()) {
                Cube lw = Models.boneCube("wing_left", "body", 12, 16, 10, 20, 23, 11);
                lw.rotation = 22.5;
                lw.axis = "y";
                lw.origin = new double[]{12, 20, 10.5};
                Cube rw = Models.boneCube("wing_right", "body", -4, 16, 10, 4, 23, 11);
                rw.rotation = -22.5;
                rw.axis = "y";
                rw.origin = new double[]{4, 20, 10.5};
                m.cubes.add(lw);
                m.cubes.add(rw);
            }
        }
        if (ce.texture != null && !ce.texture.isBlank()) {
            m.textures.put("0", ce.texture);
        } else {
            m.textures.putIfAbsent("0", "");
        }
        return m;
    }

    /** Builds a live 3D preview model for a Systems.Mob (rig or inferred base entity silhouette). */
    public static Model forMob(Project p, Systems.Mob mob) {
        Model linked = (mob.rig != null && !mob.rig.isBlank()) ? p.model(mob.rig.trim()) : null;
        if (linked != null) return copy(linked);
        Model m = new Model();
        m.kind = "entity";
        String baseLow = mob.base == null ? "" : mob.base.toLowerCase(Locale.ROOT);
        if (baseLow.contains("slime") || baseLow.contains("magma")) {
            m.cubes.add(Models.cube("core", 5, 2, 5, 11, 8, 11));
            Cube outer = Models.cube("outer", 4, 1, 4, 12, 9, 12);
            outer.inflate = 1.2;
            m.cubes.add(outer);
        } else if (baseLow.contains("pig") || baseLow.contains("cow") || baseLow.contains("sheep")
                || baseLow.contains("wolf") || baseLow.contains("fox") || baseLow.contains("cat")
                || baseLow.contains("horse") || baseLow.contains("goat") || baseLow.contains("llama")) {
            Models.applyTemplate(m, "quadruped");
            addQuadrupedAnims(m);
        } else {
            Rig.biped(m);
            if (mob.baby) shrinkBabyBody(m);
        }
        m.textures.putIfAbsent("0", "");
        return m;
    }

    /**
     * Builds a full-body 3D preview for an ArmorMaterial combining the mannequin body
     * and any configured 3D helmet / chestplate / leggings / boots models (or 3D template layers).
     */
    public static Model forArmorSet(Project p, ArmorMaterial am) {
        Model composite = new Model();
        composite.kind = "entity";
        Rig.biped(composite);
        composite.textures.put("mannequin", "");
        for (Cube c : composite.cubes) {
            c.name = "mannequin_" + c.name;
            for (Face f : c.faces.values()) f.texture = "mannequin";
        }
        String matTex = (am.armorTexture != null && !am.armorTexture.isBlank()) ? am.armorTexture : am.layer1;
        String legTex = (am.armorTexture != null && !am.armorTexture.isBlank()) ? am.armorTexture : am.layer2;

        String[] slots = {"helmet", "chestplate", "leggings", "boots"};
        for (String slot : slots) {
            String customName = am.modelForSlot(slot);
            Model src = (customName != null && !customName.isBlank()) ? p.model(customName.trim()) : null;
            boolean templateShell = false;
            if (src == null) {
                // Only add vanilla armor shell preview if at least a worn texture exists on that slot
                String fallbackTex = slot.equals("leggings") ? legTex : matTex;
                if (fallbackTex == null || fallbackTex.isBlank()) continue;
                src = new Model();
                Models.applyTemplate(src, "armor_" + slot);
                templateShell = true;
            }
            String defaultTex = slot.equals("leggings")
                    ? (legTex != null && !legTex.isBlank() ? legTex : matTex)
                    : matTex;
            double delta = !templateShell ? 0.0 : switch (slot) {
                case "chestplate" -> 0.28;
                case "leggings" -> 0.14;
                default -> 0.22;
            };
            mergeSlotModel(composite, src, slot, defaultTex, delta);
        }
        return composite;
    }

    /** Builds a 3D preview model for an Item (custom model, 3D worn armor model, or flat 3D plate). */
    public static Model forItem3d(Project p, Item it) {
        if (it.isArmor()) {
            Model worn = null;
            if (it.wornModel != null && !it.wornModel.isBlank()) worn = p.model(it.wornModel.trim());
            String matKey = it.armorMaterial == null ? "" : Ids.path(it.armorMaterial.trim());
            ArmorMaterial am = (!matKey.isBlank()
                    && p.byName(ElementType.ARMOR_MATERIAL, matKey) instanceof ArmorMaterial found) ? found : null;
            if (worn == null && am != null) {
                String slotM = am.modelForSlot(it.kind);
                if (slotM != null && !slotM.isBlank()) worn = p.model(slotM.trim());
            }
            if (worn != null) {
                Model m = copy(worn);
                if (am != null) {
                    String tx = !am.armorTexture.isBlank() ? am.armorTexture : it.kind.equals("leggings") ? am.layer2 : am.layer1;
                    if (!tx.isBlank() && m.textures.values().stream().allMatch(v -> v == null || v.isBlank())) {
                        m.textures.put("0", tx);
                    }
                }
                return m;
            }
        }
        if (it.model != null && !it.model.isBlank()) {
            Model m = p.model(it.model.trim());
            if (m != null) return copy(m);
        }
        // Fallback: extruded 3D card/blade silhouette using item texture
        Model plate = new Model();
        plate.kind = "item";
        plate.textures.put("0", it.texture == null ? "" : it.texture);
        Cube c = Models.cube("item_plate", 2, 2, 7.25, 14, 14, 8.75);
        for (String dir : Cube.FACES) {
            Face f = c.face(dir);
            f.texture = "0";
            if (!dir.equals("north") && !dir.equals("south")) f.uv = new double[]{0, 0, 1, 16};
        }
        if (it.isTool()) {
            c.rotation = -45.0;
            c.axis = "z";
            c.origin = new double[]{8, 8, 8};
        }
        plate.cubes.add(c);
        return plate;
    }

    private static void mergeSlotModel(Model dst, Model src, String slot, String fallbackTex, double inflateDelta) {
        Map<String, String> keyMap = new LinkedHashMap<>();
        for (var en : src.textures.entrySet()) {
            String newKey = slot + "_" + en.getKey();
            String val = (en.getValue() != null && !en.getValue().isBlank()) ? en.getValue() : fallbackTex;
            dst.textures.put(newKey, val == null ? "" : val);
            keyMap.put(en.getKey(), newKey);
        }
        if (keyMap.isEmpty()) {
            String newKey = slot + "_0";
            dst.textures.put(newKey, fallbackTex == null ? "" : fallbackTex);
            keyMap.put("0", newKey);
        }
        int seq = 0;
        for (Cube c : src.cubes) {
            Cube copy = Models.copy(c);
            copy.name = slot + "_" + (c.name == null ? "part" : c.name) + "_" + (++seq);
            copy.inflate = c.inflate + inflateDelta;
            if (copy.bone != null && dst.bone(copy.bone) == null) copy.bone = "";
            for (Face f : copy.faces.values()) {
                f.texture = keyMap.getOrDefault(f.texture, keyMap.values().iterator().next());
            }
            dst.cubes.add(copy);
        }
    }

    private static String resolveEquipTexture(Project p, String id) {
        if (p == null || id == null || id.isBlank()) return "";
        String path = Ids.path(id.trim());
        Element el = p.itemLike(id.trim());
        if (el instanceof Item it && it.texture != null && !it.texture.isBlank()) return it.texture;
        if (el instanceof Block bl && bl.texture != null && !bl.texture.isBlank()) return bl.texture;
        TextureAsset ta = p.textureByName(path);
        return ta != null ? ta.id : "";
    }

    private static void scaleModelGeometry(Model m, double factor) {
        for (Cube c : m.cubes) {
            for (int axis = 0; axis < 3; axis++) {
                double midX = 8.0;
                if (axis == 0 || axis == 2) {
                    c.from[axis] = midX + (c.from[axis] - midX) * factor;
                    c.to[axis] = midX + (c.to[axis] - midX) * factor;
                } else {
                    c.from[1] *= factor;
                    c.to[1] *= factor;
                }
            }
        }
        for (Bone b : m.bones) {
            if (b.pivot != null && b.pivot.length >= 3) {
                b.pivot[0] = 8.0 + (b.pivot[0] - 8.0) * factor;
                b.pivot[1] *= factor;
                b.pivot[2] = 8.0 + (b.pivot[2] - 8.0) * factor;
            }
        }
    }

    private static void shrinkBabyBody(Model m) {
        for (Cube c : m.cubes) {
            if ("head".equals(c.bone) || "head".equals(c.name)) {
                c.from = new double[]{4, 15, 4};
                c.to = new double[]{12, 23, 12};
            } else if ("body".equals(c.bone)) {
                c.from = new double[]{5.5, 7, 6.5};
                c.to = new double[]{10.5, 15, 9.5};
            } else if ("left_arm".equals(c.bone)) {
                c.from = new double[]{3, 7, 6.5};
                c.to = new double[]{5.5, 15, 9.5};
            } else if ("right_arm".equals(c.bone)) {
                c.from = new double[]{10.5, 7, 6.5};
                c.to = new double[]{13, 15, 9.5};
            } else if ("left_leg".equals(c.bone)) {
                c.from = new double[]{5.5, 0, 6.5};
                c.to = new double[]{8, 7, 9.5};
            } else if ("right_leg".equals(c.bone)) {
                c.from = new double[]{8, 0, 6.5};
                c.to = new double[]{10.5, 7, 9.5};
            }
        }
    }

    private static void addQuadrupedAnims(Model m) {
        Animation idle = new Animation();
        idle.name = "idle";
        idle.length = 2.0;
        idle.mode = "loop";
        Rig.key(idle, "head", "rotation", "smooth", new double[][]{{0, 0, 0, 0}, {1, -5, 0, 0}, {2, 0, 0, 0}});
        Animation walk = new Animation();
        walk.name = "walk";
        walk.length = 1.0;
        walk.mode = "loop";
        Rig.applyPreset(walk, "leg_fl", "swing");
        Rig.applyPreset(walk, "leg_br", "swing");
        Rig.applyPreset(walk, "leg_fr", "swing_back");
        Rig.applyPreset(walk, "leg_bl", "swing_back");
        m.animations.add(idle);
        m.animations.add(walk);
    }
}

