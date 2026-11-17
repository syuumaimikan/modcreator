package modstudio.gen;

/** Small shared file templates for generated Gradle projects. */
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
}
