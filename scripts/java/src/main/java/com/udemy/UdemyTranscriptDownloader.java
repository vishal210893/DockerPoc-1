package com.udemy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Udemy Transcript Downloader (Java)
 * ====================================
 * Downloads transcripts chapter-wise from a Udemy course using Udemy's internal API,
 * then generates clean HTML documentation.
 *
 * HOW TO GET YOUR CREDENTIALS (Cookie method - most reliable):
 * 1. Log into Udemy in Chrome/Firefox
 * 2. Open DevTools (F12) -> Network tab
 * 3. Refresh the course page
 * 4. Click any request to 'www.udemy.com/api-2.0/...'
 * 5. Scroll to 'Request Headers' -> find the 'cookie:' header
 * 6. Copy the ENTIRE cookie string value
 *
 * BUILD:
 *   mvn clean package
 *
 * RUN (cookie - recommended):
 *   java -jar target/udemy-transcript-downloader-jar-with-dependencies.jar \
 *       --cookie "PASTE_FULL_COOKIE_STRING_HERE" \
 *       --course-url "https://www.udemy.com/course/claudecode/" \
 *       --output-dir "./udemy_docs"
 *
 * RUN (access token only):
 *   java -jar target/udemy-transcript-downloader-jar-with-dependencies.jar \
 *       --access-token "YOUR_ACCESS_TOKEN_VALUE" \
 *       --course-url "https://www.udemy.com/course/claudecode/" \
 *       --output-dir "./udemy_docs"
 */
public class UdemyTranscriptDownloader {

    private static final String BASE_URL = "https://www.udemy.com/api-2.0";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    // ── Data records ──────────────────────────────────────────────────────

    record Lecture(String title, int index, int lectureId, long assetId,
                   String assetType, List<JsonNode> captions) {}

    record Chapter(String title, int index, List<Lecture> lectures) {}

    // ── Auth helpers ──────────────────────────────────────────────────────

