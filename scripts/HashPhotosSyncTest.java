import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hash Photos CSV -> Hash Photos.mm
 * 年/月/日/活动，每天仅一个活动节点，直接替换标题，保留子节点备注。
 */
public class HashPhotosSyncTest {

    private static final String ACTIVITIES_MARKER = "ID=\"ID_966018587\"";
    private static final String TITLES_FILE = ".hashphotos-titles.properties";
    private static final Pattern DATE_KEY = Pattern.compile("^\\d{8}$");
    private static final Pattern ATTR_SYNCED = Pattern.compile(
            "<attribute NAME=\"hashPhotosTitle\"[^>]*/>", Pattern.CASE_INSENSITIVE);

    static class ExistingEvent {
        String fullText;
        String childXml = "";
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: HashPhotosSyncTest <path-to-Hash Photos.mm>");
            System.exit(1);
        }
        File mapFile = new File(args[0]);
        File exportDir = new File(mapFile.getParentFile(), ".files" + File.separator + "Hash Photos");
        File csvFile = findLatestCsv(exportDir);
        if (csvFile == null) {
            System.err.println("未找到 CSV");
            System.exit(1);
        }

        Charset charset = detectCharset(mapFile);
        Map<String, String> desired = readCsv(csvFile);
        Map<String, String> titleStore = loadTitleStore(exportDir);

        String content = readText(mapFile, charset);
        int marker = content.indexOf(ACTIVITIES_MARKER);
        int openEnd = content.indexOf('>', marker);
        int closeStart = findActivitiesClose(content, openEnd);
        String activitiesBody = content.substring(openEnd + 1, closeStart);
        Map<String, ExistingEvent> existing = parseExisting(activitiesBody);

        Map<String, String> newStore = new HashMap<>();
        String body = buildBody(desired, existing, titleStore, newStore, System.currentTimeMillis());
        String result = content.substring(0, openEnd + 1) + body + content.substring(closeStart);
        writeText(mapFile, result, charset);
        saveTitleStore(exportDir, newStore);

