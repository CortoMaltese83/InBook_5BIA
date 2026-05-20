package com.inbook.service;

import com.inbook.repository.AdminAuditEventRepository;
import com.inbook.repository.entity.AdminAuditEvent;
import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.BookImportRun;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminActivityLogService {
    public static final String ACTION_BOOK_IMPORT_MIM_START = "BOOK_IMPORT_MIM_START";
    public static final String ACTION_BOOK_IMPORT_FALLBACK_START = "BOOK_IMPORT_FALLBACK_START";

    private final AdminAuditEventRepository auditRepository;

    public AdminActivityLogService(AdminAuditEventRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBookImportRunStarted(AppUser actor, BookImportRun run) {
        if (run == null) {
            return;
        }
        AdminAuditEvent event = new AdminAuditEvent();
        event.setActor(actor);
        event.setAction(actionForRun(run));
        event.setDetails(detailsForRun(run));
        event.setCreated_at(System.currentTimeMillis());
        auditRepository.save(event);
    }

    private String actionForRun(BookImportRun run) {
        if ("MIM_OPEN_DATA_IMPORT".equals(run.getType())) {
            return ACTION_BOOK_IMPORT_MIM_START;
        }
        if ("ISBN_FALLBACK_BATCH".equals(run.getType())) {
            return ACTION_BOOK_IMPORT_FALLBACK_START;
        }
        return "BOOK_IMPORT_RUN_START";
    }

    private String detailsForRun(BookImportRun run) {
        String label = "MIM_OPEN_DATA_IMPORT".equals(run.getType())
                ? "Importa Open Data MIM"
                : ("ISBN_FALLBACK_BATCH".equals(run.getType()) ? "Aggiorna ISBN locali" : run.getType());
        return label + " - Run #" + run.getId();
    }
}
