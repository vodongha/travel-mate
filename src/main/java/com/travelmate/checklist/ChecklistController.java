package com.travelmate.checklist;

import com.travelmate.checklist.dto.ChecklistItemResponse;
import com.travelmate.checklist.dto.CreateChecklistItemRequest;
import com.travelmate.checklist.dto.UpdateChecklistItemRequest;
import com.travelmate.common.security.AuthPrincipal;
import com.travelmate.common.security.CurrentUser;
import com.travelmate.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Trip checklist (SPEC §7 Module 12). */
@RestController
@RequestMapping("/api/v1/trips/{tripRid}/checklist")
public class ChecklistController {

    private final ChecklistService checklistService;

    public ChecklistController(ChecklistService checklistService) {
        this.checklistService = checklistService;
    }

    @GetMapping
    public ApiResponse<List<ChecklistItemResponse>> list(@CurrentUser AuthPrincipal principal,
                                                         @PathVariable String tripRid) {
        return ApiResponse.ok(checklistService.list(principal.id(), tripRid));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChecklistItemResponse> create(@CurrentUser AuthPrincipal principal,
                                                     @PathVariable String tripRid,
                                                     @Valid @RequestBody CreateChecklistItemRequest request) {
        return ApiResponse.ok(checklistService.create(principal.id(), tripRid, request));
    }

    @PatchMapping("/{rid}")
    public ApiResponse<ChecklistItemResponse> update(@CurrentUser AuthPrincipal principal,
                                                     @PathVariable String tripRid,
                                                     @PathVariable String rid,
                                                     @Valid @RequestBody UpdateChecklistItemRequest request) {
        return ApiResponse.ok(checklistService.update(principal.id(), tripRid, rid, request));
    }

    @DeleteMapping("/{rid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser AuthPrincipal principal,
                       @PathVariable String tripRid,
                       @PathVariable String rid) {
        checklistService.delete(principal.id(), tripRid, rid);
    }
}
