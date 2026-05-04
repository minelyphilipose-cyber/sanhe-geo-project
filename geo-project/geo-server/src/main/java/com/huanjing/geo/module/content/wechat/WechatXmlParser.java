package com.huanjing.geo.module.content.wechat;

import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WechatXmlParser {
    private WechatXmlParser() {
    }

    public static Map<String, String> parse(String xml) {
        if (!StringUtils.hasText(xml)) {
            return Map.of();
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            NodeList children = document.getDocumentElement().getChildNodes();
            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    values.put(children.item(i).getNodeName(), children.item(i).getTextContent().trim());
                }
            }
            return values;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid wechat xml payload", ex);
        }
    }

    public static String textReply(String toUser, String fromUser, String content) {
        long now = System.currentTimeMillis() / 1000;
        return "<xml>"
                + "<ToUserName><![CDATA[" + cdata(toUser) + "]]></ToUserName>"
                + "<FromUserName><![CDATA[" + cdata(fromUser) + "]]></FromUserName>"
                + "<CreateTime>" + now + "</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[" + cdata(content) + "]]></Content>"
                + "</xml>";
    }

    private static String cdata(String value) {
        return value == null ? "" : value.replace("]]>", "]]]]><![CDATA[>");
    }
}
