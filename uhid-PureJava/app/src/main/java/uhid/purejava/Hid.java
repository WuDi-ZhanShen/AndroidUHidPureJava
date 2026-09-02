package uhid.purejava;

import android.os.SystemClock;
import android.util.SparseArray;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.Scanner;

/** Interactive UHID demo and test entry point for app_process. */
public final class Hid {
    private static final int BUS_USB = 0x03;

    private static final byte[] MOUSE_NEUTRAL_REPORT = new byte[]{0, 0, 0, 0};
    private static final byte[] KEYBOARD_ALL_KEYS_UP_REPORT = new byte[8];

    // USB HID Keyboard/Keypad usage IDs for the characters in "helloworld".
    private static final int[] HELLO_WORLD_KEY_USAGES = {
            0x0b, // h
            0x08, // e
            0x0f, // l
            0x0f, // l
            0x12, // o
            0x1a, // w
            0x12, // o
            0x15, // r
            0x0f, // l
            0x07, // d
    };

    private static final byte[] HID_KEYBOARD_DESCRIPTOR = new byte[]{
            0x05, 0x01, 0x09, 0x06, (byte) 0xA1, 0x01,
            0x05, 0x07, 0x19, (byte) 0xE0, 0x29, (byte) 0xE7,
            0x15, 0x00, 0x25, 0x01, 0x75, 0x01, (byte) 0x95, 0x08,
            (byte) 0x81, 0x02,
            (byte) 0x95, 0x01, 0x75, 0x08, (byte) 0x81, 0x03,
            (byte) 0x95, 0x05, 0x75, 0x01, 0x05, 0x08,
            0x19, 0x01, 0x29, 0x05, (byte) 0x91, 0x02,
            (byte) 0x95, 0x01, 0x75, 0x03, (byte) 0x91, 0x03,
            (byte) 0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65,
            0x05, 0x07, 0x19, 0x00, 0x29, 0x65, (byte) 0x81, 0x00,
            (byte) 0xC0,
    };

    private static final byte[] HID_MOUSE_DESCRIPTOR = new byte[]{
            0x05, 0x01, 0x09, 0x02, (byte) 0xA1, 0x01,
            0x09, 0x01, (byte) 0xA1, 0x00,
            0x05, 0x09, 0x19, 0x01, 0x29, 0x05,
            0x15, 0x00, 0x25, 0x01, (byte) 0x95, 0x05, 0x75, 0x01,
            (byte) 0x81, 0x02,
            (byte) 0x95, 0x01, 0x75, 0x03, (byte) 0x81, 0x01,
            0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38,
            0x15, (byte) 0x81, 0x25, 0x7F, 0x75, 0x08, (byte) 0x95, 0x03,
            (byte) 0x81, 0x06,
            (byte) 0xC0, (byte) 0xC0,
    };

    private static final byte[] HID_GAMEPAD_DESCRIPTOR = new byte[]{
            0x05, 0x01, 0x09, 0x05, (byte) 0xA1, 0x01,
            0x05, 0x09, 0x19, 0x01, 0x29, 0x10,
            0x15, 0x00, 0x25, 0x01, 0x75, 0x01, (byte) 0x95, 0x10,
            (byte) 0x81, 0x02,
            0x05, 0x01, 0x15, (byte) 0x81, 0x25, 0x7F,
            0x09, 0x30, 0x09, 0x31, 0x09, 0x32, 0x09, 0x35,
            0x75, 0x08, (byte) 0x95, 0x04, (byte) 0x81, 0x02,
            (byte) 0xC0,
    };

    private static final int TOUCH_MAX_CONTACTS = 5;
    private static final int TOUCH_LOGICAL_MAX = 4095;
    private static final int TOUCH_INPUT_REPORT_ID = 1;
    private static final int TOUCH_CONTACT_MAX_REPORT_ID = 2;
    private static final int TOUCH_BYTES_PER_CONTACT = 6;
    private static final int TOUCH_CONTACT_COUNT_OFFSET = 1 + TOUCH_MAX_CONTACTS * TOUCH_BYTES_PER_CONTACT;
    private static final int TOUCH_REPORT_SIZE = TOUCH_CONTACT_COUNT_OFFSET + 1;
    private static final byte[] HID_TOUCHSCREEN_DESCRIPTOR = buildMultiTouchScreenDescriptor();

