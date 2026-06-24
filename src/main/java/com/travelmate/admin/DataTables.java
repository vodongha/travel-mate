package com.travelmate.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

/**
 * Helpers for the admin panel's **server-side** data tables: clamp the page size to an allow-list,
 * validate the sort column against a whitelist (so a crafted {@code ?sort=} can't sort by an
 * arbitrary field), and slice an in-memory list into a {@link Page} so list- and DB-backed tables
 * share the same Thymeleaf fragments and pager.
 */
public final class DataTables {

    private DataTables() {
    }

    /** The page sizes offered in the size selector. */
    public static final List<Integer> PAGE_SIZES = List.of(10, 20, 50, 100);

    private static final int DEFAULT_SIZE = 20;

    public static int clampSize(Integer size) {
        return size != null && PAGE_SIZES.contains(size) ? size : DEFAULT_SIZE;
    }

    /** A validated {@link Sort}: {@code sortKey} must be in {@code allowed}, else fall to {@code defaultKey}. */
    public static Sort sort(String sortKey, String dir, Set<String> allowed, String defaultKey) {
        String key = sortKey != null && allowed.contains(sortKey) ? sortKey : defaultKey;
        Sort.Direction direction = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, key);
    }

    public static Pageable pageable(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), clampSize(size), sort);
    }

    /** Apply paging to an already-filtered, already-sorted in-memory list (for the Ops tables). */
    public static <T> Page<T> slice(List<T> all, Pageable pageable) {
        int from = (int) Math.min((long) pageable.getPageNumber() * pageable.getPageSize(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), pageable, all.size());
    }

    /**
     * The state a server-side table view needs to render its toolbar, sortable headers and pager:
     * the page's {@code baseUrl}, the active search/sort/size, and the current {@link Page}. Built
     * once in the controller and handed to the {@code datatable} Thymeleaf fragments.
     */
    public record View(String baseUrl, String q, String sort, String dir, int size, Page<?> page) {

        /** The direction a header link should request next: flip when it's the active column, else asc. */
        public String nextDir(String key) {
            return key.equals(sort) && "asc".equalsIgnoreCase(dir) ? "desc" : "asc";
        }

        /** An arrow for the active sort column (▲/▼), or empty for the rest. */
        public String arrow(String key) {
            if (!key.equals(sort)) {
                return "";
            }
            return "asc".equalsIgnoreCase(dir) ? "▲" : "▼";
        }
    }

    /** Build the view from the parts a controller already has. */
    public static View view(String baseUrl, String q, String sort, String dir, int size, Page<?> page) {
        return new View(baseUrl, q == null ? "" : q, sort, dir, clampSize(size), page);
    }
}
