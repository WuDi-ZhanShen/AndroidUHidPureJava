/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Pure-Java UHID transport for Android. No libhidcommand_jni dependency.
 * Linux UHID ABI: include/uapi/linux/uhid.h
 */
package uhid.purejava;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.MessageQueue;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;
import android.util.SparseArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One userspace HID device backed by /dev/uhid.
 *
 * <p>This class intentionally uses only Java + public android.system.Os APIs. It does not load
 * libhidcommand_jni.so and does not bind to Android's private cmd/hid JNI ABI.</p>
 */
public final class Device implements AutoCloseable {
    private static final String TAG = "PureJavaUHID";
    private static final String UHID_PATH = "/dev/uhid";

    // include/uapi/linux/uhid.h
    private static final int UHID_DESTROY = 1;
    private static final int UHID_START = 2;
    private static final int UHID_STOP = 3;
    private static final int UHID_OPEN = 4;
    private static final int UHID_CLOSE = 5;
    private static final int UHID_OUTPUT = 6;
    private static final int UHID_GET_REPORT = 9;
    private static final int UHID_GET_REPORT_REPLY = 10;
    private static final int UHID_CREATE2 = 11;
    private static final int UHID_INPUT2 = 12;
    private static final int UHID_SET_REPORT = 13;
    private static final int UHID_SET_REPORT_REPLY = 14;

    private static final int UHID_FEATURE_REPORT = 0;
    private static final int UHID_DATA_MAX = 4096;
    // sizeof(struct uhid_event) on the Linux UAPI ABI. The outer struct is packed, but the union
    // size is rounded to its largest alignment because it contains u64 uhid_start_req.
    private static final int UHID_EVENT_SIZE = 4380;

    private static final int CREATE2_NAME_OFFSET = 4;
    private static final int CREATE2_PHYS_OFFSET = CREATE2_NAME_OFFSET + 128;
    private static final int CREATE2_UNIQ_OFFSET = CREATE2_PHYS_OFFSET + 64;
    private static final int CREATE2_RD_SIZE_OFFSET = CREATE2_UNIQ_OFFSET + 64;
    private static final int CREATE2_BUS_OFFSET = CREATE2_RD_SIZE_OFFSET + 2;
    private static final int CREATE2_VENDOR_OFFSET = CREATE2_BUS_OFFSET + 2;
    private static final int CREATE2_PRODUCT_OFFSET = CREATE2_VENDOR_OFFSET + 4;
    private static final int CREATE2_VERSION_OFFSET = CREATE2_PRODUCT_OFFSET + 4;
    private static final int CREATE2_COUNTRY_OFFSET = CREATE2_VERSION_OFFSET + 4;
    private static final int CREATE2_DESCRIPTOR_OFFSET = CREATE2_COUNTRY_OFFSET + 4; // 280

    private static final int OUTPUT_DATA_OFFSET = 4;
    private static final int OUTPUT_SIZE_OFFSET = OUTPUT_DATA_OFFSET + UHID_DATA_MAX;
    private static final int OUTPUT_RTYPE_OFFSET = OUTPUT_SIZE_OFFSET + 2;

    private static final long INIT_TIMEOUT_MS = 5000;
    private static final long START_TIMEOUT_MS = 2000;
    private static final long CLOSE_TIMEOUT_MS = 2000;

    private final int mId;
    private final String mName;
    private final SparseArray<byte[]> mFeatureReports;
    private final Map<ByteBuffer, byte[]> mOutputs;
    private final HandlerThread mEventThread;
    private final Handler mHandler;
    private final Object mWriteLock = new Object();
    private final AtomicBoolean mClosed = new AtomicBoolean(false);
    private final CountDownLatch mStarted = new CountDownLatch(1);

    private volatile FileDescriptor mFd;
    private volatile IOException mFatalError;
    private volatile boolean mKernelOpened;

