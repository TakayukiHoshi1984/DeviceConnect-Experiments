/*
 BleDeviceDetector
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.health.ble;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Build;
import android.os.DeadObjectException;
import android.os.Handler;
import android.util.Log;

//import org.deviceconnect.android.deviceplugin.health.BuildConfig;
import org.deviceconnect.android.BuildConfig;
import org.deviceconnect.android.deviceplugin.health.ble.adapter.NewBleDeviceAdapterImpl;
import org.deviceconnect.android.deviceplugin.health.ble.adapter.OldBleDeviceAdapterImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * This class to detect the BLE device.
 *
 * @author NTT DOCOMO, INC.
 */
public class BleDeviceDetector {
    /**
     * Instance of ScheduledExecutorService.
     */
    private ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();

    /**
     * ScheduledFuture of scan timer.
     */
    private ScheduledFuture<?> mScanTimerFuture;

    /**
     * Defines a delay 1 second at first execution.
     */
    private static final long SCAN_FIRST_WAIT_PERIOD = 1000;

    /**
     * Defines a period 10 seconds between successive executions.
     */
    private static final long SCAN_WAIT_PERIOD = 10 * 1000;

    /**
     * Stops scanning after 1 second.
     */
    private static final long SCAN_PERIOD = 2000;

    private Context mContext;
    private BleDeviceAdapter mBleAdapter;

    private Handler mHandler = new Handler();
    private boolean mScanning;
    private BleDeviceDiscoveryListener mListener;

    /**
     * 1回のスキャンで検出できたデバイスを保持するためのリスト
     * 10秒ごとに2秒間スキャンを実施し、その結果をHealthCareManagerに通知する
     */
    private List<BluetoothDevice> mDevices = new CopyOnWriteArrayList<>();

    private BleDeviceAdapterFactory mFactory;

