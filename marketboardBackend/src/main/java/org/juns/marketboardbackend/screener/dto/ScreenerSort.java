package org.juns.marketboardbackend.screener.dto;

public record ScreenerSort(ScreenerSortField field, SortDirection direction) {
    public ScreenerSort {
        if (field == null) field = ScreenerSortField.MOMENTUM_6M;
        if (direction == null) direction = SortDirection.DESC;
    }
}
