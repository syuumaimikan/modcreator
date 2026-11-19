package modstudio.gen;

import modstudio.core.Elements;
import modstudio.core.Elements.ArmorMaterial;
import modstudio.core.Elements.Bone;
import modstudio.core.Elements.Cube;
import modstudio.core.Elements.Face;
import modstudio.core.Elements.Item;
import modstudio.core.Elements.Model;
import modstudio.core.Ids;
import modstudio.core.McVersion;
import modstudio.core.ModElements.CustomEntity;
import modstudio.core.Models;
import modstudio.core.Project;
import modstudio.core.TextureAsset;
import modstudio.gen.JavaCommon.Loader;

import java.util.*;

/**
 * Generates native Minecraft EntityModel, LayerDefinition, custom MobRenderer/EntityRenderer, and 3D HumanoidModel
 * armor renderers across Fabric, Forge, and NeoForge (MC 1.20.1 - 26.x).
 */
public final class ModelGen {
    private final GenContext ctx;
    private final Loader loader;

    public ModelGen(GenContext ctx, Loader loader) {
        this.ctx = ctx;
        this.loader = loader;
    }

    private boolean fabric() { return loader == Loader.FABRIC; }
    private boolean neo() { return loader == Loader.NEOFORGE; }
    private String main() { return ctx.mainClass; }
    private boolean unobf() { return ctx.v.unobfuscated(); }

    // ------------------------------------------------------------------ resolution helpers

    public static boolean hasCustomArmor3d(GenContext ctx) {
        for (Item it : ctx.all(Item.class)) {
            if (it.isArmor() && wornArmorModel(ctx.p, it) != null) return true;
        }
        return false;
    }

    public static Model wornArmorModel(Project p, Item it) {
        if (it == null) return null;
        if (it.wornModel != null && !it.wornModel.isBlank()) {
            Model m = p.model(it.wornModel.trim());
            if (m != null) return m;
        }
        if (it.armorMaterial != null && !it.armorMaterial.isBlank()) {
            if (p.byName(modstudio.core.ElementType.ARMOR_MATERIAL, it.armorMaterial.trim()) instanceof ArmorMaterial am) {
                String slotModel = am.modelForSlot(it.kind);
                if (slotModel != null && !slotModel.isBlank()) return p.model(slotModel.trim());
            }
        }
        return null;
    }

    public static ArmorMaterial armorMaterialOf(Project p, Item it) {
        if (it.armorMaterial == null || it.armorMaterial.isBlank()) return null;
        return p.byName(modstudio.core.ElementType.ARMOR_MATERIAL, it.armorMaterial.trim()) instanceof ArmorMaterial am ? am : null;
    }

    /** Returns custom or non-biped built-in 3D model for an entity, or null to keep default HumanoidMobRenderer / ThrownItemRenderer. */
    public static Model entityModelSpec(Project p, CustomEntity ce) {
        if (ce.model != null && !ce.model.isBlank()) {
            Model m = p.model(ce.model.trim());
            if (m != null) return m;
        }
        if (ce.isLivingMob()) {
            if ("quadruped".equals(ce.baseAppearance)) {
                Model m = new Model();
                m.name = ce.name + "_quadruped";
                Models.applyTemplate(m, "quadruped");
                return m;
            }
            if ("slime".equals(ce.baseAppearance)) {
                Model m = new Model();
                m.name = ce.name + "_slime";
                m.kind = "entity";
                m.textureWidth = 64;
                m.textureHeight = 32;
                Bone b = new Bone();
                b.name = "body";
                b.pivot = new double[]{8, 8, 8};
                m.bones.add(b);
                m.cubes.add(Models.boneCube("core", "body", 5, 1, 5, 11, 7, 11));
                Cube shell = Models.boneCube("shell", "body", 4, 0, 4, 12, 8, 12);
                shell.inflate = 0.25;
                m.cubes.add(shell);
                return m;
            }
        }
        return null;
    }

    /** First valid texture asset attached to a model (for entities or 3D armor without an explicit texture override). */
    public static TextureAsset firstModelTexture(Project p, Model m) {
        if (m == null) return null;
        for (String v : m.textures.values()) {
            if (v == null || v.isBlank()) continue;
            TextureAsset ta = p.texture(v);
            if (ta != null) return ta;
        }
        return null;
    }

    public static String armor3dTexKey(Project p, Item it, ArmorMaterial am, Model mdl) {
        if (am != null && !am.armorTexture.isBlank() && p.texture(am.armorTexture) != null) return "mat_" + am.name;
        if (firstModelTexture(p, mdl) != null) return "mdl_" + Ids.toId(mdl.name);
        if (am != null && !am.layer1.isBlank() && p.texture(am.layer1) != null) return "lay_" + am.name;
        return null;
    }

    // ------------------------------------------------------------------ generation entry

    public void generate() {
        for (CustomEntity ce : ctx.all(CustomEntity.class)) {
            Model mdl = entityModelSpec(ctx.p, ce);
            if (mdl == null) continue;
            String entityCls = Ids.toPascal(ce.name) + "Entity";
            String modelCls = Ids.toPascal(ce.name) + "Model";
            String rendererCls = Ids.toPascal(ce.name) + "Renderer";
            entityModelClass(ce, mdl, entityCls, modelCls);
            entityRendererClass(ce, mdl, entityCls, modelCls, rendererCls);
        }
        if (hasCustomArmor3d(ctx)) {
            armorModelsClass();
        }
    }

