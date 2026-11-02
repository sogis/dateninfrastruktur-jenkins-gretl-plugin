package ch.so.agi.jenkins.gretldatenportal;

public final class UploadedFileInfo {
    private final String fileName;
    private final long sizeBytes;

    public UploadedFileInfo(String fileName, long sizeBytes) {
        this.fileName = fileName == null ? "" : fileName;
        this.sizeBytes = sizeBytes;
    }

    public String getFileName() {
        return fileName;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getExtension() {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(java.util.Locale.ROOT);
    }
}