    static Map<String, String> getHeaders(String accessToken, String cookieStr, String csrfToken) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Accept-Language", "en-US");
        headers.put("Referer", "https://www.udemy.com/");
        headers.put("X-Requested-With", "XMLHttpRequest");
        headers.put("User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36");
        if (cookieStr != null && !cookieStr.isEmpty()) {
            headers.put("Cookie", cookieStr);
            if (csrfToken != null && !csrfToken.isEmpty()) {
                headers.put("X-CSRFToken", csrfToken);
            }
        } else if (accessToken != null && !accessToken.isEmpty()) {
            headers.put("Authorization", "Bearer " + accessToken);
        }
        return headers;
    }

    /** Extract access_token and csrftoken from a raw cookie header string. */
    static String[] parseCookieString(String cookieStr) {
        String accessToken = "";
        String csrfToken = "";
        for (String part : cookieStr.split(";")) {
            part = part.strip();
            if (part.startsWith("access_token=")) {
                accessToken = part.substring("access_token=".length()).replace("\"", "");
            } else if (part.startsWith("csrftoken=")) {
                csrfToken = part.substring("csrftoken=".length());
            }
        }
        return new String[]{accessToken, csrfToken};
    }

    /** Extract slug from URL like https://www.udemy.com/course/claudecode/ */
    static String extractCourseSlug(String courseUrl) {
        Matcher m = Pattern.compile("/course/([^/?#]+)").matcher(courseUrl);
        if (!m.find()) throw new IllegalArgumentException("Cannot extract course slug from URL: " + courseUrl);
        return m.group(1);
    }

    // ── HTTP helper ───────────────────────────────────────────────────────

    /**
     * Performs a GET request with the given headers.
     * Returns null on 403 (forbidden), throws on other 4xx/5xx errors.
     */
    static String httpGet(String url, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET();
        headers.forEach(builder::header);
        HttpResponse<String> resp = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() == 403) return null;
        if (resp.statusCode() >= 400) throw new IOException("HTTP " + resp.statusCode() + " for " + url);
        return resp.body();
    }

    // ── Udemy API ─────────────────────────────────────────────────────────

    /** Resolve course slug → numeric course ID. */
    static long getCourseId(String slug, Map<String, String> headers) throws Exception {
        String url = BASE_URL + "/courses/" + slug + "/?fields[course]=id,title";
        String body = httpGet(url, headers);
        if (body == null) throw new IOException("403 Forbidden — check your credentials and course enrollment.");
        return JSON.readTree(body).get("id").asLong();
    }

    /** Fetch all curriculum items (chapters + lectures) with pagination. */
    static List<JsonNode> getCurriculum(long courseId, Map<String, String> headers) throws Exception {
        List<JsonNode> items = new ArrayList<>();
        int page = 1;
        while (true) {
            String url = BASE_URL + "/courses/" + courseId + "/subscriber-curriculum-items/" +
                    "?page=" + page + "&page_size=200" +
                    "&fields[lecture]=title,asset,sort_order,object_index,id" +
                    "&fields[chapter]=title,object_index,sort_order" +
                    "&fields[asset]=asset_type,captions,title,length,id" +
                    "&fields[quiz]=title,object_index,sort_order";
            String body = httpGet(url, headers);
            if (body == null) throw new IOException("403 Forbidden fetching curriculum.");
            JsonNode data = JSON.readTree(body);
            data.get("results").forEach(items::add);
            if (data.get("next").isNull()) break;
            page++;
            Thread.sleep(500);
        }
        return items;
    }

    /** Fallback: fetch captions for a single asset via dedicated endpoint. */
    static List<JsonNode> getCaptionsForAsset(long assetId, Map<String, String> headers) {
        try {
            String url = BASE_URL + "/assets/" + assetId + "/captions/";
            String body = httpGet(url, headers);
            if (body == null) return Collections.emptyList();
            List<JsonNode> result = new ArrayList<>();
            JSON.readTree(body).get("results").forEach(result::add);
            return result;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** Download VTT file content from URL. */
    static String downloadVtt(String vttUrl, Map<String, String> headers) {
        try {
            String body = httpGet(vttUrl, headers);
            return body != null ? body : "";
        } catch (Exception e) {
            System.out.println("    ⚠  Could not download VTT: " + e.getMessage());
            return "";
        }
    }

    // ── VTT → plain text ─────────────────────────────────────────────────

    /** Convert WebVTT format to clean plain text. */
    static String vttToText(String vttContent) {
        if (vttContent == null || vttContent.isEmpty()) return "";

        Pattern cueNumPat   = Pattern.compile("^\\d+$");
        Pattern timestampPat = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}[.,]\\d{3}\\s*-->");
        Pattern inlineTagPat = Pattern.compile("<[^>]+>");

        List<String> textLines = new ArrayList<>();
        boolean skipNext = false;

        for (String raw : vttContent.split("\r?\n")) {
            String line = raw.strip();
            if (line.startsWith("WEBVTT") || line.startsWith("NOTE")) continue;
            if (cueNumPat.matcher(line).matches())    { skipNext = true;  continue; }
            if (timestampPat.matcher(line).find())    { skipNext = false; continue; }
            if (skipNext)                             { skipNext = false; continue; }
            if (!line.isEmpty()) {
                textLines.add(inlineTagPat.matcher(line).replaceAll(""));
            }
        }

        // Deduplicate adjacent identical lines (common in VTT)
        List<String> deduped = new ArrayList<>();
        String prev = null;
        for (String l : textLines) {
            if (!l.equals(prev)) { deduped.add(l); prev = l; }
        }
        return String.join(" ", deduped).strip();
    }

    // ── Organise curriculum ───────────────────────────────────────────────

    static List<Chapter> organiseChapters(List<JsonNode> curriculum) {
        List<Chapter> chapters = new ArrayList<>();
        List<Lecture> currentLectures = null;

        for (JsonNode item : curriculum) {
            String cls = item.path("_class").asText();

            if ("chapter".equals(cls)) {
                currentLectures = new ArrayList<>();
                chapters.add(new Chapter(
                        item.path("title").asText("Untitled Chapter"),
                        item.path("object_index").asInt(0),
                        currentLectures
                ));

            } else if ("lecture".equals(cls)) {
                if (currentLectures == null) {
                    currentLectures = new ArrayList<>();
                    chapters.add(new Chapter("Introduction", 0, currentLectures));
                }
                JsonNode asset = item.path("asset");
                String assetType = asset.path("asset_type").asText("");

                if ("Video".equals(assetType) || "Article".equals(assetType)) {
                    List<JsonNode> captions = new ArrayList<>();
                    asset.path("captions").forEach(captions::add);

                    currentLectures.add(new Lecture(
                            item.path("title").asText("Untitled Lecture"),
                            item.path("object_index").asInt(0),
                            item.path("id").asInt(0),
                            asset.path("id").asLong(0),
                            assetType,
                            captions
                    ));
                }
            }
        }
        return chapters;
    }

    // ── HTML generator ────────────────────────────────────────────────────

    static final String HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>%s \u2013 Course Documentation</title>
              <style>
                :root {
                  --bg: #0f1117; --surface: #1a1d27; --surface2: #23263a;
                  --accent: #a78bfa; --accent2: #60a5fa;
                  --text: #e2e8f0; --muted: #94a3b8;
                  --border: #2d3148; --radius: 10px;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body { background: var(--bg); color: var(--text);
                  font-family: 'Segoe UI', system-ui, -apple-system, sans-serif;
                  display: flex; min-height: 100vh; }
                #sidebar { width: 300px; min-width: 260px; background: var(--surface);
                  border-right: 1px solid var(--border); padding: 20px 0;
                  position: sticky; top: 0; height: 100vh; overflow-y: auto; }
                #sidebar h1 { font-size: 1rem; font-weight: 700; color: var(--accent);
                  padding: 0 20px 16px; border-bottom: 1px solid var(--border); margin-bottom: 12px; }
                .chapter-link { display: block; padding: 8px 20px; color: var(--muted);
                  text-decoration: none; font-size: 0.82rem; font-weight: 600;
                  text-transform: uppercase; letter-spacing: .04em; transition: color .2s; }
                .chapter-link:hover { color: var(--accent); }
                .lecture-link { display: block; padding: 5px 20px 5px 32px; color: var(--text);
                  text-decoration: none; font-size: 0.82rem; line-height: 1.4;
                  transition: color .2s, background .2s; border-radius: 4px; margin: 1px 8px; }
                .lecture-link:hover { background: var(--surface2); color: var(--accent2); }
                #main { flex: 1; padding: 40px 48px; max-width: 960px; margin: 0 auto; }
                .course-header { margin-bottom: 48px; padding-bottom: 24px; border-bottom: 2px solid var(--border); }
                .course-header h1 { font-size: 2rem; font-weight: 800;
                  background: linear-gradient(135deg, var(--accent), var(--accent2));
                  -webkit-background-clip: text; -webkit-text-fill-color: transparent;
                  background-clip: text; margin-bottom: 8px; }
                .course-header p { color: var(--muted); font-size: 0.9rem; }
                .chapter { margin-bottom: 56px; }
                .chapter-title { font-size: 1.35rem; font-weight: 700; color: var(--accent);
                  margin-bottom: 20px; padding-bottom: 10px; border-bottom: 1px solid var(--border);
                  display: flex; align-items: center; gap: 10px; }
                .chapter-num { background: var(--accent); color: #fff; font-size: 0.75rem;
                  font-weight: 800; padding: 2px 8px; border-radius: 20px; letter-spacing: .05em; }
                .lecture { background: var(--surface); border: 1px solid var(--border);
                  border-radius: var(--radius); margin-bottom: 20px; overflow: hidden; }
                .lecture-header { background: var(--surface2); padding: 14px 20px;
                  display: flex; align-items: center; gap: 12px; cursor: pointer; user-select: none; }
                .lecture-num { color: var(--accent2); font-size: 0.75rem; font-weight: 700; min-width: 28px; }
                .lecture-title { font-size: 0.95rem; font-weight: 600; flex: 1; }
                .toggle-icon { color: var(--muted); font-size: 1rem; transition: transform .25s; }
                .lecture-body { padding: 20px; display: none; }
                .lecture-body.open { display: block; }
                .transcript { font-size: 0.9rem; line-height: 1.8; color: var(--text); white-space: pre-wrap; }
                .no-transcript { color: var(--muted); font-style: italic; font-size: 0.85rem; }
                #search-wrap { padding: 0 12px 12px; }
                #search { width: 100%%; padding: 8px 12px; background: var(--surface2);
                  border: 1px solid var(--border); border-radius: 6px; color: var(--text);
                  font-size: 0.82rem; outline: none; }
                #search::placeholder { color: var(--muted); }
                #search:focus { border-color: var(--accent); }
                ::-webkit-scrollbar { width: 6px; }
                ::-webkit-scrollbar-track { background: transparent; }
                ::-webkit-scrollbar-thumb { background: var(--border); border-radius: 3px; }
              </style>
            </head>
            <body>
            <nav id="sidebar">
              <h1>\uD83D\uDCDA %s</h1>
              <div id="search-wrap"><input id="search" type="text" placeholder="Search transcripts\u2026" /></div>
              <div id="toc">%s</div>
            </nav>
            <main id="main">
              <header class="course-header">
                <h1>%s</h1>
                <p>Auto-generated documentation from video transcripts \u00B7 %d lectures \u00B7 %d chapters</p>
              </header>
            %s
            </main>
            <script>
              document.querySelectorAll('.lecture-header').forEach(h => {
                h.addEventListener('click', () => {
                  const b = h.nextElementSibling, i = h.querySelector('.toggle-icon');
                  b.classList.toggle('open');
                  i.textContent = b.classList.contains('open') ? '\u25b2' : '\u25bc';
                });
              });
              const search = document.getElementById('search');
              search.addEventListener('input', () => {
                const q = search.value.toLowerCase().trim();
                document.querySelectorAll('.lecture').forEach(lec => {
                  const match = !q || lec.textContent.toLowerCase().includes(q);
                  lec.style.display = match ? '' : 'none';
                  if (match && q) {
                    lec.querySelector('.lecture-body').classList.add('open');
                    lec.querySelector('.toggle-icon').textContent = '\u25b2';
                  }
                });
                document.querySelectorAll('.chapter').forEach(ch => {
                  ch.style.display = [...ch.querySelectorAll('.lecture')].some(l => l.style.display !== 'none') ? '' : 'none';
                });
              });
            </script>
            </body>
            </html>
            """;

    static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    static String buildHtml(String courseTitle, List<Chapter> chapters,
                             Map<Chapter, Map<Lecture, String>> transcriptMap) {
        int lectureCount = chapters.stream().mapToInt(c -> c.lectures().size()).sum();
        StringBuilder toc  = new StringBuilder();
        StringBuilder body = new StringBuilder();

        for (Chapter ch : chapters) {
            String chId = "chapter-" + ch.index();
            toc.append(String.format(
                    "    <a class=\"chapter-link\" href=\"#%s\">%d. %s</a>\n",
                    chId, ch.index(), escapeHtml(ch.title())));

            StringBuilder lecHtml = new StringBuilder();
            for (Lecture lec : ch.lectures()) {
                String lecId = "lecture-" + ch.index() + "-" + lec.index();
                toc.append(String.format(
                        "    <a class=\"lecture-link\" href=\"#%s\">%d. %s</a>\n",
                        lecId, lec.index(), escapeHtml(lec.title())));

                String transcript = transcriptMap
                        .getOrDefault(ch, Map.of())
                        .getOrDefault(lec, "");
                String transcriptHtml = transcript.isEmpty()
                        ? "<p class=\"no-transcript\">Transcript not available for this lecture.</p>"
                        : "<div class=\"transcript\">" + escapeHtml(transcript) + "</div>";

                lecHtml.append(String.format("""
                        <div class="lecture" id="%s">
                          <div class="lecture-header">
                            <span class="lecture-num">#%d</span>
                            <span class="lecture-title">%s</span>
                            <span class="toggle-icon">\u25bc</span>
                          </div>
                          <div class="lecture-body">%s</div>
                        </div>
                        """, lecId, lec.index(), escapeHtml(lec.title()), transcriptHtml));
            }

            body.append(String.format("""
                    <section class="chapter" id="%s">
                      <h2 class="chapter-title"><span class="chapter-num">Ch %d</span>%s</h2>
                    %s
                    </section>
                    """, chId, ch.index(), escapeHtml(ch.title()), lecHtml));
        }

        // HTML_TEMPLATE args: title, courseTitle(sidebar), toc, courseTitle(main), lectureCount, chapterCount, body
        return String.format(HTML_TEMPLATE,
                courseTitle, courseTitle, toc,
                courseTitle, lectureCount, chapters.size(),
                body);
    }

    // ── Main ──────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        String cookie = null, accessToken = null, courseUrl = null;
        String outputDir = "./udemy_docs", lang = "en";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--cookie"        -> cookie      = args[++i];
                case "--access-token"  -> accessToken = args[++i];
                case "--course-url"    -> courseUrl   = args[++i];
                case "--output-dir"    -> outputDir   = args[++i];
                case "--lang"          -> lang        = args[++i];
            }
        }

        if (courseUrl == null || (cookie == null && accessToken == null)) {
            System.err.println("""
                    Usage:
                      java -jar udemy-transcript-downloader-jar-with-dependencies.jar \\
                          --cookie "FULL_COOKIE_STRING" \\
                          --course-url "https://www.udemy.com/course/claudecode/" \\
                          [--output-dir ./udemy_docs] [--lang en]

                      OR

                      java -jar udemy-transcript-downloader-jar-with-dependencies.jar \\
                          --access-token "YOUR_TOKEN" \\
                          --course-url "https://www.udemy.com/course/claudecode/"
                    """);
            System.exit(1);
        }

        // Build auth headers
        Map<String, String> headers;
        if (cookie != null) {
            String[] parsed = parseCookieString(cookie);
            headers = getHeaders("", cookie, parsed[1]);
            System.out.println("[AUTH] Cookie-based auth (access_token found: " + (!parsed[0].isEmpty() ? "yes" : "no") + ")");
        } else {
            headers = getHeaders(accessToken, "", "");
            System.out.println("[AUTH] Bearer token auth");
        }

        Path outDir = Paths.get(outputDir);
        Files.createDirectories(outDir);

        // Step 1 – slug
        String slug = extractCourseSlug(courseUrl);
        System.out.println("[1/5] Course slug: " + slug);

        // Step 2 – course ID
        System.out.println("[2/5] Fetching course ID\u2026");
        long courseId;
        try {
            courseId = getCourseId(slug, headers);
        } catch (Exception e) {
            System.err.println("      ERROR: " + e.getMessage());
            System.err.println("      Hint: Check your credentials and make sure you are enrolled in the course.");
            System.exit(1);
            return;
        }
        // Title-case the slug for a human-readable course title
        String courseTitle = Arrays.stream(slug.split("-"))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b);
        System.out.println("      Course ID: " + courseId);

        // Step 3 – curriculum
        System.out.println("[3/5] Fetching curriculum\u2026");
        List<JsonNode> curriculum = getCurriculum(courseId, headers);
        List<Chapter> chapters = organiseChapters(curriculum);
        int lecCount = chapters.stream().mapToInt(c -> c.lectures().size()).sum();
        System.out.printf("      %d chapters, %d lectures found.%n", chapters.size(), lecCount);

        // Step 4 – transcripts
        System.out.println("[4/5] Downloading transcripts\u2026");
        ObjectNode rawData = JSON.createObjectNode();
        rawData.put("course_title", courseTitle);
        ArrayNode chaptersNode = rawData.putArray("chapters");
        Map<Chapter, Map<Lecture, String>> transcriptMap = new LinkedHashMap<>();

        final String langFinal = lang;
        int chNum = 0;
        for (Chapter chapter : chapters) {
            chNum++;
            System.out.printf("%n  Chapter %d/%d: %s%n", chNum, chapters.size(), chapter.title());

            ObjectNode chNode = chaptersNode.addObject();
            chNode.put("title", chapter.title());
            chNode.put("index", chapter.index());
            ArrayNode lecturesNode = chNode.putArray("lectures");

            Map<Lecture, String> lecTranscripts = new LinkedHashMap<>();
            transcriptMap.put(chapter, lecTranscripts);

            for (Lecture lec : chapter.lectures()) {
                String transcriptText = "";

                if (lec.assetId() > 0) {
                    // Use captions embedded in curriculum response first
                    List<JsonNode> captions = new ArrayList<>(lec.captions());

                    // Fall back to separate API call if none embedded
                    if (captions.isEmpty()) {
                        captions = getCaptionsForAsset(lec.assetId(), headers);
                    }

                    // Pick preferred language, fall back to first available
                    JsonNode caption = captions.stream()
                            .filter(c -> c.path("locale_id").asText("").startsWith(langFinal))
                            .findFirst()
                            .orElse(captions.isEmpty() ? null : captions.get(0));

                    if (caption != null) {
                        String vttUrl = caption.path("url").asText(null);
                        if (vttUrl == null || vttUrl.isEmpty()) vttUrl = caption.path("vtt_url").asText(null);
                        if (vttUrl == null || vttUrl.isEmpty()) vttUrl = caption.path("file_url").asText(null);

                        if (vttUrl != null && !vttUrl.isEmpty()) {
                            System.out.printf("    \u2713 %3d. %s%n",
                                    lec.index(), lec.title().substring(0, Math.min(60, lec.title().length())));
                            transcriptText = vttToText(downloadVtt(vttUrl, headers));
                        } else {
                            System.out.printf("    - %3d. %s (no VTT URL in caption)%n",
                                    lec.index(), lec.title().substring(0, Math.min(60, lec.title().length())));
                        }
                    } else {
                        System.out.printf("    \u2717 %3d. %s (no captions found)%n",
                                lec.index(), lec.title().substring(0, Math.min(60, lec.title().length())));
                    }

                    Thread.sleep(300);
                }

                lecTranscripts.put(lec, transcriptText);

                ObjectNode lecNode = lecturesNode.addObject();
                lecNode.put("title", lec.title());
                lecNode.put("index", lec.index());
                lecNode.put("asset_type", lec.assetType());
                lecNode.put("transcript", transcriptText);
            }
        }

        // Save raw JSON
        Path jsonPath = outDir.resolve("transcripts.json");
        JSON.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), rawData);
        System.out.println("\n  Raw transcripts saved \u2192 " + jsonPath);

        // Step 5 – HTML
        System.out.println("\n[5/5] Generating HTML documentation\u2026");
        String html = buildHtml(courseTitle, chapters, transcriptMap);
        Path htmlPath = outDir.resolve("documentation.html");
        Files.writeString(htmlPath, html, StandardCharsets.UTF_8);
        System.out.println("  HTML documentation saved \u2192 " + htmlPath);
        System.out.println("\n\u2705 Done! Open the HTML file in your browser.");
    }
}
