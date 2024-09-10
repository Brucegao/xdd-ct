package com.xdd.ct.framework;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiTester {
    private static final Logger log = LoggerFactory.getLogger(ApiTester.class);
    Map<String, String> envValueMap = new HashMap<>();
    String TestEnvName = "";

    List<String> stepList = new ArrayList<>();
    Map<String, String> stepIdMap = new HashMap<>();
    Map<String, String> stepResponseMap = new HashMap<>();
    Map<String, Boolean> stepResultMap = new HashMap<>();
    Map<String, Integer> stepCodeMap = new HashMap<>();

    private void consumeResponse(String stepId, org.apache.http.HttpResponse response) throws Exception {
        String result = null;
        int responseStatusCode = response.getStatusLine().getStatusCode();
        log.info("doAction StatusCode=" + responseStatusCode);
        if (response.getStatusLine().getStatusCode() < 200 || response.getStatusLine().getStatusCode() >= 400) {
//            throw new IOException("Got bad response, error code = " + response.getStatusLine().getStatusCode());
            log.error("Got bad response, error code = " + response.getStatusLine().getStatusCode());
        }

        org.apache.http.HttpEntity entity = response.getEntity();
        if (entity != null) {
            result = org.apache.http.util.EntityUtils.toString(entity);
            log.info("doAction=" + result);
            org.apache.http.util.EntityUtils.consume(entity);
        }

        stepCodeMap.put(stepId, responseStatusCode);
        stepResponseMap.put(stepId, result);
    }

    private Boolean doAction(String stepId, HttpRequestBase httpRequestBase, JSONObject body, Map<String, String> headers,
                             Boolean bodyEvaluationDisabled) throws Exception {
        Boolean success = true;

        int timeOut = 3;
        final org.apache.http.params.HttpParams httpParams = new org.apache.http.params.BasicHttpParams();
        org.apache.http.params.HttpConnectionParams.setConnectionTimeout(httpParams, 1000 * timeOut);
        org.apache.http.params.HttpConnectionParams.setSoTimeout(httpParams, 1000 * timeOut);

        org.apache.http.client.HttpClient client = new org.apache.http.impl.client.DefaultHttpClient(httpParams);
//        org.apache.http.client.methods.HttpPost httpPost = new org.apache.http.client.methods.HttpPost(url);
//        httpRequestBase.setHeader(HttpHeaders.ACCEPT, "application/json");
        // add request header
        if (headers != null) {
            Set<String> headerKeys = headers.keySet();
            for (String headerKey : headerKeys) {
                httpRequestBase.setHeader(headerKey, headers.get(headerKey));
            }
        }

        if (httpRequestBase instanceof HttpEntityEnclosingRequestBase) {
            HttpEntityEnclosingRequestBase httpEntityEnclosingRequestBase = (HttpEntityEnclosingRequestBase) httpRequestBase;
            if ("Text".equals(body.getString("bodyType"))) {
                String textBody = body.getString("textBody");
                if (!bodyEvaluationDisabled) {
                    textBody = convertEnvs(textBody);
                }
                org.apache.http.entity.StringEntity postEntity = new org.apache.http.entity.StringEntity(textBody, "application/json", "utf-8");
                httpEntityEnclosingRequestBase.setEntity(postEntity);
            } else if ("Form".equals(body.getString("bodyType"))) {
                List<NameValuePair> formParams = new ArrayList<>();
                JSONArray items =
                        (body.getJSONObject("formBody") != null) ? body.getJSONObject("formBody").getJSONArray("items") : null;
                if (items != null) {
                    for (int i = 0; i < items.size(); i++) {
                        JSONObject item = (JSONObject) items.get(i);
                        if (item.getBoolean("enabled") != null && item.getBoolean("enabled")) {
                            formParams.add(new BasicNameValuePair(item.getString("name"), item.getString("value")));
                        }
                    }
                }
                httpEntityEnclosingRequestBase.setEntity(new UrlEncodedFormEntity(formParams, StandardCharsets.UTF_8));
            }
        }

        org.apache.http.HttpResponse response = client.execute(httpRequestBase);

        consumeResponse(stepId, response);

        return success;
    }

    public Boolean doGet(String stepId, String url, Map<String, String> headers) throws Exception {
        org.apache.http.client.methods.HttpGet httpGet = new org.apache.http.client.methods.HttpGet(url);
        return doAction(stepId, httpGet, null, headers, true);
    }

    public Boolean doPost(String stepId, String url, JSONObject body, Map<String, String> headers, Boolean bodyEvaluationDisabled) throws Exception {
        org.apache.http.client.methods.HttpPost httpPost = new org.apache.http.client.methods.HttpPost(url);
        return doAction(stepId, httpPost, body, headers, bodyEvaluationDisabled);
    }

    public Boolean doPut(String stepId, String url, JSONObject body, Map<String, String> headers, Boolean bodyEvaluationDisabled) throws Exception {
        org.apache.http.client.methods.HttpPut httpPut = new org.apache.http.client.methods.HttpPut(url);
        return doAction(stepId, httpPut, body, headers, bodyEvaluationDisabled);
    }

    public Boolean doDelete(String stepId, String url, JSONObject body, Map<String, String> headers, Boolean bodyEvaluationDisabled) throws Exception {
        org.apache.http.client.methods.HttpDelete httpDelete = new org.apache.http.client.methods.HttpDelete(url);
        return doAction(stepId, httpDelete, body, headers, bodyEvaluationDisabled);
    }

    public void readEnvs(JSONArray environments, String envName, String localEnv) {
        TestEnvName = envName;

        for (int i = 0; i < environments.size(); i++) {
            JSONObject environment = (JSONObject) environments.get(i);
            if (envName.equals(environment.getString("name"))) {
                JSONObject variables = environment.getJSONObject("variables");
                Set<String> valKeys = variables.keySet();
                for (String valKey : valKeys) {
                    JSONObject variable = variables.getJSONObject(valKey);
                    if (variable.getBoolean("enabled")) {
                        envValueMap.put(variable.getString("name"), variable.getString("value"));
                    }
                }
            }
        }

        log.info("==================Env Value=============");
        log.info(envValueMap.toString());
        log.info("========================================");

        if (localEnv != null) {
            log.info("=============Local Env Value============");
            Map<String, String> localEnvMap = convertTextToMap(localEnv);
            log.info(localEnvMap.toString());
            log.info("========================================");

            envValueMap.putAll(localEnvMap);
            log.info("================New Env Value===========");
            log.info(envValueMap.toString());
            log.info("========================================");
        }
    }

    public String convertEnvs(String text) {
        // convert ${"xxx"}
        Pattern pattern = Pattern.compile("\\$\\{\"(.*?)\"\\}");
        Matcher matcher = pattern.matcher(text);

        // 进行替换
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            if (envValueMap.containsKey(placeholder)) {
                matcher.appendReplacement(sb, envValueMap.get(placeholder));
            } else {
                log.warn("警告: 找不到占位符 {\"" + placeholder + "\"} 的替换值。");
            }
        }
        matcher.appendTail(sb);
        text = sb.toString();

        // convert getEntityById("xxx")
        pattern = Pattern.compile("\\$\\{getEntityById\\(\"(.*?)\"\\)");
        matcher = pattern.matcher(text);

        // 进行替换
        sb = new StringBuffer();
        while (matcher.find()) {
            String placeholder = matcher.group(1);
//            log.info(placeholder);
            text = convertEntityDynamicValue(text, placeholder);
        }

        // convert jsonPath("$.rows[0].companyId")

        return text;
    }

    private String convertEntityDynamicValue(String text, String key) {
        String findTemp = "${getEntityById(\"" + key + "\")";
        String toConvertTemp = findTemp + ".\"response\".\"body\".jsonPath(\"";
        if (text.indexOf(toConvertTemp) > -1) {
            if (stepResponseMap.get(key) != null) {
                JSONObject stepResponseJson = JSONObject.parseObject(stepResponseMap.get(key));
                int pos1 = text.indexOf(toConvertTemp) + toConvertTemp.length();
                int pos2 = text.indexOf("\"", pos1);
                String jsonPath = text.substring(pos1, pos2);
                if (stepResponseJson.getByPath(jsonPath) != null) {
                    String pathValue = stepResponseJson.getByPath(jsonPath).toString();

                    text = text.replace(toConvertTemp + jsonPath + "\")}", pathValue);
                }
            }
        }

        return text;
    }

    public Boolean assertResult(JSONObject entity, String stepId) {
        Boolean success = true;

        JSONArray assertions = entity.getJSONArray("assertions");
        if (assertions != null) {
            for (int i = 0; i < assertions.size(); i++) {
                Boolean assertRst = false;

                JSONObject assertion = (JSONObject) assertions.get(i);
                String comparison = assertion.getString("comparison");
                String subject = assertion.getString("subject");
                String path = assertion.getString("path");
                String value = assertion.getString("value");
                Object subjectVal = null;

                if ("ResponseStatus".equals(subject)) {
                    if ("code".equals(path)) {
                        subjectVal = stepCodeMap.get(stepId);
                    }
                } else if ("ResponseJsonBody".equals(subject)) {
                    JSONObject stepResponseJson = JSONObject.parseObject(stepResponseMap.get(stepId));
                    if (stepResponseJson != null) subjectVal = stepResponseJson.getByPath(path);
                }

//            log.info(subjectVal.toString());
                if ("Equals".equals(comparison)) {
                    assertRst = subjectVal != null && subjectVal.toString().equals(value);
                } else if ("Greater".equals(comparison)) {
                    assertRst = subjectVal != null && Integer.parseInt(subjectVal.toString()) > Integer.parseInt(value);
                } else if ("Exists".equals(comparison)) {
                    assertRst = subjectVal != null;
                }

                if (!assertRst) success = false;
                log.info("Assert " + stepId + " " + i + ":" + assertRst);
            }
        }

        return success;
    }

    private String getStepUrl(JSONObject entity) throws Exception {
        String url = "";

        JSONObject uri = entity.getJSONObject("uri");

        JSONObject scheme = uri.getJSONObject("scheme");
        if (scheme != null) {
            url += scheme.getString("name") + "//";
        }
        String host = uri.getString("host");
        if (host != null) {
            url += host;
        }
        String path = uri.getString("path");
        if (path != null) {
            url += path;
        }

        JSONObject query = uri.getJSONObject("query");
        if (query != null) {
            JSONArray items = query.getJSONArray("items");
            String delimiter = query.getString("delimiter");
            if (items != null && items.size() > 0) {
                url += "?";
                for (int i = 0; i < items.size(); i++) {
                    JSONObject item = (JSONObject) items.get(i);
                    if (item.getBoolean("enabled") != null && item.getBoolean("enabled")) {
                        url += URLEncoder.encode(item.getString("name"), "UTF-8") + "="
                                + URLEncoder.encode(item.getString("value"), "UTF-8") + delimiter;
                    }
                }
            }

            if (url.endsWith(delimiter)) {
                url = url.substring(0, url.length() - 1);
            }
            if (url.endsWith("?")) {
                url = url.substring(0, url.length() - 1);
            }
        }

        url = convertEnvs(url);

        return url;
    }

    private JSONObject readBody(JSONObject entity) {
        JSONObject body = entity.getJSONObject("body");

        return body;
    }

    private Map<String, String> readHeader(JSONObject entity) {
        Map<String, String> headersMap = new HashMap<>();
        JSONArray headers = entity.getJSONArray("headers");
        for (int i = 0; i < headers.size(); i++) {
            JSONObject header = (JSONObject) headers.get(i);
            if (header.getBoolean("enabled") != null && header.getBoolean("enabled")) {
                String headerVal = header.getString("value");
                headerVal = convertEnvs(headerVal);
                headersMap.put(header.getString("name"), headerVal);
            }
        }

        return headersMap;
    }

    public Boolean runStep(JSONObject entity, String stepName, String stepId) throws Exception {
        Boolean success = true;

        Boolean bodyEvaluationDisabled = Boolean.TRUE.equals(entity.getBoolean("bodyEvaluationDisabled"));
        JSONObject method = entity.getJSONObject("method");
        String methodName = method.getString("name");

        String stepUrl = getStepUrl(entity);
        Map<String, String> headers = readHeader(entity);
        log.info(stepUrl);
//        log.info(headers.toString());
        if ("GET".equals(methodName)) {
            doGet(stepId, stepUrl, headers);
        } else if ("POST".equals(methodName)) {
            JSONObject body = readBody(entity);
            doPost(stepId, stepUrl, body, headers, bodyEvaluationDisabled);
        } else if ("PUT".equals(methodName)) {
            JSONObject body = readBody(entity);
            doPut(stepId, stepUrl, body, headers, bodyEvaluationDisabled);
        } else if ("DELETE".equals(methodName)) {
            JSONObject body = readBody(entity);
            doDelete(stepId, stepUrl, body, headers, bodyEvaluationDisabled);
        }

        // assert
        success = assertResult(entity, stepId);

        return success;
    }

    public Boolean runProject(JSONObject entity, String steps) {
        Boolean projectResult = true;

        JSONObject entityPrj = entity.getJSONObject("entity");
        String prjName = entityPrj.getString("name");

        log.info("========================================");
        log.info("==================ToRun Project: " + prjName);

        JSONArray children = entity.getJSONArray("children");
        for (int i = 0; i < children.size(); i++) {
            JSONObject child = ((JSONObject) children.get(i)).getJSONObject("entity");
            String stepName = child.getString("name");
            String stepId = child.getString("id");

            stepList.add(stepId);
            stepIdMap.put(stepId, stepName);
        }

        for (int i = 0; i < children.size(); i++) {
            JSONObject child = ((JSONObject) children.get(i)).getJSONObject("entity");
            String stepName = child.getString("name");
            String stepId = child.getString("id");

//            log.info(stepName);

            if (steps != null) {
                if (("," + steps + ",").indexOf("," + prjName + ":" + stepName + ",") == -1) {
                    continue;
                }
            }

            Boolean result = false;
            try {
                result = runStep(child, stepName, stepId);
            } catch (Exception e) {
                e.printStackTrace();
                log.error(e.toString());
//                break;
            } finally {
                stepResultMap.put(stepId, result);
            }

            if (!result) {
                projectResult = false;
                break;
            }

//            if (i >= 3) break;
        }

        log.info("========================================");
        log.info("==================ToRun Project Result: " + prjName);
        log.info("==================ToRun EnvName: " + TestEnvName);
        for (String stepId : stepList) {
            String stepName = stepIdMap.get(stepId);
            log.info(stepName + " ----------- " + (stepResultMap.get(stepId) != null ? (stepResultMap.get(stepId) ? "success" : "fail") : "JO"));
        }
        log.info("========================================");
        log.info("========================================");

        return projectResult;
    }

    public Boolean loadAndRunAll(JSONArray entities, String projects, String steps) {
        Boolean sumResult = true;
        for (int i = 0; i < entities.size(); i++) {
            JSONObject entity = (JSONObject) entities.get(i);

            if (projects != null) {
                JSONObject entityPrj = entity.getJSONObject("entity");
                String prjName = entityPrj.getString("name");
                if (("," + projects + ",").indexOf("," + prjName + ",") == -1) {
                    break;
                }
            }

            Boolean projectResult = runProject(entity, steps);
            if (!projectResult) sumResult = false;
        }
        return sumResult;
    }

    /**
     * 格式：a=b&b=c&d=e
     *
     * @param text
     * @return
     */
    public static Map<String, String> convertTextToMap(String text) {
        Map<String, String> map = new HashMap<>();

        // 先按 & 分割字符串
        String[] pairs = text.split("&");

        for (String pair : pairs) {
            // 再按 = 分割每个键值对
            String[] keyValue = pair.split("=");

            if (keyValue.length == 2) {
                String key = keyValue[0];
                String value = keyValue[1];
                map.put(key, value);
            }
        }

        return map;
    }

    public static void main(String[] args) {
//        String configFile = "/Users/brucegao/Downloads/mt_regtest.json";
//        String envName = "MT Test";

//        String configFile = "/Users/brucegao/Downloads/regtest2.json";
//        String envName = "MT Local";

        String configFile = args[0];
        String envName = args[1];
        String projects = null;
        String envs = null;
        String steps = null;

        if (args.length > 2 && StringUtils.isNotEmpty(args[2])) {
            projects = args[2];
        }
        if (args.length > 3 && StringUtils.isNotEmpty(args[3])) {
            envs = args[3];
        }
        if (args.length > 4 && StringUtils.isNotEmpty(args[4])) {
            steps = args[4];
        }

        log.info("configFile=" + configFile);
        log.info("envName=" + envName);
        log.info("projects=" + projects);
        log.info("envs=" + envs);
        log.info("steps=" + steps);

        Boolean sumResult = true;

        try {
            String configJson = FileUtils.readFileToString(new File(configFile), "UTF-8");

            JSONObject configJsonObj = JSONObject.parseObject(configJson);

            JSONArray entities = configJsonObj.getJSONArray("entities");
            JSONArray environments = configJsonObj.getJSONArray("environments");

            ApiTester tester = new ApiTester();
            tester.readEnvs(environments, envName, envs);
            sumResult = tester.loadAndRunAll(entities, projects, steps);
        } catch (Exception e) {
//            throw new RuntimeException(e);
            e.printStackTrace();
            sumResult = false;
        }

        if (!sumResult) {
            System.exit(1);
        }
    }


}