    private static byte[] buildMultiTouchScreenDescriptor() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        appendDescriptor(out,
                0x05, 0x0D, 0x09, 0x04, (byte) 0xA1, 0x01,
                (byte) 0x85, TOUCH_INPUT_REPORT_ID);

        for (int i = 0; i < TOUCH_MAX_CONTACTS; i++) {
            appendDescriptor(out,
                    0x05, 0x0D, 0x09, 0x22, (byte) 0xA1, 0x02,
                    0x15, 0x00, 0x25, 0x01, 0x75, 0x01, (byte) 0x95, 0x01,
                    0x09, 0x42, (byte) 0x81, 0x02,                 // Tip Switch
                    0x75, 0x07, 0x25, 0x7F, (byte) 0x81, 0x03,   // padding
                    0x75, 0x08, (byte) 0x95, 0x01,
                    0x15, 0x00, 0x26, (byte) 0xFF, 0x00,
                    0x09, 0x51, (byte) 0x81, 0x02,                 // Contact ID
                    0x05, 0x01, 0x75, 0x10, (byte) 0x95, 0x01,
                    0x16, 0x00, 0x00,
                    0x26, TOUCH_LOGICAL_MAX & 0xff, (TOUCH_LOGICAL_MAX >>> 8) & 0xff,
                    0x09, 0x30, (byte) 0x81, 0x02,                 // X, 16-bit
                    0x09, 0x31, (byte) 0x81, 0x02,                 // Y, 16-bit
                    (byte) 0xC0);
        }

