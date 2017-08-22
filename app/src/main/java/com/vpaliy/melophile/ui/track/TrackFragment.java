package com.vpaliy.melophile.ui.track;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.view.ViewPager;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.request.target.ImageViewTarget;
import com.google.gson.reflect.TypeToken;
import com.ohoussein.playpause.PlayPauseView;
import com.vpaliy.domain.playback.QueueManager;
import com.vpaliy.melophile.App;
import com.vpaliy.melophile.R;
import com.vpaliy.melophile.playback.service.MusicPlaybackService;
import com.vpaliy.melophile.playback.PlaybackManager;
import com.vpaliy.melophile.ui.base.BaseFragment;
import com.vpaliy.melophile.ui.utils.Constants;
import com.vpaliy.melophile.ui.utils.PresentationUtils;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import jp.wasabeef.blurry.Blurry;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import butterknife.OnClick;
import javax.inject.Inject;
import android.support.annotation.Nullable;
import butterknife.BindView;

public class TrackFragment extends BaseFragment {

    private static final String TAG=TrackFragment.class.getSimpleName();

    @BindView(R.id.background)
    protected ImageView background;

    @BindView(R.id.end_time)
    protected TextView endTime;

    @BindView(R.id.start_time)
    protected TextView startTime;

    @BindView(R.id.circle)
    protected ImageView smallImage;

    @BindView(R.id.artist)
    protected TextView artist;

    @BindView(R.id.track_name)
    protected TextView trackName;

    @BindView(R.id.progressView)
    protected SeekBar progress;

    @BindView(R.id.play_pause)
    protected PlayPauseView playPause;

    @BindView(R.id.pages)
    protected TextView pages;


    private static final long PROGRESS_UPDATE_INTERNAL = 100;
    private static final long PROGRESS_UPDATE_INITIAL_INTERVAL = 10;

    private final ScheduledExecutorService executorService =
            Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> scheduledFuture;

    private PlaybackStateCompat lastState;
    private Handler handler=new Handler();

    private MediaBrowserCompat browserCompat;
    private MediaBrowserCompat.ConnectionCallback connectionCallback=new MediaBrowserCompat.ConnectionCallback(){
        @Override
        public void onConnected()  {
            super.onConnected();
            MediaSessionCompat.Token token=browserCompat.getSessionToken();
            try {
                inject();
                MediaControllerCompat mediaController =new MediaControllerCompat(getActivity(), token);
                // Save the controller
                mediaController.registerCallback(controllerCallback);
                MediaControllerCompat.setMediaController(getActivity(), mediaController);
                PlaybackStateCompat stateCompat=mediaController.getPlaybackState();
                updatePlaybackState(stateCompat);
                MediaMetadataCompat metadataCompat=mediaController.getMetadata();
                if(metadataCompat!=null){
                    updateDuration(metadataCompat);
                }
            }catch (RemoteException ex){
                ex.printStackTrace();
            }
        }
    };


