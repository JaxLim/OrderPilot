package com.orderpilot.common.api;

/** Normalised paging input: page is 1-based, size is clamped to [1, 100]. */
public record PageQuery(int page, int size) {

    public PageQuery {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 100);
    }

    public static PageQuery of(Integer page, Integer size) {
        return new PageQuery(page == null ? 1 : page, size == null ? 20 : size);
    }

    public int offset() {
        return (page - 1) * size;
    }
}
