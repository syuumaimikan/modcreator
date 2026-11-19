package modstudio.gen;

import com.google.gson.JsonObject;
import modstudio.core.*;
import modstudio.core.Elements.*;

import java.util.ArrayList;
import java.util.List;

/** Fabric and Quilt mods (Loom + official Mojang mappings; 26.1+ uses unobfuscated Loom). */
public final class FabricGen {
    private final GenContext ctx;
    private final boolean quilt;
    private final JavaCommon jc;
    private final HooksGen hooks;
    private final AdvancedModGen adv;
    private final String res = "src/main/resources/";

    public FabricGen(GenContext ctx) {
        this.ctx = ctx;
        this.quilt = ctx.target == Target.QUILT;
        this.jc = new JavaCommon(ctx, JavaCommon.Loader.FABRIC);
        this.hooks = new HooksGen(ctx, jc, JavaCommon.Loader.FABRIC);
        this.adv = new AdvancedModGen(ctx, jc, JavaCommon.Loader.FABRIC);
    }

    private boolean needsClient() {
        if (!ctx.unobf()) {
            for (Block b : ctx.all(Block.class)) if (b.cutout()) return true;
            if (adv.hasPortals()) return true;
        }
        return !ctx.all(Elements.Fluid.class).isEmpty() || hooks.hasClient() || adv.hasClientHooks()
                || !JavaSteps.hooks(ctx, "client_init").isEmpty();
    }

    public void generate() {
        buildFiles();
        metadata();
        mainClass();
        if (needsClient()) clientClass();
        if (!ctx.all(Elements.Fluid.class).isEmpty()) fluids();
        registries();
        events();
        hooks.classes(false);
        adv.generate(false);
        jc.procedures();
        JavaSteps.writeModHandlers(ctx);
        if (jc.hasArmor()) jc.armorMaterials();
        if (jc.hasToolMaterials()) jc.toolMaterials();
        if (jc.hasEffects()) jc.effects();
        if (jc.hasPotions()) jc.potions();
        if (jc.codePaintings()) jc.paintings();
        if (jc.hasCommands()) jc.commands();
        for (Item it : ctx.all(Item.class)) if (jc.needsClass(it)) jc.itemClass(it);
        for (Block bl : ctx.all(Block.class)) if (jc.needsBlockClass(bl)) jc.blockClass(bl);
        for (Elements.Fluid fl : ctx.all(Elements.Fluid.class)) jc.fluidBlockClass(fl);
        new DataGen(ctx, res, true, true).generate(true);
        new AssetGen(ctx, res).modAssets(false);
        JavaCommon.codeFiles(ctx);
    }

    private void buildFiles() {
        boolean unobf = ctx.v.unobfuscated();
        String depScope = unobf ? "implementation" : "modImplementation";
        String loaderVer = quilt ? ctx.version("quilt_loader") : ctx.version("loader");
        String apiVer = quilt ? ctx.version("quilted_fabric_api") : ctx.version("fabric_api");
        ctx.files.text("gradle.properties", """
                org.gradle.jvmargs=-Xmx2G
                org.gradle.parallel=true
                minecraft_version=%s
                loader_version=%s
                fabric_version=%s
                mod_version=%s
                maven_group=%s
                archives_base_name=%s
                """.formatted(ctx.v.id, loaderVer, apiVer, ctx.p.meta.version, ctx.pkg, ctx.ns));
        ctx.files.text("gradle/wrapper/gradle-wrapper.properties", Scaffold.wrapperProperties(ctx.version("gradle")));
        String quiltRepo = quilt ? """
                        maven {
                            name = 'Quilt'
                            url = 'https://maven.quiltmc.org/repository/release/'
                        }
                """ : "";
        ctx.files.text("settings.gradle", """
                pluginManagement {
                    repositories {
                %s        maven {
                            name = 'Fabric'
                            url = 'https://maven.fabricmc.net/'
                        }
                        mavenCentral()
                        gradlePluginPortal()
                    }
                }

                plugins {
                    id 'org.gradle.toolchains.foojay-resolver-convention' version '%s'
                }

                rootProject.name = '%s'
                """.formatted(quiltRepo, Versions.FOOJAY, ctx.ns));
        String pluginId = quilt ? "org.quiltmc.loom" : (unobf ? "net.fabricmc.fabric-loom" : "fabric-loom");
        String awFile = Scaffold.findCodeFile(ctx, ".accesswidener");
        String customRepos = Scaffold.mavenRepos(ctx, "    ");
        String customDeps = Scaffold.gradleDeps(ctx, "    ", true, unobf);
        String jvmPlugins = Scaffold.jvmPlugins(ctx);
        String jvmDeps = Scaffold.jvmDeps(ctx, true, unobf);
        String jvmFooter = Scaffold.jvmFooter(ctx);
        String customGradle = Scaffold.customGradle(ctx);
        String repos = (quilt || !customRepos.isEmpty()) ? """
                repositories {
                %s%s}
                """.formatted(quilt ? """
                    maven {
                        name = 'Quilt'
                        url = 'https://maven.quiltmc.org/repository/release/'
                    }
                    mavenCentral()
                """ : "", customRepos) : "";
        String loomBlock = awFile == null ? "" : """

                loom {
                    accessWidenerPath = file("%s")
                }
                """.formatted(awFile.replace("\\", "/").replace("\"", "\\\""));
        String mappingsLine = unobf ? "" : "    mappings loom.officialMojangMappings()\n";
        String loaderDep = quilt
                ? "    " + depScope + " \"org.quiltmc:quilt-loader:${project.loader_version}\"\n    " + depScope + " \"org.quiltmc.quilted-fabric-api:quilted-fabric-api:${project.fabric_version}\""
                : "    " + depScope + " \"net.fabricmc:fabric-loader:${project.loader_version}\"\n    " + depScope + " \"net.fabricmc.fabric-api:fabric-api:${project.fabric_version}\"";
        ctx.files.text("build.gradle", """
                plugins {
                    id '%s' version '%s'
                %s}

                version = project.mod_version
                group = project.maven_group

                base {
                    archivesName = project.archives_base_name
                }
                %s%s
                dependencies {
                    minecraft "com.mojang:minecraft:${project.minecraft_version}"
                %s%s
                %s%s}

                tasks.withType(JavaCompile).configureEach {
                    it.options.release = %d
                    it.options.encoding = 'UTF-8'
                }

                java {
                    toolchain.languageVersion = JavaLanguageVersion.of(%d)
                    sourceCompatibility = JavaVersion.VERSION_%d
                    targetCompatibility = JavaVersion.VERSION_%d
                }
                %s%s""".formatted(pluginId, ctx.version("loom"), jvmPlugins, repos, loomBlock, mappingsLine, loaderDep, jvmDeps, customDeps, ctx.v.java, ctx.v.java, ctx.v.java, ctx.v.java, jvmFooter, customGradle));
        ctx.files.text(".gitignore", Scaffold.GITIGNORE);
    }

