package com.huanjing.geo.module.partner.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartnerPresaleReportQuotaServiceTest {

    private PartnerMapper partnerMapper;
    private PartnerAccountMapper partnerAccountMapper;
    private PartnerAccountTxnMapper partnerAccountTxnMapper;
    private PartnerPresaleReportQuotaTxnMapper quotaTxnMapper;
    private PartnerPresaleReportQuotaService service;

    @BeforeEach
    void setUp() {
        partnerMapper = mock(PartnerMapper.class);
        partnerAccountMapper = mock(PartnerAccountMapper.class);
        partnerAccountTxnMapper = mock(PartnerAccountTxnMapper.class);
        quotaTxnMapper = mock(PartnerPresaleReportQuotaTxnMapper.class);
        service = new PartnerPresaleReportQuotaService(
                partnerMapper,
                partnerAccountMapper,
                partnerAccountTxnMapper,
                quotaTxnMapper,
                new ObjectMapper()
        );
        doAnswer(invocation -> {
            PartnerPresaleReportQuotaTxn txn = invocation.getArgument(0);
            txn.setId(99L);
            return 1;
        }).when(quotaTxnMapper).insert(any(PartnerPresaleReportQuotaTxn.class));
        doAnswer(invocation -> {
            PartnerAccountTxn txn = invocation.getArgument(0);
            txn.setId(199L);
            return 1;
        }).when(partnerAccountTxnMapper).insert(any(PartnerAccountTxn.class));
    }

    @Test
    void internalUserDoesNotReservePartnerQuota() {
        var reservation = service.reserveIfPartner(user("operator"), request("REQ-1", "三和口腔"));

        assertFalse(reservation.partnerReservation());
        verify(quotaTxnMapper, never()).insert(any());
    }

    @Test
    void partnerStaffCannotCreatePresaleReport() {
        BizException ex = assertThrows(BizException.class,
                () -> service.reserveIfPartner(user("partner_staff"), request("REQ-1", "三和口腔")));

        assertEquals(403, ex.getCode());
        verify(quotaTxnMapper, never()).insert(any());
    }

    @Test
    void partnerUsesFreeQuotaWhenRemaining() {
        when(partnerMapper.selectByIdForUpdate(100L)).thenReturn(partner(2, new BigDecimal("5.00")));
        when(quotaTxnMapper.countReservedOrConfirmedFreeQuota(100L)).thenReturn(1L);

        var reservation = service.reserveIfPartner(user("partner"), request("REQ-1", "三和口腔"));

        assertTrue(reservation.partnerReservation());
        assertEquals(100L, reservation.partnerId());
        assertEquals("free_quota", reservation.quotaTxn().getBizType());
        assertEquals(1, reservation.quotaTxn().getQuotaAmount());
        verify(partnerAccountMapper, never()).selectByPartnerIdForUpdate(any());
    }

    @Test
    void partnerDeductsPointsWhenFreeQuotaExceeded() {
        when(partnerMapper.selectByIdForUpdate(100L)).thenReturn(partner(1, new BigDecimal("5.00")));
        when(quotaTxnMapper.countReservedOrConfirmedFreeQuota(100L)).thenReturn(1L);
        when(partnerAccountMapper.selectByPartnerIdForUpdate(100L)).thenReturn(account(new BigDecimal("20.00")));

        var reservation = service.reserveIfPartner(user("partner"), request("REQ-1", "三和口腔"));

        assertEquals("points", reservation.quotaTxn().getBizType());
        assertEquals(new BigDecimal("5.00"), reservation.quotaTxn().getPointsAmount());
        assertEquals(199L, reservation.quotaTxn().getRelatedPointsTxnId());

        ArgumentCaptor<PartnerAccount> accountCaptor = ArgumentCaptor.forClass(PartnerAccount.class);
        verify(partnerAccountMapper).updateById(accountCaptor.capture());
        assertEquals(new BigDecimal("15.00"), accountCaptor.getValue().getCurrentBalance());

        ArgumentCaptor<PartnerAccountTxn> pointsCaptor = ArgumentCaptor.forClass(PartnerAccountTxn.class);
        verify(partnerAccountTxnMapper).insert(pointsCaptor.capture());
        assertEquals(new BigDecimal("-5.00"), pointsCaptor.getValue().getAmount());
        assertEquals("partner_presale_report", pointsCaptor.getValue().getBizType());
    }

    @Test
    void existingRequestWithReportIdReturnsExistingReport() {
        PartnerPresaleReportQuotaTxn existing = new PartnerPresaleReportQuotaTxn();
        existing.setPartnerId(100L);
        existing.setRequestId("REQ-1");
        existing.setRequestHash(hashFor(request("REQ-1", "三和口腔")));
        existing.setReportId(900L);
        when(quotaTxnMapper.selectByPartnerAndRequestId(100L, "REQ-1")).thenReturn(existing);

        var reservation = service.reserveIfPartner(user("partner"), request("REQ-1", "三和口腔"));

        assertEquals(900L, reservation.existingReportId());
        verify(partnerMapper, never()).selectByIdForUpdate(any());
    }

    @Test
    void existingRequestWithDifferentPayloadIsConflict() {
        PartnerPresaleReportQuotaTxn existing = new PartnerPresaleReportQuotaTxn();
        existing.setPartnerId(100L);
        existing.setRequestId("REQ-1");
        existing.setRequestHash(hashFor(request("REQ-1", "其他品牌")));
        when(quotaTxnMapper.selectByPartnerAndRequestId(100L, "REQ-1")).thenReturn(existing);

        BizException ex = assertThrows(BizException.class,
                () -> service.reserveIfPartner(user("partner"), request("REQ-1", "三和口腔")));

        assertEquals(409, ex.getCode());
        verify(partnerMapper, never()).selectByIdForUpdate(any());
    }

    @Test
    void confirmLinksQuotaAndPointsTxnToReport() {
        PartnerPresaleReportQuotaTxn quotaTxn = new PartnerPresaleReportQuotaTxn();
        quotaTxn.setId(99L);
        quotaTxn.setBizType("points");
        PartnerAccountTxn pointsTxn = new PartnerAccountTxn();
        pointsTxn.setId(199L);
        var reservation = PartnerPresaleReportQuotaService.Reservation.created(
                100L, "REQ-1", "hash", "{}", quotaTxn, pointsTxn);
        when(quotaTxnMapper.update(any(), any())).thenReturn(1);

        service.confirm(reservation, 900L);

        verify(quotaTxnMapper).update(any(), any());
        ArgumentCaptor<PartnerAccountTxn> pointsCaptor = ArgumentCaptor.forClass(PartnerAccountTxn.class);
        verify(partnerAccountTxnMapper).updateById(pointsCaptor.capture());
        assertEquals(900L, pointsCaptor.getValue().getRelatedPresaleReportId());
    }

    @Test
    void refundForReportFailureRefundsPointsAndMarksQuotaTxnRefunded() {
        PartnerPresaleReportQuotaTxn quotaTxn = new PartnerPresaleReportQuotaTxn();
        quotaTxn.setId(99L);
        quotaTxn.setPartnerId(100L);
        quotaTxn.setRequestId("REQ-1");
        quotaTxn.setReportId(900L);
        quotaTxn.setBizType("points");
        quotaTxn.setPointsAmount(new BigDecimal("5.00"));
        quotaTxn.setStatus("confirmed");
        quotaTxn.setCreatedBy(7L);
        when(quotaTxnMapper.selectByReportId(900L)).thenReturn(quotaTxn);
        when(partnerAccountMapper.selectByPartnerIdForUpdate(100L)).thenReturn(account(new BigDecimal("15.00")));
        when(quotaTxnMapper.update(any(), any())).thenReturn(1);

        service.refundForReportFailure(900L, "GENERATION_FAILED", "生成失败");

        ArgumentCaptor<PartnerAccount> accountCaptor = ArgumentCaptor.forClass(PartnerAccount.class);
        verify(partnerAccountMapper).updateById(accountCaptor.capture());
        assertEquals(new BigDecimal("20.00"), accountCaptor.getValue().getCurrentBalance());

        ArgumentCaptor<PartnerAccountTxn> refundCaptor = ArgumentCaptor.forClass(PartnerAccountTxn.class);
        verify(partnerAccountTxnMapper).insert(refundCaptor.capture());
        assertEquals("refund", refundCaptor.getValue().getTxnType());
        assertEquals(new BigDecimal("5.00"), refundCaptor.getValue().getAmount());
        assertEquals(900L, refundCaptor.getValue().getRelatedPresaleReportId());
        verify(quotaTxnMapper).update(any(), any());
    }

    @Test
    void failedRefundCanBeMarkedManualReviewInIndependentStep() {
        PartnerPresaleReportQuotaTxn quotaTxn = new PartnerPresaleReportQuotaTxn();
        quotaTxn.setId(99L);
        quotaTxn.setPartnerId(100L);
        quotaTxn.setRequestId("REQ-1");
        quotaTxn.setReportId(900L);
        quotaTxn.setBizType("points");
        quotaTxn.setPointsAmount(new BigDecimal("5.00"));
        quotaTxn.setStatus("confirmed");
        when(quotaTxnMapper.selectByReportId(900L)).thenReturn(quotaTxn);
        when(partnerAccountMapper.selectByPartnerIdForUpdate(100L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> service.refundForReportFailure(900L, "GENERATION_FAILED", "生成失败"));

        assertEquals(400, ex.getCode());
        service.markManualReviewForReportFailure(900L, "REFUND_FAILED", "账户异常");

        verify(quotaTxnMapper).update(any(), any());
    }

    private String hashFor(CreateReportRequest req) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode node = mapper.valueToTree(req);
            node.remove("requestId");
            String payload = mapper.writeValueAsString(node);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private SysUser user(String role) {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setRole(role);
        user.setPartnerId(100L);
        user.setIsActive(true);
        return user;
    }

    private Partner partner(int freeLimit, BigDecimal extraPoints) {
        Partner partner = new Partner();
        partner.setId(100L);
        partner.setStatus("active");
        partner.setPresaleReportFreeQuotaLimit(freeLimit);
        partner.setPresaleReportExtraPoints(extraPoints);
        return partner;
    }

    private PartnerAccount account(BigDecimal balance) {
        PartnerAccount account = new PartnerAccount();
        account.setId(300L);
        account.setPartnerId(100L);
        account.setStatus("active");
        account.setCurrentBalance(balance);
        account.setTotalDeduction(BigDecimal.ZERO);
        return account;
    }

    private CreateReportRequest request(String requestId, String brandName) {
        CreateReportRequest req = new CreateReportRequest();
        req.setRequestId(requestId);
        req.setBrandName(brandName);
        req.setIndustry("healthcare");
        req.setIndustryRole("chain_brand");
        req.setRegion("上海");
        return req;
    }
}
