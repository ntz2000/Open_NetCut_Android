package com.serhat.open_cut;

/*
 * This file is part of the dSploit.
 *
 * Copyleft of Simone Margaritelli aka evilsocket <evilsocket@gmail.com>
 *
 * dSploit is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * dSploit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with dSploit.  If not, see <http://www.gnu.org/licenses/>.
 */


//Edited by me
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseIntArray;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;



public class System
{
    public static final String SESSION_MAGIC = "DSS";

    private static final String ERROR_LOG_FILENAME = "dsploit-debug-error.log";
    private static final Pattern SERVICE_PARSER = Pattern.compile("^([^\\s]+)\\s+(\\d+).*$", Pattern.CASE_INSENSITIVE);
    public static int HTTP_PROXY_PORT = 8080;
    public static int HTTP_SERVER_PORT = 8081;
    public static int HTTPS_REDIR_PORT = 8082;
    public static int MSF_RPC_PORT = 55553;

    public static final String IPV4_FORWARD_FILEPATH = "/proc/sys/net/ipv4/ip_forward";

    private static boolean mInitialized = false;
    private static String mLastError = "";
    private static String mSuPath = null;

    private static Context mContext = null;
    private static WifiLock mWifiLock = null;
    private static WakeLock mWakeLock = null;
    private static Network mNetwork = null;
    private static final Vector<Target> mTargets = new Vector<Target>();
    private static int mCurrentTarget = 0;
    private static Map<String, String> mServices = null;
    private static Map<String, String> mPorts = null;
    private static Map<String, String> mVendors = null;
    private static SparseIntArray mOpenPorts = null;
    private static String TAG = "System";



    private static String mStoragePath = null;
    private static String mSessionName = null;

    private static String mLocalApkVersion = null;
    private static Vector<Integer> mCutTargets = null;



    private static Object mCustomData = null;



    public static void init(Context context) throws Exception{
        mContext = context;
        try{
            mStoragePath = getSettings().getString("PREF_SAVE_PATH", Environment.getExternalStorageDirectory().toString());
            mSessionName = "dsploit-session-" + java.lang.System.currentTimeMillis();
            mOpenPorts = new SparseIntArray(3);
            mCutTargets = new Vector<Integer>();

            // if we are here, network initialization didn't throw any error, lock wifi
            WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);

            if(mWifiLock == null)
                mWifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "wifiLock");

            if(!mWifiLock.isHeld())
                mWifiLock.acquire();

            // wake lock if enabled
            if(getSettings().getBoolean("PREF_WAKE_LOCK", true)){
                PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);

