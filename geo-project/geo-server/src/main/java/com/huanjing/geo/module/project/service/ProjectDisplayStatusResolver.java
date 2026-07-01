package com.huanjing.geo.module.project.service;

import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectStartRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProjectDisplayStatusResolver {

    public ProjectDisplayStatusResult resolve(Project project, ProjectStartRequest latestRequest) {
        String displayStatus = resolveStatus(project, latestRequest);
        return new ProjectDisplayStatusResult(
                displayStatus,
                labelOf(displayStatus),
                isEditable(displayStatus),
                isSubmittable(displayStatus)
        );
    }

    public String resolveStatus(Project project, ProjectStartRequest latestRequest) {
        String projectStatus = project == null ? null : project.getStatus();
        String requestStatus = latestRequest == null ? null : latestRequest.getStatus();

        if (ProjectFlowPolicy.ACTIVE.equals(projectStatus)) {
            return ProjectDisplayStatus.ACTIVE;
        }
        if (ProjectFlowPolicy.PAUSED.equals(projectStatus)) {
            return ProjectDisplayStatus.PAUSED;
        }
        if (ProjectFlowPolicy.COMPLETED.equals(projectStatus)) {
            return ProjectDisplayStatus.COMPLETED;
        }
        if (ProjectFlowPolicy.ARCHIVED.equals(projectStatus) || ProjectFlowPolicy.EXPIRED.equals(projectStatus)) {
            return ProjectDisplayStatus.ARCHIVED;
        }
        if (ProjectFlowPolicy.SETUP_READY.equals(projectStatus)) {
            return ProjectDisplayStatus.SETUP_READY;
        }
        if (ProjectFlowPolicy.APPROVED_PENDING_SETUP.equals(projectStatus)) {
            return ProjectDisplayStatus.APPROVED_PENDING_SETUP;
        }

        if ("submitted".equals(requestStatus)) {
            return ProjectDisplayStatus.SUBMITTED;
        }
        if ("rejected".equals(requestStatus)) {
            return ProjectDisplayStatus.REJECTED;
        }
        if ("cancelled".equals(requestStatus)) {
            return ProjectDisplayStatus.DRAFT;
        }
        if ("approved".equals(requestStatus)) {
            return ProjectDisplayStatus.APPROVED_PENDING_SETUP;
        }

        if (ProjectFlowPolicy.SUBMITTED.equals(projectStatus)) {
            return ProjectDisplayStatus.SUBMITTED;
        }
        if (ProjectFlowPolicy.REJECTED.equals(projectStatus)) {
            return ProjectDisplayStatus.REJECTED;
        }
        if (ProjectFlowPolicy.CANCELLED.equals(projectStatus)) {
            return ProjectDisplayStatus.CANCELLED;
        }

        if (!StringUtils.hasText(projectStatus) || ProjectFlowPolicy.PENDING_START.equals(projectStatus)) {
            return ProjectDisplayStatus.DRAFT;
        }
        return projectStatus;
    }

    private boolean isEditable(String displayStatus) {
        return ProjectDisplayStatus.DRAFT.equals(displayStatus) || ProjectDisplayStatus.REJECTED.equals(displayStatus);
    }

    private boolean isSubmittable(String displayStatus) {
        return ProjectDisplayStatus.DRAFT.equals(displayStatus) || ProjectDisplayStatus.REJECTED.equals(displayStatus);
    }

    private String labelOf(String displayStatus) {
        return switch (displayStatus) {
            case ProjectDisplayStatus.DRAFT -> "草稿";
            case ProjectDisplayStatus.SUBMITTED -> "已提交";
            case ProjectDisplayStatus.REJECTED -> "已驳回";
            case ProjectDisplayStatus.APPROVED_PENDING_SETUP -> "已审批待配置";
            case ProjectDisplayStatus.SETUP_READY -> "配置完成待启动";
            case ProjectDisplayStatus.ACTIVE -> "已启动";
            case ProjectDisplayStatus.PAUSED -> "已暂停";
            case ProjectDisplayStatus.COMPLETED -> "已完成";
            case ProjectDisplayStatus.ARCHIVED -> "已归档";
            case ProjectDisplayStatus.CANCELLED -> "已取消";
            default -> displayStatus;
        };
    }
}
