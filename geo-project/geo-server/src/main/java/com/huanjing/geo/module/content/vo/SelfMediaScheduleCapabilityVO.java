package com.huanjing.geo.module.content.vo;

import com.huanjing.geo.module.content.entity.SelfMediaScheduleCapability;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformCapabilityContract;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SelfMediaScheduleCapabilityVO {
    private Long id;
    private String platform;
    private String verificationStatus;
    private Boolean supportsSchedule;
    private Integer minDelayMinutes;
    private Integer maxDelayMinutes;
    private Boolean saveCreatesSchedule;
    private Boolean supportsCancel;
    private Boolean supportsModify;
    private Boolean supportsPublishCheck;
    private String v1Strategy;
    private String selectorStatus;
    private String evidenceJson;
    private String notes;
    private String displayName;
    private String publishChannel;
    private String scheduleMode;
    private Boolean contractRequiresCoverUpload;
    private Boolean contractSupportsLocation;
    private Boolean contractSupportsOneClickFormat;
    private Boolean contractSupportsPublishCheck;
    private LocalDateTime verifiedAt;
    private Long verifiedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SelfMediaScheduleCapabilityVO from(SelfMediaScheduleCapability row) {
        if (row == null) {
            return null;
        }
        SelfMediaScheduleCapabilityVO vo = new SelfMediaScheduleCapabilityVO();
        vo.setId(row.getId());
        vo.setPlatform(row.getPlatform());
        vo.setVerificationStatus(row.getVerificationStatus());
        vo.setSupportsSchedule(row.getSupportsSchedule());
        vo.setMinDelayMinutes(row.getMinDelayMinutes());
        vo.setMaxDelayMinutes(row.getMaxDelayMinutes());
        vo.setSaveCreatesSchedule(row.getSaveCreatesSchedule());
        vo.setSupportsCancel(row.getSupportsCancel());
        vo.setSupportsModify(row.getSupportsModify());
        vo.setSupportsPublishCheck(row.getSupportsPublishCheck());
        vo.setV1Strategy(row.getV1Strategy());
        vo.setSelectorStatus(row.getSelectorStatus());
        vo.setEvidenceJson(row.getEvidenceJson());
        vo.setNotes(row.getNotes());
        vo.setVerifiedAt(row.getVerifiedAt());
        vo.setVerifiedBy(row.getVerifiedBy());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setUpdatedAt(row.getUpdatedAt());
        return vo;
    }

    public void applyContract(SelfMediaPlatformCapabilityContract contract) {
        if (contract == null) {
            return;
        }
        setDisplayName(contract.displayName());
        setPublishChannel(contract.publishChannel().name());
        setScheduleMode(contract.scheduleMode().name());
        setContractRequiresCoverUpload(contract.requiresCoverUpload());
        setContractSupportsLocation(contract.supportsLocation());
        setContractSupportsOneClickFormat(contract.supportsOneClickFormat());
        setContractSupportsPublishCheck(contract.supportsPublishCheck());
    }
}
