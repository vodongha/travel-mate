package com.travelmate.admin.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.yaml.snakeyaml.Yaml;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Ops &amp; libs" backing logic for the admin panel: fetch the backend {@code pom.xml} and the app
 * {@code pubspec.yaml} from GitHub, compare each pinned dependency against the latest published
 * version (Maven Central / pub.dev), and list open Dependabot alerts. The result is cached in memory
 * and only recomputed on an explicit refresh, so page loads make no outbound calls.
 */
@Service
public class OpsService {

    private static final Logger log = LoggerFactory.getLogger(OpsService.class);
    private static final Pattern VERSION = Pattern.compile("\\d[\\w.+-]*");
    // pubspec entries that are SDK/git/path refs, not pub.dev packages.
    private static final Set<String> SKIP_PUB = Set.of("flutter", "flutter_localizations", "flutter_test");

    private final OpsFetcher fetcher;
    private final ObjectMapper mapper;
    private final String backendRepo;
    private final String backendRef;
    private final String appRepo;
    private final String appRef;
    private final String githubToken;

    private volatile OpsSnapshot cached;

    public OpsService(OpsFetcher fetcher, ObjectMapper mapper,
                      @Value("${app.ops.backend-repo:vodongha/travel-mate}") String backendRepo,
                      @Value("${app.ops.backend-ref:master}") String backendRef,
                      @Value("${app.ops.app-repo:vodongha/travel-mate-app}") String appRepo,
                      @Value("${app.ops.app-ref:master}") String appRef,
                      @Value("${app.ops.github-token:}") String githubToken) {
        this.fetcher = fetcher;
        this.mapper = mapper;
        this.backendRepo = backendRepo;
        this.backendRef = backendRef;
        this.appRepo = appRepo;
        this.appRef = appRef;
        this.githubToken = githubToken;
    }

    /** The last computed snapshot, or null if {@link #refresh()} hasn't run yet this boot. */
    public OpsSnapshot current() {
        return cached;
    }

    /** Recompute the snapshot (fetch + compare + Dependabot). Per-item failures are collected, not fatal. */
    public OpsSnapshot refresh() {
        List<String> errors = new ArrayList<>();
        List<LibStatus> maven = mavenStatuses(errors);
        List<LibStatus> pub = pubStatuses(errors);
        boolean dependabotConfigured = !githubToken.isBlank();
        List<DependabotAlert> alerts = dependabotConfigured ? dependabotAlerts(errors) : List.of();
        OpsSnapshot snapshot = new OpsSnapshot(Instant.now(), maven, pub, alerts, dependabotConfigured, errors);
        this.cached = snapshot;
        return snapshot;
    }

    // ── Maven (pom.xml → Maven Central) ──────────────────────────────────────

    private List<LibStatus> mavenStatuses(List<String> errors) {
        List<String[]> deps;
        try {
            deps = parsePom(rawGitHub(backendRepo, backendRef, "pom.xml"));
        } catch (Exception e) {
            errors.add("pom.xml: " + e.getMessage());
            return List.of();
        }
        return deps.parallelStream().map(ga -> {
            String name = ga[0] + ":" + ga[1];
            String current = ga[2];
            String latest = mavenLatest(ga[0], ga[1]);
            return new LibStatus(name, current, latest == null ? "?" : latest,
                    latest != null && !latest.equals(current), latest == null ? "lookup failed" : "");
        }).sorted((a, b) -> Boolean.compare(b.outdated(), a.outdated())).toList();
    }

