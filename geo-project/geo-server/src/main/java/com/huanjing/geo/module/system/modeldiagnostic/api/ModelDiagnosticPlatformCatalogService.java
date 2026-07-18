package com.huanjing.geo.module.system.modeldiagnostic.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.modeldiagnostic.ModelDiagnosticPermissions;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticModelTier;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ModelDiagnosticPlatformCatalogService {

    private final AiPlatformConfigMapper platformMapper;
    private final PlatformCredentialService credentialService;
    private final CurrentUserService currentUserService;

    public List<ModelDiagnosticPlatformOption> list() {
        currentUserService.ensurePermission(ModelDiagnosticPermissions.DIAGNOSE);
        return platformMapper.selectList(new LambdaQueryWrapper<AiPlatformConfig>()
                        .orderByAsc(AiPlatformConfig::getChannelCode)
                        .orderByAsc(AiPlatformConfig::getPlatformName)
                        .orderByAsc(AiPlatformConfig::getId))
                .stream().flatMap(this::toOptions).toList();
    }

    private Stream<ModelDiagnosticPlatformOption> toOptions(AiPlatformConfig config) {
        Stream.Builder<ModelDiagnosticPlatformOption> options = Stream.builder();
        options.add(toOption(config, ModelDiagnosticModelTier.PRIMARY, config.getModelId()));
        if (StringUtils.hasText(config.getLowModelId())) {
            options.add(toOption(config, ModelDiagnosticModelTier.LOW, config.getLowModelId()));
        }
        return options.build();
    }

    private ModelDiagnosticPlatformOption toOption(AiPlatformConfig config,
                                                   ModelDiagnosticModelTier modelTier,
                                                   String modelId) {
        IntegrationType type = parseType(config.getIntegrationType());
        List<String> modes = type == IntegrationType.OPENAI_CHAT
                ? List.of("BASIC_CHAT")
                : type != null && type.isWebSearch() ? List.of("WEB_SEARCH") : List.of();
        boolean credentialAvailable = StringUtils.hasText(
                credentialService.resolvePrimaryCredentialStrict(
                        config.getPrimaryKeyRef(), config.getApiKey()));
        String unavailableReason = unavailableReason(config, modelId, modes, credentialAvailable);
        return new ModelDiagnosticPlatformOption(
                config.getId(), fallback(config.getChannelCode(), config.getPlatformCode()),
                config.getPlatformCode(), config.getPlatformName(), modelId, modelTier,
                config.getUsageScene(), config.getIntegrationType(), config.getEnabled(),
                config.getEnabledForQuestionPoll(),
                credentialAvailable, modes, List.of("SYNC"),
                unavailableReason == null, unavailableReason);
    }

    private String unavailableReason(AiPlatformConfig config,
                                     String modelId,
                                     List<String> modes,
                                     boolean credentialAvailable) {
        if (modes.isEmpty()) return "不支持诊断协议";
        if (!StringUtils.hasText(modelId)) return "未配置模型ID";
        if (!validHttpUrl(config.getApiUrl())) return "未配置有效接口地址";
        if (!credentialAvailable) return "主凭证不可用";
        return null;
    }

    private IntegrationType parseType(String value) {
        try {
            return IntegrationType.valueOf(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean validHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getHost() != null && ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String fallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