    /**
     * Constructor.
     *
     * @param context context of this application
     */
    public BleDeviceDetector(final Context context) {
        this(context, new BleDeviceAdapterFactory() {
            @Override
            public BleDeviceAdapter createAdapter(Context context) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    return new NewBleDeviceAdapterImpl(context);
                } else {
                    return new OldBleDeviceAdapterImpl(context);
                }
            }
        });
    }

    public BleDeviceDetector(final Context context, final BleDeviceDiscoveryListener listener) {
        this(context, new BleDeviceAdapterFactory() {
            @Override
            public BleDeviceAdapter createAdapter(Context context) {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
                    return new NewBleDeviceAdapterImpl(context);
                } else {
                    return new OldBleDeviceAdapterImpl(context);
                }
            }
        });
        mListener = listener;
    }

    /**
     * Constructor.
     *
     * @param context context of this application
     * @param factory factory class
     */
    public BleDeviceDetector(final Context context, final BleDeviceAdapterFactory factory) {
        mContext = context;
        mFactory = factory;
        initialize();
    }

    /**
     * Get a context.
     *
     * @return context
     */
    public Context getContext() {
        return mContext;
    }

    /**
     * Initialize the BleDeviceDetector.
     */
    public synchronized void initialize() {
        if (mBleAdapter == null) {
            mBleAdapter = mFactory.createAdapter(mContext);
        }
    }

    /**
     * reInitialize the BleDeviceDetector.
     */
    public synchronized void reInitialize() {
        mBleAdapter = mFactory.createAdapter(mContext);
    }

    /**
     * Get a state of scan.
     *
     * @return true if scanning a BLE
     */
    public boolean isScanning() {
        return mScanning;
    }

    /**
     * Return true if Bluetooth is currently enabled and ready for use.
     *
     * @return true if the local adapter is turned on
     */
    public boolean isEnabled() {
        if (mBleAdapter == null) {
            return false;
        }
        return mBleAdapter.isEnabled();
    }

    /**
     * Start Bluetooth LE scan.
     */
    public void startScan() {
        if (isEnabled()) {
            scanLeDevice(true);
        }
    }

    /**
     * Stop Bluetooth LE scan.
     */
    public void stopScan() {
        if (isEnabled()) {
            scanLeDevice(false);
        }
        // Bluetooth OFF時において isEnabled()がfalseとなる場合であっても
        // 内部フラグはクリアする.
        mScanning = false;
        mScanTimerFuture = null;
    }

    /**
     * Gets a BluetoothDevice from address.
     *
     * @param address bluetooth address
     * @return instance of BluetoothDevice, null if not found device
     */
    public BluetoothDevice getDevice(final String address) {
        if (mBleAdapter == null || !mBleAdapter.isEnabled()) {
            return null;
        }
        return mBleAdapter.getDevice(address);
    }

    /**
     * Gets the set of BluetoothDevice that are bonded (paired) to the local adapter.
     *
     * @return set of BluetoothDevice, or null on error
     */
    public Set<BluetoothDevice> getBondedDevices() {
        if (mBleAdapter == null || !mBleAdapter.isEnabled()) {
            return null;
        }
        return mBleAdapter.getBondedDevices();
    }

    /**
     * Scan a BLE only once.
     * @param listener listener
     */
    public synchronized void scanLeDeviceOnce(final BleDeviceDiscoveryListener listener) {
        final List<BluetoothDevice> devices = new ArrayList<>();
        final BleDeviceAdapter.BleDeviceScanCallback callback =
                new BleDeviceAdapter.BleDeviceScanCallback() {
                    @Override
                    public void onLeScan(final BluetoothDevice device, final int rssi) {
                        devices.add(device);
                    }
                };
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mBleAdapter.isEnabled()) {
                        mBleAdapter.stopScan(callback);
                        listener.onDiscovery(devices);
                    }
                } catch (Exception e) {
                    // DeadObjectExceptionとなる場合がある.
                }
            }
        }, SCAN_PERIOD);
        try {
            mBleAdapter.startScan(callback);
        } catch (Exception e) {
            // DeadObjectExceptionとなる場合がある.
        }
    }

    /**
     * Sets the scan state of BLE.
     *
     * @param enable Start the scan if enable is true. Stop the scan if enable is false
     */
    private synchronized void scanLeDevice(final boolean enable) {

        if (mBleAdapter == null || !mBleAdapter.isEnabled()) {
            return;
        }

        if (enable) {
            if (mScanning || mScanTimerFuture != null) {
                // scan have already started.
                return;
            }
            mScanning = true;
            mScanTimerFuture = mExecutor.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    // Stops scanning after a pre-defined scan period.
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (mBleAdapter.isEnabled()) {
                                stopBleScan();
                                notifyBluetoothDevice();
                            } else {
                                if (mScanTimerFuture != null) {
                                    mScanTimerFuture.cancel(true);
                                }
                            }
                        }
                    }, SCAN_PERIOD);
                    if (mBleAdapter.isEnabled()) {
                        mDevices.clear();
                        startBleScan();
                    } else {
                        if (mScanTimerFuture != null) {
                            mScanTimerFuture.cancel(true);
                        }
                    }
                }
            }, SCAN_FIRST_WAIT_PERIOD, SCAN_WAIT_PERIOD, TimeUnit.MILLISECONDS);
        } else {
            mScanning = false;
            stopBleScan();
            if (mScanTimerFuture != null) {
                mScanTimerFuture.cancel(true);
                mScanTimerFuture = null;
            }
        }
    }

    /**
     * Starts a BLE scan.
     */
    private void startBleScan() {
        try {
            mBleAdapter.startScan(mScanCallback);
        } catch (Exception e) {
            // Exception occurred when the BLE state is invalid.
            if (BuildConfig.DEBUG) {
                Log.e("health.dplugin", "", e);
            }
        }
    }

    /**
     * Stops a BLE scan.
     */
    private void stopBleScan() {
        try {
            mBleAdapter.stopScan(mScanCallback);
        } catch (Exception e) {
            // Exception occurred when the BLE state is invalid.
            if (BuildConfig.DEBUG) {
                Log.e("health.dplugin", "", e);
            }
        }
    }

    /**
     * Notify that all bluetooth device was discovered.
     */
    private void notifyBluetoothDevice() {
        if (mListener != null) {
            mListener.onDiscovery(mDevices);
        }
    }

    /**
     * Implement the BluetoothAdapter.
     */
    private final BleDeviceAdapter.BleDeviceScanCallback mScanCallback =
            new BleDeviceAdapter.BleDeviceScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi) {
                    if (!mDevices.contains(device)) {
                        mDevices.add(device);
                    }
                }
            };

    /**
     * This listener to be notified when discovered the BLE device.
     */
    public interface BleDeviceDiscoveryListener {
        /**
         * Discovered the BLE device.
         *
         * @param devices BLE device list
         */
        void onDiscovery(List<BluetoothDevice> devices);
    }
}
