package com.huanjing.geo.module.partner.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.partner.entity.Partner;
import com.huanjing.geo.module.partner.entity.PartnerAccount;
import com.huanjing.geo.module.partner.entity.PartnerAccountTxn;
import com.huanjing.geo.module.partner.entity.PartnerPresaleReportQuotaTxn;
import com.huanjing.geo.module.partner.mapper.PartnerAccountMapper;
import com.huanjing.geo.module.partner.mapper.PartnerAccountTxnMapper;
import com.huanjing.geo.module.partner.mapper.PartnerMapper;
import com.huanjing.geo.module.partner.mapper.PartnerPresaleReportQuotaTxnMapper;
import com.huanjing.geo.module.presale.dto.request.CreateReportRequest;
import com.huanjing.geo.module.system.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartnerPresaleReportQuotaService {

    private static final String ROLE_PARTNER = "partner";
    private static final String ROLE_PARTNER_STAFF = "partner_staff";
    private static final String BIZ_TYPE_FREE_QUOTA = "free_quota";
    private static final String BIZ_TYPE_POINTS = "points";
    private static final String STATUS_RESERVED = "reserved";
    private static final String STATUS_CONFIRMED = "confirmed";
    private static final String POINTS_TXN_BIZ_TYPE = "partner_presale_report";

    private final PartnerMapper partnerMapper;
    private final PartnerAccountMapper partnerAccountMapper;
    private final PartnerAccountTxnMapper partnerAccountTxnMapper;
    private final PartnerPresaleReportQuotaTxnMapper quotaTxnMapper;
    private final ObjectMapper objectMapper;

    public Reservation reserveIfPartner(SysUser operator, CreateReportRequest req) {
        String role = normalizeRole(operator == null ? null : operator.getRole());
        if (ROLE_PARTNER_STAFF.equals(role)) {
            throw new BizException(403, "Partner staff cannot create presale reports");
        }
        if (!ROLE_PARTNER.equals(role)) {
            return Reservation.internal();
        }
        if (operator.getPartnerId() == null) {
            throw new BizException(403, "Partner account missing partner_id binding");
        }
        String requestId = normalizeRequestId(req == null ? null : req.getRequestId());
        String payloadJson = requestPayloadJson(req);
        String requestHash = sha256(payloadJson);

        PartnerPresaleReportQuotaTxn existing = quotaTxnMapper.selectByPartnerAndRequestId(operator.getPartnerId(), requestId);
        if (existing != null) {
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new BizException(409, "Partner presale report requestId payload conflict", 409,
                        Map.of("errorCode", "PARTNER_PRESALE_REQUEST_CONFLICT"));
            }
            if (existing.getReportId() != null) {
                return Reservation.existing(existing.getReportId());
            }
            throw new BizException(409, "Partner presale report request is processing, please retry later", 409,
                    Map.of("errorCode", "PARTNER_PRESALE_REQUEST_PROCESSING"));
        }

        Partner partner = partnerMapper.selectByIdForUpdate(operator.getPartnerId());
        if (partner == null || !"active".equals(partner.getStatus())) {
            throw new BizException(400, "Partner is not active");
        }

        Long usedFree = quotaTxnMapper.countReservedOrConfirmedFreeQuota(partner.getId());
        int freeLimit = partner.getPresaleReportFreeQuotaLimit() == null ? 0 : partner.getPresaleReportFreeQuotaLimit();
        boolean useFreeQuota = usedFree != null && usedFree < freeLimit;
        BigDecimal points = useFreeQuota
                ? BigDecimal.ZERO
                : normalizePoints(partner.getPresaleReportExtraPoints());
        PartnerAccountTxn pointsTxn = null;
        if (!useFreeQuota) {
            pointsTxn = deductPoints(partner.getId(), points, operator.getId(), requestId);
        }

        PartnerPresaleReportQuotaTxn quotaTxn = new PartnerPresaleReportQuotaTxn();
        quotaTxn.setPartnerId(partner.getId());
        quotaTxn.setRequestId(requestId);
        quotaTxn.setRequestHash(requestHash);
        quotaTxn.setRequestPayloadSnapshotJson(payloadJson);
        quotaTxn.setBizType(useFreeQuota ? BIZ_TYPE_FREE_QUOTA : BIZ_TYPE_POINTS);
        quotaTxn.setQuotaAmount(useFreeQuota ? 1 : 0);
        quotaTxn.setPointsAmount(useFreeQuota ? BigDecimal.ZERO : points);
        quotaTxn.setStatus(STATUS_RESERVED);
        quotaTxn.setRelatedPointsTxnId(pointsTxn == null ? null : pointsTxn.getId());
        quotaTxn.setCreatedBy(operator.getId());
        try {
            quotaTxnMapper.insert(quotaTxn);
        } catch (DuplicateKeyException ex) {
            throw new BizException(409, "Partner presale report request already exists, please refresh and retry", 409,
                    Map.of("errorCode", "PARTNER_PRESALE_REQUEST_DUPLICATED"), ex);
        }
        return Reservation.created(partner.getId(), requestId, requestHash, payloadJson, quotaTxn, pointsTxn);
    }

    public void confirm(Reservation reservation, Long reportId) {
        if (reservation == null || !reservation.partnerReservation() || reservation.existingReportId() != null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = quotaTxnMapper.update(null, new UpdateWrapper<PartnerPresaleReportQuotaTxn>()
                .eq("id", reservation.quotaTxn().getId())
                .eq("status", STATUS_RESERVED)
                .set("report_id", reportId)
                .set("status", STATUS_CONFIRMED)
                .set("confirmed_at", now));
        if (updated == 0) {
            throw new BizException(409, "Partner presale quota transaction status changed, please retry");
        }
        if (reservation.pointsTxn() != null) {
            reservation.pointsTxn().setRelatedPresaleReportId(reportId);
            partnerAccountTxnMapper.updateById(reservation.pointsTxn());
        }
    }

    @Transactional
    public void refundForReportFailure(Long reportId, String failureCode, String failureMessage) {
        if (reportId == null) {
            return;
        }
        PartnerPresaleReportQuotaTxn quotaTxn = quotaTxnMapper.selectByReportId(reportId);
        if (quotaTxn == null || !STATUS_CONFIRMED.equals(quotaTxn.getStatus())) {
            return;
        }
        if (BIZ_TYPE_POINTS.equals(quotaTxn.getBizType())
                && quotaTxn.getPointsAmount() != null
                && quotaTxn.getPointsAmount().compareTo(BigDecimal.ZERO) > 0) {
            refundPoints(quotaTxn, failureCode);
        }
        int updated = quotaTxnMapper.update(null, new UpdateWrapper<PartnerPresaleReportQuotaTxn>()
                .eq("id", quotaTxn.getId())
                .eq("status", STATUS_CONFIRMED)
                .set("status", "refunded")
                .set("failure_code", truncate(failureCode, 64))
                .set("failure_message", truncate(failureMessage, 512))
                .set("refunded_at", LocalDateTime.now()));
        if (updated == 0) {
            throw new BizException(409, "Partner presale quota transaction status changed, please retry");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markManualReviewForReportFailure(Long reportId, String failureCode, String failureMessage) {
        if (reportId == null) {
            return;
        }
        quotaTxnMapper.update(null, new UpdateWrapper<PartnerPresaleReportQuotaTxn>()
                .eq("report_id", reportId)
                .eq("status", STATUS_CONFIRMED)
                .set("status", "manual_review")
                .set("failure_code", truncate(failureCode, 64))
                .set("failure_message", truncate(failureMessage, 512)));
    }

    private PartnerAccountTxn deductPoints(Long partnerId, BigDecimal points, Long operatorId, String requestId) {
        if (points == null || points.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "Partner presale report points are not configured");
        }
        PartnerAccount account = partnerAccountMapper.selectByPartnerIdForUpdate(partnerId);
        BigDecimal before = account == null || account.getCurrentBalance() == null
                ? BigDecimal.ZERO
                : account.getCurrentBalance();
        if (account == null || !"active".equals(account.getStatus()) || before.compareTo(points) < 0) {
            throw new BizException(400, "Partner points are insufficient for presale report");
        }
        BigDecimal after = before.subtract(points);
        account.setCurrentBalance(after);
        account.setTotalDeduction((account.getTotalDeduction() == null ? BigDecimal.ZERO : account.getTotalDeduction()).add(points));
        partnerAccountMapper.updateById(account);

        PartnerAccountTxn txn = new PartnerAccountTxn();
        txn.setPartnerId(partnerId);
        txn.setAccountId(account.getId());
        txn.setTxnNo("PPR" + System.currentTimeMillis()
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT));
        txn.setTxnType("deduct");
        txn.setBizType(POINTS_TXN_BIZ_TYPE);
        txn.setAmount(points.negate());
        txn.setBalanceBefore(before);
        txn.setBalanceAfter(after);
        txn.setOperatorUserId(operatorId);
        txn.setRemark("Partner presale report points, requestId=" + requestId);
        partnerAccountTxnMapper.insert(txn);
        return txn;
    }

    private PartnerAccountTxn refundPoints(PartnerPresaleReportQuotaTxn quotaTxn, String failureCode) {
        PartnerAccount account = partnerAccountMapper.selectByPartnerIdForUpdate(quotaTxn.getPartnerId());
        if (account == null || !"active".equals(account.getStatus())) {
            throw new BizException(400, "Partner account is not active");
        }
        BigDecimal amount = quotaTxn.getPointsAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal before = account.getCurrentBalance() == null ? BigDecimal.ZERO : account.getCurrentBalance();
        BigDecimal after = before.add(amount);
        account.setCurrentBalance(after);
        account.setTotalDeduction((account.getTotalDeduction() == null ? BigDecimal.ZERO : account.getTotalDeduction()).subtract(amount));
        partnerAccountMapper.updateById(account);

        PartnerAccountTxn txn = new PartnerAccountTxn();
        txn.setPartnerId(quotaTxn.getPartnerId());
        txn.setAccountId(account.getId());
        txn.setTxnNo("PPRR" + System.currentTimeMillis()
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT));
        txn.setTxnType("refund");
        txn.setBizType(POINTS_TXN_BIZ_TYPE);
        txn.setAmount(amount);
        txn.setBalanceBefore(before);
        txn.setBalanceAfter(after);
        txn.setRelatedPresaleReportId(quotaTxn.getReportId());
        txn.setOperatorUserId(quotaTxn.getCreatedBy());
        txn.setRemark("Partner presale report refund, requestId=" + quotaTxn.getRequestId()
                + ", failureCode=" + (failureCode == null ? "" : failureCode));
        partnerAccountTxnMapper.insert(txn);
        return txn;
    }

    private String normalizeRequestId(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            throw new BizException(400, "Partner presale report requestId is required");
        }
        return requestId.trim();
    }

    private BigDecimal normalizePoints(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String requestPayloadJson(CreateReportRequest req) {
        try {
            JsonNode node = objectMapper.valueToTree(req);
            if (node instanceof ObjectNode objectNode) {
                objectNode.remove("requestId");
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new BizException(400, "Invalid presale report request payload", ex);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BizException(500, "Failed to hash presale report request", ex);
        }
    }

    private String normalizeRole(String role) {
        return StringUtils.hasText(role) ? role.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    public record Reservation(boolean partnerReservation,
                              Long existingReportId,
                              Long partnerId,
                              String requestId,
                              String requestHash,
                              String requestPayloadJson,
                              PartnerPresaleReportQuotaTxn quotaTxn,
                              PartnerAccountTxn pointsTxn) {
        static Reservation internal() {
            return new Reservation(false, null, null, null, null, null, null, null);
        }

        static Reservation existing(Long reportId) {
            return new Reservation(true, reportId, null, null, null, null, null, null);
        }

        static Reservation created(Long partnerId,
                                   String requestId,
                                   String requestHash,
                                   String requestPayloadJson,
                                   PartnerPresaleReportQuotaTxn quotaTxn,
                                   PartnerAccountTxn pointsTxn) {
            return new Reservation(true, null, partnerId, requestId, requestHash, requestPayloadJson, quotaTxn, pointsTxn);
        }
    }
}
