package com.mvgv70.xposed_mtc_music;

import java.io.File;
import java.util.ArrayList;

import com.mvgv70.utils.IniFile;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Music implements IXposedHookLoadPackage 
{
  private final static String INI_FILE_NAME = "/mnt/external_sd/mtc-music/mtc-music.ini";
  private final static String MAIN_SECTION = "main";
  private final static String MUSIC_SECTION = "music";
  private static IniFile props = new IniFile();
  //
  private static Activity musicActivity;
  private static Object mUi;
  private static Object mServer;
  private static Context context;
  private static Drawable ico_shuffle;
  private static BroadcastReceiver usbReceiver = null;
  private static boolean mPlaying = false;
  private static Handler handler;
  // режимы loop
  private final static int LOOP_MODE_NEXT_DIR = 0;
  private final static int LOOP_MODE_SINGLE_FILE = 1;
  private final static int LOOP_MODE_ALBUM = 2;
  private final static int LOOP_MODE_SHUFFLE = 3;
  private static int loop_mode;
  // mp3-tags
  private static String currentFileName;
  private static String album = "";
  private static String title = "";
  private static String artist = "";
  private static long album_id = -1;
  private static long _id = -1;
  private static Bitmap cover = null;
  // параметры в mtc-music.ini
  private static String back_press_name;
  private static int back_press_time;
  private static Boolean control_keys;
  private static String album_cover_name = "";
  private static String music_list_name;
  private static String title_name = "";
  private static String album_name = "";
  private static String artist_name = "";
  private static String cover_name = "";
  private static String visualizer_name = "";
  private static String ico_shuffle_name = "";
  private static String findview_by_name = "";
  private static String blank_cover_name = "";
  // элементы
  private static View visualizerView = null;
  private static View back_press_view = null;
  private static int ico_shuffle_id;
  private static int blank_cover_id;
  // теги
  private static TextView titleView = null;
  private static TextView artistView = null;
  private static TextView albumView = null;
  private static ImageView coverView = null;
  private final static String TAG = "xposed-mtc-music";
  
  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    
    // MusicActivity.onCreate(Bundle)
    XC_MethodHook onCreate = new XC_MethodHook() {
        
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"onCreate");
        musicActivity = (Activity)param.thisObject;
        handler = (Handler)XposedHelpers.getObjectField(musicActivity, "handler");
        mUi = XposedHelpers.getObjectField(param.thisObject, "mUi");
        mServer = XposedHelpers.getObjectField(param.thisObject, "mServer");
        // показать версию модуля
        try 
        {
     	  context = musicActivity.createPackageContext(getClass().getPackage().getName(), Context.CONTEXT_IGNORE_SECURITY);
     	  String version = context.getString(R.string.app_version_name);
          Log.d(TAG,"version="+version);
        } catch (NameNotFoundException e) {}
        // чтение настроек
        readSettings();
        // обработчики подключения флешки и медиа-кнопок
        createReceivers();
        // создаем обработчик возврата
        createBackPressListener();
        // если выбран режим перемешивания нарисуем картинку
        if (loop_mode == LOOP_MODE_SHUFFLE)
          XposedHelpers.callMethod(mUi, "updateRepeat", loop_mode);
      }
    };
    
    // MusicActivity.onDestroy()
    XC_MethodHook onDestroy = new XC_MethodHook() {
        
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    	Log.d(TAG,"onDestroy");
    	if (usbReceiver != null) musicActivity.unregisterReceiver(mediaReceiver);
        if (control_keys) 
        {
          musicActivity.unregisterReceiver(mediaButtonReceiver);
          musicActivity.unregisterReceiver(commandReceiver);
        }
        musicActivity.unregisterReceiver(tagsQueryReceiver);
        musicActivity = null;
      }
    };
    
    // MusicActivity.onState(int)
    XC_MethodHook onState = new XC_MethodHook() {
        
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        musicActivity = (Activity)param.thisObject;
        int playState = (int)param.args[0];
        mPlaying = (playState == 1);
        Log.d(TAG,"mPlaying="+mPlaying);
        sendNotifyIntent(musicActivity);
      }
    };
    
    // MusicActivity.updataMp3info()
    XC_MethodHook updataMp3info = new XC_MethodHook() {
        
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
      	String fileName = "";
      	Log.d(TAG,"updataMp3info");
      	int currentPlayIndex = XposedHelpers.getIntField(musicActivity, "currentPlayIndex");
      	if (currentPlayIndex >= 0)
      	{
          @SuppressWarnings("unchecked")
          ArrayList<String> list = (ArrayList<String>)XposedHelpers.getObjectField(musicActivity, music_list_name);
          if (list.size() > 0)
      	    fileName = list.get(currentPlayIndex);
      	}
      	Log.d(TAG,"fileName="+fileName);
      	currentFileName = fileName;
      	// отложенное чтение mp3-тегов
      	handler.postDelayed(mp3Info, 100);
      }
    };
    
    // SurfaceView.setVisibility(int)
    XC_MethodHook setVisuVisibility = new XC_MethodHook() {
        
      @Override
      protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    	int visibility = (int)param.args[0];
    	if (param.thisObject.getClass().getName().equals("com.music.VisualizerView"))
    	{
    	  if (visibility == View.VISIBLE)
    	  {
    	    // пытаемся показать visualizer
    	    if ((coverView != null) && (cover != null) && (visualizerView != null))
    	    {
    	      // ничего не делаем, если задана обложка
    	      Log.d(TAG,"Visualizer: set invisible");
    	      param.setResult(null);
    	    }
    	  }
    	}
      }
    };
    
    // MusicServer.onCompletion(MediaPlayer)
    XC_MethodReplacement onCompletion = new XC_MethodReplacement() {
        
      @Override
      protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
        int LoopMode = XposedHelpers.getIntField(param.thisObject, "LoopMode");
        Log.d(TAG,"onCompletion.LoopMode="+LoopMode+" ("+loop_mode+")");
        // MediaPlayer
        MediaPlayer mPlayer = (MediaPlayer)XposedHelpers.getObjectField(param.thisObject, "mPlayer");
        if (mPlayer != null)
        {
          XposedHelpers.callMethod(param.thisObject, "sSendSessionInfo", 0);
          mPlayer.release();   
          XposedHelpers.setObjectField(param.thisObject, "mPlayer", null);
        }
        @SuppressWarnings("unchecked")
		ArrayList<String> folder_list = (ArrayList<String>)XposedHelpers.getObjectField(musicActivity, "folderList");
        // по режимам
        if (LoopMode == LOOP_MODE_NEXT_DIR)
        {
          int sel_item = XposedHelpers.getIntField(param.thisObject, "selItem");
          @SuppressWarnings("unchecked")
          ArrayList<String> music_list = (ArrayList<String>)XposedHelpers.getObjectField(musicActivity, music_list_name);
          if (sel_item < music_list.size()-1)
          {
            // есть следующий файл
            XposedHelpers.callMethod(param.thisObject, "sNext");
          }
          else
          {
            // играем следующий каталог
            File file = new File(currentFileName,"");
            String dir = file.getParent();
            int index = folder_list.indexOf(dir);
            Log.d(TAG,"dir index="+index);
            if ((index >= 0) && (index < folder_list.size()-1))
            {
              // играем следующую папку
              XposedHelpers.callMethod(musicActivity, "toFolder", index+1);
            }
          }
        }
        else if (LoopMode == LOOP_MODE_SINGLE_FILE)
        {
         // проигрывание одного файла
         XposedHelpers.callMethod(param.thisObject, "sPlayPause");  
        }
        else if (LoopMode == LOOP_MODE_ALBUM)
        {
          // проигрывание альбома
          int sel_item = XposedHelpers.getIntField(param.thisObject, "selItem");
          @SuppressWarnings("unchecked")
          ArrayList<String> music_list = (ArrayList<String>)XposedHelpers.getObjectField(musicActivity, music_list_name);
          if (sel_item < music_list.size()-1)
          {
            // есть следующий файл
            XposedHelpers.callMethod(param.thisObject, "sNext");
          }
          else
          {
            // в начало альбома 
            XposedHelpers.callMethod(param.thisObject, "sSendSessionInfo", 0);
            XposedHelpers.setIntField(param.thisObject, "selItem", 0);
            XposedHelpers.setIntField(param.thisObject, "currentCmd", 2);
            XposedHelpers.callMethod(param.thisObject, "sStart");
          }
        }
        else if (LoopMode == LOOP_MODE_SHUFFLE)
        {
          // перемешивание
          XposedHelpers.callMethod(param.thisObject, "sShuffle");
        }
        return null;
      }
    };
    
    // MusicActivity.cmdLoop()
    XC_MethodReplacement cmdLoop = new XC_MethodReplacement() {
        
      @Override
      protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
        int loop_flag = XposedHelpers.getIntField(param.thisObject, "loop_flag") + 1;
        if (loop_flag > 3) loop_flag = 0;
        Log.d(TAG,"setloopMode("+loop_flag+")");
        XposedHelpers.setIntField(param.thisObject, "loop_flag", loop_flag);
        XposedHelpers.callMethod(mUi, "updateRepeat", loop_flag);
        XposedHelpers.callMethod(mServer, "setloopMode", loop_flag);
        return null;
      }
    };
    
    // Ui*.updateRepeat(int)
    XC_MethodReplacement updateRepeat = new XC_MethodReplacement() {
        
      @Override
      protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
        int loop_flag = (int)param.args[0];
        loop_mode = loop_flag;
        Log.d(TAG,"updateRepeat("+loop_flag+")");
        // для JY кнопка это FrameLayout
        View mRepeat = (View)XposedHelpers.getObjectField(param.thisObject, "mRepeat");
        ImageView image;
        if (mRepeat instanceof ImageButton)
          image = (ImageView)mRepeat;
        else
          image = (ImageView)XposedHelpers.getObjectField(mRepeat, "imageView");
        if ((loop_flag >= 0) && (loop_flag <= 2))
        {
          int[] loop_mode = (int[])XposedHelpers.getObjectField(param.thisObject, "loop_mode");
          int resource_id = loop_mode[loop_flag];
          image.setImageResource(resource_id);
        }
        else if (loop_flag == LOOP_MODE_SHUFFLE)
        {
          if (ico_shuffle_id > 0)
            image.setImageResource(ico_shuffle_id);
          else
            image.setImageDrawable(ico_shuffle);
        }
        return null;
      }
    };
    
    // start hooks
    if (!lpparam.packageName.equals("com.microntek.music")) return;
    Log.d(TAG,"com.microntek.music");
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicActivity", lpparam.classLoader, "onCreate", Bundle.class, onCreate);
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicActivity", lpparam.classLoader, "onState", int.class, onState);
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicActivity", lpparam.classLoader, "updataMp3info", updataMp3info);
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicActivity", lpparam.classLoader, "onDestroy", onDestroy);
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicActivity", lpparam.classLoader, "cmdLoop", cmdLoop);
    // ищем используемый Ui
    for (int i=1; i<=5; i++)
    {
      try
      {
        XposedHelpers.findAndHookMethod("com.microntek.music.ui.Ui"+i, lpparam.classLoader, "updateRepeat", int.class, updateRepeat);
        Log.d(TAG,"Ui"+i+" detected...");
        break;
      }
      catch (Error e) {}
    }
    XposedHelpers.findAndHookMethod("android.view.SurfaceView", lpparam.classLoader, "setVisibility", int.class, setVisuVisibility);
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicServer", lpparam.classLoader, "onCompletion", MediaPlayer.class, onCompletion);
    Log.d(TAG,"com.microntek.music hook OK");
  }

  // чтение глобальных настроек из mtc-music.ini
  private void readSettings()
  {
    try
    {
      Log.d(TAG,"read settings from "+INI_FILE_NAME);
      props.loadFromFile(INI_FILE_NAME);
      // имя переменной music_list
      music_list_name = props.getValue(MAIN_SECTION, "music_list", "music_list");
      Log.d(TAG,"music_list="+music_list_name);
      // control_keys
      control_keys = props.getBoolValue(MAIN_SECTION, "control_keys", false);
      Log.d(TAG,"control_keys="+control_keys);
      // backpress
      back_press_name = props.getValue(MAIN_SECTION, "backpress.name");
      back_press_time = props.getIntValue(MAIN_SECTION, "backpress.time",20);
      Log.d(TAG,"back_press_name="+back_press_name+", back_press_time="+back_press_time);
      // visualizer_name
      visualizer_name = props.getValue(MAIN_SECTION, "visualizer");
      Log.d(TAG,"visualizer_name="+visualizer_name);
      // album_cover_name
      album_cover_name = props.getValue(MAIN_SECTION, "album_cover");
      Log.d(TAG,"album_cover_name="+album_cover_name);
      // теги
      title_name = props.getValue(MUSIC_SECTION, "title", "");
      Log.d(TAG,"title_name="+title_name);
      album_name = props.getValue(MUSIC_SECTION, "album", "");
      Log.d(TAG,"album_name="+album_name);
      artist_name = props.getValue(MUSIC_SECTION, "artist", "");
      Log.d(TAG,"artist_name="+artist_name);
      cover_name = props.getValue(MUSIC_SECTION, "cover", "");
      Log.d(TAG,"cover_name="+cover_name);
      // ico_shuffle_name
      ico_shuffle_name = props.getValue(MAIN_SECTION, "ico_shuffle", "");
      Log.d(TAG,"ico_shuffle_name="+ico_shuffle_name);
      // findview_by_name
      findview_by_name = props.getValue(MAIN_SECTION, "findview_by", "");
      Log.d(TAG,"findview_by_name="+findview_by_name);
      // blank_cover_name
      blank_cover_name = props.getValue(MAIN_SECTION, "blank_cover", "");
      Log.d(TAG,"blank_cover_name="+blank_cover_name);
    } catch (Exception e)
    {
      Log.e(TAG,e.getMessage());
    }
    // id
    int title_id = 0;
    int album_id = 0;
    int artist_id = 0;
    int cover_id = 0;
    int visualizer_id = 0;
    int back_press_id = 0;
    Resources res = musicActivity.getResources();
    if (!title_name.isEmpty())
      title_id = res.getIdentifier(title_name, "id", musicActivity.getPackageName());
    if (!album_name.isEmpty())
      album_id = res.getIdentifier(album_name, "id", musicActivity.getPackageName());
    if (!artist_name.isEmpty())
      artist_id = res.getIdentifier(artist_name, "id", musicActivity.getPackageName());
    if (!cover_name.isEmpty())
      cover_id = res.getIdentifier(cover_name, "id", musicActivity.getPackageName());
    if (!visualizer_name.isEmpty())
      visualizer_id = res.getIdentifier(visualizer_name, "id", musicActivity.getPackageName());
    if (!back_press_name.isEmpty())
      back_press_id = res.getIdentifier(back_press_name, "id", musicActivity.getPackageName());
    //
    Log.d(TAG,"title_id="+title_id);
    Log.d(TAG,"album_id="+album_id);
    Log.d(TAG,"artist_id="+artist_id);
    Log.d(TAG,"cover_id="+cover_id);
    Log.d(TAG,"visualizer_id="+visualizer_id);
    Log.d(TAG,"back_press_id="+back_press_id);
    // views
    titleView = null;
    albumView = null;
    artistView = null;
    coverView = null;
    visualizerView = null;
    // альтернативный View для поиска элементов
    View findView = null;
    if (!findview_by_name.isEmpty())
    {
      try
      {
        findView = (View)XposedHelpers.getObjectField(mUi, findview_by_name);
      }
      catch (Error e)
      {
        Log.e(TAG,"mUi."+findview_by_name+" not found");
      }
    }
    // поиск элементов
    if (title_id > 0)
    {
      if (findView == null)
        titleView = (TextView)musicActivity.findViewById(title_id);
      else
        titleView = (TextView)findView.findViewById(title_id);
      if (titleView == null) Log.w(TAG,"titleView == null");
    }
    if (album_id > 0)
    {
      if (findView == null)
        albumView = (TextView)musicActivity.findViewById(album_id);
      else
        albumView = (TextView)findView.findViewById(album_id);
      if (albumView == null) Log.w(TAG,"albumView == null");
    }
    if (artist_id > 0) 
    {
      if (findView == null)
        artistView = (TextView)musicActivity.findViewById(artist_id);
      else
        artistView = (TextView)findView.findViewById(artist_id);
      if (artistView == null) Log.w(TAG,"artistView == null");
    }
    if (cover_id > 0)
    {
      if (findView == null)
        coverView = (ImageView)musicActivity.findViewById(cover_id);
      else
        coverView = (ImageView)findView.findViewById(cover_id);
      if (coverView == null) Log.w(TAG,"coverView == null");
    }
    if (visualizer_id > 0)
    {
      if (findView == null)
        visualizerView = (View)musicActivity.findViewById(visualizer_id);
      else
        visualizerView = (View)musicActivity.findViewById(visualizer_id);
      if (visualizerView == null) Log.w(TAG,"visualizerView == null");
    }
    if (back_press_id > 0)
    {
      if (findView == null)
        back_press_view = musicActivity.findViewById(back_press_id);
      else
        back_press_view = findView.findViewById(back_press_id);
      if (back_press_view == null) Log.w(TAG,"back_press_view == null");
    }
    // иконка для режима смешивания
    ico_shuffle = context.getResources().getDrawable(R.drawable.ico_shuffle);
    if (ico_shuffle_name.isEmpty())
      ico_shuffle_id = 0;
    else
      ico_shuffle_id = res.getIdentifier(ico_shuffle_name, "drawable", musicActivity.getPackageName());
    Log.d(TAG,"ico_shuffle_id="+ico_shuffle_id);
    // картинка, если нет обложки
    if (blank_cover_name.isEmpty())
      blank_cover_id = 0;
    else
      blank_cover_id = res.getIdentifier(blank_cover_name, "drawable", musicActivity.getPackageName());
    Log.d(TAG,"blank_cover_id="+blank_cover_id);
  }  

  // создание обработчика нажатия возврата на 20 сек.
  private void createBackPressListener()
  {
    if ((back_press_view != null) && (back_press_time > 0))
    {
      back_press_view.setClickable(true);
      back_press_view.setOnClickListener(backPressClick);
      Log.d(TAG,"backpress listener created");
    }
  }
  
  // возврат на некоторое время назад
  public View.OnClickListener backPressClick = new View.OnClickListener() 
  {
    public void onClick(View v) 
    {
      Log.d(TAG,"back press");
      Object mServer = XposedHelpers.getObjectField(musicActivity,"mServer");
      Log.d(TAG,"mPlayer");
      MediaPlayer mPlayer = (MediaPlayer)XposedHelpers.getObjectField(mServer,"mPlayer");
      if ((mPlayer.isPlaying()) && (back_press_time > 0))
      {
        Log.d(TAG,"playing");
        // в режиме проигрывания
        int position = XposedHelpers.getIntField(musicActivity,"currentPosition");
        int duration = XposedHelpers.getIntField(musicActivity,"currentDuration");
        //
        Log.d(TAG,"position="+position);
        Log.d(TAG,"duration="+duration);
        // перемотка назад на несколько секунд назад
        if (position > back_press_time*1000)
          position = position - back_press_time*1000;
        else
          position = 0;
        if (duration > 0)
        {
          Log.d(TAG,"set position to "+position);
          XposedHelpers.callMethod(musicActivity, "setPosition", (int)(100*position/duration));
          Log.d(TAG,"position changed OK");
          Toast.makeText(musicActivity, "возврат на "+back_press_time+" секунд", Toast.LENGTH_SHORT).show();
        }
      }
    }
  };

  // замена receiver монтирования флешки и медиа-кнопок
  private void createReceivers()
  {
    // обработчик MEDIA_MOUNT/UNMOUNT/EJECT
    usbReceiver = (BroadcastReceiver)XposedHelpers.getObjectField(musicActivity,"UsbCardBroadCastReceiver");
    if (usbReceiver != null)
  	{
      // выключаем receiver на монтирование флешки
      musicActivity.unregisterReceiver(usbReceiver);
      // заменяем его другим, который никогда не вызывается
      musicActivity.registerReceiver(usbReceiver, new IntentFilter());
      // включаем свой обработчик
      IntentFilter ui = new IntentFilter();
      // обработчик носителей media
      ui.addAction(Intent.ACTION_MEDIA_MOUNTED);
      ui.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
      ui.addAction(Intent.ACTION_MEDIA_EJECT);
      ui.addDataScheme("file");
      musicActivity.registerReceiver(mediaReceiver, ui);
      Log.d(TAG,"UsbCardBroadCastReceiver changed");
    }
    if (control_keys)
    {
      // обработчик media кнопок
      IntentFilter mi = new IntentFilter();
      mi.addAction(Intent.ACTION_MEDIA_BUTTON);
      mi.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
      musicActivity.registerReceiver(mediaButtonReceiver, mi);
      Log.d(TAG,"MediaButtonReceiver created");
      // обработчик com.android.music.musicservicecommand
      IntentFilter ci = new IntentFilter();
      ci.addAction("com.android.music.musicservicecommand");
      musicActivity.registerReceiver(commandReceiver, ci);
      Log.d(TAG,"com.android.music.* receivers created");
    }
    // обработчик com.android.music.playstatusrequest
    IntentFilter qi = new IntentFilter();
    qi.addAction("com.android.music.playstatusrequest");
    musicActivity.registerReceiver(tagsQueryReceiver, qi);
  }
  
  // посылка интента play/pause
  private void sendNotifyIntent(Context context)
  {
    Intent intent = new Intent("com.android.music.playstatechanged");
    addMp3Tags(intent);
    context.sendBroadcast(intent);
    // context.sendOrderedBroadcast(intent, null);
    Log.d(TAG,"com.android.music.playstatechanged sent");
  }
  
  // чтение и обновление mp3-тегов
  private Runnable mp3Info = new Runnable()
  {
    public void run() 
    {
      if (musicActivity == null) return;
      readMp3Infos(musicActivity, currentFileName);
      showMp3Infos();
      // отсылаем информацию о тегах
      sendNotifyIntent(musicActivity);
    }
  };
  
  // чтение тэгов mp3-файла
  private void readMp3Infos(Context context, String fileName)
  {
    // если имя файла заполнено
    if (!fileName.isEmpty())
    {
      String[] names = { fileName };
      Cursor cursor = context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, new String[] { "title", "duration", "artist", "_id", "album", "_display_name", "_data", "album_id", "_size" }, "_data=?", names, "title_key");
      // TODO: MediaStore.Audio.AudioColumns.ALBUM/TITLE/ARTIST/ALBUM_ID/TITLE_KEY/_ID
      try
      {
        if (cursor.moveToFirst())
        {
          album = cursor.getString(cursor.getColumnIndex("album"));
          Log.d(TAG,"album="+album);
          title = cursor.getString(cursor.getColumnIndex("title"));
          Log.d(TAG,"title="+title);
          artist = cursor.getString(cursor.getColumnIndex("artist"));
          if (artist.equals("<unknown>")) artist = "";
          Log.d(TAG,"artist="+artist);
          album_id = cursor.getLong(cursor.getColumnIndex("album_id"));
          Log.d(TAG,"album_id="+album_id);
          _id = cursor.getLong(cursor.getColumnIndex("_id"));
          Log.d(TAG,"_id="+_id);
          // определение картинки
          getAlbumArt();
          if (cover == null)
          {
            // обложки нет в файле
            Log.d(TAG,"bitmap notfound in tags");
            // картинка из каталога <album_cover_name>.jpg
            File f = new File(fileName);
            String coverFileName = f.getParent()+"/"+album_cover_name;
            Log.d(TAG,"cover="+coverFileName);
            // загрузить картинку
            cover = BitmapFactory.decodeFile(coverFileName);
            // если нет файла ошибки не возникает
          }
        }
      }
      catch (Exception e)
      {
        Log.e(TAG,e.getMessage());
      }
      finally
      {
        cursor.close();
      }
    }
    else
    {
      album = "";
      title = "";
      artist = "";
      album_id = -1;
      cover = null;
    }
  }
  
  // TODO: определение картинки
  private void getAlbumArt()
  {
	if (album_id > 0)
	{
      try
      {
        Uri uri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), album_id);
        cover = MediaStore.Images.Media.getBitmap(musicActivity.getContentResolver(), uri);
        if (cover != null) Log.d(TAG,"query:cover != null"); else Log.d(TAG,"query:cover == null");
        if (cover != null) return;
      }
      catch (Exception e)
      {
        cover = null;
        Log.d(TAG,"exception: cover == null");
      }
	}
	if (_id > 0)
	{
      Uri uri = Uri.parse("content://media/external/audio/media/"+_id+"/albumart");
      try
      {
        ParcelFileDescriptor fd = musicActivity.getContentResolver().openFileDescriptor(uri, "r");
        if (fd != null) 
        {
          cover = BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor());
          if (cover != null) Log.d(TAG,"descriptor:cover != null"); else Log.d(TAG,"descriptor:cover == null");
        }
      }
      catch (Exception e) {}
	}
  }
  
  // показ тегов
  private void showMp3Infos()
  {
	// title
	if (titleView != null)
	  titleView.setText(title);
	// album
	if (albumView != null)
	  albumView.setText(album);
	// artist
	if (artistView != null)
	  artistView.setText(artist);
	// cover
	if (coverView != null)
	{
	  coverView.setImageBitmap(cover);
	  if (cover != null)
      {
        coverView.setVisibility(View.VISIBLE);
        if (visualizerView != null)
          // прячем визуализатор
      	  visualizerView.setVisibility(View.INVISIBLE);
      }
      else
      {
    	if (blank_cover_id == 0)
    	{
    	  // прячем обложку
    	  Log.d(TAG,"hide image");
          coverView.setVisibility(View.INVISIBLE);
          if (visualizerView != null)
        	// показываем визуализатор
            visualizerView.setVisibility(View.VISIBLE);
    	}
    	else
        {
          // TODO: показываем картинку по-умолчанию
    	  Log.d(TAG,"set image default picture");
          coverView.setImageResource(blank_cover_id);
       }
      }
    }
  }  
  
  // теги в intent extras
  private void addMp3Tags(Intent intent)
  {
    // artist
    intent.putExtra("artist", artist);
    intent.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, artist);
    // album
    intent.putExtra("album", album);
    intent.putExtra(MediaStore.EXTRA_MEDIA_ALBUM, album);
    intent.putExtra("album_id", album_id);
    // title
    String titleTag = title;
    // показать имя файла, если нет тега
    if (title.isEmpty())
    {
      File f = new File(currentFileName);
      titleTag = f.getName();
    }
    Log.d(TAG,"titleTag="+titleTag);
    intent.putExtra("track", titleTag);
    intent.putExtra("title", titleTag);
    intent.putExtra(MediaStore.EXTRA_MEDIA_TITLE, titleTag);
    // playing
    intent.putExtra("playstate", mPlaying);
    intent.putExtra("playing", mPlaying);
  }
  
  // обработчик MEDIA_MOUNT/UNMOUNT/EJECT
  private BroadcastReceiver mediaReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      String action = intent.getAction(); 
      Log.d(TAG,"media receiver:"+action);
      if (action.equals(Intent.ACTION_MEDIA_MOUNTED))
      {
    	// не вызываем стандартный обработчик
        String file_name = "";
        // перечитываем список устройств
        XposedHelpers.callMethod(musicActivity, "Updata_DevList");
        int currentDev = XposedHelpers.getIntField(musicActivity, "currentDev");
        Object mUi = XposedHelpers.getObjectField(musicActivity, "mUi");
        XposedHelpers.callMethod(mUi, "updateDevList", currentDev);
        // подключение флешки, карты
        String drivePath = intent.getData().getPath();
        Log.d(TAG,"ACTION_MEDIA_MOUNTED: "+drivePath);
        // индекс проигрываемого файла
        int currentPlayIndex = XposedHelpers.getIntField(musicActivity, "currentPlayIndex");
        Log.d(TAG,"currentPlayIndex="+currentPlayIndex);
        if (currentPlayIndex == -1)
        {
          // список файлов пуст, загрузим сохраненный список файлов
          @SuppressWarnings("unchecked")
          ArrayList<String> list = (ArrayList<String>)XposedHelpers.callMethod(musicActivity, "loadList", "playlist");
          Log.d(TAG,"list.size="+list.size());
          if (list.size() > 0)
          {
            // первый файл в списке
            file_name = (String)list.get(0);
            Log.d(TAG,file_name);
            if (file_name.startsWith(drivePath))
            {
              Log.d(TAG,"continue play");
              // смонтирована карта на которой находился проигрываемый файл
              XposedHelpers.callMethod(musicActivity, "ReadLocalData");
              XposedHelpers.callMethod(musicActivity, "MusicOncePlay");
              return;
            }
          }
          else
          {
            // если вставлена другая флешка или нет сохраненных настроек нужно ли ее проигрывать, см. настройки
            int automedia_enable = Settings.System.getInt(context.getContentResolver(),"MusicAutoPlayEN",0);
            Log.d(TAG,"automedia_enable="+automedia_enable);
            if (automedia_enable == 0) return;
            // если 1 вызываем штатный обработчик
          }
        }
        else
        {
          // файл уже проигрывается
          return;
        }
      }
      // вызываем обработчик по умолчанию
      Log.d(TAG,"call default usbReceiver");
      usbReceiver.onReceive(context, intent);
    }
  };
  
  // обработчик android.intent.action.MEDIA_BUTTON
  private BroadcastReceiver mediaButtonReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      KeyEvent event = (KeyEvent)intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
      int keyCode = event.getKeyCode();
      Log.d(TAG,"android.intent.action.MEDIA_BUTTON: "+keyCode);
      if (event.getAction() == KeyEvent.ACTION_DOWN)
      {
    	// срабатывает нажатие кнопки
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
          XposedHelpers.callMethod(musicActivity, "cmdPlayPause");
        else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY)
        {
          if (mPlaying == false) XposedHelpers.callMethod(musicActivity, "cmdPlayPause");
        }
        else if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE)
        {
          if (mPlaying == true) XposedHelpers.callMethod(musicActivity, "cmdPlayPause");
        }
        else if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT)
          XposedHelpers.callMethod(musicActivity, "cmdNext");
        else if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS)
          XposedHelpers.callMethod(musicActivity, "cmdPrev");
      }
      abortBroadcast();
    }
  };
  
  // обработчик com.android.music.playstatusrequest
  private BroadcastReceiver tagsQueryReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      // отправить mp3-теги
      Log.d(TAG,"Music: tags query receiver");
      sendNotifyIntent(context);
    }
  };
  
  // обработчик com.android.music.musicservicecommand
  private BroadcastReceiver commandReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      String cmd = intent.getStringExtra("command");
      Object mServer = XposedHelpers.getObjectField(musicActivity, "mServer");
      int mPlaying = (int)XposedHelpers.callMethod(mServer, "getplaystate");
      if (cmd.equals("previous"))
        XposedHelpers.callMethod(mServer, "sPrev");
      else if (cmd.equals("next"))
        XposedHelpers.callMethod(mServer, "sNext");
      else if (cmd.equals("play"))
        if (mPlaying == 2) XposedHelpers.callMethod(mServer, "sPlayPause");
      else if (cmd.equals("pause"))
        if (mPlaying == 1) XposedHelpers.callMethod(mServer, "sPlayPause");
      else if (cmd.equals("stop"))
        XposedHelpers.callMethod(mServer, "sStop");
      else if (cmd.equals("toggleplay"))
        XposedHelpers.callMethod(mServer, "sPlayPause");
      else
        Log.w(TAG,"unknown command: "+cmd);
    }
  };

}
