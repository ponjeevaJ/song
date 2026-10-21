package io.isaiworld.catalog;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminCatalogController {
    private final CatalogSyncService sync;

    public AdminCatalogController(CatalogSyncService sync) {
        this.sync = sync;
    }

    @PostMapping("/sync")
    public CatalogSyncService.SyncResult refresh() {
        return sync.synchronize();
    }

    @GetMapping("/sync-logs")
    public List<CatalogSyncService.SyncLog> logs() {
        return sync.logs();
    }
}
