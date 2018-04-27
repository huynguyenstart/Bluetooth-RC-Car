package com.example.pc.rccar;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    public static int NOISE_REMOVE_FACT = 4;
    public static int PRESS_DOWN_EFFECT = 5;

    Display display;
    Point size;
    int exitPointX = 0;
    int exitPointY = 0;
    ImageView rcwheel;
    ImageView toWard;
    ImageView backWard;
    double angle = 0;
    View contentView;
    View closeView;
    MySpinner listDevice;
    WindowManager windowManager;
    WindowManager.LayoutParams p;
    LayoutInflater inflater;
    ImageView closeHead;
    int[] padding2close = new int[2];
    Button conn;

    private BluetoothAdapter myBluetooth = null;
    private Set pairedDevices;
    private ProgressDialog progress;
    private boolean isBtConnected = false;
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private String address = null;
    BluetoothSocket btSocket = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        display = getWindowManager().getDefaultDisplay();
        size = new Point();

        inflater = (LayoutInflater) MainActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        contentView = inflater.inflate(R.layout.game_pad, null, false);
        closeView = inflater.inflate(R.layout.game_pad_close, null, false);

        rcwheel = (ImageView) contentView.findViewById(R.id.car_wheel);
        rcwheel.setOnTouchListener(new ChoiceTouchListener());

        toWard = (ImageView) contentView.findViewById(R.id.car_toward);
        toWard.setOnTouchListener(new CarToWardListenner() );
        backWard = (ImageView) contentView.findViewById(R.id.car_fordward);
        backWard.setOnTouchListener( new CarBackWardListenner() );

        listDevice = (MySpinner) contentView.findViewById(R.id.listDevice);

        listDevice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String info = adapterView.getItemAtPosition(i).toString();
                address = info.substring(info.length() - 17);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
        ArrayList items = new ArrayList();
        items.add( "Press scan button to get list of paired devices" );
        items.add( "This is for test and should have length more than 17" );
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        listDevice.setAdapter(adapter);

        final Button scan = (Button) contentView.findViewById(R.id.connect);
        scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pairedDevicesList();
                listDevice.performClick();
            }
        });

        closeHead = (ImageView) contentView.findViewById(R.id.close);
        closeHead.setOnTouchListener( new MoveHeadListenner() );
        closeHead.setOnClickListener(new CloseGamePadListenner() );

        ImageView openGamePad = (ImageView) closeView.findViewById(R.id.open_game_pad);
        openGamePad.setOnTouchListener( new OpenGamePadListenner() );

        conn = (Button) contentView.findViewById(R.id.disconnect);
        conn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if( !address.contains(":")) address = "";
                if( !address.isEmpty() && btSocket == null ) {
                    new ConnectBT().execute();
                    conn.setText("Disc");
                } else {
                    Disconnect();
                    conn.setText("Conn");
                }
            }
        });

        myBluetooth = BluetoothAdapter.getDefaultAdapter();
        if (myBluetooth == null) {
            //Show a message that the device has no bluetooth adapter
            Toast.makeText(getApplicationContext(), "Bluetooth Device Not Available", Toast.LENGTH_LONG).show();
        } else {
            if (myBluetooth.isEnabled()) {
            } else {
                //Ask to the user turn the bluetooth on
                Intent turnBTon = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(turnBTon, 1);
            }
        }

        showTransparent();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void showTransparent(){
        p = new WindowManager.LayoutParams(
                // Shrink the window to wrap the content rather than filling the screen
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                // Display it on top of other application windows, but only for the current user
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                // Don't let it grab the input focus
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                //WindowManager.LayoutParams.FLAG_LOCAL_FOCUS_MODE,
                // Make the underlying application window visible through any transparent parts
                PixelFormat.TRANSLUCENT);

        // Define the position of the window within the screen
        p.gravity = Gravity.TOP | Gravity.LEFT;
        p.x = 0;
        p.y = 0;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        windowManager.addView(closeView, p);
        p.x = closeView.getWidth()/2;
        p.y = closeView.getHeight()/2;
        moveTaskToBack(true);
    }

    private final class MoveHeadListenner implements View.OnTouchListener {
        private int lastAction;
        private int initialX;
        private int initialY;
        private float initialTouchX;
        private float initialTouchY;
        private int xMove = 0;
        private int yMove = 0;
        private int lastMoveCount = 0;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:

                    lastMoveCount = 0;
                    //remember the initial position.
                    initialX = p.x;
                    initialY = p.y;

                    //get the touch location
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();

                    lastAction = event.getAction();
                    return true;
                case MotionEvent.ACTION_UP:
                    lastMoveCount = 0;
                    if (lastAction == MotionEvent.ACTION_DOWN) {
                        int[] location = new int[2];
                        closeHead.getLocationOnScreen(location);
                        int[] locationParent = new int[2];
                        contentView.getLocationOnScreen(locationParent);
                        padding2close[0] = location[0] - locationParent[0];
                        padding2close[1] = location[1] - locationParent[1];
                        p.x = location[0];
                        p.y = location[1] - MainActivity.this.getStatusBarHeight();
                        p.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_USER;
                        windowManager.removeViewImmediate(contentView);
                        windowManager.addView(closeView, p);
                        windowManager.updateViewLayout(closeView, p);
                    }
                    lastAction = event.getAction();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    //Calculate the X and Y coordinates of the view.
                    xMove = (int) (event.getRawX() - initialTouchX);
                    yMove = (int) (event.getRawY() - initialTouchY);
                    p.x = initialX + xMove;
                    p.y = initialY + yMove;

                    //Update the layout with new X & Y coordinate
                    windowManager.updateViewLayout(contentView, p);

                    lastMoveCount++;
                    if (lastMoveCount > NOISE_REMOVE_FACT) lastAction = event.getAction();
                    return true;
            }
            return false;
        }
    }

    private final class CloseGamePadListenner implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            int[] location = new int[2];
            closeHead.getLocationOnScreen(location);
            int[] locationParent = new int[2];
            contentView.getLocationOnScreen(locationParent);
            padding2close[0] = location[0] - locationParent[0];
            padding2close[1] = location[1] - locationParent[1];
            p.x = location[0];
            p.y = location[1];
            windowManager.removeViewImmediate(contentView);
            windowManager.addView(closeView, p);
            windowManager.updateViewLayout(closeView, p);
        }
    }

    private final class OpenGamePadListenner implements View.OnTouchListener {
        private int lastAction;
        private int initialX;
        private int initialY;
        private float initialTouchX;
        private float initialTouchY;
        private int xMove = 0;
        private int yMove = 0;
        private int lastMoveCount = 0;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastMoveCount = 0;
                    //remember the initial position.
                    initialX = p.x;
                    initialY = p.y;

                    //get the touch location
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();

                    lastAction = event.getAction();
                    return true;
                case MotionEvent.ACTION_UP:
                    lastMoveCount = 0;
                    System.out.println("up touch: " + lastAction);
                    if (lastAction == MotionEvent.ACTION_DOWN) {
                        int[] location = new int[2];
                        closeView.getLocationOnScreen(location);
                        p.x = location[0] - padding2close[0];
                        p.y = location[1] - padding2close[1] - MainActivity.this.getStatusBarHeight();
                        windowManager.removeViewImmediate(closeView);
                        windowManager.addView(contentView, p);
                        windowManager.updateViewLayout(contentView, p);
                    } else if( lastAction == MotionEvent.ACTION_MOVE ){
                        System.out.println("Exit app?" + exitPointX + "=="+ p.x +"&&"+ exitPointY +"=="+ p.y);
                        if( exitPointX == p.x && exitPointY == p.y ){
                            System.out.println("Exit app!...");
                            System.exit(0);
                        }
                    }
                    lastAction = event.getAction();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    //Calculate the X and Y coordinates of the view.
                    xMove = (int) (event.getRawX() - initialTouchX);
                    yMove = (int) (event.getRawY() - initialTouchY);
                    p.x = initialX + xMove;
                    p.y = initialY + yMove;

                    // Check exit app condition
                    display.getSize(size);
                    int screenW = size.x;
                    int screenH = size.y;

                    float radius = size.x / 3;
                    if( screenW > screenH){
                        // Landscape Mode
                        radius = size.y / 3;
                    }

                    float halfCloseHead = closeHead.getWidth()/2;
                    if(halfCloseHead == 0 ) halfCloseHead = closeView.getWidth()/2;
                     float yRange = screenH - radius - halfCloseHead;
                    float xRangeMin = screenW/2 - radius - halfCloseHead;
                    float xRangeMax = screenW/2 + radius - halfCloseHead;
                    exitPointX = (int) (screenW / 2 - halfCloseHead ) ;
                    exitPointY = (int) (screenH - closeHead.getHeight() - closeHead.getHeight()/4);

                    if( p.y > yRange && p.x > xRangeMin && p.x < xRangeMax ){
                        p.x = exitPointX;
                        p.y = exitPointY;
                    }

                    //Update the layout with new X & Y coordinate
                    windowManager.updateViewLayout(closeView, p);

                    lastMoveCount++;
                    if (lastMoveCount > NOISE_REMOVE_FACT) lastAction = event.getAction();
                    return true;
            }
            return false;
        }
    }

    private final class ChoiceTouchListener implements View.OnTouchListener {
        int Xp = 0;
        int Yp = 0;
        float pivotX = 0;
        float pivotY = 0;
        double p_angle;
        boolean runback = false;

        public boolean onTouch(View view, MotionEvent event) {
            rcwheel.setScaleType(ImageView.ScaleType.MATRIX);   //required
            int[] location = new int[2];
            contentView.getLocationOnScreen(location);
            pivotX = location[0] + rcwheel.getX() + rcwheel.getWidth() / 2;
            pivotY = location[1] + rcwheel.getY() + rcwheel.getHeight() / 2;// + getStatusBarHeight();
            int padding = rcwheel.getWidth() / 6;
            final int X = (int) event.getRawX();
            final int Y = (int) event.getRawY();
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    runback = false;
                    Xp = X;
                    Yp = Y;
                    break;
                case MotionEvent.ACTION_MOVE:
                    //calc angle
                    float tana = (Yp - pivotY) / (Xp - pivotX);
                    float tanb = (Y - pivotY) / (X - pivotX);

                    double BDegree = Math.atan(tanb);
                    double ADegree = Math.atan(tana);

                    if (X < pivotX) {
                        BDegree = Math.PI + BDegree;
                    } else {
                        if (Y < pivotY) BDegree += Math.PI * 2;
                    }
                    if (Xp < pivotX) {
                        ADegree = Math.PI + ADegree;
                    } else {
                        if (Yp < pivotY) ADegree += Math.PI * 2;
                    }

                    if (Xp < X) {
                        if (Yp < pivotY) {
                            // clock
                            if (BDegree < ADegree) BDegree += Math.PI * 2;
                        } else {
                            if (BDegree > ADegree) ADegree += Math.PI * 2;
                        }
                    } else {
                        if (Yp < pivotY) {
                            if (BDegree > ADegree) ADegree += Math.PI * 2;
                        } else {
                            // clock
                            if (BDegree < ADegree) BDegree += Math.PI * 2;
                        }
                    }

                    p_angle = Math.toDegrees(BDegree - ADegree);
                    p_angle = Math.abs(p_angle);
                    if (p_angle > 180) p_angle = 360 - p_angle;

                    boolean clockDirection = true;

                    if (Yp < pivotY - padding) {
                        if (Xp < X) clockDirection = true;
                        if (Xp > X) clockDirection = false;
                    } else if (Yp > pivotY + padding) {
                        if (Xp > X) clockDirection = true;
                        if (Xp < X) clockDirection = false;
                    } else if (Xp < pivotX - padding) {
                        if (Yp < Y) clockDirection = false;
                        if (Yp > Y) clockDirection = true;
                    } else if (Xp > pivotX + padding) {
                        if (Yp > Y) clockDirection = false;
                        if (Yp < Y) clockDirection = true;
                    } else {
                        p_angle = 0;
                    }

                    if (!clockDirection) p_angle = -p_angle;

                    if (angle + p_angle > 90) p_angle = 90 - (angle + p_angle);
                    if (angle + p_angle < -90) p_angle = -90 - (angle + p_angle);
                    //rotate
                    Matrix matrix = new Matrix();
                    matrix.postRotate((float) (angle + p_angle), rcwheel.getWidth() / 2, rcwheel.getHeight() / 2);
                    rcwheel.setImageMatrix(matrix);

                    // SAVE FOR NEXT
                    Xp = X;
                    Yp = Y;
                    angle += p_angle;

                    if( Math.abs(p_angle) >= 0.5 ) writeWheel(angle);
                    break;
                case MotionEvent.ACTION_UP:
                    angle += p_angle;
                    runback = true;
                    runBack();
                    break;
            }
            return true;
        }

        public void runBack() {
            final int esp = 8;
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    //TODO your background code
                    try {
                        while (runback) {
                            if (angle > 0) {
                                if (angle >= esp) {
                                    angle -= esp;
                                } else {
                                    angle = 0;
                                }
                            } else {
                                if (angle <= -esp) {
                                    angle += esp;
                                } else {
                                    angle = 0;
                                }
                            }

                            rcwheel.post(new Runnable() {
                                public void run() {
                                    Matrix matrix = new Matrix();
                                    matrix.postRotate((float) angle, rcwheel.getWidth() / 2, rcwheel.getHeight() / 2);
                                    rcwheel.setImageMatrix(matrix);
                                }
                            });
                            writeWheel(angle);
                            if (angle == 0) break;
                            Thread.sleep(1);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

    }

    private final class CarToWardListenner implements View.OnTouchListener {
        Matrix matrix;
        public boolean onTouch(View view, MotionEvent event) {
            toWard.setScaleType(ImageView.ScaleType.MATRIX);
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    matrix = new Matrix();
                    matrix.setTranslate(PRESS_DOWN_EFFECT, PRESS_DOWN_EFFECT);
                    toWard.setImageMatrix(matrix);

                    writeToward(true);
                    break;
                case MotionEvent.ACTION_MOVE:

                    break;
                case MotionEvent.ACTION_UP:
                    matrix = new Matrix();
                    matrix.setTranslate(0, 0);
                    toWard.setImageMatrix(matrix);

                    writeToward(false);
                    break;
            }
            return true;
        }
    }

    private final class CarBackWardListenner implements View.OnTouchListener {
        Matrix matrix = new Matrix();
        public boolean onTouch(View view, MotionEvent event) {
            backWard.setScaleType(ImageView.ScaleType.MATRIX);
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    matrix.setTranslate(PRESS_DOWN_EFFECT, PRESS_DOWN_EFFECT);
                    backWard.setImageMatrix(matrix);
                    writeBackward(true);
                    break;
                case MotionEvent.ACTION_MOVE:

                    break;
                case MotionEvent.ACTION_UP:
                    matrix.setTranslate(0, 0);
                    backWard.setImageMatrix(matrix);
                    writeBackward(false);
                    break;
            }
            return true;
        }
    }

    public int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    private void pairedDevicesList() {
        pairedDevices = myBluetooth.getBondedDevices();
        ArrayList list = new ArrayList();

        if (pairedDevices.size() > 0) {
            for (Object oj : pairedDevices) {
                BluetoothDevice bt = (BluetoothDevice) oj;
                list.add(bt.getName() + "\n" + bt.getAddress()); //Get the device's name and the address
            }
        } else {
            Toast.makeText(getApplicationContext(), "No Paired Bluetooth Devices Found.", Toast.LENGTH_LONG).show();
        }

        final ArrayAdapter<String> adapter = new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, list);
        listDevice.setAdapter(adapter);
    }

    private class ConnectBT extends AsyncTask<Void, Void, Void>  // UI thread
    {
        private boolean ConnectSuccess = true;

        @Override
        protected void onPreExecute()
        {
            progress = ProgressDialog.show(MainActivity.this, "Connecting...", "Please wait!!!");
        }

        @Override
        protected Void doInBackground(Void... devices)
        {
            if( !address.contains(":")) address = "";
            try
            {
                if ( !address.isEmpty() && ( btSocket == null || !isBtConnected ) )
                {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                    BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);//connects to the device's address and checks if it's available
                    btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RFCOMM (SPP) connection
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    btSocket.connect();//start connection
                }
            }
            catch (IOException e)
            {
                ConnectSuccess = false;
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result)
        {
            super.onPostExecute(result);

            if (!ConnectSuccess)
            {
                msg("Connection Failed. Is it a SPP Bluetooth? Try again.");
                finish();
            }
            else
            {
                msg("Connected.");
                isBtConnected = true;
            }
            progress.dismiss();
        }
    }

    private void msg(String s)
    {
        Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show();
    }

    private void Disconnect()
    {
        if (btSocket!=null) //If the btSocket is busy
        {
            try
            {
                btSocket.close(); //close connection
                btSocket = null;
            }
            catch (IOException e)
            { msg("Error");}
        }
    }

    public void writeWheel (double angle){
        if (btSocket!=null) {
            int value = (int) (((angle + 90) / 180) * 100);
            try {
                btSocket.getOutputStream().write(("T" + String.valueOf(value)).getBytes());
            } catch (IOException e) {

            }
        }
    }

    private void writeToward(boolean status)
    {
        if (btSocket!=null)
        {
            String code = "STG";
            if( status ) code = "GOO";
            try
            {
                btSocket.getOutputStream().write(code.getBytes());
            }
            catch (IOException e)
            {
                msg("Error");
            }
        }
    }

    private void writeBackward(boolean status)
    {
        if (btSocket!=null)
        {
            String code = "STB";
            if( status ) code = "BAC";
            try
            {
                btSocket.getOutputStream().write(code.getBytes());
            }
            catch (IOException e)
            {
                msg("Error");
            }
        }
    }
}
