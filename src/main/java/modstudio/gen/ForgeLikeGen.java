package modstudio.gen;

import com.google.gson.JsonObject;
import modstudio.core.*;
import modstudio.core.Elements.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Forge (FG6) and NeoForge (ModDevGradle) projects. */
public final class ForgeLikeGen {
    private final GenContext ctx;
    private final boolean neo;
    private final JavaCommon jc;
    private final HooksGen hooks;
    private final AdvancedModGen adv;
    private final String res = "src/main/resources/";

    public ForgeLikeGen(GenContext ctx, boolean neo) {
        this.ctx = ctx;
        this.neo = neo;
        this.jc = new JavaCommon(ctx, neo ? JavaCommon.Loader.NEOFORGE : JavaCommon.Loader.FORGE);
        this.hooks = new HooksGen(ctx, jc, neo ? JavaCommon.Loader.NEOFORGE : JavaCommon.Loader.FORGE);
        this.adv = new AdvancedModGen(ctx, jc, neo ? JavaCommon.Loader.NEOFORGE : JavaCommon.Loader.FORGE);
    }

    private String evPkg() {
        return neo ? "net.neoforged.neoforge.event" : "net.minecraftforge.event";
    }

    public void generate() {
        buildFiles();
        metadata();
        mainClass();
        if (!ctx.all(Elements.Fluid.class).isEmpty()) fluids();
        registries();
        events();
        hooks.classes(eventBus7());
        adv.generate(eventBus7());
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
        new AssetGen(ctx, res).modAssets(true);
        worldgen();
        fuels();
        JavaCommon.codeFiles(ctx);
    }

    private void buildFiles() {
        String gradle = ctx.version("gradle");
        Map<String, String> props = new LinkedHashMap<>();
        props.put("org.gradle.jvmargs", "-Xmx3G");
        props.put("org.gradle.daemon", "false");
        props.put("org.gradle.daemon.idletimeout", "900000");
        props.put("minecraft_version", ctx.v.id);
        if (neo) props.put("neo_version", ctx.version("neoforge"));
        else props.put("forge_version", ctx.version("forge"));
        props.put("mod_id", ctx.ns);
        props.put("mod_version", ctx.p.meta.version);
        props.put("mod_group_id", ctx.pkg);
        StringBuilder gp = new StringBuilder();
        props.forEach((k, v) -> gp.append(k).append('=').append(v).append('\n'));
        ctx.files.text("gradle.properties", gp.toString());
        ctx.files.text("gradle/wrapper/gradle-wrapper.properties", Scaffold.wrapperProperties(gradle));

        String atFile = Scaffold.findCodeFile(ctx, "accesstransformer.cfg");
        String customRepos = Scaffold.mavenRepos(ctx, "    ");
        String extraRepos = customRepos.isEmpty() ? "" : "\nrepositories {\n" + customRepos + "}\n";
        String customDeps = Scaffold.gradleDeps(ctx, "    ", false, false);
        String jvmPlugins = Scaffold.jvmPlugins(ctx);
        String jvmDeps = Scaffold.jvmDeps(ctx, false, false);
        String jvmFooter = Scaffold.jvmFooter(ctx);
        String allDeps = jvmDeps + customDeps;
        String customGradle = Scaffold.customGradle(ctx);
        String jvmRunLine = "";
        if (ctx.p.meta.jvmArgs != null && !ctx.p.meta.jvmArgs.isBlank()) {
            List<String> jargs = new ArrayList<>();
            for (String tok : ctx.p.meta.jvmArgs.trim().split("\\s+")) if (!tok.isBlank()) jargs.add("'" + tok.replace("'", "\\'") + "'");
            if (!jargs.isEmpty()) jvmRunLine = neo
                    ? "\n                jvmArguments.addAll(" + String.join(", ", jargs) + ")"
                    : "\n                jvmArgs " + String.join(", ", jargs);
        }

        if (neo) {
            ctx.files.text("settings.gradle", """
                    pluginManagement {
                        repositories {
                            mavenLocal()
                            gradlePluginPortal()
                            maven { url = 'https://maven.neoforged.net/releases' }
                        }
                    }

                    plugins {
                        id 'org.gradle.toolchains.foojay-resolver-convention' version '%s'
                    }

                    rootProject.name = '%s'
                    """.formatted(Versions.FOOJAY, ctx.ns));
            String neoDeps = allDeps.isEmpty() ? "" : "\ndependencies {\n" + allDeps + "}\n";
            String neoRuns = """
                            runs {
                                client {
                                    client()
                                }
                                server {
                                    server()
                                    programArgument '--nogui'
                                }
                                configureEach {
                                    logLevel = org.slf4j.event.Level.INFO%s
                                }
                            }""".formatted(jvmRunLine);
            ctx.files.text("build.gradle", """
                    plugins {
                        id 'java-library'
                        id 'idea'
                        id 'net.neoforged.moddev' version '%s'
                    %s}

                    version = mod_version
                    group = mod_group_id

                    base {
                        archivesName = mod_id
                    }

                    java.toolchain.languageVersion = JavaLanguageVersion.of(%d)
                    %s
                    neoForge {
                        version = project.neo_version

                    %s

                        mods {
                            "${mod_id}" {
                                sourceSet(sourceSets.main)
                            }
                        }
                    }
                    %s
                    tasks.withType(JavaCompile).configureEach {
                        options.encoding = 'UTF-8'
                    }
                    %s%s""".formatted(ctx.version("moddev"), jvmPlugins, ctx.v.java, extraRepos, neoRuns, neoDeps, jvmFooter, customGradle));
        } else {
            ctx.files.text("settings.gradle", """
                    pluginManagement {
                        repositories {
                            gradlePluginPortal()
                            maven {
                                name = 'MinecraftForge'
                                url = 'https://maven.minecraftforge.net/'
                            }
                        }
                    }

                    plugins {
                        id 'org.gradle.toolchains.foojay-resolver-convention' version '%s'
                    }

                    rootProject.name = '%s'
                    """.formatted(Versions.FOOJAY, ctx.ns));
            String atLine = atFile == null ? "" : "\n    accessTransformer = file('" + atFile.replace("'", "\\'") + "')";
            String jarBlock = (ctx.v.atLeast(modstudio.core.McVersion.V1_20_4) ? """

                    // Forge 1.20.4+ uses ForgeBootstrap/ClasspathLocator and requires classes + resources in one directory.
                    sourceSets.each {
                        def dir = layout.buildDirectory.dir("sourcesSets/$it.name")
                        it.output.resourcesDir = dir
                        it.java.destinationDirectory = dir
                    }
                    """ : "") + (ctx.components() ? "" : """

                    tasks.named('jar', Jar).configure {
                        finalizedBy 'reobfJar'
                    }
                    """) + (adv.hasMixins() ? """

                    jar {
                        manifest {
                            attributes('MixinConfigs': '%s.mixins.json')
                        }
                    }
                    """.formatted(ctx.ns) : "") + jvmFooter + customGradle;
            ctx.files.text("build.gradle", """
                    plugins {
                        id 'eclipse'
                        id 'idea'
                        id 'net.minecraftforge.gradle' version '%s'
                    %s}

                    version = mod_version
                    group = mod_group_id

                    base {
                        archivesName = mod_id
                    }

                    java.toolchain.languageVersion = JavaLanguageVersion.of(%d)

                    minecraft {
                        mappings channel: 'official', version: minecraft_version
                        copyIdeResources = true%s

                        runs {
                            configureEach {
                                workingDirectory project.file('run')
                                property 'forge.logging.console.level', 'info'%s
                                mods {
                                    "${mod_id}" {
                                        source sourceSets.main
                                    }
                                }
                            }
                            client {
                            }
                            server {
                                args '--nogui'
                            }
                        }
                    }
                    %s
                    dependencies {
                        minecraft "net.minecraftforge:forge:${minecraft_version}-${forge_version}"
                    %s}

                    tasks.withType(JavaCompile).configureEach {
                        options.encoding = 'UTF-8'
                    }
                    %s""".formatted(ctx.version("forgegradle"), jvmPlugins, ctx.v.java, atLine, jvmRunLine, extraRepos, allDeps, jarBlock));
        }
        ctx.files.text(".gitignore", Scaffold.GITIGNORE);
    }

