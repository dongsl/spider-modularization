package com.spider.handler;

import com.spider.bean.StandardizationBean;
import org.apache.commons.lang.StringUtils;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.util.CollectionUtils;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class CustomerHandlerMethodArgumentResolver implements HandlerMethodArgumentResolver {

    protected static final List<HttpMessageConverter<?>> messageConverters = new ArrayList<>();
    private static final String SPLIT = "split";
    private static final String LIST = "LIST";
    private static final String ELEMENT_ATTR = "ELEMENT-ATTR";
    private static final String DICTIONARY = "DICTIONARY";
    private static final String INPUT_RESET = "INPUT-RESET";
    private static final Integer SPLIT_INPUT_NAME_IDX = 0;
    private static final Integer SPLIT_MARK_IDX = 1;
    private static final Integer SPLIT_VALUE_IDX = 2;

    static {
        messageConverters.add(new MappingJackson2HttpMessageConverter());
        messageConverters.add(new MappingJackson2XmlHttpMessageConverter());
    }

    /**
     * 生成requestBean
     *
     * @param standardMap 标准化map 模版
     * @param inputMap    输入信息
     * @param requestBean
     */
    public void initRequestBean(Map<String, Object> standardMap, Map<String, Object> inputMap, Object requestBean) {
        if (null == standardMap || standardMap.isEmpty()) return;
        standardMap.entrySet().stream().forEach(e -> {
            try {
                String key = e.getKey();
                Object value = e.getValue();

                if (key.equals(SPLIT)) { //需要拆分内容的 映射关系
                    if (value instanceof Map) {
                        Map<String, String> splitMap = (Map) value; //映射关系
                        splitMap.entrySet().stream().forEach(es -> { //根据数据 输入模版进行 拆分内容
                            String[] templateSplit = es.getValue().split(","); //拆分信息
                            String inputName = templateSplit[SPLIT_INPUT_NAME_IDX].trim(); //输入字段名
                            if (inputMap.containsKey(inputName)) { //实际输入中存在
                                String[] contentSplit = inputMap.get(inputName).toString().split(templateSplit[SPLIT_MARK_IDX].trim()); //根据 模版中的拆分符号 对内容进行拆分
                                Integer position = Integer.valueOf(templateSplit[SPLIT_VALUE_IDX].trim()); //获取内容拆分数组的位置
                                if (null == contentSplit || contentSplit.length - 1 < position) {
                                    return; //跳过
                                }

                                setField(requestBean, es.getKey(), contentSplit[Integer.valueOf(templateSplit[SPLIT_VALUE_IDX].trim())]); //根据 模版中的索引位置 获取对应内容
                            }
                        });
                    }
                } else if (value instanceof String) {
                    String[] values = ((String) value).split(","); //输入内容，idx:0=输入字段名，1=默认值
                    String fieldName = values[0]; //模版：输入字段名
                    Object content = null; //输入内容
                    if (inputMap.containsKey(fieldName)) { //根据 模版名称 获取输入参数
                        content = inputMap.get(fieldName);
                    }
                    if (values.length > 1 && null == content) { //设置默认值
                        content = values[1];
                    }
                    setField(requestBean, key, content); //通过反射为 bean set内容
                }
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        });
    }

    /**
     * 生成responseBean
     *
     * @param standardMap
     * @param returnValue
     * @param inputMap
     */
    public void initStandardListMap(Map<String, Object> standardMap, Object returnValue, Map<String, Object> inputMap) {
        try {
            StandardizationBean standardizationBean = null;
            List dataList = null;
            if (returnValue instanceof StandardizationBean) {
                //获取所有 ResponseBean类型的 信息
                //将data中的数据转换为list
                standardizationBean = (StandardizationBean) returnValue;
                if (standardizationBean.getData() instanceof List) {
                    dataList = (List) standardizationBean.getData();
                } else if (standardizationBean.getData() instanceof Object) {
                    dataList = Arrays.asList(standardizationBean.getData());
                }
            }
            if (null == standardizationBean) return;


            Map<String, Map> dictionaryMasterMap = (Map) standardMap.remove(DICTIONARY);

            //设置最顶层模版信息
            for (Map.Entry<String, Object> standardMapEntry : standardMap.entrySet()) {
                String key = standardMapEntry.getKey();
                Object value = standardMapEntry.getValue();
                if (value instanceof String) {
                    Object returnV = getField(standardizationBean, "_", (String) value);
                    if (!CollectionUtils.isEmpty(dictionaryMasterMap)) {
                        Map<String, String> fieldDictionaryMap = dictionaryMasterMap.get(key);
                        if (!CollectionUtils.isEmpty(fieldDictionaryMap) && fieldDictionaryMap.containsKey(returnV)) {
                            returnV = fieldDictionaryMap.get(returnV);
                        }
                    }
                    standardMapEntry.setValue(returnV); //多字段 使用 _ 拼接
                }
            }

            if (standardMap.containsKey(INPUT_RESET)) {
                Map<String, Object> resultInputMap = (Map) standardMap.remove(INPUT_RESET); //响应中的 输入信息映射关系
                for (Map.Entry<String, Object> entry : resultInputMap.entrySet()) {
                    String dataValue = inputMap.containsKey(entry.getValue()) ? inputMap.get(entry.getValue()).toString() : null;
                    resultInputMap.put(entry.getKey(), dataValue);
                }
                standardMap.putAll(resultInputMap);
            }

            if (standardMap.containsKey(LIST)) {
                Map<String, Object> fieldMap = (Map) standardMap.get(LIST);

                //获取节点属性后 移除
                Map<String, String> elementAttr = (Map) fieldMap.remove(ELEMENT_ATTR);
                Map<String, Map> dictionaryMap = (Map) fieldMap.remove(DICTIONARY);

                Map<String, Map<String, Object>> contentMaps = new LinkedHashMap();
                if (!CollectionUtils.isEmpty(dataList)) {
                    AtomicInteger i = new AtomicInteger(1);
                    for (Object data : dataList) {
                        Map<String, Object> contentMap = new HashMap<>();
                        for (Map.Entry<String, Object> entry : fieldMap.entrySet()) {
                            String columnName = entry.getValue().toString();
                            if (StringUtils.isBlank(columnName)) continue;
                            Object dataValue = getField(data, "_", columnName.split(","));
                            if (StringUtils.isEmpty(dataValue.toString())) {
                                dataValue = getField(standardizationBean, "_", columnName.split(","));
                                if (StringUtils.isEmpty(dataValue.toString())) continue;
                            }

                            //转换字典码值
                            if (null != dictionaryMap && dictionaryMap.containsKey(entry.getKey())) {
                                Map<Object, String> fieldDictionaryMap = dictionaryMap.get(entry.getKey());
                                if (!CollectionUtils.isEmpty(fieldDictionaryMap) && fieldDictionaryMap.containsKey(dataValue)) {
                                    dataValue = fieldDictionaryMap.get(dataValue);
                                } else {
                                    dataValue = null;
                                }
                            }
                            contentMap.put(entry.getKey(), dataValue);
                        }

                        StringBuffer listKey = new StringBuffer();
                        if (null != elementAttr && !elementAttr.isEmpty()) {
                            listKey.append(elementAttr.get("name")).append(" ").append(elementAttr.get("attr")).append("=");
                        }
                        listKey.append(i.getAndIncrement());
                        contentMaps.put(listKey.toString(), contentMap);
                    }
                }
                fieldMap.clear();
                fieldMap.putAll(contentMaps);
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * @param object
     * @param fieldNames 字段名称
     * @param split      多字段 分割
     * @return
     */
    public Object getField(Object object, String split, String... fieldNames) {
        StringBuffer r = new StringBuffer();
        AtomicInteger i = new AtomicInteger(1);
        for (String fieldName : fieldNames) {
            Object value = setField(object, fieldName.trim(), null);
            if (null == value) continue;
            r.append(value);
            if (!(i.getAndIncrement() == fieldNames.length)) {
                r.append(split);
            }

        }
        return r.toString();
    }

    /**
     * @param object
     * @param fieldName 字段名称
     * @param value     null = 获取object中的get， !null 设置set
     * @return
     */
    public Object setField(Object object, String fieldName, Object value) {
        Class<?> clazz = object.getClass();
        for (; clazz != Object.class; clazz = clazz.getSuperclass()) {
            try {
                if (fieldName.contains(".")) {
                    String[] splitFiledName = fieldName.split("\\.");
                    return setField(setField(object, splitFiledName[0], value), splitFiledName[1], value);
                }
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);

                PropertyDescriptor pd = new PropertyDescriptor(field.getName(), clazz);

                if (null == value) {
                    Method getMethod = pd.getReadMethod();
                    return getMethod.invoke(object);
                } else {
                    Method setMethod = pd.getWriteMethod();

                    for (Class setMethodClass : setMethod.getParameterTypes()) {
                        String v = null != value ? value.toString().trim() : null;
                        if (StringUtils.isNotEmpty(v)) {
                            String fieldType = setMethodClass.getName();
                            if ("java.lang.Integer".equals(fieldType)) {
                                setMethod.invoke(object, Integer.valueOf(v));
                            } else if ("java.lang.Long".equals(fieldType) || "long".equals(fieldType)) {
                                setMethod.invoke(object, Long.valueOf(v));
                            } else if ("java.lang.Double".equals(fieldType)) {
                                setMethod.invoke(object, Double.valueOf(v));
                            } else {
                                setMethod.invoke(object, v);
                            }
                        } else {
                            setMethod.invoke(object, null);
                        }
                    }
                }
            } catch (Exception e) {
                //e.printStackTrace();
            }
        }
        return null;
    }


    //RETURN ------------

    protected ServletServerHttpRequest createInputMessage(NativeWebRequest webRequest) {
        HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
        return new ServletServerHttpRequest(servletRequest);
    }

    protected ServletServerHttpResponse createOutputMessage(NativeWebRequest webRequest) {
        HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
        return new ServletServerHttpResponse(response);
    }

    protected Class<?> getReturnValueType(Object value, MethodParameter returnType) {
        return (value != null ? value.getClass() : returnType.getParameterType());
    }

    protected Type getGenericType(MethodParameter returnType) {
        if (HttpEntity.class.isAssignableFrom(returnType.getParameterType())) {
            return ResolvableType.forType(returnType.getGenericParameterType()).getGeneric(0).getType();
        } else {
            return returnType.getGenericParameterType();
        }
    }

}