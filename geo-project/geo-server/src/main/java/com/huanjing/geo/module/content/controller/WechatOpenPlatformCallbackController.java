package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.module.content.wechat.WechatOpenPlatformEventService;
import com.huanjing.geo.module.content.wechat.WechatOpenPlatformMessageService;
import com.huanjing.geo.module.content.wechat.WechatMessageCryptoService;
import com.huanjing.geo.module.content.wechat.WechatMpAuthorizationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "WechatOpenPlatformCallback")
@RestController
@RequestMapping("/api/wechat/open-platform")
@RequiredArgsConstructor
public class WechatOpenPlatformCallbackController {

    private final WechatOpenPlatformEventService eventService;
    private final WechatOpenPlatformMessageService messageService;
    private final WechatMessageCryptoService cryptoService;
    private final WechatMpAuthorizationService authorizationService;

    @GetMapping("/auth/callback")
    public ResponseEntity<Void> authCallback(@RequestParam(name = "auth_code") String authCode,
                                             @RequestParam String state) {
        String location;
        try {
            location = authorizationService.handleCallback(authCode, state);
        } catch (Exception ex) {
            log.error("WeChat auth callback failed", ex);
            location = authorizationService.errorRedirect("callback_failed");
        }
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_PLAIN_VALUE)
    public String verifyEventUrl(@RequestParam(required = false) String signature,
                                 @RequestParam(required = false, name = "msg_signature") String msgSignature,
                                 @RequestParam(required = false) String timestamp,
                                 @RequestParam(required = false) String nonce,
                                 @RequestParam(required = false) String echostr) {
        return verifyUrl(signature, msgSignature, timestamp, nonce, echostr);
    }

    @PostMapping(value = "/events", consumes = MediaType.ALL_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String receiveEvent(@RequestParam(required = false, name = "msg_signature") String msgSignature,
                               @RequestParam(required = false) String timestamp,
                               @RequestParam(required = false) String nonce,
                               @RequestBody String rawXml) {
        String decrypted = null;
        try {
            decrypted = cryptoService.decryptIfNeeded(rawXml, msgSignature, timestamp, nonce);
            return eventService.handleComponentEvent(decrypted);
        } catch (Exception ex) {
            log.error("WeChat component event callback failed rawXml={} decryptedXml={}",
                    rawXml, decrypted, ex);
            return "success";
        }
    }

    @GetMapping(value = "/messages/{authorizerAppid}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String verifyMessageUrl(@PathVariable String authorizerAppid,
                                   @RequestParam(required = false) String signature,
                                   @RequestParam(required = false, name = "msg_signature") String msgSignature,
                                   @RequestParam(required = false) String timestamp,
                                   @RequestParam(required = false) String nonce,
                                   @RequestParam(required = false) String echostr) {
        return verifyUrl(signature, msgSignature, timestamp, nonce, echostr);
    }

    @PostMapping(value = "/messages/{authorizerAppid}", consumes = MediaType.ALL_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String receiveAuthorizerMessage(@PathVariable String authorizerAppid,
                                           @RequestParam(required = false, name = "msg_signature") String msgSignature,
                                           @RequestParam(required = false) String timestamp,
                                           @RequestParam(required = false) String nonce,
                                           @RequestBody String rawXml) {
        String decrypted = null;
        try {
            boolean encrypted = cryptoService.isEncrypted(rawXml);
            decrypted = cryptoService.decryptIfNeeded(rawXml, msgSignature, timestamp, nonce);
            String response = messageService.handleAuthorizerMessage(authorizerAppid, decrypted);
            return encrypted ? cryptoService.encryptReply(response, timestamp, nonce) : response;
        } catch (Exception ex) {
            log.error("WeChat authorizer message callback failed authorizerAppid={} rawXml={} decryptedXml={}",
                    authorizerAppid, rawXml, decrypted, ex);
            return "success";
        }
    }

    private String verifyUrl(String signature,
                             String msgSignature,
                             String timestamp,
                             String nonce,
                             String echostr) {
        try {
            cryptoService.verifyUrlSignature(StringUtils.hasText(signature) ? signature : msgSignature, timestamp, nonce, echostr);
            return echostr == null ? "" : echostr;
        } catch (Exception ex) {
            log.warn("WeChat callback url verification failed", ex);
            return "";
        }
    }
}
