package modstudio.gen;

import com.google.gson.JsonObject;
import modstudio.core.*;
import modstudio.core.Elements.*;

import java.util.ArrayList;
import java.util.List;

/** Fabric (Loom, official Mojang mappings). Output also runs on Quilt. */
public final class FabricGen {
    private final GenContext ctx;
    private final JavaCommon jc;
    private final HooksGen hooks;
    private final String res = "src/main/resources/";

    public FabricGen(GenContext ctx) {
        this.ctx = ctx;
        this.jc = new JavaCommon(ctx, JavaCommon.Loader.FABRIC);
        this.hooks = new HooksGen(ctx, jc, JavaCommon.Loader.FABRIC);
    }

    private boolean needsClient() {
        for (Block b : ctx.all(Block.class)) if (b.cutout()) return true;
        return hooks.hasClient();
    }

    public void generate() {
        buildFiles();
        metadata();
        mainClass();
        if (needsClient()) clientClass();
        registries();
        events();
        hooks.classes(false);
        jc.procedures();
        if (jc.hasArmor()) jc.armorMaterials();
        if (jc.hasEffects()) jc.effects();
        if (jc.hasPotions()) jc.potions();
        if (jc.codePaintings()) jc.paintings();
        if (jc.hasCommands()) jc.commands();
        for (Item it : ctx.all(Item.class)) if (jc.needsClass(it)) jc.itemClass(it);
        for (Block bl : ctx.all(Block.class)) if (jc.needsBlockClass(bl)) jc.blockClass(bl);
        new DataGen(ctx, res, true, true).generate(true);
        new AssetGen(ctx, res).modAssets(false);
        JavaCommon.codeFiles(ctx);
    }

    private void buildFiles() {
        ctx.files.text("gradle.properties", """
                org.gradle.jvmargs=-Xmx2G
                org.gradle.parallel=true
                minecraft_version=%s
                loader_version=%s
                fabric_version=%s
                mod_version=%s
                maven_group=%s
                archives_base_name=%s
                """.formatted(ctx.v.id, ctx.version("loader"), ctx.version("fabric_api"), ctx.p.meta.version, ctx.pkg, ctx.ns));
        ctx.files.text("gradle/wrapper/gradle-wrapper.properties", Scaffold.wrapperProperties(ctx.version("gradle")));
        ctx.files.text("settings.gradle", """
                pluginManagement {
                    repositories {
                        maven {
                            name = 'Fabric'
                            url = 'https://maven.fabricmc.net/'
                        }
                        mavenCentral()
                        gradlePluginPortal()
                    }
                }

                rootProject.name = '%s'
                """.formatted(ctx.ns));
        ctx.files.text("build.gradle", """
                plugins {
                    id 'fabric-loom' version '%s'
                }

                version = project.mod_version
                group = project.maven_group

                base {
                    archivesName = project.archives_base_name
                }

                dependencies {
                    minecraft "com.mojang:minecraft:${project.minecraft_version}"
                    mappings loom.officialMojangMappings()
                    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
                    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
                }

                tasks.withType(JavaCompile).configureEach {
                    it.options.release = %d
                    it.options.encoding = 'UTF-8'
                }

                java {
                    sourceCompatibility = JavaVersion.VERSION_%d
                    targetCompatibility = JavaVersion.VERSION_%d
                }
                """.formatted(ctx.version("loom"), ctx.v.java, ctx.v.java, ctx.v.java));
        ctx.files.text(".gitignore", Scaffold.GITIGNORE);
    }

