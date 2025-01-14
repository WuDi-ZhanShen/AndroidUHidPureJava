/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.commands.hid;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

public class Device {
    private static final String TAG = "HidDevice";

    private static final int MSG_OPEN_DEVICE = 1;
    private static final int MSG_SEND_REPORT = 2;
    private static final int MSG_SEND_GET_FEATURE_REPORT_REPLY = 3;
    private static final int MSG_SEND_SET_REPORT_REPLY = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? 4 : -1;
    private static final int MSG_CLOSE_DEVICE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? 5 : 4;

    // Sync with linux uhid_event_type::UHID_OUTPUT
    private static final byte UHID_EVENT_TYPE_UHID_OUTPUT = 6;
    // Sync with linux uhid_event_type::UHID_SET_REPORT
    private static final byte UHID_EVENT_TYPE_SET_REPORT = 13;
    private final int mId;
    private final HandlerThread mThread;
    private final DeviceHandler mHandler;
    // mFeatureReports is limited to 256 entries, because the report number is 8-bit
    private final SparseArray<byte[]> mFeatureReports;
    private final Map<ByteBuffer, byte[]> mOutputs;
    private final OutputStream mOutputStream;
    private long mTimeToSend;

    private final Object mCond = new Object();

    private int mResponseId;

    static {
        System.loadLibrary("hidcommand_jni");
    }
    //since Android 6.0.1
    private static native long nativeOpenDevice(String name, int id, int vid, int pid,
                                                byte[] descriptor, MessageQueue queue, DeviceCallback callback);
    //since Android 8.1,API 27
    private static native long nativeOpenDevice(String name, int id, int vid, int pid,
                                                byte[] descriptor, DeviceCallback callback);
    //since Android 11,API 30
    private static native long nativeOpenDevice(String name, int id, int vid, int pid, int bus,
                                                byte[] descriptor, DeviceCallback callback);

    //since Android 15,API 35
    private static native long nativeOpenDevice(String name, String uniq, int id, int vid, int pid, int bus,
                                                byte[] descriptor, DeviceCallback callback);

    private static native void nativeSendReport(long ptr, byte[] data);

    private static native void nativeSendGetFeatureReportReply(long ptr, int id, byte[] data);

    //since Android 14,API 34
    private static native void nativeSendSetReportReply(long ptr, int id, boolean success);

    private static native void nativeCloseDevice(long ptr);

    public Device(int id, String name,String uniq, int vid, int pid, int bus, byte[] descriptor,
                  byte[] report, SparseArray<byte[]> featureReports, Map<ByteBuffer, byte[]> outputs) {
        mId = id;
        mThread = new HandlerThread("HidDeviceHandler");
        mThread.start();
        mHandler = new DeviceHandler(mThread.getLooper());
        mFeatureReports = featureReports;
        mOutputs = outputs;
        mOutputStream = System.out;
        Bundle args = new Bundle();
        args.putInt("id", id);
        args.putInt("vid", vid);
        args.putInt("pid", pid);
        args.putInt("bus", bus);
        if (name != null) {
            args.putString("name", name);
        } else {
            args.putString("name", id + ":" + vid + ":" + pid);
        }
        args.putString("uniq", uniq);
        args.putByteArray("descriptor", descriptor);
        args.putByteArray("report", report);
        mHandler.obtainMessage(MSG_OPEN_DEVICE, args).sendToTarget();
        mTimeToSend = SystemClock.uptimeMillis();
    }

    public void sendReport(byte[] report) {
        Message msg = mHandler.obtainMessage(MSG_SEND_REPORT, report);
        // if two messages are sent at identical time, they will be processed in order received
        mHandler.sendMessageAtTime(msg, mTimeToSend);
    }

    public void setGetReportResponse(byte[] report) {
        mFeatureReports.put(report[0], report);
    }

    public void sendSetReportReply(boolean success) {
        Message msg =
                mHandler.obtainMessage(MSG_SEND_SET_REPORT_REPLY, mResponseId, success ? 1 : 0);

        mHandler.sendMessageAtTime(msg, mTimeToSend);
    }

    public void addDelay(int delay) {
        mTimeToSend = Math.max(SystemClock.uptimeMillis(), mTimeToSend) + delay;
    }

    public void close() {
        Message msg = mHandler.obtainMessage(MSG_CLOSE_DEVICE);
        mHandler.sendMessageAtTime(msg, Math.max(SystemClock.uptimeMillis(), mTimeToSend) + 1);
        try {
            synchronized (mCond) {
                mCond.wait();
            }
        } catch (InterruptedException ignore) {
        }
    }

    private class DeviceHandler extends Handler {
        private long mPtr;
        private boolean mBarrierToken;

