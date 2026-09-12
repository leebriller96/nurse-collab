package com.nursecollab.domain.workorder.dto;

import com.nursecollab.domain.department.dto.DepartmentSummary;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.workorder.entity.OrderPriority;
import com.nursecollab.domain.workorder.entity.OrderStatus;
import com.nursecollab.domain.workorder.entity.OrderType;
import com.nursecollab.domain.workorder.entity.WorkOrder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 요청 상세(W-05 / E-02) */
public record WorkOrderDetailResponse(
        Long id,
        String requestNo,
        OrderType orderType,
        String orderTypeLabel,
        OrderStatus status,
        /** 이 종류에서 이 상태를 부르는 이름 (검사중 / 조제중 / 수리중) */
        String statusLabel,
        OrderPriority priority,
        /**
         * 대상 재원 건. 환자가 없는 업무에서는 비어 있다.
         *
         * <b>여기에는 침대와 병동만 있다.</b> 이름·나이·진단명·주의사항은
         * 화면이 subjectRef 로 원내에 따로 물어 채운다.
         */
        EpisodeInfo episode,
        ServiceItemInfo serviceItem,
        DepartmentSummary fromDepartment,
        DepartmentSummary toDepartment,
        StaffInfo requestedBy,
        OffsetDateTime requestedAt,
        OffsetDateTime desiredAt,
        OffsetDateTime scheduledAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String note,
        String holdReason,
        List<TransitionOption> availableTransitions,
        Long version
) {
    public record EpisodeInfo(UUID subjectRef, String roomNo, String bedNo) {}

    /**
     * @param requiredAlerts 이 업무 전에 확인할 항목. 화면이 이것을 들고 원내에 물어
     *                       "이 업무 전에 확인이 필요합니다" 경고를 받아 온다.
     *                       어떤 항목을 봐야 하는지는 업무 쪽이 알고, 그 사람에게
     *                       그 항목이 있는지는 원내가 안다.
     */
    public record ServiceItemInfo(Long id, String code, String name, int defaultDuration,
                               String prepInstruction,
                               List<com.nursecollab.domain.patient.entity.AlertType> requiredAlerts) {}

    public record StaffInfo(Long id, String name) {}

    public static WorkOrderDetailResponse of(WorkOrder request, Staff viewer) {
        var episode = request.getCareEpisode();
        var serviceItem = request.getServiceItem();
        var type = request.getOrderType();

        EpisodeInfo episodeInfo = episode == null ? null
                : new EpisodeInfo(episode.getSubjectRef(), episode.getRoomNo(), episode.getBedNo());

        List<TransitionOption> transitions = TransitionOption.listOf(request, viewer);

        return new WorkOrderDetailResponse(
                request.getId(),
                request.getRequestNo(),
                type,
                type.getLabel(),
                request.getStatus(),
                type.labelOf(request.getStatus()),
                request.getPriority(),
                episodeInfo,
                new ServiceItemInfo(serviceItem.getId(), serviceItem.getCode(), serviceItem.getName(),
                        serviceItem.getDefaultDuration(), serviceItem.getPrepInstruction(),
                        serviceItem.requiredAlertTypes()),
                DepartmentSummary.from(request.getFromDepartment()),
                DepartmentSummary.from(request.getToDepartment()),
                new StaffInfo(request.getRequestedBy().getId(), request.getRequestedBy().getName()),
                request.getRequestedAt(),
                request.getDesiredAt(),
                request.getScheduledAt(),
                request.getStartedAt(),
                request.getCompletedAt(),
                request.getNote(),
                request.getHoldReason(),
                transitions,
                request.getVersion());
    }
}
