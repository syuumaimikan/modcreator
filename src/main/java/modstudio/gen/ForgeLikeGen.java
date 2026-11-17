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
    private final String res = "src/main/resources/";

    public ForgeLikeGen(GenContext ctx, boolean neo) {
        this.ctx = ctx;
        this.neo = neo;
        this.jc = new JavaCommon(ctx, neo ? JavaCommon.Loader.NEOFORGE : JavaCommon.Loader.FORGE);
    }

    private String evPkg() {
        return neo ? "net.neoforged.neoforge.event" : "net.minecraftforge.event";
    }

    public void generate() {
        buildFiles();
        metadata();
        mainClass();
        registries();
        events();
        jc.procedures();
        if (jc.hasCommands()) jc.commands();
        for (Item it : ctx.all(Item.class)) if (jc.needsClass(it)) jc.itemClass(it);
        for (Block bl : ctx.all(Block.class)) if (jc.needsBlockClass(bl)) jc.blockClass(bl);
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
            ctx.files.text("build.gradle", """
                    plugins {
                        id 'java-library'
                        id 'idea'
                        id 'net.neoforged.moddev' version '%s'
                    }

                    version = mod_version
                    group = mod_group_id

                    base {
                        archivesName = mod_id
                    }

                    java.toolchain.languageVersion = JavaLanguageVersion.of(%d)

                    neoForge {
                        version = project.neo_version

                        runs {
                            client {
                                client()
                            }
                            server {
                                server()
                                programArgument '--nogui'
                            }
                            configureEach {
                                logLevel = org.slf4j.event.Level.INFO
                            }
                        }

                        mods {
                            "${mod_id}" {
                                sourceSet(sourceSets.main)
                            }
                        }
                    }

                    tasks.withType(JavaCompile).configureEach {
                        options.encoding = 'UTF-8'
                    }
                    """.formatted(ctx.version("moddev"), ctx.v.java));
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
            String jarBlock = ctx.components() ? "" : """

                    tasks.named('jar', Jar).configure {
                        finalizedBy 'reobfJar'
                    }
                    """;
            ctx.files.text("build.gradle", """
                    plugins {
                        id 'eclipse'
                        id 'idea'
                        id 'net.minecraftforge.gradle' version '%s'
                    }

                    version = mod_version
                    group = mod_group_id

                    base {
                        archivesName = mod_id
                    }

                    java.toolchain.languageVersion = JavaLanguageVersion.of(%d)

                    minecraft {
                        mappings channel: 'official', version: minecraft_version
                        copyIdeResources = true

                        runs {
                            configureEach {
                                workingDirectory project.file('run')
                                property 'forge.logging.console.level', 'info'
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

                    dependencies {
                        minecraft "net.minecraftforge:forge:${minecraft_version}-${forge_version}"
                    }

                    tasks.withType(JavaCompile).configureEach {
                        options.encoding = 'UTF-8'
                    }
                    %s""".formatted(ctx.version("forgegradle"), ctx.v.java, jarBlock));
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
        ctx.files.text(res + "META-INF/" + (legacyToml() ? "mods.toml" : "neoforge.mods.toml"), t.toString());
        if (legacyToml()) {
            ctx.files.json(res + "pack.mcmeta", J.obj("pack", J.obj("description", ctx.ns + " resources", "pack_format", ctx.v.resourceFormat)));
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

    /** Names of project items/blocks assigned to a creative tab key. */
    private List<String> tabMembers(String key) {
        List<String> r = new ArrayList<>();
        for (Element e : ctx.p.elements) {
            if (e instanceof Item it && it.creativeTab.equals(key)) r.add(it.name);
            if (e instanceof Block b && b.creativeTab.equals(key)) r.add(b.name);
        }
        return r;
    }

    private void mainClass() {
        String main = ctx.mainClass;
        Src s = new Src(ctx.pkg);
        s.imp("net.minecraft.resources.ResourceLocation", "net.minecraft.world.item.CreativeModeTabs");
        if (neo) {
            s.imp("net.neoforged.bus.api.IEventBus", "net.neoforged.fml.common.Mod", "net.neoforged.neoforge.common.NeoForge",
                    "net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent");
        } else {
            s.imp("net.minecraftforge.common.MinecraftForge", "net.minecraftforge.event.BuildCreativeModeTabContentsEvent",
                    eventBus7() ? "net.minecraftforge.eventbus.api.bus.BusGroup" : "net.minecraftforge.eventbus.api.IEventBus",
                    "net.minecraftforge.fml.common.Mod", "net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext");
        }
        s.l("@Mod(" + main + ".MODID)");
        s.l("public class " + main + " {");
        s.l("public static final String MODID = " + Src.str(ctx.ns) + ";");
        s.blank();
        if (neo) s.l("public " + main + "(IEventBus modBus) {");
        else if (eventBus7()) {
            // Forge 56+ (Minecraft 1.21.6+) uses EventBus 7: bus groups and a per-event bus
            s.l("public " + main + "(FMLJavaModLoadingContext context) {");
            s.l("BusGroup modBus = context.getModBusGroup();");
        } else {
            s.l("public " + main + "() {");
            s.l("IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();");
        }
        s.l("ModBlocks.BLOCKS.register(modBus);");
        s.l("ModItems.ITEMS.register(modBus);");
        s.l("ModTabs.TABS.register(modBus);");
        s.l("ModSounds.SOUNDS.register(modBus);");
        s.l(eventBus7() ? "BuildCreativeModeTabContentsEvent.getBus(modBus).addListener(this::addToVanillaTabs);"
                : "modBus.addListener(this::addToVanillaTabs);");
        s.l((neo ? "NeoForge" : "MinecraftForge") + ".EVENT_BUS.register(ModEvents.class);");
        s.l("}");
        s.blank();
        s.l("public static ResourceLocation id(String path) {");
        s.l("return " + GenContext.rlNew(ctx.singular(), "MODID", "path") + ";");
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
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModEvents.java", s.toString());
    }

    private void worldgen() {
        for (Block b : ctx.all(Block.class)) {
            if (!b.oreGen) continue;
            String dir = neo ? "neoforge/biome_modifier" : "forge/biome_modifier";
            JsonObject mod = J.obj("type", neo ? "neoforge:add_features" : "forge:add_features",
                    "biomes", DataGen.biomeTag(b), "features", ctx.id(DataGen.oreFeature(b)), "step", "underground_ores");
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
