package org.juns.marketboardbackend.marketreport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest(properties = "market-report.cron=-")
@AutoConfigureMockMvc
class MarketReportSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean MarketReportService service;

    @Test void publicCanReadButCannotPublishOrReadPrivateStockReports() throws Exception {
        when(service.list(null)).thenReturn(List.of());
        mvc.perform(get("/api/market-reports")).andExpect(status().isOk());
        mvc.perform(post("/api/admin/market-reports/generate")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reports/1")).andExpect(status().isUnauthorized());
        verify(service, never()).generate();
    }
    @Test @WithMockUser(roles = "USER") void regularUserCannotPublish() throws Exception {
        mvc.perform(post("/api/admin/market-reports/generate")).andExpect(status().isForbidden());
        verify(service, never()).generate();
    }
    @Test @WithMockUser(roles = "ADMIN") void adminCanPublish() throws Exception {
        when(service.generate()).thenReturn(new MarketReportService.Generation("DELAYED", null, null, List.of("자료 준비 중")));
        mvc.perform(post("/api/admin/market-reports/generate")).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("DELAYED"));
    }
}
