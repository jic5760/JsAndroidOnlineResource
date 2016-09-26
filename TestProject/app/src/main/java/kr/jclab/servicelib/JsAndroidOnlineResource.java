package kr.jclab.servicelib;

import android.content.Context;
import android.os.Build;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;
import android.view.View;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import kr.jclab.javautils.JsByteBuilder;
import kr.jclab.javautils.JsHttpRequest;

/*
 * JsAndroidOnlineResource.java
 *
 * Created: 2016-09-21.
 * Author: 이지찬 / Jichan Lee ( jic5760@naver.com / ablog.jc-lab.net )
 * License: MIT License
 */
public class JsAndroidOnlineResource {
    private static JsAndroidOnlineResource m_staticthis = null;

    /* ========== static consts ========== */
    private static final String TAG = "JAOR";
    private static final String INFOFILENAME = "info.json";

    public interface ReadOnlineResourceListener {
        void onReadOnlineResource(Object userobj, String resName, String content);
    }

    /* ========== member variables ========== */
    private boolean m_isDebug = false;

    private Context m_context;

    private MyWorkThread m_workThread = null;

    private File m_cacheDir;
    private String m_waitMsg = "Please wait...";
    private String m_serverUrl;

    private Map<String, DataFileInfo> m_dataFiles;

    private Object m_current_dataLock = null;
    private Map<String, String> m_current_dataMap = null;

    private Map<Object, OnlineResourceObject> m_onlineResObjs;

    private String m_device_langcode;
    private String m_current_langcode;

    public static JsAndroidOnlineResource create(Context context, boolean isDebug) {
        JsAndroidOnlineResource obj = new JsAndroidOnlineResource(context, isDebug);
        m_staticthis = obj;
        return obj;
    }

    public static JsAndroidOnlineResource create(Context context) {
        return create(context, false);
    }

    public JsAndroidOnlineResource(Context context) {
        this(context, false);
    }

    public JsAndroidOnlineResource(Context context, boolean isDebug) {
        Locale curlocale;

        m_context = context;
        m_isDebug = isDebug;
        m_cacheDir = context.getCacheDir();
        m_dataFiles = new TreeMap<String, DataFileInfo>(String.CASE_INSENSITIVE_ORDER);
        m_onlineResObjs = new HashMap<Object, OnlineResourceObject>();
        m_current_dataLock = new Object();

        m_workThread = new MyWorkThread();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            curlocale = context.getResources().getConfiguration().getLocales().get(0);
        } else {
            curlocale = context.getResources().getConfiguration().locale;
        }

