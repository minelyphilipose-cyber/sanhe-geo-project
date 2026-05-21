package com.huanjing.geo.module.content.service.adapter;

import java.util.List;

public record ForumPublishPayload(Long articleId,
                                  Long projectId,
                                  String title,
                                  String contentMarkdown,
                                  String contentHtml,
                                  String category,
                                  List<String> tags) {
}
