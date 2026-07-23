package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.ManualArticleImportResponse;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManualArticleImportServiceTest {
    private CurrentUserService currentUserService;
    private ManualArticleImportService service;

    @BeforeEach
    void setUp() {
        currentUserService = mock(CurrentUserService.class);
        when(currentUserService.requireCurrentUser()).thenReturn(new SysUser());
        service = new ManualArticleImportService(currentUserService);
    }

    @Test
    void parsesMarkdownTitleAndOmitsImages() {
        String markdown = "# 完整标题\n\n第一段。\n\n![外部图](https://example.test/a.png)\n\n# 第二部分\n\n正文。";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "article.md",
                "text/markdown",
                markdown.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        ManualArticleImportResponse response = service.parse(file);

        assertEquals("md", response.format());
        assertEquals("完整标题", response.title());
        assertTrue(response.contentMarkdown().contains("## 第二部分"));
        assertFalse(response.contentMarkdown().contains("example.test"));
        assertEquals(1, response.stats().omittedImages());
        assertTrue(response.warnings().stream().anyMatch(item -> "IMAGES_OMITTED".equals(item.code())));
        assertTrue(response.warnings().stream().anyMatch(item -> "MULTIPLE_H1_NORMALIZED".equals(item.code())));
    }

    @Test
    void suggestsLeadingMarkdownLineWithoutRemovingIt() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "article.md",
                "text/markdown",
                "可能的标题\n\n正文内容".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        ManualArticleImportResponse response = service.parse(file);

        assertEquals("", response.title());
        assertEquals("可能的标题", response.suggestedTitle());
        assertTrue(response.contentMarkdown().startsWith("可能的标题"));
    }

    @Test
    void parsesDocxTitleParagraphAndTable() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph title = document.createParagraph();
            title.setStyle("Title");
            title.createRun().setText("DOCX 标题");
            document.createParagraph().createRun().setText("第一段正文");
            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("名称");
            table.getRow(0).getCell(1).setText("说明");
            table.getRow(1).getCell(0).setText("A");
            table.getRow(1).getCell(1).setText("B");
            document.write(output);
            bytes = output.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "article.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                bytes
        );

        ManualArticleImportResponse response = service.parse(file);

        assertEquals("docx", response.format());
        assertEquals("DOCX 标题", response.title());
        assertTrue(response.contentMarkdown().contains("第一段正文"));
        assertTrue(response.contentMarkdown().contains("| 名称 | 说明 |"));
        assertEquals(1, response.stats().tables());
    }

    @Test
    void rejectsUnsupportedLegacyWordFormat() {
        MockMultipartFile file = new MockMultipartFile("file", "article.doc", "application/msword", new byte[]{1, 2, 3});

        BizException exception = assertThrows(BizException.class, () -> service.parse(file));

        assertTrue(exception.getMessage().contains("仅支持 DOCX 和 MD"));
    }

    @Test
    void rejectsDocumentWithTitleOnly() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "article.md",
                "text/markdown",
                "# 只有标题".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        BizException exception = assertThrows(BizException.class, () -> service.parse(file));

        assertTrue(exception.getMessage().contains("只有标题"));
    }
}
