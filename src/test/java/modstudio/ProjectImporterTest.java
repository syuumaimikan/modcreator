package modstudio;

import modstudio.core.*;
import modstudio.core.Elements.*;
import modstudio.core.ModElements.*;
import modstudio.core.Systems.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

final class ProjectImporterTest {

    private static void zipText(ZipOutputStream zos, String path, String content) throws Exception {
        zos.putNextEntry(new ZipEntry(path));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static void zipBytes(ZipOutputStream zos, String path, byte[] bytes) throws Exception {
        zos.putNextEntry(new ZipEntry(path));
        zos.write(bytes);
        zos.closeEntry();
    }

    @Test
    void importsFabricModJarWithAssetsDataLangsAndMixins(@TempDir Path dir) throws Exception {
        Path jar = dir.resolve("crystalcraft-1.2.0.jar");
        byte[] png16 = Images.png(TextureTemplates.generate("gem", 0xFF2FA4FF), 16, 16);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
            zipText(zos, "fabric.mod.json", """
                    {
                      "schemaVersion": 1,
                      "id": "crystalcraft",
                      "version": "1.2.0",
                      "name": "Crystal Craft",
                      "description": "Adds crystals and tools",
                      "authors": ["Alice"],
                      "license": "MIT",
                      "icon": "assets/crystalcraft/icon.png",
                      "entrypoints": { "main": ["net.alice.crystal.CrystalMod"] },
                      "depends": { "minecraft": "1.21.1", "cloth-config": "*" }
                    }
                    """);
            zipBytes(zos, "assets/crystalcraft/icon.png", png16);
            zipBytes(zos, "assets/crystalcraft/textures/item/sky_gem.png", png16);
            zipBytes(zos, "assets/crystalcraft/textures/block/sky_ore.png", png16);
            zipText(zos, "assets/crystalcraft/textures/block/sky_ore.png.mcmeta", "{\"animation\":{\"frametime\":4}}");
            zipText(zos, "assets/crystalcraft/lang/en_us.json", """
                    {
                      "item.crystalcraft.sky_gem": "Sky Gem",
                      "block.crystalcraft.sky_ore": "Sky Ore"
                    }
                    """);
            zipText(zos, "assets/crystalcraft/lang/ja_jp.json", """
                    {
                      "item.crystalcraft.sky_gem": "空の宝石",
                      "block.crystalcraft.sky_ore": "空の鉱石"
                    }
                    """);
            zipText(zos, "assets/crystalcraft/blockstates/sky_ore.json", "{\"variants\":{\"\":{\"model\":\"crystalcraft:block/sky_ore\"}}}");
            zipText(zos, "assets/crystalcraft/models/block/sky_ore.json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"crystalcraft:block/sky_ore\"}}");
            zipText(zos, "assets/crystalcraft/models/item/sky_gem.json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"crystalcraft:item/sky_gem\"}}");
            zipText(zos, "crystalcraft.mixins.json", "{\"package\":\"net.alice.crystal.mixin\",\"client\":[\"GuiMixin\"]}");
            zipText(zos, "data/crystalcraft/recipe/gem_craft.json", """
                    {
                      "type": "minecraft:crafting_shapeless",
                      "ingredients": [{"item": "minecraft:diamond"}],
                      "result": {"id": "crystalcraft:sky_gem", "count": 2}
                    }
                    """);
            zipText(zos, "data/minecraft/tags/function/load.json", "{\"values\":[\"crystalcraft:startup\"]}");
            zipText(zos, "data/crystalcraft/function/startup.mcfunction", "say Crystal Craft ready!");
            zipBytes(zos, "net/alice/crystal/CrystalMod.class", new byte[]{0x1});
        }

        ProjectImporter.Report rep = ProjectImporter.importArtifact(jar);
        Project p = rep.project();
        assertEquals(ProjectScope.Kind.MOD, ProjectScope.kind(p));
        assertEquals("crystalcraft", p.meta.modId);
        assertEquals("Crystal Craft", p.meta.name);
        assertEquals("1.2.0", p.meta.version);
        assertEquals("net.alice.crystal", p.meta.packageName);
        assertTrue(p.meta.targets.contains("fabric"));
        assertTrue(p.meta.mcVersions.contains("1.21.1"));
        assertTrue(p.meta.languages.contains("ja_jp"));
        assertEquals("cloth-config", p.meta.dependencies.get(0).modId);

        Elements.Block ore = p.all(Elements.Block.class).get(0);
        assertEquals("sky_ore", ore.name);
        assertEquals("Sky Ore", ore.displayName);
        assertEquals("空の鉱石", ore.loc("name", "ja_jp"));
        assertEquals(4, p.texture(ore.texture).frameTime);

        Elements.Item gem = p.all(Elements.Item.class).get(0);
        assertEquals("sky_gem", gem.name);
        assertEquals("Sky Gem", gem.displayName);
        assertEquals("空の宝石", gem.loc("name", "ja_jp"));

        assertEquals(1, p.all(MixinSpec.class).size());
        assertEquals("client", p.all(MixinSpec.class).get(0).side);
        assertEquals(1, p.all(Elements.Recipe.class).size());
        assertTrue(p.all(Elements.Function.class).get(0).onLoad);
        assertFalse(rep.notes().isEmpty());
    }

