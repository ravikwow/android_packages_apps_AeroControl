package com.aero.control.fragments;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.aero.control.AeroActivity;
import com.aero.control.R;
import com.aero.control.helpers.CustomTextPreference;
import com.espian.showcaseview.ShowcaseView;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by Alexander Christ on 16.09.13.
 * Default Memory Fragment
 */
public class MemoryFragment extends PreferenceFragment {

    public static final String GOV_IO_FILE = "/sys/block/mmcblk0/queue/scheduler";
    public static final String SWAPPNIESS_FILE = "/proc/sys/vm/swappiness";
    public static final String DYANMIC_FSYNC = "/sys/kernel/dyn_fsync/Dyn_fsync_active";
    public static final String CMDLINE_ZACHE = "/system/bootmenu/2nd-boot/cmdline";
    public static final String WRITEBACK = "/sys/devices/virtual/misc/writeback/writeback_enabled";
    public static final String MIN_FREE = "/proc/sys/vm/extra_free_kbytes";
    public static final String FILENAME = "firstrun_trim";
    public static final String FILENAME_HIDDEN = "firstrun_hidden_feature";
    public static final String GOV_IO_PARAMETER = "/sys/devices/platform/mmci-omap-hs.0/mmc_host/mmc0/mmc0:1234/block/mmcblk0/queue/iosched/";

    public ShowcaseView.ConfigOptions mConfigOptions;
    public ShowcaseView mShowCase;
    public PreferenceCategory PrefCat;
    public PreferenceScreen root;

    public boolean showDialog = true;

    public boolean checkDynFsync;
    public boolean checkDynWriteback;

    public static final Handler progressHandler = new Handler();

    private CheckBoxPreference mDynFSync, mZCache, mLowMemoryPref, mWriteBackControl;