    private static String toml(String s) {
        return "\"" + (s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")) + "\"";
    }

    /** NeoForge 20.4 still used META-INF/mods.toml and "mandatory" dependencies. */
    private boolean legacyToml() {
        return !neo || !ctx.components();
    }

    /** Forge 56 and later (Minecraft 1.21.6+) replaced the event bus API. */
    private boolean eventBus7() {
        if (neo) return false;
        try {
            return Integer.parseInt(major(ctx.version("forge")).trim()) >= 56;
        } catch (NumberFormatException e) {
            return ctx.v.atLeast(modstudio.core.McVersion.V1_21_8);
        }
    }

    private static String major(String version) {
        int dot = version.indexOf('.');
        return dot < 0 ? version : version.substring(0, dot);
    }

    private static String majorMinor(String version) {
        String[] p = version.split("\\.");
        return p.length >= 2 ? p[0] + "." + p[1] : version;
    }

    private void metadata() {
        Project.Meta m = ctx.p.meta;
        String loaderRange = neo ? "[1,)" : "[" + major(ctx.version("forge")) + ",)";
        StringBuilder t = new StringBuilder();
        t.append("modLoader=\"javafml\"\n");
        t.append("loaderVersion=").append(toml(loaderRange)).append('\n');
        t.append("license=").append(toml(m.license.isBlank() ? "All Rights Reserved" : m.license)).append("\n\n");
        t.append("[[mods]]\n");
        t.append("modId=").append(toml(ctx.ns)).append('\n');
        t.append("version=").append(toml(m.version)).append('\n');
        t.append("displayName=").append(toml(m.name)).append('\n');
        if (!m.authors.isBlank()) t.append("authors=").append(toml(m.authors)).append('\n');
        if (!m.website.isBlank()) t.append("displayURL=").append(toml(m.website)).append('\n');
        if (ctx.p.texture(m.icon) != null) t.append("logoFile=\"icon.png\"\n");
        t.append("description=").append(toml(m.description)).append("\n\n");
        String req = legacyToml() ? "mandatory=true" : "type=\"required\"";
        t.append("[[dependencies.").append(ctx.ns).append("]]\n");
        t.append("modId=").append(toml(neo ? "neoforge" : "forge")).append('\n');
        t.append(req).append('\n');
        t.append("versionRange=").append(toml(neo ? "[" + majorMinor(ctx.version("neoforge")) + ",)" : loaderRange)).append('\n');
        t.append("ordering=\"NONE\"\nside=\"BOTH\"\n\n");
        t.append("[[dependencies.").append(ctx.ns).append("]]\n");
        t.append("modId=\"minecraft\"\n");
        t.append(req).append('\n');
        t.append("versionRange=").append(toml(ctx.v.range)).append('\n');
        t.append("ordering=\"NONE\"\nside=\"BOTH\"\n");
        AdvancedModGen.appendTomlDepends(ctx, t, legacyToml());
        if (adv.hasMixins() && (neo || !legacyToml())) {
            t.append("\n[[mixins]]\nconfig=").append(toml(ctx.ns + ".mixins.json")).append('\n');
        }
        String atFile = Scaffold.findCodeFile(ctx, "accesstransformer.cfg");
        if (atFile != null && neo && !legacyToml()) {
            String rel = atFile.startsWith("src/main/resources/") ? atFile.substring("src/main/resources/".length()) : atFile;
            t.append("\n[[accessTransformers]]\nfile=").append(toml(rel)).append('\n');
        }
        ctx.files.text(res + "META-INF/" + (legacyToml() ? "mods.toml" : "neoforge.mods.toml"), t.toString());
        if (legacyToml()) {
            ctx.files.json(res + "pack.mcmeta", J.packMeta(ctx.v, true, ctx.ns + " resources"));
        }
        jc.writeIcon(res + "icon.png");
    }

    private String regImports(Src s) {
        if (neo) {
            s.imp("net.neoforged.neoforge.registries.DeferredRegister");
        } else {
            s.imp("net.minecraftforge.registries.DeferredRegister", "net.minecraftforge.registries.ForgeRegistries",
                    "net.minecraftforge.registries.RegistryObject");
        }
        return "";
    }

    private void registries() {
        String main = ctx.mainClass;
        // Blocks
        Src b = new Src(ctx.pkg);
        regImports(b);
        jc.importsForBlocks(b);
        b.l("public final class ModBlocks {");
        if (neo) {
            b.imp("net.neoforged.neoforge.registries.DeferredBlock");
            b.l("public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(" + main + ".MODID);");
        } else {
            b.l("public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, " + main + ".MODID);");
        }
        b.blank();
        for (Block bl : ctx.all(Block.class)) {
            String type = neo ? "DeferredBlock<Block>" : "RegistryObject<Block>";
            b.l("public static final " + type + " " + Ids.toConst(bl.name) + " = BLOCKS.register(" + Src.str(bl.name) + ", () -> " + jc.blockConstruct(bl) + ");");
        }
        for (Elements.Fluid fl : ctx.all(Elements.Fluid.class)) {
            String type = neo ? "DeferredBlock<Block>" : "RegistryObject<Block>";
            String blkCls = JavaCommon.hasFluidBlockClass(fl) ? JavaCommon.fluidBlockClassName(fl) : "LiquidBlock";
            String c = Ids.toConst(fl.name);
            String arg = neo ? "ModFluids.STILL_" + c + ".get()" : "ModFluids.STILL_" + c;
            b.l("public static final " + type + " " + c + " = BLOCKS.register(" + Src.str(fl.name) + ", () -> new "
                    + blkCls + "(" + arg + ", " + jc.fluidBlockProps(fl) + "));");
        }
        for (ModElements.Container ct : ctx.all(ModElements.Container.class)) {
            if (!adv.standaloneContainerBlock(ct)) continue;
            String type = neo ? "DeferredBlock<Block>" : "RegistryObject<Block>";
            String bn = ct.block.isBlank() ? ct.name : Ids.path(Ids.full(ct.block));
            String blkCls = Ids.toPascal(ct.name) + "ContainerBlock";
            b.imp(ctx.pkg + ".block." + blkCls);
            String props = "BlockBehaviour.Properties.of()" + (ctx.registryKeys() ? ".setId(" + jc.blockKey(bn) + ")" : "") + ".strength(2.5F)";
            b.l("public static final " + type + " " + Ids.toConst(bn) + " = BLOCKS.register(" + Src.str(bn) + ", () -> new " + blkCls + "(" + props + "));");
        }
        for (ModElements.Portal pt : ctx.all(ModElements.Portal.class)) {
            String type = neo ? "DeferredBlock<Block>" : "RegistryObject<Block>";
            String bn = pt.name + "_portal";
            String blkCls = Ids.toPascal(pt.name) + "PortalBlock";
            b.imp(ctx.pkg + ".block." + blkCls);
            String props = "BlockBehaviour.Properties.of()" + (ctx.registryKeys() ? ".setId(" + jc.blockKey(bn) + ")" : "") + "." + ctx.noCollision() + ".strength(-1.0F).lightLevel(s -> 11).noLootTable()";
            b.l("public static final " + type + " " + Ids.toConst(bn) + " = BLOCKS.register(" + Src.str(bn) + ", () -> new " + blkCls + "(" + props + "));");
        }
        b.blank().l("private ModBlocks() {").l("}").l("}");
        ctx.files.text(ctx.javaDir() + "ModBlocks.java", b.toString());

        // Items
        Src i = new Src(ctx.pkg);
        regImports(i);
        jc.importsForItems(i);
        i.l("public final class ModItems {");
        if (neo) {
            i.imp("net.neoforged.neoforge.registries.DeferredItem");
            i.l("public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(" + main + ".MODID);");
        } else {
            i.l("public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, " + main + ".MODID);");
        }
        i.blank();
        String itemType = neo ? "DeferredItem<Item>" : "RegistryObject<Item>";
        for (Item it : ctx.all(Item.class)) {
            i.l("public static final " + itemType + " " + Ids.toConst(it.name) + " = ITEMS.register(" + Src.str(it.name) + ", () -> " + jc.itemConstruct(it) + ");");
        }
        for (Block bl : ctx.all(Block.class)) {
            i.l("public static final " + itemType + " " + Ids.toConst(bl.name) + " = ITEMS.register(" + Src.str(bl.name)
                    + ", () -> new BlockItem(" + jc.blockRef(bl.name) + ", " + jc.blockItemProps(bl.name) + "));");
        }
        for (Elements.Fluid fl : ctx.all(Elements.Fluid.class)) {
            if (!fl.generateBucket) continue;
            i.imp("net.minecraft.world.item.BucketItem", "net.minecraft.world.item.Items");
            String bname = fl.name + "_bucket";
            String bprops = "new Item.Properties()" + (ctx.registryKeys() ? ".setId(" + jc.itemKey(bname) + ")" : "") + ".craftRemainder(Items.BUCKET).stacksTo(1)";
            String c = Ids.toConst(fl.name);
            String arg = neo ? "ModFluids.STILL_" + c + ".get()" : "ModFluids.STILL_" + c;
            i.l("public static final " + itemType + " " + Ids.toConst(bname) + " = ITEMS.register(" + Src.str(bname)
                    + ", () -> new BucketItem(" + arg + ", " + bprops + "));");
        }
        for (ModElements.Container ct : ctx.all(ModElements.Container.class)) {
            if (!adv.standaloneContainerBlock(ct)) continue;
            String bn = ct.block.isBlank() ? ct.name : Ids.path(Ids.full(ct.block));
            i.l("public static final " + itemType + " " + Ids.toConst(bn) + " = ITEMS.register(" + Src.str(bn)
                    + ", () -> new BlockItem(" + jc.blockRef(bn) + ", " + jc.blockItemProps(bn) + "));");
        }
        for (ModElements.CustomEntity ce : ctx.all(ModElements.CustomEntity.class)) {
            if (!ce.spawnEgg || !ce.isLivingMob()) continue;
            String egg = ce.name + "_spawn_egg";
            i.l("public static final " + itemType + " " + Ids.toConst(egg) + " = ITEMS.register(" + Src.str(egg)
                    + ", () -> " + jc.spawnEggExpr(i, ce) + ");");
        }
        for (ModElements.Portal pt : ctx.all(ModElements.Portal.class)) {
            if (!pt.generateIgniter) continue;
            String iname = pt.name + "_igniter", cls = Ids.toPascal(pt.name) + "IgniterItem";
            i.imp(ctx.pkg + ".item." + cls);
            String props = "new Item.Properties()" + (ctx.registryKeys() ? ".setId(" + jc.itemKey(iname) + ")" : "") + ".stacksTo(1)";
            i.l("public static final " + itemType + " " + Ids.toConst(iname) + " = ITEMS.register(" + Src.str(iname)
                    + ", () -> new " + cls + "(" + props + "));");
        }
        for (ModElements.ArmorTrim at : ctx.all(ModElements.ArmorTrim.class)) {
            if (!at.generateTemplate) continue;
            String tname = at.name + "_armor_trim_smithing_template";
            String props = "new Item.Properties()" + (ctx.registryKeys() ? ".setId(" + jc.itemKey(tname) + ")" : "");
            i.l("public static final " + itemType + " " + Ids.toConst(tname) + " = ITEMS.register(" + Src.str(tname)
                    + ", () -> new Item(" + props + "));");
        }
        i.blank().l("private ModItems() {").l("}").l("}");
        ctx.files.text(ctx.javaDir() + "ModItems.java", i.toString());

        // Creative tabs
        Src t = new Src(ctx.pkg);
        regImports(t);
        t.imp("net.minecraft.core.registries.Registries", "net.minecraft.network.chat.Component",
                "net.minecraft.world.item.CreativeModeTab", "net.minecraft.world.item.ItemStack");
        t.l("public final class ModTabs {");
        t.l("public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, " + main + ".MODID);");
        t.blank();
        for (CreativeTab tab : ctx.all(CreativeTab.class)) {
            if (neo) t.imp("net.neoforged.neoforge.registries.DeferredHolder");
            String type = neo ? "DeferredHolder<CreativeModeTab, CreativeModeTab>" : "RegistryObject<CreativeModeTab>";
            t.l("public static final " + type + " " + Ids.toConst(tab.name) + " = TABS.register(" + Src.str(tab.name) + ", () -> CreativeModeTab.builder()");
            t.l("        .title(Component.translatable(" + Src.str("itemGroup." + ctx.ns + "." + tab.name) + "))");
            t.l("        .icon(() -> new ItemStack(" + jc.anyItemRef(tab.icon) + "))");
            t.l("        .displayItems((parameters, output) -> {");
            for (String name : tabMembers("tab:" + tab.name)) t.l("            output.accept(" + jc.itemRef(name) + ");");
            t.l("        })");
            t.l("        .build());");
        }
        t.blank().l("private ModTabs() {").l("}").l("}");
        ctx.files.text(ctx.javaDir() + "ModTabs.java", t.toString());

        // Sounds
        Src so = new Src(ctx.pkg);
        regImports(so);
        so.imp("net.minecraft.sounds.SoundEvent");
        so.l("public final class ModSounds {");
        if (neo) {
            so.imp("net.minecraft.core.registries.Registries");
            so.l("public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, " + main + ".MODID);");
        } else {
            so.l("public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, " + main + ".MODID);");
        }
        so.blank();
        for (Sound s : ctx.all(Sound.class)) {
            if (neo) so.imp("net.neoforged.neoforge.registries.DeferredHolder");
            String type = neo ? "DeferredHolder<SoundEvent, SoundEvent>" : "RegistryObject<SoundEvent>";
            so.l("public static final " + type + " " + Ids.toConst(s.name) + " = SOUNDS.register(" + Src.str(s.name)
                    + ", () -> SoundEvent.createVariableRangeEvent(" + main + ".id(" + Src.str(s.name) + ")));");
        }
        so.blank().l("private ModSounds() {").l("}").l("}");
        ctx.files.text(ctx.javaDir() + "ModSounds.java", so.toString());
    }

    private void fluids() {
        String main = ctx.mainClass;
        Src s = new Src(ctx.pkg);
        regImports(s);
        s.imp("net.minecraft.core.registries.Registries", "net.minecraft.sounds.SoundEvents",
                "net.minecraft.world.level.block.LiquidBlock", "net.minecraft.world.level.material.FlowingFluid",
                "net.minecraft.world.level.material.Fluid");
        if (neo) {
            s.imp("net.neoforged.neoforge.common.SoundActions", "net.neoforged.neoforge.fluids.BaseFlowingFluid",
                    "net.neoforged.neoforge.fluids.FluidType", "net.neoforged.neoforge.registries.DeferredHolder",
                    "net.neoforged.neoforge.registries.NeoForgeRegistries");
        } else {
            s.imp("net.minecraftforge.common.SoundActions", "net.minecraftforge.fluids.ForgeFlowingFluid",
                    "net.minecraftforge.fluids.FluidType");
        }
        boolean inlineClientExt = !(neo && ctx.singular());
        if (inlineClientExt) {
            s.imp("java.util.function.Consumer", ctx.rlImport(),
                    (neo ? "net.neoforged.neoforge.client.extensions.common." : "net.minecraftforge.client.extensions.common.") + "IClientFluidTypeExtensions");
        }
        s.l("public final class ModFluids {");
        String ftKey = neo ? "NeoForgeRegistries.Keys.FLUID_TYPES" : "ForgeRegistries.Keys.FLUID_TYPES";
        String flKey = neo ? "Registries.FLUID" : "ForgeRegistries.FLUIDS";
        s.l("public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(" + ftKey + ", " + main + ".MODID);");
        s.l("public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(" + flKey + ", " + main + ".MODID);");
        s.blank();
        String ftWrap = neo ? "DeferredHolder<FluidType, FluidType>" : "RegistryObject<FluidType>";
        String flWrap = neo ? "DeferredHolder<Fluid, FlowingFluid>" : "RegistryObject<FlowingFluid>";
        String baseClass = neo ? "BaseFlowingFluid" : "ForgeFlowingFluid";
        for (Elements.Fluid fl : ctx.all(Elements.Fluid.class)) {
            String c = Ids.toConst(fl.name);
            String props = "FluidType.Properties.create().density(" + fl.density + ").viscosity(" + Math.max(100, fl.viscosity)
                    + ").lightLevel(" + Math.min(15, fl.luminance) + ")"
                    + ".sound(SoundActions.BUCKET_FILL, SoundEvents." + (fl.isLavaLike ? "BUCKET_FILL_LAVA" : "BUCKET_FILL") + ")"
                    + ".sound(SoundActions.BUCKET_EMPTY, SoundEvents." + (fl.isLavaLike ? "BUCKET_EMPTY_LAVA" : "BUCKET_EMPTY") + ")";
            if (inlineClientExt) {
                int rgb = JavaCommon.rgbInt(fl.tintColor, 0x3F76E4);
                String stillTex = ctx.rlParseExpr(Src.str(jc.fluidTextureId(fl, false)));
                String flowTex = ctx.rlParseExpr(Src.str(jc.fluidTextureId(fl, true)));
                s.l("public static final " + ftWrap + " " + c + "_TYPE = FLUID_TYPES.register(" + Src.str(fl.name) + ", () -> new FluidType(" + props + ") {");
                s.l("    @Override public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {");
                s.l("        consumer.accept(new IClientFluidTypeExtensions() {");
                s.l("            @Override public " + ctx.rlType() + " getStillTexture() { return " + stillTex + "; }");
                s.l("            @Override public " + ctx.rlType() + " getFlowingTexture() { return " + flowTex + "; }");
                s.l("            @Override public int getTintColor() { return 0xFF" + String.format("%06X", rgb) + "; }");
                if (neo && fl.fogColor != null && !fl.fogColor.isBlank() && !ctx.v.atLeast(McVersion.V1_21_8)) {
                    int fog = JavaCommon.rgbInt(fl.fogColor, rgb);
                    s.imp("net.minecraft.client.Camera", "net.minecraft.client.multiplayer.ClientLevel", "org.joml.Vector3f");
                    s.l("            @Override public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, Vector3f fluidFogColor) {");
                    s.l("                return new Vector3f(" + Src.f(((fog >> 16) & 0xFF) / 255.0f) + ", " + Src.f(((fog >> 8) & 0xFF) / 255.0f) + ", " + Src.f((fog & 0xFF) / 255.0f) + ");");
                    s.l("            }");
                }
                s.l("        });");
                s.l("    }");
                s.l("});");
            } else {
                s.l("public static final " + ftWrap + " " + c + "_TYPE = FLUID_TYPES.register(" + Src.str(fl.name) + ", () -> new FluidType(" + props + "));");
            }
            s.l("public static final " + flWrap + " STILL_" + c + " = FLUIDS.register(" + Src.str(fl.name) + ", () -> new " + baseClass + ".Source(ModFluids." + c + "_PROPS));");
            s.l("public static final " + flWrap + " FLOWING_" + c + " = FLUIDS.register(" + Src.str("flowing_" + fl.name) + ", () -> new " + baseClass + ".Flowing(ModFluids." + c + "_PROPS));");
            s.l("public static final " + baseClass + ".Properties " + c + "_PROPS = new " + baseClass + ".Properties("
                    + c + "_TYPE, STILL_" + c + ", FLOWING_" + c + ")");
            s.l("        .slopeFindDistance(" + Math.max(1, fl.slopeFindDistance) + ").levelDecreasePerBlock(" + Math.max(1, fl.levelDecrease)
                    + ").tickRate(" + Math.max(1, fl.tickRate) + ")");
            s.l("        .block(() -> (LiquidBlock) ModBlocks." + c + ".get())"
                    + (fl.generateBucket ? ".bucket(() -> ModItems." + c + "_BUCKET.get());" : ";"));
            s.blank();
        }
        s.l("private ModFluids() {").l("}").l("}");
        ctx.files.text(ctx.javaDir() + "ModFluids.java", s.toString());
    }

    /** Names of project items/blocks assigned to a creative tab key. */
    private List<String> tabMembers(String key) {
        return JavaCommon.tabMembers(ctx, adv, key);
    }

    private void mainClass() {
        String main = ctx.mainClass;
        Src s = new Src(ctx.pkg);
        s.imp(ctx.rlImport(), "net.minecraft.world.item.CreativeModeTabs");
        if (neo) {
            s.imp("net.neoforged.bus.api.IEventBus", "net.neoforged.fml.common.Mod", "net.neoforged.neoforge.common.NeoForge",
                    "net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent");
        } else {
            if (!eventBus7()) s.imp("net.minecraftforge.common.MinecraftForge");
            s.imp("net.minecraftforge.event.BuildCreativeModeTabContentsEvent",
                    eventBus7() ? "net.minecraftforge.eventbus.api.bus.BusGroup" : "net.minecraftforge.eventbus.api.IEventBus",
                    "net.minecraftforge.fml.common.Mod", "net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext");
        }
        s.l("@Mod(" + main + ".MODID)");
        s.l("public class " + main + " {");
        s.l("public static final String MODID = " + Src.str(ctx.ns) + ";");
        s.blank();
        if (neo) {
            if (adv.hasConfigs() && ctx.components()) {
                s.imp("net.neoforged.fml.ModContainer");
                s.l("public " + main + "(IEventBus modBus, ModContainer modContainer) {");
            } else {
                s.l("public " + main + "(IEventBus modBus) {");
            }
        } else if (eventBus7()) {
            // Forge 56+ (Minecraft 1.21.6+) uses EventBus 7: bus groups and a per-event bus
            s.l("public " + main + "(FMLJavaModLoadingContext context) {");
            s.l("BusGroup modBus = context.getModBusGroup();");
        } else {
            s.l("public " + main + "() {");
            s.l("IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();");
        }
        if (!ctx.all(Elements.Fluid.class).isEmpty()) {
            s.l("ModFluids.FLUID_TYPES.register(modBus);");
            s.l("ModFluids.FLUIDS.register(modBus);");
        }
        s.l("ModBlocks.BLOCKS.register(modBus);");
        s.l("ModItems.ITEMS.register(modBus);");
        if (adv.hasEntities()) s.l("ModEntities.ENTITIES.register(modBus);");
        if (adv.hasContainers()) {
            s.l("ModBlockEntities.BLOCK_ENTITIES.register(modBus);");
            s.l("ModContainerMenus.MENUS.register(modBus);");
        }
        if (adv.hasProfessions()) {
            s.l("ModProfessions.POI_TYPES.register(modBus);");
            s.l("ModProfessions.PROFESSIONS.register(modBus);");
        }
        if (adv.hasRecipeTypes()) {
            s.l("ModRecipeTypes.TYPES.register(modBus);");
            s.l("ModRecipeTypes.SERIALIZERS.register(modBus);");
        }
        s.l("ModTabs.TABS.register(modBus);");
        s.l("ModSounds.SOUNDS.register(modBus);");
        if (jc.hasArmor() && jc.registeredArmor()) s.l("ModArmorMaterials.ARMOR_MATERIALS.register(modBus);");
        if (jc.hasEffects()) s.l("ModEffects.EFFECTS.register(modBus);");
        if (jc.hasPotions()) s.l("ModPotions.POTIONS.register(modBus);");
        if (jc.codePaintings()) s.l("ModPaintings.PAINTINGS.register(modBus);");
        if (adv.hasConfigs()) {
            s.imp(neo ? "net.neoforged.fml.config.ModConfig" : "net.minecraftforge.fml.config.ModConfig");
            for (ModElements.ModConfig cfg : ctx.all(ModElements.ModConfig.class)) {
                String cls = Ids.toPascal(cfg.name) + "Config";
                s.imp(ctx.pkg + ".config." + cls);
                String scope = "ModConfig.Type." + cfg.scope.toUpperCase();
                if (neo && ctx.components()) s.l("modContainer.registerConfig(" + scope + ", " + cls + ".SPEC);");
                else if (eventBus7()) s.l("context.registerConfig(" + scope + ", " + cls + ".SPEC);");
                else {
                    s.imp(neo ? "net.neoforged.fml.ModLoadingContext" : "net.minecraftforge.fml.ModLoadingContext");
                    s.l("ModLoadingContext.get().registerConfig(" + scope + ", " + cls + ".SPEC);");
                }
            }
        }
        if (adv.hasNetwork()) s.l("ModNetwork.register(modBus);");
        List<ModElements.CustomEntity> living = ctx.all(ModElements.CustomEntity.class).stream().filter(ModElements.CustomEntity::isLivingMob).toList();
        if (!living.isEmpty()) {
            s.imp(neo ? "net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent" : "net.minecraftforge.event.entity.EntityAttributeCreationEvent");
            for (var ce : living) s.imp(ctx.pkg + ".entity." + Ids.toPascal(ce.name) + "Entity");
            String regCall = eventBus7() ? "EntityAttributeCreationEvent.getBus(modBus).addListener" : "modBus.addListener";
            s.l(regCall + "(" + (eventBus7() ? "ev -> {" : "(EntityAttributeCreationEvent ev) -> {"));
            for (var ce : living) {
                s.l("    ev.put(ModEntities." + Ids.toConst(ce.name) + ".get(), " + Ids.toPascal(ce.name) + "Entity.createAttributes().build());");
            }
            s.l("});");
        }
        s.l(eventBus7() ? "BuildCreativeModeTabContentsEvent.getBus(modBus).addListener(this::addToVanillaTabs);"
                : "modBus.addListener(this::addToVanillaTabs);");
        if (eventBus7()) {
            s.l("ModEvents.init();");
        } else {
            s.l((neo ? "NeoForge" : "MinecraftForge") + ".EVENT_BUS.register(ModEvents.class);");
            for (var h : JavaSteps.hooks(ctx, "event_bus")) {
                s.l((neo ? "NeoForge" : "MinecraftForge") + ".EVENT_BUS.register(" + h.fqcn() + ".class);");
            }
        }
        for (var h : JavaSteps.hooks(ctx, "common_init")) {
            s.l(h.fqcn() + "." + h.method() + "();");
        }
        hooks.forgeConstructor(s);
        List<JavaSteps.HookCall> clientInits = JavaSteps.hooks(ctx, "client_init");
        if (adv.hasClientHooks() || !clientInits.isEmpty()) {
            if (neo) s.imp("net.neoforged.fml.loading.FMLEnvironment", "net.neoforged.api.distmarker.Dist");
            else s.imp("net.minecraftforge.fml.loading.FMLEnvironment", "net.minecraftforge.api.distmarker.Dist");
            String distExpr = (neo && ctx.v.atLeast(McVersion.V1_21_9)) ? "FMLEnvironment.getDist()" : "FMLEnvironment.dist";
            if (adv.hasClientHooks()) s.l("if (" + distExpr + " == Dist.CLIENT) ModClientRegistrations.init(modBus);");
            for (var h : clientInits) s.l("if (" + distExpr + " == Dist.CLIENT) " + h.fqcn() + "." + h.method() + "();");
        }
        s.l("}");
        s.blank();
        s.l("public static " + ctx.rlType() + " id(String path) {");
        s.l("return " + ctx.rlFrom("MODID", "path") + ";");
        s.l("}");
        s.blank();
        JavaCommon.registryKeyHelpers(ctx, s);
        s.l("private void addToVanillaTabs(BuildCreativeModeTabContentsEvent event) {");
        for (var tab : Vanilla.CREATIVE_TABS.entrySet()) {
            List<String> members = tabMembers(tab.getKey());
            if (members.isEmpty()) continue;
            s.l("if (event.getTabKey() == CreativeModeTabs." + tab.getValue() + ") {");
            for (String n : members) s.l("event.accept(" + jc.itemRef(n) + ");");
            s.l("}");
        }
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + main + ".java", s.toString());
    }