        System.out.println("已同步 " + desired.size() + " 条，每天一个活动节点（无属性行）");
        System.out.println(mapFile.getAbsolutePath());
    }

    static String mergeTitle(String csvTitle, String fullText, String syncedTitle) {
        String csv = csvTitle == null ? "" : csvTitle.trim();
        String full = fullText == null ? "" : fullText.trim();
        if (csv.isEmpty()) return full;
        if (full.isEmpty()) return csv;
        if (full.equals(csv)) return full;
        String synced = syncedTitle == null ? "" : syncedTitle.trim();
        if (!synced.isEmpty()) {
            if (full.equals(synced)) return csv;
            if (full.startsWith(synced)) return csv + full.substring(synced.length());
            return csv;
        }
        if (full.startsWith(csv)) return full;
        return full;
    }

    private static String buildBody(Map<String, String> events, Map<String, ExistingEvent> existing,
            Map<String, String> titleStore, Map<String, String> newStore, long base) {
        TreeMap<String, String> sorted = new TreeMap<>(events);
        StringBuilder sb = new StringBuilder();
        sb.append("\n<edge COLOR=\"#ff0000\"/>");

        String cy = null, cm = null;
        int seq = 0;

        for (Map.Entry<String, String> e : sorted.entrySet()) {
            String dk = e.getKey();
            String csvTitle = e.getValue();
            newStore.put(dk, csvTitle);

            String y = dk.substring(0, 4);
            String m = String.valueOf(Integer.parseInt(dk.substring(4, 6)));
            String d = String.valueOf(Integer.parseInt(dk.substring(6, 8)));

            if (!y.equals(cy)) {
                if (cm != null) sb.append("\n</node>");
                if (cy != null) sb.append("\n</node>");
                sb.append(nodeOpen(y, base, seq++));
                cy = y;
                cm = null;
            }
            if (!m.equals(cm)) {
                if (cm != null) sb.append("\n</node>");
                sb.append(nodeOpen(m, base, seq++));
                cm = m;
            }

            sb.append(nodeOpen(d, base, seq++));
            ExistingEvent old = existing.get(dk);
            String full = csvTitle;
            String childXml = "";
            if (old != null) {
                full = mergeTitle(csvTitle, old.fullText, titleStore.get(dk));
                childXml = old.childXml;
            }
            appendEventNode(sb, full, childXml, base, seq++);
            sb.append("\n</node>");
        }
        if (cm != null) sb.append("\n</node>");
        if (cy != null) sb.append("\n</node>");
        sb.append('\n');
        return sb.toString();
    }

    private static void appendEventNode(StringBuilder sb, String text, String childXml, long base, int seq) {
        String id = "ID_" + (base + seq);
        if (childXml == null || childXml.trim().isEmpty()) {
            sb.append("\n<node TEXT=\"").append(esc(text))
              .append("\" POSITION=\"right\" ID=\"").append(id)
              .append("\" CREATED=\"").append(base).append("\" MODIFIED=\"").append(base).append("\"/>");
        } else {
            sb.append("\n<node TEXT=\"").append(esc(text))
              .append("\" POSITION=\"right\" ID=\"").append(id)
              .append("\" CREATED=\"").append(base).append("\" MODIFIED=\"").append(base).append("\">");
            sb.append('\n').append(childXml.trim());
            sb.append("\n</node>");
        }
    }

    private static Map<String, ExistingEvent> parseExisting(String body) {
        Map<String, ExistingEvent> map = new HashMap<>();
        List<String> path = new ArrayList<>();
        int i = 0;
        while (i < body.length()) {
            int close = body.indexOf("</node>", i);
            int nodeStart = body.indexOf("<node", i);
            if (nodeStart < 0) break;
            if (close >= 0 && close < nodeStart) {
                if (!path.isEmpty()) path.remove(path.size() - 1);
                i = close + 7;
                continue;
            }
            int tagEnd = body.indexOf('>', nodeStart);
            if (tagEnd < 0) break;
            String tag = body.substring(nodeStart, tagEnd + 1);
            Matcher textM = Pattern.compile("TEXT=\"([^\"]*)\"").matcher(tag);
            if (!textM.find()) {
                i = tagEnd + 1;
                continue;
            }
            String text = unesc(textM.group(1));
            boolean selfClosing = tag.endsWith("/>");
            if (path.size() == 3) {
                ExistingEvent ev = new ExistingEvent();
                ev.fullText = text;
                if (!selfClosing) {
                    int innerStart = tagEnd + 1;
                    int innerEnd = findMatchingClose(body, innerStart);
                    if (innerEnd > innerStart) {
                        ev.childXml = stripSyncedAttributes(body.substring(innerStart, innerEnd).trim());
                    }
                    i = innerEnd + 7;
                } else {
                    i = tagEnd + 1;
                }
                map.put(dateKey(path.get(0), path.get(1), path.get(2)), ev);
            } else if (path.size() < 3) {
                path.add(text);
                i = tagEnd + 1;
            } else {
                i = tagEnd + 1;
            }
        }
        return map;
    }

    private static String stripSyncedAttributes(String xml) {
        if (xml == null) return "";
        String cleaned = ATTR_SYNCED.matcher(xml).replaceAll("").trim();
        cleaned = cleaned.replaceAll("(?m)^\\s*<attribute NAME=\"hashPhotosTitle\"[^>]*/>\\s*", "");
        return cleaned.trim();
    }

    private static int findMatchingClose(String body, int from) {
        int depth = 1, i = from;
        while (i < body.length()) {
            int nodeStart = body.indexOf("<node", i);
            int closeStart = body.indexOf("</node>", i);
            if (closeStart < 0) return -1;
            if (nodeStart >= 0 && nodeStart < closeStart) {
                int tagEnd = body.indexOf('>', nodeStart);
                if (tagEnd < 0) return -1;
                if (body.charAt(tagEnd - 1) != '/') depth++;
                i = tagEnd + 1;
            } else {
                depth--;
                if (depth == 0) return closeStart;
                i = closeStart + 7;
            }
        }
        return -1;
    }

    private static int findActivitiesClose(String content, int from) {
        int depth = 1, i = from + 1;
        while (i < content.length()) {
            int nodeStart = content.indexOf("<node", i);
            int closeStart = content.indexOf("</node>", i);
            if (closeStart < 0 && nodeStart < 0) return -1;
            if (nodeStart >= 0 && (closeStart < 0 || nodeStart < closeStart)) {
                int tagEnd = content.indexOf('>', nodeStart);
                if (tagEnd < 0) return -1;
                if (content.charAt(tagEnd - 1) != '/') depth++;
                i = tagEnd + 1;
            } else {
                depth--;
                if (depth == 0) return closeStart;
                i = closeStart + 7;
            }
        }
        return -1;
    }

    private static String dateKey(String y, String m, String d) {
        return String.format("%s%02d%02d", y, Integer.parseInt(m), Integer.parseInt(d));
    }

    private static String nodeOpen(String text, long base, int seq) {
        return "\n<node TEXT=\"" + esc(text) + "\" POSITION=\"right\" ID=\"ID_" + (base + seq)
                + "\" CREATED=\"" + base + "\" MODIFIED=\"" + base + "\">";
    }

    private static Map<String, String> loadTitleStore(File exportDir) throws IOException {
        Map<String, String> map = new HashMap<>();
        File f = new File(exportDir, TITLES_FILE);
        if (!f.isFile()) return map;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq > 0) map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        }
        return map;
    }

    private static void saveTitleStore(File exportDir, Map<String, String> store) throws IOException {
        File f = new File(exportDir, TITLES_FILE);
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
            for (Map.Entry<String, String> e : store.entrySet()) {
                w.write(e.getKey());
                w.write('=');
                w.write(e.getValue());
                w.write('\n');
            }
        }
    }

    private static File findLatestCsv(File dir) {
        if (!dir.isDirectory()) return null;
        File latest = null;
        long best = -1;
        for (File f : dir.listFiles()) {
            if (!f.isFile() || !f.getName().toLowerCase().endsWith(".csv")) continue;
            long key = f.lastModified();
            int dash = f.getName().lastIndexOf('-');
            if (dash > 0) {
                String stamp = f.getName().substring(dash + 1, f.getName().length() - 4);
                if (stamp.matches("\\d{8}_\\d{6}")) key = Long.parseLong(stamp.replace("_", ""));
            }
            if (latest == null || key > best) { latest = f; best = key; }
        }
        return latest;
    }

    private static Map<String, String> readCsv(File csv) throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(csv), StandardCharsets.UTF_8))) {
            r.readLine();
            String line;
            while ((line = r.readLine()) != null) {
                int comma = line.indexOf(',');
                if (comma <= 0) continue;
                String dk = line.substring(0, comma).trim();
                String title = line.substring(comma + 1).trim();
                if (title.startsWith("\"") && title.endsWith("\""))
                    title = title.substring(1, title.length() - 1).replace("\"\"", "\"");
                if (DATE_KEY.matcher(dk).matches() && !title.isEmpty()) map.put(dk, title);
            }
        }
        return map;
    }

    private static Charset detectCharset(File file) throws IOException {
        byte[] head = new byte[4096];
        try (InputStream in = new FileInputStream(file)) {
            int n = in.read(head);
            String utf8 = new String(head, 0, n, StandardCharsets.UTF_8);
            if (utf8.contains("活动数据") || utf8.contains("&#x6d3b;&#x52a8;&#x6570;&#x636e;"))
                return StandardCharsets.UTF_8;
        }
        return Charset.forName("GBK");
    }

    private static String esc(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                default:
                    if (ch > 127) sb.append("&#x").append(Integer.toHexString(ch)).append(";");
                    else sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static String unesc(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length();) {
            if (text.charAt(i) == '&') {
                int semi = text.indexOf(';', i);
                if (semi < 0) break;
                String ent = text.substring(i + 1, semi);
                if (ent.startsWith("#x")) sb.append((char) Integer.parseInt(ent.substring(2), 16));
                else if (ent.startsWith("#")) sb.append((char) Integer.parseInt(ent.substring(1)));
                else if ("amp".equals(ent)) sb.append('&');
                else if ("lt".equals(ent)) sb.append('<');
                else if ("gt".equals(ent)) sb.append('>');
                else if ("quot".equals(ent)) sb.append('"');
                else sb.append('&').append(ent).append(';');
                i = semi + 1;
            } else sb.append(text.charAt(i++));
        }
        return sb.toString();
    }

    private static String readText(File f, Charset cs) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), cs))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static void writeText(File f, String text, Charset cs) throws IOException {
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), cs))) {
            w.write(text);
        }
    }
}
