package com.huanjing.geo.module.content.service;

public record PromptContextRequest(Long projectId,
                                   String topicSource,
                                   String articleType,
                                   String channelGroupCode,
                                   String channelSubCode,
                                   String topic,
                                   String topicAsQuestion,
                                   String length,
                                   Long keywordGroupId,
                                   String keywordGroupName,
                                   String extraPrompt,
                                   Long promptTemplateId,
                                   Long promptTemplateVersionId,
                                   int articleIndexInBatch) {
}
