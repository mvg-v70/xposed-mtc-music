package com.mvgv70.xposed_mtc_music;

import java.io.File;
import java.util.ArrayList;

import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.mp3.MP3File;

import com.mvgv70.utils.IniFile;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;

import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.datatype.Artwork;

import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
  private final static String INI_FILE_SHORT = "settings.ini";
  private final static String INI_FILE_NAME = "/mnt/external_sd/mtc-music/"+INI_FILE_SHORT;
  private final static String USER_SETTINGS_NAME = "/mnt/external_sd/mtc-music/mtc-music.ini";
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
  private static Toast toast = null;
  private static boolean assetProps = false;
  // настройки
  private static boolean active_flag = false;
  private static boolean ss_flag = false;
  private static boolean toastEnable = false;
  private static int toastSize = 0;
  // private static Handler handler;
  // режимы loop
  private final static int LOOP_MODE_NEXT_DIR = 0;
  private final static int LOOP_MODE_SINGLE_FILE = 1;
  private final static int LOOP_MODE_ALBUM = 2;
  private final static int LOOP_MODE_SHUFFLE = 3;
  private static int loop_mode;
  // mp3-tags
  private static String currentFileName = "";
  private static String shortFileName = "";
  private static String album = "";
  private static String title = "";
  private static String artist = "";
  private static Bitmap cover = null;
  // параметры в mtc-music.ini
  private static String back_press_name = "";
  private static int back_press_time = 0;
  private static String fwd_press_name = "";
  private static int fwd_press_time = 0;
  private static String eq_button_name = "";
  private static Boolean control_keys = false;
  private static String album_cover_name = "";
  private static String music_list_name = "music_list";
  private static String title_name = "";
  private static String album_name = "";
  private static String artist_name = "";
  private static String cover_name = "";
  private static String visualizer_name = "";
  private static String ico_shuffle_name = "";
  private static String findview_by_name = "";
  private static String blank_cover_name = "";
  private static String background_image_name = "";
  private static float background_alpha = 1;
  private static String toast_format = "%title%";
  // элементы
  private static View visualizerView = null;
  private static View back_press_view = null;
  private static View fwd_press_view = null;
  private static Button eq_button_view = null;
  private static int ico_shuffle_id;
  private static int blank_cover_id;
  // теги
  private static TextView titleView = null;
  private static TextView artistView = null;
  private static TextView albumView = null;
  private static ImageView coverView = null;
  private static ImageView backgroundView = null;
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
        // handler = (Handler)XposedHelpers.getObjectField(musicActivity, "handler");
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
        createBackFwdPressListener();
        // если выбран режим перемешивания нарисуем картинку
        if (loop_mode == LOOP_MODE_SHUFFLE)
          XposedHelpers.callMethod(mUi, "updateRepeat", loop_mode);
      }
    };
    
    // MusicActivity.onStop()
    XC_MethodHook onStop = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    	active_flag = false;
      }
    };
    
    // MusicActivity.onResume()
    XC_MethodHook onResume = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        ss_flag = false;
    	active_flag = true;
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
        musicActivity.unregisterReceiver(endClockReceiver);
        musicActivity = null;
        toast = null;
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
      	// чтение mp3-тегов
        readMp3Infos(currentFileName);
        showMp3Infos();
        // отсылаем информацию о тегах
        sendNotifyIntent(musicActivity);
        // всплывающее уведомление
        if (toastEnable && !active_flag && !ss_flag)
        {
          if (toast != null)
          {
            toast.cancel();
            toast = null;
          }
          toast = Toast.makeText(musicActivity, getToastText(), Toast.LENGTH_SHORT);
          if (toastSize > 0)
          {
            // toast size
            ViewGroup group = (ViewGroup)toast.getView();
            TextView messageTextView = (TextView)group.getChildAt(0);
            messageTextView.setTextSize(toastSize);
          }
          toast.show();
        }
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
    
    // MusicServer.sNext()
    XC_MethodHook sNext = new XC_MethodHook() {
        
      @Override
      protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        int LoopMode = XposedHelpers.getIntField(param.thisObject, "LoopMode");
        Log.d(TAG,"sNext.LoopMode="+LoopMode+" ("+loop_mode+")");
        // в режиме следующего каталога
        if (LoopMode == LOOP_MODE_NEXT_DIR)
        {
          int sel_item = XposedHelpers.getIntField(param.thisObject, "selItem");
          @SuppressWarnings("unchecked")
          ArrayList<String> music_list = (ArrayList<String>)XposedHelpers.getObjectField(musicActivity, music_list_name);
          Log.d(TAG,"sel_item="+sel_item+", music_list.size="+music_list.size());
          if (sel_item == (music_list.size()-1))
          {
            // играем следующий каталог
        	@SuppressWarnings("unchecked")
            ArrayList<String> folder_list = (ArrayList<String>)XposedHelpers.getObjectField(musicActivity, "folderList");
            File file = new File(currentFileName,"");
            String dir = file.getParent();
            int index = folder_list.indexOf(dir);
            Log.d(TAG,"dir index="+index);
            if ((index >= 0) && (index < (folder_list.size()-1)))
            {
              // играем следующую папку
              XposedHelpers.callMethod(musicActivity, "toFolder", index+1);
            }
            // не вызываем штатный обработчик
            param.setResult(null);
          }
        }
      }
    };
    
    // MusicServer.sPrev()
    XC_MethodHook sPrev = new XC_MethodHook() {
        
      @Override
      protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        int LoopMode = XposedHelpers.getIntField(param.thisObject, "LoopMode");
        Log.d(TAG,"sPrev.LoopMode="+LoopMode+" ("+loop_mode+")");
        // в режиме следующего каталога
        if (LoopMode == LOOP_MODE_NEXT_DIR)
        {
          int sel_item = XposedHelpers.getIntField(param.thisObject, "selItem");
          @SuppressWarnings("unchecked")
          ArrayList<String> music_list = (ArrayList<String>)XposedHelpers.getObjectField(musicActivity, music_list_name);
          Log.d(TAG,"sel_item="+sel_item+", music_list.size="+music_list.size());
          if ((sel_item == 0) && (music_list.size() > 0))
          {
            // играем предыдующий каталог
            @SuppressWarnings("unchecked")
            ArrayList<String> folder_list = (ArrayList<String>)XposedHelpers.getObjectField(musicActivity, "folderList");
            File file = new File(currentFileName,"");
            String dir = file.getParent();
            int index = folder_list.indexOf(dir);
            Log.d(TAG,"dir index="+index);
            if ((index > 0) && (folder_list.size() > 0))
            {
              // играем предыдующую папку
              XposedHelpers.callMethod(musicActivity, "toFolder", index-1);
              // TODO: перейти на последний трек
            }
            // не вызываем штатный обработчик
            param.setResult(null);
          }
        }
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
    
    // Ui*.updateEq(int)
    XC_MethodHook updateEq = new XC_MethodHook() {
        
      @Override
      protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        if (eq_button_view != null) param.setResult(null);
      }
    };
    
    // XDA: Ui*.updateImageView(Bitmap)
    XC_MethodHook updateImageView = new XC_MethodHook() {
        
      @Override
      protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    	// если есть assets не выполняем функцию
        if (assetProps) param.setResult(null);
      }
    };
    
    // start hooks
    if (!lpparam.packageName.equals("com.microntek.music")) return;
    Log.d(TAG,"com.microntek.music");
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicActivity", lpparam.classLoader, "onCreate", Bundle.class, onCreate);
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicActivity", lpparam.classLoader, "onStop", onStop);
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicActivity", lpparam.classLoader, "onResume", onResume);
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicActivity", lpparam.classLoader, "onDestroy", onDestroy);
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicActivity", lpparam.classLoader, "updataMp3info", updataMp3info);
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicActivity", lpparam.classLoader, "onState", int.class, onState);
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicActivity", lpparam.classLoader, "cmdLoop", cmdLoop);
    // ищем используемый Ui
    for (int i=1; i<=5; i++)
    {
      try
      {
        XposedHelpers.findAndHookMethod("com.microntek.music.ui.Ui"+i, lpparam.classLoader, "updateRepeat", int.class, updateRepeat);
        XposedHelpers.findAndHookMethod("com.microntek.music.ui.Ui"+i, lpparam.classLoader, "updateEq", int.class, updateEq);
        Log.d(TAG,"Ui"+i+" detected...");
        // для плеера с XDA 
        try
        {
          XposedHelpers.findAndHookMethod("com.microntek.music.ui.Ui"+i, lpparam.classLoader, "updateImageView", Bitmap.class, updateImageView);
        }
        catch (Error e) {}
        break;
      }
      catch (Error e) {}
    }
    XposedHelpers.findAndHookMethod("android.view.SurfaceView", lpparam.classLoader, "setVisibility", int.class, setVisuVisibility);
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicServer", lpparam.classLoader, "onCompletion", MediaPlayer.class, onCompletion);
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicServer", lpparam.classLoader, "sNext", sNext);
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicServer", lpparam.classLoader, "sPrev", sPrev);
    Log.d(TAG,"com.microntek.music hook OK");
  }

  // чтение глобальных настроек из mtc-music.ini
  private void readSettings()
  {
    assetProps = false;
    try
    {
      // читаем настроечный файл из assests
      try
      {
    	Log.d(TAG,"read settings from assets/"+INI_FILE_SHORT);
        props.loadFromAssets(musicActivity, INI_FILE_SHORT);
        assetProps = true;
      }
      catch (Exception e)
      {
    	// если нет assests читаем файл
        Log.d(TAG,"read settings from "+INI_FILE_NAME);
        props.loadFromFile(INI_FILE_NAME);
      }
      // имя переменной music_list
      music_list_name = props.getValue(MUSIC_SECTION, "music_list", "music_list");
      Log.d(TAG,"music_list="+music_list_name);
      // control_keys
      control_keys = props.getBoolValue(MUSIC_SECTION, "control_keys", false);
      Log.d(TAG,"control_keys="+control_keys);
      // backpress
      back_press_name = props.getValue(MUSIC_SECTION, "backpress.name");
      back_press_time = props.getIntValue(MUSIC_SECTION, "backpress.time",20);
      Log.d(TAG,"back_press_name="+back_press_name+", back_press_time="+back_press_time);
      // forwardpress
      fwd_press_name = props.getValue(MUSIC_SECTION, "fwdpress.name");
      fwd_press_time = props.getIntValue(MUSIC_SECTION, "fwdpress.time",20);
      Log.d(TAG,"fwd_press_name="+fwd_press_name+", fwd_press_time="+fwd_press_time);
      // visualizer_name
      visualizer_name = props.getValue(MUSIC_SECTION, "visualizer");
      Log.d(TAG,"visualizer_name="+visualizer_name);
      // album_cover_name
      album_cover_name = props.getValue(MUSIC_SECTION, "album_cover");
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
      // background_image_name
      background_image_name = props.getValue(MUSIC_SECTION, "background_image", "");
      Log.d(TAG,"background_image_name="+background_image_name);
      // background_alpha
      background_alpha = props.getFloatValue(MUSIC_SECTION, "background_alpha", 0.25f);
      Log.d(TAG,"background_alpha="+background_alpha);
      // кнопка эквалайзера
      eq_button_name = props.getValue(MUSIC_SECTION, "eq_button", "");
      Log.d(TAG,"eq_button_name="+eq_button_name);
      // ico_shuffle_name
      ico_shuffle_name = props.getValue(MUSIC_SECTION, "ico_shuffle", "");
      Log.d(TAG,"ico_shuffle_name="+ico_shuffle_name);
      // findview_by_name
      findview_by_name = props.getValue(MUSIC_SECTION, "findview_by", "");
      Log.d(TAG,"findview_by_name="+findview_by_name);
      // blank_cover_name
      blank_cover_name = props.getValue(MUSIC_SECTION, "blank_cover", "");
      Log.d(TAG,"blank_cover_name="+blank_cover_name);
      // toast
      toastEnable = props.getBoolValue(MUSIC_SECTION, "toast", false);
      Log.d(TAG,"toastEnable="+toastEnable);
      // toast.size
      toastSize = props.getIntValue(MUSIC_SECTION, "toast.size", 0);
      Log.d(TAG,"toast.size="+toastSize);
      // toast.format
      toast_format = props.getValue(MUSIC_SECTION, "toast.format", "%title%");
      Log.d(TAG,"toast.format="+toast_format);
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
      // control_keys
      control_keys = user_props.getBoolValue(MUSIC_SECTION, "control_keys", control_keys);
      Log.d(TAG,"control_keys="+control_keys);
      // backpress
      back_press_time = user_props.getIntValue(MUSIC_SECTION, "backpress.time",back_press_time);
      Log.d(TAG,"back_press_name="+back_press_name+", back_press_time="+back_press_time);
      // forwardpress
      fwd_press_time = props.getIntValue(MUSIC_SECTION, "fwdpress.time",fwd_press_time);
      Log.d(TAG,"fwd_press_name="+fwd_press_name+", fwd_press_time="+fwd_press_time);
      // album_cover_name
      album_cover_name = user_props.getValue(MUSIC_SECTION, "album_cover", album_cover_name);
      Log.d(TAG,"album_cover_name="+album_cover_name);
      // toast
      toastEnable = user_props.getBoolValue(MUSIC_SECTION, "toast", toastEnable);
      Log.d(TAG,"toastEnable="+toastEnable);
      // toast.size
      toastSize = user_props.getIntValue(MUSIC_SECTION, "toast.size", toastSize);
      Log.d(TAG,"toast.size="+toastSize);
      // toast.format
      toast_format = user_props.getValue(MUSIC_SECTION, "toast.format", toast_format);
      Log.d(TAG,"toast.format="+toast_format);
      // background_alpha
      background_alpha = user_props.getFloatValue(MUSIC_SECTION, "background_alpha", background_alpha);
      Log.d(TAG,"background_alpha="+background_alpha);
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
    int background_id = 0;
    int visualizer_id = 0;
    int back_press_id = 0;
    int fwd_press_id = 0;
    int eq_button_id = 0;
    Resources res = musicActivity.getResources();
    if (!title_name.isEmpty())
      title_id = res.getIdentifier(title_name, "id", musicActivity.getPackageName());
    if (!album_name.isEmpty())
      album_id = res.getIdentifier(album_name, "id", musicActivity.getPackageName());
    if (!artist_name.isEmpty())
      artist_id = res.getIdentifier(artist_name, "id", musicActivity.getPackageName());
    if (!cover_name.isEmpty())
      cover_id = res.getIdentifier(cover_name, "id", musicActivity.getPackageName());
    if (!background_image_name.isEmpty())
      background_id = res.getIdentifier(background_image_name, "id", musicActivity.getPackageName());
    if (!visualizer_name.isEmpty())
      visualizer_id = res.getIdentifier(visualizer_name, "id", musicActivity.getPackageName());
    if (!back_press_name.isEmpty())
      back_press_id = res.getIdentifier(back_press_name, "id", musicActivity.getPackageName());
    if (!fwd_press_name.isEmpty())
      fwd_press_id = res.getIdentifier(fwd_press_name, "id", musicActivity.getPackageName());
    if (!eq_button_name.isEmpty())
      eq_button_id = res.getIdentifier(eq_button_name, "id", musicActivity.getPackageName());
    // debug
    Log.d(TAG,"title_id="+title_id);
    Log.d(TAG,"album_id="+album_id);
    Log.d(TAG,"artist_id="+artist_id);
    Log.d(TAG,"cover_id="+cover_id);
    Log.d(TAG,"background_id="+background_id);
    Log.d(TAG,"visualizer_id="+visualizer_id);
    Log.d(TAG,"back_press_id="+back_press_id);
    Log.d(TAG,"fwd_press_id="+fwd_press_id);
    Log.d(TAG,"eq_button_id="+eq_button_id);
    // views
    titleView = null;
    albumView = null;
    artistView = null;
    coverView = null;
    backgroundView = null;
    visualizerView = null;
    eq_button_view = null;
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
    // cover
    if (cover_id > 0)
    {
      if (findView == null)
        coverView = (ImageView)musicActivity.findViewById(cover_id);
      else
        coverView = (ImageView)findView.findViewById(cover_id);
      if (coverView == null) Log.w(TAG,"coverView == null");
    }
    // background
    if (background_id > 0)
    {
      if (findView == null)
        backgroundView = (ImageView)musicActivity.findViewById(background_id);
      else
        backgroundView = (ImageView)findView.findViewById(background_id);
      if (backgroundView == null) Log.w(TAG,"backgroundView == null");
    }
    // visualizer
    if (visualizer_id > 0)
    {
      if (findView == null)
        visualizerView = (View)musicActivity.findViewById(visualizer_id);
      else
        visualizerView = (View)musicActivity.findViewById(visualizer_id);
      if (visualizerView == null) Log.w(TAG,"visualizerView == null");
    }
    // back
    if (back_press_id > 0)
    {
      if (findView == null)
        back_press_view = musicActivity.findViewById(back_press_id);
      else
        back_press_view = findView.findViewById(back_press_id);
      if (back_press_view == null) Log.w(TAG,"back_press_view == null");
    }
    // forward
    if (fwd_press_id > 0)
    {
      if (findView == null)
        fwd_press_view = musicActivity.findViewById(fwd_press_id);
      else
        fwd_press_view = findView.findViewById(fwd_press_id);
      if (fwd_press_view == null) Log.w(TAG,"fwd_press_view == null");
    }
    // eq button
    if (eq_button_id > 0)
    {
      if (findView == null)
        eq_button_view = (Button)musicActivity.findViewById(eq_button_id);
      else
        eq_button_view = (Button)findView.findViewById(eq_button_id);
      if (eq_button_view == null) Log.w(TAG,"eq_button_view == null");
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

  // создание обработчика нажатия вперед/назад на 20 сек.
  private void createBackFwdPressListener()
  {
	// back
    if ((back_press_view != null) && (back_press_time > 0))
    {
      back_press_view.setClickable(true);
      back_press_view.setOnClickListener(backPressClick);
      Log.d(TAG,"back press listener created");
    }
    // forward
    if ((fwd_press_view != null) && (fwd_press_time > 0))
    {
      fwd_press_view.setClickable(true);
      fwd_press_view.setOnClickListener(fwdPressClick);
      Log.d(TAG,"forward press listener created");
    }
    // eq button
    if (eq_button_view != null)
    {
      eq_button_view.setText("EQ");
      eq_button_view.setOnClickListener(eqPressClick);
      Log.d(TAG,"equalizer call listener created");
    }
  }
  
  // возврат на некоторое время назад
  public View.OnClickListener backPressClick = new View.OnClickListener() 
  {
    public void onClick(View v) 
    {
      Log.d(TAG,"back press");
      Object mServer = XposedHelpers.getObjectField(musicActivity,"mServer");
      MediaPlayer mPlayer = (MediaPlayer)XposedHelpers.getObjectField(mServer,"mPlayer");
      if ((mPlayer.isPlaying()) && (back_press_time > 0))
      {
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
          // переход в начало
          position = 0;
        if (position >= 0)
        {
          Log.d(TAG,"set position to "+position);
          XposedHelpers.callMethod(musicActivity, "setPosition", (int)(100*position/duration));
          Log.d(TAG,"position changed OK");
          Toast.makeText(musicActivity, "возврат на "+back_press_time+" секунд", Toast.LENGTH_SHORT).show();
        }
      }
    }
  };
  
  // переход на некоторое время назад
  public View.OnClickListener fwdPressClick = new View.OnClickListener() 
  {
    public void onClick(View v) 
    {
      Log.d(TAG,"forward press");
      Object mServer = XposedHelpers.getObjectField(musicActivity,"mServer");
      MediaPlayer mPlayer = (MediaPlayer)XposedHelpers.getObjectField(mServer,"mPlayer");
      if ((mPlayer.isPlaying()) && (fwd_press_time > 0))
      {
        // в режиме проигрывания
        int position = XposedHelpers.getIntField(musicActivity,"currentPosition");
        int duration = XposedHelpers.getIntField(musicActivity,"currentDuration");
        //
        Log.d(TAG,"position="+position);
        Log.d(TAG,"duration="+duration);
        // перемотка назад на несколько секунд назад
        if (duration > (position+fwd_press_time*1000))
          position = position + fwd_press_time*1000;
        else
          // переход в конец
          position = duration-1;
        if (position <= duration)
        {
          Log.d(TAG,"set position to "+position);
          XposedHelpers.callMethod(musicActivity, "setPosition", (int)(100*position/duration));
          Log.d(TAG,"position changed OK");
          Toast.makeText(musicActivity, "переход на "+back_press_time+" секунд", Toast.LENGTH_SHORT).show();
        }
      }
    }
  };
  
  // вызов эквалайзера
  public View.OnClickListener eqPressClick = new View.OnClickListener() 
  {
    public void onClick(View v) 
    {
      Log.d(TAG,"call equalizer");
      Intent intent = new Intent(Intent.ACTION_MAIN);
      intent.setClassName("com.android.settings","com.android.settings.MtcAmpSetup");
      musicActivity.startActivity(intent);
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
    // обработчик закрытия screen saver
    IntentFilter si = new IntentFilter();
    si.addAction("com.microntek.musicclockreset");
    musicActivity.registerReceiver(endClockReceiver, si);
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
  
  // чтение тэгов mp3-файла
  private void readMp3Infos(String fileName)
  {
    album = "";
    title = "";
    artist = "";
    cover = null;
    Tag tags = null;
    if (fileName.isEmpty()) return;
    // пользуемся библиотекой jAudioTagger http://www.jthink.net/jaudiotagger/
    try
    {
      try
      {
        MP3File mp3file = (MP3File)AudioFileIO.read(new File(fileName));
        tags = mp3file.getTag();
        album = tags.getFirst(FieldKey.ALBUM);
        title = tags.getFirst(FieldKey.TITLE);
        artist = tags.getFirst(FieldKey.ARTIST);
      }
      catch (Exception e) { }
      // разберем имя файла
      String file = "";
      String folder = "";
      String dirs[] = currentFileName.split("\\s*/\\s*");
      if (dirs.length > 0)
      {
        Log.d(TAG,"dirs.length="+dirs.length);
        file = dirs[dirs.length-1];
        shortFileName = file;
        // уберем расширение
        int lastPointPos = file.lastIndexOf('.');
        if (lastPointPos > 0)
          file = file.substring(0, lastPointPos);
      }
      if (dirs.length > 1) folder = dirs[dirs.length-2];
      // если тег не задан возьмем имя папки
      if (album.isEmpty()) album = folder;
      // если тег не задан возьмем имя файла
      if (title.isEmpty()) title = file;
      // определение картинки
      cover = getArtWork(tags, fileName, album_cover_name);
      //
      Log.d(TAG,"album="+album);
      Log.d(TAG,"title="+title);
      Log.d(TAG,"artist="+artist);
    }
    catch (Exception e)
    {
      Log.e(TAG,"exception: "+e.getMessage());
    }
  }
  
  // картинка из файла или из каталога
  public static Bitmap getArtWork(Tag tags, String fileName, String coverName)
  {
    Bitmap result = null;
    if (tags != null)
    {
      try
      {
        Artwork artwork = tags.getFirstArtwork();
        if (artwork != null)
        {
          byte[] cover_data = artwork.getBinaryData();
          result = BitmapFactory.decodeByteArray(cover_data, 0, cover_data.length);
        }
      }
      catch (Exception e)
      {
        Log.e(TAG,"BitmapFactory: "+e.getMessage());
     }
    }
    if (result == null)
    {
      // обложки нет в файле, возьмем картинку album_cover_name из каталога 
      File f = new File(fileName);
      String coverFileName = f.getParent()+"/"+coverName;
      Log.d(TAG,"cover="+coverFileName);
      // загрузить картинку
      result = BitmapFactory.decodeFile(coverFileName);
      // если нет файла ошибки не возникает
    }
    return result;
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
    if (backgroundView != null)
    {
      backgroundView.setImageBitmap(cover);
      if (cover != null)
      {
        backgroundView.setVisibility(View.VISIBLE);
        backgroundView.setAlpha(background_alpha);
      }
      else
        backgroundView.setVisibility(View.INVISIBLE);
    }
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
          coverView.setVisibility(View.INVISIBLE);
          if (visualizerView != null)
        	// показываем визуализатор
            visualizerView.setVisibility(View.VISIBLE);
    	}
    	else
          coverView.setImageResource(blank_cover_id);
      }
    }
  }  
  
  // теги в intent extras
  private static void addMp3Tags(Intent intent)
  {
    // artist
    intent.putExtra("artist", artist);
    intent.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, artist);
    // album
    intent.putExtra("album", album);
    intent.putExtra(MediaStore.EXTRA_MEDIA_ALBUM, album);
    // title
    intent.putExtra("track", title);
    intent.putExtra("title", title);
    intent.putExtra(MediaStore.EXTRA_MEDIA_TITLE, title);
    // playing
    intent.putExtra("playstate", mPlaying);
    intent.putExtra("playing", mPlaying);
    // filename
    intent.putExtra("filename", currentFileName);
  }
  
  // форматирование строки для всплывающего уведомления
  private static String getToastText()
  {
	String result = toast_format;
	result = result.replaceAll("%title%", title);
	result = result.replaceAll("%album%", album);
	result = result.replaceAll("%artist%", artist);
	result = result.replaceAll("%fullfilename%", currentFileName);
	result = result.replaceAll("%filename%", shortFileName);
	return result;
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
      ss_flag = true;
      sendNotifyIntent(context);
    }
  };
 
  // обработчик выключения Screen Saver
  private BroadcastReceiver endClockReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      Log.d(TAG,"Music: end clock receiver");
      ss_flag = false;
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
