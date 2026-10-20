package org.juns.marketboardbackend.execution;

import java.util.*;
import tools.jackson.databind.JsonNode;

/** Standalone exports from persisted results; no recalculation or remote assets. */
public final class CostReport {
    private CostReport() {}
    public record Export(String filename, String contentType, String content) {}
    private static final String LIMITS = "과거 저장 가격의 모의 결과. 수정주가·배당 반영 미확인, 거래일 누락 미검사. 준비 가격은 성과 제외. 마지막 종가로 평가하며 강제 청산하지 않음. 반복 검증 구간은 독립 검증으로 간주할 수 없음.";
    static String html(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    static String csv(String value) {
        String trimmed = value.stripLeading();
        if (!value.matches("-?[0-9]+(\\.[0-9]+)?") && (!value.equals(trimmed) ||
                (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0))) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    private static String chart(JsonNode results) {
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        int count = results.get(0).path("result").path("curve").size();
        if (count == 0) return "";
        for (var scenario : results) for (var point : scenario.path("result").path("curve")) {
            double v = Double.parseDouble(point.path("strategy").asText());
            low = Math.min(low, v); high = Math.max(high, v);
        }
        double padding = Math.max((high - low) * .1, 1);
        low -= padding; high += padding;
        var svg = new StringBuilder("<h2>검증 기간 자산 추이 · USD</h2><p>");
        for (int j=0; j<results.size(); j++) svg.append(j == 0 ? "기준" : "조건 " + j).append(": ")
                .append(html(results.get(j).path("feeBps").asText())).append(" / ")
                .append(html(results.get(j).path("slippageBps").asText())).append(" bp &nbsp; ");
        svg.append("</p><svg viewBox=\"0 0 900 300\" role=\"img\" aria-label=\"비용 조건별 평가 자산 추이\" style=\"width:100%\">");
        for (int i=0; i<4; i++) {
            double y = 25 + i * 70, v = high - (high - low) * i / 3;
            svg.append(String.format(Locale.ROOT, "<line x1=\"20\" x2=\"780\" y1=\"%.2f\" y2=\"%.2f\" stroke=\"#ddd\"/><text x=\"890\" y=\"%.2f\" text-anchor=\"end\" font-size=\"12\">%.2f</text>", y, y, y + 4, v));
        }
        for (int j=0; j<results.size(); j++) {
            var curve = results.get(j).path("result").path("curve");
            svg.append("<polyline fill=\"none\" stroke=\"").append(j == 0 ? "#333" : "#167d75").append("\" stroke-width=\"2\"");
            if (j > 0) svg.append(" stroke-dasharray=\"").append(j * 3).append(" 3\"");
            svg.append(" points=\"");
            for (int i=0; i<count; i++) {
                double x = 20 + i * 760.0 / Math.max(1, count - 1);
                double y = 235 - (Double.parseDouble(curve.get(i).path("strategy").asText()) - low) / (high - low) * 210;
                svg.append(String.format(Locale.ROOT, "%.2f,%.2f ", x, y));
            }
            svg.append("\"/>");
        }
        var curve = results.get(0).path("result").path("curve");
        return svg.append("<text x=\"20\" y=\"280\" font-size=\"12\">").append(html(curve.get(0).path("date").asText()))
                .append("</text><text x=\"780\" y=\"280\" text-anchor=\"end\" font-size=\"12\">").append(html(curve.get(count - 1).path("date").asText()))
                .append("</text></svg>").toString();
    }
    public static Export export(AutoTradingService.CostAnalysis analysis, String format) {
        var v = analysis.validation();
        var c = v.configuration();
        var metadata = List.of(
                List.of("보고서", "자동 모의 매매 · 비용 민감도"),
                List.of("분석 ID / 검증 ID", analysis.summary().id() + " / " + v.summary().id()),
                List.of("생성 시각 UTC", analysis.summary().createdAt().toString()),
                List.of("전략", v.strategyName()),
                List.of("종목 / 버전", c.path("ticker").asText() + " / " + c.path("version").asText()),
                List.of("조정 기간", c.path("startDate").asText() + " — " + c.path("endDate").asText()),
                List.of("검증 기간", v.summary().startDate() + " — " + v.summary().endDate()),
                List.of("준비 가격", v.warmupStart() + " — " + v.warmupEnd() + " / " + v.warmupCount()),
                List.of("초기 자금 USD", c.path("initialCapital").asText()),
                List.of("SMA / 비중 / 주문 한도 USD", c.path("window").asText() + " / " + c.path("maxPositionWeight").asText() + " / " + c.path("maxOrderValue").asText()),
                List.of("비교 방법", "검증 기록의 동일 가격·기간·전략 고정. 비용만 변경하여 재실행. 1bp = 0.01%. 슬리피지는 체결 가격에 포함되며 수수료 합계와 별개. 비용 변화로 수량·거래 횟수도 바뀔 수 있음."),
                List.of("데이터 한계", LIMITS));
        var rows = new ArrayList<List<String>>();
        rows.add(List.of("조건", "수수료 bp", "슬리피지 bp", "수익률 %", "최대 낙폭 %", "수수료 USD", "체결 횟수", "평가 자산 USD", "거래일"));
        for (var r : analysis.results()) {
            var result = r.path("result"); var state = result.path("strategy");
            rows.add(List.of(r.path("baseline").asBoolean() ? "기준" : "비교", r.path("feeBps").asText(), r.path("slippageBps").asText(),
                    state.path("returnPct").asText(), state.path("maxDrawdownPct").asText(), state.path("fees").asText(),
                    state.path("trades").asText(), state.path("equity").asText(), result.path("steps").asText()));
        }
        String name = "marketboard-cost-analysis-" + analysis.summary().id();
        if ("csv".equals(format)) {
            var output = new StringBuilder("\ufeff");
            for (var group : List.of(metadata, rows)) {
                for (var row : group) output.append(String.join(",", row.stream().map(CostReport::csv).toList())).append("\r\n");
                output.append("\r\n");
            }
            return new Export(name + ".csv", "text/csv;charset=utf-8", output.toString());
        }
        if (!"html".equals(format)) throw new IllegalArgumentException("지원하지 않는 보고서 형식");
        var output = new StringBuilder("""
                <!doctype html><html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>MarketBoard 비용 비교 보고서</title><style>
                body{font:14px/1.7 system-ui,sans-serif;color:#222;max-width:1000px;margin:48px auto;padding:0 24px}
                h1{font-size:28px}h2{margin-top:36px}dt{font-weight:600}dd{margin:0 0 12px;color:#444}
                table{border-collapse:collapse;width:100%;font-size:12px}th,td{padding:10px;border-bottom:1px solid #ddd;text-align:right}th:first-child,td:first-child{text-align:left}
                @media print{body{margin:0;font-size:11px}tr{break-inside:avoid}h2{break-after:avoid}}
                </style><body><p>MarketBoard · 모의 매매</p><h1>비용 변화에 따른 성과 비교</h1><dl>
                """);
        for (var row : metadata) output.append("<dt>").append(html(row.get(0))).append("</dt><dd>").append(html(row.get(1))).append("</dd>");
        output.append("</dl>").append(chart(analysis.results())).append("<h2>비용 시나리오</h2><table><thead><tr>");
        for (String cell : rows.get(0)) output.append("<th>").append(html(cell)).append("</th>");
        output.append("</tr></thead><tbody>");
        for (var row : rows.subList(1, rows.size())) {
            output.append("<tr>"); for (String cell : row) output.append("<td>").append(html(cell)).append("</td>"); output.append("</tr>");
        }
        output.append("</tbody></table><h2>일별 평가 자산 · USD</h2><table><thead><tr><th>날짜</th>");
        for (var r : analysis.results()) output.append("<th>").append(html(r.path("feeBps").asText())).append(" / ").append(html(r.path("slippageBps").asText())).append(" bp</th>");
        output.append("</tr></thead><tbody>");
        var curve = analysis.results().get(0).path("result").path("curve");
        for (int i=0; i<curve.size(); i++) {
            output.append("<tr><td>").append(html(curve.get(i).path("date").asText())).append("</td>");
            for (var r : analysis.results()) output.append("<td>").append(html(r.path("result").path("curve").get(i).path("strategy").asText())).append("</td>");
            output.append("</tr>");
        }
        return new Export(name + ".html", "text/html;charset=utf-8", output.append("</tbody></table></body></html>").toString());
    }
}
