package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.ManualArticleImportResponse;
import com.huanjing.geo.module.content.dto.ManualArticleImportResponse.ImportStats;
import com.huanjing.geo.module.content.dto.ManualArticleImportResponse.ImportWarning;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHyperlink;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ManualArticleImportService {
    private static final long MAX_FILE_SIZE = 10L * 1024L * 1024L;
    private static final int MAX_MARKDOWN_LENGTH = 50_000;
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[[^\\]]*]\\([^\\r\\n)]*\\)");
    private static final Pattern HTML_IMAGE = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern DOCX_HEADING_STYLE = Pattern.compile("(?:heading|标题)\\s*([1-6])", Pattern.CASE_INSENSITIVE);
    private static final Set<String> DOCX_CONTENT_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/octet-stream",
            "application/zip"
    );
    private static final Set<String> MARKDOWN_CONTENT_TYPES = Set.of(
            "text/markdown",
            "text/x-markdown",
            "text/plain",
            "application/octet-stream"
    );

    private final CurrentUserService currentUserService;

    public ManualArticleImportResponse parse(MultipartFile file) {
        currentUserService.requireCurrentUser();
        currentUserService.ensurePermissionOrLegacy(
                "content.article.write",
                "project.update",
                Set.of("operator", "delivery_manager", "partner", "partner_staff")
        );
        validateFile(file);

        String fileName = safeFileName(file.getOriginalFilename());
        String extension = extension(fileName);
        try {
            return switch (extension) {
                case "md" -> parseMarkdown(file);
                case "docx" -> parseDocx(file);
                default -> throw unsupportedType();
            };
        } catch (BizException ex) {
            throw ex;
        } catch (CharacterCodingException ex) {
            throw new BizException(400, "MD 文件必须使用 UTF-8 编码");
        } catch (InvalidFormatException ex) {
            throw new BizException(400, "DOCX 文件格式无效或已损坏");
        } catch (IOException ex) {
            throw new BizException("读取文章导入文件失败", ex);
        } catch (RuntimeException ex) {
            throw new BizException(400, "文档无法解析，请确认文件未损坏、未加密且格式为 DOCX 或 MD");
        }
    }

    private ManualArticleImportResponse parseMarkdown(MultipartFile file) throws IOException {
        String source = decodeUtf8(file.getBytes());
        source = stripUtf8Bom(source).replace("\r\n", "\n").replace('\r', '\n');

        SanitizedMarkdown sanitized = omitImages(source);
        MarkdownExtraction extraction = extractMarkdownTitle(sanitized.markdown());
        String body = trimBlankLines(extraction.body());
        ensureUsableBody(body, extraction.title());

        List<ImportWarning> warnings = new ArrayList<>();
        if (sanitized.omittedImages() > 0) {
            warnings.add(new ImportWarning(
                    "IMAGES_OMITTED",
                    "检测到 " + sanitized.omittedImages() + " 张图片，未导入；请从当前项目品牌图库重新添加。",
                    sanitized.omittedImages()
            ));
        }
        if (!StringUtils.hasText(extraction.title()) && StringUtils.hasText(extraction.suggestedTitle())) {
            warnings.add(new ImportWarning(
                    "TITLE_CONFIRMATION_REQUIRED",
                    "未检测到明确的一级标题，请确认是否采用首行作为文章标题。",
                    null
            ));
        }
        if (extraction.demotedHeadings() > 0) {
            warnings.add(new ImportWarning(
                    "MULTIPLE_H1_NORMALIZED",
                    "除文章标题外的 " + extraction.demotedHeadings() + " 个一级标题已调整为二级标题。",
                    extraction.demotedHeadings()
            ));
        }

        return response(
                "md",
                extraction.title(),
                extraction.suggestedTitle(),
                extraction.titleConfidence(),
                body,
                warnings,
                countMarkdownParagraphs(body),
                extraction.headingCount(),
                countMarkdownTables(body),
                sanitized.omittedImages()
        );
    }

    private ManualArticleImportResponse parseDocx(MultipartFile file) throws IOException, InvalidFormatException {
        try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
            DocxExtraction extraction = extractDocx(document);
            String body = trimBlankLines(String.join("\n\n", extraction.blocks()));
            ensureUsableBody(body, extraction.title());

            int omittedImages = document.getAllPictures().size();
            List<ImportWarning> warnings = new ArrayList<>();
            if (omittedImages > 0) {
                warnings.add(new ImportWarning(
                        "EMBEDDED_IMAGES_OMITTED",
                        "检测到 " + omittedImages + " 张 DOCX 内嵌图片，未导入；请从当前项目品牌图库重新添加。",
                        omittedImages
                ));
            }
            if (!StringUtils.hasText(extraction.title()) && StringUtils.hasText(extraction.suggestedTitle())) {
                warnings.add(new ImportWarning(
                        "TITLE_CONFIRMATION_REQUIRED",
                        "文档没有明确的标题样式，请确认是否采用首段作为文章标题。",
                        null
                ));
            }

            return response(
                    "docx",
                    extraction.title(),
                    extraction.suggestedTitle(),
                    StringUtils.hasText(extraction.title()) ? "high" : StringUtils.hasText(extraction.suggestedTitle()) ? "medium" : "none",
                    body,
                    warnings,
                    extraction.paragraphs(),
                    extraction.headings(),
                    extraction.tables(),
                    omittedImages
            );
        }
    }

    private DocxExtraction extractDocx(XWPFDocument document) {
        List<String> blocks = new ArrayList<>();
        String title = "";
        String suggestedTitle = "";
        int paragraphs = 0;
        int headings = 0;
        int tables = 0;

        for (IBodyElement element : document.getBodyElements()) {
            if (element.getElementType() == BodyElementType.PARAGRAPH) {
                XWPFParagraph paragraph = (XWPFParagraph) element;
                String text = paragraphMarkdown(document, paragraph).trim();
                if (!StringUtils.hasText(text)) {
                    continue;
                }
                paragraphs++;
                ParagraphStyle style = paragraphStyle(document, paragraph);
                if (!StringUtils.hasText(title) && style.articleTitle()) {
                    title = plainText(paragraph.getText());
                    continue;
                }
                if (!StringUtils.hasText(title) && style.headingLevel() == 1 && blocks.isEmpty()) {
                    title = plainText(paragraph.getText());
                    continue;
                }
                if (!StringUtils.hasText(suggestedTitle) && blocks.isEmpty()) {
                    String candidate = plainText(paragraph.getText());
                    if (candidate.length() <= 120) {
                        suggestedTitle = candidate;
                    }
                }
                if (style.headingLevel() > 0) {
                    int markdownLevel = Math.min(6, Math.max(2, style.headingLevel() + 1));
                    blocks.add("#".repeat(markdownLevel) + " " + plainText(paragraph.getText()));
                    headings++;
                } else if (paragraph.getNumID() != null) {
                    blocks.add("- " + text);
                } else {
                    blocks.add(text);
                }
            } else if (element.getElementType() == BodyElementType.TABLE) {
                String table = tableMarkdown((XWPFTable) element);
                if (StringUtils.hasText(table)) {
                    blocks.add(table);
                    tables++;
                }
            }
        }
        return new DocxExtraction(blocks, title, suggestedTitle, paragraphs, headings, tables);
    }

    private String paragraphMarkdown(XWPFDocument document, XWPFParagraph paragraph) {
        StringBuilder markdown = new StringBuilder();
        for (XWPFRun run : paragraph.getRuns()) {
            String text = run.text();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            text = text.replace("\t", "    ").replace("\r", "");
            if (run.isBold()) {
                text = "**" + text + "**";
            }
            if (run.isItalic()) {
                text = "*" + text + "*";
            }
            if (run instanceof XWPFHyperlinkRun hyperlinkRun) {
                XWPFHyperlink hyperlink = document.getHyperlinkByID(hyperlinkRun.getHyperlinkId());
                if (hyperlink != null && StringUtils.hasText(hyperlink.getURL())) {
                    text = "[" + text + "](" + hyperlink.getURL().trim() + ")";
                }
            }
            markdown.append(text);
        }
        if (markdown.isEmpty()) {
            return paragraph.getText();
        }
        return markdown.toString();
    }

    private ParagraphStyle paragraphStyle(XWPFDocument document, XWPFParagraph paragraph) {
        String styleName = paragraph.getStyle();
        if (StringUtils.hasText(styleName) && document.getStyles() != null) {
            XWPFStyle style = document.getStyles().getStyle(styleName);
            if (style != null && StringUtils.hasText(style.getName())) {
                styleName = style.getName();
            }
        }
        String normalized = styleName == null ? "" : styleName.trim().toLowerCase(Locale.ROOT).replace("_", " ").replace("-", " ");
        boolean articleTitle = "title".equals(normalized) || "标题".equals(normalized);
        Matcher matcher = DOCX_HEADING_STYLE.matcher(normalized.replaceAll("\\s+", ""));
        int headingLevel = matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
        return new ParagraphStyle(articleTitle, headingLevel);
    }

    private String tableMarkdown(XWPFTable table) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) {
            return "";
        }
        int columnCount = rows.stream().mapToInt(row -> row.getTableCells().size()).max().orElse(0);
        if (columnCount == 0) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        lines.add(tableRowMarkdown(rows.get(0), columnCount));
        lines.add("|" + " --- |".repeat(columnCount));
        for (int i = 1; i < rows.size(); i++) {
            lines.add(tableRowMarkdown(rows.get(i), columnCount));
        }
        return String.join("\n", lines);
    }

    private String tableRowMarkdown(XWPFTableRow row, int columnCount) {
        StringBuilder line = new StringBuilder("|");
        List<XWPFTableCell> cells = row.getTableCells();
        for (int i = 0; i < columnCount; i++) {
            String text = i < cells.size() ? cells.get(i).getText() : "";
            text = text == null ? "" : text.trim().replace("|", "\\|").replaceAll("[\\r\\n]+", "<br>");
            line.append(' ').append(text).append(" |");
        }
        return line.toString();
    }

    private MarkdownExtraction extractMarkdownTitle(String markdown) {
        String[] lines = markdown.split("\n", -1);
        List<String> body = new ArrayList<>();
        String title = "";
        String suggestedTitle = "";
        boolean fenced = false;
        int headings = 0;
        int demoted = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                fenced = !fenced;
                body.add(line);
                continue;
            }
            Matcher matcher = fenced ? null : MARKDOWN_HEADING.matcher(line);
            if (matcher != null && matcher.matches()) {
                int level = matcher.group(1).length();
                String headingText = matcher.group(2).trim();
                if (level == 1 && !StringUtils.hasText(title)) {
                    title = headingText;
                    continue;
                }
                if (level == 1) {
                    body.add("## " + headingText);
                    headings++;
                    demoted++;
                    continue;
                }
                headings++;
            }
            body.add(line);
        }

        if (!StringUtils.hasText(title)) {
            suggestedTitle = firstTitleCandidate(body);
        }
        return new MarkdownExtraction(
                title,
                suggestedTitle,
                StringUtils.hasText(title) ? "high" : StringUtils.hasText(suggestedTitle) ? "medium" : "none",
                String.join("\n", body),
                headings,
                demoted
        );
    }

    private String firstTitleCandidate(List<String> lines) {
        for (String line : lines) {
            String candidate = line.trim().replaceFirst("^#{1,6}\\s+", "");
            candidate = candidate.replaceAll("^[>*_`~\\-\\s]+|[*_`~\\s]+$", "").trim();
            if (StringUtils.hasText(candidate) && candidate.length() <= 120) {
                return candidate;
            }
        }
        return "";
    }

    private SanitizedMarkdown omitImages(String markdown) {
        int count = 0;
        Matcher markdownMatcher = MARKDOWN_IMAGE.matcher(markdown);
        StringBuffer withoutMarkdownImages = new StringBuffer();
        while (markdownMatcher.find()) {
            count++;
            markdownMatcher.appendReplacement(withoutMarkdownImages, "");
        }
        markdownMatcher.appendTail(withoutMarkdownImages);

        Matcher htmlMatcher = HTML_IMAGE.matcher(withoutMarkdownImages.toString());
        StringBuffer withoutImages = new StringBuffer();
        while (htmlMatcher.find()) {
            count++;
            htmlMatcher.appendReplacement(withoutImages, "");
        }
        htmlMatcher.appendTail(withoutImages);
        return new SanitizedMarkdown(withoutImages.toString(), count);
    }

    private ManualArticleImportResponse response(String format,
                                                 String title,
                                                 String suggestedTitle,
                                                 String confidence,
                                                 String body,
                                                 List<ImportWarning> warnings,
                                                 int paragraphs,
                                                 int headings,
                                                 int tables,
                                                 int omittedImages) {
        int characters = body.replaceAll("\\s", "").length();
        return new ManualArticleImportResponse(
                format,
                title,
                suggestedTitle,
                confidence,
                body,
                List.copyOf(warnings),
                new ImportStats(characters, paragraphs, headings, tables, omittedImages)
        );
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "请选择需要导入的 DOCX 或 MD 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(400, "导入文件不能超过 10MB");
        }
        String extension = extension(safeFileName(file.getOriginalFilename()));
        if (!"docx".equals(extension) && !"md".equals(extension)) {
            throw unsupportedType();
        }
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType)) {
            String normalizedContentType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            Set<String> allowedTypes = "docx".equals(extension) ? DOCX_CONTENT_TYPES : MARKDOWN_CONTENT_TYPES;
            if (!allowedTypes.contains(normalizedContentType)) {
                throw new BizException(400, "文件类型与扩展名不匹配，请重新选择 DOCX 或 UTF-8 MD 文件");
            }
        }
    }

    private void ensureUsableBody(String body, String title) {
        if (!StringUtils.hasText(body)) {
            throw new BizException(400, StringUtils.hasText(title) ? "文档只有标题，没有可导入的正文" : "文档没有可导入的正文内容");
        }
        if (body.length() > MAX_MARKDOWN_LENGTH) {
            throw new BizException(400, "解析后的文章正文超过 50000 字符，请缩减内容后重新导入");
        }
    }

    private String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private String stripUtf8Bom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private String trimBlankLines(String value) {
        return value.replaceFirst("^(?:[ \\t]*\\n)+", "").replaceFirst("(?:\\n[ \\t]*)+$", "").trim();
    }

    private String plainText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private int countMarkdownParagraphs(String markdown) {
        return (int) Pattern.compile("(?m)^(?!\\s*$)(?!#{1,6}\\s)(?!\\|)(?![-*+]\\s)(?!\\d+\\.\\s).+")
                .matcher(markdown)
                .results()
                .count();
    }

    private int countMarkdownTables(String markdown) {
        return (int) Pattern.compile("(?m)^\\|(?:\\s*:?-+:?\\s*\\|)+$").matcher(markdown).results().count();
    }

    private String safeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        String normalized = fileName.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1).trim();
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private BizException unsupportedType() {
        return new BizException(400, "仅支持 DOCX 和 MD 文件，不支持 DOC、DOCM 或其他格式");
    }

    private record SanitizedMarkdown(String markdown, int omittedImages) {
    }

    private record MarkdownExtraction(String title,
                                      String suggestedTitle,
                                      String titleConfidence,
                                      String body,
                                      int headingCount,
                                      int demotedHeadings) {
    }

    private record DocxExtraction(List<String> blocks,
                                  String title,
                                  String suggestedTitle,
                                  int paragraphs,
                                  int headings,
                                  int tables) {
    }

    private record ParagraphStyle(boolean articleTitle, int headingLevel) {
    }
}
