package modstudio;

import modstudio.core.*;
import modstudio.core.Elements.*;
import modstudio.gen.GenContext;
import modstudio.gen.Generators;
import modstudio.gen.ModPatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

final class UpgradeAndPatchTest {

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
    void nestedAdvancementParentsAndSpecialOrConditionalRecipesValidateCleanly(@TempDir Path dir) throws Exception {
        Path jar = dir.resolve("richmod-1.0.jar");
        byte[] png16 = Images.png(TextureTemplates.generate("gem", 0xFF33AAFF), 16, 16);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
            zipText(zos, "fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"richmod\",\"version\":\"1.0.0\",\"name\":\"Rich Mod\",\"depends\":{\"minecraft\":\"1.20.4\"}}");
            zipBytes(zos, "assets/richmod/textures/item/ruby.png", png16);
            zipText(zos, "assets/richmod/models/item/ruby.json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"richmod:item/ruby\"}}");

            // Nested subfolder advancements + recipe unlock referencing missing local recipes/root + un-namespaced vanilla root
            zipText(zos, "data/richmod/advancements/story/root.json",
                    "{\"display\":{\"icon\":{\"item\":\"richmod:ruby\"},\"title\":\"Story\",\"description\":\"Root\",\"background\":\"minecraft:textures/block/stone.png\"},\"criteria\":{\"tick\":{\"trigger\":\"minecraft:tick\"}}}");
            zipText(zos, "data/richmod/advancements/story/get_ruby.json",
                    "{\"parent\":\"richmod:story/root\",\"display\":{\"icon\":{\"item\":\"richmod:ruby\"},\"title\":\"Get Ruby\",\"description\":\"Obtain\"},\"criteria\":{\"got\":{\"trigger\":\"minecraft:inventory_changed\",\"conditions\":{\"items\":[{\"items\":[\"richmod:ruby\"]}]}}}}");
            zipText(zos, "data/richmod/advancements/adventure_link.json",
                    "{\"parent\":\"adventure/root\",\"display\":{\"icon\":{\"item\":\"richmod:ruby\"},\"title\":\"Venture\",\"description\":\"Go\"},\"criteria\":{\"tick\":{\"trigger\":\"minecraft:tick\"}}}");
            zipText(zos, "data/richmod/advancements/recipes/misc/ruby_unlock.json",
                    "{\"parent\":\"richmod:recipes/root\",\"criteria\":{\"has\":{\"trigger\":\"minecraft:tick\"}},\"rewards\":{\"recipes\":[\"richmod:ruby\"]}}");

            // Recipes: special, smithing_trim, forge:conditional wrapper, custom serializer, and result-less json
            zipText(zos, "data/richmod/recipes/special_dye.json", "{\"type\":\"minecraft:crafting_special_armordye\"}");
            zipText(zos, "data/richmod/recipes/trim_tmpl.json", "{\"type\":\"minecraft:smithing_trim\",\"template\":{\"item\":\"richmod:ruby\"},\"base\":{\"tag\":\"minecraft:trimmable_armor\"},\"addition\":{\"item\":\"minecraft:diamond\"}}");
            zipText(zos, "data/richmod/recipes/cond_ruby.json", """
                    {
                      "type": "forge:conditional",
                      "recipes": [
                        {
                          "conditions": [],
                          "recipe": {
                            "type": "minecraft:crafting_shaped",
                            "pattern": [" D ", "DRD", " D "],
                            "key": { "D": {"item": "minecraft:diamond"}, "R": {"item": "richmod:ruby"} },
                            "result": { "item": "richmod:ruby", "count": 2 }
                          }
                        }
                      ]
                    }
                    """);
            zipText(zos, "data/richmod/recipes/crushing_ruby.json",
                    "{\"type\":\"create:crushing\",\"ingredients\":[{\"item\":\"minecraft:diamond\"}],\"results\":[{\"item\":\"richmod:ruby\"}]}");
        }

        Project p = ProjectImporter.importArtifact(jar).project();
        List<Validator.Problem> errors = Validator.validate(p).stream()
                .filter(pr -> pr.severity() == Validator.Severity.ERROR)
                .toList();
        assertTrue(errors.isEmpty(), () -> "Expected no validation errors but got: " + errors);

