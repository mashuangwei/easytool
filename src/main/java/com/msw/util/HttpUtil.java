package com.msw.util;

//import net.sf.json.JSONObject;
//import org.apache.commons.lang.StringUtils;

import com.alibaba.fastjson.JSONObject;
import com.msw.entity.ResultResponse;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by mashuangwei on 2018/11/10.
 */
public class HttpUtil {
    public HttpUtil() {
    }

    private static RequestConfig requestConfig;
    private static final int MAX_TIMEOUT = 20000;   // 20秒超时
    private static final String ENCODING = "UTF-8";

    static {
        // 设置连接池
        PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager();
        // 设置连接池大小
        connMgr.setMaxTotal(100);
        connMgr.setDefaultMaxPerRoute(connMgr.getMaxTotal());
        requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(MAX_TIMEOUT)   // 设置从连接池获取连接实例的超时
                .setConnectTimeout(MAX_TIMEOUT)             // 设置连接超时
                .setSocketTimeout(MAX_TIMEOUT)              // 设置读取超时
                .setCookieSpec(CookieSpecs.STANDARD_STRICT) // Cookie策略
                .setRedirectsEnabled(true)
                .setRelativeRedirectsAllowed(true)
                .build();
    }

    /**
     * 产生通用的HttpClient对象
     */
    public static CloseableHttpClient getHttpClient() {
        final CloseableHttpClient httpclient;
        httpclient = HttpClients.custom()
                .build();
        return httpclient;
    }

    /**
     * 创建HTTPS的HttpClinet对象
     */
    public static CloseableHttpClient getSSLHttpClient() {
        SSLContext sslContext = null;
        //忽略证书校验
        try {
            sslContext = new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
                public boolean isTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                    return true;
                }
            }).build();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        }
        HostnameVerifier hostnameVerifier = SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", sslSocketFactory)
                .build();
        PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        return HttpClients.custom()
                .setConnectionManager(connMgr)
                .setSSLHostnameVerifier(hostnameVerifier)
                .build();
    }

    public static ResultResponse doDelete(String url, CloseableHttpClient httpclient, String encoding) {
        HttpDelete http = new HttpDelete(url);
        CloseableHttpResponse closeableHttpResponse = null;
        try {
            closeableHttpResponse = httpclient.execute(http);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return executeResponse(closeableHttpResponse, encoding);
    }

    /**
     * @param url:接口请求URL
     * @param jsonParam：key为参数名，value参数值
     * @param filesOnbject：              key为上传的文件参数名，value为文件的名字
     * @param httpclient
     * @return
     */
    public static ResultResponse doPostUpfile(String url, JSONObject jsonParam, JSONObject filesOnbject,
                                                     CloseableHttpClient httpclient, String encoding) {
        HttpPut http = new HttpPut(url);
        CloseableHttpResponse closeableHttpResponse = null;
        MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
        Iterator iterator = null;

        if (null != jsonParam) {
            iterator = jsonParam.keySet().iterator();
            while (iterator.hasNext()) {
                String key = (String) iterator.next();
                String value = jsonParam.getString(key);
                multipartEntityBuilder.addPart(key, new StringBody(value, ContentType.create("text/plain", Consts.UTF_8)));
            }
        }
        if (null != filesOnbject) {
            iterator = filesOnbject.keySet().iterator();
            while (iterator.hasNext()) {
                String key = (String) iterator.next();
                String value = filesOnbject.getString(key);
                String localFile = System.getProperty("user.dir") + "/src/main/resources/picture/" + value;
                // 把文件转换成流对象FileBody
                FileBody bin = new FileBody(new File(localFile));
                multipartEntityBuilder.addPart(key, bin);
            }
        }

        HttpEntity reqEntity = multipartEntityBuilder.build();
        http.setEntity(reqEntity);
        try {
            closeableHttpResponse = httpclient.execute(http);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return executeResponse(closeableHttpResponse, encoding);
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
    public static ResultResponse doGet(String url, Map<String, String> headers, Map<String, String> params, String encoding, CloseableHttpClient httpclient) {
        url = url + (null == params ? "" : assemblyParameter(params));
        HttpGet httpGet = new HttpGet(url);
        if (null != headers) {
            httpGet.setHeaders(assemblyHeader(headers));
        }
        CloseableHttpResponse closeableHttpResponse = null;
        try {
            closeableHttpResponse = httpclient.execute(httpGet);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return executeResponse(closeableHttpResponse, encoding);
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
    public static ResultResponse doJsonPost(String url, Map<String, String> headers, String params, String encoding,CloseableHttpClient httpclient) {
        HttpPost httpPost = new HttpPost(url);
        StringEntity stringEntity = new StringEntity(params, encoding);
        stringEntity.setContentEncoding(new BasicHeader(HTTP.CONTENT_ENCODING, encoding));
        httpPost.setEntity(stringEntity);
        if (null != headers) {
            httpPost.setHeaders(assemblyHeader(headers));
        }
        CloseableHttpResponse closeableHttpResponse = null;
        try {
            closeableHttpResponse = httpclient.execute(httpPost);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return executeResponse(closeableHttpResponse, encoding);
    }

    public static ResultResponse doFormPost(String url, Map<String, String> headers, Map<String, String> param, String encoding, CloseableHttpClient httpclient) {
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
            closeableHttpResponse = httpclient.execute(httpPost);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return executeResponse(closeableHttpResponse, encoding);
    }

    public static ResultResponse executeResponse(CloseableHttpResponse closeableHttpResponse, String encoding) {
        ResultResponse resultResponse = new ResultResponse();
        try {
            if (closeableHttpResponse == null || closeableHttpResponse.getStatusLine() == null) {
                return resultResponse;
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
    public static Header[] assemblyHeader(Map<String, String> headers) {
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
    public static String assemblyParameter(Map<String, String> parameters) {
        String para = "?";
        for (String str : parameters.keySet()) {
            para += str + "=" + parameters.get(str) + "&";
        }
        return para.substring(0, para.length() - 1);
    }

}
