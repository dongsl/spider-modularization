package com.spider.handler;

import com.alibaba.fastjson.JSONArray;
import com.spider.annotation.StandardizationRequestBody;
import com.spider.annotation.StandardizationResponseBody;
import org.apache.commons.lang.StringUtils;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.http.HttpServletRequest;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.Map;

public class RsRqMethodHandler extends CustomerHandlerMethodArgumentResolver implements HandlerMethodReturnValueHandler {


    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(StandardizationRequestBody.class);
    }

    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        return (AnnotatedElementUtils.hasAnnotation(returnType.getContainingClass(), StandardizationResponseBody.class) ||
                returnType.hasMethodAnnotation(StandardizationResponseBody.class));
    }

    //拆分url中的  身份和接口名
    public String[] splitUrlInfo(NativeWebRequest webRequest) {
        HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);

        String servletPath = servletRequest.getServletPath();
        if (servletPath.lastIndexOf("/") + 1 == servletPath.length()) {
            servletPath = servletPath.substring(0, servletPath.length() - 1);
        }
        String methodName = servletPath.substring(servletPath.lastIndexOf("/") + 1);
        String identity = servletPath.replaceAll("/api", "").replaceAll(methodName, "").replaceAll("/", "");

        return new String[]{methodName, identity};
    }

    /**
     * 获取标准化模版
     *
     * @param webRequest
     * @param nodeName   rq或rs
     * @return
     * @throws FileNotFoundException
     */
    public Map<String, Object> getTemplate(NativeWebRequest webRequest, String nodeName) throws FileNotFoundException {
        String[] urlInfo = splitUrlInfo(webRequest);
        String methodName = urlInfo[0];
        String identity = urlInfo[1];
        return BootstrapConfigHandler.getStandardConfig(identity, methodName, nodeName);
    }


    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {


        ServletServerHttpRequest inputMessage = new ServletServerHttpRequest(webRequest.getNativeRequest(HttpServletRequest.class));


        Type targetType = parameter.getNestedGenericParameterType();
        MediaType contentType = inputMessage.getHeaders().getContentType();
        Class<?> contextClass = (parameter != null ? parameter.getContainingClass() : null);
        Class targetClass = (targetType instanceof Class ? (Class) targetType : null);

        Object requestBean = targetClass.newInstance();
        Map<String, Object> standardMap = getTemplate(webRequest, "rq");
        for (HttpMessageConverter<?> converter : messageConverters) {
            if (converter instanceof GenericHttpMessageConverter) {
                GenericHttpMessageConverter<?> genericConverter = (GenericHttpMessageConverter<?>) converter;
                if (genericConverter.canRead(targetType, contextClass, contentType)) {
                    if (inputMessage.getBody() != null) {
                        if (null != standardMap && !standardMap.isEmpty()) {
                            Map<String, Object> inputMap = (Map<String, Object>) genericConverter.read(Map.class, contextClass, inputMessage);
                            initRequestBean(standardMap, inputMap, requestBean);
                            ThreadLocalHandler.threadLocal.set(inputMap);
                        } else {
                            requestBean = genericConverter.read(targetType, contextClass, inputMessage);
                        }

                    }
                    break;
                }
            }
        }
        return requestBean;

        //return readWithMessageConverters(inputMessage, parameter, parameter.getNestedGenericParameterType(), standardMap);
    }

    @Override
    public void handleReturnValue(Object returnValue, MethodParameter returnType, ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {
        Map<String, Object> methodStandardMap = getTemplate(webRequest, "rs");
        mavContainer.setRequestHandled(true);
        ServletServerHttpRequest inputMessage = createInputMessage(webRequest);
        ServletServerHttpResponse outputMessage = createOutputMessage(webRequest);

        Class<?> valueType = getReturnValueType(returnValue, returnType);
        Type declaredType = getGenericType(returnType);
        MediaType returnMediaType = inputMessage.getHeaders().getContentType();


        if (null == methodStandardMap || methodStandardMap.isEmpty()) {
            if (returnMediaType != null) {
                returnMediaType = returnMediaType.removeQualityValue();
                for (HttpMessageConverter<?> messageConverter : messageConverters) {
                    if (messageConverter instanceof GenericHttpMessageConverter) {
                        if (((GenericHttpMessageConverter) messageConverter).canWrite(declaredType, valueType, returnMediaType)) {
                            if (returnValue != null) {
                                //addContentDispositionHeader(inputMessage, outputMessage);
                                ((GenericHttpMessageConverter) messageConverter).write(returnValue, declaredType, returnMediaType, outputMessage);
                            }
                            return;
                        }
                    }
                }
            }
        } else {
            Map<String, Object> standardMap = null;
            if (methodStandardMap.containsKey("XML")) {
                standardMap = (Map) methodStandardMap.get("XML");
                returnMediaType = MediaType.APPLICATION_XML;
            } else if (methodStandardMap.containsKey("JSON")) {
                standardMap = (Map) methodStandardMap.get("JSON");
                returnMediaType = MediaType.APPLICATION_JSON;
            }

            if (null != standardMap && !standardMap.isEmpty()) {

                initStandardListMap(standardMap, returnValue, (Map) ThreadLocalHandler.threadLocal.get()); //标准化

                //map转换为 xml或json
                String returnContent = null;
                if (returnMediaType == MediaType.APPLICATION_XML) {
                    returnContent = XmlHandler.createXmlByMap(standardMap, "ROOT");
                } else if (returnMediaType == MediaType.APPLICATION_JSON) {
                    returnContent = JSONArray.toJSONString(standardMap);
                }
                if (StringUtils.isNotEmpty(returnContent)) {
                    outputMessage.getServletResponse().addHeader(HttpHeaders.CONTENT_TYPE, returnMediaType.toString());
                    OutputStream out = outputMessage.getBody();
                    out.write(returnContent.getBytes());
                    outputMessage.getBody().flush();
                    return;
                }
            }
        }

    }




}
