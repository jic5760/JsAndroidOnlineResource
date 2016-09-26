package kr.jclab.javautils;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

/*
 * JsAndroidOnlineResource.java
 *
 * Created: 2016-09-21.
 * Author: 이지찬 / Jichan Lee ( jic5760@naver.com / ablog.jc-lab.net )
 * License: MIT License
 */
public class JsHttpRequest {
    public static class ProtocolNotSupportedException extends Exception {
        private static final long serialVersionUID = 6365015332977436634L;

        public ProtocolNotSupportedException() {
            super("Protocol Not Supported!");
        }
    }

    public static class MethodNotSupportedException extends Exception {
        private static final long serialVersionUID = 2184596627201711401L;

        public MethodNotSupportedException() {
            super("Method Not Supported!");
        }
    }

    private boolean m_isDebug = false;

    private URL m_url;
    private URLConnection m_conn;
    private HttpURLConnection m_conn_http;
    private HttpsURLConnection m_conn_https;

    private String m_charset = "UTF-8";
    private String m_protocol_str;
    private int m_protocol_num;
    private int m_method_num;

    private Map<String, List<String>> m_response_header;

    public JsHttpRequest() {

    }

    public JsHttpRequest(boolean isDebug) {
        m_isDebug = isDebug;
    }

    public void setCharset(String charset) {
        m_charset = charset;
    }

    public void openUrl(
            String strURL, String method
    ) throws IOException, ProtocolNotSupportedException, MethodNotSupportedException {
        String strprotocol;

        m_url = new URL(strURL);

        strprotocol = m_url.getProtocol().toLowerCase();
        method = method.toUpperCase();

        m_protocol_str = strprotocol;

        if (method.compareTo("GET") == 0) {
            m_method_num = 1;
        } else if (method.compareTo("POST") == 0) {
            m_method_num = 2;
        } else {
            throw new MethodNotSupportedException();
        }

        if (strprotocol.compareTo("http") == 0) {
            m_protocol_num = 1;
        } else if (strprotocol.compareTo("https") == 0) {
            m_protocol_num = 2;
        } else {
            throw new ProtocolNotSupportedException();
        }

        m_conn = m_url.openConnection();
    }

    public void setCookies(List<HttpCookie> cookies) throws UnsupportedEncodingException {
        String strCookies = "";
        for (Iterator<HttpCookie> iter = cookies.iterator(); iter.hasNext();) {
            HttpCookie item = iter.next();
            System.out.println("item:" + item);
            strCookies += item.getName() + "=" + URLEncoder.encode(item.getValue(), m_charset);
            if (iter.hasNext())
                strCookies += "; ";
        }
        m_conn.addRequestProperty("Cookie", strCookies);
    }

    public void getCookies(List<HttpCookie> cookies) {
        List<String> newcookies;

        newcookies = m_response_header.get("Set-Cookie");

        List<HttpCookie> addcookies = new ArrayList<HttpCookie>();
        for (Iterator<String> iter = newcookies.iterator(); iter.hasNext();) {
            String value = iter.next();
            try {
                boolean bReplaced = false;
                List<HttpCookie> items = HttpCookie.parse(value);
                HttpCookie item = items.get(0);
                for (ListIterator<HttpCookie> iter2 = cookies.listIterator(); iter2.hasNext();) {
                    HttpCookie item2 = iter2.next();
                    if (item2.getName().equals(item.getName())) {
                        iter2.set(item);
                        bReplaced = true;
                        break;
                    }
                }
                if (!bReplaced) {
                    addcookies.add(item);
                }
            } catch (Exception e) {
                if(m_isDebug)
                    e.printStackTrace();
            }
        }
        cookies.addAll(addcookies);

        for (Iterator<HttpCookie> iter = cookies.iterator(); iter.hasNext();) {
            HttpCookie item = iter.next();
            if (item.hasExpired()) {
                iter.remove();
            }
        }
    }

    public int request(Map<String, List<String>> reqHead, byte[] reqpostdata, Map<String, List<String>> resHead, JsByteBuilder resData) throws IOException {
        int httprescode = -1;

        InputStream instream = null;
        InputStreamReader instreamreader = null;

        String encoding;

        if(reqHead != null) {
            for (Iterator<String> iter = reqHead.keySet().iterator(); iter.hasNext(); ) {
                String name = iter.next();
                for (Iterator<String> iter2 = reqHead.get(name).iterator(); iter2.hasNext(); ) {
                    String value = iter2.next();
                    m_conn.addRequestProperty(name, value);
                }
            }
        }

        if (reqpostdata != null) {
            if (m_method_num == 2) {
                OutputStream os = m_conn.getOutputStream();
                os.write(reqpostdata);
                os.flush();
                os.close();
            }else{
                return 0;
            }
        }

        // try
        if (m_protocol_num == 1) {
            m_conn_http = (HttpURLConnection)m_conn;
            httprescode = m_conn_http.getResponseCode();
        } else if (m_protocol_num == 2) {
            m_conn_http = (HttpURLConnection)m_conn;
            httprescode = m_conn_https.getResponseCode();
        }

        encoding = m_conn.getContentEncoding();
        if(encoding != null)
            m_charset = encoding;

        m_response_header = m_conn.getHeaderFields();
        if(resHead != null) {
            for (Iterator<String> iter = m_response_header.keySet().iterator(); iter.hasNext(); ) {
                String name = iter.next();
                if (name == null)
                    continue;
                resHead.put(name, m_response_header.get(name));
            }
        }

        try {
            instream = m_conn.getInputStream();
            instreamreader = new InputStreamReader(instream);
            byte[] buffer = new byte[4096];
            int readsize;

            while ((readsize = instream.read(buffer)) > 0) {
                resData.append(buffer, 0, readsize);
            }
        }catch(IOException ioe) {
            if(m_isDebug)
                ioe.printStackTrace();
        }finally {
            if(instreamreader != null) {
                try { instreamreader.close(); }catch(IOException tioe) {  }
                instreamreader = null;
                instream = null;
            }else if(instream != null) {
                try { instream.close(); }catch(IOException tioe) {  }
                instream = null;
            }
        }

        return httprescode;
    }

    public URLConnection getURLConnection() {
        return m_conn;
    }
}
