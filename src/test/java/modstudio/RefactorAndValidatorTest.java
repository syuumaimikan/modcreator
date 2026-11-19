package modstudio;

import modstudio.core.*;
import modstudio.core.Elements.*;
import modstudio.core.ModElements.*;
import modstudio.core.Systems.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class RefactorAndValidatorTest {

    @Test
    void renameElementUpdatesTypedAndIdReferences() {
        Project p = new Project();
        p.meta.modId = "testmod";

        Model m = new Model();
        m.name = "old_model";
        Elements.Block blk = new Elements.Block();
        blk.name = "my_block";
        blk.shape = "model";
        blk.model = "old_model";

        CustomEntity ce = new CustomEntity();
        ce.name = "crystal_mob";
        ce.model = "old_model";

        Procedure pr = new Procedure();
        pr.name = "open_ui";

        CustomScreen cs = new CustomScreen();
        cs.name = "main_menu";
        ScreenWidget btn = new ScreenWidget();
        btn.kind = "button";
        btn.action = "procedure";
        btn.target = "open_ui";
        cs.widgets.add(btn);

        ArmorMaterial am = new ArmorMaterial();
        am.name = "old_arm";
        Elements.Item helm = new Elements.Item();
        helm.name = "my_helm";
        helm.kind = "helmet";
        helm.armorMaterial = "old_arm";

        p.elements.addAll(List.of(m, blk, ce, pr, cs, am, helm));

        m.name = "new_model";
        Refactor.renameElement(p, m, "old_model", "new_model");
        assertEquals("new_model", blk.model);
        assertEquals("new_model", ce.model);

        pr.name = "launch_ui";
        Refactor.renameElement(p, pr, "open_ui", "launch_ui");
        assertEquals("launch_ui", btn.target);

        am.name = "new_arm";
        Refactor.renameElement(p, am, "old_arm", "new_arm");
        assertEquals("new_arm", helm.armorMaterial);
    }

    @Test
    void localizationCoversAllAdvancedTypesAndLanguages() {
        Project p = new Project();
        p.meta.modId = "locmod";
        p.meta.languages.add("ja_jp");

        Fluid f = new Fluid();
        f.name = "liquid_gold";
        f.displayName = "Liquid Gold";
        f.setLoc("name", "ja_jp", "液体ゴールド");

        CustomEntity ce = new CustomEntity();
        ce.name = "gold_sprite";
        ce.displayName = "Gold Sprite";
        ce.spawnEgg = true;

        Portal pt = new Portal();
        pt.name = "aether_gate";
        pt.displayName = "Aether Gate";
        pt.generateIgniter = true;

        ArmorTrim at = new ArmorTrim();
        at.name = "solar";
        at.displayName = "Solar";
        at.generateTemplate = true;

        BannerPattern bp = new BannerPattern();
        bp.name = "sun";
        bp.displayName = "Sun Emblem";

        Profession pf = new Profession();
        pf.name = "alchemist";
        pf.displayName = "Alchemist";

        p.elements.addAll(List.of(f, ce, pt, at, bp, pf));

        Map<String, Map<String, String>> files = Localization.files(p);
        assertTrue(files.containsKey("en_us"));
        assertTrue(files.containsKey("ja_jp"));
        assertEquals("Liquid Gold", files.get("en_us").get("block.locmod.liquid_gold"));
        assertEquals("Liquid Gold Bucket", files.get("en_us").get("item.locmod.liquid_gold_bucket"));
        assertEquals("液体ゴールド", files.get("ja_jp").get("block.locmod.liquid_gold"));
        assertTrue(files.get("en_us").containsKey("entity.locmod.gold_sprite"));
        assertTrue(files.get("en_us").containsKey("item.locmod.gold_sprite_spawn_egg"));
        assertTrue(files.get("en_us").containsKey("block.locmod.aether_gate_portal"));
        assertTrue(files.get("en_us").containsKey("item.locmod.aether_gate_igniter"));
        assertTrue(files.get("en_us").containsKey("trim_pattern.locmod.solar"));
        assertTrue(files.get("en_us").containsKey("block.minecraft.banner.locmod.sun"));
        assertTrue(files.get("en_us").containsKey("entity.minecraft.villager.locmod.alchemist"));
    }

    @Test
    void validatorCatchesAdvancedElementErrors() {
        Project p = new Project();
        p.meta.modId = "valmod";

        Container ct = new Container();
        ct.name = "broken_ct";
        ct.block = "";

        CustomEntity ce = new CustomEntity();
        ce.name = "broken_mob";
        ce.naturalSpawn = true;
        ce.spawnMin = 6;
        ce.spawnMax = 2;

        ModConfig cfg = new ModConfig();
        cfg.name = "broken_cfg";
        ConfigEntry entry = new ConfigEntry();
        entry.key = "bad key!";
        cfg.entries.add(entry);

        MixinSpec mx = new MixinSpec();
        mx.name = "broken_mixin";
        mx.targetClass = "";

        Portal pt = new Portal();
        pt.name = "broken_portal";
        pt.frameBlock = "";

        WorldFeature wf = new WorldFeature();
        wf.name = "broken_wf";
        wf.minY = 60;
        wf.maxY = 10;

        Profession pf = new Profession();
        pf.name = "broken_prof";
        pf.workstationBlock = "";

        p.elements.addAll(List.of(ct, ce, cfg, mx, pt, wf, pf));

        List<Validator.Problem> probs = Validator.validate(p);
        assertEquals(0, Validator.count(probs, Validator.Severity.ERROR) == 0 ? 1 : 0);
        assertTrue(probs.stream().anyMatch(x -> x.message().contains("broken_ct")));
        assertTrue(probs.stream().anyMatch(x -> x.message().contains("broken_mob")));
        assertTrue(probs.stream().anyMatch(x -> x.message().contains("broken_cfg")));
        assertTrue(probs.stream().anyMatch(x -> x.message().contains("broken_mixin")));
        assertTrue(probs.stream().anyMatch(x -> x.message().contains("broken_portal")));
        assertTrue(probs.stream().anyMatch(x -> x.message().contains("broken_wf")));
        assertTrue(probs.stream().anyMatch(x -> x.message().contains("broken_prof")));
    }
}

