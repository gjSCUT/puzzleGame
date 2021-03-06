package com.example.administrator.puzzleGame.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.example.administrator.puzzleGame.R;
import com.example.administrator.puzzleGame.util.WifiUtil;

/**
 * Created by HUI on 2016-04-03.
 */
public class ConnectModeActivity extends Activity {

    Button wifi;
    Button bluetooth;
    private WifiUtil temp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        wifi = (Button) findViewById(R.id.btn_wifi);
        bluetooth = (Button) findViewById(R.id.btn_bluetooth);

        wifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ConnectModeActivity.this, GameRoomActivity.class);
                startActivity(intent);
            }
        });

        bluetooth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Indo 跳转到另外一个蓝牙页面
                Intent intent = new Intent(ConnectModeActivity.this, GameActivity.class);
                startActivity(intent);

            }
        });

    }

}
