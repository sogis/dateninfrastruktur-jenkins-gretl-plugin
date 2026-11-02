package ch.so.agi.jenkins.gretldatenportal;

import java.util.Map;

public final class StartFormSubmission {
    private final Map<String, String> values;
    private final Map<String, UploadedFileInfo> files;

    public StartFormSubmission(Map<String, String> values, Map<String, UploadedFileInfo> files) {
        this.values = values == null ? Map.of() : Map.copyOf(values);
        this.files = files == null ? Map.of() : Map.copyOf(files);
    }

    public String value(String key) {
        return values.getOrDefault(key, "");
    }

    public UploadedFileInfo file(String key) {
        return files.get(key);
    }

    public boolean hasFile(String key) {
        UploadedFileInfo file = files.get(key);
        return file != null && file.getSizeBytes() > 0;
    }
}
