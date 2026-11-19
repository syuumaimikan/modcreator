package modstudio.core;

import modstudio.core.Elements.Animation;
import modstudio.core.Elements.Bone;
import modstudio.core.Elements.Cube;
import modstudio.core.Elements.Display;
import modstudio.core.Elements.Model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-crafted pixel art textures, animations, 3D weapon/shrine models, and rigged boss geometry
 * for the {@link RpgSampleProject} Action-RPG sample mod.
 */
final class RpgSampleArt {
    private RpgSampleArt() {}

    static final int TRANSPARENT = 0x00000000;

    static void addTextures(Project p) {
        List<TextureAsset> list = new ArrayList<>();

        // Crafting materials, currencies & elemental cores (Skyblock + Genshin vibe)
        list.add(tex("astral_shard", 16, 16, pixelGrid(new String[]{
                "................",
                ".......11.......",
                "......1551......",
                ".....145541.....",
                "....14566431....",
                "...1356665321...",
                "...1356553221...",
                "..123455432211..",
                "..12344432211...",
                "...123332211....",
                "....1222211.....",
                ".....12211......",
                "......121.......",
                ".......1........",
                "................",
                "................"
        }, Map.of(
                '1', 0xFF13294B, '2', 0xFF215D9E, '3', 0xFF2FA4E7,
                '4', 0xFF66E2FF, '5', 0xFFB8F6FF, '6', 0xFFFFFFFF))));

        list.add(tex("pyro_core", 16, 16, pixelGrid(new String[]{
                "................",
                ".....11..11.....",
                "....13411431....",
                "...1356446531...",
                "..124665566421..",
                "..124567765321..",
                "..123567764321..",
                "...1245665321...",
                "...1134554311...",
                "....12344321....",
                ".....123321.....",
                "......1221......",
                ".......11.......",
                "................",
                "................",
                "................"
        }, Map.of(
                '1', 0xFF3E0B0B, '2', 0xFF8E1D15, '3', 0xFFD93E1F,
                '4', 0xFFFF6D2E, '5', 0xFFFFA73B, '6', 0xFFFFDC6E, '7', 0xFFFFFFFF))));

        list.add(tex("anemo_feather", 16, 16, pixelGrid(new String[]{
                "................",
                "..........1111..",
                ".........145541.",
                "........1456541.",
                ".......1355441..",
                "......1355431...",
                ".....1245431....",
                "....1244321.....",
                "...1234321......",
                "..1g33221.......",
                "..1gg221........",
                ".1ggg11.........",
                ".1gg1...........",
                "1gg1............",
                "111.............",
                "................"
        }, Map.of(
                '1', 0xFF0E3D36, '2', 0xFF198574, '3', 0xFF2ED8B6,
                '4', 0xFF7AF2D8, '5', 0xFFC5FFF1, '6', 0xFFFFFFFF, 'g', 0xFFF4C55A))));

        list.add(tex("celestial_coin", 16, 16, pixelGrid(new String[]{
                "................",
                "....11111111....",
                "..114555543211..",
                ".1455333333221..",
                ".1433226622321..",
                ".1332666666221..",
                ".1332266662211..",
                ".1332662266211..",
                ".1332222222211..",
                ".122222222211...",
                "..1122222111....",
                "....11111111....",
                "................",
                "................",
                "................",
                "................"
        }, Map.of(
                '1', 0xFF5A3708, '2', 0xFFB87814, '3', 0xFFE5A922,
                '4', 0xFFF9D55C, '5', 0xFFFFF0A6, '6', 0xFFFFFFFF))));

        list.add(tex("starlight_resin", 16, 16, pixelGrid(new String[]{
                "................",
                ".....111111.....",
                "...1145654211...",
                "..145654321111..",
                ".14654211.......",
                ".155311.........",
                ".14421..........",
                ".14421......6...",
                ".144311....565..",
                ".1344311....6...",
                "..12454211......",
                "...1123443211...",
                ".....11122211...",
                "........1111....",
                "................",
                "................"
        }, Map.of(
                '1', 0xFF102448, '2', 0xFF275EA8, '3', 0xFF3E8BE6,
                '4', 0xFF74C8FF, '5', 0xFFC2EEFF, '6', 0xFFFFFFFF))));

        int[] essenceBase = pixelGrid(new String[]{
                "................",
                ".....111111.....",
                "...1134554311...",
                "..134566654321..",
                ".13566776654221.",
                ".14567777653221.",
                ".14567776543211.",
                ".13456654322111.",
                ".1234443322111..",
                "..12233222111...",
                "...112222111....",
                ".....111111.....",
                "................",
                "................",
                "................",
                "................"
        }, Map.of(
                '1', 0xFF2D104A, '2', 0xFF5E2499, '3', 0xFF8C4BFF,
                '4', 0xFFBD8BFF, '5', 0xFFFFD86B, '6', 0xFFFFF2B2, '7', 0xFFFFFFFF));
        list.add(animatedStrip("primordial_essence", essenceBase, 4, 3));

        // Weapons & Tools
        list.add(tex("astral_greatsword", 16, 16, pixelGrid(new String[]{
                "............1111",
                "...........15661",
                "..........145651",
                ".........134541.",
                "........134541..",
                ".......134541...",
                "......134541....",
                "..11.134541.....",
                "..1g134541......",
                "...1g6531.......",
                "..11gg11........",
                ".1hhg111........",
                "1hH1............",
                "11g1............",
                ".11.............",
                "................"
        }, Map.of(
                '1', 0xFF101935, '3', 0xFF2A6CD4, '4', 0xFF56D8FF,
                '5', 0xFFB8F8FF, '6', 0xFFFFFFFF, 'g', 0xFFF4C55A,
                'h', 0xFF6B4226, 'H', 0xFF3E2413))));

        list.add(tex("gale_staff", 16, 16, pixelGrid(new String[]{
                ".........11111..",
                "........1456541.",
                "........1566651.",
                "........1345431.",
                ".........1g2g1..",
                "........1ggg1...",
                ".......1hh11....",
                "......1hh1......",
                ".....1hh1.......",
                "....1hH1........",
                "...1hH1.........",
                "..1hH1..........",
                ".1hh1...........",
                ".1g1............",
                "..1.............",
                "................"
        }, Map.of(
                '1', 0xFF0E2F2A, '2', 0xFF1A9E86, '3', 0xFF2ED8B6,
                '4', 0xFF82FFE2, '5', 0xFFC5FFF1, '6', 0xFFFFFFFF,
                'g', 0xFFF4C55A, 'h', 0xFF7B4F2C, 'H', 0xFF4F311A))));

        list.add(tex("ember_bow", 16, 16, pixelGrid(new String[]{
                ".......1111.....",
                ".....113431s....",
                "....134521.s....",
                "...13421...s....",
                "..1231.....s....",
                "..1g1......s....",
                ".1gg1......s....",
                "..1g1......s....",
                "..1231.....s....",
                "...13421...s....",
                "....134521.s....",
                ".....113431s....",
                ".......1111.....",
                "................",
                "................",
                "................"
        }, Map.of(
                '1', 0xFF3D0C08, '2', 0xFF9C2214, '3', 0xFFE84F24,
                '4', 0xFFFF9A3C, '5', 0xFFFFE57F, 'g', 0xFFF4C55A, 's', 0xFFDDD8CC))));

        list.add(tex("void_scythe", 16, 16, pixelGrid(new String[]{
                "....111111111...",
                "..114566543211..",
                ".1456543211111..",
                "14543211....1g1.",
                "13321......1hh1.",
                "121.......1hh1..",
                ".1.......1hh1...",
                "........1hh1....",
                ".......1hh1.....",
                "......1hh1......",
                ".....1hh1.......",
                "....1hh1........",
                "...1hh1.........",
                "..1gg1..........",
                "..111...........",
                "................"
        }, Map.of(
                '1', 0xFF190A2E, '2', 0xFF4E1E8A, '3', 0xFF8C4BFF,
                '4', 0xFFC599FF, '5', 0xFFF0D9FF, '6', 0xFFFFFFFF,
                'g', 0xFFF4C55A, 'h', 0xFF3A3548))));

        list.add(tex("chrono_shield", 16, 16, pixelGrid(new String[]{
                "................",
                "..111111111111..",
                ".1gggggggggggg1.",
                ".1g3333443333g1.",
                ".1g3445555443g1.",
                ".1g3456666543g1.",
                ".1g3345gg5433g1.",
                ".1g2345gg5432g1.",
                "..1g23455432g1..",
                "..1g22344322g1..",
                "...1g223322g1...",
                "....1g2222g1....",
                ".....1gggg1.....",
                "......1gg1......",
                ".......11.......",
                "................"
        }, Map.of(
                '1', 0xFF101935, '2', 0xFF1F4287, '3', 0xFF278EA5,
                '4', 0xFF56D8FF, '5', 0xFFBCEFFF, '6', 0xFFFFFFFF, 'g', 0xFFF4C55A))));

        list.add(tex("aetheria_pickaxe", 16, 16, TextureTemplates.generate("pickaxe", 0xFF43C9F5)));

        // Armor icons + 64x32 Valkyrie Gold-Trimmed Armor Layers
        list.add(tex("relic_crown", 16, 16, trimmedArmorIcon("helmet", 0xFF2D55B8, 0xFFF4C55A)));
        list.add(tex("relic_cuirass", 16, 16, trimmedArmorIcon("chestplate", 0xFF2D55B8, 0xFFF4C55A)));
        list.add(tex("relic_tassets", 16, 16, trimmedArmorIcon("leggings", 0xFF2D55B8, 0xFFF4C55A)));
        list.add(tex("relic_greaves", 16, 16, trimmedArmorIcon("boots", 0xFF2D55B8, 0xFFF4C55A)));
        list.add(tex("valkyrie_layer_1", 64, 32, goldTrimmedArmorLayer(1, 0xFF2B50AA, 0xFFF4C55A)));
        list.add(tex("valkyrie_layer_2", 64, 32, goldTrimmedArmorLayer(2, 0xFF2B50AA, 0xFFF4C55A)));

        // Foods, crops & discs
        list.add(tex("sun_nectar_tart", 16, 16, pixelGrid(new String[]{
                "................",
                "................",
                ".......55.......",
                "....11166111....",
                "..114566654311..",
                ".14556666543321.",
                ".14455554433221.",
                ".1c444443332cc1.",
                ".1cc3333322ccc1.",
                "..1cccccccccc1..",
                "...1cccccccc1...",
                "....11111111....",
                "................",
                "................",
                "................",
                "................"
        }, Map.of(
                '1', 0xFF4F280A, '2', 0xFFB86014, '3', 0xFFE88E26,
                '4', 0xFFFFC145, '5', 0xFFFFE57F, '6', 0xFFFFFFFF, 'c', 0xFFC78646))));

        list.add(tex("celestial_fruit", 16, 16, TextureTemplates.generate("apple", 0xFF4CE3D2)));
        list.add(tex("celestial_blossom_seeds", 16, 16, TextureTemplates.generate("seeds", 0xFF56D8FF)));
        for (int i = 0; i < 4; i++) {
            list.add(tex("celestial_blossom_stage_" + i, 16, 16, TextureTemplates.cropStage(i, 4, 0xFF4CE3D2)));
        }

        list.add(tex("disc_celestia", 16, 16, pixelGrid(new String[]{
                "................",
                ".....111111.....",
                "...1122332211...",
                "..123443344321..",
                ".12342211224321.",
                ".12321gggg12321.",
                ".1221g6666g1221.",
                ".1221g6116g1221.",
                ".1221g6666g1221.",
                ".12321gggg12321.",
                ".12342211224321.",
                "..123443344321..",
                "...1122332211...",
                ".....111111.....",
                "................",
                "................"
        }, Map.of(
                '1', 0xFF101426, '2', 0xFF202A52, '3', 0xFF395096,
                '4', 0xFF59D2FE, 'g', 0xFFF4C55A, '6', 0xFFFFFFFF))));

        // Effects & Armor Trim / Banner
        list.add(tex("starlight_surge_icon", 16, 16, TextureTemplates.generate("gem", 0xFF56D8FF)));
        list.add(tex("anemo_blessing_icon", 16, 16, TextureTemplates.generate("gem", 0xFF2ED8B6)));
        list.add(tex("celestial_crest_trim", 16, 16, TextureTemplates.generate("gem", 0xFFF4C55A)));
        list.add(tex("astral_banner_pattern", 16, 16, TextureTemplates.generate("dust", 0xFF59D2FE)));

        // World Blocks & Fluids & Portals
        list.add(tex("celestite_ore", 16, 16, celestiteOreTex()));
        list.add(tex("runed_marble", 16, 16, runedMarbleTex()));
        list.add(tex("celestial_pillar_top", 16, 16, pillarTopTex()));
        list.add(tex("celestial_pillar_side", 16, 16, pillarSideTex()));
        list.add(tex("relic_forge_top", 16, 16, forgeTopTex()));
        list.add(tex("relic_forge_side", 16, 16, forgeSideTex()));
        list.add(tex("relic_forge_bottom", 16, 16, TextureTemplates.generate("block", 0xFF252D48)));

        int[] waypointBase = TextureTemplates.generate("gem", 0xFF4BE0FF);
        list.add(animatedStrip("waypoint_core", waypointBase, 4, 3));
        list.add(animatedStrip("mana_spring_still", fluidSwirl(0xFF249EE8, 0xFF7AF2FF), 4, 4));
        list.add(tex("mana_spring_flowing", 16, 16, fluidSwirl(0xFF249EE8, 0xFF9DF7FF)));
        list.add(animatedStrip("starlight_portal", fluidSwirl(0xFF6B2ED8, 0xFF52E5FF), 4, 2));
        list.add(tex("portal_igniter", 16, 16, TextureTemplates.generate("rod", 0xFF52E5FF)));

        // Custom Entity 64x64 Skins
        list.add(tex("sky_stalker_skin", 64, 64, humanoidSkin64(0xFF1D2442, 0xFF2ED8B6, 0xFFF4C55A)));
        list.add(tex("star_wisp_skin", 64, 64, humanoidSkin64(0xFF15386B, 0xFF7AEBFF, 0xFFFFFFFF)));

        // Landscape Painting (32x16 - floating islands & aurora)
        list.add(tex("celestia_vista", 32, 16, paintingVista32x16()));

        // Custom Chest GUI Art (176x222 for 6 rows) & Codex Icons
        list.add(tex("astral_hub_bg", 176, 222, astralHubMenuArt()));
        list.add(tex("icon_astral", 16, 16, pixelGrid(new String[]{
                ".......4........",
                ".......5........",
                "......464.......",
                "....1356531.....",
                "..4566666654....",
                "....1356531.....",
                "......464.......",
                ".......5........",
                ".......4........",
                "................",
                "................",
                "................",
                "................",
                "................",
                "................",
                "................"
        }, Map.of('1', 0xFF2A5CA8, '3', 0xFF56D8FF, '4', 0xFFF4C55A, '5', 0xFFFFED9C, '6', 0xFFFFFFFF))));
        list.add(tex("icon_coin", 16, 16, TextureTemplates.generate("nugget", 0xFFF4C55A)));
        list.add(tex("icon_mana", 16, 16, TextureTemplates.generate("gem", 0xFF39C5FF)));
        list.add(tex("icon_pyro", 16, 16, TextureTemplates.generate("gem", 0xFFFF6D2E)));
        list.add(tex("icon_anemo", 16, 16, TextureTemplates.generate("gem", 0xFF2ED8B6)));

        p.textures.addAll(list);
        if (p.meta.icon.isBlank()) p.meta.icon = findTex(p, "astral_shard").id;
    }

