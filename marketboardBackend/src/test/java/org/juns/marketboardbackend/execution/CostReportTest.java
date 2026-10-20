package org.juns.marketboardbackend.execution;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CostReportTest {
    @Test void escapesHtmlAndSpreadsheetFormulasWithoutChangingNumbers() {
        assertThat(CostReport.html("<script>\"&'</script>")).isEqualTo("&lt;script&gt;&quot;&amp;&#39;&lt;/script&gt;");
        for (var input : new String[]{"=HYPERLINK(\"x\")", " +cmd", "@SUM(A1)", "\t=1", "\n=1"})
            assertThat(CostReport.csv(input)).startsWith("\"'");
        assertThat(CostReport.csv("-0.123")).isEqualTo("\"-0.123\"");
        assertThat(CostReport.csv("a,\"b\"")).isEqualTo("\"a,\"\"b\"\"\"");
    }
}