    public void emitLayerRegistrations(Src s, boolean fabric, boolean neo, boolean forgeBus7) {
        List<String> modelClasses = customEntityModelClasses();
        boolean armor = hasCustomArmor3d(ctx);
        if (modelClasses.isEmpty() && !armor) return;
        if (fabric) {
            String layerReg = unobf() ? "ModelLayerRegistry" : "EntityModelLayerRegistry";
            s.imp("net.fabricmc.fabric.api.client.rendering.v1." + layerReg);
            for (String mc : modelClasses) {
                s.imp(ctx.pkg + ".client." + mc);
                s.l(layerReg + ".registerModelLayer(" + mc + ".LAYER_LOCATION, " + mc + "::createBodyLayer);");
            }
            if (armor) {
                s.imp(ctx.pkg + ".client.ModArmorModels");
                s.l("ModArmorModels.registerLayers();");
            }
        } else if (neo) {
            s.imp("net.neoforged.neoforge.client.event.EntityRenderersEvent");
            s.l("modBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions ev) -> {");
            for (String mc : modelClasses) {
                s.imp(ctx.pkg + ".client." + mc);
                s.l("    ev.registerLayerDefinition(" + mc + ".LAYER_LOCATION, " + mc + "::createBodyLayer);");
            }
            if (armor) {
                s.imp(ctx.pkg + ".client.ModArmorModels");
                s.l("    ModArmorModels.registerLayers(ev);");
            }
            s.l("});");
        } else {
            // Forge registers EntityRenderersEvent.RegisterLayerDefinitions directly on modBus
            s.imp("net.minecraftforge.client.event.EntityRenderersEvent");
            if (forgeBus7) {
                s.l("EntityRenderersEvent.RegisterLayerDefinitions.getBus(modBus).addListener(ev -> {");
            } else {
                s.l("modBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions ev) -> {");
            }
            for (String mc : modelClasses) {
                s.imp(ctx.pkg + ".client." + mc);
                s.l("    ev.registerLayerDefinition(" + mc + ".LAYER_LOCATION, " + mc + "::createBodyLayer);");
            }
            if (armor) {
                s.imp(ctx.pkg + ".client.ModArmorModels");
                s.l("    ModArmorModels.registerLayers(ev);");
            }
            s.l("});");
        }
    }

    public void emitArmorRegistrations(Src s, boolean fabric, boolean neo) {
        if (!hasCustomArmor3d(ctx)) return;
        s.imp(ctx.pkg + ".client.ModArmorModels");
        if (fabric) s.l("ModArmorModels.register();");
        else if (neo) s.l("ModArmorModels.register(modBus);");
    }

    private List<String> customEntityModelClasses() {
        List<String> list = new ArrayList<>();
        for (CustomEntity ce : ctx.all(CustomEntity.class)) {
            if (entityModelSpec(ctx.p, ce) != null) list.add(Ids.toPascal(ce.name) + "Model");
        }
        return list;
    }

    // ------------------------------------------------------------------ EntityModel & Renderer generation

    private String rlType() { return ctx.rlType(); }
    private String rlImport() { return ctx.rlImport(); }
    private String rlFrom(String modidExpr, String pathExpr) { return ctx.rlFrom(modidExpr, pathExpr); }
    private String rlParse(String literal) { return ctx.rlParseExpr(literal); }

