package com.creativeboy.audioandvideocapture;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.display.DisplayManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.creativeboy.audioandvideocapture.encoder.AudioEncoder;
import com.creativeboy.audioandvideocapture.encoder.AudioRecorder;
import com.creativeboy.audioandvideocapture.encoder.ScreenRecorder;
import com.creativeboy.audioandvideocapture.encoder.VideoRecorder;
import com.creativeboy.audioandvideocapture.libsuperuser.Shell;

import java.io.DataOutputStream;
import java.io.File;


public class MainActivity extends ActionBarActivity {
    private Button button;
    private Button stop;
    private Button recordVideo;
    private AudioRecorder audioRecorder;
    private AudioEncoder audioEncoder;
    private File file;
    private VideoRecorder videoRecorder;
    private ScreenRecorder screenRecorder;
    private static final String INSTALL_SCRIPT = "mount -o rw,remount /system\n"
            + "cat %s > /system/priv-app/GetRecorderIo.apk.tmp\n"
            + "chmod 644 /system/priv-app/GetRecorderIo.apk.tmp\n"
            + "pm uninstall %s\n"
            + "mv /system/priv-app/GetRecorderIo.apk.tmp /system/priv-app//GetRecorderIo.apk\n"
            + "pm install -r /system/priv-app/GetRecorderIo.apk\n"
            + "sleep 5\n" + "reboot";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        button = (Button) findViewById(R.id.record);
        stop = (Button) findViewById(R.id.stop);
        recordVideo = (Button) findViewById(R.id.recordVideo);
        File path = new File(Environment.getExternalStorageDirectory(), "AudioRecord");
        path.mkdirs();
        System.out.print("path" + path);


        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                file = new File(Environment.getExternalStorageDirectory() + "/AudioRecord/", System.currentTimeMillis() + ".m4a");
                audioRecorder = AudioRecorder.getInstance(file);
                audioEncoder = new AudioEncoder();
                audioRecorder.setAudioEncoder(audioEncoder);
                audioRecorder.startAudioRecording();
            }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                audioRecorder.stopAudioRecording();
                audioEncoder.stop();
            }
        });

        recordVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("videoRecorder--", videoRecorder + "");
                if (screenRecorder != null) {
                    screenRecorder.quit();
                    screenRecorder = null;
                    audioRecorder.stopAudioRecording();
                    audioEncoder.stop();
                    recordVideo.setText("开始录屏");
                } else {

                    if (MainActivity.this.getPackageCodePath().contains("system")) {
                        begin();
                    } else {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("警告")
                                .setMessage("首次使用此功能需要重启手机，点击确定手机将自动重启")
                                .setCancelable(false)
                                .setPositiveButton("确定",
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog,
                                                                int which) {
                                                install();
                                            }
                                        })
                                .setNegativeButton("取消",
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog,
                                                                int which) {
                                                dialog.dismiss();
                                                //begin();
                                            }


                                        }).show();
                    }
                }
            }
        });
    }

    private void begin() {
        DisplayManager mDisplayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        int width = getScreenWidth(MainActivity.this);
        int height = getScreenHeight(MainActivity.this);
        file = new File(Environment.getExternalStorageDirectory() + "/AudioRecord/", System.currentTimeMillis() + ".mp4");
        //videoRecorder = new VideoRecorder(width,height,file.getAbsolutePath(),mDisplayManager);
        //videoRecorder.start();
        screenRecorder = new ScreenRecorder(width,height,file.getAbsolutePath(),mDisplayManager);
        screenRecorder.start();
        recordVideo.setText("停止录屏");
        audioRecorder = AudioRecorder.getInstance(file);
        audioEncoder = new AudioEncoder();
        audioRecorder.setAudioEncoder(audioEncoder);
        audioRecorder.startAudioRecording();
        moveTaskToBack(true);
    }


    public boolean isRoot()
    {
        boolean isRoot = false;

        try
        {
            isRoot = !((!new File("/system/bin/su").exists()) && (!new File(
                    "/system/xbin/su").exists()));
        } catch (Exception e)
        {
            e.printStackTrace();
        }
        return isRoot;
    }

    public Boolean getRootPermission(String pkgCodePath)
    {
        Process process = null;
        DataOutputStream os = null;
        String cmd = "chmod 777" + pkgCodePath;
        try
        {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(cmd + "\n");
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
        } catch (Exception e)
        {
            e.printStackTrace();
            return false;
        } finally
        {
            try
            {
                if (os != null)
                {
                    os.close();
                }
                if (process != null)
                {
                    process.destroy();
                }
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        return true;
    }
    public int getScreenHeight(Context ctx)
    {
        android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        return dm.heightPixels;
    }

    public int getScreenWidth(Context ctx)
    {
        android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        return dm.widthPixels;
    }


    private void install()
    {
        new AsyncTask<Void, Void, Void>()
        {

            @Override
            protected Void doInBackground(Void... params)
            {
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        Toast.makeText(MainActivity.this,
                                "正在安装，安装完成将自动重启，请等待...", Toast.LENGTH_LONG)
                                .show();
                    }
                });
                // SharedPreferences.Editor editor = prefs.edit();
                // editor.putBoolean(KEY_SYSTEM_PRIVILEGE_PREF, true).apply();
                Shell.SU.run(String.format(INSTALL_SCRIPT, new String[] {
                        MainActivity.this.getPackageCodePath(),
                        MainActivity.this.getPackageName() }));
                return null;
            }
        }.execute();

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
