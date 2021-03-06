package com.appdevper.mediaplayer.activity;

import java.util.ArrayList;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.appdevper.mediaplayer.R;
import com.appdevper.mediaplayer.adater.DeviceListAdapter;
import com.appdevper.mediaplayer.app.AppMediaPlayer;
import com.appdevper.mediaplayer.app.MusicService;
import com.appdevper.mediaplayer.app.ShareData;
import com.appdevper.mediaplayer.util.DeviceItem;

public class RenderListActivity extends BaseActivity {

    private ArrayList<DeviceItem> deviceItem;
    private DeviceListAdapter deviceList;
    private Toolbar mToolbar;
    private ListView listView;

    private MusicService mService;

    private boolean mBound;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            MusicService.LocalBinder binder = (MusicService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;

        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist);
        initializeToolbar();

        deviceItem = ShareData.getRenList();
        deviceList = new DeviceListAdapter(this, deviceItem);

        listView = (ListView) findViewById(R.id.listView);
        listView.setAdapter(deviceList);
        listView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ShareData.setRenderDevice(deviceList.getItem(position));
                AppMediaPlayer.setService();
                if (!ShareData.getRenderDevice().getIslocal())
                    mService.connectUpnp(ShareData.getRenderDevice().toString());
                else
                    mService.disconnectUpnp();
                finish();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    protected void onMediaControllerConnected() {

    }

    @Override
    protected void initializeToolbar() {
        super.initializeToolbar();
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        if (mToolbar == null) {
            throw new IllegalStateException("Layout is required to include a Toolbar with id " + "'toolbar'");
        }

        //mToolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
    }
}