    public Device(int id, String name, String uniq, int vid, int pid, int bus, byte[] descriptor,
                  byte[] initialReport, SparseArray<byte[]> featureReports,
                  Map<ByteBuffer, byte[]> outputs) throws IOException {
        if (descriptor == null || descriptor.length == 0) {
            throw new IllegalArgumentException("HID descriptor must not be empty");
        }
        if (descriptor.length > UHID_DATA_MAX) {
            throw new IllegalArgumentException("HID descriptor is too large: " + descriptor.length);
        }
        mId = id;
        mName = name != null ? name : ("uhid-" + id);
        mFeatureReports = cloneSparseArray(featureReports);
        mOutputs = outputs;

        mEventThread = new HandlerThread("PureJavaUHID-" + id);
        mEventThread.start();
        mHandler = new Handler(mEventThread.getLooper());

        CountDownLatch initialized = new CountDownLatch(1);
        mHandler.post(() -> {
            try {
                FileDescriptor fd = Os.open(UHID_PATH,
                        OsConstants.O_RDWR | OsConstants.O_CLOEXEC, 0);
                mFd = fd;
                MessageQueue queue = mEventThread.getLooper().getQueue();
                queue.addOnFileDescriptorEventListener(fd,
                        MessageQueue.OnFileDescriptorEventListener.EVENT_INPUT
                                | MessageQueue.OnFileDescriptorEventListener.EVENT_ERROR,
                        this::onFileDescriptorEvents);

                String actualUniq = uniq != null ? uniq : ("uhid-purejava-" + id);
                String phys = "uhid-purejava/" + id;
                writeEvent(buildCreate2Request(mName, phys, actualUniq, vid, pid, bus, descriptor));
            } catch (ErrnoException e) {
                mFatalError = new IOException("Cannot open " + UHID_PATH + ": " + e, e);
                closeFromEventThread(false);
            } catch (IOException | RuntimeException e) {
                mFatalError = e instanceof IOException ? (IOException) e
                        : new IOException("Failed to create UHID device", e);
                closeFromEventThread(false);
            } finally {
                initialized.countDown();
            }
        });

        awaitOrThrow(initialized, INIT_TIMEOUT_MS, "Timed out while opening /dev/uhid");
        if (mFatalError != null) {
            throw mFatalError;
        }

        // UHID_START is expected after CREATE2. Do not fail hard on a vendor kernel that is slow;
        // INPUT2 still has a useful error path and some callers create/send immediately.
        try {
            if (!mStarted.await(START_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, mName + ": no UHID_START within " + START_TIMEOUT_MS + " ms");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for UHID_START", e);
        }

        if (initialReport != null && initialReport.length > 0) {
            sendReport(initialReport);
        }
    }

    public int getId() {
        return mId;
    }

    public boolean isKernelOpened() {
        return mKernelOpened;
    }

    /** Send one HID input report exactly as supplied. No axis-value clamp is performed here. */
    public void sendReport(byte[] report) throws IOException {
        if (report == null) {
            throw new NullPointerException("report");
        }
        if (report.length > UHID_DATA_MAX) {
            throw new IllegalArgumentException("UHID report is too large: " + report.length);
        }
        ensureUsable();
        // Build a private request buffer so a caller may safely reuse/mutate its original byte[].
        writeEvent(buildInput2Request(report));
    }

    private int onFileDescriptorEvents(FileDescriptor fd, int events) {
        final int listenEvents = MessageQueue.OnFileDescriptorEventListener.EVENT_INPUT
                | MessageQueue.OnFileDescriptorEventListener.EVENT_ERROR;

        if ((events & MessageQueue.OnFileDescriptorEventListener.EVENT_ERROR) != 0) {
            mFatalError = new IOException(mName + ": /dev/uhid fd reported EVENT_ERROR");
            closeFromEventThread(false);
            return 0;
        }

        if ((events & MessageQueue.OnFileDescriptorEventListener.EVENT_INPUT) != 0) {
            byte[] event = new byte[UHID_EVENT_SIZE];
            try {
                int count = Os.read(fd, event, 0, event.length);
                if (count == 0) {
                    mFatalError = new IOException(mName + ": unexpected EOF from /dev/uhid");
                    closeFromEventThread(false);
                    return 0;
                }
                handleKernelEvent(event, count);
            } catch (InterruptedIOException e) {
                // No protocol state is changed by an interrupted read. Keep the listener and retry
                // when the fd becomes readable again.
                return listenEvents;
            } catch (ErrnoException e) {
                mFatalError = new IOException(mName + ": failed reading /dev/uhid: " + e, e);
                closeFromEventThread(false);
                return 0;
            } catch (RuntimeException e) {
                mFatalError = new IOException(mName + ": malformed UHID event", e);
                closeFromEventThread(false);
                return 0;
            }
        }

        return listenEvents;
    }

    private void handleKernelEvent(byte[] data, int count) {
        if (count < 4) {
            Log.w(TAG, mName + ": short UHID event: " + count);
            return;
        }
        ByteBuffer buffer = ByteBuffer.wrap(data, 0, count).order(ByteOrder.nativeOrder());
        int type = buffer.getInt(0);
        switch (type) {
            case UHID_START:
                mStarted.countDown();
                break;
            case UHID_STOP:
                Log.i(TAG, mName + ": UHID_STOP");
                break;
            case UHID_OPEN:
                mKernelOpened = true;
                Log.i(TAG, mName + ": UHID_OPEN");
                break;
            case UHID_CLOSE:
                mKernelOpened = false;
                Log.i(TAG, mName + ": UHID_CLOSE");
                break;
            case UHID_OUTPUT:
                handleOutput(data, count);
                break;
            case UHID_GET_REPORT:
                handleGetReport(buffer, count);
                break;
            case UHID_SET_REPORT:
                handleSetReport(data, buffer, count);
                break;
            default:
                // Future kernels may add events. Unknown events are intentionally ignored.
                break;
        }
    }

    private void handleOutput(byte[] data, int count) {
        if (count < OUTPUT_RTYPE_OFFSET + 1) {
            Log.w(TAG, mName + ": incomplete UHID_OUTPUT: " + count);
            return;
        }
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
        int size = buffer.getShort(OUTPUT_SIZE_OFFSET) & 0xffff;
        int rtype = data[OUTPUT_RTYPE_OFFSET] & 0xff;
        if (size > UHID_DATA_MAX) {
            Log.w(TAG, mName + ": invalid UHID_OUTPUT size: " + size);
            return;
        }
        byte[] report = Arrays.copyOfRange(data, OUTPUT_DATA_OFFSET, OUTPUT_DATA_OFFSET + size);
        printOutputEvent(UHID_OUTPUT, rtype, report);

        if (mOutputs != null) {
            byte[] response = mOutputs.get(ByteBuffer.wrap(report));
            if (response != null) {
                try {
                    sendReport(response);
                } catch (IOException e) {
                    Log.e(TAG, mName + ": failed to send configured OUTPUT response", e);
                }
            }
        }
    }

    private void handleGetReport(ByteBuffer buffer, int count) {
        if (count < 10) {
            Log.w(TAG, mName + ": incomplete UHID_GET_REPORT: " + count);
            return;
        }
        long requestId = buffer.getInt(4) & 0xffffffffL;
        int reportNumber = buffer.get(8) & 0xff;
        int reportType = buffer.get(9) & 0xff;

        byte[] report = reportType == UHID_FEATURE_REPORT && mFeatureReports != null
                ? mFeatureReports.get(reportNumber) : null;
        try {
            writeEvent(buildGetReportReply(requestId, report));
        } catch (IOException e) {
            Log.e(TAG, mName + ": failed replying UHID_GET_REPORT", e);
        }
    }

    private void handleSetReport(byte[] data, ByteBuffer buffer, int count) {
        if (count < 12) {
            Log.w(TAG, mName + ": incomplete UHID_SET_REPORT: " + count);
            return;
        }
        long requestId = buffer.getInt(4) & 0xffffffffL;
        int reportNumber = buffer.get(8) & 0xff;
        int reportType = buffer.get(9) & 0xff;
        int size = buffer.getShort(10) & 0xffff;
        if (size > UHID_DATA_MAX || count < 12 + size) {
            Log.w(TAG, mName + ": invalid UHID_SET_REPORT size: " + size);
            try {
                writeEvent(buildSetReportReply(requestId, false));
            } catch (IOException ignored) {
            }
            return;
        }

        byte[] report = Arrays.copyOfRange(data, 12, 12 + size);
        printOutputEvent(UHID_SET_REPORT, reportType, report);
        Log.i(TAG, mName + ": SET_REPORT number=" + reportNumber);
        try {
            // This demo has no mutable device-side feature state, but acknowledging a syntactically
            // valid SET_REPORT is friendlier to HID drivers than leaving the request unanswered.
            writeEvent(buildSetReportReply(requestId, true));
        } catch (IOException e) {
            Log.e(TAG, mName + ": failed replying UHID_SET_REPORT", e);
        }
    }

    private void printOutputEvent(int eventType, int reportType, byte[] report) {
        try {
            JSONObject json = new JSONObject();
            json.put("eventId", eventType);
            json.put("deviceId", mId);
            json.put("reportType", reportType);
            JSONArray array = new JSONArray();
            for (byte b : report) {
                array.put(b & 0xff);
            }
            json.put("reportData", array);
            System.out.println(json.toString());
        } catch (JSONException e) {
            Log.w(TAG, "Could not format UHID output", e);
        }
    }

    private void writeEvent(byte[] event) throws IOException {
        synchronized (mWriteLock) {
            ensureUsableLocked();
            writeUhidEventAtomicLocked(event);
        }
    }

    /**
     * Write exactly one UHID protocol event.
     *
     * <p>/dev/uhid is event-oriented, not a byte stream. A short successful write must never be
     * "completed" by a second write: the kernel has already interpreted the first write as one
     * event (short events are zero-extended by the UHID ABI), and the second write would become a
     * different event. Therefore this method performs one complete event write per successful
     * syscall and treats any positive short write as fatal.</p>
     *
     * <p>An InterruptedIOException is retried only when bytesTransferred == 0, which means no part
     * of this Java write operation was transferred. We cap retries so a signal storm cannot hang
     * device creation or report delivery forever.</p>
     */
    private void writeUhidEventAtomicLocked(byte[] event) throws IOException {
        final FileDescriptor fd = mFd;
        if (fd == null) {
            throw new IOException(mName + ": UHID fd is closed");
        }

        final int maxZeroByteInterruptRetries = 8;
        int zeroByteInterrupts = 0;
        while (true) {
            try {
                int n = Os.write(fd, event, 0, event.length);
                if (n == event.length) {
                    return;
                }

                IOException failure = new IOException(mName
                        + ": atomic UHID event write was short: " + n + "/" + event.length
                        + ". Refusing to append the remainder because /dev/uhid is event-oriented.");
                mFatalError = failure;
                throw failure;
            } catch (InterruptedIOException e) {
                if (e.bytesTransferred != 0) {
                    IOException failure = new IOException(mName
                            + ": UHID event write was interrupted after transferring "
                            + e.bytesTransferred + "/" + event.length
                            + " bytes; the event cannot be safely resumed", e);
                    mFatalError = failure;
                    throw failure;
                }
                if (++zeroByteInterrupts > maxZeroByteInterruptRetries) {
                    IOException failure = new IOException(mName
                            + ": UHID event write repeatedly interrupted before transferring data",
                            e);
                    mFatalError = failure;
                    throw failure;
                }
                // Safe retry: this interrupted write transferred zero bytes.
            } catch (ErrnoException e) {
                IOException failure = new IOException(
                        mName + ": write(/dev/uhid) failed: " + e, e);
                mFatalError = failure;
                throw failure;
            }
        }
    }

    private void ensureUsable() throws IOException {
        synchronized (mWriteLock) {
            ensureUsableLocked();
        }
    }

    private void ensureUsableLocked() throws IOException {
        if (mClosed.get() || mFd == null) {
            if (mFatalError != null) {
                throw mFatalError;
            }
            throw new IOException(mName + ": UHID device is closed");
        }
    }

    @Override
    public void close() {
        if (!mClosed.compareAndSet(false, true)) {
            return;
        }
        CountDownLatch done = new CountDownLatch(1);
        if (!mHandler.post(() -> {
            closeFromEventThread(true);
            done.countDown();
        })) {
            closeFileDescriptorDirectly();
            return;
        }
        try {
            done.await(CLOSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeFromEventThread(boolean sendDestroy) {
        FileDescriptor fd = mFd;
        if (fd != null) {
            try {
                mEventThread.getLooper().getQueue().removeOnFileDescriptorEventListener(fd);
            } catch (RuntimeException ignored) {
            }
            if (sendDestroy) {
                try {
                    synchronized (mWriteLock) {
                        // Do not call ensureUsableLocked(): close() has already set mClosed=true.
                        if (mFd != null) {
                            writeUhidEventAtomicLocked(buildSimpleEvent(UHID_DESTROY));
                        }
                    }
                } catch (IOException e) {
                    Log.w(TAG, mName + ": UHID_DESTROY failed", e);
                }
            }
            closeFileDescriptorDirectly();
        }
        mEventThread.quitSafely();
    }

    private void closeFileDescriptorDirectly() {
        synchronized (mWriteLock) {
            FileDescriptor fd = mFd;
            mFd = null;
            if (fd != null) {
                try {
                    Os.close(fd);
                } catch (ErrnoException e) {
                    Log.w(TAG, mName + ": close(/dev/uhid) failed", e);
                }
            }
        }
    }

    private static byte[] buildCreate2Request(String name, String phys, String uniq,
                                               int vid, int pid, int bus, byte[] descriptor) {
        ByteBuffer buffer = ByteBuffer.allocate(CREATE2_DESCRIPTOR_OFFSET + descriptor.length)
                .order(ByteOrder.nativeOrder());
        buffer.putInt(0, UHID_CREATE2);
        putCString(buffer, CREATE2_NAME_OFFSET, 128, name);
        putCString(buffer, CREATE2_PHYS_OFFSET, 64, phys);
        putCString(buffer, CREATE2_UNIQ_OFFSET, 64, uniq);
        buffer.putShort(CREATE2_RD_SIZE_OFFSET, (short) descriptor.length);
        buffer.putShort(CREATE2_BUS_OFFSET, (short) bus);
        buffer.putInt(CREATE2_VENDOR_OFFSET, vid);
        buffer.putInt(CREATE2_PRODUCT_OFFSET, pid);
        buffer.putInt(CREATE2_VERSION_OFFSET, 0);
        buffer.putInt(CREATE2_COUNTRY_OFFSET, 0);
        buffer.position(CREATE2_DESCRIPTOR_OFFSET);
        buffer.put(descriptor);
        return buffer.array();
    }

    private static byte[] buildInput2Request(byte[] report) {
        ByteBuffer buffer = ByteBuffer.allocate(6 + report.length).order(ByteOrder.nativeOrder());
        buffer.putInt(UHID_INPUT2);
        buffer.putShort((short) report.length);
        buffer.put(report);
        return buffer.array();
    }

    private static byte[] buildGetReportReply(long requestId, byte[] report) {
        int size = report != null ? report.length : 0;
        ByteBuffer buffer = ByteBuffer.allocate(12 + size).order(ByteOrder.nativeOrder());
        buffer.putInt(UHID_GET_REPORT_REPLY);
        buffer.putInt((int) requestId);
        buffer.putShort((short) (report == null ? OsConstants.EIO : 0));
        buffer.putShort((short) size);
        if (report != null) {
            buffer.put(report);
        }
        return buffer.array();
    }

    private static byte[] buildSetReportReply(long requestId, boolean success) {
        ByteBuffer buffer = ByteBuffer.allocate(10).order(ByteOrder.nativeOrder());
        buffer.putInt(UHID_SET_REPORT_REPLY);
        buffer.putInt((int) requestId);
        buffer.putShort((short) (success ? 0 : OsConstants.EIO));
        return buffer.array();
    }

    private static byte[] buildSimpleEvent(int type) {
        return ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(type).array();
    }

    private static void putCString(ByteBuffer buffer, int offset, int capacity, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(utf8.length, capacity - 1);
        // Avoid ending in the middle of a UTF-8 continuation sequence.
        while (length > 0 && length < utf8.length && (utf8[length] & 0xc0) == 0x80) {
            length--;
        }
        int oldPosition = buffer.position();
        buffer.position(offset);
        buffer.put(utf8, 0, length);
        buffer.position(oldPosition);
    }

    private static SparseArray<byte[]> cloneSparseArray(SparseArray<byte[]> source) {
        if (source == null) {
            return null;
        }
        SparseArray<byte[]> result = new SparseArray<>(source.size());
        for (int i = 0; i < source.size(); i++) {
            byte[] value = source.valueAt(i);
            result.put(source.keyAt(i), value != null ? value.clone() : null);
        }
        return result;
    }

    private static void awaitOrThrow(CountDownLatch latch, long timeoutMs, String message)
            throws IOException {
        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new IOException(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted: " + message, e);
        }
    }
}
