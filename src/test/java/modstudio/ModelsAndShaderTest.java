package modstudio;

import com.google.gson.JsonObject;
import modstudio.core.*;
import modstudio.core.Elements.Cube;
import modstudio.core.Elements.Model;
import modstudio.core.Systems.Shader;
import modstudio.gen.J;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ModelsAndShaderTest {

    @Test
    void bedrockGeometryRoundTripPreservesCubesAndUVs() {
        Model m = new Model();
        m.name = "pedestal";
        m.textureWidth = 32;
        m.textureHeight = 32;
        Cube base = Models.cube("base", 2, 0, 2, 14, 4, 14);
        base.rotation = 22.5;
        base.axis = "y";
        base.origin = new double[]{8, 2, 8};
        base.face("up").uv = new double[]{0, 0, 8, 8};
        m.cubes.add(base);

        JsonObject geo = Models.toBedrock(m, "geometry.pedestal", 32, 32);
        Models.Imported imported = Models.read(J.pretty(geo), "pedestal.geo.json");
        Model back = imported.model();

        assertEquals(32, back.textureWidth);
        assertEquals(32, back.textureHeight);
        assertEquals(1, back.cubes.size());
        Cube c = back.cubes.get(0);
        assertEquals(2.0, c.from[0], 0.01);
        assertEquals(0.0, c.from[1], 0.01);
        assertEquals(2.0, c.from[2], 0.01);
        assertEquals(14.0, c.to[0], 0.01);
        assertEquals(4.0, c.to[1], 0.01);
        assertEquals(14.0, c.to[2], 0.01);
        assertEquals(22.5, c.rotation, 0.01);
        assertEquals("y", c.axis);
        assertArrayEquals(new double[]{0, 0, 8, 8}, c.face("up").uv, 0.05);
    }

    @Test
    void importsBedrockBoxUvWithMultipleBones() {
        String bedrockBox = """
                {
                  "format_version": "1.16.0",
                  "minecraft:geometry": [
                    {
                      "description": {
                        "identifier": "geometry.crate",
                        "texture_width": 64,
                        "texture_height": 64
                      },
                      "bones": [
                        {
                          "name": "body",
                          "pivot": [0, 8, 0],
                          "cubes": [
                            {"origin": [-4, 0, -4], "size": [8, 8, 8], "uv": [0, 0]}
                          ]
                        },
                        {
                          "name": "lid",
                          "parent": "body",
                          "pivot": [0, 8, 4],
                          "cubes": [
                            {"origin": [-4, 8, -4], "size": [8, 2, 8], "uv": [0, 16]}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;
        Model m = Models.read(bedrockBox, "crate.geo.json").model();
        assertEquals(64, m.textureWidth);
        assertEquals(2, m.bones.size());
        assertEquals("body", m.bones.get(1).parent);
        assertEquals(2, m.cubes.size());
        assertEquals(4.0, m.cubes.get(0).from[0], 0.01);
        assertEquals(12.0, m.cubes.get(0).to[0], 0.01);
        assertEquals(8.0, m.cubes.get(1).from[1], 0.01);
        assertEquals(10.0, m.cubes.get(1).to[1], 0.01);
    }

    @Test
    void postShadersAndNodeCpuProgramWork() {
        assertTrue(Shaders.KINDS.contains("post"));
        List<String> postList = Shaders.postPrograms();
        assertFalse(postList.isEmpty());
        assertTrue(postList.contains("phosphor") || postList.contains("blur"));

        Shader post = new Shader();
        post.name = "neon_tint";
        post.kind = "post";
        post.mode = "nodes";
        post.nodes.addAll(ShaderNodes.starter());

        String glsl = ShaderNodes.fragment(post);
        assertTrue(glsl.contains("#version 150"));
        assertTrue(glsl.contains("DiffuseSampler"));
        assertTrue(glsl.contains("ms_graph("));

        ShaderNodes.Program prog = new ShaderNodes.Program(post.nodes);
        ShaderNodes.Env env = new ShaderNodes.Env();
        System.arraycopy(new double[]{0.5, 0.25, 0.75, 1.0}, 0, env.color, 0, 4);
        env.u = 0.5;
        env.v = 0.5;
        env.time = 1.0;
        env.texture = (u, v, o) -> System.arraycopy(env.color, 0, o, 0, 4);
        double[] out = new double[4];
        prog.run(env, out);
        for (double ch : out) assertFalse(Double.isNaN(ch));
        assertTrue(out[3] > 0.0);
    }
}