    static TextureAsset findTex(Project p, String name) {
        return p.textures.stream().filter(t -> t.name.equals(name)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing texture: " + name));
    }

    static List<Model> buildModels(Project p) {
        List<Model> out = new ArrayList<>();
        String shardId = findTex(p, "astral_shard").id;
        String goldId = findTex(p, "celestial_coin").id;
        String galeId = findTex(p, "anemo_feather").id;
        String coreId = findTex(p, "waypoint_core").id;
        String marbleId = findTex(p, "runed_marble").id;
        String essenceId = findTex(p, "primordial_essence").id;

        // 1. 3D Astral Greatsword Item Model
        Model gs = new Model();
        gs.name = "astral_greatsword";
        gs.kind = "item";
        gs.textures.put("0", shardId);
        gs.textures.put("1", goldId);
        addTexCube(gs, "pommel", 7.0, 0.5, 7.0, 9.0, 2.5, 9.0, "1");
        addTexCube(gs, "grip", 7.4, 2.5, 7.4, 8.6, 7.5, 8.6, "1");
        addTexCube(gs, "guard", 4.0, 7.5, 6.8, 12.0, 9.0, 9.2, "1");
        addTexCube(gs, "blade_core", 6.8, 9.0, 7.5, 9.2, 23.0, 8.5, "0");
        addTexCube(gs, "blade_tip", 7.3, 23.0, 7.6, 8.7, 26.5, 8.4, "0");
        Cube gem = addTexCube(gs, "core_orb", 7.2, 7.7, 6.4, 8.8, 8.9, 9.6, "0");
        gem.rotation = 45; gem.axis = "z"; gem.origin = new double[]{8, 8.3, 8};
        gs.display.put("thirdperson_righthand", disp(new double[]{0, -90, 45}, new double[]{0, 4, 1}, new double[]{0.95, 0.95, 0.95}));
        gs.display.put("firstperson_righthand", disp(new double[]{0, -90, 25}, new double[]{1.5, 3.2, 1.0}, new double[]{0.8, 0.8, 0.8}));
        gs.display.put("gui", disp(new double[]{0, 0, -45}, new double[]{-2, -3, 0}, new double[]{0.72, 0.72, 0.72}));
        out.add(gs);

        // 2. 3D Gale Staff Item Model
        Model staff = new Model();
        staff.name = "gale_staff";
        staff.kind = "item";
        staff.textures.put("0", galeId);
        staff.textures.put("1", goldId);
        addTexCube(staff, "shaft", 7.4, 0, 7.4, 8.6, 17, 8.6, "1");
        addTexCube(staff, "ring_l", 5.5, 17, 7.3, 6.8, 22, 8.7, "1");
        addTexCube(staff, "ring_r", 9.2, 17, 7.3, 10.5, 22, 8.7, "1");
        addTexCube(staff, "ring_b", 6.5, 16.5, 7.3, 9.5, 17.5, 8.7, "1");
        Cube windOrb = addTexCube(staff, "wind_orb", 7.0, 18.5, 7.0, 9.0, 20.5, 9.0, "0");
        windOrb.rotation = 22.5; windOrb.axis = "y"; windOrb.origin = new double[]{8, 19.5, 8};
        staff.display.put("thirdperson_righthand", disp(new double[]{0, 0, 15}, new double[]{0, 3, 1}, new double[]{0.85, 0.85, 0.85}));
        staff.display.put("firstperson_righthand", disp(new double[]{0, -90, 20}, new double[]{1, 3, 1}, new double[]{0.75, 0.75, 0.75}));
        staff.display.put("gui", disp(new double[]{0, 0, -45}, new double[]{-1.5, -2.5, 0}, new double[]{0.75, 0.75, 0.75}));
        out.add(staff);

        // 3. 3D Waypoint Shrine Block Model (Genshin Statue/Teleport Waypoint feel)
        Model shrine = new Model();
        shrine.name = "waypoint_shrine";
        shrine.kind = "block";
        shrine.textures.put("0", marbleId);
        shrine.textures.put("1", coreId);
        shrine.textures.put("2", goldId);
        addTexCube(shrine, "plinth", 1, 0, 1, 15, 3, 15, "0");
        addTexCube(shrine, "gold_trim", 2, 3, 2, 14, 4.5, 14, "2");
        addTexCube(shrine, "pedestal", 4.5, 4.5, 4.5, 11.5, 7, 11.5, "0");
        Cube obelisk = addTexCube(shrine, "crystal", 6, 8.5, 6, 10, 15.5, 10, "1");
        obelisk.rotation = 45; obelisk.axis = "y"; obelisk.origin = new double[]{8, 12, 8};
        out.add(shrine);

        // 4. Animated 3D Multiblock Model: Resonance Beacon
        Model beacon = new Model();
        beacon.name = "resonance_beacon";
        beacon.kind = "item";
        beacon.textures.put("0", marbleId);
        beacon.textures.put("1", coreId);
        beacon.textures.put("2", goldId);
        addBone(beacon, "base", "", 8, 0, 8);
        addBone(beacon, "ring", "base", 8, 22, 8);
        addBone(beacon, "star", "base", 8, 22, 8);
        addBoneCube(beacon, "base", "plinth_b", -12, 0, -12, 28, 6, 28, "0");
        addBoneCube(beacon, "base", "collar", -2, 6, -2, 18, 12, 18, "2");
        addBoneCube(beacon, "ring", "arc_n", 2, 20, -4, 14, 24, -1, "2");
        addBoneCube(beacon, "ring", "arc_s", 2, 20, 17, 14, 24, 20, "2");
        addBoneCube(beacon, "ring", "arc_w", -4, 20, 2, -1, 24, 14, "2");
        addBoneCube(beacon, "ring", "arc_e", 17, 20, 2, 20, 24, 14, "2");
        addBoneCube(beacon, "star", "core_gem", 4, 18, 4, 12, 26, 12, "1");
        Animation spin = new Animation();
        spin.name = "work";
        spin.length = 2.0;
        spin.mode = "loop";
        Rig.key(spin, "ring", "rotation", "linear", new double[][]{{0, 0, 0, 0}, {2, 0, 360, 0}});
        Rig.key(spin, "star", "position", "smooth", new double[][]{{0, 0, 0, 0}, {1, 0, 3, 0}, {2, 0, 0, 0}});
        Rig.key(spin, "star", "rotation", "linear", new double[][]{{0, 0, 0, 0}, {2, 0, -360, 0}});
        beacon.animations.add(spin);
        out.add(beacon);

        // 5. Flagship Rigged 3D Boss Model: Astral Warden (Haloed Winged Knight)
        Model boss = new Model();
        boss.name = "astral_warden";
        boss.kind = "item";
        boss.textures.put("0", marbleId);
        boss.textures.put("1", essenceId);
        boss.textures.put("2", goldId);

        addBone(boss, "base", "", 8, 0, 8);
        addBone(boss, "torso", "base", 8, 13, 8);
        addBone(boss, "left_leg", "base", 5, 13, 8);
        addBone(boss, "right_leg", "base", 11, 13, 8);
        addBone(boss, "head", "torso", 8, 28, 8);
        addBone(boss, "crown_halo", "head", 8, 37, 9);
        addBone(boss, "left_arm", "torso", 1, 26, 8);
        addBone(boss, "right_arm", "torso", 15, 26, 8);
        addBone(boss, "blade", "right_arm", 15, 15, 6);
        addBone(boss, "left_wing", "torso", 5, 25, 11);
        addBone(boss, "right_wing", "torso", 11, 25, 11);

        addBoneCube(boss, "torso", "chest", 3, 13, 5, 13, 28, 11, "0");
        addBoneCube(boss, "torso", "chest_core", 6, 18, 4.5, 10, 23, 5.5, "1");
        addBoneCube(boss, "left_leg", "leg_l", 3.5, 0, 6, 7.5, 13, 10, "0");
        addBoneCube(boss, "right_leg", "leg_r", 8.5, 0, 6, 12.5, 13, 10, "0");
        addBoneCube(boss, "head", "helm", 4, 28, 4, 12, 36, 12, "0");
        addBoneCube(boss, "head", "crest", 7, 35, 3, 9, 39, 10, "2");
        addBoneCube(boss, "crown_halo", "halo_t", 4, 38, 10, 12, 40, 12, "2");
        addBoneCube(boss, "crown_halo", "halo_l", 2, 34, 10, 4, 39, 12, "2");
        addBoneCube(boss, "crown_halo", "halo_r", 12, 34, 10, 14, 39, 12, "2");
        addBoneCube(boss, "left_arm", "pauldron_l", -2, 23, 5, 3, 28, 11, "2");
        addBoneCube(boss, "left_arm", "arm_l", -1, 13, 6, 3, 23, 10, "0");
        addBoneCube(boss, "right_arm", "pauldron_r", 13, 23, 5, 18, 28, 11, "2");
        addBoneCube(boss, "right_arm", "arm_r", 13, 13, 6, 17, 23, 10, "0");
        addBoneCube(boss, "blade", "guard", 13.5, 13.5, 3, 16.5, 16.5, 7, "2");
        addBoneCube(boss, "blade", "sword_edge", 14.5, 14, -14, 15.5, 16, 3, "1");
        addBoneCube(boss, "left_wing", "wing_l1", -10, 22, 12, 4, 33, 14, "1");
        addBoneCube(boss, "right_wing", "wing_r1", 12, 22, 12, 26, 33, 14, "1");

        Animation idle = addAnim(boss, "idle", 2.4, "loop");
        Rig.key(idle, "crown_halo", "rotation", "linear", new double[][]{{0, 0, 0, 0}, {2.4, 0, 0, 360}});
        Rig.key(idle, "left_wing", "rotation", "smooth", new double[][]{{0, 0, 15, 0}, {1.2, 0, 28, 6}, {2.4, 0, 15, 0}});
        Rig.key(idle, "right_wing", "rotation", "smooth", new double[][]{{0, 0, -15, 0}, {1.2, 0, -28, -6}, {2.4, 0, -15, 0}});
        Rig.key(idle, "torso", "position", "smooth", new double[][]{{0, 0, 0, 0}, {1.2, 0, 0.8, 0}, {2.4, 0, 0, 0}});

        Animation walk = addAnim(boss, "walk", 1.0, "loop");
        Rig.applyPreset(walk, "right_leg", "swing");
        Rig.applyPreset(walk, "left_leg", "swing_back");
        Rig.applyPreset(walk, "left_arm", "swing");
        Rig.key(walk, "right_arm", "rotation", "smooth", new double[][]{{0, -12, 0, 0}, {0.5, -20, 0, 0}, {1, -12, 0, 0}});

        Animation hurt = addAnim(boss, "hurt", 0.4, "once");
        Rig.applyPreset(hurt, "torso", "flinch");

        Animation attack = addAnim(boss, "attack", 0.65, "once");
        Rig.key(attack, "right_arm", "rotation", "ease_out", new double[][]{{0, 0, 0, 0}, {0.2, 125, -20, 0}, {0.45, -35, 25, 0}, {0.65, 0, 0, 0}});
        Rig.key(attack, "left_wing", "rotation", "ease_out", new double[][]{{0, 0, 15, 0}, {0.3, 0, 45, 0}, {0.65, 0, 15, 0}});
        Rig.key(attack, "right_wing", "rotation", "ease_out", new double[][]{{0, 0, -15, 0}, {0.3, 0, -45, 0}, {0.65, 0, -15, 0}});

        Animation supernova = addAnim(boss, "supernova", 1.5, "once");
        Rig.key(supernova, "left_arm", "rotation", "ease_out", new double[][]{{0, 0, 0, 0}, {0.4, 150, 0, 20}, {1.1, 150, 0, 20}, {1.5, 0, 0, 0}});
        Rig.key(supernova, "right_arm", "rotation", "ease_out", new double[][]{{0, 0, 0, 0}, {0.4, 150, 0, -20}, {1.1, 150, 0, -20}, {1.5, 0, 0, 0}});
        Rig.key(supernova, "head", "rotation", "ease_out", new double[][]{{0, 0, 0, 0}, {0.4, -30, 0, 0}, {1.1, -30, 0, 0}, {1.5, 0, 0, 0}});

        Animation death = addAnim(boss, "death", 0.9, "hold");
        Rig.key(death, "base", "rotation", "ease_in", new double[][]{{0, 0, 0, 0}, {0.85, 0, 0, 90}});

        out.add(boss);
        return out;
    }

    // ------------------------------------------------------------------ drawing helpers

    private static TextureAsset tex(String name, int w, int h, int[] px) {
        return TextureAsset.of(name, px, w, h);
    }

    private static TextureAsset animatedStrip(String name, int[] frame16x16, int frames, int frameTime) {
        int[] strip = new int[256 * frames];
        for (int f = 0; f < frames; f++) {
            double mul = 0.88 + 0.16 * Math.sin(f * Math.PI * 2.0 / frames);
            for (int i = 0; i < 256; i++) {
                int c = frame16x16[i];
                if ((c >>> 24) == 0) continue;
                strip[f * 256 + i] = scaleColor(c, mul);
            }
        }
        TextureAsset t = TextureAsset.of(name, strip, 16, 16 * frames);
        t.frameTime = frameTime;
        t.interpolate = true;
        return t;
    }

    private static int scaleColor(int argb, double m) {
        int a = argb & 0xFF000000;
        int r = Math.min(255, Math.max(0, (int) (((argb >> 16) & 255) * m)));
        int g = Math.min(255, Math.max(0, (int) (((argb >> 8) & 255) * m)));
        int b = Math.min(255, Math.max(0, (int) ((argb & 255) * m)));
        return a | (r << 16) | (g << 8) | b;
    }

    private static int[] pixelGrid(String[] rows, Map<Character, Integer> pal) {
        int[] px = new int[256];
        for (int y = 0; y < 16; y++) {
            String r = rows[y];
            for (int x = 0; x < 16; x++) {
                char c = x < r.length() ? r.charAt(x) : '.';
                px[y * 16 + x] = pal.getOrDefault(c, TRANSPARENT);
            }
        }
        return px;
    }

    private static int[] trimmedArmorIcon(String template, int bodyArgb, int trimArgb) {
        int[] base = TextureTemplates.generate(template, bodyArgb);
        int[] trim = TextureTemplates.shades(trimArgb);
        // Highlight key trim lines along the top ridge / belt
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = base[y * 16 + x];
                if ((c >>> 24) == 0) continue;
                boolean edge = (y == 4 || y == 11) && (x >= 4 && x <= 11);
                if (edge) base[y * 16 + x] = trim[3];
            }
        }
        return base;
    }

    private static int[] goldTrimmedArmorLayer(int layer, int bodyArgb, int trimArgb) {
        int[] base = TextureTemplates.generate("armor_layer_" + layer, bodyArgb);
        int[] gold = TextureTemplates.shades(trimArgb);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                int c = base[y * 64 + x];
                if ((c >>> 24) == 0) continue;
                if ((layer == 1 && (y == 8 || y == 18 || y == 27)) || (layer == 2 && (y == 20 || y == 25))) {
                    base[y * 64 + x] = gold[2];
                }
            }
        }
        return base;
    }

    private static int[] celestiteOreTex() {
        int[] px = new int[256];
        int[] slate = {0xFF282D3C, 0xFF313749, 0xFF394055, 0xFF222633};
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int h = Math.abs((x * 73856093) ^ (y * 19349663));
                px[y * 16 + x] = slate[h % slate.length];
            }
        }
        int[][] veins = {{3, 3}, {4, 3}, {4, 4}, {11, 4}, {12, 5}, {11, 5}, {6, 9}, {7, 9}, {6, 10}, {10, 11}, {11, 11}, {11, 12}};
        for (int[] v : veins) {
            px[v[1] * 16 + v[0]] = (v[0] + v[1]) % 2 == 0 ? 0xFF66E2FF : 0xFFF4C55A;
        }
        return px;
    }

    private static int[] runedMarbleTex() {
        int[] px = new int[256];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean border = x == 0 || y == 0 || x == 15 || y == 15;
                boolean innerFrame = x == 2 || y == 2 || x == 13 || y == 13;
                boolean rune = innerFrame && ((x >= 5 && x <= 10) || (y >= 5 && y <= 10));
                if (border) px[y * 16 + x] = 0xFFB8B4AE;
                else if (rune) px[y * 16 + x] = ((x + y) % 2 == 0) ? 0xFFF4C55A : 0xFF56D8FF;
                else if (innerFrame) px[y * 16 + x] = 0xFF8C8680;
                else px[y * 16 + x] = ((x + y) % 5 == 0) ? 0xFFECE9E2 : 0xFFE0DDD4;
            }
        }
        return px;
    }

    private static int[] pillarTopTex() {
        int[] px = new int[256];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean rim = x < 2 || y < 2 || x > 13 || y > 13;
                boolean ring = (x == 3 || y == 3 || x == 12 || y == 12) && !rim;
                px[y * 16 + x] = rim ? 0xFFF4C55A : ring ? 0xFF4BE0FF : 0xFFE5E1D8;
            }
        }
        return px;
    }

    private static int[] pillarSideTex() {
        int[] px = new int[256];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean cap = y < 2 || y > 13;
                boolean flute = x % 3 == 1;
                px[y * 16 + x] = cap ? 0xFFE4B44B : flute ? 0xFFBFB9AE : 0xFFE8E4DC;
            }
        }
        return px;
    }

    private static int[] forgeTopTex() {
        int[] px = new int[256];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean star = Math.abs(x - 8) + Math.abs(y - 8) <= 3;
                boolean border = x < 2 || y < 2 || x > 13 || y > 13;
                px[y * 16 + x] = star ? 0xFF66E2FF : border ? 0xFFF4C55A : 0xFF282E4A;
            }
        }
        return px;
    }

    private static int[] forgeSideTex() {
        int[] px = new int[256];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean brazier = y >= 5 && y <= 11 && x >= 4 && x <= 11;
                boolean trim = y == 1 || y == 14;
                px[y * 16 + x] = brazier ? (y < 8 ? 0xFFFFB74D : 0xFFFF6D2E) : trim ? 0xFFF4C55A : 0xFF23283F;
            }
        }
        return px;
    }

    private static int[] fluidSwirl(int base, int crest) {
        int[] px = new int[256];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                px[y * 16 + x] = ((x + y * 2) % 5 == 0) ? crest : base;
            }
        }
        return px;
    }

    private static int[] humanoidSkin64(int robe, int accent, int eye) {
        int[] px = new int[64 * 64];
        // Head box: (0,0) 8x8x8 -> (0..32, 0..16), Body (16,16) 8x12x4, Arms (40,16), Legs (0,16)
        fillRect(px, 64, 0, 0, 32, 16, robe);
        fillRect(px, 64, 8, 8, 8, 8, scaleColor(robe, 1.15));
        px[11 * 64 + 10] = eye; px[11 * 64 + 13] = eye;
        fillRect(px, 64, 16, 16, 24, 16, robe);
        fillRect(px, 64, 21, 21, 4, 5, accent);
        fillRect(px, 64, 40, 16, 16, 16, accent);
        fillRect(px, 64, 0, 16, 16, 16, scaleColor(robe, 0.9));
        fillRect(px, 64, 16, 48, 16, 16, scaleColor(robe, 0.9));
        fillRect(px, 64, 32, 48, 16, 16, accent);
        return px;
    }

    private static void fillRect(int[] px, int w, int rx, int ry, int rw, int rh, int col) {
        for (int y = ry; y < ry + rh && y < px.length / w; y++) {
            for (int x = rx; x < rx + rw && x < w; x++) px[y * w + x] = col;
        }
    }

    private static int[] paintingVista32x16() {
        int[] px = new int[32 * 16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 32; x++) {
                if (x == 0 || y == 0 || x == 31 || y == 15) {
                    px[y * 32 + x] = 0xFF6B4226;
                } else if (y < 6) {
                    px[y * 32 + x] = (x + y * 3) % 7 == 0 ? 0xFFFFEB8A : (y < 3 ? 0xFF131936 : 0xFF1F386E);
                } else if (y < 10 && x >= 9 && x <= 22) {
                    px[y * 32 + x] = y == 6 ? 0xFF4BE0A8 : 0xFF6D543D;
                } else if (x == 16 && y >= 6 && y <= 14) {
                    px[y * 32 + x] = 0xFF6AE8FF;
                } else {
                    px[y * 32 + x] = y < 11 ? 0xFF28558F : 0xFF19305E;
                }
            }
        }
        return px;
    }

    private static int[] astralHubMenuArt() {
        int w = MenuArt.WIDTH, h = 114 + 18 * 6;
        int[] px = MenuArt.template(6, 0xFF1B213B);
        // Enrich outer frame with gold corner accents & top starlight banner
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean border = x == 0 || y == 0 || x == w - 1 || y == h - 1;
                boolean innerGold = x == 2 || y == 2 || x == w - 3 || y == h - 3;
                if (border) px[y * w + x] = 0xFF0A0D1C;
                else if (innerGold && ((x < 12 || x > w - 13) || (y < 12 || y > h - 13))) px[y * w + x] = 0xFFF4C55A;
                else if (y >= 4 && y <= 14 && x >= 6 && x < w - 6 && (x % 14 == 0 && y == 9)) px[y * w + x] = 0xFF56D8FF;
            }
        }
        return px;
    }

    private static Cube addTexCube(Model m, String name, double x1, double y1, double z1, double x2, double y2, double z2, String texKey) {
        Cube c = Models.cube(name, x1, y1, z1, x2, y2, z2);
        for (String dir : Cube.FACES) {
            c.face(dir).texture = texKey;
            c.face(dir).uv = new double[]{0, 0, 16, 16};
        }
        m.cubes.add(c);
        return c;
    }

    private static void addBone(Model m, String name, String parent, double px, double py, double pz) {
        Bone b = new Bone();
        b.name = name;
        b.parent = parent;
        b.pivot = new double[]{px, py, pz};
        m.bones.add(b);
    }

    private static void addBoneCube(Model m, String bone, String name, double x1, double y1, double z1, double x2, double y2, double z2, String texKey) {
        Cube c = addTexCube(m, name, x1, y1, z1, x2, y2, z2, texKey);
        c.bone = bone;
    }

    private static Animation addAnim(Model m, String name, double len, String mode) {
        Animation a = new Animation();
        a.name = name;
        a.length = len;
        a.mode = mode;
        m.animations.add(a);
        return a;
    }

    private static Display disp(double[] rot, double[] tr, double[] sc) {
        Display d = new Display();
        d.rotation = rot;
        d.translation = tr;
        d.scale = sc;
        return d;
    }
}

