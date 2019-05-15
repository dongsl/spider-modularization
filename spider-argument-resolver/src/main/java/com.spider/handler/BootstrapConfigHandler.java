package com.spider.handler;

import com.alibaba.fastjson.JSON;
import org.apache.commons.lang.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class BootstrapConfigHandler {

    private static Map<String, String> configMaps = new HashMap();

    public static Map getStandardConfig(String fileName, String methodName, String nodeName) throws FileNotFoundException {
        if (StringUtils.isEmpty(fileName)) return null;
        String filePath = "bean-standard/" + fileName + ".yml";

        if (!configMaps.containsKey(filePath)) {
            Yaml yaml = new Yaml();
            URL url = BootstrapConfigHandler.class.getClassLoader().getResource(filePath);
            if (url != null) {
                configMaps.put(filePath, JSON.toJSONString(yaml.load(new FileInputStream(url.getFile()))));
            }
        }
        Map configMap = JSON.parseObject(configMaps.get(filePath), Map.class);
        if (null == configMap || configMap.isEmpty()) return null;

        if (configMap.containsKey(methodName)) {
            Map<String, Object> standardMap = (Map) configMap.get(methodName);
            if (standardMap.containsKey(nodeName)) {
                standardMap = (Map) standardMap.get(nodeName);
            }
            return standardMap;
        }

        return configMap;
    }
}