    private void events() {
        Src s = new Src(ctx.pkg);
        String subscribe = neo ? "net.neoforged.bus.api.SubscribeEvent"
                : eventBus7() ? "net.minecraftforge.eventbus.api.listener.SubscribeEvent" : "net.minecraftforge.eventbus.api.SubscribeEvent";
        s.imp(subscribe);
        s.l("/** Game event hooks. Most procedure triggers run through data pack functions instead. */");
        s.l("public final class ModEvents {");
        s.l("private ModEvents() {").l("}");
        if (jc.hasCommands()) {
            s.imp(evPkg() + ".RegisterCommandsEvent");
            s.blank().l("@SubscribeEvent").l("public static void onRegisterCommands(RegisterCommandsEvent event) {")
                    .l("ModCommands.register(event.getDispatcher());").l("}");
        }
        List<Procedure> rc = jc.eventRightClickProcs();
        if (!rc.isEmpty()) {
            s.imp(evPkg() + ".entity.player.PlayerInteractEvent", "net.minecraft.world.item.ItemStack");
            s.blank().l("@SubscribeEvent").l("public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {");
            s.l("if (event.getLevel().isClientSide()) return;");
            s.l("ItemStack stack = event.getItemStack();");
            for (Procedure p : rc) {
                s.l("if (" + jc.stackMatches(p.triggerValue) + ") ModProcedures.run(event.getEntity(), " + Src.str("procedures/" + p.name) + ");");
            }
            s.l("}");
        }
        List<ModElements.Portal> customIgniters = ctx.all(ModElements.Portal.class).stream()
                .filter(pt -> !pt.generateIgniter && !pt.igniterItem.isBlank()).toList();
        if (!customIgniters.isEmpty()) {
            s.imp(evPkg() + ".entity.player.PlayerInteractEvent", "net.minecraft.world.InteractionResult",
                    "net.minecraft.world.item.ItemStack", ctx.pkg + ".ModItems", ctx.pkg + ".ModProcedures");
            s.blank().l("@SubscribeEvent").l("public static void onRightClickPortalBlock(PlayerInteractEvent.RightClickBlock event) {");
            s.l("ItemStack stack = event.getItemStack();");
            for (ModElements.Portal pt : customIgniters) {
                String blkCls = Ids.toPascal(pt.name) + "PortalBlock";
                s.imp(ctx.pkg + ".block." + blkCls);
                s.l("if (" + jc.stackMatches(pt.igniterItem) + " && " + blkCls + ".tryIgnite(event.getLevel(), event.getPos(), event.getFace())) {");
                if (!eventBus7()) s.l("    event.setCanceled(true);");
                s.l("    event.setCancellationResult(InteractionResult.SUCCESS); return;");
                s.l("}");
            }
            s.l("}");
        }
        hooks.forgeEvents(s, evPkg());
        List<JavaSteps.HookCall> tickHooks = JavaSteps.hooks(ctx, "server_tick");
        if (!tickHooks.isEmpty()) {
            if (neo && ctx.components()) {
                s.imp("net.neoforged.neoforge.event.tick.ServerTickEvent");
                s.blank().l("@SubscribeEvent").l("public static void onCodeServerTick(ServerTickEvent.Post event) {");
                for (var h : tickHooks) s.l(h.fqcn() + "." + h.method() + "(event.getServer());");
                s.l("}");
            } else if (!neo && ctx.v.atLeast(modstudio.core.McVersion.V1_20_4)) {
                s.imp("net.minecraftforge.event.TickEvent");
                s.blank().l("@SubscribeEvent").l("public static void onCodeServerTick(TickEvent.ServerTickEvent.Post event) {");
                for (var h : tickHooks) s.l(h.fqcn() + "." + h.method() + "(event.getServer());");
                s.l("}");
            } else {
                s.imp(evPkg() + ".TickEvent");
                s.blank().l("@SubscribeEvent").l("public static void onCodeServerTick(TickEvent.ServerTickEvent event) {");
                s.l("if (event.phase != TickEvent.Phase.END) return;");
                for (var h : tickHooks) s.l(h.fqcn() + "." + h.method() + "(event.getServer());");
                s.l("}");
            }
        }
        if (adv.hasProfessions()) {
            s.imp(evPkg() + ".village.VillagerTradesEvent", "net.minecraft.world.item.ItemStack",
                    "net.minecraft.world.item.trading.MerchantOffer");
            if (ctx.components()) s.imp("net.minecraft.world.item.trading.ItemCost");
            s.blank().l("@SubscribeEvent").l("public static void onVillagerTrades(VillagerTradesEvent event) {");
            for (ModElements.Profession pf : ctx.all(ModElements.Profession.class)) {
                s.l("if (event.getType() == ModProfessions." + Ids.toConst(pf.name) + ".get()) {");
                for (ModElements.ProfessionTrade tr : pf.trades) {
                    if (tr.costA.isBlank() || tr.result.isBlank()) continue;
                    s.l("    event.getTrades().get(" + Math.max(1, Math.min(5, tr.level)) + ").add((e, r) -> "
                            + AdvancedModGen.merchantOfferExpr(s, ctx, jc, tr) + ");");
                }
                s.l("}");
            }
            s.l("}");
        }
        if (eventBus7()) {
            s.blank().l("public static void init() {");
            if (jc.hasCommands()) s.l("RegisterCommandsEvent.BUS.addListener(ModEvents::onRegisterCommands);");
            if (!rc.isEmpty()) s.l("PlayerInteractEvent.RightClickItem.BUS.addListener(ModEvents::onRightClickItem);");
            if (!customIgniters.isEmpty()) s.l("PlayerInteractEvent.RightClickBlock.BUS.addListener(ModEvents::onRightClickPortalBlock);");
            if (hooks.hasLoot()) s.l("LootTableLoadEvent.BUS.addListener(ModEvents::onLootTableLoad);");
            if (hooks.hasBrewing()) s.l("BrewingRecipeRegisterEvent.BUS.addListener(ModEvents::onBrewingRecipes);");
            if (!tickHooks.isEmpty()) s.l("TickEvent.ServerTickEvent.Post.BUS.addListener(ModEvents::onCodeServerTick);");
            if (adv.hasProfessions()) s.l("VillagerTradesEvent.BUS.addListener(ModEvents::onVillagerTrades);");
            s.l("}");
        }
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModEvents.java", s.toString());
    }

    private void worldgen() {
        for (Block b : ctx.all(Block.class)) {
            if (!b.oreGen) continue;
            String dir = neo ? "neoforge/biome_modifier" : "forge/biome_modifier";
            List<String> biomes = DataGen.biomeList(b);
            JsonObject mod = J.obj("type", neo ? "neoforge:add_features" : "forge:add_features");
            if (biomes.size() > 1) mod.add("biomes", J.list(biomes));
            else mod.addProperty("biomes", biomes.get(0));
            mod.addProperty("features", ctx.id(DataGen.oreFeature(b)));
            mod.addProperty("step", "underground_ores");
            ctx.files.json(res + "data/" + ctx.ns + "/" + dir + "/" + DataGen.oreFeature(b) + ".json", mod);
        }
    }

    private void fuels() {
        if (!neo) return;
        JsonObject values = new JsonObject();
        for (Item it : ctx.all(Item.class)) {
            if (it.burnTime > 0) values.add(ctx.id(it.name), J.obj("burn_time", it.burnTime));
        }
        if (!values.isEmpty()) ctx.files.json(res + "data/neoforge/data_maps/item/furnace_fuels.json", J.obj("values", values));
    }
}
