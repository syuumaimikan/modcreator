package modstudio.core;

import java.util.LinkedHashMap;

/** Default toolchain versions for each target and Minecraft version. Projects may override every value. */
public final class Versions {
    private Versions() {}

    public static final String GRADLE = "8.14.7";
    public static final String FOOJAY = "0.10.0";

    private static String pick(McVersion v, String v1201, String v1204, String v1206, String v1211, String v1214, String v1218) {
        return switch (v) {
            case V1_20_1 -> v1201;
            case V1_20_4 -> v1204;
            case V1_20_6 -> v1206;
            case V1_21_1 -> v1211;
            case V1_21_4 -> v1214;
            case V1_21_8 -> v1218;
        };
    }

    public static LinkedHashMap<String, String> defaults(Target t, McVersion v) {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        switch (t) {
            case FORGE -> {
                m.put("forge", pick(v, "47.4.28", "49.2.13", "50.2.15", "52.1.20", "54.1.22", "58.1.26"));
                m.put("forgegradle", "[6.0.24,6.2)");
                m.put("gradle", GRADLE);
            }
            case NEOFORGE -> {
                m.put("neoforge", pick(v, "", "20.4.253", "20.6.144", "21.1.266", "21.4.161", "21.8.57"));
                m.put("moddev", "2.0.148");
                m.put("gradle", GRADLE);
            }
            case FABRIC -> {
                m.put("loader", "0.17.3");
                m.put("fabric_api", pick(v, "0.92.12+1.20.1", "0.97.3+1.20.4", "0.100.8+1.20.6", "0.116.18+1.21.1",
                        "0.119.4+1.21.4", "0.136.1+1.21.8"));
                m.put("loom", "1.11.8");
                m.put("gradle", GRADLE);
            }
            case PAPER -> {
                m.put("api", "paper");
                m.put("api_artifact", v.id + "-R0.1-SNAPSHOT");
                m.put("gradle", GRADLE);
            }
            case BEDROCK -> {
                m.put("min_engine", "1.21.0");
                m.put("script_api", "1.11.0");
            }
            default -> {
            }
        }
        return m;
    }
}