    private void metadata() {
        Project.Meta m = ctx.p.meta;
        JsonObject entry = J.obj("main", J.arr(ctx.pkg + "." + ctx.mainClass));
        if (needsClient()) entry.add("client", J.arr(ctx.pkg + "." + ctx.mainClass + "Client"));
        List<String> authors = new ArrayList<>();
        for (String a : m.authors.split(",")) if (!a.isBlank()) authors.add(a.trim());
        String iconPath = null;
        if (ctx.p.texture(m.icon) != null) {
            iconPath = "assets/" + ctx.ns + "/icon.png";
            jc.writeIcon(res + iconPath);
        }
        String awFile = Scaffold.findCodeFile(ctx, ".accesswidener");
        String awRel = awFile == null ? null : (awFile.startsWith("src/main/resources/") ? awFile.substring("src/main/resources/".length()) : awFile);
        if (quilt) {
            JsonObject meta = J.obj("name", m.name, "description", m.description, "license", m.license.isBlank() ? "All Rights Reserved" : m.license);
            if (!authors.isEmpty()) {
                JsonObject contrib = new JsonObject();
                for (String a : authors) contrib.addProperty(a, "Author");
                meta.add("contributors", contrib);
            }
            if (!m.website.isBlank()) meta.add("contact", J.obj("homepage", m.website));
            if (iconPath != null) meta.addProperty("icon", iconPath);
            var qdeps = J.arr(
                    J.obj("id", "quilt_loader", "versions", ">=0.25.0"),
                    J.obj("id", "quilted_fabric_api", "versions", "*"),
                    J.obj("id", "minecraft", "versions", ctx.v.id),
                    J.obj("id", "java", "versions", ">=" + ctx.v.java));
            AdvancedModGen.quiltDepends(ctx, qdeps);
            JsonObject qloader = J.obj(
                    "group", ctx.pkg,
                    "id", ctx.ns,
                    "version", m.version,
                    "metadata", meta,
                    "intermediate_mappings", "net.fabricmc:intermediary",
                    "entrypoints", entry,
                    "depends", qdeps);
            JsonObject qroot = J.obj("schema_version", 1, "quilt_loader", qloader);
            if (awRel != null) qroot.addProperty("access_widener", awRel);
            if (adv.hasMixins()) qroot.addProperty("mixin", ctx.ns + ".mixins.json");
            ctx.files.json(res + "quilt.mod.json", qroot);
            return;
        }
        JsonObject depends = J.obj("fabricloader", ">=0.15.0", "minecraft", ctx.v.id, "java", ">=" + ctx.v.java, "fabric-api", "*");
        JsonObject suggests = new JsonObject();
        AdvancedModGen.fabricDepends(ctx, depends, suggests);
        JsonObject o = J.obj(
                "schemaVersion", 1,
                "id", ctx.ns,
                "version", m.version,
                "name", m.name,
                "description", m.description,
                "authors", J.list(authors),
                "license", m.license.isBlank() ? "All Rights Reserved" : m.license,
                "environment", "*",
                "entrypoints", entry,
                "depends", depends);
        if (!suggests.isEmpty()) o.add("suggests", suggests);
        if (awRel != null) o.addProperty("accessWidener", awRel);
        if (adv.hasMixins()) o.add("mixins", J.arr(ctx.ns + ".mixins.json"));
        if (!m.website.isBlank()) o.add("contact", J.obj("homepage", m.website));
        if (iconPath != null) o.addProperty("icon", iconPath);
        ctx.files.json(res + "fabric.mod.json", o);
    }

