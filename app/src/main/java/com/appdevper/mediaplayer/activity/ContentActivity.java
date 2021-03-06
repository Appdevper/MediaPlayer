package com.appdevper.mediaplayer.activity;

import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.widget.Toast;

import com.appdevper.mediaplayer.R;
import com.appdevper.mediaplayer.adater.ContentItemAdapter;
import com.appdevper.mediaplayer.app.AppMediaPlayer;
import com.appdevper.mediaplayer.app.ShareData;
import com.appdevper.mediaplayer.model.MusicProvider;
import com.appdevper.mediaplayer.util.ContentActionCallback;
import com.appdevper.mediaplayer.util.ContentItem;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.support.model.container.Container;

import java.util.List;
import java.util.Stack;

public class ContentActivity extends BaseActivity {

    private ListView contentListView;
    private ContentItemAdapter contentAdapter;
    private AndroidUpnpService upnpService;
    private final static String TAG = ContentActivity.class.getSimpleName();
    private Device<?, ?, ?> cDevice;
    private Service<?, ?> cServices;
    private String type;
    private ContentItem mainContent;
    private Stack<ContentItem> stackContent;
    private Toolbar mToolbar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_content);

        initializeToolbar();
        setTitle(getIntent().getStringExtra("name"));

        contentListView = (ListView) findViewById(R.id.contentList);

        contentAdapter = new ContentItemAdapter(this);
        contentListView.setAdapter(contentAdapter);
        contentListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                selectMedia(position);
            }
        });

        contentListView.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long id) {
                ContentItem content = contentAdapter.getItem(position);
                if (!content.isContainer()) {
                    showDialog(content);
                }
                return true;
            }
        });

        upnpService = AppMediaPlayer.getUpnpService();
        cDevice = ShareData.getDevice();

        stackContent = new Stack<>();

        try {
            cServices = cDevice.findService(new UDAServiceType("ContentDirectory"));
            mainContent = new ContentItem(createRootContainer(cServices), cServices);
            upnpService.getControlPoint().execute(new ContentActionCallback(this, mainContent.getService(), mainContent.getContainer(), contentAdapter));
        } catch (Exception e) {
            e.printStackTrace();
        }

        AppMediaPlayer.setService();

    }

    private final MediaControllerCompat.Callback mMediaControllerCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            super.onMetadataChanged(metadata);
            if (metadata == null) {
                return;
            }
            Log.d(TAG, "Received metadata change to media " + metadata.getDescription().getMediaId());
            contentAdapter.notifyDataSetChanged();
        }

        @Override
        public void onPlaybackStateChanged(@NonNull PlaybackStateCompat state) {
            super.onPlaybackStateChanged(state);
            Log.d(TAG, "Received state change: " + state);

            contentAdapter.notifyDataSetChanged();
        }
    };

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_BACK:
                    return onBack();
            }
        }

        return super.dispatchKeyEvent(event);
    }

    private boolean onBack() {
        if (!stackContent.isEmpty()) {
            mainContent = stackContent.pop();
            upnpService.getControlPoint().execute(new ContentActionCallback(this, mainContent.getService(), mainContent.getContainer(), contentAdapter));
            return true;
        } else {
            setResult(200);
            onBackPressed();
            return true;
        }
    }

    protected Container createRootContainer(Service<?, ?> service) {
        Container rootContainer = new Container();
        rootContainer.setId("0");
        rootContainer.setTitle("Content Directory on " + service.getDevice().getDisplayString());
        return rootContainer;
    }

    private void selectMedia(int position) {
        ContentItem content = contentAdapter.getItem(position);
        type = content.getType();
        Log.d(TAG, content.isContainer().toString());
        if (content.isContainer()) {
            stackContent.push(mainContent);
            mainContent = content;
            upnpService.getControlPoint().execute(new ContentActionCallback(ContentActivity.this, content.getService(), content.getContainer(), contentAdapter));
        } else {
            try {
                if (ShareData.getRenderDevice().getIslocal()) {
                    switch (type) {
                        case "video":
                            getSupportMediaController().getTransportControls().stop();
                            playVideo(content);
                            break;
                        case "audio":
                            MusicProvider.getInstance().retrieveMedia(contentAdapter.getAll());
                            onMediaItemSelected(String.valueOf(content.getResourceUri().hashCode()));
                            break;
                        case "image":
                            ShareData.aContentImage = contentAdapter.getAll();
                            AppMediaPlayer.setMedia(content);
                            Intent intent = new Intent();
                            intent.putExtra("position", position);
                            intent.setClass(ContentActivity.this, ImageActivity.class);
                            startActivity(intent);
                            break;
                    }
                } else {
                    switch (type) {
                        case "video":
                            AppMediaPlayer.stopMusic();
                            AppMediaPlayer.sendRender(content);
                            break;
                        case "audio":
                            MusicProvider.getInstance().retrieveMedia(contentAdapter.getAll());
                            onMediaItemSelected(String.valueOf(content.getResourceUri().hashCode()));
                            break;
                        case "image":
                            AppMediaPlayer.sendRender(content);
                            break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

    }

    public void onMediaItemSelected(String mediaId) {
        Log.i(TAG, "onMediaItemSelected: " + mediaId);
        getSupportMediaController().getTransportControls().playFromMediaId(mediaId, null);
    }

    private void showDialog(final ContentItem content) {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);

        alertDialog.setTitle("Download file!");

        alertDialog.setMessage("You want to download this file?");

        alertDialog.setNegativeButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                download(content);
                dialog.cancel();
            }
        });

        alertDialog.setPositiveButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        alertDialog.show();
    }

    private void download(ContentItem content) {
        String url = content.getResourceUri();
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setDescription("Download file with Media player");
        request.setTitle("Download " + content.toString());
        String[] ss = url.split("\\.");
        request.allowScanningByMediaScanner();
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, content.toString() + "." + ss[ss.length - 1]);

        // get download service and enqueue file
        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        manager.enqueue(request);
    }


    @Override
    protected void onMediaControllerConnected() {
        if (getSupportMediaController() != null) {
            getSupportMediaController().registerCallback(mMediaControllerCallback);
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        mToolbar.setTitle(title);
    }

    @Override
    protected void initializeToolbar() {
        super.initializeToolbar();
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        if (mToolbar == null) {
            throw new IllegalStateException("Layout is required to include a Toolbar with id " + "'toolbar'");
        }

        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBack();
            }
        });
    }

    private void playVideo(ContentItem content) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setDataAndType(Uri.parse(content.getItem().getFirstResource().getValue()), content.getMimeType());
        startActivity(intent);
    }
}