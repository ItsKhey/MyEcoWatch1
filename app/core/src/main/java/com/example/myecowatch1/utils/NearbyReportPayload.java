package com.example.myecowatch1.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Wire format for handing a nearby existing report from the repository to the
 * ViewModel.
 *
 * <p>A submission that lands close to a report already on file is not a
 * failure, but the only callback the repository has for it is the error
 * channel, so the nearby-report data travels as a prefixed string the
 * ViewModel recognises and turns back into a map.
 *
 * <p>The payload must be encoded as real JSON. A {@link Map#toString()} yields
 * Java syntax (unquoted keys, '=' separators, unquoted string values), which no
 * JSON parser accepts, and decoding then fails and the resident gets an
 * unexplained error instead of the "a report is already near here" prompt.
 */
public final class NearbyReportPayload {

    public static final String PREFIX = "NEARBY_REPORT:";

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();

    private NearbyReportPayload() {
    }

    /** Wraps a response map into the prefixed error string. */
    public static String encode(Map<String, Object> nearbyData) {
        return PREFIX + (nearbyData == null ? "{}" : GSON.toJson(nearbyData));
    }

    /** True when the error string carries a nearby-report hand-off. */
    public static boolean isNearbyReportPayload(String error) {
        return error != null && error.startsWith(PREFIX);
    }

    /**
     * Recovers the nearby-report map from an error string.
     *
     * @return the nearby data, or {@code null} when this is not a nearby-report
     *         payload or its JSON cannot be read.
     */
    public static Map<String, Object> decode(String error) {
        if (!isNearbyReportPayload(error)) return null;

        String json = error.substring(PREFIX.length()).trim();
        if (json.isEmpty()) return null;

        try {
            Map<String, Object> parsed = GSON.fromJson(json, MAP_TYPE);
            return parsed;
        } catch (RuntimeException e) {
            // Malformed or non-JSON payload. The caller reports a retryable
            // message rather than surfacing a raw parse failure.
            return null;
        }
    }

    /**
     * Whether the hand-off is worth showing the resident, i.e. whether the spot
     * still has a report a new submission could clone.
     *
     * <p>The server counts every open report at that spot. Denied and Completed
     * reports are deliberately left out: a Denied report was reviewed and
     * rejected, and a Completed one describes waste that has already been
     * cleared, so neither can be the original of a live clone.
     *
     * <p>A count of zero therefore means there is no open report there, so
     * there is nothing left to warn about, and a sheet reading "A Report Is
     * Already Near Here / Existing Reports 0" would ask the resident to decide
     * about nothing. {@code reportCount} and {@code spotCount} are the same
     * figure on the server and either one counts. A missing or unreadable count
     * is treated as "something is there": silence is only justified by a
     * positive statement that nothing is.
     *
     * @return true when at least one open report sits at the spot.
     */
    public static boolean hasOutstandingReports(Map<String, Object> nearbyData) {
        if (nearbyData == null) return true;

        Object reportCount = nearbyData.get("reportCount");
        if (reportCount == null) reportCount = nearbyData.get("spotCount");
        if (reportCount == null) return true;

        try {
            return Double.parseDouble(reportCount.toString()) > 0d;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /**
     * The status the resident should read for the nearby reports.
     *
     * <p>{@code reportStatus} is derived from the open reports themselves. The
     * older {@code clusterStatus} field is the location row's status, which
     * never advances past Pending, so an in-progress spot used to be announced
     * as "Pending" in the duplicate sheet. Prefer the report-derived value and
     * only fall back to the legacy field for a server that predates it.
     *
     * @return a non-empty status label, or "Unknown" when the payload carries
     *         neither field.
     */
    public static String displayStatus(Map<String, Object> nearbyData) {
        if (nearbyData == null) return "Unknown";

        String reportStatus = statusText(nearbyData.get("reportStatus"));
        if (reportStatus != null) return reportStatus;

        String clusterStatus = statusText(nearbyData.get("clusterStatus"));
        return clusterStatus != null ? clusterStatus : "Unknown";
    }

    private static String statusText(Object value) {
        if (value == null) return null;
        String trimmed = value.toString().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}