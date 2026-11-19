package modstudio.core;

import java.util.LinkedHashMap;

/** Default toolchain versions for each target and Minecraft version. Projects may override every value. */
public final class Versions {
    private Versions() {}

    public static final String GRADLE = "8.14.7";
    public static final String GRADLE_MODERN = "9.2.1";
    public static final String FOOJAY = "0.10.0";

    private static String pick(McVersion v, String v1201, String v1204, String v1206, String v1211, String v1214,
                               String v1218, String v1219, String v12110, String v12111, String v261) {
        return switch (v) {
            case V1_20_1 -> v1201;
            case V1_20_4 -> v1204;
            case V1_20_6 -> v1206;
            case V1_21_1 -> v1211;
            case V1_21_4 -> v1214;
            case V1_21_8 -> v1218;
            case V1_21_9 -> v1219;
            case V1_21_10 -> v12110;
            case V1_21_11 -> v12111;
            case V26_1 -> v261;
        };
    }

    public static LinkedHashMap<String, String> defaults(Target t, McVersion v) {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        String gradle = v.unobfuscated() ? GRADLE_MODERN : GRADLE;
        switch (t) {
            case FORGE -> {
                m.put("forge", pick(v, "47.4.28", "49.2.13", "50.2.15", "52.1.20", "54.1.22", "58.1.26", "59.0.5", "60.1.18", "61.0.10", ""));
                m.put("forgegradle", "[6.0.24,6.2)");
                m.put("gradle", gradle);
            }
            case NEOFORGE -> {
                m.put("neoforge", pick(v, "", "20.4.253", "20.6.144", "21.1.266", "21.4.161", "21.8.57", "21.9.16-beta", "21.10.63", "21.11.48", "26.1.2.117"));
                m.put("moddev", "2.0.148");
                m.put("gradle", gradle);
            }
            case FABRIC -> {
                m.put("loader", v.unobfuscated() ? "0.18.6" : "0.17.3");
                m.put("fabric_api", pick(v, "0.92.12+1.20.1", "0.97.3+1.20.4", "0.100.8+1.20.6", "0.116.18+1.21.1",
                        "0.119.4+1.21.4", "0.136.1+1.21.8", "0.134.0+1.21.9", "0.138.3+1.21.10", "0.141.2+1.21.11", "0.145.1+26.1"));
                m.put("loom", v.unobfuscated() ? "1.14.10" : "1.11.8");
                m.put("gradle", gradle);
            }
            case QUILT -> {
                m.put("quilt_loader", "0.30.2");
                m.put("quilted_fabric_api", pick(v, "7.6.0+0.92.6-1.20.1", "8.0.0-alpha.4+0.97.0-1.20.4", "9.0.0-alpha.1+0.100.8-1.20.6",
                        "11.0.0-alpha.3+0.102.0-1.21", "12.0.0-alpha.1+0.119.4-1.21.4", "13.0.0-alpha.1+0.136.1-1.21.8",
                        "14.0.0-alpha.1+0.134.0-1.21.9", "14.1.0-alpha.1+0.138.3-1.21.10", "15.0.0-alpha.1+0.141.2-1.21.11", "16.0.0-alpha.1+0.145.1-26.1"));
                m.put("loom", v.unobfuscated() ? "1.14.10" : "1.11.8");
                m.put("gradle", gradle);
            }
            case PAPER, FOLIA -> {
                m.put("api", t == Target.FOLIA ? "folia" : "paper");
                m.put("api_artifact", v.unobfuscated() ? v.id + ".1.build.+" : v.id + "-R0.1-SNAPSHOT");
                m.put("gradle", gradle);
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
