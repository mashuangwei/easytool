package com.msw.util;

import com.msw.entity.ResultResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author mashuangwei
 * @date 2018-11-06 16:13
 * @description: http工具类
 */
@Slf4j
public class HttpClientsUtil {
    private static final int DEFAULT_POOL_MAX_TOTAL = 200;      // 连接池的最大连接数
    private static final int DEFAULT_POOL_MAX_PER_ROUTE = 200; //连接池按route配置的最大连接数

    private static final int DEFAULT_CONNECT_TIMEOUT = 500; // tcp connect的超时时间
    private static final int DEFAULT_CONNECT_REQUEST_TIMEOUT = 500; // 从连接池获取连接的超时时间
    private static final int DEFAULT_SOCKET_TIMEOUT = 2000; // tcp io的读写超时时间

    private PoolingHttpClientConnectionManager gcm;

    private CloseableHttpClient closeableHttpClient;

    private IdleConnectionMonitorThread idleThread;

    public HttpClientsUtil() {
        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", SSLConnectionSocketFactory.getSocketFactory())
                .build();

        this.gcm = new PoolingHttpClientConnectionManager(registry);
        this.gcm.setMaxTotal(DEFAULT_POOL_MAX_TOTAL);
        this.gcm.setDefaultMaxPerRoute(DEFAULT_POOL_MAX_PER_ROUTE);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(DEFAULT_CONNECT_TIMEOUT)                     // 设置连接超时
                .setSocketTimeout(DEFAULT_SOCKET_TIMEOUT)                       // 设置读取超时
                .setConnectionRequestTimeout(DEFAULT_CONNECT_REQUEST_TIMEOUT)    // 设置从连接池获取连接实例的超时
                .build();

        HttpClientBuilder httpClientBuilder = HttpClients.custom();
        closeableHttpClient = httpClientBuilder
                .setConnectionManager(this.gcm)
                .setDefaultRequestConfig(requestConfig)
                .build();

        idleThread = new IdleConnectionMonitorThread(this.gcm);
        idleThread.start();

    }

    public void shutdown() {
        idleThread.shutdown();
    }

    // 监控有异常的链接
    private class IdleConnectionMonitorThread extends Thread {

        private final HttpClientConnectionManager connMgr;
        private volatile boolean exitFlag = false;

        public IdleConnectionMonitorThread(HttpClientConnectionManager connMgr) {
            this.connMgr = connMgr;
            setDaemon(true);
        }

