package com.spider.handler;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;

public class XmlHandler {

    public static String createXmlByMap(Map<String, Object> map, String parentName) {
        //获取map的key对应的value
        Map<String, Object> rootMap = (Map<String, Object>) map.get(parentName);
        if (rootMap == null) {
            rootMap = map;
        }
        Document doc = DocumentHelper.createDocument();
        //设置根节点
        doc.addElement(parentName);
        String xml = iteratorXml(doc.getRootElement(), parentName, rootMap);
        return formatXML(xml);
    }

    public static String iteratorXml(Element element, String parentName, Map<String, Object> params) {
        String[] parentNames = parentName.split(" ");
        Element e = element.addElement(parentNames[0]);
        if (parentNames.length > 1) {
            String[] attr = parentNames[1].split("=");
            String attrName = attr[0];
            String attrValue = attr[1];
            e.addAttribute(attrName, attrValue);
        }


        Set<String> set = params.keySet();
        for (Iterator<String> it = set.iterator(); it.hasNext(); ) {
            String key = it.next();
            if (params.get(key) instanceof Map) {
                iteratorXml(e, key, (Map<String, Object>) params.get(key));
            } else if (params.get(key) instanceof List) {
                List<Object> list = (ArrayList<Object>) params.get(key);
                for (int i = 0; i < list.size(); i++) {
                    iteratorXml(e, key, (Map<String, Object>) list.get(i));
                }
            } else {
                String value = params.get(key) == null ? "" : params.get(key)
                        .toString();
                e.addElement(key).addText(value);
                // e.addElement(key).addCDATA(value);
            }
        }
        return e.asXML();
    }

    public static String formatXML(String xml) {
        String requestXML = null;
        XMLWriter writer = null;
        Document document = null;
        try {
            SAXReader reader = new SAXReader();
            document = reader.read(new StringReader(xml));
            if (document != null) {
                StringWriter stringWriter = new StringWriter();
                OutputFormat format = new OutputFormat(" ", true);// 格式化，每一级前的空格
                format.setNewLineAfterDeclaration(false); // xml声明与内容是否添加空行
                format.setSuppressDeclaration(false); // 是否设置xml声明头部 false：添加
                format.setNewlines(true); // 设置分行
                writer = new XMLWriter(stringWriter, format);
                writer.write(document);
                writer.flush();
                requestXML = stringWriter.getBuffer().toString();
            }
            return requestXML;
        } catch (Exception e1) {
            e1.printStackTrace();
            return null;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {

                }
            }
        }
    }

    public static List xml2List(String xml) {
        try {
            List list = new ArrayList();
            Document document = DocumentHelper.parseText(xml);
            Element nodesElement = document.getRootElement();
            List nodes = nodesElement.elements();
            for (Iterator its = nodes.iterator(); its.hasNext(); ) {
                Element nodeElement = (Element) its.next();
                if (("l").equals(nodeElement.attributeValue("type"))) {
                    List s = xml2List(nodeElement.asXML());
                    list.add(s);
                    s = null;
                } else if (("o").equals(nodeElement.attributeValue("type"))) {
                    Map map = xml2Map(nodeElement.asXML());
                    list.add(map);
                    map = null;
                } else {
                    list.add(nodeElement.getText());
                }
            }
            nodes = null;
            nodesElement = null;
            document = null;
            return list;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Map xml2Map(String xml) {
        try {
            Map map = new HashMap();
            Document document = DocumentHelper.parseText(xml);
            Element nodeElement = document.getRootElement();
            List node = nodeElement.elements();
            for (Iterator it = node.iterator(); it.hasNext(); ) {
                Element elm = (Element) it.next();
                if ("l".equals(elm.attributeValue("type"))) {
                    map.put(elm.getName(), xml2List(elm.asXML()));
                } else if ("o".equals(elm.attributeValue("type"))) {
                    map.put(elm.getName(), xml2Map(elm.asXML()));
                } else {
                    map.put(elm.getName(), elm.getText());
                }
                elm = null;
            }
            node = null;
            nodeElement = null;
            document = null;
            return map;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
