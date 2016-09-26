package kr.jclab.test.jsandroidonlineresourcetest;

import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

import kr.jclab.servicelib.JsAndroidOnlineResource;

public class MainActivity extends AppCompatActivity {
    JsAndroidOnlineResource m_jaor = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        m_jaor = JsAndroidOnlineResource.create(getApplicationContext(), BuildConfig.DEBUG);
        m_jaor.setServerUrl(R.string.app_jaor_serverurl);
        m_jaor.start();

        m_jaor.addORView(findViewById(R.id.tvtest1), "test1");

        m_jaor.addORListener(null, new JsAndroidOnlineResource.ReadOnlineResourceListener() {
            @Override
            public void onReadOnlineResource(final Object userobj, final  String resName, final String content) {
                if(Thread.currentThread() != Looper.getMainLooper().getThread()) {
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onReadOnlineResource(userobj, resName, content);
                        }
                    });
                    return ;
                }

                Toast.makeText(MainActivity.this, resName + "=" + content, Toast.LENGTH_LONG).show();
            }
        }, "test2");
    }
}