    private void mainClass() {
        String main = ctx.mainClass;
        Src s = new Src(ctx.pkg);
        s.imp("net.fabricmc.api.ModInitializer", ctx.rlImport());
        s.l("public class " + main + " implements ModInitializer {");
        s.l("public static final String MODID = " + Src.str(ctx.ns) + ";");
        s.blank();
        s.l("@Override");
        s.l("public void onInitialize() {");
        if (!ctx.all(Elements.ArmorMaterial.class).isEmpty() && ctx.components() && !ctx.registryKeys()) s.l("ModArmorMaterials.init();");
        if (jc.hasEffects()) s.l("ModEffects.init();");
        if (jc.hasPotions()) s.l("ModPotions.init();");
        if (jc.codePaintings()) s.l("ModPaintings.init();");
        if (!ctx.all(Elements.Fluid.class).isEmpty()) s.l("ModFluids.init();");
        if (adv.hasConfigs()) s.l("ModConfigs.init();");
        if (adv.hasEntities()) {
            s.l("ModEntities.init();");
            for (ModElements.CustomEntity ce : ctx.all(ModElements.CustomEntity.class)) {
                if (!ce.isLivingMob()) continue;
                s.imp("net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry",
                        ctx.pkg + ".entity." + Ids.toPascal(ce.name) + "Entity");
                s.l("FabricDefaultAttributeRegistry.register(ModEntities." + Ids.toConst(ce.name) + ", " + Ids.toPascal(ce.name) + "Entity.createAttributes());");
            }
        }
        s.l("ModBlocks.init();");
        s.l("ModItems.init();");
        if (adv.hasContainers()) {
            s.l("ModBlockEntities.init();");
            s.l("ModContainerMenus.init();");
        }
        if (adv.hasProfessions()) s.l("ModProfessions.init();");
        if (adv.hasRecipeTypes()) s.l("ModRecipeTypes.init();");
        if (adv.hasNetwork()) s.l("ModNetwork.register();");
        s.l("ModTabs.init();");
        s.l("ModSounds.init();");
        s.l("ModEvents.init();");
        for (var h : JavaSteps.hooks(ctx, "common_init")) s.l(h.fqcn() + "." + h.method() + "();");
        s.l("}");
        s.blank();
        s.l("public static " + ctx.rlType() + " id(String path) {");
        s.l("return " + ctx.rlFrom("MODID", "path") + ";");
        s.l("}");
        s.blank();
        JavaCommon.registryKeyHelpers(ctx, s);
        s.l("}");
        ctx.files.text(ctx.javaDir() + main + ".java", s.toString());
    }