    @Test
    void importsWrappedDatapackAndResourcepackAndFoliaPlugin(@TempDir Path dir) throws Exception {
        // 1. Wrapped datapack zip (with nested root folder & vanilla loot override)
        Path dpZip = dir.resolve("cool-datapack.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(dpZip))) {
            zipText(zos, "cool-dp/pack.mcmeta", "{\"pack\":{\"min_format\":[88,0],\"max_format\":[88,0],\"description\":\"Cool DP\"}}");
            zipText(zos, "cool-dp/data/cooldp/function/tick.mcfunction", "execute as @a run effect give @s minecraft:speed 1 0 true");
            zipText(zos, "cool-dp/data/minecraft/tags/function/tick.json", "{\"values\":[\"cooldp:tick\"]}");
            zipText(zos, "cool-dp/data/minecraft/loot_table/chests/simple_dungeon.json", "{\"type\":\"minecraft:chest\",\"pools\":[]}");
            zipText(zos, "cool-dp/data/cooldp/worldgen/biome/neon_plains.json", "{\"temperature\":0.9,\"downfall\":0.1,\"has_precipitation\":false,\"effects\":{\"sky_color\":16711935}}");
        }
        Project dp = ProjectImporter.importArtifact(dpZip).project();
        assertEquals(ProjectScope.Kind.DATAPACK, ProjectScope.kind(dp));
        assertEquals("cooldp", dp.meta.modId);
        assertEquals("1.21.9", dp.meta.mcVersion);
        assertTrue(dp.all(Elements.Function.class).get(0).onTick);
        assertEquals("minecraft:chests/simple_dungeon", dp.all(LootTable.class).get(0).replaces);
        assertEquals("#FF00FF", dp.all(Biome.class).get(0).sky);

        // 2. Folia plugin jar
        Path pluginJar = dir.resolve("myplugin.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(pluginJar))) {
            zipText(zos, "plugin.yml", """
                    name: SkyPlugin
                    version: 2.0.0
                    main: org.sky.SkyMain
                    api-version: '1.21.8'
                    folia-supported: true
                    depend: [Vault]
                    """);
            zipText(zos, "datapack/data/skyplugin/function/hello.mcfunction", "say Hi from Folia");
        }
        Project pl = ProjectImporter.importArtifact(pluginJar).project();
        assertEquals(ProjectScope.Kind.PLUGIN, ProjectScope.kind(pl));
        assertTrue(pl.meta.targets.contains("folia"));
        assertEquals("org.sky", pl.meta.packageName);
        assertEquals("Vault", pl.meta.dependencies.get(0).modId);
        assertEquals(1, pl.all(Elements.Function.class).size());

        // 3. Resourcepack folder with post shader & CLI --import
        Path rpDir = dir.resolve("my_rp");
        Files.createDirectories(rpDir.resolve("assets/minecraft/shaders/program"));
        Files.writeString(rpDir.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":46,\"description\":\"Glow shaders\"}}");
        Files.writeString(rpDir.resolve("assets/minecraft/shaders/program/phosphor.fsh"), "#version 150\nvoid main() {}\n");

        Path outProj = dir.resolve("imported_rp.msproj");
        assertEquals(0, Cli.run(new String[]{"--import", rpDir.toString(), outProj.toString()}));
        Project rpLoaded = ProjectIO.load(outProj);
        assertEquals(ProjectScope.Kind.RESOURCEPACK, ProjectScope.kind(rpLoaded));
        assertEquals(1, rpLoaded.all(Shader.class).size());
        assertEquals("post", rpLoaded.all(Shader.class).get(0).kind);
    }