    private void metadata() {
        Project.Meta m = ctx.p.meta;
        JsonObject entry = J.obj("main", J.arr(ctx.pkg + "." + ctx.mainClass));
        if (needsClient()) entry.add("client", J.arr(ctx.pkg + "." + ctx.mainClass + "Client"));
        List<String> authors = new ArrayList<>();
        for (String a : m.authors.split(",")) if (!a.isBlank()) authors.add(a.trim());
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
                "depends", J.obj("fabricloader", ">=0.15.0", "minecraft", ctx.v.id, "java", ">=" + ctx.v.java, "fabric-api", "*"));
        if (!m.website.isBlank()) o.add("contact", J.obj("homepage", m.website));
        if (ctx.p.texture(m.icon) != null) {
            o.addProperty("icon", "assets/" + ctx.ns + "/icon.png");
            jc.writeIcon(res + "assets/" + ctx.ns + "/icon.png");
        }
        ctx.files.json(res + "fabric.mod.json", o);
    }

    private void mainClass() {
        String main = ctx.mainClass;
        Src s = new Src(ctx.pkg);
        s.imp("net.fabricmc.api.ModInitializer", "net.minecraft.resources.ResourceLocation");
        s.l("public class " + main + " implements ModInitializer {");
        s.l("public static final String MODID = " + Src.str(ctx.ns) + ";");
        s.blank();
        s.l("@Override");
        s.l("public void onInitialize() {");
        if (!ctx.all(Elements.ArmorMaterial.class).isEmpty() && ctx.components() && !ctx.registryKeys()) s.l("ModArmorMaterials.init();");
        if (jc.hasEffects()) s.l("ModEffects.init();");
        if (jc.hasPotions()) s.l("ModPotions.init();");
        if (jc.codePaintings()) s.l("ModPaintings.init();");
        s.l("ModBlocks.init();");
        s.l("ModItems.init();");
        s.l("ModTabs.init();");
        s.l("ModSounds.init();");
        s.l("ModEvents.init();");
        s.l("}");
        s.blank();
        s.l("public static ResourceLocation id(String path) {");
        s.l("return " + GenContext.rlNew(ctx.singular(), "MODID", "path") + ";");
        s.l("}");
        s.blank();
        JavaCommon.registryKeyHelpers(ctx, s);
        s.l("}");
        ctx.files.text(ctx.javaDir() + main + ".java", s.toString());
    }

    private void clientClass() {
        Src s = new Src(ctx.pkg);
        boolean sectionLayers = jc.toolProps();
        // Fabric API for 1.21.6+ moved the render layer map to the rendering module and uses ChunkSectionLayer.
        s.imp("net.fabricmc.api.ClientModInitializer", sectionLayers
                        ? "net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap"
                        : "net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap",
                sectionLayers ? "net.minecraft.client.renderer.chunk.ChunkSectionLayer" : "net.minecraft.client.renderer.RenderType");
        s.l("public class " + ctx.mainClass + "Client implements ClientModInitializer {");
        s.l("@Override");
        s.l("public void onInitializeClient() {");
        for (Block b : ctx.all(Block.class)) {
            if (!b.cutout()) continue;
            if (sectionLayers) s.l("BlockRenderLayerMap.putBlock(" + jc.blockRef(b.name) + ", ChunkSectionLayer.CUTOUT);");
            else s.l("BlockRenderLayerMap.INSTANCE.putBlock(" + jc.blockRef(b.name) + ", RenderType.cutout());");
        }
        if (hooks.hasClient()) s.l("ModClient.init();");
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + ctx.mainClass + "Client.java", s.toString());
    }

    private List<String> tabMembers(String key) {
        List<String> r = new ArrayList<>();
        for (Element e : ctx.p.elements) {
            if (e instanceof Item it && it.creativeTab.equals(key)) r.add(it.name);
            if (e instanceof Block b && b.creativeTab.equals(key)) r.add(b.name);
        }
        return r;
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
        i.blank().l("private ModItems() {").l("}").blank();
        i.l("private static Item register(String name, Item item) {");
        i.l("return Registry.register(BuiltInRegistries.ITEM, " + main + ".id(name), item);");
        i.l("}").blank();
        i.l("public static void init() {");
        boolean anyTab = false;
        for (var tab : Vanilla.CREATIVE_TABS.entrySet()) {
            List<String> members = tabMembers(tab.getKey());
            if (members.isEmpty()) continue;
            anyTab = true;
            i.l("ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs." + tab.getValue() + ").register(entries -> {");
            for (String n : members) i.l("entries.accept(" + jc.itemRef(n) + ");");
            i.l("});");
        }
        if (anyTab) i.imp("net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents", "net.minecraft.world.item.CreativeModeTabs");
        List<Item> fuels = ctx.all(Item.class).stream().filter(it -> it.burnTime > 0).toList();
        if (!fuels.isEmpty() && ctx.registryKeys()) {
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
            t.imp("net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup", "net.minecraft.network.chat.Component",
                    "net.minecraft.world.item.CreativeModeTab", "net.minecraft.world.item.ItemStack");
            t.l("public static final CreativeModeTab " + Ids.toConst(tab.name) + " = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, "
                    + main + ".id(" + Src.str(tab.name) + "), FabricItemGroup.builder()");
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
                "net.minecraft.resources.ResourceLocation", "net.minecraft.sounds.SoundEvent");
        so.l("public final class ModSounds {");
        for (Sound s : ctx.all(Sound.class)) {
            so.l("public static final SoundEvent " + Ids.toConst(s.name) + " = register(" + Src.str(s.name) + ");");
        }
        so.blank().l("private ModSounds() {").l("}").blank();
        so.l("private static SoundEvent register(String name) {");
        so.l("ResourceLocation id = " + main + ".id(name);");
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
            if (!b.oreGen) continue;
            s.imp("net.fabricmc.fabric.api.biome.v1.BiomeModifications", "net.fabricmc.fabric.api.biome.v1.BiomeSelectors",
                    "net.minecraft.core.registries.Registries", "net.minecraft.resources.ResourceKey",
                    "net.minecraft.world.level.levelgen.GenerationStep");
            String sel = switch (b.oreDimension) {
                case "nether" -> "foundInTheNether()";
                case "end" -> "foundInTheEnd()";
                default -> "foundInOverworld()";
            };
            s.l("BiomeModifications.addFeature(BiomeSelectors." + sel + ", GenerationStep.Decoration.UNDERGROUND_ORES,");
            s.l("        ResourceKey.create(Registries.PLACED_FEATURE, " + ctx.mainClass + ".id(" + Src.str(DataGen.oreFeature(b)) + ")));");
        }
        hooks.fabricInit(s);
        s.l("}");
        s.l("}");
        ctx.files.text(ctx.javaDir() + "ModEvents.java", s.toString());
    }
}