    private static final String MEMORY_SETTINGS_CATEGORY = "memory_settings";
    private static final String FORCE_HIGHEND_GFX_PERSIST_PROP = "persist.sys.force_highendgfx";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.layout.memory_fragment);


        root = this.getPreferenceScreen();
        final PreferenceCategory memorySettingsCategory =
                (PreferenceCategory) findPreference(MEMORY_SETTINGS_CATEGORY);
        // I don't like the following, can we simplify it?

        // Declare our entries;
        final EditTextPreference swappiness = (EditTextPreference)root.findPreference("swappiness");
        final EditTextPreference min_free_ram = (EditTextPreference)root.findPreference("min_free");
        mDynFSync = (CheckBoxPreference)findPreference("dynFsync");
        mZCache = (CheckBoxPreference)findPreference("zcache");
        mWriteBackControl = (CheckBoxPreference)findPreference("writeback");
        mLowMemoryPref = (CheckBoxPreference)findPreference("low_mem");

        // Swappiness:
        swappiness.setText(AeroActivity.shell.getInfo(SWAPPNIESS_FILE));
        // Only show numbers in input field;
        swappiness.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);

        // Only show numbers in input field;
        min_free_ram.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);

        if (AeroActivity.shell.getInfo(CMDLINE_ZACHE).equals("Unavailable"))
            memorySettingsCategory.removePreference(mZCache);

        // Min free ram:
        if (AeroActivity.shell.getInfo(MIN_FREE).equals("Unavailable")) {
            min_free_ram.setEnabled(false);

            // Remove until back in Kernel;
            PreferenceCategory memoryCategory = (PreferenceCategory) findPreference("memory_settings");
            memoryCategory.removePreference(min_free_ram);
        } else
            min_free_ram.setText(AeroActivity.shell.getInfo(MIN_FREE));


        // Check if enabled or not;
        if (AeroActivity.shell.getInfo(DYANMIC_FSYNC).equals("1")) {
            checkDynFsync = true;
        } else if (AeroActivity.shell.getInfo(DYANMIC_FSYNC).equals("0")) {
            checkDynFsync = false;
        } else {
            // If dyn fsync is not supported
            memorySettingsCategory.removePreference(mDynFSync);
        }

        mDynFSync.setChecked(checkDynFsync);

        final String fileCMD = AeroActivity.shell.getInfo(CMDLINE_ZACHE);
        final boolean zcacheEnabled = fileCMD.length() == 0 ? false : fileCMD.contains("zcache");
        mZCache.setChecked(zcacheEnabled);

        // Check if enabled or not;
        if (AeroActivity.shell.getInfo(WRITEBACK).equals("1")) {
            checkDynWriteback = true;
        }
        else if (AeroActivity.shell.getInfo(WRITEBACK).equals("0")) {
            checkDynWriteback = false;
        }
        else {
            // If dyn writeback is not supported
            memorySettingsCategory.removePreference(mWriteBackControl);
        }
        mWriteBackControl.setChecked(checkDynWriteback);

        if(android.os.Build.VERSION.SDK_INT >= 19) {
            mLowMemoryPref = (CheckBoxPreference)root.findPreference("low_mem");
            String forceHighendGfx = SystemProperties.get(FORCE_HIGHEND_GFX_PERSIST_PROP, "false");
            mLowMemoryPref.setChecked(!"true".equals(forceHighendGfx));
        } else {
            memorySettingsCategory.removePreference(findPreference("low_mem"));
        }

        if (showDialog) {
            // Ensure only devices with this special path are checked;
            final String fileMount[] = AeroActivity.shell.getInfo("/proc/mounts", false);
            boolean fileMountCheck = false;

            for (String tmp : fileMount) {
                if(tmp.contains("/dev/block/mmcblk1p25")) {
                    fileMountCheck = true;
                    break;
                }
            }

            showDialog = false;

            if (fileMountCheck) {
                final String fileJournal = AeroActivity.shell.getRootInfo("tune2fs -l", "/dev/block/mmcblk1p25");
                final boolean fileSystemCheck = fileJournal.length() == 0 ? false : fileJournal.contains("has_journal");
                if (!fileSystemCheck){

                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    LayoutInflater inflater = getActivity().getLayoutInflater();
                    // Just reuse aboutScreen, because its Linear and has a TextView
                    View layout = inflater.inflate(R.layout.about_screen, null);
                    TextView aboutText = (TextView) layout.findViewById(R.id.aboutScreen);

                    builder.setTitle(R.string.has_journal_dialog_header);
                    aboutText.setText(getText(R.string.has_journal_dialog));
                    aboutText.setTextSize(13);

                    builder.setView(layout)
                            .setPositiveButton(R.string.got_it, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                }
                            })
                            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                }
                            });
                    builder.show();
                }
            }
        }

        // Find our ListPreference (max_frequency);
        final ListPreference io_scheduler = (ListPreference) root.findPreference("io_scheduler_list");
        // Just throw in our frequencies;
        io_scheduler.setEntries(AeroActivity.shell.getInfoArray(GOV_IO_FILE, 0, 1));
        io_scheduler.setEntryValues(AeroActivity.shell.getInfoArray(GOV_IO_FILE, 0, 1));
        io_scheduler.setValue(AeroActivity.shell.getInfoString(AeroActivity.shell.getInfo(GOV_IO_FILE)));
        io_scheduler.setSummary(AeroActivity.shell.getInfoString(AeroActivity.shell.getInfo(GOV_IO_FILE)));
        io_scheduler.setDialogIcon(R.drawable.memory_dark);

        final Preference fstrim_toggle = root.findPreference("fstrim_toggle");


        fstrim_toggle.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {

                final CharSequence[] system = {"/system", "/data", "/cache"};

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                final ProgressDialog update = new ProgressDialog(getActivity());
                builder.setTitle(R.string.fstrim_header);
                builder.setIcon(R.drawable.gear_dark);

                builder.setItems(system, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {

                        final String b = (String)system[item];

                        update.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                        update.setCancelable(false);
                        update.setMax(100);
                        update.setIndeterminate(true);
                        update.show();
                        AeroActivity.shell.remountSystem();

                        Runnable runnable = new Runnable() {
                            @Override
                            public void run() {
                                try {

                                    while (update.getProgress()< 100) {

                                        // Set up the root-command;
                                        AeroActivity.shell.getRootInfo("fstrim -v", b);

                                        update.setIndeterminate(false);
                                        update.setProgress(100);

                                        progressHandler.sendMessage(progressHandler.obtainMessage());

                                        // Sleep the current thread and exit dialog;
                                        Thread.sleep(2000);
                                        update.dismiss();

                                    }

                                } catch (Exception e) {
                                    Log.e("Aero", "An error occurred while trimming.", e);
                                }
                            }
                        };
                        Thread trimThread = new Thread(runnable);
                        if (!trimThread.isAlive())
                            trimThread.start();
                    }
                }).show();

                return true;
            };

        });


        // Start our custom change listener;
        io_scheduler.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {

                String a = (String) o;

                AeroActivity.shell.setRootInfo(a, GOV_IO_FILE);
                io_scheduler.setSummary(a);

                loadIOParameter();

                //** store preferences
                preference.getEditor().commit();

                return true;
            };
        });

        swappiness.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {

                String a = (String) o;


                AeroActivity.shell.setRootInfo(a, SWAPPNIESS_FILE);
                swappiness.setText(AeroActivity.shell.getInfo(SWAPPNIESS_FILE));

                //** store preferences
                preference.getEditor().commit();

                return true;
            };
        });

        min_free_ram.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {

                String a = (String) o;

                AeroActivity.shell.setRootInfo(a, MIN_FREE);
                min_free_ram.setText(AeroActivity.shell.getInfo(MIN_FREE));

                //** store preferences
                preference.getEditor().commit();

                return true;
            };
        });
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {

        super.onActivityCreated(savedInstanceState);
        // Prepare Showcase;
        mConfigOptions = new ShowcaseView.ConfigOptions();
        mConfigOptions.hideOnClickOutside = false;
        mConfigOptions.shotType = ShowcaseView.TYPE_ONE_SHOT;

        // Set up our file;
        int output = 0;
        final byte[] buffer = new byte[1024];

        try {
            FileInputStream fis = getActivity().openFileInput(FILENAME);
            output = fis.read(buffer);
            fis.close();
        } catch (IOException e) {
            Log.e("Aero", "Couldn't open File... " + output);
        }

        // Only show showcase once;
        if (output == 0)
            DrawFirstStart(R.string.showcase_memory_fragment_trim, R.string.showcase_memory_fragment_trim_sum, FILENAME);

    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if(preference == mLowMemoryPref) {
            boolean value = !mLowMemoryPref.isChecked();
            boolean forceHighendGfx = SystemProperties.getBoolean(FORCE_HIGHEND_GFX_PERSIST_PROP, false);
            if (forceHighendGfx == value) return true;
            SystemProperties.set(FORCE_HIGHEND_GFX_PERSIST_PROP, value ? "true" : "false");
            Toast.makeText(getActivity(), R.string.need_reboot, Toast.LENGTH_LONG).show();
        } else if (preference == mDynFSync) {
            boolean value = mDynFSync.isChecked();
            if (value) AeroActivity.shell.setRootInfo("1", DYANMIC_FSYNC);
            else AeroActivity.shell.setRootInfo("0", DYANMIC_FSYNC);
            //** store preferences
            preference.getEditor().commit();
        } else if (preference == mZCache) {
            zCacheClick();
        } else if (preference == mWriteBackControl) {
            boolean value = mWriteBackControl.isChecked();
            if (value) AeroActivity.shell.setRootInfo("1", WRITEBACK);
            else AeroActivity.shell.setRootInfo("0", WRITEBACK);
            //** store preferences
            preference.getEditor().commit();
        } else {
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }
        return true;
    }

    private void zCacheClick() {
        String getState = AeroActivity.shell.getInfo(CMDLINE_ZACHE);
        boolean value = mZCache.isChecked();
        AeroActivity.shell.remountSystem();
        if (value) {
            // If already on, we can bail out;
            if (getState.contains("zcache")) return;
            getState = getState + " zcache";
        } else {
            // bail out again, because its already how we want it;
            if (!getState.contains("zcache")) return;
            getState = getState.replace(" zcache", "");
        }
        // Set current State to path;
        AeroActivity.shell.setRootInfo(getState, CMDLINE_ZACHE);
        Toast.makeText(getActivity(), R.string.need_reboot, Toast.LENGTH_LONG).show();
    }

    public void DrawFirstStart(int header, int content, String filename) {

        try {
            FileOutputStream fos = getActivity().openFileOutput(filename, Context.MODE_PRIVATE);
            fos.write("1".getBytes());
            fos.close();
        }
        catch (IOException e) {
            Log.e("Aero", "Could not save file. ", e);
        }

        mShowCase = ShowcaseView.insertShowcaseView(150, 730, getActivity(), header, content, mConfigOptions);
    }

    private void loadIOParameter() {

        mConfigOptions = new ShowcaseView.ConfigOptions();
        mConfigOptions.hideOnClickOutside = false;
        mConfigOptions.shotType = ShowcaseView.TYPE_ONE_SHOT;

        // Set up our file;
        int output = 0;
        final byte[] buffer = new byte[1024];

        try {
            FileInputStream fis = getActivity().openFileInput(FILENAME_HIDDEN);
            output = fis.read(buffer);
            fis.close();
        } catch (IOException e) {
            Log.e("Aero", "Couldn't open File... " + output);
        }

        // Only show showcase once;
        if (output == 0)
            DrawFirstStart(R.string.showcase_hidden_feature, R.string.showcase_hidden_feature_sum, FILENAME_HIDDEN);

        final String complete_path = GOV_IO_PARAMETER;

        try {

            String completeParamterList[] = AeroActivity.shell.getDirInfo(complete_path, true);

            // If there are already some entries, kill them all (with fire)
            if (PrefCat != null)
                root.removePreference(PrefCat);

            PrefCat = new PreferenceCategory(getActivity());
            PrefCat.setTitle(R.string.io_scheduler);
            root.addPreference(PrefCat);

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Log.e("Aero", "Something interrupted the main Thread, try again.", e);
            }

            handler h = new handler();

            for (String b : completeParamterList)
                h.generateSettings(b, complete_path);

            // Probably the wrong place, should be in getDirInfo ?
        } catch (NullPointerException e) {
            Toast.makeText(getActivity(), "Looks like there are no parameter for this governor?", Toast.LENGTH_LONG).show();
            Log.e("Aero", "There isn't any folder i can check. Does this governor has parameters?", e);

        }

    }


    // Make a private class to load all parameters;
    private class handler {

        public void generateSettings(final String parameter, String path) {

            final CustomTextPreference prefload = new CustomTextPreference(getActivity());
            // Strings saves the complete path for a given governor;
            final String parameterPath = path + "/" + parameter;
            String summary = AeroActivity.shell.getInfo(parameterPath);

            // Only show numbers in input field;
            prefload.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);

            // Setup all things we would normally do in XML;
            prefload.setSummary(summary);
            prefload.setTitle(parameter);
            prefload.setText(summary);
            prefload.setDialogTitle(parameter);

            if (prefload.getSummary().equals("Unavailable")) {
                prefload.setEnabled(false);
                prefload.setSummary("This value can't be changed.");
            }

            PrefCat.addPreference(prefload);

            // Custom OnChangeListener for each element in our list;
            prefload.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {

                    String a = (String) o;
                    CharSequence oldValue = prefload.getSummary();

                    AeroActivity.shell.setRootInfo(a, parameterPath);

                    if (AeroActivity.shell.checkPath(AeroActivity.shell.getInfo(parameterPath), a)) {
                        prefload.setSummary(a);
                    } else {
                        Toast.makeText(getActivity(), "Couldn't set desired parameter"  + " Old value; " +
                                AeroActivity.shell.getInfo(parameterPath) + " New Value; " + a, Toast.LENGTH_LONG).show();
                        prefload.setSummary(oldValue);
                    }

                    // Store our custom preferences if available;
                    SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
                    preferences.edit().putString(parameterPath, o.toString()).commit();

                    return true;
                };
            });
        }

    }

}

