package com.mvgv70.xposed_mtc_music;

import com.mvgv70.utils.IniFile;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class ScreenClock implements IXposedHookLoadPackage 
{
  private final static String INI_FILE_NAME = "/mnt/external_sd/mtc-music/mtc-music.ini";
  private final static String SCREENCLOCK_SECTION = "screenclock";
  private static IniFile props = new IniFile();
  private static Activity screenClockActivity;
  // names
  private static String title_name;
  private static String album_name;
  private static String artist_name;
  private static String cover_name;
  private static String freq_name;
  private static String station_name;
  private static String speed_name;
  private static String speed_units;
  private static String title_add;
  private static String artist_add;
  private static String freq_add;
  private static String station_add;
  private static String date_name;
  private static String date_format;
  // view
  private static TextView titleView = null;
  private static TextView albumView = null;
  private static TextView artistView = null;
  private static ImageView coverView = null;
  private static TextView freqView = null;
  private static TextView stationView = null;
  private static TextView speedView = null;
  private static TextClock dateView = null;
  private static int textColor = 0;
  private final static String TAG = "xposed-mtc-music";

  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    
    // MainActivity.onCreate(Bundle)
    XC_MethodHook onCreate = new XC_MethodHook() {
        
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"ScreenClock:onCreate");
        screenClockActivity = (Activity)param.thisObject;
        // ������ ��������
        readSettings();
        // �������� broadcast receiver
        createReceivers();
      }
    };
    
    // MainActivity.onDestroy()
    XC_MethodHook onDestroy = new XC_MethodHook() {
        
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"ScreenClock:onDestroy");
        // ��������� receiver
        screenClockActivity.unregisterReceiver(tagsReceiver);
        // ��������
        if (speedView != null)
        {
          try
          {
            LocationManager locationManager = (LocationManager)screenClockActivity.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null)
            {
              // ����������� ��������
              locationManager.removeUpdates(locationListener);
            }
          }
          catch (Exception e) { }
        } 
      }
    };
	    	    
    // start hooks
    if (!lpparam.packageName.equals("com.microntek.screenclock")) return;
    Log.d(TAG,"com.microntek.screenclock");
    XposedHelpers.findAndHookMethod("com.microntek.screenclock.MainActivity", lpparam.classLoader, "onCreate", Bundle.class, onCreate);
    XposedHelpers.findAndHookMethod("com.microntek.screenclock.MainActivity", lpparam.classLoader, "onDestroy", onDestroy);
    Log.d(TAG,"com.microntek.screenclock hook OK");
  }
  
  // ������ �������� �� mtc-music.ini
  private void readSettings()
  {
    try
    {
      Log.d(TAG,"read settings from "+INI_FILE_NAME);
      props.loadFromFile(INI_FILE_NAME);
      // ����� ���������
      title_name = props.getValue(SCREENCLOCK_SECTION, "music_title", "");
      Log.d(TAG,"title_name="+title_name);
      album_name = props.getValue(SCREENCLOCK_SECTION, "music_album", "");
      Log.d(TAG,"album_name="+album_name);
      artist_name = props.getValue(SCREENCLOCK_SECTION, "music_artist", "");
      Log.d(TAG,"artist_name="+artist_name);
      cover_name = props.getValue(SCREENCLOCK_SECTION, "music_cover", "");
      Log.d(TAG,"cover_name="+cover_name);
      freq_name = props.getValue(SCREENCLOCK_SECTION, "radio_freq", "");
      Log.d(TAG,"freq_name="+freq_name);
      station_name = props.getValue(SCREENCLOCK_SECTION, "radio_station", "");
      Log.d(TAG,"station_name="+station_name);
      speed_name = props.getValue(SCREENCLOCK_SECTION, "speed", "");
      Log.d(TAG,"speed_name="+speed_name);
      speed_units = props.getValue(SCREENCLOCK_SECTION, "speed_units", "");
      Log.d(TAG,"speed_units="+speed_units);
      title_add = props.getValue(SCREENCLOCK_SECTION, "title_add", "");
      Log.d(TAG,"title_add="+title_add);
      artist_add = props.getValue(SCREENCLOCK_SECTION, "artist_add", "");
      Log.d(TAG,"artist_add="+artist_add);
      freq_add = props.getValue(SCREENCLOCK_SECTION, "freq_add", "");
      Log.d(TAG,"freq_add="+freq_add);
      station_add = props.getValue(SCREENCLOCK_SECTION, "station_add", "");
      Log.d(TAG,"station_add="+station_add);
      date_name = props.getValue(SCREENCLOCK_SECTION, "date_name", "yeardate");
      Log.d(TAG,"date_name="+date_name);
      date_format = props.getValue(SCREENCLOCK_SECTION, "date_format", "");
      Log.d(TAG,"date_format="+date_format);
      // ����
      textColor = 0;
      String color = props.getValue(SCREENCLOCK_SECTION, "color", "");
      Log.d(TAG,"color="+color);
      try
      {
        if (!color.isEmpty()) textColor = Color.parseColor(color);
      }
      catch (Exception e)
      {
        Log.e(TAG,"invalid color: "+color);
      }
    } catch (Exception e)
    {
      Log.e(TAG,e.getMessage());
    }
    // id
    int title_id = 0;
    int album_id = 0;
    int artist_id = 0;
    int cover_id = 0;
    int freq_id = 0;
    int station_id = 0;
    int speed_id = 0;
    int date_id = 0;
    Resources res = screenClockActivity.getResources();
    if (!title_name.isEmpty())
      title_id = res.getIdentifier(title_name,"id", screenClockActivity.getPackageName());
    if (!album_name.isEmpty())
      album_id = res.getIdentifier(album_name,"id", screenClockActivity.getPackageName());
    if (!artist_name.isEmpty())
      artist_id = res.getIdentifier(artist_name,"id", screenClockActivity.getPackageName());
    if (!cover_name.isEmpty())
      cover_id = res.getIdentifier(cover_name,"id", screenClockActivity.getPackageName());
    if (!freq_name.isEmpty())
      freq_id = res.getIdentifier(freq_name,"id", screenClockActivity.getPackageName());
    if (!station_name.isEmpty())
      station_id = res.getIdentifier(station_name,"id", screenClockActivity.getPackageName());
    if (!speed_name.isEmpty())
      speed_id = res.getIdentifier(speed_name,"id", screenClockActivity.getPackageName());
    if (!date_name.isEmpty())
      date_id = res.getIdentifier(date_name,"id", screenClockActivity.getPackageName());
    Log.d(TAG,"title_id="+title_id);
    Log.d(TAG,"album_id="+album_id);
    Log.d(TAG,"artist_id="+artist_id);
    Log.d(TAG,"cover_id="+cover_id);
    Log.d(TAG,"freq_id="+freq_id);
    Log.d(TAG,"station_id="+station_id);
    Log.d(TAG,"speed_id="+speed_id);
    Log.d(TAG,"date_id="+speed_id);
    // views
    titleView = null;
    albumView = null;
    artistView = null;
    coverView = null;
    freqView = null;
    stationView = null;
    speedView = null;
    dateView = null;
    if (title_id > 0)
    {
      titleView = (TextView)screenClockActivity.findViewById(title_id);
      if (titleView != null)
      {
        if (textColor != 0) titleView.setTextColor(textColor);
      }
      else
        Log.w(TAG,"titleView == null");
    }
    if (album_id > 0)
    {
      albumView = (TextView)screenClockActivity.findViewById(album_id);
      if (albumView != null)
      {
        if (textColor != 0) albumView.setTextColor(textColor);
      }
      else
        Log.w(TAG,"albumView == null");
    }
    if (artist_id > 0)
    {
      artistView = (TextView)screenClockActivity.findViewById(artist_id);
      if (artistView != null)
      {
        if (textColor != 0) artistView.setTextColor(textColor);
      }
      else 
        Log.w(TAG,"artistView == null");
    }
    if (cover_id > 0)
    {
      coverView = (ImageView)screenClockActivity.findViewById(cover_id);
      if (coverView == null) Log.w(TAG,"coverView == null");
    }
    if (freq_id > 0)
    {
      freqView = (TextView)screenClockActivity.findViewById(freq_id);
      if (freqView != null)
      {
        if (textColor != 0) freqView.setTextColor(textColor);
      }
      else
        Log.w(TAG,"freqView == null");
    }
    if (station_id > 0)
    {
      stationView = (TextView)screenClockActivity.findViewById(station_id);
      if (stationView != null)
      {
        if (textColor != 0) stationView.setTextColor(textColor);
      }
      else
        Log.w(TAG,"stationView == null");
    }
    if (speed_id > 0)
    {
      speedView = (TextView)screenClockActivity.findViewById(speed_id);
      if (speedView == null) Log.w(TAG,"speedView == null");
    }
    if (date_id > 0)
    {
      dateView = (TextClock)screenClockActivity.findViewById(date_id);
      if (dateView != null)
      {
        // ��������� ���� ���� �����
    	if (textColor != 0) dateView.setTextColor(textColor);
        // ������ ���� ���� �����
        if (!date_format.isEmpty())
        {
          dateView.setFormat24Hour(date_format);
          dateView.setFormat12Hour(date_format);
        }
      }
    }
  }
  
  // �������� broadcast receiver
  private void createReceivers()
  {
    IntentFilter ti = new IntentFilter();
    ti.addAction("com.android.music.playstatechanged");
    screenClockActivity.registerReceiver(tagsReceiver, ti);
    Log.d(TAG,"tagsReceiver created");
    IntentFilter ri = new IntentFilter();
    ri.addAction("com.android.radio.freq");
    screenClockActivity.registerReceiver(freqReceiver, ri);
    Log.d(TAG,"freqReceiver created");
    // ������� ��������� ������ � ������ �����
    Intent intent = new Intent("com.android.music.playstatusrequest");
    screenClockActivity.sendBroadcast(intent);
    Log.d(TAG,"com.android.music.playstatusrequest sent");
    if (speedView != null)
    {
      try
      {
        LocationManager locationManager = (LocationManager)screenClockActivity.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null)
        {
          // ����������� ��������
          locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, locationListener);
          Log.d(TAG,"speed listener created");
        }
      }
      catch (Exception e)
      {
        // ��� ����
        Log.e(TAG,"LocationManager: "+e.getMessage());
      }
    }
  }
  
  // com.android.music.playstatechanged
  private BroadcastReceiver tagsReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      Log.d(TAG,"ScreenClock: tags receiver "+intent.getAction());
      // mp3 tags
      String title = intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE);
      String artist = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST);
      String album = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM);
      // show tags
      Log.d(TAG,"title="+title);
      Log.d(TAG,"artist="+artist);
      Log.d(TAG,"album="+album);
      // ��������� ����
      if (titleView != null)
      {
    	// ����� ������������ TextUtils.isEmpty()
        if ((title != null) && !title.isEmpty()) title = title + title_add;
        titleView.setText(title);
      }
      if (artistView != null)
      {
        if ((artist != null) && !artist.isEmpty()) artist = artist + artist_add;
        artistView.setText(artist);
      }
      if (albumView != null) albumView.setText(album);
      // if (coverView != null) coverView.setImageBitmap(cover);
    }
  };
  
  // com.android.radio.freq
  private BroadcastReceiver freqReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      Log.d(TAG,"ScreenClock: freq receiver");
      // freq & station
      String freq = intent.getStringExtra("freq");
      String station = intent.getStringExtra("freq_name");
      // show freq
      Log.d(TAG,"freq="+freq);
      Log.d(TAG,"station="+station);
      // ��������� ����
      if (freqView != null)
      {
        if ((freq != null) && !freq.isEmpty()) freq = freq + freq_add;
        freqView.setText(freq);
      }
      if (stationView != null)
      {
        if ((station != null) && !station.isEmpty()) station = station + station_add;
        stationView.setText(station);
      }
    }
  };
  
  // ��������� ��������
  private LocationListener locationListener = new LocationListener() 
  {
    public void onLocationChanged(Location location)
    {
      if (!location.hasSpeed()) return;
      int speed = (int)(location.getSpeed()*3.6);
      speedView.setText(speed+speed_units);
    }
      
    public void onProviderDisabled(String provider) {}
      
    public void onProviderEnabled(String provider) {}
      
    public void onStatusChanged(String provider, int status, Bundle extras) {}
  };
  
}