                if(mWakeLock == null)
                    mWakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, "wakeLock");

                if(!mWakeLock.isHeld())
                    mWakeLock.acquire();
            }

            // set ports
            try{
                HTTP_PROXY_PORT  = Integer.parseInt(getSettings().getString("PREF_HTTP_PROXY_PORT", "8080"));
                HTTP_SERVER_PORT = Integer.parseInt(getSettings().getString("PREF_HTTP_SERVER_PORT", "8081"));
                HTTPS_REDIR_PORT = Integer.parseInt(getSettings().getString("PREF_HTTPS_REDIRECTOR_PORT", "8082"));
                MSF_RPC_PORT     = Integer.parseInt(getSettings().getString("MSF_RPC_PORT", "5553"));
            } catch(NumberFormatException e){
                HTTP_PROXY_PORT = 8080;
                HTTP_SERVER_PORT = 8081;
                HTTPS_REDIR_PORT = 8082;
            }



            // initialize network data at the end
            mNetwork = new Network(mContext);

            Target network = new Target(mNetwork),
                    gateway = new Target(mNetwork.getGatewayAddress(), mNetwork.getGatewayHardware()),
                    device = new Target(mNetwork.getLocalAddress(), mNetwork.getLocalHardware());

            gateway.setAlias(mNetwork.getSSID());
            device.setAlias(android.os.Build.MODEL);

            mTargets.add(network);
            mTargets.add(gateway);
            mTargets.add(device);

            mInitialized = true;
        }
        catch(Exception e){
            errorLogging(e);

            throw e;
        }
    }




    public static void reloadNetworkMapping(){
        try{
            mNetwork = new Network(mContext);

            Target network = new Target(mNetwork),
                    gateway = new Target(mNetwork.getGatewayAddress(), mNetwork.getGatewayHardware()),
                    device = new Target(mNetwork.getLocalAddress(), mNetwork.getLocalHardware());

            gateway.setAlias(mNetwork.getSSID());
            device.setAlias(android.os.Build.MODEL);

            mTargets.clear();
            mTargets.add(network);
            mTargets.add(gateway);
            mTargets.add(device);

            mInitialized = true;
        }
        catch(NoRouteToHostException nrthe){
            // swallow bitch
        }
        catch(Exception e){
            errorLogging(e);
        }
    }

    public static boolean checkNetworking(final Activity current){
        if(!Network.isWifiConnected(mContext)){
            AlertDialog.Builder builder = new AlertDialog.Builder(current);

            builder.setCancelable(false);
            builder.setTitle(current.getString(R.string.error));
            builder.setMessage(current.getString(R.string.wifi_went_down));
            builder.setPositiveButton("Ok", new DialogInterface.OnClickListener(){
                @Override
                public void onClick(DialogInterface dialog, int which){
                    dialog.dismiss();

                    Bundle bundle = new Bundle();
                    bundle.putBoolean("WifiScannerActivity.CONNECTED", false);

                    Intent intent = new Intent();
                    intent.putExtras(bundle);

                    current.setResult(Activity.RESULT_OK, intent);
                    current.finish();
                }
            });

            AlertDialog alert = builder.create();
            alert.show();

            return false;
        }

        return true;
    }

    public static void setLastError(String error){
        mLastError = error;
    }

    public static String getLastError(){
        return mLastError;
    }

    public static synchronized void errorLogging(Exception e){
        String message = "Unknown error.",
                trace = "Unknown trace.",
                filename = (new File(Environment.getExternalStorageDirectory().toString(), ERROR_LOG_FILENAME)).getAbsolutePath();

        if(e != null){
            if(e.getMessage() != null && !e.getMessage().isEmpty())
                message = e.getMessage();

            else if(e.toString() != null)
                message = e.toString();

            if(message.equals(mLastError))
                return;

            Writer sWriter = new StringWriter();
            PrintWriter pWriter = new PrintWriter(sWriter);

            e.printStackTrace(pWriter);

            trace = sWriter.toString();

            if(mContext != null && getSettings().getBoolean("PREF_DEBUG_ERROR_LOGGING", false)){
                try{
                    FileWriter fWriter = new FileWriter(filename, true);
                    BufferedWriter bWriter = new BufferedWriter(fWriter);

                    bWriter.write(trace);

                    bWriter.close();
                }
                catch(IOException ioe){
                    Log.e(TAG,ioe.toString());

                }
            }
        }


        setLastError(message);
        Log.e(TAG,message);
        Log.e(TAG,trace);

    }

    public static synchronized void errorLog(String tag, String data){
        String filename = (new File(Environment.getExternalStorageDirectory().toString(), ERROR_LOG_FILENAME)).getAbsolutePath();

        data = data.trim();

        if(mContext != null && getSettings().getBoolean("PREF_DEBUG_ERROR_LOGGING", false)){
            try{
                FileWriter fWriter = new FileWriter(filename, true);
                BufferedWriter bWriter = new BufferedWriter(fWriter);

                bWriter.write(data);

                bWriter.close();
            }
            catch(IOException ioe){
                Log.e(TAG,ioe.toString());

            }
        }
        Log.e(TAG,data);


    }

    public static boolean isARM(){
        String abi = Build.CPU_ABI;

        Log.d(TAG,"CPU" + abi);



        return Build.CPU_ABI.toLowerCase().startsWith("armeabi");
    }

    public static synchronized void setCustomData(Object data){
        mCustomData = data;
    }

    public static Object getCustomData(){
        return mCustomData;
    }

    public static InputStream getRawResource(int id){
        return mContext.getResources().openRawResource(id);
    }



    public static String getSuPath(){

        if(mSuPath != null)
            return mSuPath;

        try{
            Process process = Runtime.getRuntime().exec("which su");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while((line = reader.readLine()) != null){
                if(!line.isEmpty() && line.startsWith("/")){
                    mSuPath = line;
                    break;
                }
            }

            return mSuPath;
        }
        catch(Exception e){
            errorLogging(e);
        }

        return "su";
    }

    private static void preloadServices(){
        if(mServices == null || mPorts == null){
            try{
                // preload network service map and mac vendors
                mServices = new HashMap<String, String>();
                mPorts = new HashMap<String, String>();

                @SuppressWarnings("ConstantConditions")
                FileInputStream fstream = new FileInputStream(mContext.getFilesDir().getAbsolutePath() + "/tools/nmap/nmap-services");

                DataInputStream in = new DataInputStream(fstream);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String line;
                Matcher matcher;

                while((line = reader.readLine()) != null){
                    line = line.trim();

                    if((matcher = SERVICE_PARSER.matcher(line)) != null && matcher.find()){
                        String proto = matcher.group(1),
                                port = matcher.group(2);

                        mServices.put(proto, port);
                        mPorts.put(port, proto);
                    }
                }

                in.close();
            }
            catch(Exception e){
                errorLogging(e);
            }
        }
    }

    private static void preloadVendors(){
        if(mVendors == null){
            try{
                mVendors = new HashMap<String, String>();
                @SuppressWarnings("ConstantConditions")
                FileInputStream fstream = new FileInputStream(mContext.getFilesDir().getAbsolutePath() + "/tools/nmap/nmap-mac-prefixes");

                DataInputStream in = new DataInputStream(fstream);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String line;

                while((line = reader.readLine()) != null){
                    line = line.trim();
                    if(!line.startsWith("#") && !line.isEmpty()){
                        String[] tokens = line.split(" ", 2);

                        if(tokens.length == 2)
                            mVendors.put(tokens[0], tokens[1]);
                    }
                }

                in.close();
            }
            catch(Exception e){
                errorLogging(e);
            }
        }
    }



    public static String getSessionName(){
        return mSessionName;
    }

    public static String getStoragePath(){
        return mStoragePath;
    }

    public static SharedPreferences getSettings(){
        return PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    public static String getAppVersionName(){
        if(mLocalApkVersion!=null)
            return mLocalApkVersion;
        try{
            PackageManager manager = mContext.getPackageManager();
            PackageInfo info = manager != null ? manager.getPackageInfo(mContext.getPackageName(), 0) : null;

            return (mLocalApkVersion = info.versionName);
        }
        catch(NameNotFoundException e){
            errorLogging(e);
        }

        return "?";
    }




    public static boolean isServiceRunning(String name){
        ActivityManager manager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);

        //noinspection ConstantConditions
        for(RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)){
            if(name.equals(service.service.getClassName()))
                return true;
        }

        return false;
    }

    public static boolean isPortAvailable(int port){
        boolean available = true;

        int available_code = mOpenPorts.get(port);

        if(available_code != 0)
            return available_code != 1;

        try{
            // attempt 3 times since proxy and server could be still releasing
            // their ports
            for(int i = 0; i < 3; i++){
                Socket channel = new Socket();
                InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(mNetwork.getLocalAddressAsString()), port);

                channel.connect(address, 200);

                available = !channel.isConnected();

                channel.close();

                if(available)
                    break;

                Thread.sleep(200);
            }
        }
        catch(Exception e){
            available = true;
        }

        mOpenPorts.put(port, available ? 2 : 1);

        return available;
    }

    public static ArrayList<String> getAvailableSessionFiles(){
        ArrayList<String> files = new ArrayList<String>();
        File storage = new File(mStoragePath);

        if(storage.exists()){
            String[] children = storage.list();

            if(children != null && children.length > 0){
                for(String child : children){
                    if(child.endsWith(".dss"))
                        files.add(child);
                }
            }
        }

        return files;
    }

    public static String saveSession(String sessionName) throws IOException{
        StringBuilder builder = new StringBuilder();
        String filename = mStoragePath + '/' + sessionName + ".dss",
                session;

        builder.append(SESSION_MAGIC + "\n");

        // skip the network target
        builder.append(mTargets.size() - 1).append("\n");
        for(Target target : mTargets){
            if(target.getType() != Target.Type.NETWORK)
                target.serialize(builder);
        }
        builder.append(mCurrentTarget).append("\n");

        session = builder.toString();

        FileOutputStream ostream = new FileOutputStream(filename);
        GZIPOutputStream gzip = new GZIPOutputStream(ostream);

        gzip.write(session.getBytes());

        gzip.close();

        mSessionName = sessionName;

        return filename;
    }



    public static String getImageMimeType(String fileName){
        String type = "image/jpeg",
                name = fileName.toLowerCase();

        if(name.endsWith(".jpeg") || name.endsWith(".jpg"))
            type = "image/jpeg";

        else if(name.endsWith(".png"))
            type = "image/png";

        else if(name.endsWith(".gif"))
            type = "image/gif";

        else if(name.endsWith(".tiff"))
            type = "image/tiff";

        return type;
    }

    public static void reset() throws NoRouteToHostException, SocketException{
        mTargets.clear();

        // local network
        mTargets.add(new Target(System.getNetwork()));
        // network gateway
        mTargets.add(new Target(System.getNetwork().getGatewayAddress(), System.getNetwork().getGatewayHardware()));
        // device network address
        mTargets.add(new Target(System.getNetwork().getLocalAddress(), System.getNetwork().getLocalHardware()));

        mCurrentTarget = 0;
    }

    public static boolean isInitialized(){
        return mInitialized;
    }

    public static String getMacVendor(byte[] mac){
        preloadVendors();

        if(mac != null && mac.length >= 3)
            return mVendors.get(String.format("%02X%02X%02X", mac[0], mac[1], mac[2]));
        else
            return null;
    }

    public static String getProtocolByPort(String port){
        preloadServices();

        return mPorts.containsKey(port) ? mPorts.get(port) : null;
    }

    public static int getPortByProtocol(String protocol){
        preloadServices();

        return mServices.containsKey(protocol) ? Integer.parseInt(mServices.get(protocol)) : 0;
    }

    public static Context getContext(){
        return mContext;
    }

    public static Network getNetwork() {
        return mNetwork;
    }

    public static Vector<Target> getTargets(){
        return mTargets;
    }

    public static ArrayList<Target> getTargetsByType(Target.Type type){
        ArrayList<Target> filtered = new ArrayList<Target>();

        for(Target target : mTargets){
            if(target.getType() == type)
                filtered.add(target);
        }

        return filtered;
    }

    public static ArrayList<Endpoint> getNetworkEndpoints(){
        ArrayList<Endpoint> filtered = new ArrayList<Endpoint>();

        for(Target target : mTargets){
            if(target.getType() == Target.Type.ENDPOINT)
                filtered.add(target.getEndpoint());
        }

        return filtered;
    }

    public static void addTarget(int index, Target target){
        mTargets.add(index, target);
        // update current target index
        if(mCurrentTarget >= index)
            mCurrentTarget++;
    }

    public static void addTarget(Target target){
        mTargets.add(target);
    }

    public static boolean addOrderedTarget(Target target){
        if(target != null && !hasTarget(target)){
            for(int i = 0; i < getTargets().size(); i++){
                if(getTarget(i).comesAfter(target)){
                    addTarget(i, target);
                    return true;
                }
            }

            addTarget(target);

            return true;
        }

        return false;
    }

    public static Target getTarget(int index){
        return mTargets.get(index);
    }

    public static boolean hasTarget(Target target){
        return mTargets.contains(target);
    }

    public static void addCutTarget(int index) {
        if (!mCutTargets.contains(Integer.valueOf(index))) {
            mCutTargets.add(Integer.valueOf(index));
        }
    }

    public static void removeCutTarget(int index) {
        if (mCutTargets.contains(Integer.valueOf(index))) {
            mCutTargets.remove(mCutTargets.indexOf(Integer.valueOf(index)));
        }
    }

    public static Vector<Integer> getCutTarget() {
        return mCutTargets;
    }

    public static boolean inInCutTargets(int index) {
        return mCutTargets.contains(Integer.valueOf(index));
    }

    public static boolean inInCutTargets(Target t) {
        if (mTargets.contains(t)) {
            return mCutTargets.contains(Integer.valueOf(mTargets.indexOf(t)));
        }
        return false;
    }


    public static void setCurrentTarget(int index){
        mCurrentTarget = index;
    }

    public static Target getCurrentTarget(){
        return getTarget(mCurrentTarget);
    }

    public static Target getTargetByAddress(String address){
        int i, size = mTargets.size();

        for(i = 0; i < size; i++){
            synchronized(mTargets){
                Target t = mTargets.get(i);

                if(t != null && t.getAddress() != null && t.getAddress().getHostAddress().equals(address))
                    return t;
            }
        }

        return null;
    }









    public static String getGatewayAddress(){
        return mNetwork.getGatewayAddress().getHostAddress();
    }

    public static boolean isForwardingEnabled(){
        boolean forwarding = false;
        BufferedReader reader;
        String line;

        try{
            reader = new BufferedReader(new FileReader(IPV4_FORWARD_FILEPATH));
            line = reader.readLine().trim();
            forwarding = line.equals("1");

            reader.close();

        }
        catch(IOException e){
            Log.w(TAG,e.toString());

        }

        return forwarding;
    }

    public static void setForwarding(boolean enabled){
        Log.d(TAG,"ipv4 setforwarding etkin");


        String status = (enabled ? "1" : "0"),
                cmd = "echo " + status + " > " + IPV4_FORWARD_FILEPATH;


    }



}