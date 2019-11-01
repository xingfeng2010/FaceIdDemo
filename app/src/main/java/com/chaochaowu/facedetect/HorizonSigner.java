package com.chaochaowu.facedetect;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.SortedMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HorizonSigner {
    public static final String HTTP_METHOD_GET = "GET";
    public static final String HTTP_METHOD_PUT = "PUT";
    public static final String HTTP_METHOD_POST = "POST";
    public static final String HTTP_METHOD_DELETE = "DELETE";

    private String ak;
    private String sk;

    private static final String DEFAULT_ENCODING = "UTF-8";
    private static final Charset UTF8 = Charset.forName(DEFAULT_ENCODING);

    public HorizonSigner(String ak, String sk) {
        this.ak = ak;
        this.sk = sk;
    }

    private String sha256Hex(String signingKey, String stringToSign) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(UTF8), "HmacSHA256"));
            return bytes2Hex(mac.doFinal(stringToSign.getBytes(UTF8)));
        } catch (Exception e) {
            System.out.println("Fail to generate the signature");
            return null;
        }
    }

    private String getCanonicalQueryString(final SortedMap<String, String> map, final String encoding) {
        final StringBuffer builder = new StringBuffer();
        final String[] concat = { "" };
        for (Map.Entry<String, String> entry : map.entrySet()) {
            try {
                builder.append(concat[0]).append(URLEncoder.encode(entry.getKey(), encoding)).append("=");
                if (entry.getValue() != null) {
                    builder.append(URLEncoder.encode(entry.getValue(), encoding));
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            concat[0] = "&";

        }
        return builder.toString();
    }

    private String getCanonicalHeaders(final SortedMap<String, String> map, final String encoding) {
        final StringBuffer builder = new StringBuffer();
        final String[] concat = { "" };
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getKey().toLowerCase().equals("host") || entry.getKey().toLowerCase().equals("content-type")) {
                try {
                    builder.append(concat[0]).append(URLEncoder.encode(entry.getKey().toLowerCase(), encoding))
                            .append(":");
                    if (entry.getValue() != null) {
                        builder.append(URLEncoder.encode(entry.getValue(), encoding));
                    }
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                concat[0] = "\n";
            }
        }
        return builder.toString();
    }

    private String normalizePath(String path) {
        String temp = "";
        try {
            temp = URLEncoder.encode(path, DEFAULT_ENCODING);
        } catch (UnsupportedEncodingException e) {
        }
        return temp.replace("%2F", "/");
    }

    private String getCanonicalURIPath(String path) {
        if (path == null) {
            return "/";
        } else if (path.startsWith("/")) {
            return normalizePath(path);
        } else {
            return normalizePath(path);
        }
    }

    public String Sign(String httpMethod, String uri, SortedMap<String, String> params,
            SortedMap<String, String> headers) throws Exception {
        if (ak == null || ak.isEmpty()) {
            throw new Exception("api key is null");
        }
        if (sk == null || sk.isEmpty()) {
            throw new Exception("secret key is null");
        }
        if (uri == null || uri.isEmpty()){
            throw new Exception("uri is null");
        }
        if(!headers.containsKey("host")){
            throw new Exception("host is not in header");
        }
        if(httpMethod == null || httpMethod.isEmpty()){
            throw new Exception("httpMethod is null");
        }
        // 1.生成sign key
        // 1.1.生成auth_string，格式为：horizon-auth-v1/{accessKeyId}/{timestamp}
        long currentTime = System.currentTimeMillis() / 1000;
        String signKeyInfo = "horizon-auth-v1/" + ak + "/" + Long.toString(currentTime);
//        LogUtils.d(TAG, "sign100: " + signKeyInfo);

        // 1.2.使用auth_string加上SK，用SHA-256生成sign key
        String signKey = sha256Hex(sk, signKeyInfo);
//        LogUtils.d(TAG, "sign101: " +signKey);

        // 2.生成规范化uri
        // String canonicalURI = requestParam.getRelativeUrl();
        // 此处java源代码只是简单返回了原本的url
        String canonicalUri = getCanonicalURIPath(uri);
//        LogUtils.d(TAG, "sign102: " +canonicalUri);

        // 3.生成规范化query string
        String canonicalQuerystring = getCanonicalQueryString(params, DEFAULT_ENCODING);
//        LogUtils.d(TAG, "sign103: " +canonicalQuerystring);

        // 4.生成规范化header
        String canonicalHeaders = getCanonicalHeaders(headers, DEFAULT_ENCODING);
//        LogUtils.d(TAG, "sign104: " +canonicalHeaders);

        StringBuffer canonicalRequest = new StringBuffer();
        canonicalRequest.append(httpMethod).append("\n").append(canonicalUri).append("\n")
                .append(canonicalQuerystring).append("\n").append(canonicalHeaders);
        String canonicalRequestStr = canonicalRequest.toString();
//        LogUtils.d(TAG, "sign105: " + canonicalRequestStr);

        String signature = sha256Hex(signKey, canonicalRequestStr);
//        LogUtils.d(TAG, "sign106: " + signature);

        String signString = signKeyInfo + "/" + signature;
        return signString;
    }

    private String bytes2Hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for(byte b : bytes) {
            builder.append(String.format("%02x", new Integer(b & 0xff)));
        }
        return builder.toString();
    }

}