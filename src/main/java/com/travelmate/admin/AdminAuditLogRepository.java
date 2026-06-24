package com.travelmate.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Append-only admin audit log; reads are paginated (sort/search driven by the admin DataTable). */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    /** Page of entries, optionally filtered by a case-insensitive match on action/target. */
    @Query("""
            select a from AdminAuditLog a
            where :q = ''
               or lower(a.action) like lower(concat('%', :q, '%'))
               or lower(a.targetType) like lower(concat('%', :q, '%'))
               or lower(a.targetRid) like lower(concat('%', :q, '%'))
            """)
    Page<AdminAuditLog> search(@Param("q") String q, Pageable pageable);
}
