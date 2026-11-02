package ch.so.agi.jenkins.gretldatenportal;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

/** A request-local, filtered page of the readable build history. */
public final class DatenportalRunOverview {
    static final int PAGE_SIZE = 10;
    static final Comparator<DatenportalRunSummary> RUN_ORDER =
            Comparator.comparingLong(DatenportalRunSummary::getStartTimeMillis).reversed()
                    .thenComparing(DatenportalRunSummary::getJobFullName, Comparator.reverseOrder())
                    .thenComparing(Comparator.comparingInt(DatenportalRunSummary::getBuildNumber).reversed());

    private final ScanResult scanResult;
    private final boolean executedRuns;
    private final String organization;
    private final String dataset;
    private final String status;
    private final List<FilterOption> organizationOptions;
    private final List<FilterOption> datasetOptions;
    private final List<FilterOption> statusOptions;
    private final List<DatenportalRunSummary> runs;
    private final int totalCount;
    private final int page;
    private final int pageCount;

    DatenportalRunOverview(
            ScanResult scanResult,
            List<DatenportalRunSummary> readableRuns,
            String organization,
            String dataset,
            String status,
            String requestedPage) {
        this.scanResult = scanResult;
        this.executedRuns = !readableRuns.isEmpty();
        this.organization = valueOrEmpty(organization);
        this.dataset = valueOrEmpty(dataset);
        this.status = valueOrEmpty(status);
        this.organizationOptions = options(
                readableRuns.stream().map(DatenportalRunSummary::getOrganizationValue).toList(), this.organization);
        this.datasetOptions = options(
                readableRuns.stream().map(DatenportalRunSummary::getDatasetValue).toList(), this.dataset);
        this.statusOptions = List.of("SUCCESS", "FAILURE", "UNSTABLE", "ABORTED", "RUNNING").stream()
                .map(value -> new FilterOption(value, DatenportalRunSummary.statusLabel(value), value.equals(this.status)))
                .toList();

        List<DatenportalRunSummary> matchingRuns = readableRuns.stream()
                .filter(run -> matches(this.organization, run.getOrganizationValue()))
                .filter(run -> matches(this.dataset, run.getDatasetValue()))
                .filter(run -> matches(this.status, run.getStatus()))
                .sorted(RUN_ORDER)
                .toList();
        this.totalCount = matchingRuns.size();
        this.pageCount = Math.max(1, (totalCount + PAGE_SIZE - 1) / PAGE_SIZE);
        this.page = (int) Math.min(pageNumber(requestedPage), pageCount);
        int offset = (page - 1) * PAGE_SIZE;
        this.runs = List.copyOf(matchingRuns.subList(offset, Math.min(offset + PAGE_SIZE, totalCount)));
    }

    public ScanResult getScanResult() {
        return scanResult;
    }

    public boolean hasExecutedRuns() {
        return executedRuns;
    }

    public List<FilterOption> getOrganizationOptions() {
        return organizationOptions;
    }

    public List<FilterOption> getDatasetOptions() {
        return datasetOptions;
    }

    public List<FilterOption> getStatusOptions() {
        return statusOptions;
    }

    public List<DatenportalRunSummary> getRuns() {
        return runs;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getPage() {
        return page;
    }

    public int getPageCount() {
        return pageCount;
    }

    public int getFirstResult() {
        return totalCount == 0 ? 0 : (page - 1) * PAGE_SIZE + 1;
    }

    public int getLastResult() {
        return totalCount == 0 ? 0 : getFirstResult() + runs.size() - 1;
    }

    public boolean hasPreviousPage() {
        return page > 1;
    }

    public boolean hasNextPage() {
        return page < pageCount;
    }

    public String getPreviousPageQuery() {
        return pageQuery(page - 1);
    }

    public String getNextPageQuery() {
        return pageQuery(page + 1);
    }

    private String pageQuery(int targetPage) {
        return "?organization=" + encode(organization)
                + "&dataset=" + encode(dataset)
                + "&status=" + encode(status)
                + "&page=" + targetPage;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean matches(String selected, String actual) {
        return selected.isEmpty() || selected.equals(actual);
    }

    private static long pageNumber(String value) {
        try {
            return Math.max(1, Long.parseLong(valueOrEmpty(value)));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private static List<FilterOption> options(List<String> values, String selected) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .map(value -> new FilterOption(value, value, value.equals(selected)))
                .toList();
    }

    public static final class FilterOption {
        private final String value;
        private final String label;
        private final boolean selected;

        FilterOption(String value, String label, boolean selected) {
            this.value = value;
            this.label = label;
            this.selected = selected;
        }

        public String getValue() {
            return value;
        }

        public String getLabel() {
            return label;
        }

        public boolean isSelected() {
            return selected;
        }
    }
}
