package modstudio.gen;

import modstudio.core.Elements.CodeFile;
import modstudio.core.ModElements.GradleDep;
import modstudio.core.Target;

/** Small shared file templates and Gradle helpers for generated projects. */
public final class Scaffold {
    private Scaffold() {}

    public static final String GITIGNORE = """
            .gradle/
            build/
            run/
            runs/
            out/
            .idea/
            *.iml
            .vscode/
            bin/
            """;

    public static String wrapperProperties(String gradle) {
        return """
                distributionBase=GRADLE_USER_HOME
                distributionPath=wrapper/dists
                distributionUrl=https\\://services.gradle.org/distributions/gradle-%s-bin.zip
                networkTimeout=10000
                validateDistributionUrl=true
                zipStoreBase=GRADLE_USER_HOME
                zipStorePath=wrapper/dists
                """.formatted(gradle);
    }

    /** True when a CodeFile or dependency target selector applies to the current build target. */
    public static boolean matchesTarget(String selector, Target target) {
        return Target.matchesSelector(selector, target);
    }

    /** Resolved output path of a matching CodeFile whose path ends with the given suffix, or null. */
    public static String findCodeFile(GenContext ctx, String pathSuffix) {
        for (CodeFile cf : ctx.all(CodeFile.class)) {
            if (!matchesTarget(cf.target, ctx.target) || cf.path == null || cf.path.isBlank()) continue;
            String p = cf.path.replace("{package}", ctx.pkgPath).replace("{modid}", ctx.ns);
            if (p.toLowerCase(java.util.Locale.ROOT).endsWith(pathSuffix.toLowerCase(java.util.Locale.ROOT))) return p;
        }
        return null;
    }

    /** Custom Maven repository entries for repositories { ... }. */
    public static String mavenRepos(GenContext ctx, String indent) {
        if (ctx.p.meta.mavenRepos == null || ctx.p.meta.mavenRepos.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String raw : ctx.p.meta.mavenRepos) {
            if (raw == null || raw.isBlank()) continue;
            String u = raw.trim();
            if (u.startsWith("maven") || u.startsWith("flatDir")) {
                for (String line : u.split("\\R")) sb.append(indent).append(line).append('\n');
            } else {
                sb.append(indent).append("maven { url = '").append(u.replace("'", "\\'")).append("' }\n");
            }
        }
        return sb.toString();
    }

    /** Custom Gradle dependencies for dependencies { ... }. */
    public static String gradleDeps(GenContext ctx, String indent, boolean loom, boolean unobf) {
        if (ctx.p.meta.libraries == null || ctx.p.meta.libraries.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (GradleDep d : ctx.p.meta.libraries) {
            if (d == null || !matchesTarget(d.target, ctx.target)) continue;
            String coord = d.coordinate == null ? "" : d.coordinate.trim();
            if (coord.isBlank()) continue;
            String scope = d.scope == null ? "implementation" : d.scope.trim();
            String q = coord.replace("'", "\\'");
            switch (scope) {
                case "localJar" -> {
                    String depScope = loom && !unobf ? "modImplementation" : "implementation";
                    sb.append(indent).append(depScope).append(" files('").append(q.replace("\\", "/")).append("')\n");
                }
                case "jarJar" -> {
                    if (loom) {
                        sb.append(indent).append("include '").append(q).append("'\n");
                        sb.append(indent).append(unobf ? "implementation" : "modImplementation").append(" '").append(q).append("'\n");
                    } else if (ctx.target.isMod()) {
                        sb.append(indent).append("jarJar '").append(q).append("'\n");
                        sb.append(indent).append("implementation '").append(q).append("'\n");
                    } else {
                        sb.append(indent).append("implementation '").append(q).append("'\n");
                    }
                }
                case "modImplementation", "modCompileOnly" -> {
                    String resolved = loom && !unobf ? scope : scope.equals("modCompileOnly") ? "compileOnly" : "implementation";
                    sb.append(indent).append(resolved).append(" '").append(q).append("'\n");
                }
                default -> sb.append(indent).append(scope).append(" '").append(q).append("'\n");
            }
        }
        return sb.toString();
    }

    /** True when the project has at least one matching CodeFile in the given resolved language. */
    public static boolean hasJvmLang(GenContext ctx, String lang) {
        for (CodeFile cf : ctx.all(CodeFile.class)) {
            if (matchesTarget(cf.target, ctx.target) && lang.equals(cf.resolvedLanguage())) return true;
        }
        return false;
    }

    /** Optional Gradle plugins block lines for Kotlin / Groovy / Scala CodeFiles. */
    public static String jvmPlugins(GenContext ctx) {
        StringBuilder sb = new StringBuilder();
        if (hasJvmLang(ctx, "kotlin")) sb.append("    id 'org.jetbrains.kotlin.jvm' version '2.1.20'\n");
        if (hasJvmLang(ctx, "groovy")) sb.append("    id 'groovy'\n");
        if (hasJvmLang(ctx, "scala")) sb.append("    id 'scala'\n");
        return sb.toString();
    }

    /** Optional dependencies block lines for Kotlin / Groovy / Scala CodeFiles. */
    public static String jvmDeps(GenContext ctx, boolean loom, boolean unobf) {
        StringBuilder sb = new StringBuilder();
        if (hasJvmLang(ctx, "kotlin")) {
            if (loom && (ctx.target == Target.FABRIC || ctx.target == Target.QUILT)) {
                String kdep = "net.fabricmc:fabric-language-kotlin:1.13.2+kotlin.2.1.20";
                sb.append("    ").append(unobf ? "implementation" : "modImplementation").append(" '").append(kdep).append("'\n");
            } else {
                sb.append("    implementation 'org.jetbrains.kotlin:kotlin-stdlib:2.1.20'\n");
                if (ctx.target.isMod() && !loom) sb.append("    jarJar 'org.jetbrains.kotlin:kotlin-stdlib:2.1.20'\n");
            }
        }
        if (hasJvmLang(ctx, "groovy")) {
            sb.append("    implementation 'org.apache.groovy:groovy:4.0.24'\n");
            if (loom) sb.append("    include 'org.apache.groovy:groovy:4.0.24'\n");
            else if (ctx.target.isMod()) sb.append("    jarJar 'org.apache.groovy:groovy:4.0.24'\n");
        }
        if (hasJvmLang(ctx, "scala")) {
            sb.append("    implementation 'org.scala-lang:scala3-library_3:3.5.2'\n");
            if (loom) sb.append("    include 'org.scala-lang:scala3-library_3:3.5.2'\n");
            else if (ctx.target.isMod()) sb.append("    jarJar 'org.scala-lang:scala3-library_3:3.5.2'\n");
        }
        return sb.toString();
    }

    /** Optional language toolchain block for build.gradle. */
    public static String jvmFooter(GenContext ctx) {
        if (!hasJvmLang(ctx, "kotlin")) return "";
        return "\nkotlin {\n    jvmToolchain(" + Math.min(23, ctx.v.java) + ")\n}\n";
    }

    /** Raw custom build.gradle block appended at the end of build.gradle. */
    public static String customGradle(GenContext ctx) {
        String c = ctx.p.meta.customGradle == null ? "" : ctx.p.meta.customGradle.strip();
        return c.isEmpty() ? "" : "\n" + c + "\n";
    }
}
