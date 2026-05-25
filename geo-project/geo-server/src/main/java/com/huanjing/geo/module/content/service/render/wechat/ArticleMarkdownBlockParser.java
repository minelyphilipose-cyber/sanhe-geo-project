package com.huanjing.geo.module.content.service.render.wechat;

import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.ArticleBlock;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

@Component
public class ArticleMarkdownBlockParser {
    private final Parser parser = Parser.builder().extensions(List.of(TablesExtension.create())).build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().extensions(List.of(TablesExtension.create())).escapeHtml(false).build();

    public List<ArticleBlock> parse(String markdown) {
        List<ArticleBlock> blocks = new ArrayList<>();
        if (!StringUtils.hasText(markdown)) {
            return blocks;
        }
        Node document = parser.parse(markdown);
        int[] order = {0};
        Node child = document.getFirstChild();
        while (child != null) {
            ArticleBlock block = toBlock(child, ++order[0]);
            if (block != null) {
                blocks.add(block);
            }
            child = child.getNext();
        }
        return blocks;
    }

    public List<ArticleBlock> parse(String markdown, String articleTitle) {
        List<ArticleBlock> blocks = parse(markdown);
        markLeadingArticleTitle(blocks, articleTitle);
        return blocks;
    }

    private ArticleBlock toBlock(Node node, int order) {
        String html = renderer.render(node);
        String text = extractText(node);
        String type = typeOf(node, html, text);
        if (!StringUtils.hasText(type)) {
            return null;
        }
        ArticleBlock block = new ArticleBlock();
        block.setType(type);
        block.setDefaultRole(defaultRole(type));
        block.setAllowedRoles(allowedRoles(type));
        block.setText(text);
        block.setHtml(html);
        block.setOrder(order);
        block.setContentHash(hash(type, normalizeText(text)));
        block.setId("blk_" + block.getContentHash().substring(0, 16));
        if ("image".equals(type)) {
            Element image = Jsoup.parseBodyFragment(html).selectFirst("img");
            if (image != null) {
                block.setImageUrl(image.attr("src"));
                block.setImageAlt(image.attr("alt"));
            }
        }
        return block;
    }

    private String typeOf(Node node, String html, String text) {
        if (node instanceof Heading) {
            return "heading";
        }
        if (node instanceof BlockQuote) {
            return "blockquote";
        }
        if (node instanceof BulletList || node instanceof OrderedList) {
            return "list";
        }
        if (node instanceof TableBlock) {
            return "table";
        }
        if (node instanceof ThematicBreak) {
            return "thematicBreak";
        }
        if (node instanceof FencedCodeBlock || node instanceof IndentedCodeBlock || node instanceof HtmlBlock) {
            return "paragraph";
        }
        if (node instanceof Paragraph) {
            Element image = Jsoup.parseBodyFragment(html).selectFirst("img");
            if (image != null && !StringUtils.hasText(text)) {
                return "image";
            }
            return "paragraph";
        }
        return StringUtils.hasText(text) ? "paragraph" : null;
    }

    private String defaultRole(String type) {
        return switch (type) {
            case "heading" -> "heading";
            case "image" -> "image_block";
            case "blockquote" -> "quote_block";
            case "list", "table" -> "native_html";
            case "thematicBreak" -> "divider";
            default -> "paragraph";
        };
    }

    private List<String> allowedRoles(String type) {
        return switch (type) {
            case "heading" -> List.of("heading", "paragraph", "highlight_block", "golden_sentence_block");
            case "paragraph" -> List.of("paragraph", "highlight_block", "golden_sentence_block", "quote_block");
            case "blockquote" -> List.of("quote_block", "paragraph", "highlight_block");
            case "image" -> List.of("image_block");
            case "list", "table" -> List.of("native_html");
            case "thematicBreak" -> List.of("divider");
            default -> List.of("paragraph");
        };
    }

    private void markLeadingArticleTitle(List<ArticleBlock> blocks, String articleTitle) {
        String normalizedTitle = normalizeText(articleTitle);
        if (!StringUtils.hasText(normalizedTitle)) {
            return;
        }
        for (ArticleBlock block : blocks) {
            if ("heading".equals(block.getType())) {
                if (normalizedTitle.equals(normalizeText(block.getText()))) {
                    block.setType("article_title");
                    block.setDefaultRole("article_title");
                    block.setAllowedRoles(List.of("article_title"));
                    block.setContentHash(hash("article_title", normalizedTitle));
                    block.setId("blk_" + block.getContentHash().substring(0, 16));
                }
                return;
            }
            if (!"image".equals(block.getType()) && !"thematicBreak".equals(block.getType())) {
                return;
            }
        }
    }

    private String extractText(Node node) {
        StringBuilder sb = new StringBuilder();
        node.accept(new AbstractVisitor() {
            @Override
            public void visit(Text text) {
                sb.append(text.getLiteral());
            }

            @Override
            public void visit(SoftLineBreak softLineBreak) {
                sb.append('\n');
            }

            @Override
            public void visit(Image image) {
                if (StringUtils.hasText(image.getTitle())) {
                    sb.append(image.getTitle());
                }
                visitChildren(image);
            }
        });
        return normalizeText(sb.toString());
    }

    public String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ");
    }

    private String hash(String type, String normalizedText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((type + ":" + normalizedText).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