        m_device_langcode = curlocale.getLanguage();
        m_current_langcode = m_device_langcode;
    }

    @Override
    public void finalize() throws Throwable {
        m_workThread.reqStop();
        super.finalize();
    }

    public void setServerUrl(String serverUrl) {
        m_serverUrl = new String(serverUrl);
    }

    public void setServerUrl(int serverUrlResId) {
        m_serverUrl = new String(m_context.getResources().getString(serverUrlResId));
    }

    public void start() {
        m_workThread.start();
    }

    public void changeLanguage(String langcode) {
        m_device_langcode = langcode;
        m_workThread.addWork(MyWorkThread.WORKID_LOADDATAFILE);
        m_workThread.addWork(MyWorkThread.WORKID_DOWNDATAFILE);
    }

    public void setWaitMsg(String waitMsg) {
        m_waitMsg = waitMsg;
    }

    public static void removeORObj(Object obj)
    {
        synchronized (m_staticthis.m_onlineResObjs) {
            m_staticthis.m_onlineResObjs.remove(obj);
        }
    }

    public static void addORView(View view, String resName) {
        addORView(view, resName, false);
    }

    public static void addORView(View view, String resName, boolean setWaitMsgText)
    {
        OnlineResourceObject ctx = new OnlineResourceObject();
        boolean bflag = false;
        String resContent = null;

        ctx.view = view;
        ctx.userobj = null;
        ctx.listener = null;
        ctx.resName = resName;

        do {
            synchronized (m_staticthis.m_current_dataLock) {
                if (m_staticthis.m_current_dataMap == null)
                    break;

                if(m_staticthis.m_current_dataMap.containsKey(resName)) {
                    resContent = m_staticthis.m_current_dataMap.get("resName");
                    bflag = true;
                }
            }
        } while(false);
        if(bflag) {
            view_setText(view, resContent);
        }else{
            if (setWaitMsgText && (m_staticthis.m_waitMsg != null))
                view_setText(view, m_staticthis.m_waitMsg);
            else
                view_setText(view, m_staticthis.app_getResString(resName));
        }
        synchronized (m_staticthis.m_onlineResObjs) {
            m_staticthis.m_onlineResObjs.put(view, ctx);
        }
    }

    public static void addORListener(Object userobj, ReadOnlineResourceListener listener, String resName)
    {
        addORListener(userobj, listener, resName, false);
    }

    public static void addORListener(Object userobj, ReadOnlineResourceListener listener, String resName, boolean setWaitMsgText)
    {
        OnlineResourceObject ctx = new OnlineResourceObject();
        boolean bflag = false;
        String resContent = null;

        ctx.view = null;
        ctx.userobj = userobj;
        ctx.listener = listener;
        ctx.resName = resName;

        do {
            synchronized (m_staticthis.m_current_dataLock) {
                if (m_staticthis.m_current_dataMap == null)
                    break;

                if(m_staticthis.m_current_dataMap.containsKey(resName)) {
                    resContent = m_staticthis.m_current_dataMap.get("resName");
                    bflag = true;
                }
            }
        } while(false);
        if(bflag) {
            listener.onReadOnlineResource(userobj, resName, resContent);
        }else{
            if (setWaitMsgText && (m_staticthis.m_waitMsg != null))
                listener.onReadOnlineResource(userobj, resName, m_staticthis.m_waitMsg);
            else
                listener.onReadOnlineResource(userobj, resName, m_staticthis.app_getResString(resName));
        }

        if(setWaitMsgText && (m_staticthis.m_waitMsg != null)) listener.onReadOnlineResource(userobj, resName, m_staticthis.m_waitMsg);
        else listener.onReadOnlineResource(userobj, resName, m_staticthis.app_getResString(resName));
        synchronized (m_staticthis.m_onlineResObjs) {
            if(userobj == null)
                m_staticthis.m_onlineResObjs.put(listener, ctx);
            else
                m_staticthis.m_onlineResObjs.put(userobj, ctx);
        }
    }

    public static boolean containsResName(String resname) {
        if(resname == null)
            return false;

        synchronized (m_staticthis.m_current_dataLock) {
            if (m_staticthis.m_current_dataMap == null)
                return false;

            return m_staticthis.m_current_dataMap.containsKey(resname);
        }
    }

    public static String getResString(String resname) {

        if(resname == null)
            return null;

        synchronized (m_staticthis.m_current_dataLock) {
            if(m_staticthis.m_current_dataMap == null)
                return null;

            return m_staticthis.m_current_dataMap.get(resname);
        }
    }

    public static void asyncRefreshAll() {
        m_staticthis.m_workThread.addWork(MyWorkThread.WORKID_REFRESHALL);
    }

    public static void syncRefreshAll() {
        m_staticthis.m_workThread.refreshAll();
    }

    private static boolean view_setText(View obj, String text) {
        Class cls = obj.getClass();
        Class[] methodParamClass = new Class[] {CharSequence.class};
        Object[] methodParamObject = new Object[] {text};
        try {
            Method method = cls.getMethod("setText", methodParamClass);
            method.invoke(obj, methodParamObject);
        } catch (NoSuchMethodException e) {
            if(m_staticthis.m_isDebug)
                e.printStackTrace();
            return false;
        } catch (InvocationTargetException e) {
            if(m_staticthis.m_isDebug)
                e.printStackTrace();
            return false;
        } catch (IllegalAccessException e) {
            if(m_staticthis.m_isDebug)
                e.printStackTrace();
            return false;
        }
        return true;
    }

    private String app_getResString(String name) {
        int resid = m_context.getResources().getIdentifier(name, "string", m_context.getPackageName());
        if(resid == 0)
            return null;
        else
            return m_context.getResources().getString(resid);
    }

    private String[] langcodeToArr(String langcode) {
        int i;
        ArrayList<String> list = new ArrayList<String>();
        String[] tokens = langcode.split("-");
        String[] arr;

        list.add(langcode);
        if(tokens.length >= 2)
            list.add(tokens[0]);

        arr = new String[list.size()];
        i = 0;
        for(Iterator<String> iter = list.iterator(); iter.hasNext(); i++) {
            String item = iter.next();
            arr[i] = item;
        }

        return arr;
    }

    private class MyWorkThread extends Thread {
        private static final String TAG = "JAOR:WorkThread";

        public static final int WORKID_LOADINFOFILE = 1;
        public static final int WORKID_DOWNDATAFILE = 2;
        public static final int WORKID_LOADDATAFILE = 3;
        public static final int WORKID_REFRESHALL = 4;

        private boolean m_run = true;
        private BlockingQueue<Integer> m_workQueue = new LinkedBlockingQueue<Integer>();

        public MyWorkThread() {
            super();
            addWork(WORKID_LOADINFOFILE);
            addWork(WORKID_LOADDATAFILE);
            addWork(WORKID_DOWNDATAFILE);
        }

        @Override
        public void run() {
            boolean loaded_data = false;
            String loaded_langcode = null;

            while(m_run) {
                int workQueueItem = -1;

                try {
                    if (!m_workQueue.isEmpty())
                        workQueueItem = m_workQueue.take();
                } catch (InterruptedException e_int) {
                    if(!m_run)
                        break;
                }

                switch (workQueueItem) {
                    case WORKID_LOADINFOFILE: {
                        boolean loaded = loadInfoFile();

                        if(m_isDebug)
                            Log.d(TAG, "loadInfoFile result: " + loaded);
                    }
                    break;
                    case WORKID_DOWNDATAFILE: {
                        int loaded = 0;
                        String[] langcodearr = langcodeToArr(m_device_langcode);
                        String langcode = null;

                        for(int i = 0; i< langcodearr.length; i++) {
                            langcode = langcodearr[i];
                            loaded = downDataFile(langcode);

                            if(m_isDebug)
                                Log.d(TAG, "downDataFile(langcode:" + langcode + ") loaded: " + loaded);

                            if(loaded >= 1)
                                break;
                        }

                        if(loaded >= 1) {
                            if(loaded_data == false || !langcode.equals(loaded_langcode) || loaded == 1)
                                addWork(WORKID_LOADDATAFILE);
                        }
                    }
                    break;
                    case WORKID_LOADDATAFILE: {
                        boolean loaded = false;
                        String[] langcodearr = langcodeToArr(m_device_langcode);
                        String langcode = null;

                        for(int i = 0; i< langcodearr.length; i++) {
                            langcode = langcodearr[i];
                            loaded = loadDataFile(langcode);

                            if(m_isDebug)
                                Log.d(TAG, "loadDataFile(langcode:" + langcode + ") loaded: " + loaded);

                            if(loaded)
                                break;
                        }

                        if(loaded) {
                            loaded_langcode = langcode;
                            loaded_data = true;
                            refreshAll();
                        }
                    }
                    break;
                    case WORKID_REFRESHALL: {
                        refreshAll();

                        if(m_isDebug)
                            Log.d(TAG, "refreshAll");
                    }
                    break;
                }

                try {
                    if (m_workQueue.isEmpty())
                        Thread.sleep(600000); // 10 minute
                } catch (InterruptedException e_int) {
                    if(!m_run)
                        break;
                }
            }
        }

        public void reqStop() {
            m_run = false;
            interrupt();
        }

        public void addWork(int workid) {
            m_workQueue.add(workid);
            interrupt();
        }

        private String getDataFilename(String langcode) {
            StringBuilder sbfilename = new StringBuilder();

            sbfilename.append("data_");
            sbfilename.append(langcode);
            sbfilename.append(".xml");

            return sbfilename.toString();
        }

        private boolean loadInfoFile() {
            File file = new File(m_cacheDir, INFOFILENAME);
            FileReader fileReader = null;
            JsonReader jsonReader = null;

            if(!file.exists()) {
                return false;
            }

            try {
                fileReader = new FileReader(file);
                jsonReader = new JsonReader(fileReader);
                jsonReader.beginArray();
                while(jsonReader.hasNext()) {
                    DataFileInfo dataFileInfo = new DataFileInfo();
                    jsonReader.beginObject();
                    while(jsonReader.hasNext()) {
                        String name = jsonReader.nextName();
                        if(name.compareToIgnoreCase("langcode") == 0) {
                            dataFileInfo.langcode = jsonReader.nextString();
                        } else if(name.compareToIgnoreCase("last_modified_ts") == 0) {
                            dataFileInfo.last_modified_ts = jsonReader.nextLong();
                        } else if(name.compareToIgnoreCase("last_modified_etag") == 0) {
                            dataFileInfo.last_modified_etag = jsonReader.nextString();
                        } else {
                            jsonReader.skipValue();
                        }
                        if(dataFileInfo.langcode != null && dataFileInfo.last_modified_ts != 0 && dataFileInfo.last_modified_etag != null) {
                            dataFileInfo.file = new File(m_cacheDir, getDataFilename(dataFileInfo.langcode));
                            if(dataFileInfo.file.exists())
                                m_dataFiles.put(dataFileInfo.langcode, dataFileInfo);
                        }
                    }
                    jsonReader.endObject();
                }
                jsonReader.endArray();
            } catch (FileNotFoundException e) {
                if(m_isDebug)
                    e.printStackTrace();
                return false;
            } catch (IOException e) {
                if(m_isDebug)
                    e.printStackTrace();
                return false;
            } finally {
                if(jsonReader != null) {
                    try { jsonReader.close(); } catch(IOException tiex) { }
                }else if(fileReader != null) {
                    try { fileReader.close(); } catch(IOException tiex) { }
                }
            }

            return true;
        }

        private boolean saveInfoFile() {
            File file = new File(m_cacheDir, INFOFILENAME);
            FileWriter fileWriter = null;
            JsonWriter jsonWriter = null;

            try {
                fileWriter = new FileWriter(file, false);
                jsonWriter = new JsonWriter(fileWriter);
                jsonWriter.beginArray();
                for(Iterator<String> iter = m_dataFiles.keySet().iterator(); iter.hasNext(); ) {
                    String langcode = iter.next();
                    DataFileInfo dataFileInfo = m_dataFiles.get(langcode);
                    jsonWriter.beginObject();
                    jsonWriter.name("langcode");
                    jsonWriter.value(langcode);
                    jsonWriter.name("last_modified_ts");
                    jsonWriter.value(dataFileInfo.last_modified_ts);
                    jsonWriter.name("last_modified_etag");
                    jsonWriter.value(dataFileInfo.last_modified_etag);
                    jsonWriter.endObject();
                }
                jsonWriter.endArray();
            } catch (FileNotFoundException e) {
                if(m_isDebug)
                    e.printStackTrace();
                return false;
            } catch (IOException e) {
                if(m_isDebug)
                    e.printStackTrace();
                return false;
            } finally {
                if(jsonWriter != null) {
                    try { jsonWriter.close(); } catch(IOException tiex) { }
                }else if(fileWriter != null) {
                    try { fileWriter.close(); } catch(IOException tiex) { }
                }
            }

            return true;
        }

        private int downDataFile(String langcode) {
            SimpleDateFormat httpsdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");

            String filename = getDataFilename(langcode);
            JsHttpRequest request = new JsHttpRequest();

            DataFileInfo dataFileInfo = m_dataFiles.get(langcode);
            boolean insertedInfoData = false;

            int httprescode = 0;
            int retval = 0;

            Map<String, List<String>> reqHead;
            Map<String, List<String>> resHead;
            JsByteBuilder resData;

            try {
                request.openUrl(m_serverUrl + filename, "GET");
            } catch (IOException e) {
                if(m_isDebug)
                    e.printStackTrace();
                return -1;
            } catch (JsHttpRequest.ProtocolNotSupportedException e) {
                if(m_isDebug)
                    e.printStackTrace();
                return -1;
            } catch (JsHttpRequest.MethodNotSupportedException e) {
                if(m_isDebug)
                    e.printStackTrace();
                return -1;
            }

            reqHead = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
            resHead = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);

            if(dataFileInfo != null) {
                Date tmpdate = new Date();
                List<String> tmplst;

                tmpdate.setTime(dataFileInfo.last_modified_ts);

                tmplst = new ArrayList<String>();
                tmplst.add(httpsdf.format(tmpdate));
                reqHead.put("If-Modified-Since", tmplst);

                tmplst = new ArrayList<String>();
                tmplst.add(dataFileInfo.last_modified_etag);
                reqHead.put("If-None-Match", tmplst);
            }else{
                dataFileInfo = new DataFileInfo();
                dataFileInfo.file = new File(m_cacheDir, filename);
                dataFileInfo.langcode = new String(langcode);
                insertedInfoData = true;
            }

            resData = new JsByteBuilder(8192);
            try {
                httprescode = request.request(reqHead, null, resHead, resData);
            } catch (IOException e) {
                return 0;
            }

            switch (httprescode) {
                case 200: {
                    boolean bflag = false;
                    long last_modtime = 0;
                    String last_etag = null;

                    FileOutputStream fos = null;

                    do {
                        List<String> tmplst;
                        String tmpstr;
                        Date tmpdate;
                        long tmplng;

                        tmplst = resHead.get("Last-Modified");
                        if(tmplst == null && tmplst.size() <= 0)
                            break;
                        tmpstr = tmplst.get(0);
                        if (tmpstr == null)
                            break;

                        try {
                            tmpdate = httpsdf.parse(tmpstr);
                            tmplng = tmpdate.getTime();
                        } catch (ParseException e) {
                            break;
                        }

                        last_modtime = tmplng;

                        tmplst = resHead.get("Etag");
                        if(tmplst == null && tmplst.size() <= 0)
                            break;
                        tmpstr = tmplst.get(0);
                        if (tmpstr == null)
                            break;

                        last_etag = new String(tmpstr);

                        bflag = true;
                    } while (false);

                    if(bflag) {
                        dataFileInfo.last_modified_ts = last_modtime;
                        dataFileInfo.last_modified_etag = last_etag;
                        insertedInfoData = true;
                    }

                    try {
                        fos = new FileOutputStream(dataFileInfo.file, false);
                        fos.write(resData.getArray());
                        fos.close();

                        if(insertedInfoData) {
                            m_dataFiles.put(dataFileInfo.langcode, dataFileInfo);
                            saveInfoFile();
                        }

                        retval = 1;
                    } catch (IOException e) {
                        if (m_isDebug)
                            e.printStackTrace();
                        break;
                    } finally {
                        if (fos != null) {
                            try { fos.close(); } catch (IOException tioe) { }
                            fos = null;
                        }
                    }
                }
                break;
                case 304:
                    retval = 2;
                    break;
            }

            return retval;
        }

        private boolean loadDataFile(String langcode) {
            File file = new File(m_cacheDir, getDataFilename(langcode));
            FileInputStream fis = null;
            Document doc;

            if(!file.exists())
                return false;

            try {
                int i;
                NodeList node_resources;
                NodeList node_strings;
                fis = new FileInputStream(file);
                doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(fis);

                node_resources = doc.getElementsByTagName("resources");
                if(node_resources == null)
                    return false;
                if(node_resources.getLength() <= 0)
                    return false;

                node_strings = node_resources.item(0).getChildNodes();

                synchronized (m_current_dataLock) {
                    m_current_dataMap = new HashMap<String, String>();
                    for (i = 0; i < node_strings.getLength(); i++) {
                        Node node = node_strings.item(i);
                        if ("string".compareToIgnoreCase(node.getNodeName()) == 0) {
                            Node node_name = node.getAttributes().getNamedItem("name");
                            if(node_name != null)
                                m_current_dataMap.put(node_name.getNodeValue(), node.getTextContent());
                        }
                    }
                    m_current_langcode = langcode;
                }

            }catch(IOException e){
                if(m_isDebug)
                    e.printStackTrace();
                return false;
            } catch (SAXException e) {
                if(m_isDebug)
                    e.printStackTrace();
                return false;
            } catch (ParserConfigurationException e) {
                if(m_isDebug)
                    e.printStackTrace();
                return false;
            }finally {
                if(fis != null) {
                    try{ fis.close(); }catch(IOException tioe){ }
                }
            }
            return true;
        }

        public boolean refreshAll() {
            if(m_isDebug)
                Log.d(TAG, "refreshAll");

            synchronized (m_current_dataLock) {
                if(m_current_dataMap == null)
                    return false;

                for (Iterator<Object> iter = m_onlineResObjs.keySet().iterator(); iter.hasNext(); ) {
                    Object key = iter.next();
                    OnlineResourceObject ctx = m_onlineResObjs.get(key);
                    String content;
                    if(m_current_dataMap.containsKey(ctx.resName)) {
                        content = getResString(ctx.resName);
                        if (ctx.listener != null)
                            ctx.listener.onReadOnlineResource(ctx.userobj, ctx.resName, content);
                        if (ctx.view != null)
                            view_setText(ctx.view, content);
                    }
                }
            }

            return true;
        }
    }

    private class DataFileInfo {
        public File file = null;
        public String langcode = null;
        public long last_modified_ts = 0;
        public String last_modified_etag = null;
    }

    private static class OnlineResourceObject {
        public View view = null;
        public Object userobj = null;
        public ReadOnlineResourceListener listener = null;
        public String resName = null;
    }
}