    private void clientClass() {
        Src s = new Src(ctx.pkg);
        boolean unobf = ctx.unobf();
        boolean sectionLayers = jc.toolProps();
        s.imp("net.fabricmc.api.ClientModInitializer");
        if (!unobf) {
            // Fabric API for 1.21.6-1.21.11 moved the render layer map to the rendering module and uses ChunkSectionLayer.
            // Minecraft 26.1+ infers block cutout/translucent layers automatically from sprite pixels.
            s.imp(sectionLayers
                            ? "net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap"
                            : "net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap",
                    sectionLayers ? "net.minecraft.client.renderer.chunk.ChunkSectionLayer" : "net.minecraft.client.renderer.RenderType");
        }
        s.l("public class " + ctx.mainClass + "Client implements ClientModInitializer {");
        s.l("@Override");
        s.l("public void onInitializeClient() {");
        if (!unobf) {
            for (Block b : ctx.all(Block.class)) {
                if (!b.cutout()) continue;
                if (sectionLayers) s.l("BlockRenderLayerMap.putBlock(" + jc.blockRef(b.name) + ", ChunkSectionLayer.CUTOUT);");
                else s.l("BlockRenderLayerMap.INSTANCE.putBlock(" + jc.blockRef(b.name) + ", RenderType.cutout());");
            }
        }
        for (Elements.Fluid fl : ctx.all(Elements.Fluid.class)) {
            if (!unobf) {
                s.imp(ctx.rlImport(),
                        "net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry",
                        "net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler");
                String c = Ids.toConst(fl.name);
                String stillTex = ctx.rlParseExpr(Src.str(jc.fluidTextureId(fl, false)));
                String flowTex = ctx.rlParseExpr(Src.str(jc.fluidTextureId(fl, true)));
                int rgb = JavaCommon.rgbInt(fl.tintColor, 0x3F76E4);
                s.l("FluidRenderHandlerRegistry.INSTANCE.register(ModFluids.STILL_" + c + ", ModFluids.FLOWING_" + c + ",");
                s.l("        new SimpleFluidRenderHandler(" + stillTex + ", " + flowTex + ", 0xFF" + String.format("%06X", rgb) + "));");
                if (sectionLayers) s.l("BlockRenderLayerMap.putFluids(ChunkSectionLayer.TRANSLUCENT, ModFluids.STILL_" + c + ", ModFluids.FLOWING_" + c + ");");
                else s.l("BlockRenderLayerMap.INSTANCE.putFluids(RenderType.translucent(), ModFluids.STILL_" + c + ", ModFluids.FLOWING_" + c + ");");
            }
        }
        if (!unobf) {
            for (ModElements.Portal pt : ctx.all(ModElements.Portal.class)) {
                String ref = jc.blockRef(pt.name + "_portal");
                if (sectionLayers) s.l("BlockRenderLayerMap.putBlock(" + ref + ", ChunkSectionLayer.TRANSLUCENT);");
                else s.l("BlockRenderLayerMap.INSTANCE.putBlock(" + ref + ", RenderType.translucent());");
            }
        }
        if (hooks.hasClient()) s.l("ModClient.init();");
        if (adv.hasClientHooks()) s.l("ModClientRegistrations.init();");
        for (var h : JavaSteps.hooks(ctx, "client_init")) s.l(h.fqcn() + "." + h.method() + "();");
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + ctx.mainClass + "Client.java", s.toString());
    }

    private List<String> tabMembers(String key) {
        return JavaCommon.tabMembers(ctx, adv, key);
    }

    private void fluids() {
        String main = ctx.mainClass;
        Src reg = new Src(ctx.pkg);
        reg.imp("net.minecraft.core.Registry", "net.minecraft.core.registries.BuiltInRegistries",
                "net.minecraft.world.level.material.FlowingFluid");
        reg.l("public final class ModFluids {");
        for (Elements.Fluid fl : ctx.all(Elements.Fluid.class)) {
            String cls = Ids.toPascal(fl.name) + "Fluid";
            String c = Ids.toConst(fl.name);
            reg.imp(ctx.pkg + ".fluid." + cls);
            reg.l("public static final FlowingFluid STILL_" + c + " = register(" + Src.str(fl.name) + ", new " + cls + ".Source());");
            reg.l("public static final FlowingFluid FLOWING_" + c + " = register(" + Src.str("flowing_" + fl.name) + ", new " + cls + ".Flowing());");

            Src f = new Src(ctx.pkg + ".fluid");
            f.imp("java.util.Optional", "net.minecraft.core.BlockPos", "net.minecraft.core.Direction",
                    "net.minecraft.sounds.SoundEvent", "net.minecraft.sounds.SoundEvents",
                    "net.minecraft.world.item.Item", "net.minecraft.world.level.BlockGetter",
                    "net.minecraft.world.level.LevelAccessor", "net.minecraft.world.level.LevelReader",
                    "net.minecraft.world.level.block.Block", "net.minecraft.world.level.block.LiquidBlock",
                    "net.minecraft.world.level.block.entity.BlockEntity", "net.minecraft.world.level.block.state.BlockState",
                    "net.minecraft.world.level.block.state.StateDefinition", "net.minecraft.world.level.material.FlowingFluid",
                    "net.minecraft.world.level.material.Fluid", "net.minecraft.world.level.material.FluidState",
                    ctx.pkg + ".ModBlocks", ctx.pkg + ".ModFluids", ctx.pkg + ".ModItems");
            if (!fl.generateBucket) f.imp("net.minecraft.world.item.Items");
            f.imp(ctx.registryKeys() ? "net.minecraft.server.level.ServerLevel" : "net.minecraft.world.level.Level");
            f.l("public abstract class " + cls + " extends FlowingFluid {");
            f.l("@Override public Fluid getSource() { return ModFluids.STILL_" + c + "; }");
            f.l("@Override public Fluid getFlowing() { return ModFluids.FLOWING_" + c + "; }");
            f.l("@Override public Item getBucket() { return " + (fl.generateBucket ? "ModItems." + c + "_BUCKET" : "Items.AIR") + "; }");
            f.l("@Override public Optional<SoundEvent> getPickupSound() { return Optional.of(SoundEvents." + (fl.isLavaLike ? "BUCKET_FILL_LAVA" : "BUCKET_FILL") + "); }");
            f.l("@Override protected boolean canConvertToSource(" + (ctx.registryKeys() ? "ServerLevel" : "Level") + " level) { return " + fl.canMultiply + "; }");
            f.l("@Override protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {");
            f.l("BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;");
            f.l("Block.dropResources(state, level, pos, be);").l("}");
            f.l("@Override protected int getSlopeFindDistance(LevelReader level) { return " + Math.max(1, fl.slopeFindDistance) + "; }");
            f.l("@Override protected int getDropOff(LevelReader level) { return " + Math.max(1, fl.levelDecrease) + "; }");
            f.l("@Override public int getTickDelay(LevelReader level) { return " + Math.max(1, fl.tickRate) + "; }");
            f.l("@Override protected float getExplosionResistance() { return 100.0F; }");
            f.l("@Override protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid, Direction direction) { return false; }");
            f.l("@Override public boolean isSame(Fluid fluid) { return fluid == getSource() || fluid == getFlowing(); }");
            f.l("@Override protected BlockState createLegacyBlock(FluidState state) { return ModBlocks." + c + ".defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state)); }");
            f.blank();
            f.l("public static final class Source extends " + cls + " {");
            f.l("@Override public int getAmount(FluidState state) { return 8; }");
            f.l("@Override public boolean isSource(FluidState state) { return true; }").l("}");
            f.blank();
            f.l("public static final class Flowing extends " + cls + " {");
            f.l("@Override protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) { super.createFluidStateDefinition(builder); builder.add(LEVEL); }");
            f.l("@Override public int getAmount(FluidState state) { return state.getValue(LEVEL); }");
            f.l("@Override public boolean isSource(FluidState state) { return false; }").l("}");
            f.l("}");
            ctx.files.text(ctx.javaDir() + "fluid/" + cls + ".java", f.toString());
        }
        reg.blank().l("private ModFluids() {").l("}").blank();
        reg.l("private static FlowingFluid register(String name, FlowingFluid fluid) {");
        reg.l("return Registry.register(BuiltInRegistries.FLUID, " + main + ".id(name), fluid);").l("}").blank();
        reg.l("public static void init() {").l("}");
        reg.l("}");
        ctx.files.text(ctx.javaDir() + "ModFluids.java", reg.toString());
    }

    private void registries() {
        String main = ctx.mainClass;
        Src b = new Src(ctx.pkg);
        b.imp("net.minecraft.core.Registry", "net.minecraft.core.registries.BuiltInRegistries");
        jc.importsForBlocks(b);
        b.l("public final class ModBlocks {");
        for (Block bl : ctx.all(Block.class)) {
            b.l("public static final Block " + Ids.toConst(bl.name) + " = register(" + Src.str(bl.name) + ", " + jc.blockConstruct(bl) + ");");
        }
        for (Elements.Fluid fl : ctx.all(Elements.Fluid.class)) {
            String blkCls = JavaCommon.hasFluidBlockClass(fl) ? JavaCommon.fluidBlockClassName(fl) : "LiquidBlock";
            b.l("public static final Block " + Ids.toConst(fl.name) + " = register(" + Src.str(fl.name) + ", new " + blkCls
                    + "(ModFluids.STILL_" + Ids.toConst(fl.name) + ", " + jc.fluidBlockProps(fl) + "));");
        }
        for (ModElements.Container ct : ctx.all(ModElements.Container.class)) {
            if (!adv.standaloneContainerBlock(ct)) continue;
            String bn = ct.block.isBlank() ? ct.name : Ids.path(Ids.full(ct.block));
            String blkCls = Ids.toPascal(ct.name) + "ContainerBlock";
            b.imp(ctx.pkg + ".block." + blkCls);
            String props = "BlockBehaviour.Properties.of()" + (ctx.registryKeys() ? ".setId(" + jc.blockKey(bn) + ")" : "") + ".strength(2.5F)";
            b.l("public static final Block " + Ids.toConst(bn) + " = register(" + Src.str(bn) + ", new " + blkCls + "(" + props + "));");
        }
        for (ModElements.Portal pt : ctx.all(ModElements.Portal.class)) {
            String bn = pt.name + "_portal";
            String blkCls = Ids.toPascal(pt.name) + "PortalBlock";
            b.imp(ctx.pkg + ".block." + blkCls);
            String props = "BlockBehaviour.Properties.of()" + (ctx.registryKeys() ? ".setId(" + jc.blockKey(bn) + ")" : "") + "." + ctx.noCollision() + ".strength(-1.0F).lightLevel(s -> 11).noLootTable()";
            b.l("public static final Block " + Ids.toConst(bn) + " = register(" + Src.str(bn) + ", new " + blkCls + "(" + props + "));");
        }
        b.blank().l("private ModBlocks() {").l("}").blank();
        b.l("private static Block register(String name, Block block) {");
        b.l("return Registry.register(BuiltInRegistries.BLOCK, " + main + ".id(name), block);");
        b.l("}").blank();
        b.l("public static void init() {").l("}");
        b.l("}");
        ctx.files.text(ctx.javaDir() + "ModBlocks.java", b.toString());

        Src i = new Src(ctx.pkg);
        i.imp("net.minecraft.core.Registry", "net.minecraft.core.registries.BuiltInRegistries");
        jc.importsForItems(i);
        i.l("public final class ModItems {");
        for (Item it : ctx.all(Item.class)) {
            i.l("public static final Item " + Ids.toConst(it.name) + " = register(" + Src.str(it.name) + ", " + jc.itemConstruct(it) + ");");
        }
        for (Block bl : ctx.all(Block.class)) {
            i.l("public static final Item " + Ids.toConst(bl.name) + " = register(" + Src.str(bl.name) + ", new BlockItem(" + jc.blockRef(bl.name) + ", " + jc.blockItemProps(bl.name) + "));");
        }
        for (Elements.Fluid fl : ctx.all(Elements.Fluid.class)) {
            if (!fl.generateBucket) continue;
            i.imp("net.minecraft.world.item.BucketItem", "net.minecraft.world.item.Items");
            String bname = fl.name + "_bucket";
            String bprops = "new Item.Properties()" + (ctx.registryKeys() ? ".setId(" + jc.itemKey(bname) + ")" : "") + ".craftRemainder(Items.BUCKET).stacksTo(1)";
            i.l("public static final Item " + Ids.toConst(bname) + " = register(" + Src.str(bname) + ", new BucketItem(ModFluids.STILL_"
                    + Ids.toConst(fl.name) + ", " + bprops + "));");
        }
        for (ModElements.Container ct : ctx.all(ModElements.Container.class)) {
            if (!adv.standaloneContainerBlock(ct)) continue;
            String bn = ct.block.isBlank() ? ct.name : Ids.path(Ids.full(ct.block));
            i.l("public static final Item " + Ids.toConst(bn) + " = register(" + Src.str(bn) + ", new BlockItem(" + jc.blockRef(bn) + ", " + jc.blockItemProps(bn) + "));");
        }
        for (ModElements.CustomEntity ce : ctx.all(ModElements.CustomEntity.class)) {
            if (!ce.spawnEgg || !ce.isLivingMob()) continue;
            String egg = ce.name + "_spawn_egg";
            i.l("public static final Item " + Ids.toConst(egg) + " = register(" + Src.str(egg) + ", " + jc.spawnEggExpr(i, ce) + ");");
        }
        for (ModElements.Portal pt : ctx.all(ModElements.Portal.class)) {
            if (!pt.generateIgniter) continue;
            String iname = pt.name + "_igniter", cls = Ids.toPascal(pt.name) + "IgniterItem";
            i.imp(ctx.pkg + ".item." + cls);
            String props = "new Item.Properties()" + (ctx.registryKeys() ? ".setId(" + jc.itemKey(iname) + ")" : "") + ".stacksTo(1)";
            i.l("public static final Item " + Ids.toConst(iname) + " = register(" + Src.str(iname) + ", new " + cls + "(" + props + "));");
        }
        for (ModElements.ArmorTrim at : ctx.all(ModElements.ArmorTrim.class)) {
            if (!at.generateTemplate) continue;
            String tname = at.name + "_armor_trim_smithing_template";
            String props = "new Item.Properties()" + (ctx.registryKeys() ? ".setId(" + jc.itemKey(tname) + ")" : "");
            i.l("public static final Item " + Ids.toConst(tname) + " = register(" + Src.str(tname) + ", new Item(" + props + "));");
        }
        i.blank().l("private ModItems() {").l("}").blank();
        i.l("private static Item register(String name, Item item) {");
        i.l("return Registry.register(BuiltInRegistries.ITEM, " + main + ".id(name), item);");
        i.l("}").blank();
        i.l("public static void init() {");
        boolean anyTab = false;
        boolean unobf = ctx.unobf();
        for (var tab : Vanilla.CREATIVE_TABS.entrySet()) {
            List<String> members = tabMembers(tab.getKey());
            if (members.isEmpty()) continue;
            anyTab = true;
            if (unobf) {
                i.l("CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs." + tab.getValue() + ").register(entries -> {");
            } else {
                i.l("ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs." + tab.getValue() + ").register(entries -> {");
            }
            for (String n : members) i.l("entries.accept(" + jc.itemRef(n) + ");");
            i.l("});");
        }
        if (anyTab) i.imp(unobf ? "net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents" : "net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents",
                "net.minecraft.world.item.CreativeModeTabs");
        List<Item> fuels = ctx.all(Item.class).stream().filter(it -> it.burnTime > 0).toList();
        if (!fuels.isEmpty() && unobf) {
            i.imp("net.fabricmc.fabric.api.registry.FuelValueEvents");
            i.l("FuelValueEvents.BUILD.register((builder, context) -> {");
            for (Item it : fuels) i.l("builder.add(" + jc.itemRef(it.name) + ", " + it.burnTime + ");");
            i.l("});");
        } else if (!fuels.isEmpty() && ctx.registryKeys()) {
            i.imp("net.fabricmc.fabric.api.registry.FuelRegistryEvents");
            i.l("FuelRegistryEvents.BUILD.register((builder, context) -> {");
            for (Item it : fuels) i.l("builder.add(" + jc.itemRef(it.name) + ", " + it.burnTime + ");");
            i.l("});");
        } else if (!fuels.isEmpty()) {
            i.imp("net.fabricmc.fabric.api.registry.FuelRegistry");
            for (Item it : fuels) i.l("FuelRegistry.INSTANCE.add(" + jc.itemRef(it.name) + ", " + it.burnTime + ");");
        }
        i.l("}");
        i.l("}");
        ctx.files.text(ctx.javaDir() + "ModItems.java", i.toString());

        Src t = new Src(ctx.pkg);
        t.imp("net.minecraft.core.Registry", "net.minecraft.core.registries.BuiltInRegistries");
        t.l("public final class ModTabs {");
        for (CreativeTab tab : ctx.all(CreativeTab.class)) {
            t.imp(unobf ? "net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab" : "net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup",
                    "net.minecraft.network.chat.Component",
                    "net.minecraft.world.item.CreativeModeTab", "net.minecraft.world.item.ItemStack");
            t.l("public static final CreativeModeTab " + Ids.toConst(tab.name) + " = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, "
                    + main + ".id(" + Src.str(tab.name) + "), " + (unobf ? "FabricCreativeModeTab" : "FabricItemGroup") + ".builder()");
            t.l("        .title(Component.translatable(" + Src.str("itemGroup." + ctx.ns + "." + tab.name) + "))");
            t.l("        .icon(() -> new ItemStack(" + jc.anyItemRef(tab.icon) + "))");
            t.l("        .displayItems((parameters, output) -> {");
            for (String n : tabMembers("tab:" + tab.name)) t.l("            output.accept(" + jc.itemRef(n) + ");");
            t.l("        })");
            t.l("        .build());");
        }
        t.blank().l("private ModTabs() {").l("}").blank();
        t.l("public static void init() {").l("}");
        t.l("}");
        ctx.files.text(ctx.javaDir() + "ModTabs.java", t.toString());

        Src so = new Src(ctx.pkg);
        so.imp("net.minecraft.core.Registry", "net.minecraft.core.registries.BuiltInRegistries",
                ctx.rlImport(), "net.minecraft.sounds.SoundEvent");
        so.l("public final class ModSounds {");
        for (Sound s : ctx.all(Sound.class)) {
            so.l("public static final SoundEvent " + Ids.toConst(s.name) + " = register(" + Src.str(s.name) + ");");
        }
        so.blank().l("private ModSounds() {").l("}").blank();
        so.l("private static SoundEvent register(String name) {");
        so.l(ctx.rlType() + " id = " + main + ".id(name);");
        so.l("return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));");
        so.l("}").blank();
        so.l("public static void init() {").l("}");
        so.l("}");
        ctx.files.text(ctx.javaDir() + "ModSounds.java", so.toString());
    }

    private void events() {
        Src s = new Src(ctx.pkg);
        s.l("public final class ModEvents {");
        s.l("private ModEvents() {").l("}");
        s.blank();
        s.l("public static void init() {");
        if (jc.hasCommands()) {
            s.imp("net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback");
            s.l("CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ModCommands.register(dispatcher));");
        }
        List<Procedure> rc = jc.eventRightClickProcs();
        if (!rc.isEmpty()) {
            s.imp("net.fabricmc.fabric.api.event.player.UseItemCallback", "net.minecraft.world.item.ItemStack",
                    ctx.registryKeys() ? "net.minecraft.world.InteractionResult" : "net.minecraft.world.InteractionResultHolder");
            s.l("UseItemCallback.EVENT.register((player, level, hand) -> {");
            s.l("ItemStack stack = player.getItemInHand(hand);");
            s.l("if (!level.isClientSide()) {");
            for (Procedure p : rc) s.l("if (" + jc.stackMatches(p.triggerValue) + ") ModProcedures.run(player, " + Src.str("procedures/" + p.name) + ");");
            s.l("}");
            s.l(ctx.registryKeys() ? "return InteractionResult.PASS;" : "return InteractionResultHolder.pass(stack);");
            s.l("});");
        }
        for (Block b : ctx.all(Block.class)) {
            if (b.flammability > 0 || b.fireSpread > 0) {
                s.imp("net.fabricmc.fabric.api.registry.FlammableBlockRegistry");
                s.l("FlammableBlockRegistry.getDefaultInstance().add(ModBlocks." + Ids.toConst(b.name) + ", "
                        + Math.max(0, b.fireSpread) + ", " + Math.max(0, b.flammability) + ");");
            }
        }
        List<ModElements.Portal> customIgniters = ctx.all(ModElements.Portal.class).stream()
                .filter(pt -> !pt.generateIgniter && !pt.igniterItem.isBlank()).toList();
        if (!customIgniters.isEmpty()) {
            s.imp("net.fabricmc.fabric.api.event.player.UseBlockCallback", "net.minecraft.world.InteractionResult",
                    "net.minecraft.world.item.ItemStack", ctx.pkg + ".ModItems", ctx.pkg + ".ModProcedures");
            s.l("UseBlockCallback.EVENT.register((player, level, hand, hit) -> {");
            s.l("ItemStack stack = player.getItemInHand(hand);");
            for (ModElements.Portal pt : customIgniters) {
                String blkCls = Ids.toPascal(pt.name) + "PortalBlock";
                s.imp(ctx.pkg + ".block." + blkCls);
                s.l("if (" + jc.stackMatches(pt.igniterItem) + " && " + blkCls + ".tryIgnite(level, hit.getBlockPos(), hit.getDirection())) return InteractionResult.SUCCESS;");
            }
            s.l("return InteractionResult.PASS;");
            s.l("});");
        }
        for (Block b : ctx.all(Block.class)) {
            if (!b.oreGen) continue;
            s.imp("net.fabricmc.fabric.api.biome.v1.BiomeModifications", "net.fabricmc.fabric.api.biome.v1.BiomeSelectors",
                    "net.minecraft.core.registries.Registries", "net.minecraft.resources.ResourceKey",
                    "net.minecraft.world.level.levelgen.GenerationStep");
            if (b.oreBiomes != null && !b.oreBiomes.isBlank()) {
                for (String sel : fabricBiomeSelectors(s, b.oreBiomes, "foundInOverworld()")) {
                    s.l("BiomeModifications.addFeature(" + sel + ", GenerationStep.Decoration.UNDERGROUND_ORES,");
                    s.l("        ResourceKey.create(Registries.PLACED_FEATURE, " + ctx.mainClass + ".id(" + Src.str(DataGen.oreFeature(b)) + ")));");
                }
            } else {
                String sel = switch (b.oreDimension) {
                    case "nether" -> "foundInTheNether()";
                    case "end" -> "foundInTheEnd()";
                    default -> "foundInOverworld()";
                };
                s.l("BiomeModifications.addFeature(BiomeSelectors." + sel + ", GenerationStep.Decoration.UNDERGROUND_ORES,");
                s.l("        ResourceKey.create(Registries.PLACED_FEATURE, " + ctx.mainClass + ".id(" + Src.str(DataGen.oreFeature(b)) + ")));");
            }
        }
        for (ModElements.WorldFeature wf : ctx.all(ModElements.WorldFeature.class)) {
            s.imp("net.fabricmc.fabric.api.biome.v1.BiomeModifications", "net.fabricmc.fabric.api.biome.v1.BiomeSelectors",
                    "net.minecraft.core.registries.Registries", "net.minecraft.resources.ResourceKey",
                    "net.minecraft.world.level.levelgen.GenerationStep");
            String step = "GenerationStep.Decoration." + wf.step.toUpperCase();
            for (String sel : fabricBiomeSelectors(s, wf.biomes, "foundInOverworld()")) {
                s.l("BiomeModifications.addFeature(" + sel + ", " + step + ", ResourceKey.create(Registries.PLACED_FEATURE, " + ctx.mainClass + ".id(" + Src.str(wf.name) + ")));");
            }
        }
        for (ModElements.CustomEntity ce : ctx.all(ModElements.CustomEntity.class)) {
            if (!ce.naturalSpawn || ce.isProjectile()) continue;
            s.imp("net.fabricmc.fabric.api.biome.v1.BiomeModifications", "net.fabricmc.fabric.api.biome.v1.BiomeSelectors",
                    "net.minecraft.world.entity.MobCategory");
            for (String sel : fabricBiomeSelectors(s, ce.spawnBiomes, "foundInOverworld()")) {
                s.l("BiomeModifications.addSpawn(" + sel + ", MobCategory." + ce.category.toUpperCase() + ", ModEntities." + Ids.toConst(ce.name)
                        + ", " + Math.max(1, ce.spawnWeight) + ", " + Math.max(1, ce.spawnMin) + ", " + Math.max(ce.spawnMin, ce.spawnMax) + ");");
            }
        }
        hooks.fabricInit(s);
        for (var h : JavaSteps.hooks(ctx, "event_bus")) s.l(h.fqcn() + "." + h.method() + "();");
        List<JavaSteps.HookCall> tickHooks = JavaSteps.hooks(ctx, "server_tick");
        if (!tickHooks.isEmpty()) {
            s.imp("net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents");
            s.l("ServerTickEvents.END_SERVER_TICK.register(server -> {");
            for (var h : tickHooks) s.l(h.fqcn() + "." + h.method() + "(server);");
            s.l("});");
        }
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModEvents.java", s.toString());
    }

    private List<String> fabricBiomeSelectors(Src s, String csv, String fallbackMethod) {
        if (csv == null || csv.isBlank()) return List.of("BiomeSelectors." + fallbackMethod);
        s.imp("net.minecraft.core.registries.Registries", "net.minecraft.resources.ResourceKey");
        List<String> out = new ArrayList<>();
        for (String raw : csv.split(",")) {
            String bm = Ids.full(raw.trim());
            if (bm.isBlank()) continue;
            if (Ids.isTag(bm)) {
                s.imp("net.minecraft.tags.TagKey");
                out.add("BiomeSelectors.tag(TagKey.create(Registries.BIOME, " + ctx.rlParseExpr(Src.str(bm.substring(1))) + "))");
            } else {
                out.add("BiomeSelectors.includeByKey(ResourceKey.create(Registries.BIOME, " + ctx.rlParseExpr(Src.str(bm)) + "))");
            }
        }
        return out.isEmpty() ? List.of("BiomeSelectors." + fallbackMethod) : out;
    }
}
