package com.datn.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Standard pagination response wrapper.
 *
 * @param <T> Item type
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> content;

    private int pageNumber;

    private int pageSize;

    private long totalElements;

    private int totalPages;

    private boolean last;

    private boolean first;

    public static <T> PageResponse<T> of(List<T> content, int pageNumber, int pageSize, long totalElements, int totalPages, boolean last, boolean first) {
        return PageResponse.<T>builder()
                .content(content)
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last(last)
                .first(first)
                .build();
    }
}
