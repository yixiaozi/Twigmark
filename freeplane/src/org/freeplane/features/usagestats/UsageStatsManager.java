package org.freeplane.features.usagestats;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.freeplane.core.util.FreeplaneVersion;

public class UsageStatsManager {
    private static final String STATS_ROOT_DIR = ".docear_stats";
    private static final String DATA_DIR = "data";
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String FILE_EXTENSION = ".json";
    /** Sessions shorter than this are excluded from reports and session counts. */
    public static final long MIN_SESSION_DURATION_MS = 1000L;
    
    private static UsageStatsManager instance;
    private List<UsageRecord> recordsCache;
    private IdleDetector idleDetector;
    private UsageRecord currentRecord;
    private String currentMapPath;
    private long idleTimeAccumulator = 0;
    private long lastIdleStart = 0;
    private boolean isWindowActive = false;
    
    private UsageStatsManager() {
        idleDetector = new IdleDetector();
        idleDetector.setIdleListener(new IdleDetector.IdleListener() {
            @Override
            public void onIdleDetected(long idleTimeMs) {
                handleIdleDetected(idleTimeMs);
            }
            
            @Override
            public void onUserActivity(long idleTimeMs) {
                handleUserActivity(idleTimeMs);
            }
        });
    }
    
    public static synchronized UsageStatsManager getInstance() {
        if (instance == null) {
            instance = new UsageStatsManager();
        }
        return instance;
    }
    
    public static File getStatsRootDir() {
        try {
            // Shared project metadata: _data/{projectId}/.docear_stats
            // Per-computer isolation is under data/{deviceId}/ — see DeviceIdentifier.
            File baseDir = org.freeplane.core.util.MindMapDataRootResolver.getProjectDataDirectory();
            if (baseDir == null) {
                baseDir = new File(org.freeplane.core.util.Compat.getApplicationUserDirectory());
            }
            if (!baseDir.exists()) {
                baseDir.mkdirs();
            }
            File docearStatsDir = new File(baseDir, STATS_ROOT_DIR);
            if (!docearStatsDir.exists()) {
                docearStatsDir.mkdirs();
            }
            return docearStatsDir;
        } catch (Exception e) {
            return null;
        }
    }

