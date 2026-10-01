package org.juns.marketboardbackend.collector;

import java.util.List;

public record BackfillMissingResult(int attempted, int succeeded, List<String> failed, int totalRows) {
}
