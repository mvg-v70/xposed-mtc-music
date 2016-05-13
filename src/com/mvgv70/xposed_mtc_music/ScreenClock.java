package com.mvgv70.xposed_mtc_music;

import java.io.File;

import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.Tag;

import com.mvgv70.utils.IniFile;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class ScreenClock implements IXposedHookLoadPackage 
{
  private final static String INI_FILE_SHORT = "settings.ini";
  private final static String INI_FILE_NAME = "/mnt/external_sd/mtc-music/"+INI_FILE_SHORT;
  private final static String USER_SETTINGS_NAME = "/mnt/external_sd/mtc-music/mtc-music.ini";
  private final static String SCREENCLOCK_SECTION = "screenclock";
  private final static String MUSIC_SECTION = "music";
  private static IniFile props = new IniFile();
  private static Activity screenClockActivity;
  // names
  private static String title_name = "";
  private static String album_name = "";
  private static String artist_name = "";
  private static String freq_name = "";
  private static String station_name = "";
  private static String speed_name = "";
  private static String speed_units = "";
  private static String title_add = "";
  private static String artist_add = "";
  private static String freq_add = "";
  private static String station_add = "";
  private static String date_name = "";
  private static String date_format = "";
  private static String cover_name = "";
  // parameters
  private static float cover_alpha = 0;
  private static String album_cover_name = "";
  private static String background_name = "";
  private static int backgroundColor = 0;
  private static int textColor = 0;
  private static boolean bluetoothClose = false;
  private static float speed_divider = 1;
  // view
  private static TextView titleView = null;
  private static TextView albumView = null;
  private static TextView artistView = null;
  private static TextView freqView = null;
  private static TextView stationView = null;
  private static TextView speedView = null;
  private static TextClock dateView = null;
  private static ImageView coverView = null;
  private static View backgroundView = null;
  // настройки
  private static boolean touchMode = false;
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
        // чтение настроек
        readSettings();
        // создание broadcast receiver
        createReceivers();
      }
    };
    
    // MainActivity.onDestroy()
    XC_MethodHook onDestroy = new XC_MethodHook() {
        
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"ScreenClock:onDestroy");
        // отключаем receiver
        screenClockActivity.unregisterReceiver(tagsReceiver);
        // скорость
        if (speedView != null)
        {
          try
          {
            LocationManager locationManager = (LocationManager)screenClockActivity.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null)
            {
              // определение скорости
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
  
  // чтение настроек из mtc-music.ini
  private void readSettings()
  {
    try
    {
      // читаем настроечный файл из assests
      try
      {
        Log.d(TAG,"read settings from assets/"+INI_FILE_SHORT);
        props.loadFromAssets(screenClockActivity, INI_FILE_SHORT);
      }
      catch (Exception e)
      {
        // если нет assests читаем файл
        Log.d(TAG,"read settings from "+INI_FILE_NAME);
        props.loadFromFile(INI_FILE_NAME);
      }
      // имена элементов
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
      // формат даты
      date_format = props.getValue(SCREENCLOCK_SECTION, "date_format", "");
      Log.d(TAG,"date_format="+date_format);
      // цвет надписей
      textColor = 0xffe3d3b6;
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
      // cover_alpha
      cover_alpha = props.getFloatValue(SCREENCLOCK_SECTION, "cover_alpha", 0.25f);
      Log.d(TAG,"cover_alpha="+cover_alpha);
      // touch_mode
      touchMode = props.getBoolValue(SCREENCLOCK_SECTION, "touch_mode", false);
      Log.d(TAG,"touch_mode="+touchMode);
      // background_name
      background_name = props.getValue(SCREENCLOCK_SECTION, "background_name", "");
      Log.d(TAG,"background_name="+background_name);
      // background_color
      backgroundColor = 0xc0000000;
      color = props.getValue(SCREENCLOCK_SECTION, "background_color", "");
      Log.d(TAG,"background_color="+color);
      try
      {
        if (!color.isEmpty()) backgroundColor = Color.parseColor(color);
      }
      catch (Exception e)
      {
        Log.e(TAG,"invalid color: "+color);
      }
      // album_cover_name
      album_cover_name = props.getValue(MUSIC_SECTION, "album_cover", "");
      Log.d(TAG,"album_cover_name="+album_cover_name);
      // формат даты
      date_format = props.getValue(SCREENCLOCK_SECTION, "date_format", "");
      Log.d(TAG,"date_format="+date_format);
      // скорость в мил€х
      speed_divider = props.getFloatValue(SCREENCLOCK_SECTION, "speed.divider", 1);
      Log.d(TAG,"speed.divider="+speed_divider);
    }
    catch (Exception e)
    {
      Log.e(TAG,e.getMessage());
    }
    // читаем пользовательские настройки
    IniFile user_props = new IniFile();
    try
    {
      Log.d(TAG,"read user settings from "+USER_SETTINGS_NAME);
      user_props.loadFromFile(USER_SETTINGS_NAME);
      // цвет надписей
      String color = user_props.getValue(SCREENCLOCK_SECTION, "color", "");
      Log.d(TAG,"color="+color);
      try
      {
        if (!color.isEmpty()) textColor = Color.parseColor(color);
      }
      catch (Exception e)
      {
        Log.e(TAG,"invalid color: "+color);
      }
      // background_color
      color = user_props.getValue(SCREENCLOCK_SECTION, "background_color", "");
      Log.d(TAG,"background_color="+color);
      try
      {
        if (!color.isEmpty()) backgroundColor = Color.parseColor(color);
      }
      catch (Exception e)
      {
        Log.e(TAG,"invalid color: "+color);
      }
      // cover_alpha
      cover_alpha = user_props.getFloatValue(SCREENCLOCK_SECTION, "cover_alpha", cover_alpha);
      Log.d(TAG,"cover_alpha="+cover_alpha);
      // формат даты
      date_format = user_props.getValue(SCREENCLOCK_SECTION, "date_format", date_format);
      Log.d(TAG,"date_format="+date_format);
      // album_cover_name
      album_cover_name = user_props.getValue(MUSIC_SECTION, "album_cover", album_cover_name);
      Log.d(TAG,"album_cover_name="+album_cover_name);
      // touch_mode
      touchMode = user_props.getBoolValue(SCREENCLOCK_SECTION, "touch_mode", touchMode);
      Log.d(TAG,"touch_mode="+touchMode);
      // bluetooth
      bluetoothClose = user_props.getBoolValue(SCREENCLOCK_SECTION, "bluetooth.close", false);
      Log.d(TAG,"bluetooth.close="+bluetoothClose);
      // скорость в мил€х
      speed_divider = user_props.getFloatValue(SCREENCLOCK_SECTION, "speed.divider", speed_divider);
      if (speed_divider <= 0) speed_divider = 1;
      Log.d(TAG,"speed.divider="+speed_divider);
    }
    catch (Exception e)
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
    int background_id = 0;
    Resources res = screenClockActivity.getResources();
    if (!title_name.isEmpty())
      title_id = res.getIdentifier(title_name, "id", screenClockActivity.getPackageName());
    if (!album_name.isEmpty())
      album_id = res.getIdentifier(album_name, "id", screenClockActivity.getPackageName());
    if (!artist_name.isEmpty())
      artist_id = res.getIdentifier(artist_name, "id", screenClockActivity.getPackageName());
    if (!cover_name.isEmpty())
      cover_id = res.getIdentifier(cover_name, "id", screenClockActivity.getPackageName());
    if (!freq_name.isEmpty())
      freq_id = res.getIdentifier(freq_name, "id", screenClockActivity.getPackageName());
    if (!station_name.isEmpty())
      station_id = res.getIdentifier(station_name, "id", screenClockActivity.getPackageName());
    if (!speed_name.isEmpty())
      speed_id = res.getIdentifier(speed_name, "id", screenClockActivity.getPackageName());
    if (!date_name.isEmpty())
      date_id = res.getIdentifier(date_name, "id", screenClockActivity.getPackageName());
    if (!background_name.isEmpty())
      background_id = res.getIdentifier(background_name, "id", screenClockActivity.getPackageName());
    Log.d(TAG,"title_id="+title_id);
    Log.d(TAG,"album_id="+album_id);
    Log.d(TAG,"artist_id="+artist_id);
    Log.d(TAG,"cover_id="+cover_id);
    Log.d(TAG,"freq_id="+freq_id);
    Log.d(TAG,"station_id="+station_id);
    Log.d(TAG,"speed_id="+speed_id);
    Log.d(TAG,"date_id="+date_id);
    Log.d(TAG,"background_id="+background_id);
    // views
    titleView = null;
    albumView = null;
    artistView = null;
    coverView = null;
    freqView = null;
    stationView = null;
    speedView = null;
    dateView = null;
    backgroundView = null;
    if (title_id > 0)
    {
      titleView = (TextView)screenClockActivity.findViewById(title_id);
      if (titleView != null)
      {
        titleView.setTextColor(textColor);
      }
      else
        Log.w(TAG,"titleView == null");
    }
    if (album_id > 0)
    {
      albumView = (TextView)screenClockActivity.findViewById(album_id);
      if (albumView != null)
      {
        albumView.setTextColor(textColor);
      }
      else
        Log.w(TAG,"albumView == null");
    }
    if (artist_id > 0)
    {
      artistView = (TextView)screenClockActivity.findViewById(artist_id);
      if (artistView != null)
      {
        artistView.setTextColor(textColor);
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
        freqView.setTextColor(textColor);
      }
      else
        Log.w(TAG,"freqView == null");
    }
    if (station_id > 0)
    {
      stationView = (TextView)screenClockActivity.findViewById(station_id);
      if (stationView != null)
      {
        stationView.setTextColor(textColor);
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
        // установим цвет если задан
    	if (textColor != 0) dateView.setTextColor(textColor);
        // формат даты если задан
        if (!date_format.isEmpty())
        {
          dateView.setFormat24Hour(date_format);
          dateView.setFormat12Hour(date_format);
        }
      }
    }
    if (background_id > 0)
    {
      backgroundView = (View)screenClockActivity.findViewById(background_id);
      if (backgroundView == null) Log.w(TAG,"backgroundView == null");
    }
	// background
	if (backgroundView != null)
	{
      if (touchMode)
        backgroundView.setOnClickListener(screenClick);
	  // TODO: установка цвета фона
	  Log.d(TAG,"setBackgroundColor("+backgroundColor+")");
      backgroundView.setBackgroundColor(backgroundColor); 
	}

  }
  
  // создание broadcast receiver
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
    // послать сообщение плееру о чтении тегов
    Intent intent = new Intent("com.android.music.playstatusrequest");
    screenClockActivity.sendBroadcast(intent);
    Log.d(TAG,"com.android.music.playstatusrequest sent");
    if (touchMode)
    {
      // выключим receiver закрыти€
      BroadcastReceiver MTCBootReceiver = (BroadcastReceiver)XposedHelpers.getObjectField(screenClockActivity, "MTCBootReceiver");
      screenClockActivity.unregisterReceiver(MTCBootReceiver);
      screenClockActivity.registerReceiver(MTCBootReceiver, new IntentFilter());
      Log.d(TAG,"com.microntek.endclock receiver disabled");
    }
    // bluetooth
    if (bluetoothClose)
    {
      IntentFilter bi = new IntentFilter();
      bi.addAction("com.microntek.bt.report");
      screenClockActivity.registerReceiver(bluetoothReceiver, bi);
      Log.d(TAG,"bluetooth created");
    }
    // скорость
    if (speedView != null)
    {
      try
      {
        LocationManager locationManager = (LocationManager)screenClockActivity.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null)
        {
          // определение скорости
          locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, locationListener);
          Log.d(TAG,"speed listener created");
        }
      }
      catch (Exception e)
      {
        // нет прав
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
      if (!intent.hasExtra(MediaStore.EXTRA_MEDIA_TITLE)) return;
      // mp3 tags
      String title = intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE);
      String artist = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST);
      String album = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM);
      String filename = intent.getStringExtra("filename");
      // show tags
      Log.d(TAG,"title="+title);
      Log.d(TAG,"artist="+artist);
      Log.d(TAG,"album="+album);
      Log.d(TAG,"filename="+filename);
      // установим теги
      if (titleView != null)
      {
    	// можно использовать TextUtils.isEmpty()
        if ((title != null) && !title.isEmpty()) title = title + title_add;
        titleView.setText(title);
      }
      if (artistView != null)
      {
        if ((artist != null) && !artist.isEmpty()) artist = artist + artist_add;
        artistView.setText(artist);
      }
      if (albumView != null) albumView.setText(album);
      if (!TextUtils.isEmpty(filename)) getCover(filename);
      // очистим пол€ радио
      if (freqView != null) freqView.setText("");
      if (stationView != null) stationView.setText("");
    }
  };
  
  // ћузыка: чтение и показ картинки из тегов
  private static void getCover(String fileName)
  {
    // cover
	if (coverView != null)
	{
      Bitmap cover = null;
      try
      {
        MP3File mp3file = (MP3File)AudioFileIO.read(new File(fileName));
        Tag tags = mp3file.getTag();
        // определение картинки
        cover = Music.getArtWork(tags, fileName, album_cover_name);
      }
      catch (Exception e)
      {
        Log.e(TAG,e.getMessage());
      }
	  coverView.setImageBitmap(cover);
	  coverView.setAlpha(cover_alpha);
    }
  }
  
  // –адио: com.android.radio.freq
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
      // установим теги
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
      // очистим пол€ музыка
      if (titleView != null) titleView.setText("");
      if (artistView != null) artistView.setText("");
      if (albumView != null) albumView.setText("");
      if (coverView != null) coverView.setImageBitmap(null);
    }
  };
  
  // bluetooth receiver
  private BroadcastReceiver bluetoothReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      Log.d(TAG,"Bluetooth: "+intent.getIntExtra("connect_state", 0));
      int connect_state = intent.getIntExtra("connect_state",0);
      if ((connect_state == 2) || (connect_state == 3))
      {
        // CALL_OUT or CALL_IN
        screenClockActivity.finish();
        Log.d(TAG,"finish by bluetooth");
      }
    }
  };
  
  // изменение скорости
  private LocationListener locationListener = new LocationListener() 
  {
    public void onLocationChanged(Location location)
    {
      if (!location.hasSpeed()) return;
      int speed = (int)(location.getSpeed()*3.6/speed_divider);
      speedView.setText(speed+speed_units);
    }
      
    public void onProviderDisabled(String provider) {}
      
    public void onProviderEnabled(String provider) {}
    
    // изменение статуса gps
    public void onStatusChanged(String provider, int status, Bundle extras) 
    {
      Log.d(TAG,"gps provider status="+status);
      // if ((status == LocationProvider.OUT_OF_SERVICE) || (status == LocationProvider.TEMPORARILY_UNAVAILABLE))
    }
  };
  
  // нажатие на экран
  public View.OnClickListener screenClick = new View.OnClickListener() 
  {
    public void onClick(View v) 
    {
      screenClockActivity.finish();
      Log.d(TAG,"finish by touch");
    }
  };
  
}