        appendDescriptor(out,
                0x05, 0x0D,
                0x15, 0x00, 0x25, TOUCH_MAX_CONTACTS,
                0x75, 0x08, (byte) 0x95, 0x01,
                0x09, 0x54, (byte) 0x81, 0x02,                   // Contact Count
                (byte) 0x85, TOUCH_CONTACT_MAX_REPORT_ID,
                0x09, 0x55, (byte) 0xB1, 0x02,                   // Contact Max feature
                (byte) 0xC0);
        return out.toByteArray();
    }

    private static void appendDescriptor(ByteArrayOutputStream out, int... values) {
        for (int value : values) {
            out.write(value & 0xff);
        }
    }

    private static final class TouchContact {
        final int id;
        final boolean touching;
        final int x;
        final int y;

        TouchContact(int id, boolean touching, int x, int y) {
            if (id < 0 || id > 255) {
                throw new IllegalArgumentException("Contact ID must be 0..255: " + id);
            }
            validateRaw16(x, "x");
            validateRaw16(y, "y");
            this.id = id;
            this.touching = touching;
            this.x = x;
            this.y = y;
        }
    }

    private static TouchContact down(int id, int x, int y) {
        return new TouchContact(id, true, x, y);
    }

    private static TouchContact up(int id, int x, int y) {
        return new TouchContact(id, false, x, y);
    }

    private static byte[] createTouchReport(TouchContact... contacts) {
        if (contacts.length > TOUCH_MAX_CONTACTS) {
            throw new IllegalArgumentException("Too many contacts: " + contacts.length);
        }
        byte[] report = new byte[TOUCH_REPORT_SIZE];
        report[0] = (byte) TOUCH_INPUT_REPORT_ID;
        for (int i = 0; i < contacts.length; i++) {
            TouchContact contact = contacts[i];
            int offset = 1 + i * TOUCH_BYTES_PER_CONTACT;
            report[offset] = contact.touching ? (byte) 1 : 0;
            report[offset + 1] = (byte) contact.id;
            putLe16Raw(report, offset + 2, contact.x);
            putLe16Raw(report, offset + 4, contact.y);
        }
        // Includes explicit release records for one frame. This keeps slot release deterministic.
        report[TOUCH_CONTACT_COUNT_OFFSET] = (byte) contacts.length;
        return report;
    }

    /**
     * Writes the raw 16-bit representation. It deliberately does NOT clamp to TOUCH_LOGICAL_MAX.
     * The only check is the physical representable range of the 16-bit HID field (0..65535).
     */
    private static void putLe16Raw(byte[] report, int offset, int value) {
        validateRaw16(value, "value");
        report[offset] = (byte) (value & 0xff);
        report[offset + 1] = (byte) ((value >>> 8) & 0xff);
    }

    private static void validateRaw16(int value, String field) {
        if (value < 0 || value > 0xffff) {
            throw new IllegalArgumentException(field + " must fit unsigned HID 16-bit field: " + value);
        }
    }

    private static SparseArray<byte[]> createTouchFeatureReports() {
        SparseArray<byte[]> featureReports = new SparseArray<>();
        featureReports.put(TOUCH_CONTACT_MAX_REPORT_ID,
                new byte[]{(byte) TOUCH_CONTACT_MAX_REPORT_ID, (byte) TOUCH_MAX_CONTACTS});
        return featureReports;
    }

    private static void sendTouchFrame(Device touch, TouchContact... contacts) throws IOException {
        touch.sendReport(createTouchReport(contacts));
    }

    /** Move the relative mouse around a deterministic rectangle and finish with no buttons held. */
    private static void runMouseRectangleTest(Device mouse) throws IOException {
        final int widthSteps = 100;
        final int heightSteps = 60;
        final int delta = 4;
        final int frameDelayMs = 4;

        mouse.sendReport(MOUSE_NEUTRAL_REPORT);
        moveMouseRelative(mouse, delta, 0, widthSteps, frameDelayMs);   // right
        moveMouseRelative(mouse, 0, delta, heightSteps, frameDelayMs);  // down
        moveMouseRelative(mouse, -delta, 0, widthSteps, frameDelayMs);  // left
        moveMouseRelative(mouse, 0, -delta, heightSteps, frameDelayMs); // up
        mouse.sendReport(MOUSE_NEUTRAL_REPORT);
    }

    private static void moveMouseRelative(Device mouse, int dx, int dy, int steps, int delayMs)
            throws IOException {
        if (dx < -127 || dx > 127 || dy < -127 || dy > 127) {
            throw new IllegalArgumentException("Mouse relative delta must fit signed 8-bit HID range");
        }
        byte[] report = new byte[]{0, (byte) dx, (byte) dy, 0};
        for (int i = 0; i < steps; i++) {
            mouse.sendReport(report);
            SystemClock.sleep(delayMs);
        }
    }

    /** Type exactly "helloworld" and make a best-effort all-keys-up report before returning. */
    private static void runKeyboardHelloWorldTest(Device keyboard) throws IOException {
        IOException failure = null;
        try {
            for (int usage : HELLO_WORLD_KEY_USAGES) {
                byte[] keyDown = new byte[8];
                keyDown[2] = (byte) usage;
                keyboard.sendReport(keyDown);
                SystemClock.sleep(35);

                // Release after every character so repeated letters ("ll") are distinct presses.
                keyboard.sendReport(KEYBOARD_ALL_KEYS_UP_REPORT);
                SystemClock.sleep(25);
            }
        } catch (IOException e) {
            failure = e;
        } finally {
            try {
                // Also release all modifiers and all six key slots if the test exits early.
                keyboard.sendReport(KEYBOARD_ALL_KEYS_UP_REPORT);
                SystemClock.sleep(20);
            } catch (IOException releaseError) {
                if (failure != null) {
                    failure.addSuppressed(releaseError);
                } else {
                    failure = releaseError;
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void runSingleTouchTest(Device touch, Random random) throws IOException {
        int x = 2048 + random.nextInt(513) - 256;
        int y = 2048 + random.nextInt(513) - 256;
        sendTouchFrame(touch, down(0, x, y));
        for (int i = 0; i < 160; i++) {
            x += 3;
            sendTouchFrame(touch, down(0, x, y));
            SystemClock.sleep(3);
        }
        for (int i = 0; i < 160; i++) {
            y += 3;
            sendTouchFrame(touch, down(0, x, y));
            SystemClock.sleep(3);
        }
        for (int i = 0; i < 160; i++) {
            x -= 3;
            sendTouchFrame(touch, down(0, x, y));
            SystemClock.sleep(3);
        }
        for (int i = 0; i < 160; i++) {
            y -= 3;
            sendTouchFrame(touch, down(0, x, y));
            SystemClock.sleep(3);
        }
        sendTouchFrame(touch, up(0, x, y));
        SystemClock.sleep(16);
        sendTouchFrame(touch);
    }

    private static void runMultiTouchTest(Device touch) throws IOException {
        int x0 = 900, y0 = 1300;
        int x1 = 3100, y1 = 2700;
        int x2 = 2000, y2 = 700;

        sendTouchFrame(touch, down(0, x0, y0), down(1, x1, y1));
        SystemClock.sleep(60);
        for (int i = 0; i < 180; i++) {
            x0 += 10; y0 += 3;
            x1 -= 10; y1 -= 3;
            sendTouchFrame(touch, down(0, x0, y0), down(1, x1, y1));
            SystemClock.sleep(4);
        }

        sendTouchFrame(touch, down(0, x0, y0), down(1, x1, y1), down(2, x2, y2));
        SystemClock.sleep(60);
        for (int i = 0; i < 120; i++) {
            y0 += 5; y1 -= 5; y2 += 8;
            sendTouchFrame(touch, down(0, x0, y0), down(1, x1, y1), down(2, x2, y2));
            SystemClock.sleep(4);
        }

        sendTouchFrame(touch, down(0, x0, y0), up(1, x1, y1), down(2, x2, y2));
        SystemClock.sleep(60);
        for (int i = 0; i < 100; i++) {
            x0 -= 6; x2 += 6;
            sendTouchFrame(touch, down(0, x0, y0), down(2, x2, y2));
            SystemClock.sleep(4);
        }

        sendTouchFrame(touch, up(0, x0, y0), up(2, x2, y2));
        SystemClock.sleep(30);
        sendTouchFrame(touch);
    }

    private static void runOutOfBoundsTest(Device touch) throws IOException {
        final int y = 2048;
        int[] rawXs = {2048, 4095, 4096, 5000, 10000, 20000};
        sendTouchFrame(touch, down(7, rawXs[0], y));
        SystemClock.sleep(80);
        for (int x : rawXs) {
            System.out.println("OOB raw X=" + x + " (descriptor Logical Max=" + TOUCH_LOGICAL_MAX + ")");
            sendTouchFrame(touch, down(7, x, y));
            SystemClock.sleep(250);
        }
        sendTouchFrame(touch, up(7, rawXs[rawXs.length - 1], y));
        SystemClock.sleep(30);
        sendTouchFrame(touch);
    }

    private static Device createTouchDevice() throws IOException {
        return new Device(4, "uHidMultiTouchScreen", "purejava-touch-4",
                0x1d6b, 0x1040, BUS_USB, HID_TOUCHSCREEN_DESCRIPTOR,
                createTouchReport(), createTouchFeatureReports(), null);
    }

    private static Device createMouse() throws IOException {
        return new Device(1, "uHidMouse", "purejava-mouse-1",
                0x1d6b, 0x1041, BUS_USB, HID_MOUSE_DESCRIPTOR,
                MOUSE_NEUTRAL_REPORT, null, null);
    }

    private static Device createKeyboard() throws IOException {
        return new Device(2, "uHidKeyboard", "purejava-keyboard-2",
                0x1d6b, 0x1042, BUS_USB, HID_KEYBOARD_DESCRIPTOR,
                KEYBOARD_ALL_KEYS_UP_REPORT, null, null);
    }

    private static Device createGamepad() throws IOException {
        return new Device(3, "uHidGamePad", "purejava-gamepad-3",
                0x1d6b, 0x1043, BUS_USB, HID_GAMEPAD_DESCRIPTOR,
                new byte[]{0, 0, 0, 0, 0, 0}, null, null);
    }

    public static void main(String[] args) {
        String automaticTest = null;
        boolean touchOnly = false;
        for (String arg : args) {
            if (arg.startsWith("--test=")) {
                automaticTest = arg.substring("--test=".length());
            } else if ("--touch-only".equals(arg)) {
                touchOnly = true;
            }
        }
        int uid = android.os.Process.myUid();
        if (uid != 0 && uid != 2000) {
            System.err.println("Warning: uid=" + uid
                    + ". Normal app UIDs usually cannot open /dev/uhid; shell/root or a custom SELinux policy is normally required.");
        }

        Device mouse = null;
        Device keyboard = null;
        Device gamepad = null;
        Device touch = null;
        try {
            touch = createTouchDevice();
            if (!touchOnly) {
                mouse = createMouse();
                keyboard = createKeyboard();
                gamepad = createGamepad();
            }

            if (automaticTest != null) {
                runNamedTest(mouse, keyboard, gamepad, touch, automaticTest, new Random());
                return;
            }

            System.out.println("Pure-Java /dev/uhid devices created successfully (no libhidcommand_jni.so).");
            if (!touchOnly) {
                System.out.println("m  : move mouse in a rectangle");
                System.out.println("k  : type helloworld, then release all keys");
                System.out.println("g  : random gamepad axes");
            }
            System.out.println("t  : single-finger square gesture");
            System.out.println("mt : multi-finger add/move/release test");
            System.out.println("oob: no-clamp raw X test (4095 -> 20000)");
            System.out.println("exit: close all UHID devices");

            byte[] gamepadCode = {0, 0, 0, 0, 0, 0};
            Random random = new Random();

            try (Scanner scanner = new Scanner(System.in)) {
                while (scanner.hasNextLine()) {
                    String command = scanner.nextLine().trim();
                    if ("exit".equals(command)) {
                        break;
                    }
                    switch (command) {
                        case "m":
                            if (mouse != null) {
                                runMouseRectangleTest(mouse);
                            }
                            break;
                        case "k":
                            if (keyboard != null) {
                                runKeyboardHelloWorldTest(keyboard);
                            }
                            break;
                        case "g":
                            if (gamepad != null) {
                                boolean active = random.nextBoolean();
                                for (int i = 2; i < gamepadCode.length; i++) {
                                    gamepadCode[i] = active ? (byte) random.nextInt() : 0;
                                }
                                gamepad.sendReport(gamepadCode);
                            }
                            break;
                        case "t":
                            runSingleTouchTest(touch, random);
                            break;
                        case "mt":
                            runMultiTouchTest(touch);
                            break;
                        case "oob":
                            runOutOfBoundsTest(touch);
                            break;
                        default:
                            if (!command.isEmpty()) {
                                System.out.println("Unknown command: " + command);
                            }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("UHID failed: " + e.getMessage());
            e.printStackTrace(System.err);
            System.err.println("Check: ls -l /dev/uhid ; id ; dmesg/logcat for SELinux avc denials.");
            System.exit(2);
        } finally {
            // Best-effort neutralization before destroying devices. In particular, make sure no
            // keyboard modifier or key slot is intentionally left pressed when the process exits.
            sendReportQuietly(gamepad, new byte[6]);
            sendReportQuietly(keyboard, KEYBOARD_ALL_KEYS_UP_REPORT);
            sendReportQuietly(mouse, MOUSE_NEUTRAL_REPORT);

            closeQuietly(gamepad);
            closeQuietly(keyboard);
            closeQuietly(mouse);
            closeQuietly(touch);
        }
    }

    private static void runNamedTest(Device mouse, Device keyboard, Device gamepad, Device touch,
                                     String test, Random random) throws IOException {
        switch (test) {
            case "mouse":
                requireDevice(mouse, "mouse", "Do not combine --test=mouse with --touch-only");
                runMouseRectangleTest(mouse);
                break;
            case "keyboard":
                requireDevice(keyboard, "keyboard", "Do not combine --test=keyboard with --touch-only");
                runKeyboardHelloWorldTest(keyboard);
                break;
            case "gamepad":
                requireDevice(gamepad, "gamepad", "Do not combine --test=gamepad with --touch-only");
                runGamepadTest(gamepad, random);
                break;
            case "single":
                runSingleTouchTest(touch, random);
                break;
            case "mt":
                runMultiTouchTest(touch);
                break;
            case "oob":
                runOutOfBoundsTest(touch);
                break;
            default:
                throw new IllegalArgumentException("Unknown --test=" + test
                        + "; expected mouse, keyboard, gamepad, single, mt, or oob");
        }
    }

    private static void requireDevice(Device device, String name, String hint) {
        if (device == null) {
            throw new IllegalArgumentException(name + " device was not created. " + hint);
        }
    }

    private static void runGamepadTest(Device gamepad, Random random) throws IOException {
        byte[] report = new byte[6];
        try {
            for (int frame = 0; frame < 120; frame++) {
                for (int i = 2; i < report.length; i++) {
                    report[i] = (byte) (random.nextInt(255) - 127);
                }
                gamepad.sendReport(report);
                SystemClock.sleep(8);
            }
        } finally {
            gamepad.sendReport(new byte[6]);
        }
    }

    private static void sendReportQuietly(Device device, byte[] report) {
        if (device != null) {
            try {
                device.sendReport(report);
            } catch (IOException ignored) {
                // Device may already be in a fatal state; closeQuietly() will still tear it down.
            }
        }
    }

    private static void closeQuietly(Device device) {
        if (device != null) {
            try {
                device.close();
            } catch (RuntimeException e) {
                e.printStackTrace(System.err);
            }
        }
    }

    private Hid() {
    }
}