    public static File getStatsDataDir() {
        try {
            File docearStatsDir = getStatsRootDir();
            if (docearStatsDir == null) {
                return null;
            }
            File dataDir = new File(docearStatsDir, DATA_DIR);
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }
            return dataDir;
        } catch (Exception e) {
            return null;
        }
    }
    
    public void start() {
        idleDetector.start();
    }
    
    public void stop() {
        if (currentRecord != null) {
            endCurrentRecord();
        }
        idleDetector.stop();
    }
    
    public void onWindowActivated() {
        isWindowActive = true;
        // Resume existing session when returning from an in-app dialog; only start if none.
        if (currentRecord != null) {
            idleDetector.markActivity();
            return;
        }
        if (currentMapPath != null && !currentMapPath.isEmpty()) {
            startNewRecord();
        }
    }
    
    /**
     * Pause recording when the user leaves the application.
     * In-app focus changes (dialogs, other Docear windows) should NOT call this —
     * see FreeplaneGUIStarter window listener.
     */
    public void onWindowDeactivated() {
        isWindowActive = false;
        if (currentRecord != null) {
            endCurrentRecord();
        }
    }
    
    public void onMapOpened(String mapPath) {
        this.currentMapPath = mapPath;
        if (isWindowActive) {
            startNewRecord();
        }
    }
    
    public void onMapClosed(String mapPath) {
        if (mapPath != null && mapPath.equals(currentMapPath)) {
            if (currentRecord != null) {
                endCurrentRecord();
            }
            this.currentMapPath = null;
        }
    }
    
    private void startNewRecord() {
        if (currentRecord != null) {
            endCurrentRecord();
        }
        
        if (currentMapPath == null || currentMapPath.isEmpty()) {
            return;
        }
        
        currentRecord = new UsageRecord();
        currentRecord.setDeviceId(DeviceIdentifier.getDeviceId());
        currentRecord.setStartTime(System.currentTimeMillis());
        currentRecord.setEventType("activated");
        currentRecord.setMapPath(currentMapPath);
        currentRecord.setFileHash(calculateDcrId(currentMapPath));
        currentRecord.setAppVersion(getApplicationVersion());
        idleTimeAccumulator = 0;
    }
    
    private void endCurrentRecord() {
        if (currentRecord == null) {
            return;
        }

        // Flush trailing idle (user went idle and never came back before session ended).
        if (lastIdleStart > 0L || idleDetector.isIdle()) {
            idleTimeAccumulator += idleDetector.getChargeableIdleMsNow();
            lastIdleStart = 0L;
        }

        long now = System.currentTimeMillis();
        currentRecord.setEndTime(now);
        currentRecord.setIdleDurationMs(idleTimeAccumulator);
        currentRecord.calculateDurations();

        // Guard against idle > total (clock skew / double flush).
        if (currentRecord.getEffectiveDurationMs() < 0L) {
            currentRecord.setEffectiveDurationMs(0L);
            currentRecord.setIdleDurationMs(currentRecord.getTotalDurationMs());
        }

        saveRecord(currentRecord);

        currentRecord = null;
        idleTimeAccumulator = 0L;
        lastIdleStart = 0L;
    }
    
    private void handleIdleDetected(long idleTimeMs) {
        if (currentRecord != null) {
            lastIdleStart = System.currentTimeMillis() - idleTimeMs;
        }
    }
    
    private void handleUserActivity(long chargeableIdleMs) {
        if (currentRecord != null && lastIdleStart > 0) {
            if (chargeableIdleMs > 0L) {
                idleTimeAccumulator += chargeableIdleMs;
            }
            lastIdleStart = 0;
        }
    }
    
    private String calculateDcrId(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists() || !file.canRead()) {
                return "";
            }
            
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
                String firstLine = reader.readLine();
                if (firstLine != null && firstLine.contains("dcr_id=")) {
                    int startIndex = firstLine.indexOf("dcr_id=\"");
                    if (startIndex >= 0) {
                        startIndex += 8;
                        int endIndex = firstLine.indexOf("\"", startIndex);
                        if (endIndex > startIndex) {
                            return firstLine.substring(startIndex, endIndex);
                        }
                    }
                }
            } finally {
                if (reader != null) {
                    try { reader.close(); } catch (IOException e) { }
                }
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }
    
    private synchronized void saveRecord(UsageRecord record) {
        try {
            File dataDir = getStatsDataDir();
            if (dataDir == null) {
                return;
            }
            
            String deviceId = record.getDeviceId();
            File deviceDir = new File(dataDir, deviceId);
            if (!deviceDir.exists()) {
                deviceDir.mkdirs();
            }
            
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
            String dateStr = sdf.format(new Date());
            File dateFile = new File(deviceDir, dateStr + FILE_EXTENSION);
            
            List<UsageRecord> records = loadRecordsFromFile(dateFile);
            records.add(record);
            saveRecordsToFile(dateFile, records);
            invalidateRecordsCache();
        } catch (Exception e) {
            // Ignore save errors
        }
    }

    public synchronized void invalidateRecordsCache() {
        recordsCache = null;
    }

    public List<UsageRecord> loadAllRecords() {
        synchronized (this) {
            if (recordsCache != null) {
                return new ArrayList<UsageRecord>(recordsCache);
            }
        }
        final List<UsageRecord> loaded = loadAllRecordsFromDisk();
        synchronized (this) {
            recordsCache = loaded;
            return new ArrayList<UsageRecord>(recordsCache);
        }
    }

    private List<UsageRecord> loadAllRecordsFromDisk() {
        final List<UsageRecord> all = new ArrayList<UsageRecord>();
        try {
            final File dataDir = getStatsDataDir();
            if (dataDir == null || !dataDir.isDirectory()) {
                return all;
            }
            final File[] deviceDirs = dataDir.listFiles();
            if (deviceDirs == null) {
                return all;
            }
            for (final File deviceDir : deviceDirs) {
                if (!deviceDir.isDirectory()) {
                    continue;
                }
                final File[] dateFiles = deviceDir.listFiles();
                if (dateFiles == null) {
                    continue;
                }
                for (final File dateFile : dateFiles) {
                    if (dateFile.isFile() && dateFile.getName().endsWith(FILE_EXTENSION)) {
                        all.addAll(loadRecordsFromFile(dateFile));
                    }
                }
            }
        }
        catch (Exception e) {
            // Ignore
        }
        return all;
    }

    public static boolean isSignificantSession(final UsageRecord record) {
        if (record == null) {
            return false;
        }
        long effectiveMs = record.getEffectiveDurationMs();
        if (effectiveMs <= 0L && record.getEndTime() > record.getStartTime()) {
            effectiveMs = record.getEndTime() - record.getStartTime() - record.getIdleDurationMs();
        }
        if (effectiveMs <= 0L) {
            effectiveMs = record.getTotalDurationMs();
        }
        return effectiveMs >= MIN_SESSION_DURATION_MS;
    }

    public MapUsageSummary summarizeForMap(final String mapPath) {
        final MapUsageSummary summary = new MapUsageSummary(mapPath);
        if (mapPath == null || mapPath.isEmpty()) {
            return summary;
        }
        final String wantHash = calculateDcrId(mapPath);
        final String wantKey = summaryKeyForPath(mapPath, wantHash);
        for (final UsageRecord record : loadAllRecords()) {
            if (!isSignificantSession(record)) {
                continue;
            }
            if (recordMatchesMap(record, mapPath, wantHash, wantKey)) {
                summary.preferPath(record.getMapPath(), record.getEndTime());
                summary.addRecord(record);
            }
        }
        return summary;
    }

    public List<UsageRecord> loadRecordsForMap(final String mapPath) {
        final List<UsageRecord> result = new ArrayList<UsageRecord>();
        if (mapPath == null || mapPath.isEmpty()) {
            return result;
        }
        final String wantHash = calculateDcrId(mapPath);
        final String wantKey = summaryKeyForPath(mapPath, wantHash);
        for (final UsageRecord record : loadAllRecords()) {
            if (!isSignificantSession(record)) {
                continue;
            }
            if (recordMatchesMap(record, mapPath, wantHash, wantKey)) {
                result.add(record);
            }
        }
        Collections.sort(result, new Comparator<UsageRecord>() {
            public int compare(final UsageRecord a, final UsageRecord b) {
                final long diff = b.getEndTime() - a.getEndTime();
                if (diff > 0L) {
                    return 1;
                }
                if (diff < 0L) {
                    return -1;
                }
                return 0;
            }
        });
        return result;
    }

    private static boolean recordMatchesMap(final UsageRecord record, final String mapPath, final String wantHash,
            final String wantKey) {
        if (record == null) {
            return false;
        }
        final String hash = record.getFileHash();
        if (wantHash != null && wantHash.length() > 0 && hash != null && wantHash.equals(hash)) {
            return true;
        }
        if (wantKey != null && wantKey.length() > 0 && wantKey.equals(summaryKeyFor(record))) {
            return true;
        }
        final MapUsageSummary matcher = new MapUsageSummary(mapPath);
        return matcher.matchesPath(record.getMapPath());
    }

    private static String summaryKeyForPath(final String path, final String hash) {
        if (hash != null && hash.length() > 0) {
            return "hash:" + hash;
        }
        if (path == null || path.length() == 0) {
            return "";
        }
        try {
            return "path:" + new File(path).getCanonicalPath();
        }
        catch (Exception e) {
            return "path:" + path;
        }
    }

    public java.util.Map<String, MapUsageSummary> summarizeByMap() {
        final java.util.LinkedHashMap<String, MapUsageSummary> byKey = new java.util.LinkedHashMap<String, MapUsageSummary>();
        for (final UsageRecord record : loadAllRecords()) {
            if (!isSignificantSession(record)) {
                continue;
            }
            final String path = record.getMapPath();
            if (path == null || path.isEmpty()) {
                continue;
            }
            final String key = summaryKeyFor(record);
            MapUsageSummary summary = byKey.get(key);
            if (summary == null) {
                summary = new MapUsageSummary(path);
                byKey.put(key, summary);
            }
            else {
                summary.preferPath(path, record.getEndTime());
            }
            summary.addRecord(record);
        }
        // Re-key by canonical/preferred path for UI selection.
        final java.util.LinkedHashMap<String, MapUsageSummary> byPath = new java.util.LinkedHashMap<String, MapUsageSummary>();
        for (final MapUsageSummary summary : byKey.values()) {
            byPath.put(summary.getMapPath(), summary);
        }
        return byPath;
    }

    /** Merge key: dcr/file hash when present, else canonical path, else raw path. */
    static String summaryKeyFor(final UsageRecord record) {
        if (record == null) {
            return "";
        }
        final String hash = record.getFileHash();
        if (hash != null && hash.length() > 0) {
            return "hash:" + hash;
        }
        final String path = record.getMapPath();
        if (path == null || path.length() == 0) {
            return "";
        }
        try {
            return "path:" + new File(path).getCanonicalPath();
        }
        catch (Exception e) {
            return "path:" + path;
        }
    }

    public static String formatDuration(final long millis) {
        if (millis <= 0L) {
            return "0\u79d2";
        }
        long seconds = millis / 1000L;
        final long hours = seconds / 3600L;
        seconds %= 3600L;
        final long minutes = seconds / 60L;
        seconds %= 60L;
        final StringBuilder sb = new StringBuilder();
        if (hours > 0L) {
            sb.append(hours).append("\u5c0f\u65f6");
        }
        if (minutes > 0L) {
            sb.append(minutes).append("\u5206");
        }
        if (seconds > 0L || sb.length() == 0) {
            sb.append(seconds).append("\u79d2");
        }
        return sb.toString();
    }

    private List<UsageRecord> loadRecordsFromFile(File file) {
        List<UsageRecord> records = new ArrayList<UsageRecord>();
        
        if (!file.exists()) {
            return records;
        }
        
        Reader reader = null;
        BufferedReader br = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            br = new BufferedReader(reader);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            
            String content = sb.toString();
            if (!content.trim().isEmpty()) {
                parseRecordsFromContent(content, records);
            }
        } catch (Exception e) {
            // Ignore loading errors
        } finally {
            if (br != null) {
                try { br.close(); } catch (IOException e) { }
            }
            if (reader != null) {
                try { reader.close(); } catch (IOException e) { }
            }
        }
        
        return records;
    }
    
    private void parseRecordsFromContent(String content, List<UsageRecord> records) {
        // Very simple JSON parsing to avoid adding dependencies
        int startIndex = 0;
        while (true) {
            int objStart = content.indexOf('{', startIndex);
            if (objStart < 0) {
                break;
            }
            
            int braceCount = 1;
            int objEnd = objStart + 1;
            while (objEnd < content.length() && braceCount > 0) {
                char c = content.charAt(objEnd);
                if (c == '{') {
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                }
                objEnd++;
            }
            
            if (braceCount == 0) {
                UsageRecord record = parseRecord(content.substring(objStart, objEnd));
                if (record != null) {
                    records.add(record);
                }
            }
            
            startIndex = objEnd;
        }
    }
    
    private UsageRecord parseRecord(String json) {
        UsageRecord record = new UsageRecord();
        
        String[] pairs = json.replace('{', ' ').replace('}', ' ').trim().split(",\"");
        for (String pair : pairs) {
            pair = pair.trim();
            if (pair.startsWith("\"")) {
                pair = pair.substring(1);
            }
            int colonIndex = pair.indexOf("\":");
            if (colonIndex > 0) {
                String key = pair.substring(0, colonIndex).trim();
                String value = pair.substring(colonIndex + 2).trim();
                
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                
                value = unescapeJsonString(value);
                
                try {
                    if ("recordId".equals(key)) {
                        // Skip, since we generate new
                    } else if ("fileHash".equals(key)) {
                        record.setFileHash(value);
                    } else if ("deviceId".equals(key)) {
                        record.setDeviceId(value);
                    } else if ("startTime".equals(key)) {
                        record.setStartTime(Long.parseLong(value));
                    } else if ("endTime".equals(key)) {
                        record.setEndTime(Long.parseLong(value));
                    } else if ("totalDurationMs".equals(key)) {
                        record.setTotalDurationMs(Long.parseLong(value));
                    } else if ("idleDurationMs".equals(key)) {
                        record.setIdleDurationMs(Long.parseLong(value));
                    } else if ("effectiveDurationMs".equals(key)) {
                        record.setEffectiveDurationMs(Long.parseLong(value));
                    } else if ("eventType".equals(key)) {
                        record.setEventType(value);
                    } else if ("mapPath".equals(key)) {
                        record.setMapPath(value);
                    } else if ("appVersion".equals(key)) {
                        record.setAppVersion(value);
                    }
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }
        
        return record;
    }
    
    private String unescapeJsonString(String value) {
        if (value == null) {
            return "";
        }
        String result = value;
        String prevResult;
        int maxIterations = 20;
        int iteration = 0;
        do {
            prevResult = result;
            result = result.replace("\\\\", "\\")
                          .replace("\\\"", "\"")
                          .replace("\\n", "\n")
                          .replace("\\r", "\r")
                          .replace("\\t", "\t");
        } while (!result.equals(prevResult) && ++iteration < maxIterations);
        return result;
    }
    
    private String getApplicationVersion() {
        try {
            Class<?> docearControllerClass = Class.forName("org.docear.plugin.core.DocearController");
            java.lang.reflect.Method getControllerMethod = docearControllerClass.getMethod("getController");
            Object controller = getControllerMethod.invoke(null);
            java.lang.reflect.Method getApplicationVersionMethod = docearControllerClass.getMethod("getApplicationVersion");
            return (String) getApplicationVersionMethod.invoke(controller);
        } catch (Exception e) {
            return FreeplaneVersion.getVersion().toString();
        }
    }
    
    private void saveRecordsToFile(File file, List<UsageRecord> records) {
        Writer writer = null;
        BufferedWriter bw = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
            bw = new BufferedWriter(writer);
            
            bw.write("[");
            for (int i = 0; i < records.size(); i++) {
                if (i > 0) {
                    bw.write(",");
                }
                writeRecord(bw, records.get(i));
            }
            bw.write("]");
        } catch (IOException e) {
            // Ignore save errors
        } finally {
            if (bw != null) {
                try { bw.close(); } catch (IOException e) { }
            }
            if (writer != null) {
                try { writer.close(); } catch (IOException e) { }
            }
        }
    }
    
    private void writeRecord(BufferedWriter writer, UsageRecord record) throws IOException {
        writer.write("{");
        writeStringField(writer, "recordId", record.getRecordId(), true);
        writeStringField(writer, "dcrId", record.getFileHash(), true);
        writeStringField(writer, "deviceId", record.getDeviceId(), true);
        writeLongField(writer, "startTime", record.getStartTime(), true);
        writeLongField(writer, "endTime", record.getEndTime(), true);
        writeLongField(writer, "totalDurationMs", record.getTotalDurationMs(), true);
        writeLongField(writer, "idleDurationMs", record.getIdleDurationMs(), true);
        writeLongField(writer, "effectiveDurationMs", record.getEffectiveDurationMs(), true);
        writeStringField(writer, "eventType", record.getEventType(), true);
        writeStringField(writer, "mapPath", record.getMapPath(), true);
        writeStringField(writer, "appVersion", record.getAppVersion(), false);
        writer.write("}");
    }
    
    private void writeStringField(BufferedWriter writer, String name, String value, boolean addComma) throws IOException {
        if (value == null) {
            value = "";
        }
        writer.write("\"" + escapeJsonString(name) + "\":\"" + escapeJsonString(value) + "\"");
        if (addComma) {
            writer.write(",");
        }
    }
    
    private void writeLongField(BufferedWriter writer, String name, long value, boolean addComma) throws IOException {
        writer.write("\"" + escapeJsonString(name) + "\":" + value);
        if (addComma) {
            writer.write(",");
        }
    }
    
    private String escapeJsonString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