    private void entityModelClass(CustomEntity ce, Model mdl, String entityCls, String modelCls) {
        Src s = new Src(ctx.pkg + ".client");
        boolean stateBased = ctx.registryKeys();
        s.imp(rlImport(),
                "net.minecraft.client.model.geom.ModelLayerLocation",
                "net.minecraft.client.model.geom.ModelPart",
                "net.minecraft.client.model.geom.PartPose",
                "net.minecraft.client.model.geom.builders.CubeDeformation",
                "net.minecraft.client.model.geom.builders.CubeListBuilder",
                "net.minecraft.client.model.geom.builders.LayerDefinition",
                "net.minecraft.client.model.geom.builders.MeshDefinition",
                "net.minecraft.client.model.geom.builders.PartDefinition",
                "net.minecraft.util.Mth",
                ctx.pkg + "." + main());
        String baseClass;
        if (stateBased) {
            String st = ce.isProjectile() ? "EntityRenderState" : "LivingEntityRenderState";
            s.imp("net.minecraft.client.model.EntityModel",
                    "net.minecraft.client.renderer.entity.state." + st);
            baseClass = "EntityModel<" + st + ">";
        } else {
            s.imp("net.minecraft.client.model.HierarchicalModel", ctx.pkg + ".entity." + entityCls);
            baseClass = "HierarchicalModel<" + entityCls + ">";
        }

        // Collect rendered parts map (boneName -> parentField/Lookup)
        List<Bone> ordered = orderedBones(mdl);
        Map<String, Bone> byName = new LinkedHashMap<>();
        for (Bone b : ordered) byName.put(Ids.toId(b.name), b);
        boolean needsRootPart = !stateBased || ordered.isEmpty();

        s.l("public class " + modelCls + " extends " + baseClass + " {");
        s.l("public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(" + rlFrom(main() + ".MODID", Src.str(ce.name)) + ", \"main\");");
        if (needsRootPart) s.l("private final ModelPart root;");
        for (Bone b : ordered) {
            s.l("private final ModelPart " + boneField(b.name) + ";");
        }
        s.blank();
        s.l("public " + modelCls + "(ModelPart root) {");
        if (stateBased) s.l("super(root);");
        if (needsRootPart) s.l("this.root = root;");
        for (Bone b : ordered) {
            String bn = Ids.toId(b.name);
            String par = Ids.toId(b.parent);
            String parentExpr = !par.isEmpty() && byName.containsKey(par) && !par.equals(bn) ? "this." + boneField(par) : "root";
            s.l("this." + boneField(b.name) + " = " + parentExpr + ".getChild(" + Src.str(bn) + ");");
        }
        s.l("}");
        s.blank();

        // Store initial PartPose rotations so setupAnim adds delta around base pose
        Map<String, float[]> baseRot = new LinkedHashMap<>();
        s.l("public static LayerDefinition createBodyLayer() {");
        s.l("MeshDefinition meshdefinition = new MeshDefinition();");
        s.l("PartDefinition partdefinition = meshdefinition.getRoot();");

        int texW = resolvedTexW(ce, mdl), texH = resolvedTexH(ce, mdl);
        int[] cubeSeq = {0};
        if (ordered.isEmpty()) {
            // Unboned cube model centered at ground (8, 0, 8)
            PartEmitter pe = new PartEmitter(s, mdl, texW, texH, cubeSeq);
            pe.emitPart("partdefinition", "body", 0, 0, 0, 0, 0, 0, 8, 0, 8, mdl.cubes);
        } else {
            Map<String, List<Cube>> cubesByBone = new LinkedHashMap<>();
            List<Cube> orphan = new ArrayList<>();
            for (Cube c : mdl.cubes) {
                String b = c.bone == null ? "" : Ids.toId(c.bone);
                if (!b.isEmpty() && byName.containsKey(b)) cubesByBone.computeIfAbsent(b, k -> new ArrayList<>()).add(c);
                else orphan.add(c);
            }
            if (!orphan.isEmpty()) {
                cubesByBone.computeIfAbsent(Ids.toId(ordered.get(0).name), k -> new ArrayList<>()).addAll(orphan);
            }
            PartEmitter pe = new PartEmitter(s, mdl, texW, texH, cubeSeq);
            for (Bone b : ordered) {
                String bn = Ids.toId(b.name);
                String par = Ids.toId(b.parent);
                boolean hasPar = !par.isEmpty() && byName.containsKey(par) && !par.equals(bn);
                Bone pb = hasPar ? byName.get(par) : null;
                double ppx = pb != null ? pb.pivot[0] : 8.0;
                double ppy = pb != null ? pb.pivot[1] : 24.0;
                double ppz = pb != null ? pb.pivot[2] : 8.0;
                float offX = (float) (b.pivot[0] - ppx);
                float offY = (float) (-(b.pivot[1] - ppy));
                float offZ = (float) (b.pivot[2] - ppz);
                float rx = 0f;
                float ry = 0f;
                float rz = 0f;
                baseRot.put(bn, new float[]{rx, ry, rz});
                String parVar = hasPar ? "part_" + boneField(par) : "partdefinition";
                String var = "part_" + boneField(b.name);
                pe.emitPartVar(parVar, var, bn, offX, offY, offZ, rx, ry, rz, b.pivot[0], b.pivot[1], b.pivot[2],
                        cubesByBone.getOrDefault(bn, List.of()));
            }
        }
        s.l("return LayerDefinition.create(meshdefinition, " + texW + ", " + texH + ");");
        s.l("}");

        if (!stateBased) {
            s.blank().l("@Override public ModelPart root() { return this.root; }");
            s.blank().l("@Override");
            s.l("public void setupAnim(" + entityCls + " entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {");
        } else {
            String st = ce.isProjectile() ? "EntityRenderState" : "LivingEntityRenderState";
            s.blank().l("@Override");
            s.l("public void setupAnim(" + st + " state) {");
            if (!ce.isProjectile()) {
                s.l("float limbSwing = state.walkAnimationPos;");
                s.l("float limbSwingAmount = state.walkAnimationSpeed;");
                s.l("float ageInTicks = state.ageInTicks;");
                s.l("float netHeadYaw = state.yRot;");
                s.l("float headPitch = state.xRot;");
            } else {
                s.l("float ageInTicks = state.ageInTicks;");
            }
        }
        emitBoneAnimations(s, ce, ordered, baseRot);
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "client/" + modelCls + ".java", s.toString());
    }

    private void emitBoneAnimations(Src s, CustomEntity ce, List<Bone> ordered, Map<String, float[]> baseRot) {
        if (ce.isProjectile()) {
            if (!ordered.isEmpty()) {
                String f0 = boneField(ordered.get(0).name);
                float[] base = baseRot.getOrDefault(Ids.toId(ordered.get(0).name), new float[3]);
                s.l("this." + f0 + ".yRot = " + Src.f(base[1]) + " + ageInTicks * 0.25F;");
            }
            return;
        }
        for (Bone b : ordered) {
            String bn = Ids.toId(b.name);
            String fn = "this." + boneField(b.name);
            float[] base = baseRot.getOrDefault(bn, new float[3]);
            String bx = Src.f(base[0]), by = Src.f(base[1]), bz = Src.f(base[2]);
            if (bn.contains("head") || bn.equals("skull")) {
                s.l(fn + ".yRot = " + by + " + netHeadYaw * 0.017453292F;");
                s.l(fn + ".xRot = " + bx + " + headPitch * 0.017453292F;");
            } else if (bn.equals("right_leg") || bn.equals("leg_fl") || bn.equals("leg_br") || bn.contains("leg_r")) {
                s.l(fn + ".xRot = " + bx + " + Mth.cos(limbSwing * 0.6662F) * 1.2F * limbSwingAmount;");
            } else if (bn.equals("left_leg") || bn.equals("leg_fr") || bn.equals("leg_bl") || bn.contains("leg_l")) {
                s.l(fn + ".xRot = " + bx + " + Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 1.2F * limbSwingAmount;");
            } else if (bn.equals("right_arm") || bn.contains("arm_r")) {
                s.l(fn + ".xRot = " + bx + " + Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 1.1F * limbSwingAmount;");
                s.l(fn + ".zRot = " + bz + " + Mth.cos(ageInTicks * 0.09F) * 0.04F + 0.04F;");
            } else if (bn.equals("left_arm") || bn.contains("arm_l")) {
                s.l(fn + ".xRot = " + bx + " + Mth.cos(limbSwing * 0.6662F) * 1.1F * limbSwingAmount;");
                s.l(fn + ".zRot = " + bz + " - Mth.cos(ageInTicks * 0.09F) * 0.04F - 0.04F;");
            } else if (bn.contains("wing")) {
                boolean left = bn.endsWith("_l") || bn.contains("left");
                s.l(fn + ".zRot = " + bz + (left ? " + " : " - ") + "Mth.sin(ageInTicks * 0.28F) * 0.35F;");
            } else if (bn.contains("tail")) {
                s.l(fn + ".yRot = " + by + " + Mth.sin(ageInTicks * 0.14F) * 0.18F;");
            }
        }
    }

    private void entityRendererClass(CustomEntity ce, Model mdl, String entityCls, String modelCls, String rendererCls) {
        Src s = new Src(ctx.pkg + ".client");
        boolean hasCustomTex = (ce.texture != null && !ce.texture.isBlank() && ctx.p.texture(ce.texture) != null)
                || firstModelTexture(ctx.p, mdl) != null;
        String texExpr = hasCustomTex
                ? rlFrom(main() + ".MODID", Src.str("textures/entity/" + ce.name + ".png"))
                : rlParse("slime".equals(ce.baseAppearance)
                ? "\"minecraft:textures/entity/slime/slime.png\""
                : "quadruped".equals(ce.baseAppearance)
                ? "\"minecraft:textures/entity/wolf/wolf.png\""
                : "\"minecraft:textures/entity/zombie/zombie.png\"");

        s.imp(rlImport(), "net.minecraft.client.renderer.entity.EntityRendererProvider",
                ctx.pkg + ".entity." + entityCls, ctx.pkg + "." + main());

        if (ce.isProjectile()) {
            // Custom 3D projectile renderer
            s.imp("com.mojang.blaze3d.vertex.PoseStack",
                    "net.minecraft.client.renderer.texture.OverlayTexture");
            if (unobf()) {
                s.imp("net.minecraft.client.renderer.SubmitNodeCollector",
                        "net.minecraft.client.renderer.entity.EntityRenderer",
                        "net.minecraft.client.renderer.entity.state.EntityRenderState",
                        "net.minecraft.client.renderer.rendertype.RenderTypes",
                        "net.minecraft.client.renderer.state.level.CameraRenderState");
                s.l("public class " + rendererCls + " extends EntityRenderer<" + entityCls + ", EntityRenderState> {");
                s.l("private static final " + rlType() + " TEX = " + texExpr + ";");
                s.l("private final " + modelCls + " model;");
                s.l("public " + rendererCls + "(EntityRendererProvider.Context ctx) { super(ctx); this.model = new " + modelCls + "(ctx.bakeLayer(" + modelCls + ".LAYER_LOCATION)); }");
                s.l("@Override public EntityRenderState createRenderState() { return new EntityRenderState(); }");
                s.l("@Override public void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {");
                s.l("poseStack.pushPose();");
                if (Math.abs(ce.scale - 1.0) > 0.001) s.l("poseStack.scale(" + Src.f(ce.scale) + ", " + Src.f(ce.scale) + ", " + Src.f(ce.scale) + ");");
                s.l("collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(TEX), state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);");
                s.l("poseStack.popPose(); super.submit(state, poseStack, collector, camera);");
                s.l("}");
            } else if (ctx.registryKeys()) {
                s.imp("net.minecraft.client.renderer.MultiBufferSource",
                        "net.minecraft.client.renderer.RenderType",
                        "net.minecraft.client.renderer.entity.EntityRenderer",
                        "net.minecraft.client.renderer.entity.state.EntityRenderState");
                s.l("public class " + rendererCls + " extends EntityRenderer<" + entityCls + ", EntityRenderState> {");
                s.l("private static final " + rlType() + " TEX = " + texExpr + ";");
                s.l("private final " + modelCls + " model;");
                s.l("public " + rendererCls + "(EntityRendererProvider.Context ctx) { super(ctx); this.model = new " + modelCls + "(ctx.bakeLayer(" + modelCls + ".LAYER_LOCATION)); }");
                s.l("@Override public EntityRenderState createRenderState() { return new EntityRenderState(); }");
                s.l("@Override public void render(EntityRenderState state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {");
                s.l("poseStack.pushPose();");
                if (Math.abs(ce.scale - 1.0) > 0.001) s.l("poseStack.scale(" + Src.f(ce.scale) + ", " + Src.f(ce.scale) + ", " + Src.f(ce.scale) + ");");
                s.l("this.model.setupAnim(state);");
                s.l("this.model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(TEX)), packedLight, OverlayTexture.NO_OVERLAY);");
                s.l("poseStack.popPose(); super.render(state, poseStack, buffer, packedLight);");
                s.l("}");
            } else {
                s.imp("com.mojang.blaze3d.vertex.VertexConsumer",
                        "net.minecraft.client.renderer.MultiBufferSource",
                        "net.minecraft.client.renderer.RenderType",
                        "net.minecraft.client.renderer.entity.EntityRenderer");
                s.l("public class " + rendererCls + " extends EntityRenderer<" + entityCls + "> {");
                s.l("private static final " + rlType() + " TEX = " + texExpr + ";");
                s.l("private final " + modelCls + " model;");
                s.l("public " + rendererCls + "(EntityRendererProvider.Context ctx) { super(ctx); this.model = new " + modelCls + "(ctx.bakeLayer(" + modelCls + ".LAYER_LOCATION)); }");
                s.l("@Override public " + rlType() + " getTextureLocation(" + entityCls + " entity) { return TEX; }");
                s.l("@Override public void render(" + entityCls + " entity, float yaw, float pt, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {");
                s.l("poseStack.pushPose();");
                if (Math.abs(ce.scale - 1.0) > 0.001) s.l("poseStack.scale(" + Src.f(ce.scale) + ", " + Src.f(ce.scale) + ", " + Src.f(ce.scale) + ");");
                s.l("this.model.setupAnim(entity, 0.0F, 0.0F, entity.tickCount + pt, 0.0F, 0.0F);");
                s.l("VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEX));");
                s.l("this.model.renderToBuffer(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY"
                        + (ctx.components() ? ");" : ", 1.0F, 1.0F, 1.0F, 1.0F);"));
                s.l("poseStack.popPose(); super.render(entity, yaw, pt, poseStack, buffer, packedLight);");
                s.l("}");
            }
            s.l("}");
            ctx.files.text(ctx.javaDir() + "client/" + rendererCls + ".java", s.toString());
            return;
        }

        // Living mob MobRenderer using custom ModelCls
        float shadow = (float) Math.max(0.2, Math.min(2.5, ce.width * 0.75 * Math.max(0.2, ce.scale)));
        s.imp("net.minecraft.client.renderer.entity.MobRenderer");
        if (Math.abs(ce.scale - 1.0) > 0.001 && !ctx.components()) s.imp("com.mojang.blaze3d.vertex.PoseStack");
        if (ctx.registryKeys()) {
            s.imp("net.minecraft.client.renderer.entity.state.LivingEntityRenderState");
            s.l("public class " + rendererCls + " extends MobRenderer<" + entityCls + ", LivingEntityRenderState, " + modelCls + "> {");
            s.l("private static final " + rlType() + " TEX = " + texExpr + ";");
            s.l("public " + rendererCls + "(EntityRendererProvider.Context ctx) { super(ctx, new " + modelCls + "(ctx.bakeLayer(" + modelCls + ".LAYER_LOCATION)), " + Src.f(shadow) + "); }");
            s.l("@Override public LivingEntityRenderState createRenderState() { return new LivingEntityRenderState(); }");
            s.l("@Override public " + rlType() + " getTextureLocation(LivingEntityRenderState state) { return TEX; }");
        } else {
            s.l("public class " + rendererCls + " extends MobRenderer<" + entityCls + ", " + modelCls + "> {");
            s.l("private static final " + rlType() + " TEX = " + texExpr + ";");
            s.l("public " + rendererCls + "(EntityRendererProvider.Context ctx) { super(ctx, new " + modelCls + "(ctx.bakeLayer(" + modelCls + ".LAYER_LOCATION)), " + Src.f(shadow) + "); }");
            s.l("@Override public " + rlType() + " getTextureLocation(" + entityCls + " entity) { return TEX; }");
            if (Math.abs(ce.scale - 1.0) > 0.001 && !ctx.components()) {
                s.l("@Override protected void scale(" + entityCls + " entity, PoseStack poseStack, float partialTick) { poseStack.scale("
                        + Src.f(ce.scale) + ", " + Src.f(ce.scale) + ", " + Src.f(ce.scale) + "); }");
            }
        }
        s.l("}");
        ctx.files.text(ctx.javaDir() + "client/" + rendererCls + ".java", s.toString());
    }

    // ------------------------------------------------------------------ 3D Humanoid Armor models

    private record Armor3dSpec(Item item, ArmorMaterial mat, Model model, String fieldPrefix, String texKey) {}

    private List<Armor3dSpec> armor3dSpecs() {
        List<Armor3dSpec> list = new ArrayList<>();
        for (Item it : ctx.all(Item.class)) {
            if (!it.isArmor()) continue;
            Model mdl = wornArmorModel(ctx.p, it);
            if (mdl == null) continue;
            ArmorMaterial am = armorMaterialOf(ctx.p, it);
            String key = armor3dTexKey(ctx.p, it, am, mdl);
            list.add(new Armor3dSpec(it, am, mdl, Ids.toConst(it.name), key));
        }
        return list;
    }

    private void armorModelsClass() {
        List<Armor3dSpec> specs = armor3dSpecs();
        if (specs.isEmpty()) return;

        Src s = new Src(ctx.pkg + ".client");
        s.imp(rlImport(),
                "net.minecraft.client.Minecraft",
                "net.minecraft.client.model.HumanoidModel",
                "net.minecraft.client.model.Model",
                "net.minecraft.client.model.geom.ModelLayerLocation",
                "net.minecraft.client.model.geom.PartPose",
                "net.minecraft.client.model.geom.builders.CubeDeformation",
                "net.minecraft.client.model.geom.builders.CubeListBuilder",
                "net.minecraft.client.model.geom.builders.LayerDefinition",
                "net.minecraft.client.model.geom.builders.MeshDefinition",
                "net.minecraft.client.model.geom.builders.PartDefinition",
                "net.minecraft.world.entity.EquipmentSlot",
                "net.minecraft.world.item.ItemStack",
                ctx.pkg + "." + main(), ctx.pkg + ".ModItems");

        if (fabric()) {
            String layerReg = unobf() ? "ModelLayerRegistry" : "EntityModelLayerRegistry";
            s.imp("com.mojang.blaze3d.vertex.PoseStack",
                    "net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer",
                    "net.fabricmc.fabric.api.client.rendering.v1." + layerReg);
            if (unobf()) {
                s.imp("net.minecraft.client.renderer.SubmitNodeCollector",
                        "net.minecraft.client.renderer.entity.state.HumanoidRenderState",
                        "net.minecraft.client.renderer.rendertype.RenderTypes",
                        "net.minecraft.client.renderer.texture.OverlayTexture");
            } else if (ctx.registryKeys()) {
                s.imp("net.minecraft.client.renderer.MultiBufferSource",
                        "net.minecraft.client.renderer.entity.state.HumanoidRenderState");
            } else {
                s.imp("net.minecraft.client.renderer.MultiBufferSource",
                        "net.minecraft.world.entity.LivingEntity");
            }
        } else if (neo()) {
            s.imp("net.neoforged.bus.api.IEventBus",
                    "net.neoforged.neoforge.client.event.EntityRenderersEvent",
                    "net.neoforged.neoforge.client.extensions.common.IClientItemExtensions",
                    "net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent");
            if (ctx.registryKeys()) s.imp("net.minecraft.client.resources.model.EquipmentClientInfo");
            else s.imp("net.minecraft.world.entity.LivingEntity");
        } else {
            s.imp("net.minecraftforge.client.event.EntityRenderersEvent",
                    "net.minecraftforge.client.extensions.common.IClientItemExtensions");
            if (ctx.registryKeys()) s.imp("net.minecraft.client.renderer.entity.state.LivingEntityRenderState");
            else s.imp("net.minecraft.world.entity.LivingEntity");
        }

        s.l("/** Custom 3D armor layer definitions and loader client render hooks. */");
        s.l("public final class ModArmorModels {");
        s.l("private ModArmorModels() {").l("}");
        s.blank();

        for (Armor3dSpec sp : specs) {
            s.l("public static final ModelLayerLocation LAYER_" + sp.fieldPrefix + " = new ModelLayerLocation("
                    + rlFrom(main() + ".MODID", Src.str("armor_" + sp.item.name)) + ", \"main\");");
            String texExpr = sp.texKey != null
                    ? rlFrom(main() + ".MODID", Src.str("textures/entity/armor_3d/" + sp.texKey + ".png"))
                    : rlParse("\"minecraft:textures/block/iron_block.png\"");
            s.l("public static final " + rlType() + " TEX_" + sp.fieldPrefix + " = " + texExpr + ";");
            s.l("private static HumanoidModel<?> model_" + sp.fieldPrefix + ";");
        }
        s.blank();

        // Layer registration
        if (fabric()) {
            String layerReg = unobf() ? "ModelLayerRegistry" : "EntityModelLayerRegistry";
            s.l("public static void registerLayers() {");
            for (Armor3dSpec sp : specs) {
                s.l(layerReg + ".registerModelLayer(LAYER_" + sp.fieldPrefix + ", ModArmorModels::create_" + sp.fieldPrefix + ");");
            }
            s.l("}");
        } else {
            s.l("public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions ev) {");
            for (Armor3dSpec sp : specs) {
                s.l("ev.registerLayerDefinition(LAYER_" + sp.fieldPrefix + ", ModArmorModels::create_" + sp.fieldPrefix + ");");
            }
            s.l("}");
        }
        s.blank();

        // Lazy model baker & visibility helper
        for (Armor3dSpec sp : specs) {
            s.l("@SuppressWarnings(\"unchecked\")");
            s.l("public static <M extends HumanoidModel<?>> M model_" + sp.fieldPrefix + "() {");
            s.l("if (model_" + sp.fieldPrefix + " == null) {");
            s.l("    model_" + sp.fieldPrefix + " = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(LAYER_" + sp.fieldPrefix + "));");
            s.l("}");
            s.l("model_" + sp.fieldPrefix + ".setAllVisible(false);");
            switch (sp.item.kind) {
                case "helmet" -> {
                    s.l("model_" + sp.fieldPrefix + ".head.visible = true;");
                    s.l("model_" + sp.fieldPrefix + ".hat.visible = true;");
                }
                case "chestplate" -> {
                    s.l("model_" + sp.fieldPrefix + ".body.visible = true;");
                    s.l("model_" + sp.fieldPrefix + ".rightArm.visible = true;");
                    s.l("model_" + sp.fieldPrefix + ".leftArm.visible = true;");
                }
                case "leggings" -> {
                    s.l("model_" + sp.fieldPrefix + ".body.visible = true;");
                    s.l("model_" + sp.fieldPrefix + ".rightLeg.visible = true;");
                    s.l("model_" + sp.fieldPrefix + ".leftLeg.visible = true;");
                }
                default -> { // boots
                    s.l("model_" + sp.fieldPrefix + ".rightLeg.visible = true;");
                    s.l("model_" + sp.fieldPrefix + ".leftLeg.visible = true;");
                }
            }
            s.l("return (M) model_" + sp.fieldPrefix + ";");
            s.l("}");
            s.blank();
        }

        // Loader hooks
        if (fabric()) {
            s.l("public static void register() {");
            for (Armor3dSpec sp : specs) {
                String itemRef = "ModItems." + sp.fieldPrefix;
                String fp = sp.fieldPrefix;
                if (unobf()) {
                    s.l("ArmorRenderer.register((PoseStack matrices, SubmitNodeCollector collector, ItemStack stack, HumanoidRenderState entity, EquipmentSlot slot, int light, HumanoidModel<HumanoidRenderState> contextModel) -> {");
                    s.l("    HumanoidModel<HumanoidRenderState> m = model_" + fp + "();");
                    s.l("    ArmorRenderer.submitTransformCopyingModel(contextModel, entity, m, entity, false, collector, matrices, RenderTypes.armorCutoutNoCull(TEX_" + fp + "), light, OverlayTexture.NO_OVERLAY, 0, null);");
                    s.l("}, " + itemRef + ");");
                } else if (ctx.registryKeys()) {
                    s.l("ArmorRenderer.register((PoseStack matrices, MultiBufferSource vertexConsumers, ItemStack stack, HumanoidRenderState state, EquipmentSlot slot, int light, HumanoidModel<HumanoidRenderState> contextModel) -> {");
                    s.l("    HumanoidModel<HumanoidRenderState> m = model_" + fp + "();");
                    s.l("    m.setupAnim(state);");
                    s.l("    ArmorRenderer.renderPart(matrices, vertexConsumers, light, stack, m, TEX_" + fp + ");");
                    s.l("}, " + itemRef + ");");
                } else {
                    s.l("ArmorRenderer.register((PoseStack matrices, MultiBufferSource vertexConsumers, ItemStack stack, LivingEntity entity, EquipmentSlot slot, int light, HumanoidModel<LivingEntity> contextModel) -> {");
                    s.l("    HumanoidModel<LivingEntity> m = model_" + fp + "();");
                    s.l("    contextModel.copyPropertiesTo(m);");
                    s.l("    m = model_" + fp + "();");
                    s.l("    ArmorRenderer.renderPart(matrices, vertexConsumers, light, stack, m, TEX_" + fp + ");");
                    s.l("}, " + itemRef + ");");
                }
            }
            s.l("}");
            s.blank();
        } else if (neo()) {
            s.l("public static void register(IEventBus modBus) {");
            s.l("modBus.addListener((RegisterClientExtensionsEvent ev) -> {");
            for (Armor3dSpec sp : specs) {
                String fp = sp.fieldPrefix;
                String itemRef = "ModItems." + fp + ".get()";
                s.l("ev.registerItem(new IClientItemExtensions() {");
                if (ctx.registryKeys()) {
                    s.l("    @Override public Model getHumanoidArmorModel(ItemStack stack, EquipmentClientInfo.LayerType layerType, Model original) { return model_" + fp + "(); }");
                    if (sp.texKey != null) {
                        s.l("    @Override public " + rlType() + " getArmorTexture(ItemStack stack, EquipmentClientInfo.LayerType type, EquipmentClientInfo.Layer layer, " + rlType() + " _default) { return TEX_" + fp + "; }");
                    }
                } else {
                    s.l("    @Override public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {");
                    s.l("        HumanoidModel<?> m = model_" + fp + "();");
                    s.l("        ((HumanoidModel) original).copyPropertiesTo((HumanoidModel) m);");
                    s.l("        return model_" + fp + "();");
                    s.l("    }");
                }
                s.l("}, " + itemRef + ");");
            }
            s.l("});");
            s.l("}");
            s.blank();
        } else {
            // Forge IClientItemExtensions factory invoked by ArmorItem.initializeClient
            for (Armor3dSpec sp : specs) {
                String fp = sp.fieldPrefix;
                s.l("public static IClientItemExtensions ext_" + fp + "() {");
                s.l("return new IClientItemExtensions() {");
                if (ctx.registryKeys()) {
                    s.l("    @Override public HumanoidModel<?> getHumanoidArmorModel(LivingEntityRenderState state, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) { return model_" + fp + "(); }");
                } else {
                    s.l("    @Override public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {");
                    s.l("        HumanoidModel<?> m = model_" + fp + "();");
                    s.l("        ((HumanoidModel) original).copyPropertiesTo((HumanoidModel) m);");
                    s.l("        return model_" + fp + "();");
                    s.l("    }");
                }
                s.l("};");
                s.l("}");
                s.blank();
            }
        }

        // Layer builders per armor item
        for (Armor3dSpec sp : specs) {
            emitArmorLayerBuilder(s, sp);
            s.blank();
        }
        s.l("}");
        ctx.files.text(ctx.javaDir() + "client/ModArmorModels.java", s.toString());
    }

    private void emitArmorLayerBuilder(Src s, Armor3dSpec sp) {
        Model m = sp.model;
        int texW = armorTexDim(m, sp.texKey, true);
        int texH = armorTexDim(m, sp.texKey, false);

        s.l("private static LayerDefinition create_" + sp.fieldPrefix + "() {");
        s.l("MeshDefinition meshdefinition = new MeshDefinition();");
        s.l("PartDefinition partdefinition = meshdefinition.getRoot();");

        Map<String, List<Cube>> byLimb = new LinkedHashMap<>();
        for (String limb : List.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg")) {
            byLimb.put(limb, new ArrayList<>());
        }
        for (Cube c : m.cubes) {
            String target = mapArmorBone(c, sp.item.kind);
            byLimb.get(target).add(c);
        }

        int[] cubeSeq = {0};
        PartEmitter pe = new PartEmitter(s, m, texW, texH, cubeSeq);
        // HumanoidModel required parts & standard MS-space pivots matching MC PartPose offsets
        pe.emitPartVar("partdefinition", "part_head", "head", 0, 0, 0, 0, 0, 0, 8.0, 24.0, 8.0, byLimb.get("head"));
        if (ctx.registryKeys()) {
            s.l("part_head.addOrReplaceChild(\"hat\", CubeListBuilder.create(), PartPose.ZERO);");
        } else {
            s.l("partdefinition.addOrReplaceChild(\"hat\", CubeListBuilder.create(), PartPose.ZERO);");
        }
        pe.emitPart("partdefinition", "body", 0, 0, 0, 0, 0, 0, 8.0, 24.0, 8.0, byLimb.get("body"));
        pe.emitPart("partdefinition", "right_arm", -5.0F, 2.0F, 0.0F, 0, 0, 0, 3.0, 22.0, 8.0, byLimb.get("right_arm"));
        pe.emitPart("partdefinition", "left_arm", 5.0F, 2.0F, 0.0F, 0, 0, 0, 13.0, 22.0, 8.0, byLimb.get("left_arm"));
        pe.emitPart("partdefinition", "right_leg", -1.9F, 12.0F, 0.0F, 0, 0, 0, 6.1, 12.0, 8.0, byLimb.get("right_leg"));
        pe.emitPart("partdefinition", "left_leg", 1.9F, 12.0F, 0.0F, 0, 0, 0, 9.9, 12.0, 8.0, byLimb.get("left_leg"));

        s.l("return LayerDefinition.create(meshdefinition, " + texW + ", " + texH + ");");
        s.l("}");
    }

    private String mapArmorBone(Cube c, String slot) {
        String bn = c.bone == null ? "" : Ids.toId(c.bone);
        if (bn.contains("head") || bn.contains("hat") || bn.contains("helm")) return "head";
        if (bn.equals("right_arm") || bn.contains("arm_r")) return "right_arm";
        if (bn.equals("left_arm") || bn.contains("arm_l")) return "left_arm";
        if (bn.equals("right_leg") || bn.contains("leg_r") || bn.contains("boot_r")) return "right_leg";
        if (bn.equals("left_leg") || bn.contains("leg_l") || bn.contains("boot_l")) return "left_leg";
        if (bn.contains("body") || bn.contains("torso") || bn.contains("chest") || bn.contains("belt")) return "body";
        double cx = (c.from[0] + c.to[0]) * 0.5;
        return switch (slot) {
            case "helmet" -> "head";
            case "chestplate" -> cx < 3.5 ? "right_arm" : cx > 12.5 ? "left_arm" : "body";
            default -> cx < 8.0 ? "right_leg" : "left_leg";
        };
    }

    // ------------------------------------------------------------------ shared PartDefinition emitter

    private static final class PartEmitter {
        final Src s;
        final Model m;
        final int texW, texH;
        final int[] seq;

        PartEmitter(Src s, Model m, int texW, int texH, int[] seq) {
            this.s = s;
            this.m = m;
            this.texW = Math.max(1, texW);
            this.texH = Math.max(1, texH);
            this.seq = seq;
        }

        void emitPart(String parentVar, String partName, float px, float py, float pz, float rx, float ry, float rz,
                      double pivotX, double pivotY, double pivotZ, List<Cube> cubes) {
            emitPartInternal(parentVar, null, partName, px, py, pz, rx, ry, rz, pivotX, pivotY, pivotZ, cubes);
        }

        void emitPartVar(String parentVar, String varName, String partName, float px, float py, float pz, float rx, float ry, float rz,
                         double pivotX, double pivotY, double pivotZ, List<Cube> cubes) {
            emitPartInternal(parentVar, varName, partName, px, py, pz, rx, ry, rz, pivotX, pivotY, pivotZ, cubes);
        }

        private void emitPartInternal(String parentVar, String varName, String partName, float px, float py, float pz,
                                      float rx, float ry, float rz, double pivotX, double pivotY, double pivotZ, List<Cube> cubes) {
            List<Cube> plain = new ArrayList<>();
            List<Cube> rotated = new ArrayList<>();
            for (Cube c : cubes) {
                if (Math.abs(c.rotation) >= 1e-4) rotated.add(c);
                else plain.add(c);
            }
            StringBuilder cb = new StringBuilder("CubeListBuilder.create()");
            for (Cube c : plain) appendBox(cb, c, pivotX, pivotY, pivotZ);

            String pose;
            if (Math.abs(rx) > 1e-5 || Math.abs(ry) > 1e-5 || Math.abs(rz) > 1e-5) {
                pose = "PartPose.offsetAndRotation(" + Src.f(px) + ", " + Src.f(py) + ", " + Src.f(pz) + ", "
                        + Src.f(rx) + ", " + Src.f(ry) + ", " + Src.f(rz) + ")";
            } else if (Math.abs(px) > 1e-5 || Math.abs(py) > 1e-5 || Math.abs(pz) > 1e-5) {
                pose = "PartPose.offset(" + Src.f(px) + ", " + Src.f(py) + ", " + Src.f(pz) + ")";
            } else {
                pose = "PartPose.ZERO";
            }

            boolean needVar = varName != null || !rotated.isEmpty();
            String actualVar = varName != null ? varName : ("p_" + seq[0]++);
            String decl = needVar ? "PartDefinition " + actualVar + " = " : "";
            s.l(decl + parentVar + ".addOrReplaceChild(" + Src.str(partName) + ", " + cb + ", " + pose + ");");

            for (Cube c : rotated) {
                double rad = Math.toRadians(c.rotation);
                float crx = "x".equals(c.axis) ? (float) -rad : 0.0F;
                float cry = "y".equals(c.axis) ? (float) -rad : 0.0F;
                float crz = "z".equals(c.axis) ? (float) rad : 0.0F;
                float ox = (float) (c.origin[0] - pivotX);
                float oy = (float) (-(c.origin[1] - pivotY));
                float oz = (float) (c.origin[2] - pivotZ);
                StringBuilder rcb = new StringBuilder("CubeListBuilder.create()");
                appendBox(rcb, c, c.origin[0], c.origin[1], c.origin[2]);
                String cName = "rot_" + (seq[0]++);
                String rPose = "PartPose.offsetAndRotation(" + Src.f(ox) + ", " + Src.f(oy) + ", " + Src.f(oz) + ", "
                        + Src.f(crx) + ", " + Src.f(cry) + ", " + Src.f(crz) + ")";
                s.l(actualVar + ".addOrReplaceChild(" + Src.str(cName) + ", " + rcb + ", " + rPose + ");");
            }
        }

        private void appendBox(StringBuilder cb, Cube c, double px, double py, double pz) {
            double x1 = Math.min(c.from[0], c.to[0]), x2 = Math.max(c.from[0], c.to[0]);
            double y1 = Math.min(c.from[1], c.to[1]), y2 = Math.max(c.from[1], c.to[1]);
            double z1 = Math.min(c.from[2], c.to[2]), z2 = Math.max(c.from[2], c.to[2]);
            float ox = (float) (x1 - px);
            float oy = (float) (-(y2 - py));
            float oz = (float) (z1 - pz);
            float sx = (float) Math.max(0.01, x2 - x1);
            float sy = (float) Math.max(0.01, y2 - y1);
            float sz = (float) Math.max(0.01, z2 - z1);

            int[] uv = cubeUv(c, sx, sy, sz);
            cb.append(".texOffs(").append(uv[0]).append(", ").append(uv[1]).append(')');
            cb.append(".addBox(").append(Src.f(ox)).append(", ").append(Src.f(oy)).append(", ").append(Src.f(oz))
                    .append(", ").append(Src.f(sx)).append(", ").append(Src.f(sy)).append(", ").append(Src.f(sz));
            if (Math.abs(c.inflate) > 1e-5) {
                cb.append(", new CubeDeformation(").append(Src.f(c.inflate)).append(')');
            }
            cb.append(')');
        }

        private int[] cubeUv(Cube c, float sx, float sy, float sz) {
            Face north = c.faces != null ? c.faces.get("north") : null;
            if (north != null && north.uv != null && north.uv.length == 4) {
                int un = (int) Math.round(north.uv[0] * texW / 16.0 - sz);
                int vn = (int) Math.round(north.uv[1] * texH / 16.0 - sz);
                if (un >= 0 && vn >= 0 && un + 2 * (sx + sz) <= texW + 1 && vn + sz + sy <= texH + 1) {
                    return new int[]{un, vn};
                }
                int ux = Math.max(0, Math.min(texW - 1, (int) Math.round(north.uv[0] * texW / 16.0)));
                int vx = Math.max(0, Math.min(texH - 1, (int) Math.round(north.uv[1] * texH / 16.0)));
                return new int[]{ux, vx};
            }
            return new int[]{0, 0};
        }
    }

    // ------------------------------------------------------------------ bone & texture utilities

    private static List<Bone> orderedBones(Model m) {
        List<Bone> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Map<String, Bone> byId = new LinkedHashMap<>();
        for (Bone b : m.bones) {
            if (b.name != null && !b.name.isBlank()) byId.putIfAbsent(Ids.toId(b.name), b);
        }
        for (Bone b : m.bones) addBoneTree(b, byId, seen, out, 0);
        return out;
    }

    private static void addBoneTree(Bone b, Map<String, Bone> byId, Set<String> seen, List<Bone> out, int depth) {
        if (b == null || b.name == null || b.name.isBlank() || depth > 32) return;
        String id = Ids.toId(b.name);
        if (seen.contains(id)) return;
        String par = b.parent == null ? "" : Ids.toId(b.parent);
        if (!par.isEmpty() && !par.equals(id) && byId.containsKey(par) && !seen.contains(par)) {
            addBoneTree(byId.get(par), byId, seen, out, depth + 1);
        }
        if (seen.add(id)) out.add(b);
    }

    private static String boneField(String raw) {
        String id = Ids.toId(raw);
        return "bone_" + (id.isEmpty() ? "part" : id);
    }

    private int resolvedTexW(CustomEntity ce, Model m) {
        if (m.textureWidth > 16) return m.textureWidth;
        TextureAsset ta = ce.texture != null && !ce.texture.isBlank() ? ctx.p.texture(ce.texture) : firstModelTexture(ctx.p, m);
        return ta != null ? Math.max(16, ta.width) : Math.max(16, m.textureWidth);
    }

    private int resolvedTexH(CustomEntity ce, Model m) {
        if (m.textureHeight > 16) return m.textureHeight;
        TextureAsset ta = ce.texture != null && !ce.texture.isBlank() ? ctx.p.texture(ce.texture) : firstModelTexture(ctx.p, m);
        return ta != null ? Math.max(16, ta.frameHeight()) : Math.max(16, m.textureHeight);
    }

    private int armorTexDim(Model m, String texKey, boolean width) {
        int dim = width ? m.textureWidth : m.textureHeight;
        if (dim > 16) return dim;
        TextureAsset ta = firstModelTexture(ctx.p, m);
        if (ta != null) return Math.max(16, width ? ta.width : ta.frameHeight());
        return Math.max(16, dim);
    }
}
