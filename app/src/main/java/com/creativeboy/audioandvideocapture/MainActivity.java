package com.creativeboy.audioandvideocapture;

import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.creativeboy.audioandvideocapture.encoder.AudioEncoder;
import com.creativeboy.audioandvideocapture.encoder.AudioRecorder;

import java.io.File;


public class MainActivity extends ActionBarActivity {
    private Button button;
    private Button stop;
    private AudioRecorder audioRecorder;
    private AudioEncoder audioEncoder;
    private File file;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        button = (Button) findViewById(R.id.record);
        stop = (Button) findViewById(R.id.stop);
        File path = new File(Environment.getExternalStorageDirectory(),"AudioRecord");
        path.mkdirs();
        System.out.print("path" + path);


        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                file = new File(Environment.getExternalStorageDirectory()+"/AudioRecord/",System.currentTimeMillis()+".m4a");
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
