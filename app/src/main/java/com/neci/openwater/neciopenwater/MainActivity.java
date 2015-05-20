package com.neci.openwater.neciopenwater;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    final Context context = this;

    private static final String TAG = "MainActivity";

    Button readingButton;
    Spinner waterSpinner;
    ProgressBar progressBar;
    TextView readingDisplay;

    Handler handler;

    Context c;

    private static BluetoothManager blueMan;

    boolean flag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        blueMan = new BluetoothManager(this);

        progressBar = (ProgressBar) this.findViewById(R.id.progressBar);
        readingDisplay = (TextView) this.findViewById(R.id.readingDisplay);
        waterSpinner = (Spinner) this.findViewById(R.id.unit_types);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.water_unit_types, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        waterSpinner.setAdapter(adapter);
    }

    public void takeUniqueReading(View v)
    {
        if(!blueMan.startUp(this))
        {
            return;
        }

        flag = false;

        progressBar.setVisibility(View.VISIBLE);

        if(!blueMan.isConnected())
            blueMan.Connect();

        String dataToSend =  "select 0\n";


        handler = new Handler() {
            public void handleMessage(Message msg)
            {
                Bundle bundle = msg.getData();
                String data = bundle.getString(BluetoothManager.KEY);
                if(data.equals("ACK"))
                {
                    flag = true;
                    String message = "test\n";
                    blueMan.getReading(message);

                }
                else if (data.equals("NACK"))
                {
                    Toast.makeText(getApplicationContext(), "Failed to change banks, please try again.", Toast.LENGTH_LONG).show();
                    return;
                }
                else if (flag)
                {
                    readingDisplay.setText(convertReading(Double.parseDouble(data)));
                    readingDisplay.setVisibility(View.VISIBLE);
                    progressBar.setVisibility(View.GONE);

                    flag = false;
                }
                else if(data.equals("Error"))
                {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(getApplicationContext(), "Connection Failed", Toast.LENGTH_LONG).show();
                }
                else
                {
                    Toast.makeText(getApplicationContext(), "Error communicating, reboot photometer", Toast.LENGTH_LONG).show();
                }
            }
        };

        blueMan.setHandler(handler);
        if (!dataToSend.equals("NULL"))
        {
            blueMan.getReading(dataToSend);
        }
        else
            Toast.makeText(getApplicationContext(), "No message to send.", Toast.LENGTH_LONG).show();
    }

    public String convertReading(double reading)
    {
        //do math here
        reading = -1 * Math.log10(reading);
        reading = (reading - 0.0007) / 0.0695;

        String units = (String)waterSpinner.getSelectedItem();

        if (units.equals("Standard"))
            return (roundTwoDecimals(reading) + "ppm Nitrate-N");
        else if (units.equals("US EPA"))
            return (roundTwoDecimals(reading) + "ppm Nitrate-N");
        else if (units.equals("WHO"))
            return (roundTwoDecimals((reading * 4.4)) + "ppm Nitrate");
        else if (units.equals("US EPA Nitrite"))
            return (roundTwoDecimals(reading) + "ppm Nitrite-N");
        else if (units.equals("WHO Nitrite"))
            return (roundTwoDecimals((reading * 3.3)) + "ppm Nitrite");

        return (roundTwoDecimals(reading) + "ppm Nitrate-N");
    }

    private double roundTwoDecimals(double d)
    {
        DecimalFormat twoDForm = new DecimalFormat("#.##");
        return Double.valueOf(twoDForm.format(d));

    }
}