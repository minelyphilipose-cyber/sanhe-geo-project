package com.huanjing.geo.module.retention.dynamicswitch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchManagementService.ChangeView;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchManagementService.DisableCommand;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchManagementService.ManualChangeCommand;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchManagementService.SwitchView;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.DynamicSwitchException;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SwitchKey;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "DataRetentionDynamicSwitch")
@RestController
@RequestMapping("/api/data-retention/dynamic-switches")
@RequiredArgsConstructor
public class DataRetentionDynamicSwitchController {

    private final DataRetentionDynamicSwitchManagementService managementService;

    @GetMapping
    public R<List<SwitchView>> list() {
        return R.ok(managementService.list());
    }

    @GetMapping("/changes/{changeId}")
    public R<ChangeView> get(@PathVariable String changeId) {
        return R.ok(managementService.get(changeId));
    }

    @PostMapping("/{switchKey}/changes")
    public R<ChangeView> create(@PathVariable String switchKey,
                                @Valid @RequestBody ManualChangeRequest request) {
        return R.ok(managementService.createManual(parseSwitch(switchKey), new ManualChangeCommand(
                request.changeId(), request.targetEnabled(), request.reason(),
                request.approvalRef(), request.ticketRef())));
    }

    @PostMapping("/changes/{changeId}/approve")
    public R<ChangeView> approve(@PathVariable String changeId,
                                 @Valid @RequestBody ApproveRequest request) {
        return R.ok(managementService.approve(changeId, request.approvalRef()));
    }

    @PostMapping("/{switchKey}/emergency-disable")
    public R<ChangeView> emergencyDisable(@PathVariable String switchKey,
                                          @Valid @RequestBody EmergencyDisableRequest request) {
        return R.ok(managementService.emergencyDisable(parseSwitch(switchKey), new DisableCommand(
                request.changeId(), request.reason(), request.ticketRef())));
    }

    @PostMapping("/changes/{changeId}/reconcile")
    public R<ChangeView> reconcile(@PathVariable String changeId) {
        return R.ok(managementService.reconcile(changeId));
    }

    private static SwitchKey parseSwitch(String value) {
        if (value == null || !value.equals(value.trim())) {
            throw new DynamicSwitchException(400, "UNKNOWN_SWITCH", "Unknown dynamic switch key");
        }
        return SwitchKey.fromDatabaseValue(value);
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ManualChangeRequest(
            @NotBlank String changeId,
            @NotNull Boolean targetEnabled,
            @NotBlank @Size(max = 500) String reason,
            @Size(max = 128) String approvalRef,
            @Size(max = 128) String ticketRef) {
        @JsonAnySetter
        public void rejectUnknown(String field, Object value) {
            throw new DynamicSwitchException(400, "UNKNOWN_FIELD", "Unknown request field: " + field);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record EmergencyDisableRequest(
            @NotBlank String changeId,
            @NotBlank @Size(max = 500) String reason,
            @Size(max = 128) String ticketRef) {
        @JsonAnySetter
        public void rejectUnknown(String field, Object value) {
            throw new DynamicSwitchException(400, "UNKNOWN_FIELD", "Unknown request field: " + field);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ApproveRequest(@NotBlank @Size(max = 128) String approvalRef) {
        @JsonAnySetter
        public void rejectUnknown(String field, Object value) {
            throw new DynamicSwitchException(400, "UNKNOWN_FIELD", "Unknown request field: " + field);
        }
    }
}