    static List<String[]> parsePom(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        Document doc = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList deps = doc.getElementsByTagName("dependency");
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < deps.getLength(); i++) {
            Element dep = (Element) deps.item(i);
            String g = childText(dep, "groupId");
            String a = childText(dep, "artifactId");
            String v = childText(dep, "version");
            // Only deps with an explicit, literal version (skip BOM-managed and ${property} versions).
            if (g != null && a != null && v != null && !v.startsWith("${")) {
                out.add(new String[]{g, a, v});
            }
        }
        return out;
    }

    private static String childText(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            // Only direct children of <dependency> (avoid nested <exclusion> groupId/artifactId).
            if (n.getParentNode() == parent) {
                return n.getTextContent().trim();
            }
        }
        return null;
    }

    private String mavenLatest(String groupId, String artifactId) {
        try {
            String url = "https://search.maven.org/solrsearch/select?q=g:%22" + groupId
                    + "%22+AND+a:%22" + artifactId + "%22&rows=1&wt=json";
            JsonNode docs = mapper.readTree(fetcher.get(url, Map.of())).path("response").path("docs");
            return docs.isArray() && docs.size() > 0 ? docs.get(0).path("latestVersion").asText(null) : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Flutter (pubspec.yaml → pub.dev) ─────────────────────────────────────

    private List<LibStatus> pubStatuses(List<String> errors) {
        Map<String, String> deps;
        try {
            deps = parsePubspec(rawGitHub(appRepo, appRef, "pubspec.yaml"));
        } catch (Exception e) {
            errors.add("pubspec.yaml: " + e.getMessage());
            return List.of();
        }
        return deps.entrySet().parallelStream().map(entry -> {
            String current = entry.getValue();
            String latest = pubLatest(entry.getKey());
            return new LibStatus(entry.getKey(), current, latest == null ? "?" : latest,
                    latest != null && current != null && !latest.equals(current),
                    latest == null ? "lookup failed" : (current == null ? "no pinned version" : ""));
        }).sorted((a, b) -> Boolean.compare(b.outdated(), a.outdated())).toList();
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> parsePubspec(String yaml) {
        Map<String, Object> root = new Yaml().load(yaml);
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (String section : List.of("dependencies", "dev_dependencies")) {
            Object node = root.get(section);
            if (!(node instanceof Map)) {
                continue;
            }
            for (Map.Entry<String, Object> e : ((Map<String, Object>) node).entrySet()) {
                if (SKIP_PUB.contains(e.getKey())) {
                    continue;
                }
                // String value = a version constraint; a Map = sdk/git/path ref (skip).
                if (e.getValue() instanceof String s) {
                    out.put(e.getKey(), normalizeVersion(s));
                }
            }
        }
        return out;
    }

    /** "^2.6.1" / ">=3.5.0 <4.0.0" → "2.6.1"; "any" → null (nothing to compare). */
    static String normalizeVersion(String constraint) {
        Matcher m = VERSION.matcher(constraint);
        return m.find() ? m.group() : null;
    }

    private String pubLatest(String name) {
        try {
            String body = fetcher.get("https://pub.dev/api/packages/" + name,
                    Map.of("Accept", "application/json"));
            return mapper.readTree(body).path("latest").path("version").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Dependabot (GitHub API) ──────────────────────────────────────────────

    private List<DependabotAlert> dependabotAlerts(List<String> errors) {
        List<DependabotAlert> out = new ArrayList<>();
        for (String repo : List.of(backendRepo, appRepo)) {
            try {
                String body = fetcher.get(
                        "https://api.github.com/repos/" + repo + "/dependabot/alerts?state=open&per_page=100",
                        Map.of("Authorization", "Bearer " + githubToken,
                                "Accept", "application/vnd.github+json",
                                "X-GitHub-Api-Version", "2022-11-28"));
                for (JsonNode a : mapper.readTree(body)) {
                    JsonNode adv = a.path("security_advisory");
                    out.add(new DependabotAlert(
                            repo,
                            adv.path("severity").asText(""),
                            a.path("dependency").path("package").path("name").asText(""),
                            adv.path("summary").asText(""),
                            a.path("html_url").asText("")));
                }
            } catch (Exception e) {
                errors.add("Dependabot " + repo + ": " + e.getMessage());
            }
        }
        return out;
    }

    private String rawGitHub(String repo, String ref, String path) throws Exception {
        return fetcher.get("https://raw.githubusercontent.com/" + repo + "/" + ref + "/" + path, Map.of());
    }

    // ── view models ──────────────────────────────────────────────────────────

    public record LibStatus(String name, String current, String latest, boolean outdated, String note) {
    }

    public record DependabotAlert(String repo, String severity, String packageName, String summary,
                                  String url) {
    }

    public record OpsSnapshot(Instant checkedAt, List<LibStatus> maven, List<LibStatus> pub,
                              List<DependabotAlert> alerts, boolean dependabotConfigured,
                              List<String> errors) {
    }
}
