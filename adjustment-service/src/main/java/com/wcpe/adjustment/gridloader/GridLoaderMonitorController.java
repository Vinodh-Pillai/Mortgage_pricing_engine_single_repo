package com.wcpe.adjustment.gridloader;

import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/adjustment/grids")
public class GridLoaderMonitorController {
    private final GseGridRepository repository;
    private final GseGridLoader loader;

    public GridLoaderMonitorController(GseGridRepository repository, GseGridLoader loader) {
        this.repository = repository;
        this.loader = loader;
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public String monitorPage() {
        List<GseGridLoadStatus> statuses = repository.latestStatuses();
        String rows = statuses.stream()
            .map(status -> "<tr><td>" + escape(status.investorCode()) + "</td><td>" + escape(status.ruleBookVersion())
                + "</td><td>" + status.cellCount() + "</td><td>" + escape(status.status()) + "</td><td>" + status.lastLoad() + "</td></tr>")
            .reduce("", String::concat);
        return """
            <!doctype html><html lang=\"en\"><head><title>GSE LLPA Grid Loader</title></head>
            <body><main><h1>GSE LLPA Grid Loader</h1>
            <form method=\"post\" action=\"/admin/adjustment/grids/reload\"><button type=\"submit\">Reload</button></form>
            <table><thead><tr><th>Investor</th><th>Version</th><th>Cells</th><th>Status</th><th>Last Load</th></tr></thead><tbody>
            """ + rows + """
            </tbody></table><nav><a href=\"/admin/adjustment/grids/status\">View Grid</a> | <a href=\"/admin/adjustment/grids/status\">Validate Overlaps</a> | <a href=\"/admin/adjustment/grids/status\">Export CSV</a></nav>
            </main></body></html>
            """;
    }

    @GetMapping(path = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<GseGridLoadStatus> statuses() {
        return repository.latestStatuses();
    }

    @PostMapping("/reload")
    public GseGridLoadStatus reload(@RequestParam(defaultValue = "FNMA") String investor) {
        GseGridMappedRules mapped = "FHLMC".equalsIgnoreCase(investor)
            ? loader.loadGrid("FHLMC", GseGridLoader.FHLMC_LLPA_URL)
            : loader.loadGrid("FNMA", GseGridLoader.FNMA_LLPA_URL);
        return new GseGridLoadStatus(mapped.investorCode(), mapped.ruleBookVersion(), mapped.cellCount(), "LOADED", Instant.now(), mapped.ruleBookHash(), 0, null);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