        public DeviceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {

            if (msg.what == MSG_OPEN_DEVICE) {
                Bundle args = (Bundle) msg.obj;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    mPtr = nativeOpenDevice(args.getString("name"), args.getString("uniq"), args.getInt("id"), args.getInt("vid"), args.getInt("pid"),
                            args.getInt("bus"), args.getByteArray("descriptor"), new DeviceCallback());
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    mPtr = nativeOpenDevice(args.getString("name"), args.getInt("id"), args.getInt("vid"), args.getInt("pid"),
                            args.getInt("bus"), args.getByteArray("descriptor"), new DeviceCallback());
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    mPtr = nativeOpenDevice(args.getString("name"), args.getInt("id"), args.getInt("vid"), args.getInt("pid"),
                            args.getByteArray("descriptor"), new DeviceCallback());
                } else {
                    mPtr = nativeOpenDevice(args.getString("name"), args.getInt("id"), args.getInt("vid"), args.getInt("pid"),
                            args.getByteArray("descriptor"), getLooper().myQueue(), new DeviceCallback());
                }
                pauseEvents();
            } else if (msg.what == MSG_SEND_REPORT) {
                if (mPtr != 0 && mBarrierToken) {
                    nativeSendReport(mPtr, (byte[]) msg.obj);
                } else {
                    Log.e(TAG, "Tried to send report to closed device.");
                }
            } else if (msg.what == MSG_SEND_GET_FEATURE_REPORT_REPLY) {
                if (mPtr != 0 && mBarrierToken) {
                    nativeSendGetFeatureReportReply(mPtr, msg.arg1, (byte[]) msg.obj);
                } else {
                    Log.e(TAG, "Tried to send feature report reply to closed device.");
                }
            } else if (msg.what == MSG_SEND_SET_REPORT_REPLY) {
                if (mPtr != 0) {
                    final boolean success = msg.arg2 == 1;
                    nativeSendSetReportReply(mPtr, msg.arg1, success);
                } else {
                    Log.e(TAG, "Tried to send set report reply to closed device.");
                }
            } else if (msg.what == MSG_CLOSE_DEVICE) {
                if (mPtr != 0) {
                    nativeCloseDevice(mPtr);
                    getLooper().quitSafely();
                    mPtr = 0;
                } else {
                    Log.e(TAG, "Tried to close already closed device.");
                }
                synchronized (mCond) {
                    mCond.notify();
                }
            } else {
                throw new IllegalArgumentException("Unknown device message");
            }
        }

        public void pauseEvents() {
            mBarrierToken = false;
        }

        public void resumeEvents() {
            mBarrierToken = true;
        }
    }

    private class DeviceCallback {
        public void onDeviceOpen() {
            mHandler.resumeEvents();
        }

        public void onDeviceGetReport(int requestId, int reportId) {
            if (mFeatureReports == null) {
                Log.e(TAG, "Received GET_REPORT request for reportId=" + reportId
                        + ", but 'feature_reports' section is not found");
                return;
            }
            byte[] report = mFeatureReports.get(reportId);

            if (report == null) {
                Log.e(TAG, "Requested feature report " + reportId + " is not specified");
            }

            Message msg;
            msg = mHandler.obtainMessage(MSG_SEND_GET_FEATURE_REPORT_REPLY, requestId, 0, report);

            // Message is set to asynchronous so it won't be blocked by synchronization
            // barrier during UHID_OPEN. This is necessary for drivers that do
            // UHID_GET_REPORT requests during probe.
            msg.setAsynchronous(true);
            mHandler.sendMessageAtTime(msg, mTimeToSend);
        }

        // Send out the report to HID command output
        private void sendReportOutput(byte eventId, byte rtype, byte[] data) {
            JSONObject json = new JSONObject();
            try {
                json.put("eventId", eventId);
                json.put("deviceId", mId);
                json.put("reportType", rtype);
                JSONArray dataArray = new JSONArray();
                for (byte datum : data) {
                    dataArray.put(datum & 0xFF);
                }
                json.put("reportData", dataArray);
            } catch (JSONException e) {
                throw new RuntimeException("Could not create JSON object ", e);
            }
            try {
                mOutputStream.write(json.toString().getBytes());
                mOutputStream.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }

        // native callback
        public void onDeviceSetReport(byte rtype, byte[] data) {
            // We don't need to reply for the SET_REPORT but just send it to HID output for test
            // verification.
            sendReportOutput(UHID_EVENT_TYPE_SET_REPORT, rtype, data);
        }

        // native callback
        public void onDeviceSetReport(int id, byte rType, byte[] data) {
            // Used by sendSetReportReply()
            mResponseId = id;
            // We don't need to reply for the SET_REPORT but just send it to HID output for test
            // verification.
            sendReportOutput(UHID_EVENT_TYPE_SET_REPORT, rType, data);
        }

        // native callback
        public void onDeviceOutput(byte rtype, byte[] data) {
            sendReportOutput(UHID_EVENT_TYPE_UHID_OUTPUT, rtype, data);
            if (mOutputs == null) {
                Log.e(TAG, "Received OUTPUT request, but 'outputs' section is not found");
                return;
            }
            byte[] response = mOutputs.get(ByteBuffer.wrap(data));
            if (response == null) {
                Log.i(TAG,
                        "Requested response for output " + Arrays.toString(data) + " is not found");
                return;
            }

            Message msg;
            msg = mHandler.obtainMessage(MSG_SEND_REPORT, response);

            // Message is set to asynchronous so it won't be blocked by synchronization
            // barrier during UHID_OPEN. This is necessary for drivers that do
            // UHID_OUTPUT requests during probe, and expect a response right away.
            msg.setAsynchronous(true);
            mHandler.sendMessageAtTime(msg, mTimeToSend);
        }

        public void onDeviceError() {
            Log.e(TAG, "Device error occurred, closing /dev/uhid");
            Message msg = mHandler.obtainMessage(MSG_CLOSE_DEVICE);
            msg.setAsynchronous(true);
            msg.sendToTarget();
        }
    }
}