        Advancement getRuby = (Advancement) p.byName(ElementType.ADVANCEMENT, "story/get_ruby");
        assertNotNull(getRuby);
        assertEquals("richmod:story/root", getRuby.parent);
        assertEquals("richmod:ruby", getRuby.triggerValue);

        Advancement venture = (Advancement) p.byName(ElementType.ADVANCEMENT, "adventure_link");
        assertNotNull(venture);
        assertEquals("minecraft:adventure/root", venture.parent);

        assertEquals(1, p.all(Recipe.class).size());
        Recipe r = p.all(Recipe.class).get(0);
        assertEquals("richmod:ruby", r.result);
        assertEquals(2, r.count);

        // Special & custom recipes preserved as editable JSON CodeFiles
        List<CodeFile> preserved = p.all(CodeFile.class);
        assertTrue(preserved.stream().anyMatch(cf -> cf.path.endsWith("special_dye.json")));
        assertTrue(preserved.stream().anyMatch(cf -> cf.path.endsWith("trim_tmpl.json")));
        assertTrue(preserved.stream().anyMatch(cf -> cf.path.endsWith("crushing_ruby.json")));
    }

    @Test
    void upgradesLegacy112ModTo1211NeoForgeAndMigratesAssetsAndData(@TempDir Path dir) throws Exception {
        Path jar = dir.resolve("oldforge-1.12.2.jar");
        byte[] png16 = Images.png(TextureTemplates.generate("brick", 0xFFD24D57), 16, 16);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
            zipText(zos, "mcmod.info", "[{\"modid\":\"oldmod\",\"name\":\"Old Mod\",\"version\":\"1.0\",\"mcversion\":\"1.12.2\"}]");
            zipText(zos, "pack.mcmeta", "{\"pack\":{\"pack_format\":3,\"description\":\"Old Forge\"}}");
            zipText(zos, "assets/oldmod/lang/en_us.lang", "tile.old_brick.name=Old Brick\nitem.old_gem.name=Old Gem\n");
            zipBytes(zos, "assets/oldmod/textures/blocks/old_brick.png", png16);
            zipBytes(zos, "assets/oldmod/textures/items/old_gem.png", png16);
            zipText(zos, "assets/oldmod/blockstates/old_brick.json", "{\"variants\":{\"normal\":{\"model\":\"oldmod:old_brick\"}}}");
            zipText(zos, "assets/oldmod/models/block/old_brick.json", "{\"parent\":\"block/cube_all\",\"textures\":{\"all\":\"oldmod:blocks/old_brick\"}}");
            zipText(zos, "assets/oldmod/models/item/old_gem.json", "{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"oldmod:items/old_gem\"}}");
            zipText(zos, "data/oldmod/recipes/old_craft.json",
                    "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:grass\"},{\"item\":\"minecraft:scute\"}],\"result\":{\"item\":\"oldmod:old_gem\",\"count\":1}}");
            zipText(zos, "data/oldmod/recipes/custom_special.json",
                    "{\"type\":\"custommod:infuser\",\"input\":{\"item\":\"minecraft:grass\"},\"result\":{\"item\":\"oldmod:old_gem\"}}");
        }

        Project p = ProjectImporter.importArtifact(jar).project();
        assertEquals("1.12.2", p.meta.sourceMcVersion);
        Block b = (Block) p.byName(ElementType.BLOCK, "old_brick");
        Item it = (Item) p.byName(ElementType.ITEM, "old_gem");
        assertNotNull(b);
        assertNotNull(it);
        assertEquals("Old Brick", b.displayName);
        assertEquals("Old Gem", it.displayName);
        assertFalse(b.texture.isBlank());
        assertFalse(it.texture.isBlank());

        ProjectUpgrader.Report rep = ProjectUpgrader.upgrade(p, McVersion.V1_21_11, List.of(Target.NEOFORGE));
        assertTrue(rep.migratedIds() >= 2);
        assertEquals("1.21.11", p.meta.mcVersion);
        assertEquals(List.of("neoforge"), p.meta.targets);

        Recipe rc = p.all(Recipe.class).get(0);
        assertTrue(rc.grid.contains("minecraft:short_grass"));
        assertTrue(rc.grid.contains("minecraft:turtle_scute"));

        CodeFile cf = p.all(CodeFile.class).stream()
                .filter(c -> c.path.contains("custom_special.json"))
                .findFirst()
                .orElseThrow();
        assertTrue(cf.path.contains("src/main/resources/data/oldmod/recipe/"));
        assertTrue(cf.content.contains("\"minecraft:short_grass\""));
        assertTrue(cf.content.contains("\"id\": \"oldmod:old_gem\""));

        GenContext ctx = Generators.generate(p, new Build(Target.NEOFORGE, McVersion.V1_21_11));
        assertNotNull(ctx.files.get("src/main/resources/data/oldmod/recipe/custom_special.json"));
    }

    @Test
    void patchesOriginalModJarPreservingClassesLibrariesAndStrippingSignatures(@TempDir Path dir) throws Exception {
        Path jar = dir.resolve("compiled-mod.jar");
        byte[] png16 = Images.png(TextureTemplates.generate("sword", 0xFF3FB950), 16, 16);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
            zipText(zos, "fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"compmod\",\"version\":\"1.0.0\",\"name\":\"Compiled Mod\",\"entrypoints\":{\"main\":[\"net.comp.CompMod\"]},\"depends\":{\"minecraft\":\"1.21.1\"}}");
            zipBytes(zos, "net/comp/CompMod.class", new byte[]{0x10, 0x20, 0x30});
            zipBytes(zos, "META-INF/CERT.SF", new byte[]{0x1});
            zipBytes(zos, "META-INF/CERT.RSA", new byte[]{0x2});
            zipBytes(zos, "META-INF/jars/dep.jar", new byte[]{0x50, 0x4B});
            zipBytes(zos, "assets/compmod/textures/item/magic_blade.png", png16);
            zipText(zos, "assets/compmod/models/item/magic_blade.json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"compmod:item/magic_blade\"}}");
            zipText(zos, "assets/compmod/lang/en_us.json", "{\"item.compmod.magic_blade\":\"Magic Blade\"}");
            zipText(zos, "data/compmod/worldgen/placed_feature/ore_spot.json", "{\"feature\":\"compmod:ore_spot\",\"placement\":[]}");
        }

        Project p = ProjectImporter.importArtifact(jar).project();
        p.meta.name = "Edited Compiled Mod";
        p.meta.version = "1.0.1-edited";
        Item blade = (Item) p.byName(ElementType.ITEM, "magic_blade");
        assertNotNull(blade);
        blade.displayName = "Super Modified Blade";

        GenContext ctx = Generators.generate(p, new Build(Target.FABRIC, McVersion.V1_21_1));
        Path patchedJar = dir.resolve("compiled-mod-patched.jar");
        ModPatcher.Result res = ModPatcher.patchJar(p, ctx, jar, patchedJar);
        assertTrue(res.keptEntries() >= 2);
        assertTrue(res.patchedEntries() >= 1);

        try (ZipFile zf = new ZipFile(patchedJar.toFile(), StandardCharsets.UTF_8)) {
            assertNotNull(zf.getEntry("net/comp/CompMod.class"));
            assertNotNull(zf.getEntry("META-INF/jars/dep.jar"));
            assertNull(zf.getEntry("META-INF/CERT.SF"));
            assertNull(zf.getEntry("META-INF/CERT.RSA"));
            String fm = new String(zf.getInputStream(zf.getEntry("fabric.mod.json")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(fm.contains("Edited Compiled Mod"));
            assertTrue(fm.contains("net.comp.CompMod"));
            String lang = new String(zf.getInputStream(zf.getEntry("assets/compmod/lang/en_us.json")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(lang.contains("Super Modified Blade"));
            assertNotNull(zf.getEntry("data/compmod/worldgen/placed_feature/ore_spot.json"));
        }
    }

    @Test
    void handlesShadedPackagesAndReconciles112AssetsDataAcross121And261(@TempDir Path dir) throws Exception {
        byte[] png16 = Images.png(TextureTemplates.generate("gem", 0xFF7B61FF), 16, 16);

        // 1. Shaded library uppercase path + Fabric minecraft depends as JSON array
        Path shadedJar = dir.resolve("dh-like-fabric-neoforge.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(shadedJar))) {
            zipBytes(zos, "ShadedLib/libraries/electronwill/Config.class", new byte[]{0x1});
            zipBytes(zos, "com/example/dhorig/MainClient.class", new byte[]{0x1});
            zipText(zos, "fabric.mod.json", """
                    {
                      "schemaVersion": 1,
                      "id": "dhlike",
                      "version": "1.0.0",
                      "name": "DH Like",
                      "entrypoints": { "client": ["com.example.dhorig.MainClient"] },
                      "depends": { "minecraft": ["1.21", "1.21.1"] }
                    }
                    """);
            zipText(zos, "META-INF/neoforge.mods.toml", "[[mods]]\nmodId=\"dhlike\"\ndisplayName=\"DH Like\"\nversion=\"1.0.0\"");
        }
        Project dhProj = ProjectImporter.importArtifact(shadedJar).project();
        assertEquals("com.example.dhorig", dhProj.meta.packageName);
        assertTrue(Ids.PACKAGE.matcher(dhProj.meta.packageName).matches());
        assertTrue(dhProj.meta.targets.contains("fabric"));
        assertTrue(dhProj.meta.targets.contains("neoforge"));
        assertTrue(Validator.validate(dhProj).stream().noneMatch(pr -> pr.severity() == Validator.Severity.ERROR));

        // 2. 1.12.2 Forge mod with recipes/advancements inside assets/, OreDict, dye/hyphen/camelCase IDs -> upgrade to 26.1
        Path legacyJar = dir.resolve("botania-like-1.12.2.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(legacyJar))) {
            zipText(zos, "mcmod.info", "[{\"modid\":\"botlike\",\"name\":\"Bot Like\",\"version\":\"1.0\",\"mcversion\":\"1.12.2\"}]");
            zipBytes(zos, "vazkii/botlike/BotMain.class", new byte[]{0x1});
            zipBytes(zos, "assets/botlike/textures/items/mana_gun.png", png16);
            zipText(zos, "assets/botlike/models/item/up-grade.json", "{\"parent\":\"item/generated\"}");
            zipText(zos, "assets/botlike/recipes/gun_craft.json", """
                    {
                      "type": "forge:ore_shaped",
                      "pattern": ["ID ", " U "],
                      "key": {
                        "I": { "type": "forge:ore_dict", "ore": "ingotIron" },
                        "D": { "item": "minecraft:dye", "data": 0 },
                        "U": { "item": "botlike:up-grade" }
                      },
                      "result": { "item": "botlike:manaGun", "count": 1 }
                    }
                    """);
            zipText(zos, "assets/botlike/advancements/get_gun.json", """
                    {
                      "display": { "icon": { "item": "botlike:manaGun" }, "title": "Mana Gun", "description": "Crafted" },
                      "criteria": { "inv": { "trigger": "minecraft:inventory_changed", "conditions": { "items": [{ "item": "botlike:manaGun" }] } } }
                    }
                    """);
        }
        Project p = ProjectImporter.importArtifact(legacyJar).project();
        assertEquals("1.12.2", p.meta.sourceMcVersion);
        assertEquals(1, p.all(Recipe.class).size());
        assertEquals(1, p.all(Advancement.class).size());
        assertNotNull(p.byName(ElementType.ITEM, "managun"));
        assertNotNull(p.byName(ElementType.ITEM, "up_grade"));
        assertTrue(Validator.validate(p).stream().noneMatch(pr -> pr.severity() == Validator.Severity.ERROR));

        Recipe rc = p.all(Recipe.class).get(0);
        assertTrue(rc.grid.contains("#c:ingots/iron"));
        assertTrue(rc.grid.contains("botlike:up_grade"));

        ProjectUpgrader.upgrade(p, McVersion.V26_1, null);
        assertEquals("26.1", p.meta.mcVersion);
        assertEquals(List.of("neoforge"), p.meta.targets);
        assertTrue(rc.grid.contains("minecraft:white_dye"));
        assertTrue(Validator.validate(p).stream().noneMatch(pr -> pr.severity() == Validator.Severity.ERROR));

        GenContext ctx26 = Generators.generate(p, new Build(Target.NEOFORGE, McVersion.V26_1));
        assertFalse(ctx26.files.paths().isEmpty());
    }
}

