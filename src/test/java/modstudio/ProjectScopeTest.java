package modstudio;

import modstudio.core.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ProjectScopeTest {

    @Test
    void datapackAndResourcepackWorkspacesStayFocused() {
        Project dp = new Project();
        dp.meta.targets = new java.util.ArrayList<>(List.of("datapack"));
        assertEquals(ProjectScope.Kind.DATAPACK, ProjectScope.kind(dp));
        assertFalse(ProjectScope.hasCode(dp.targets()));
        assertTrue(ProjectScope.available(dp, ElementType.RECIPE));
        assertTrue(ProjectScope.available(dp, ElementType.ADVANCEMENT));
        assertTrue(ProjectScope.available(dp, ElementType.FUNCTION));
        assertTrue(ProjectScope.available(dp, ElementType.WORLD_FEATURE));
        assertFalse(ProjectScope.available(dp, ElementType.BLOCK));
        assertFalse(ProjectScope.available(dp, ElementType.ITEM));
        assertFalse(ProjectScope.available(dp, ElementType.FLUID));
        assertFalse(ProjectScope.available(dp, ElementType.MACHINE));
        assertFalse(ProjectScope.available(dp, ElementType.API_REQUEST));
        assertFalse(ProjectScope.available(dp, ElementType.SHADER));

        Project rp = new Project();
        rp.meta.targets = new java.util.ArrayList<>(List.of("resourcepack"));
        assertEquals(ProjectScope.Kind.RESOURCEPACK, ProjectScope.kind(rp));
        assertFalse(ProjectScope.hasCode(rp.targets()));
        assertTrue(ProjectScope.available(rp, ElementType.MODEL));
        assertTrue(ProjectScope.available(rp, ElementType.SHADER));
        assertTrue(ProjectScope.available(rp, ElementType.SOUND));
        assertTrue(ProjectScope.available(rp, ElementType.THEME));
        assertTrue(ProjectScope.available(rp, ElementType.ICON_FONT));
        assertFalse(ProjectScope.available(rp, ElementType.ITEM));
        assertFalse(ProjectScope.available(rp, ElementType.BLOCK));
        assertFalse(ProjectScope.available(rp, ElementType.RECIPE));
        assertFalse(ProjectScope.available(rp, ElementType.MENU));

        Project plugin = new Project();
        plugin.meta.targets = new java.util.ArrayList<>(List.of("folia"));
        assertEquals(ProjectScope.Kind.PLUGIN, ProjectScope.kind(plugin));
        assertTrue(ProjectScope.hasCode(plugin.targets()));
        assertTrue(ProjectScope.available(plugin, ElementType.COMMAND));
        assertTrue(ProjectScope.available(plugin, ElementType.CODE_FILE));
        assertFalse(ProjectScope.available(plugin, ElementType.BLOCK));
        assertFalse(ProjectScope.available(plugin, ElementType.MIXIN));
        assertFalse(ProjectScope.available(plugin, ElementType.CONTAINER));
    }

    @Test
    void kindSpecificStarterTemplatesGenerateCleanProjects() {
        assertEquals(List.of("empty", "dp_starter", "pack_starter"), ProjectTemplates.forKind(ProjectScope.Kind.DATAPACK));
        assertEquals(List.of("empty", "rp_starter"), ProjectTemplates.forKind(ProjectScope.Kind.RESOURCEPACK));
        assertTrue(ProjectTemplates.forKind(ProjectScope.Kind.PLUGIN).contains("plugin_starter"));
        assertTrue(ProjectTemplates.forKind(ProjectScope.Kind.BEDROCK).contains("bedrock_starter"));

        Project dp = new Project();
        dp.meta.modId = "mydp";
        dp.meta.name = "My Data Pack";
        dp.meta.targets = new java.util.ArrayList<>(List.of("datapack"));
        ProjectTemplates.apply(dp, "dp_starter");
        assertTrue(dp.ofType(ElementType.BLOCK).isEmpty());
        assertTrue(dp.ofType(ElementType.ITEM).isEmpty());
        assertFalse(dp.ofType(ElementType.RECIPE).isEmpty());
        assertFalse(dp.ofType(ElementType.FUNCTION).isEmpty());
        assertEquals(0, Validator.count(Validator.validate(dp), Validator.Severity.ERROR));

        Project rp = new Project();
        rp.meta.modId = "myrp";
        rp.meta.name = "My Resource Pack";
        rp.meta.targets = new java.util.ArrayList<>(List.of("resourcepack"));
        ProjectTemplates.apply(rp, "rp_starter");
        assertFalse(rp.ofType(ElementType.MODEL).isEmpty());
        assertFalse(rp.ofType(ElementType.SHADER).isEmpty());
        assertEquals(0, Validator.count(Validator.validate(rp), Validator.Severity.ERROR));
    }
}

