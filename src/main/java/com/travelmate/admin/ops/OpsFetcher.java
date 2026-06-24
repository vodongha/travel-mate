package com.travelmate.admin.ops;

import java.util.Map;

/** Tiny HTTP GET seam so {@link OpsService} can be unit-tested without real network calls. */
public interface OpsFetcher {

    /** GET the URL with the given headers; returns the response body. Throws on any failure. */
    String get(String url, Map<String, String> headers) throws Exception;
}