    @Test
    void importsBedrockMcaddonWithScriptsAndTextures(@TempDir Path dir) throws Exception {
        Path addon = dir.resolve("test.mcaddon");
        byte[] png16 = Images.png(TextureTemplates.generate("sword", 0xFF3FB950), 16, 16);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(addon))) {
            zipText(zos, "test_BP/manifest.json", "{\"format_version\":2,\"header\":{\"name\":\"Test Addon BP\",\"description\":\"Bedrock test\",\"version\":[1,2,3]}}");
            zipText(zos, "test_BP/scripts/main.js", "import { world } from '@minecraft/server';\n");
            zipText(zos, "test_BP/functions/init.mcfunction", "say Hello Bedrock");
            zipText(zos, "test_RP/manifest.json", "{\"format_version\":2,\"header\":{\"name\":\"Test Addon RP\",\"version\":[1,2,3]}}");
            zipBytes(zos, "test_RP/textures/items/emerald_blade.png", png16);
            zipText(zos, "test_RP/texts/en_US.lang", "item.test:emerald_blade=Emerald Blade\n");
        }
        Project p = ProjectImporter.importArtifact(addon).project();
        assertEquals(ProjectScope.Kind.BEDROCK, ProjectScope.kind(p));
        assertTrue(p.meta.targets.contains("bedrock"));
        assertFalse(p.textures.isEmpty());
        assertEquals(1, p.all(Elements.Function.class).size());
        assertEquals(1, p.all(CodeFile.class).size());
    }

    @Test
    void importsModJarsAndGradleSourcesWithoutResourcePackMisclassification(@TempDir Path dir) throws Exception {
        byte[] png16 = Images.png(TextureTemplates.generate("sword", 0xFF58A6FF), 16, 16);

        // 1. Mixed-case meta-inf/mods.toml NeoForge 1.20.4 mod with assets only & pack.mcmeta
        Path neoJar = dir.resolve("myneo-1.0.0.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(neoJar))) {
            zipText(zos, "pack.mcmeta", "{\"pack\":{\"pack_format\":26,\"description\":\"Neo mod resources\"}}");
            zipText(zos, "meta-inf/mods.toml", """
                    modLoader="javafml"
                    license="MIT"
                    [[mods]]
                    modId="myneo"
                    version="1.0.0"
                    displayName="My Neo Mod"
                    [[dependencies.myneo]]
                    modId="neoforge"
                    mandatory=true
                    versionRange="[20.4,)"
                    """);
            zipBytes(zos, "assets/myneo/textures/item/wand.png", png16);
            zipBytes(zos, "com/example/myneo/NeoMain.class", new byte[]{0x1});
        }
        Project neo = ProjectImporter.importArtifact(neoJar).project();
        assertEquals(ProjectScope.Kind.MOD, ProjectScope.kind(neo));
        assertTrue(neo.meta.targets.contains("neoforge"));
        assertEquals("myneo", neo.meta.modId);

        // 2. Legacy Forge 1.12.2 mcmod.info mod with assets only & no data/
        Path legacyJar = dir.resolve("legacy-forge-mod.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(legacyJar))) {
            zipText(zos, "mcmod.info", """
                    [
                      {
                        "modid": "legacygem",
                        "name": "Legacy Gem",
                        "description": "Old Forge mod",
                        "version": "1.2.3",
                        "mcversion": "1.12.2",
                        "authorList": ["RetroDev"],
                      }
                    ]
                    """);
            zipBytes(zos, "assets/legacygem/textures/item/ruby.png", png16);
            zipBytes(zos, "net/retro/legacygem/LegacyMod.class", new byte[]{0x1});
        }
        Project legacy = ProjectImporter.importArtifact(legacyJar).project();
        assertEquals(ProjectScope.Kind.MOD, ProjectScope.kind(legacy));
        assertTrue(legacy.meta.targets.contains("forge"));
        assertEquals("legacygem", legacy.meta.modId);
        assertEquals("Legacy Gem", legacy.meta.name);

        // 3. Descriptor-less .jar with .class, assets/, .mixins.json, and non-Bedrock assets/manifest.json
        Path bareModJar = dir.resolve("client-addon.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(bareModJar))) {
            zipText(zos, "pack.mcmeta", "{\"pack\":{\"pack_format\":34,\"description\":\"Client mod\"}}");
            zipText(zos, "clientmod.mixins.json", "{\"package\":\"org.clientmod.mixin\",\"client\":[\"HudMixin\"]}");
            zipText(zos, "assets/clientmod/manifest.json", "{\"custom\":true}");
            zipBytes(zos, "assets/clientmod/textures/item/badge.png", png16);
            zipBytes(zos, "org/clientmod/ClientInit.class", new byte[]{0x1});
        }
        Project bare = ProjectImporter.importArtifact(bareModJar).project();
        assertEquals(ProjectScope.Kind.MOD, ProjectScope.kind(bare));
        assertFalse(bare.meta.targets.contains("bedrock"));
        assertFalse(bare.meta.targets.contains("resourcepack"));
        assertTrue(bare.meta.targets.contains("fabric"));

        // 4. Nested Jar-in-Jar mod descriptor inside META-INF/jars/inner.jar
        ByteArrayOutputStream innerBuf = new ByteArrayOutputStream();
        try (ZipOutputStream innerZos = new ZipOutputStream(innerBuf)) {
            zipText(innerZos, "fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"innerlib\",\"version\":\"1.0.0\"}");
        }
        Path jijJar = dir.resolve("wrapper-jij.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jijJar))) {
            zipText(zos, "pack.mcmeta", "{\"pack\":{\"pack_format\":34,\"description\":\"Wrapped\"}}");
            zipBytes(zos, "META-INF/jars/innerlib.jar", innerBuf.toByteArray());
            zipBytes(zos, "assets/wrapper/textures/item/icon.png", png16);
        }
        Project jij = ProjectImporter.importArtifact(jijJar).project();
        assertEquals(ProjectScope.Kind.MOD, ProjectScope.kind(jij));
        assertTrue(jij.meta.targets.contains("fabric"));

        // 5. Gradle source workspace folder (src/main/resources + src/main/java)
        Path gradleDir = dir.resolve("gradle-mod-src");
        Files.createDirectories(gradleDir.resolve("src/main/resources/assets/gradledemo/textures/item"));
        Files.createDirectories(gradleDir.resolve("src/main/java/net/demo"));
        Files.writeString(gradleDir.resolve("build.gradle"), "plugins { id 'fabric-loom' }\n");
        Files.writeString(gradleDir.resolve("src/main/resources/fabric.mod.json"), """
                // lenient comment in mod json
                {
                  "schemaVersion": 1,
                  "id": "gradledemo",
                  "version": "0.5.0",
                  "name": "Gradle Demo",
                }
                """);
        Files.write(gradleDir.resolve("src/main/resources/assets/gradledemo/textures/item/coin.png"), png16);
        Files.writeString(gradleDir.resolve("src/main/java/net/demo/DemoMod.java"), "package net.demo; public class DemoMod {}");

        Project gradleProj = ProjectImporter.importArtifact(gradleDir).project();
        assertEquals(ProjectScope.Kind.MOD, ProjectScope.kind(gradleProj));
        assertTrue(gradleProj.meta.targets.contains("fabric"));
        assertEquals("gradledemo", gradleProj.meta.modId);
        assertEquals("net.demo", gradleProj.meta.packageName);
        assertFalse(gradleProj.textures.isEmpty());
        assertEquals(1, gradleProj.all(CodeFile.class).size());

        // 6. MDK neoforge.mods.toml with #mandatory comments & multiline ''' description
        Path mdkJar = dir.resolve("jei-1.21.1-neoforge-sample.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(mdkJar))) {
            zipText(zos, "META-INF/neoforge.mods.toml", """
                    modLoader="javafml" #mandatory
                    license="MIT"
                    [[mods]] #mandatory
                    modId="jei" #mandatory
                    version="19.70.0.469" #mandatory
                    displayName="Just Enough Items" #mandatory
                    authors="mezz" #optional
                    description='''
                    Item and recipe viewer for Minecraft.
                    Supports modern versions.
                    '''
                    [[dependencies.jei]]
                    modId="neoforge" #mandatory
                    type="required" #mandatory
                    versionRange="[21.1.238,)" #mandatory
                    [modproperties.jei]
                    catalogueImageIcon="jei-icon.png"
                    """);
            zipBytes(zos, "mezz/jei/JustEnoughItems.class", new byte[]{0x1});
        }
        Project mdk = ProjectImporter.importArtifact(mdkJar).project();
        assertEquals(ProjectScope.Kind.MOD, ProjectScope.kind(mdk));
        assertTrue(mdk.meta.targets.contains("neoforge"));
        assertEquals("jei", mdk.meta.modId);
        assertEquals("Just Enough Items", mdk.meta.name);
        assertEquals("19.70.0.469", mdk.meta.version);
        assertTrue(mdk.meta.description.contains("Item and recipe viewer"));
    }
}

