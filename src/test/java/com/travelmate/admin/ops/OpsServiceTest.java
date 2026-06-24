package com.travelmate.admin.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmate.admin.ops.OpsService.LibStatus;
import com.travelmate.admin.ops.OpsService.OpsSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Parsing (pom/pubspec) + outdated detection, driven through a canned {@link OpsFetcher}. */
class OpsServiceTest {

    private static final String POM = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <dependencies>
                <dependency>
                  <groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId><version>0.12.6</version>
                </dependency>
                <dependency>
                  <groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId>
                </dependency>
                <dependency>
                  <groupId>com.x</groupId><artifactId>y</artifactId><version>${some.prop}</version>
                </dependency>
              </dependencies>
            </project>
            """;

    private static final String PUBSPEC = """
            name: app
            environment:
              sdk: ">=3.5.0 <4.0.0"
            dependencies:
              flutter:
                sdk: flutter
              dio: ^5.7.0
              intl: any
            dev_dependencies:
              flutter_lints: ^5.0.0
            """;

    /** Canned responses keyed by a URL substring. */
    private final OpsFetcher fetcher = (url, headers) -> {
        if (url.contains("/pom.xml")) return POM;
        if (url.contains("/pubspec.yaml")) return PUBSPEC;
        if (url.contains("search.maven.org")) return "{\"response\":{\"docs\":[{\"latestVersion\":\"0.12.7\"}]}}";
        if (url.contains("pub.dev/api/packages/dio")) return "{\"latest\":{\"version\":\"5.8.0\"}}";
        if (url.contains("pub.dev/api/packages/intl")) return "{\"latest\":{\"version\":\"0.20.0\"}}";
        if (url.contains("pub.dev/api/packages/flutter_lints")) return "{\"latest\":{\"version\":\"5.0.0\"}}";
        throw new IllegalStateException("unexpected url " + url);
    };

    private OpsService service() {
        return new OpsService(fetcher, new ObjectMapper(),
                "vodongha/travel-mate", "master", "vodongha/travel-mate-app", "master", "");
    }

    @Test
    void parsesOnlyExplicitVersionMavenDeps() throws Exception {
        var deps = OpsService.parsePom(POM);
        // jjwt only; the BOM-managed (no version) and ${prop} deps are skipped.
        assertThat(deps).hasSize(1);
        assertThat(deps.get(0)).containsExactly("io.jsonwebtoken", "jjwt-api", "0.12.6");
    }

    @Test
    void parsesPubspecSkippingSdkRefs() {
        Map<String, String> deps = OpsService.parsePubspec(PUBSPEC);
        assertThat(deps).doesNotContainKey("flutter");      // sdk ref skipped
        assertThat(deps.get("dio")).isEqualTo("5.7.0");      // ^ stripped
        assertThat(deps.get("intl")).isNull();               // "any" → no comparable version
        assertThat(deps).containsKey("flutter_lints");
    }

    @Test
    void normalizeVersionStripsConstraintPrefixes() {
        assertThat(OpsService.normalizeVersion("^2.6.1")).isEqualTo("2.6.1");
        assertThat(OpsService.normalizeVersion(">=3.5.0 <4.0.0")).isEqualTo("3.5.0");
        assertThat(OpsService.normalizeVersion("any")).isNull();
    }

    @Test
    void refreshFlagsOutdatedAndCaches() {
        OpsService service = service();
        OpsSnapshot snap = service.refresh();

        LibStatus jjwt = snap.maven().get(0);
        assertThat(jjwt.name()).isEqualTo("io.jsonwebtoken:jjwt-api");
        assertThat(jjwt.current()).isEqualTo("0.12.6");
        assertThat(jjwt.latest()).isEqualTo("0.12.7");
        assertThat(jjwt.outdated()).isTrue();

        LibStatus dio = snap.pub().stream().filter(l -> l.name().equals("dio")).findFirst().orElseThrow();
        assertThat(dio.outdated()).isTrue();
        LibStatus lints = snap.pub().stream().filter(l -> l.name().equals("flutter_lints")).findFirst().orElseThrow();
        assertThat(lints.outdated()).isFalse();              // 5.0.0 == 5.0.0

        assertThat(snap.dependabotConfigured()).isFalse();   // blank token
        assertThat(service.current()).isSameAs(snap);        // cached
    }
}
