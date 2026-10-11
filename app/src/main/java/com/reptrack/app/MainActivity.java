package com.reptrack.app;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private final int BG=0xFF0B0E11, PANEL=0xFF151A20, BORDER=0xFF29313A, TEXT=0xFFF1F4EF, MUTED=0xFF929BA5;
    private SharedPreferences prefs;
    private LinearLayout root, content, nav;
    private String page="Home";
    private BroadcastReceiver receiver;

    // Timer & Stopwatch state
    private boolean timerRunning = false;
    private boolean isStopwatch = true;
    private long timerSeconds = 60;
    private long stopwatchSeconds = 0;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private TextView timerDisplayTv;
    private Button startPauseBtn;
    private android.app.AlertDialog celebrationDialog;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("reptrack",MODE_PRIVATE);
        loadWorkout();
        build();
        requestPermissionsIfNeeded();
        checkStepGoalCelebration();
    }
    private int getAccentColor() {
        try {
            return com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0xFFC6FF63);
        } catch (Exception e) {
            return 0xFFC6FF63;
        }
    }
    private void requestPermissionsIfNeeded(){
        if(Build.VERSION.SDK_INT>=29 && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION},10);return;}
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);}
        else startTracking();
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==10&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);else startTracking();}else if(r==11&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startTracking();}
    private void startTracking(){SensorManager sm=(SensorManager)getSystemService(SENSOR_SERVICE);if(sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)==null){Toast.makeText(this,"This phone has no step-counter sensor. You can enter steps manually.",Toast.LENGTH_LONG).show();return;}try{Intent i=new Intent(this,StepCounterService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}catch(Exception e){Toast.makeText(this,"Could not start step tracking. Check app permissions.",Toast.LENGTH_LONG).show();}}

    private int getExerciseCount() { return prefs.getInt("exercise_count", 0); }
    private int getStepGoal() { return prefs.getInt("step_goal", 8000); }
    private int getSetCount() {
        int count = prefs.getInt("set_count", -1);
        if (count == -1) {
            prefs.edit().putInt("set_count", 3).apply();
            return 3;
        }
        return count;
    }
    private int countSetsDone() {
        int count = getSetCount();
        int completedSets = 0;
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String saved = prefs.getString("workout_session_day", "");
        if (!day.equals(saved)) return 0;
        for (int i = 0; i < count; i++) {
            if (prefs.getBoolean("set_done_" + i, false)) completedSets++;
        }
        return completedSets;
    }
    private void loadWorkout(){
        String day=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());
        String saved=prefs.getString("workout_session_day","");
        if(!day.equals(saved)){
            int count = getSetCount();
            for(int i=0;i<count;i++) prefs.edit().putBoolean("set_done_"+i,false).apply();
            prefs.edit().putString("workout_session_day",day).apply();
        }
    }
    private void updateWorkoutCompletionStatus() {
        int totalSets = getSetCount();
        int doneSets = countSetsDone();
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        boolean wasDone = prefs.getBoolean("workout_done_" + day, false);

        if (doneSets >= totalSets && totalSets > 0) {
            prefs.edit().putBoolean("workout_done_" + day, true).apply();
            if (!day.equals(prefs.getString("last_workout_day", ""))) {
                prefs.edit().putInt("workouts", workouts() + 1).putString("last_workout_day", day).apply();
                showCelebrationDialog("Workout Completed! 🎉", "Amazing job! You've successfully finished all sets for today's session. Keep up the consistency!");
            }
        } else {
            if (wasDone) {
                prefs.edit().putBoolean("workout_done_" + day, false).apply();
                if (day.equals(prefs.getString("last_workout_day", ""))) {
                    int w = workouts();
                    if (w > 0) {
                        prefs.edit().putInt("workouts", w - 1).remove("last_workout_day").apply();
                    }
                }
            }
        }
    }

    private void checkStepGoalCelebration() {
        int s = steps();
        int goal = getStepGoal();
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (goal > 0 && s < goal) {
            prefs.edit().putBoolean("step_goal_celebrated_" + day, false).apply();
        } else if (goal > 0 && s >= goal) {
            if (!prefs.getBoolean("step_goal_celebrated_" + day, false)) {
                prefs.edit().putBoolean("step_goal_celebrated_" + day, true).apply();
                showCelebrationDialog("Step Goal Reached! 🏆", "Fantastic! You've hit your daily target of " + fmt(goal) + " steps today!");
            }
        }
    }

    private String getRoutineStatusForDate(String dateStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date date = sdf.parse(dateStr);
            Calendar c = Calendar.getInstance();
            c.setTime(date);
            int dow = c.get(Calendar.DAY_OF_WEEK);
            int routineIndex = 0;
            if (dow == Calendar.MONDAY) routineIndex = 0;
            else if (dow == Calendar.TUESDAY) routineIndex = 1;
            else if (dow == Calendar.WEDNESDAY) routineIndex = 2;
            else if (dow == Calendar.THURSDAY) routineIndex = 3;
            else if (dow == Calendar.FRIDAY) routineIndex = 4;
            else if (dow == Calendar.SATURDAY) routineIndex = 5;
            else if (dow == Calendar.SUNDAY) routineIndex = 6;

            String defaultStatus = (routineIndex == 2 || routineIndex == 5 || routineIndex == 6) ? "Rest" : "Workout";
            return prefs.getString("routine_day_" + routineIndex, defaultStatus);
        } catch (Exception e) {
            return "Workout";
        }
    }

    private void addExercise(String name, String details) {
        int count = getExerciseCount();
        prefs.edit()
            .putString("exercise_name_" + count, name)
            .putString("exercise_details_" + count, details)
            .putInt("exercise_count", count + 1)
            .apply();
    }
    private void deleteExercise(int delIdx) {
        int count = getExerciseCount();
        for (int i = delIdx; i < count - 1; i++) {
            String nextName = prefs.getString("exercise_name_" + (i + 1), "");
            String nextDetails = prefs.getString("exercise_details_" + (i + 1), "");
            prefs.edit()
                .putString("exercise_name_" + i, nextName)
                .putString("exercise_details_" + i, nextDetails)
                .apply();
        }
        prefs.edit()
            .remove("exercise_name_" + (count - 1))
            .remove("exercise_details_" + (count - 1))
            .putInt("exercise_count", count - 1)
            .apply();
    }
    private void addSet() {
        int count = getSetCount();
        prefs.edit()
            .putBoolean("set_done_" + count, false)
            .putInt("set_count", count + 1)
            .apply();
        updateWorkoutCompletionStatus();
    }
    private void deleteSet(int delIdx) {
        int count = getSetCount();
        if (count <= 1) return;
        for (int i = delIdx; i < count - 1; i++) {
            boolean nextDone = prefs.getBoolean("set_done_" + (i + 1), false);
            prefs.edit().putBoolean("set_done_" + i, nextDone).apply();
        }
        prefs.edit()
            .remove("set_done_" + (count - 1))
            .putInt("set_count", count - 1)
            .apply();
        updateWorkoutCompletionStatus();
    }

    private void showCelebrationDialog(String title, String message) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(24), dp(24), dp(24));
        layout.setGravity(Gravity.CENTER);
        layout.setBackground(round(PANEL, 20));

        TextView titleTv = text(title, 20, getAccentColor(), true);
        titleTv.setGravity(Gravity.CENTER);
        layout.addView(titleTv);
        layout.addView(space(10));

        TextView msgTv = text(message, 14, TEXT, false);
        msgTv.setGravity(Gravity.CENTER);
        layout.addView(msgTv);
        layout.addView(space(18));

        Button okBtn = button("Awesome! Continue");
        okBtn.setOnClickListener(v -> {
            if (celebrationDialog != null && celebrationDialog.isShowing()) {
                celebrationDialog.dismiss();
            }
        });
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, -2);
        layout.addView(okBtn, btnLp);

        builder.setView(layout);
        builder.setCancelable(true);

        celebrationDialog = builder.create();
        if (celebrationDialog.getWindow() != null) {
            celebrationDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        celebrationDialog.show();
    }

    private String pendingExportJson = "";

    private void exportData() {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            java.util.Map<String, ?> allEntries = prefs.getAll();
            for (java.util.Map.Entry<String, ?> entry : allEntries.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "routinetrack_backup_" + new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date()) + ".json");
            pendingExportJson = json.toString();
            startActivityForResult(intent, 100);
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importData() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, 101);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                if (requestCode == 100) {
                    android.net.Uri uri = data.getData();
                    try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os != null) {
                            os.write(pendingExportJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            Toast.makeText(this, "Data backed up successfully! 💾", Toast.LENGTH_LONG).show();
                        }
                    }
                } else if (requestCode == 101) {
                    android.net.Uri uri = data.getData();
                    StringBuilder sb = new StringBuilder();
                    try (java.io.InputStream is = getContentResolver().openInputStream(uri);
                         java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                    }
                    org.json.JSONObject json = new org.json.JSONObject(sb.toString());
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.clear();
                    java.util.Iterator<String> keys = json.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        Object val = json.get(key);
                        if (val instanceof Boolean) editor.putBoolean(key, (Boolean) val);
                        else if (val instanceof Integer) editor.putInt(key, (Integer) val);
                        else if (val instanceof Long) editor.putLong(key, (Long) val);
                        else if (val instanceof Float) editor.putFloat(key, (Float) val);
                        else if (val instanceof String) editor.putString(key, (String) val);
                    }
                    editor.apply();
                    Toast.makeText(this, "Data restored successfully! 🎉", Toast.LENGTH_LONG).show();
                    showPage();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Operation failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showDayDetailDialog(Calendar cellCal, String dateStr, Runnable onUpdated) {
        String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        long daySteps = dateStr.equals(todayStr) ? steps() : prefs.getLong("steps_" + dateStr, 0);
        boolean dayWorkoutDone = prefs.getBoolean("workout_done_" + dateStr, false) || dateStr.equals(prefs.getString("last_workout_day", ""));
        String routineStatus = getRoutineStatusForDate(dateStr);

        SimpleDateFormat titleFmt = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US);
        String dateTitle = titleFmt.format(cellCal.getTime());

        String workoutStatusText;
        if (dayWorkoutDone) {
            workoutStatusText = "Completed ✓";
        } else if ("Rest".equalsIgnoreCase(routineStatus)) {
            workoutStatusText = "Rest";
        } else {
            workoutStatusText = "Not logged";
        }

        String summary = String.format(Locale.US, "Steps: %,d\nWorkout Status: %s", 
            daySteps, 
            workoutStatusText
        );

        android.app.AlertDialog.Builder dayBuilder = new android.app.AlertDialog.Builder(this);
        dayBuilder.setTitle(dateTitle);
        dayBuilder.setMessage(summary);

        String toggleBtnText = dayWorkoutDone ? "Mark as Incomplete" : "Mark Workout Completed ✓";
        dayBuilder.setPositiveButton(toggleBtnText, (d, w) -> {
            boolean newState = !dayWorkoutDone;
            prefs.edit().putBoolean("workout_done_" + dateStr, newState).apply();
            if (newState) {
                if (!dateStr.equals(prefs.getString("last_workout_day", ""))) {
                    prefs.edit().putInt("workouts", workouts() + 1).putString("last_workout_day", dateStr).apply();
                }
                showCelebrationDialog("Workout Completed! 🎉", "Workout successfully marked as completed for " + dateStr + ".");
            } else {
                if (dateStr.equals(prefs.getString("last_workout_day", ""))) {
                    int wk = workouts();
                    if (wk > 0) {
                        prefs.edit().putInt("workouts", wk - 1).remove("last_workout_day").apply();
                    } else {
                        prefs.edit().remove("last_workout_day").apply();
                    }
                }
            }
            Toast.makeText(this, "Workout status updated", Toast.LENGTH_SHORT).show();
            if (onUpdated != null) onUpdated.run();
        });

        dayBuilder.setNegativeButton("Close", (d, w) -> d.dismiss());

        android.app.AlertDialog dialog = dayBuilder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(getAccentColor());
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(MUTED);
        });
        dialog.show();
    }

    private void showAddExerciseDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Add Routine Exercise");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(16), dp(20), dp(16));

        final EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint("Exercise name (e.g. Squats)");
        nameInput.setHintTextColor(MUTED);
        nameInput.setTextColor(TEXT);
        layout.addView(nameInput);

        final EditText detailsInput = new EditText(this);
        detailsInput.setSingleLine(true);
        detailsInput.setHint("Details (e.g. 12 reps · 10 kg)");
        detailsInput.setHintTextColor(MUTED);
        detailsInput.setTextColor(TEXT);
        layout.addView(detailsInput);

        builder.setView(layout);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String name = nameInput.getText().toString().trim();
            String details = detailsInput.getText().toString().trim();
            if(name.isEmpty()){
                Toast.makeText(this, "Please enter an exercise name", Toast.LENGTH_SHORT).show();
                return;
            }
            addExercise(name, details);
            showPage();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        android.app.AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(getAccentColor());
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(MUTED);
        });
        dialog.show();
    }

    private void showEditRoutineDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Edit Weekly Routine");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(16), dp(20), dp(16));

        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        final String[] statuses = new String[7];

        for (int i = 0; i < 7; i++) {
            final int index = i;
            String defaultStatus = (i == 2 || i == 5 || i == 6) ? "Rest" : "Workout";
            statuses[index] = prefs.getString("routine_day_" + index, defaultStatus);

            LinearLayout dayRow = new LinearLayout(this);
            dayRow.setOrientation(LinearLayout.HORIZONTAL);
            dayRow.setGravity(Gravity.CENTER_VERTICAL);
            dayRow.setPadding(0, dp(4), 0, dp(4));

            TextView dayLbl = text(days[index], 14, TEXT, true);
            LinearLayout.LayoutParams lblLp = new LinearLayout.LayoutParams(0, -2, 1f);
            dayRow.addView(dayLbl, lblLp);

            Button statBtn = button(statuses[index]);
            statBtn.setTextSize(12);
            statBtn.setOnClickListener(v -> {
                if (statuses[index].equals("Workout")) {
                    statuses[index] = "Rest";
                    statBtn.setText("Rest");
                    if (Build.VERSION.SDK_INT >= 21) {
                        statBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(BORDER));
                    }
                } else {
                    statuses[index] = "Workout";
                    statBtn.setText("Workout");
                    if (Build.VERSION.SDK_INT >= 21) {
                        statBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getAccentColor()));
                    }
                }
            });
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(dp(100), dp(36));
            dayRow.addView(statBtn, btnLp);

            layout.addView(dayRow);
        }

        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            for (int i = 0; i < 7; i++) {
                prefs.edit().putString("routine_day_" + i, statuses[i]).apply();
            }
            Toast.makeText(this, "Weekly routine updated!", Toast.LENGTH_SHORT).show();
            showPage();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        android.app.AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(getAccentColor());
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(MUTED);
        });
        dialog.show();
    }

    private void showFullCalendarDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setCancelable(true);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(dp(16), dp(16), dp(16), dp(16));
        mainLayout.setBackgroundColor(PANEL);

        final Calendar cal = Calendar.getInstance();
        final int[] curYear = {cal.get(Calendar.YEAR)};
        final int[] curMonth = {cal.get(Calendar.MONTH)};

        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setGravity(Gravity.CENTER_VERTICAL);

        Button prevBtn = iconButton(" ‹ ");
        Button nextBtn = iconButton(" › ");
        TextView titleTv = text("", 16, TEXT, true);
        titleTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);

        navRow.addView(prevBtn);
        navRow.addView(titleTv, titleLp);
        navRow.addView(nextBtn);
        mainLayout.addView(navRow);
        mainLayout.addView(space(12));

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] headers = {"S", "M", "T", "W", "T", "F", "S"};
        for (String h : headers) {
            TextView hw = text(h, 12, MUTED, true);
            hw.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams hlps = new LinearLayout.LayoutParams(0, -2, 1f);
            headerRow.addView(hw, hlps);
        }
        mainLayout.addView(headerRow);
        mainLayout.addView(space(8));

        final LinearLayout gridContainer = new LinearLayout(this);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        mainLayout.addView(gridContainer);

        SimpleDateFormat monthTitleFmt = new SimpleDateFormat("MMMM yyyy", Locale.US);
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String todayStr = sdfDate.format(new Date());
        int accent = getAccentColor();

        final Runnable[] renderGrid = new Runnable[1];
        renderGrid[0] = () -> {
            gridContainer.removeAllViews();
            Calendar tempCal = Calendar.getInstance();
            tempCal.set(curYear[0], curMonth[0], 1);
            titleTv.setText(monthTitleFmt.format(tempCal.getTime()));

            int firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK); // Sun=1, Mon=2...
            int maxDays = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH);
            int offset = firstDayOfWeek - 1;

            int totalCells = offset + maxDays;
            int rows = (int) Math.ceil(totalCells / 7.0);

            int dayCounter = 1;
            for (int r = 0; r < rows; r++) {
                LinearLayout weekRow = new LinearLayout(this);
                weekRow.setOrientation(LinearLayout.HORIZONTAL);
                weekRow.setPadding(0, dp(2), 0, dp(2));

                for (int c = 0; c < 7; c++) {
                    LinearLayout cell = new LinearLayout(this);
                    cell.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, dp(38), 1f);
                    cell.setLayoutParams(cellLp);

                    int cellIndex = r * 7 + c;
                    if (cellIndex >= offset && dayCounter <= maxDays) {
                        Calendar cellCal = Calendar.getInstance();
                        cellCal.set(curYear[0], curMonth[0], dayCounter);
                        String dateStr = sdfDate.format(cellCal.getTime());
                        boolean isWorkoutDone = prefs.getBoolean("workout_done_" + dateStr, false) || dateStr.equals(prefs.getString("last_workout_day", ""));
                        boolean isToday = dateStr.equals(todayStr);

                        TextView dayTv = new TextView(this);
                        dayTv.setText(String.valueOf(dayCounter));
                        dayTv.setTextSize(13);
                        dayTv.setGravity(Gravity.CENTER);
                        dayTv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

                        int size = dp(32);
                        LinearLayout.LayoutParams dayLp = new LinearLayout.LayoutParams(size, size);
                        dayTv.setLayoutParams(dayLp);

                        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);

                        if (isWorkoutDone) {
                            bg.setColor(accent);
                            dayTv.setTextColor(0xFF11170A);
                        } else if (isToday) {
                            bg.setColor(PANEL);
                            bg.setStroke(dp(2), accent);
                            dayTv.setTextColor(TEXT);
                        } else {
                            bg.setColor(0x00000000);
                            dayTv.setTextColor(TEXT);
                        }
                        dayTv.setBackground(bg);
                        cell.addView(dayTv);

                        cell.setOnClickListener(v -> showDayDetailDialog(cellCal, dateStr, renderGrid[0]));

                        dayCounter++;
                    }
                    weekRow.addView(cell);
                }
                gridContainer.addView(weekRow);
            }
        };

        renderGrid[0].run();

        prevBtn.setOnClickListener(v -> {
            curMonth[0]--;
            if (curMonth[0] < 0) {
                curMonth[0] = 11;
                curYear[0]--;
            }
            renderGrid[0].run();
        });

        nextBtn.setOnClickListener(v -> {
            curMonth[0]++;
            if (curMonth[0] > 11) {
                curMonth[0] = 0;
                curYear[0]++;
            }
            renderGrid[0].run();
        });

        builder.setView(mainLayout);
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void buildMonthlyCalendarCard(LinearLayout parentLayout) {
        cardStart();

        final Calendar cal = Calendar.getInstance();
        final int[] curYear = {cal.get(Calendar.YEAR)};
        final int[] curMonth = {cal.get(Calendar.MONTH)};

        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setGravity(Gravity.CENTER_VERTICAL);

        Button prevBtn = iconButton(" ‹ ");
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(dp(40), dp(36));
        prevBtn.setLayoutParams(prevLp);

        Button nextBtn = iconButton(" › ");
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(dp(40), dp(36));
        nextBtn.setLayoutParams(nextLp);

        TextView titleTv = text("", 16, TEXT, true);
        titleTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);

        navRow.addView(prevBtn);
        navRow.addView(titleTv, titleLp);
        navRow.addView(nextBtn);
        content.addView(navRow);
        content.addView(space(12));

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] headers = {"S", "M", "T", "W", "T", "F", "S"};
        for (String h : headers) {
            TextView hw = text(h, 12, MUTED, true);
            hw.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams hlps = new LinearLayout.LayoutParams(0, -2, 1f);
            headerRow.addView(hw, hlps);
        }
        content.addView(headerRow);
        content.addView(space(8));

        final LinearLayout gridContainer = new LinearLayout(this);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(gridContainer);

        SimpleDateFormat monthTitleFmt = new SimpleDateFormat("MMMM yyyy", Locale.US);
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String todayStr = sdfDate.format(new Date());
        int accent = getAccentColor();

        final Runnable[] renderGrid = new Runnable[1];
        renderGrid[0] = () -> {
            gridContainer.removeAllViews();
            Calendar tempCal = Calendar.getInstance();
            tempCal.set(curYear[0], curMonth[0], 1);
            titleTv.setText(monthTitleFmt.format(tempCal.getTime()));

            int firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK); // Sun=1, Mon=2...
            int maxDays = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH);
            int offset = firstDayOfWeek - 1;

            int totalCells = offset + maxDays;
            int rows = (int) Math.ceil(totalCells / 7.0);

            int dayCounter = 1;
            for (int r = 0; r < rows; r++) {
                LinearLayout weekRow = new LinearLayout(this);
                weekRow.setOrientation(LinearLayout.HORIZONTAL);
                weekRow.setGravity(Gravity.CENTER);
                weekRow.setPadding(0, dp(2), 0, dp(2));

                for (int c = 0; c < 7; c++) {
                    LinearLayout cell = new LinearLayout(this);
                    cell.setOrientation(LinearLayout.VERTICAL);
                    cell.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, dp(40), 1f);
                    cell.setLayoutParams(cellLp);

                    int cellIndex = r * 7 + c;
                    if (cellIndex >= offset && dayCounter <= maxDays) {
                        Calendar cellCal = Calendar.getInstance();
                        cellCal.set(curYear[0], curMonth[0], dayCounter);
                        String dateStr = sdfDate.format(cellCal.getTime());
                        boolean isWorkoutDone = prefs.getBoolean("workout_done_" + dateStr, false) || dateStr.equals(prefs.getString("last_workout_day", ""));
                        boolean isToday = dateStr.equals(todayStr);

                        TextView dayTv = new TextView(this);
                        dayTv.setText(String.valueOf(dayCounter));
                        dayTv.setTextSize(13);
                        dayTv.setGravity(Gravity.CENTER);
                        dayTv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

                        int size = dp(32);
                        LinearLayout.LayoutParams dayLp = new LinearLayout.LayoutParams(size, size);
                        dayLp.gravity = Gravity.CENTER;
                        dayTv.setLayoutParams(dayLp);

                        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);

                        if (isWorkoutDone) {
                            bg.setColor(accent);
                            dayTv.setTextColor(0xFF11170A);
                        } else if (isToday) {
                            bg.setColor(PANEL);
                            bg.setStroke(dp(2), accent);
                            dayTv.setTextColor(TEXT);
                        } else {
                            bg.setColor(0x00000000);
                            dayTv.setTextColor(TEXT);
                        }
                        dayTv.setBackground(bg);
                        cell.addView(dayTv);

                        cell.setOnClickListener(v -> showDayDetailDialog(cellCal, dateStr, renderGrid[0]));

                        dayCounter++;
                    }
                    weekRow.addView(cell);
                }
                gridContainer.addView(weekRow);
            }
        };

        renderGrid[0].run();

        prevBtn.setOnClickListener(v -> {
            curMonth[0]--;
            if (curMonth[0] < 0) {
                curMonth[0] = 11;
                curYear[0]--;
            }
            renderGrid[0].run();
        });

        nextBtn.setOnClickListener(v -> {
            curMonth[0]++;
            if (curMonth[0] > 11) {
                curMonth[0] = 0;
                curYear[0]++;
            }
            renderGrid[0].run();
        });

        cardEnd();
    }

    private void startTimerTicker() {
        if (timerRunnable != null) mainHandler.removeCallbacks(timerRunnable);
        timerRunnable = new Runnable() {
            @Override public void run() {
                if (!timerRunning) return;
                if (isStopwatch) {
                    stopwatchSeconds++;
                } else {
                    if (timerSeconds > 0) {
                        timerSeconds--;
                    } else {
                        timerRunning = false;
                        if(startPauseBtn != null) startPauseBtn.setText("Start");
                        Toast.makeText(MainActivity.this, "Timer finished! ⏰", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                if (timerDisplayTv != null) {
                    timerDisplayTv.setText(formatTime(isStopwatch ? stopwatchSeconds : timerSeconds));
                }
                mainHandler.postDelayed(this, 1000);
            }
        };
        mainHandler.postDelayed(timerRunnable, 1000);
    }

    private String formatTime(long totalSecs) {
        long mins = totalSecs / 60;
        long secs = totalSecs % 60;
        return String.format(Locale.US, "%02d:%02d", mins, secs);
    }

    private int steps(){String day=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());if(!day.equals(prefs.getString("step_day","")))return 0;return (int)prefs.getLong("steps_today",0);}
    private int workouts(){return prefs.getInt("workouts",0);}
    private int getTotalScheduledWorkouts() {
        int count = 0;
        for (int i = 0; i < 7; i++) {
            String def = (i == 2 || i == 5 || i == 6) ? "Rest" : "Workout";
            String stat = prefs.getString("routine_day_" + i, def);
            if ("Workout".equalsIgnoreCase(stat)) {
                count++;
            }
        }
        return Math.max(1, count);
    }
    private void build(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(18),dp(16),dp(18),dp(10));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(dp(18), bars.top + dp(12), dp(18), bars.bottom + dp(10));
            return insets;
        });
        setContentView(root);
        LinearLayout brand=new LinearLayout(this);brand.setGravity(Gravity.CENTER_VERTICAL);android.widget.ImageView logo=new android.widget.ImageView(this);logo.setImageResource(R.drawable.ic_brand_logo);logo.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);logo.setBackground(round(0xFF1B241C,12));brand.addView(logo,new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout bt=new LinearLayout(this);bt.setOrientation(LinearLayout.VERTICAL);bt.setPadding(dp(12),0,0,0);bt.addView(text("Routine Track",19,TEXT,true));bt.addView(text("YOUR WORK. YOUR PROGRESS.",10,MUTED,false));brand.addView(bt);root.addView(brand);root.addView(space(16));
        nav=new LinearLayout(this);nav.setPadding(dp(3),dp(3),dp(3),dp(3));nav.setBackground(round(PANEL,18));String[] tabs={"Home","Workout","Steps","Progress"};for(String t:tabs){Button b=button(t);b.setTextSize(11);b.setPadding(dp(2),dp(12),dp(2),dp(12));b.setOnClickListener(v->{page=t;showPage();});nav.addView(b,new LinearLayout.LayoutParams(0,dp(45),1));}root.addView(nav);root.addView(space(14));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));showPage();
    }
    private void showPage(){
        content.removeAllViews();
        int accent = getAccentColor();
        for(int i=0;i<nav.getChildCount();i++){
            Button b=(Button)nav.getChildAt(i);
            boolean active=b.getText().toString().equals(page);
            b.setTextColor(active?accent:MUTED);
            b.setBackground(round(active?0xFF283323:0xFF151A20,12));
            if (Build.VERSION.SDK_INT >= 21) {
                b.setBackgroundTintList(null);
            }
        }
        switch(page){case "Home":home();break;case "Workout":workout();break;case "Steps":stepsPage();break;default:progress();}
    }
    private void home(){
        int s=steps();
        int goal=getStepGoal();
        int pct=goal>0?Math.min(100,s*100/goal):0;
        int totalSets=getSetCount();
        int accent = getAccentColor();
        content.addView(text(new SimpleDateFormat("EEEE, MMM d",Locale.getDefault()).format(new Date()).toUpperCase(Locale.getDefault()),11,MUTED,true));
        content.addView(text("Daily overview",27,TEXT,true));
        content.addView(space(12));

        // 1. Daily Movement Card
        cardStart();
        content.addView(text("DAILY MOVEMENT",11,MUTED,true));
        content.addView(text(fmt(s),38,accent,true));
        content.addView(text("of "+fmt(goal)+" steps  ·  "+pct+"% complete",13,MUTED,false));
        content.addView(space(10));
        progressBar(pct);
        content.addView(text(Math.max(0,goal-s)+" steps remaining  ·  ~"+String.format(Locale.US,"%.1f",s*.0007)+" km",12,MUTED,false));
        cardEnd();
        content.addView(space(12));

        // 2. This Week's Workouts Card (Calendar)
        cardStart();
        content.addView(text("THIS WEEK'S WORKOUTS",11,MUTED,true));
        content.addView(text(workouts()+" / " + getTotalScheduledWorkouts() + " sessions completed",25,TEXT,true));
        content.addView(space(8));

        // Weekly Calendar Row (Sun - Sat)
        LinearLayout calRow = new LinearLayout(this);
        calRow.setOrientation(LinearLayout.HORIZONTAL);
        calRow.setGravity(Gravity.CENTER);
        calRow.setPadding(0, dp(4), 0, dp(4));
        calRow.setOnClickListener(v -> showFullCalendarDialog());

        Calendar todayCal = Calendar.getInstance();
        int dayOfWeek = todayCal.get(Calendar.DAY_OF_WEEK); // Sunday=1, Mon=2 ... Sat=7
        int diffToSun = -(dayOfWeek - Calendar.SUNDAY);
        todayCal.add(Calendar.DATE, diffToSun);

        String[] dayLetters = {"S", "M", "T", "W", "T", "F", "S"};
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat sdfDayNum = new SimpleDateFormat("d", Locale.US);
        String todayStr = sdfDate.format(new Date());

        for (int i = 0; i < 7; i++) {
            Calendar c = (Calendar) todayCal.clone();
            c.add(Calendar.DATE, i);
            String dateStr = sdfDate.format(c.getTime());
            String dayNum = sdfDayNum.format(c.getTime());
            boolean isWorkoutDone = prefs.getBoolean("workout_done_" + dateStr, false) || dateStr.equals(prefs.getString("last_workout_day", ""));
            boolean isToday = dateStr.equals(todayStr);

            LinearLayout dayCol = new LinearLayout(this);
            dayCol.setOrientation(LinearLayout.VERTICAL);
            dayCol.setGravity(Gravity.CENTER);

            TextView lbl = text(dayLetters[i], 11, MUTED, true);
            lbl.setGravity(Gravity.CENTER);
            dayCol.addView(lbl);

            TextView circle = new TextView(this);
            circle.setText(dayNum);
            circle.setTextSize(13);
            circle.setGravity(Gravity.CENTER);
            circle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

            int circleSize = dp(32);
            LinearLayout.LayoutParams circleLp = new LinearLayout.LayoutParams(circleSize, circleSize);
            circleLp.topMargin = dp(4);
            circleLp.bottomMargin = dp(2);
            circle.setLayoutParams(circleLp);

            android.graphics.drawable.GradientDrawable circleBg = new android.graphics.drawable.GradientDrawable();
            circleBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);

            if (isWorkoutDone) {
                circleBg.setColor(accent);
                circle.setTextColor(0xFF11170A);
            } else if (isToday) {
                circleBg.setColor(PANEL);
                circleBg.setStroke(dp(2), accent);
                circle.setTextColor(TEXT);
            } else {
                circleBg.setColor(0x00000000);
                circle.setTextColor(MUTED);
            }
            circle.setBackground(circleBg);
            dayCol.addView(circle);

            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, -2, 1f);
            calRow.addView(dayCol, colLp);
        }
        content.addView(calRow);

        content.addView(space(12));
        content.addView(text("TODAY'S WORKOUT",11,MUTED,true));
        content.addView(text(countSetsDone()+" / "+totalSets+" sets completed",18,TEXT,true));
        Button wb=button("Start workout  →");
        wb.setOnClickListener(v->{page="Workout";showPage();});
        content.addView(wb);
        cardEnd();
        content.addView(space(12));

        // 3. Exercise Timer & Stopwatch Card
        cardStart();
        content.addView(text("EXERCISE TIMER & STOPWATCH",11,MUTED,true));
        timerDisplayTv = text(formatTime(isStopwatch ? stopwatchSeconds : timerSeconds), 42, accent, true);
        timerDisplayTv.setGravity(Gravity.CENTER);
        content.addView(timerDisplayTv);
        content.addView(space(4));

        LinearLayout controlRow = new LinearLayout(this);
        controlRow.setOrientation(LinearLayout.HORIZONTAL);
        controlRow.setGravity(Gravity.CENTER);

        Button modeBtn = button(isStopwatch ? "Stopwatch" : "Timer");
        modeBtn.setTextSize(12);
        modeBtn.setBackground(round(BORDER, 10));
        modeBtn.setOnClickListener(v -> {
            isStopwatch = !isStopwatch;
            timerRunning = false;
            if(timerRunnable != null) mainHandler.removeCallbacks(timerRunnable);
            showPage();
        });
        LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(0, -2, 1.2f);
        modeLp.rightMargin = dp(6);
        controlRow.addView(modeBtn, modeLp);

        startPauseBtn = button(timerRunning ? "Pause" : "Start");
        startPauseBtn.setTextSize(12);
        startPauseBtn.setOnClickListener(v -> {
            timerRunning = !timerRunning;
            startPauseBtn.setText(timerRunning ? "Pause" : "Start");
            if (timerRunning) {
                startTimerTicker();
            } else {
                if (timerRunnable != null) mainHandler.removeCallbacks(timerRunnable);
            }
        });
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(0, -2, 1f);
        spLp.rightMargin = dp(6);
        controlRow.addView(startPauseBtn, spLp);

        Button resetBtn = button("Reset");
        resetBtn.setTextSize(12);
        resetBtn.setBackground(round(BORDER, 10));
        resetBtn.setOnClickListener(v -> {
            timerRunning = false;
            if(timerRunnable != null) mainHandler.removeCallbacks(timerRunnable);
            stopwatchSeconds = 0;
            timerSeconds = 60;
            if(timerDisplayTv != null) timerDisplayTv.setText(formatTime(isStopwatch ? stopwatchSeconds : timerSeconds));
            if(startPauseBtn != null) startPauseBtn.setText("Start");
        });
        LinearLayout.LayoutParams resLp = new LinearLayout.LayoutParams(0, -2, 0.8f);
        controlRow.addView(resetBtn, resLp);

        content.addView(controlRow);

        if (!isStopwatch) {
            content.addView(space(6));
            LinearLayout presetRow = new LinearLayout(this);
            presetRow.setOrientation(LinearLayout.HORIZONTAL);
            int[] presets = {30, 60, 90, 120, 180};
            for(int p : presets) {
                Button pb = button((p >= 60 ? (p/60 + "m") : (p + "s")));
                pb.setTextSize(11);
                final int sec = p;
                pb.setOnClickListener(v -> {
                    timerRunning = false;
                    if(timerRunnable != null) mainHandler.removeCallbacks(timerRunnable);
                    timerSeconds = sec;
                    if(timerDisplayTv != null) timerDisplayTv.setText(formatTime(timerSeconds));
                    if(startPauseBtn != null) startPauseBtn.setText("Start");
                });
                LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
                pLp.rightMargin = dp(4);
                presetRow.addView(pb, pLp);
            }
            content.addView(presetRow);
        }
        cardEnd();
        content.addView(space(12));

        // 4. Your Routine Card
        cardStart();
        content.addView(text("YOUR ROUTINE",17,TEXT,true));
        content.addView(text(getExerciseCount()+" exercises in routine · "+totalSets+" workout sets.",13,MUTED,false));
        content.addView(text("Manage routine in the Workout tab.",12,accent,false));
        cardEnd();
    }
    private void workout(){
        content.addView(text("Workout session",26,TEXT,true));
        content.addView(text("Click set once to strike off (complete), double click to un-strike",13,MUTED,false));
        content.addView(space(12));
        
        // Card 1: Sets Tracker
        cardStart();
        content.addView(text("SETS PROGRESS",11,MUTED,true));
        int totalSets = getSetCount();
        int doneSets = countSetsDone();
        int accent = getAccentColor();
        content.addView(text(doneSets+" / "+totalSets+" sets completed",23,accent,true));
        if(totalSets > 0) progressBar(doneSets * 100 / totalSets);
        content.addView(space(6));

        for(int i=0; i<totalSets; i++){
            final int index=i;
            boolean isDone = prefs.getBoolean("set_done_"+i, false);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));

            TextView setTv = text("Set " + (i + 1), 16, isDone ? MUTED : TEXT, true);
            if (isDone) {
                setTv.setPaintFlags(setTv.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            }
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0, -2, 1f);
            row.addView(setTv, tvLp);

            final long[] lastClick = {0};
            row.setOnClickListener(v -> {
                long now = System.currentTimeMillis();
                boolean current = prefs.getBoolean("set_done_"+index, false);
                if (now - lastClick[0] < 400) {
                    if (current) {
                        prefs.edit().putBoolean("set_done_"+index, false).apply();
                        updateWorkoutCompletionStatus();
                        showPage();
                    }
                } else {
                    if (!current) {
                        prefs.edit().putBoolean("set_done_"+index, true).apply();
                        updateWorkoutCompletionStatus();
                        showPage();
                    }
                }
                lastClick[0] = now;
            });

            if(totalSets > 1) {
                Button del = iconButton("✕");
                LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(dp(28), dp(28));
                del.setOnClickListener(v -> {
                    deleteSet(index);
                    showPage();
                });
                row.addView(del, delLp);
            }

            content.addView(row);
        }

        Button addSetBtn = button("＋ Add Set");
        addSetBtn.setOnClickListener(v -> {
            addSet();
            showPage();
        });
        content.addView(addSetBtn);
        cardEnd();

        // Card 2: Routine Exercises Reference & Add
        content.addView(space(12));
        cardStart();
        content.addView(text("Routine exercises",17,TEXT,true));
        content.addView(text("Exercises performed in each set",12,MUTED,false));
        content.addView(space(6));

        int totalEx = getExerciseCount();
        if(totalEx == 0) {
            content.addView(text("No exercises added yet.",13,MUTED,false));
        } else {
            for(int i=0; i<totalEx; i++){
                final int exIndex = i;
                String name = prefs.getString("exercise_name_"+i, "");
                String details = prefs.getString("exercise_details_"+i, "");

                LinearLayout exRow = new LinearLayout(this);
                exRow.setOrientation(LinearLayout.HORIZONTAL);
                exRow.setGravity(Gravity.CENTER_VERTICAL);
                exRow.setPadding(0, dp(4), 0, dp(4));

                TextView tv = text("• " + name + (!details.isEmpty() ? " (" + details + ")" : ""), 14, TEXT, false);
                LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0, -2, 1f);
                exRow.addView(tv, tvLp);

                Button delEx = iconButton("✕");
                LinearLayout.LayoutParams delExLp = new LinearLayout.LayoutParams(dp(28), dp(28));
                delEx.setOnClickListener(v -> {
                    deleteExercise(exIndex);
                    showPage();
                });
                exRow.addView(delEx, delExLp);

                content.addView(exRow);
            }
        }
        content.addView(space(10));

        Button addToggleBtn = button("＋ Add exercise");
        addToggleBtn.setOnClickListener(v -> {
            showAddExerciseDialog();
        });
        content.addView(addToggleBtn);
        cardEnd();
    }
    private void stepsPage(){
        int s=steps();
        int goal=getStepGoal();
        int pct=goal>0?Math.min(100,s*100/goal):0;
        int accent = getAccentColor();
        content.addView(text("Step tracker",26,TEXT,true));
        content.addView(text("Daily goal · "+fmt(goal)+" steps",13,MUTED,false));
        content.addView(space(12));
        cardStart();
        content.addView(text(fmt(s),38,accent,true));
        content.addView(text("steps today",13,MUTED,false));
        content.addView(space(8));
        progressBar(pct);
        content.addView(text(Math.max(0,goal-s)+" remaining · ~"+String.format(Locale.US,"%.1f",s*.0007)+" km",13,MUTED,false));
        cardEnd();

        content.addView(space(12));
        cardStart();
        content.addView(text("Set step goal",17,TEXT,true));
        content.addView(text("Select a preset or type your custom goal below",12,MUTED,false));
        content.addView(space(8));

        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        int[] presetValues = {1000, 3000, 5000, 8000, 10000};
        for(int val : presetValues){
            Button pb = button(val / 1000 + "k");
            pb.setTextSize(12);
            pb.setPadding(dp(2), dp(6), dp(2), dp(6));
            final int targetGoal = val;
            pb.setOnClickListener(v -> {
                prefs.edit().putInt("step_goal", targetGoal).apply();
                Toast.makeText(this, "Goal set to " + fmt(targetGoal) + " steps", Toast.LENGTH_SHORT).show();
                showPage();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            lp.rightMargin = dp(4);
            presets.addView(pb, lp);
        }
        content.addView(presets);
        content.addView(space(8));

        EditText goalInput = new EditText(this);
        goalInput.setSingleLine(true);
        goalInput.setInputType(2);
        goalInput.setText(String.valueOf(goal));
        goalInput.setTextColor(TEXT);
        goalInput.setHintTextColor(MUTED);
        goalInput.setHint("Custom step goal");
        content.addView(goalInput);

        Button saveGoal = button("Save custom goal");
        saveGoal.setOnClickListener(v -> {
            try {
                int newGoal = Integer.parseInt(goalInput.getText().toString());
                if(newGoal < 100 || newGoal > 1000000) throw new Exception();
                prefs.edit().putInt("step_goal", newGoal).apply();
                Toast.makeText(this, "Goal updated to " + fmt(newGoal) + " steps", Toast.LENGTH_SHORT).show();
                showPage();
            } catch(Exception e) {
                Toast.makeText(this, "Enter a valid number between 100 and 1,000,000", Toast.LENGTH_SHORT).show();
            }
        });
        content.addView(saveGoal);
        cardEnd();

        content.addView(space(12));
        cardStart();
        content.addView(text("Manual step adjustment",17,TEXT,true));
        content.addView(text("The phone sensor adds steps automatically while tracking is enabled. Use this to correct today's total.",12,MUTED,false));
        EditText input=new EditText(this);input.setSingleLine(true);input.setInputType(2);input.setText(String.valueOf(s));input.setTextColor(TEXT);input.setHintTextColor(MUTED);input.setHint("Total steps today");content.addView(input);Button save=button("Save today's total");save.setOnClickListener(v->{try{int value=Integer.parseInt(input.getText().toString());if(value<0||value>100000)throw new Exception();String day=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());prefs.edit().putString("step_day",day).putLong("steps_today",value).apply();checkStepGoalCelebration();try{Intent sIntent=new Intent(MainActivity.this,StepCounterService.class);sIntent.setAction("UPDATE_NOTIFICATION");if(Build.VERSION.SDK_INT>=26)startForegroundService(sIntent);else startService(sIntent);}catch(Exception ignored){}showPage();}catch(Exception e){Toast.makeText(this,"Enter a number from 0 to 100,000",Toast.LENGTH_SHORT).show();}});content.addView(save);cardEnd();content.addView(space(12));cardStart();content.addView(text("Tracking status",17,TEXT,true));SensorManager sm=(SensorManager)getSystemService(SENSOR_SERVICE);boolean sensor=sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)!=null;content.addView(text(sensor?"Step-counter sensor detected":"No step-counter sensor found",14,sensor?getAccentColor():MUTED,true));content.addView(text("Keep Routine Track tracking enabled and allow physical activity permission. Some phones may pause background tracking due to battery settings.",12,MUTED,false));cardEnd();}
    private void progress(){
        int accent = getAccentColor();
        content.addView(text("Your progress",26,TEXT,true));
        content.addView(text("Your activity is saved on this device.",13,MUTED,false));
        content.addView(space(12));
        metric("WORKOUTS COMPLETED",String.valueOf(workouts()));
        metric("TODAY'S STEPS",fmt(steps()));
        metric("SETS COMPLETED THIS SESSION",countSetsDone()+" / "+getSetCount());
        metric("DAILY STEP GOAL",steps()>=getStepGoal()?"Reached ✓":"In progress");
        content.addView(space(8));

        // Interactive Monthly Calendar Card (Inside cardStart / cardEnd)
        buildMonthlyCalendarCard(content);
        content.addView(space(8));

        cardStart();
        content.addView(text("Weekly routine",18,TEXT,true));
        content.addView(space(6));

        String[] dayNames = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        for (int i = 0; i < 7; i++) {
            String def = (i == 2 || i == 5 || i == 6) ? "Rest" : "Workout";
            String stat = prefs.getString("routine_day_" + i, def);

            LinearLayout dayRow = new LinearLayout(this);
            dayRow.setOrientation(LinearLayout.HORIZONTAL);
            dayRow.setGravity(Gravity.CENTER_VERTICAL);
            dayRow.setPadding(0, dp(4), 0, dp(4));

            TextView dayTv = text(dayNames[i], 14, TEXT, true);
            LinearLayout.LayoutParams dayLp = new LinearLayout.LayoutParams(0, -2, 1f);
            dayRow.addView(dayTv, dayLp);

            TextView statTv = text(stat, 14, stat.equals("Workout") ? accent : MUTED, true);
            statTv.setGravity(Gravity.END);
            LinearLayout.LayoutParams statLp = new LinearLayout.LayoutParams(-2, -2);
            dayRow.addView(statTv, statLp);

            content.addView(dayRow);
        }

        content.addView(space(8));

        Button editRoutineBtn = button("Edit");
        editRoutineBtn.setBackground(round(BORDER,12));
        editRoutineBtn.setOnClickListener(v -> showEditRoutineDialog());
        content.addView(editRoutineBtn);
        cardEnd();

        content.addView(space(8));
        cardStart();
        content.addView(text("Data backup & restore", 18, TEXT, true));
        content.addView(text("Export your data before uninstalling or restore from a backup file.", 12, MUTED, false));
        content.addView(space(10));

        Button exportBtn = secondaryButton("Backup Data (Export)");
        exportBtn.setOnClickListener(v -> exportData());
        content.addView(exportBtn);

        content.addView(space(8));

        Button importBtn = secondaryButton("Restore Data (Import)");
        importBtn.setOnClickListener(v -> importData());
        content.addView(importBtn);
        cardEnd();

        content.addView(space(16));
    }
    private void metric(String label,String value){cardStart();content.addView(text(label,11,MUTED,true));content.addView(text(value,25,getAccentColor(),true));cardEnd();content.addView(space(8));}
    private String fmt(int n){return String.format(Locale.US,"%,d",n);}
    private void progressBar(int percent){
        android.widget.ProgressBar p=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        p.setMax(100);
        p.setProgress(Math.max(0,Math.min(100,percent)));
        int accent = getAccentColor();
        if(Build.VERSION.SDK_INT>=21){
            p.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
            p.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(BORDER));
        }
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(8));
        lp.topMargin=dp(8);
        lp.bottomMargin=dp(10);
        content.addView(p,lp);
    }
    private void cardStart(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(15),dp(16),dp(15));c.setBackground(round(PANEL,18));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(0);content.addView(c,lp);content=c;}
    private void cardEnd(){View v=content; if(v.getParent() instanceof LinearLayout){LinearLayout parent=(LinearLayout)v.getParent();content=parent;}}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setPadding(0,dp(3),0,dp(3));return t;}
    private Button button(String s){
        Button b=new Button(this);
        b.setText(s);
        b.setTextColor(0xFF11170A);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        int accent = getAccentColor();
        b.setBackground(round(accent,12));
        if (Build.VERSION.SDK_INT >= 21) {
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
        }
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(10);
        b.setPadding(dp(10),dp(9),dp(10),dp(9));
        return b;
    }
    private Button secondaryButton(String s){
        Button b=new Button(this);
        b.setText(s);
        b.setTextColor(TEXT);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(round(BORDER,12));
        if (Build.VERSION.SDK_INT >= 21) {
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(BORDER));
        }
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(10);
        b.setPadding(dp(10),dp(9),dp(10),dp(9));
        return b;
    }
    private Button iconButton(String s){
        Button b=new Button(this);
        b.setText(s);
        b.setTextColor(TEXT);
        b.setTextSize(10);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(round(BORDER,6));
        if (Build.VERSION.SDK_INT >= 21) {
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(BORDER));
        }
        b.setPadding(0,0,0,0);
        return b;
    }
    private android.graphics.drawable.GradientDrawable round(int color,int radius){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private View space(int h){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));return v;}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onResume(){
        super.onResume();
        receiver=new BroadcastReceiver(){
            @Override public void onReceive(Context c,Intent i){
                checkStepGoalCelebration();
                if(content!=null) showPage();
            }
        };
        IntentFilter f=new IntentFilter("com.reptrack.app.STEPS_UPDATED");
        if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver,f);
        checkStepGoalCelebration();
        if(content!=null) showPage();
    }
    @Override protected void onPause(){super.onPause();if(receiver!=null){try{unregisterReceiver(receiver);}catch(Exception ignored){}receiver=null;}}
}