    private MediaControllerCompat.Callback controllerCallback=new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            updatePlaybackState(state);
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            super.onMetadataChanged(metadata);
            if(metadata!=null) {
                updateDuration(metadata);
            }
        }
    };

    @Override
    public void onStart() {
        super.onStart();
        if(browserCompat!=null) {
            browserCompat.connect();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if(browserCompat!=null){
            browserCompat.disconnect();
        }
        MediaControllerCompat controllerCompat=MediaControllerCompat.getMediaController(getActivity());
        if(controllerCompat!=null){
            controllerCompat.unregisterCallback(controllerCallback);
        }
    }

    private void startSeekBarUpdate(){
        scheduledFuture = executorService.scheduleAtFixedRate(()-> handler.post(TrackFragment.this::updateProgress),
                PROGRESS_UPDATE_INITIAL_INTERVAL, PROGRESS_UPDATE_INTERNAL, TimeUnit.MILLISECONDS);
    }

    private void stopSeekBarUpdate(){
        lastState=null;
        if(scheduledFuture !=null) scheduledFuture.cancel(false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopSeekBarUpdate();
        executorService.shutdown();
    }

    @OnClick(R.id.play_pause)
    public void playPause(){
        lastState=null;
        MediaControllerCompat controllerCompat=MediaControllerCompat.getMediaController(getActivity());
        PlaybackStateCompat stateCompat=controllerCompat.getPlaybackState();
        if(stateCompat!=null){
            MediaControllerCompat.TransportControls controls=
                    controllerCompat.getTransportControls();
            switch (stateCompat.getState()){
                case PlaybackStateCompat.STATE_PLAYING:
                case PlaybackStateCompat.STATE_BUFFERING:
                    controls.pause();
                    break;
                case PlaybackStateCompat.STATE_NONE:
                case PlaybackStateCompat.STATE_PAUSED:
                case PlaybackStateCompat.STATE_STOPPED:
                    controls.play();
                    break;
                default:
                    Log.d(TAG, "State "+stateCompat.getState());
            }
        }
    }

    public void updatePlaybackState(PlaybackStateCompat stateCompat){
        if(stateCompat==null) return;
        lastState=stateCompat;
        switch (stateCompat.getState()){
            case PlaybackStateCompat.STATE_PLAYING:
                playPause.setVisibility(VISIBLE);
                if(playPause.isPlay()){
                    playPause.change(false,true);
                }
                startSeekBarUpdate();
                break;
            case PlaybackStateCompat.STATE_PAUSED:
                // mControllers.setVisibility(VISIBLE);
                // mLoading.setVisibility(INVISIBLE);
                playPause.setVisibility(VISIBLE);
                if(!playPause.isPlay()){
                    playPause.change(true,true);
                }
                stopSeekBarUpdate();
                break;
            case PlaybackStateCompat.STATE_NONE:
            case PlaybackStateCompat.STATE_STOPPED:
                playPause.setVisibility(VISIBLE);
                if(playPause.isPlay()){
                    playPause.change(false,true);
                }
                stopSeekBarUpdate();
                break;
            case PlaybackStateCompat.STATE_BUFFERING:
                playPause.setVisibility(INVISIBLE);
                stopSeekBarUpdate();
                break;
            default:
                Log.d(TAG, "Unhandled state "+stateCompat.getState());
        }
    }


    @Inject
    public void updateQueue(PlaybackManager manager){
        QueueManager queueManager=fetchQueue();
        if(queueManager!=null) {
            manager.setQueueManager(fetchQueue());
            manager.handleResumeRequest();
        }
    }

    private QueueManager fetchQueue(){
        if(getArguments()!=null) {
            String queueString = getArguments().getString(Constants.EXTRA_QUEUE);
            if(queueString!=null){
                return PresentationUtils.convertFromJsonString(queueString,
                        new TypeToken<QueueManager>() {}.getType());
            }
        }
        return null;
    }

    @OnClick(R.id.next)
    public void playNext(){
        MediaControllerCompat controllerCompat=MediaControllerCompat.getMediaController(getActivity());
        MediaControllerCompat.TransportControls controls=
                controllerCompat.getTransportControls();
        controls.skipToNext();
    }

    @OnClick(R.id.prev)
    public void playPrev(){
        MediaControllerCompat controllerCompat=MediaControllerCompat.getMediaController(getActivity());
        MediaControllerCompat.TransportControls controls=
                controllerCompat.getTransportControls();
        controls.skipToPrevious();
    }

    private void updateProgress() {
        if (lastState == null) return;
        long currentPosition = lastState.getPosition();
        if (lastState.getState() == PlaybackStateCompat.STATE_PLAYING) {
            long timeDelta = SystemClock.elapsedRealtime() -
                    lastState.getLastPositionUpdateTime();
            currentPosition += (int) timeDelta * lastState.getPlaybackSpeed();
        }
        if (progress != null) {
            progress.setProgress((int) currentPosition);
            startTime.setText(DateUtils.formatElapsedTime(progress.getProgress() / 1000));
        }
    }

    public static TrackFragment newInstance(Bundle args){
        TrackFragment fragment=new TrackFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private void inject(){
        App.appInstance().playerComponent()
                .inject(this);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getActivity().supportPostponeEnterTransition();
        browserCompat=new MediaBrowserCompat(getActivity(),
                new ComponentName(getActivity(), MusicPlaybackService.class),
                connectionCallback,null);
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                startTime.setText(DateUtils.formatElapsedTime(progress/1000));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                stopSeekBarUpdate();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                getControls().seekTo(seekBar.getProgress());
                startSeekBarUpdate();
            }
        });
    }

    private MediaControllerCompat.TransportControls getControls(){
        return MediaControllerCompat.getMediaController(getActivity()).getTransportControls();
    }

    public void showArt(String artUrl){
        Log.d(TAG,"showArt");
        Glide.with(getContext())
                .load(artUrl)
                .asBitmap()
                .priority(Priority.IMMEDIATE)
                .into(new ImageViewTarget<Bitmap>(smallImage) {
                    @Override
                    protected void setResource(Bitmap resource) {
                        smallImage.setImageBitmap(resource);
                        Blurry.with(getContext())
                                .async()
                                .from(resource)
                                .into(background);
                            getActivity().supportStartPostponedEnterTransition();

                    }
                });
    }

    private void updateDuration(MediaMetadataCompat metadataCompat){
        Log.d(TAG,"Update duration");
        int duration=(int)metadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
        endTime.setText(DateUtils.formatElapsedTime(duration/1000));
        startTime.setText("0");
        progress.setMax(duration);
        String text=Long.toString(metadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER))
                +" of "+Long.toString(metadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS));
        trackName.setText(metadataCompat.getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE));
        artist.setText(metadataCompat.getText(MediaMetadataCompat.METADATA_KEY_ARTIST));
        pages.setText(text);
        String imageUrl=metadataCompat.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI);
        showArt(imageUrl);
    }

    @Override
    protected int layoutId() {
        return R.layout.fragment_player;
    }
}