        @Override
        public void run() {
            while (!this.exitFlag) {
                synchronized (this) {
                    try {
                        this.wait(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                // 关闭失效的连接
                connMgr.closeExpiredConnections();
                // 可选的, 关闭30秒内不活动的连接
                connMgr.closeIdleConnections(30, TimeUnit.SECONDS);
            }
        }

        public void shutdown() {
            this.exitFlag = true;
            synchronized (this) {
                notify();
            }
        }

    }

    /**
     * 发送Get请求
     *
     * @param url      请求的地址
     * @param headers  请求的头部信息
     * @param params   请求的参数
     * @param encoding 字符编码
     * @return ResultResponse
     */
    public  ResultResponse doGet(String url, Map<String, String> headers, Map<String, String> params, String encoding) {
        url = url + (null == params ? "" : assemblyParameter(params));
        HttpGet httpGet = new HttpGet(url);
        if (null != headers) {
            httpGet.setHeaders(assemblyHeader(headers));
        }
        CloseableHttpResponse closeableHttpResponse = null;
        try {
            closeableHttpResponse = closeableHttpClient.execute(httpGet);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return executeResponse(closeableHttpResponse, encoding);
    }

    /**
     *  上传文件，参数可以带也可以不带。
     * @param url 接口地址
     * @param headers 请求头
     * @param params 上传文件时携带的参数
     * @param path 文件的绝对路径，比如c:/test.jpeg
     */
    public void doUploadFilePost(String url, Map<String, String> headers, Map<String, String> params, String path){
        HttpPost httpPost = new HttpPost(url);
        MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
        File file = new File(path);
        multipartEntityBuilder.addBinaryBody("file",file);
       for (String key: params.keySet()) {
           multipartEntityBuilder.addTextBody(key, params.get(key));
       }
        HttpEntity httpEntity = multipartEntityBuilder.build();
        httpPost.setEntity(httpEntity);

        if (null != headers) {
            httpPost.setHeaders(assemblyHeader(headers));
        }
        CloseableHttpResponse closeableHttpResponse = null;
        try {
            closeableHttpResponse = closeableHttpClient.execute(httpPost);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 发送Post请求
     *
     * @param url      请求的地址
     * @param headers  请求的头部信息
     * @param params   请求的参数
     * @param encoding 字符编码
     * @return ResultResponse
     */
    public ResultResponse doJsonPost(String url, Map<String, String> headers, String params, String encoding) {
        HttpPost httpPost = new HttpPost(url);
        StringEntity stringEntity = new StringEntity(params, encoding);
        stringEntity.setContentEncoding(new BasicHeader(HTTP.CONTENT_ENCODING, encoding));
        httpPost.setEntity(stringEntity);
        if (null != headers) {
            httpPost.setHeaders(assemblyHeader(headers));
        }
        CloseableHttpResponse closeableHttpResponse = null;
        try {
            closeableHttpResponse = closeableHttpClient.execute(httpPost);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return executeResponse(closeableHttpResponse, encoding);
    }

    public ResultResponse doFormPost(String url, Map<String, String> headers, Map<String, String> param, String encoding) {
        CloseableHttpResponse closeableHttpResponse = null;

        HttpPost httpPost = new HttpPost(url);
        if (param != null) {
            List<NameValuePair> paramList = new ArrayList<>();
            for (String key : param.keySet()) {
                paramList.add(new BasicNameValuePair(key, param.get(key)));
            }
            if (null != headers) {
                httpPost.setHeaders(assemblyHeader(headers));
            }
            UrlEncodedFormEntity entity = null;
            try {
                entity = new UrlEncodedFormEntity(paramList);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            httpPost.setEntity(entity);
        }
        try {
            closeableHttpResponse = closeableHttpClient.execute(httpPost);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return executeResponse(closeableHttpResponse, encoding);
    }

    public ResultResponse executeResponse(CloseableHttpResponse closeableHttpResponse, String encoding) {
        ResultResponse resultResponse = new ResultResponse();
        try {
            if (closeableHttpResponse == null || closeableHttpResponse.getStatusLine() == null) {
                return null;
            }
            resultResponse.setHeaders(closeableHttpResponse.getAllHeaders());
            resultResponse.setStatusCode(closeableHttpResponse.getStatusLine().getStatusCode());
            HttpEntity httpEntity = closeableHttpResponse.getEntity();
            if (httpEntity != null) {
                resultResponse.setResponseContent(EntityUtils.toString(httpEntity, encoding));
                EntityUtils.consume(httpEntity);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (closeableHttpResponse != null) {
                    closeableHttpResponse.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return resultResponse;
    }

    /**
     * 组装头部信息
     *
     * @param headers
     * @return
     */
    public Header[] assemblyHeader(Map<String, String> headers) {
        Header[] allHeader = new BasicHeader[headers.size()];
        int i = 0;
        for (String str : headers.keySet()) {
            allHeader[i] = new BasicHeader(str, headers.get(str));
            i++;
        }
        return allHeader;
    }

    /**
     * 组装Cookie
     *
     * @param cookies
     * @return
     */
    public String assemblyCookie(List<Cookie> cookies) {
        StringBuffer sbu = new StringBuffer();
        for (Cookie cookie : cookies) {
            sbu.append(cookie.getName()).append("=").append(cookie.getValue()).append(";");
        }
        if (sbu.length() > 0)
            sbu.deleteCharAt(sbu.length() - 1);
        return sbu.toString();
    }

    /**
     * 组装请求参数
     *
     * @param parameters
     * @return
     */
    public String assemblyParameter(Map<String, String> parameters) {
        String para = "?";
        for (String str : parameters.keySet()) {
            para += str + "=" + parameters.get(str) + "&";
        }
        return para.substring(0, para.length() - 1);
    }

}
