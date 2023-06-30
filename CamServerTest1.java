import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

public class CameraServer {
    private static ArrayList<Camera> cameras = new ArrayList<>();

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        while (true) {
            System.out.println("--- MENU ---");
            System.out.println("1. Set Station Mode");
            System.out.println("2. Connect to Camera");
            System.out.println("0. Exit the program");
            int opc = in.nextInt();
            switch (opc) {
                case 0:
                    System.exit(0);
                    break;
                case 1:
                    System.out.println("Type the ESSID");
                    String essid = in.next();
                    System.out.println("Type the Pass");
                    String pass = in.next();

                    System.out.println("Waiting for device");

                    if (setStationMode(essid, pass)) {
                        System.out.println("Station Mode Activated");
                    } else {
                        System.out.println("Waiting...");
                    }
                    break;
                case 2:
                    discoverCameras();
                    if (cameras.isEmpty()) {
                        System.out.println("No cameras detected\nExiting the program!");
                        System.exit(0);
                    }
                    System.out.println("Camera available!");
                    try {
                        ServerSocket serverSocket = new ServerSocket(8081);
                        System.out.println("Server started at http://127.0.0.1:8081/cam.html");

                        while (true) {
                            Socket clientSocket = serverSocket.accept();
                            Thread clientThread = new Thread(new ClientHandler(clientSocket));
                            clientThread.start();
                        }
                    } catch (IOException e) {
                        System.out.println(e.getMessage());
                    }
                    break;
                default:
                    System.out.println("Command not available!");
            }
        }
    }

    private static void initiateCamera(String name, String ip, DatagramSocket socket) throws IOException {
        byte[] buffer = new byte[2];
        InetAddress address = InetAddress.getByName(ip);
        int port = 8070;

        buffer[0] = (byte) 0x30;
        buffer[1] = (byte) 0x67;
        socket.send(new DatagramPacket(buffer, buffer.length, address, port));

        buffer[0] = (byte) 0x30;
        buffer[1] = (byte) 0x66;
        socket.send(new DatagramPacket(buffer, buffer.length, address, port));

        port = 8080;
        buffer[0] = (byte) 0x42;
        buffer[1] = (byte) 0x76;
        socket.send(new DatagramPacket(buffer, buffer.length, address, port));

        Camera camera = new Camera(name, ip, socket);
        cameras.add(camera);
    }

    private static boolean setStationMode(String essid, String pass) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setReuseAddress(true);
            int port = 8090;

            String data = "f" + essid + "&&&" + essid + "###" + pass + "";
            byte[] buffer = data.getBytes();

            System.out.println("Sending data and waiting for response...");
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName("192.168.4.153"), port);
            socket.send(packet);

            byte[] responseBuffer = new byte[Byte.MAX_VALUE];
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
            socket.receive(responsePacket);

            String response = new String(responsePacket.getData(), 0, responsePacket.getLength());
            if (!response.isEmpty()) {
                System.out.println("Data acquired: ");
                System.out.println("Response: " + response);
                System.out.println("ASCII response: " + asciiToHex(response));
                if (response.equals("f�����")) {
                    return true;
                }
            }
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }
        return false;
    }

    public static String hexToAscii(String hexString) {
        StringBuilder output = new StringBuilder();

        for (int i = 0; i < hexString.length(); i += 2) {
            String hex = hexString.substring(i, i + 2);
            int decimal = Integer.parseInt(hex, 16);
            output.append((char) decimal);
        }

        return output.toString();
    }

    public static String asciiToHex(String asciiString) {
        StringBuilder output = new StringBuilder();

        for (int i = 0; i < asciiString.length(); i++) {
            char c = asciiString.charAt(i);
            String hex = Integer.toHexString((int) c);
            if (hex.length() < 2) {
                hex = "0" + hex; // Agregar cero a la izquierda si es necesario
            }
            output.append(hex);
        }

        return output.toString().toUpperCase();
    }

    private static void discoverCameras() {
        try {
            DatagramSocket socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setSoTimeout(1000);
            initiateCamera("ACCQ495869RFVSV", "192.168.4.153", socket);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    private static boolean isImageStart(byte[] buffer) {
        for (int i = 0; i < buffer.length - 2; i++) {
            if (buffer[i] == (byte) 0xff && buffer[i + 1] == (byte) 0xd8) {
                return true;
            }
        }
        return false;
    }

    private static boolean isImageEnd(byte[] buffer) {
        for (int i = 0; i < buffer.length - 2; i++) {
            if (buffer[i] == (byte) 0xff && buffer[i + 1] == (byte) 0xd9) {
                return true;
            }
        }
        return false;
    }

    private static void sendImage(Socket clientSocket, byte[] frame) throws IOException {
        OutputStream outputStream = clientSocket.getOutputStream();

        outputStream.write(("HTTP/1.1 200 OK\r\n"
                + "Content-type: image/jpeg\r\n"
                + "Content-length: " + frame.length + "\r\n"
                + "\r\n").getBytes());
        outputStream.write(frame);
        outputStream.write("\r\n--jpgboundary\r\n".getBytes());
        outputStream.flush();
    }

    static class ClientHandler implements Runnable {
        private Socket clientSocket;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        public void run() {
            try {
                String clientAddress = clientSocket.getInetAddress().getHostAddress();
                String cameraName = "ACCQ495869RFVSV";
                Camera camera = getCameraByName(cameraName);

                if (camera != null) {
                    InputStream inputStream = clientSocket.getInputStream();
                    OutputStream outputStream = clientSocket.getOutputStream();

                    outputStream.write(("HTTP/1.1 200 OK\r\n"
                            + "Content-type: multipart/x-mixed-replace; boundary=--jpgboundary\r\n"
                            + "\r\n").getBytes());

                    while (true) {
                        byte[] buffer = new byte[4096];
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        camera.getSocket().receive(packet);

                        buffer = packet.getData();

                        if (isImageStart(buffer)) {
                            camera.setFrame(Arrays.copyOfRange(buffer, 8, buffer.length));
                        } else {
                            byte[] newFrame = new byte[camera.getFrame().length + buffer.length];
                            System.arraycopy(camera.getFrame(), 0, newFrame, 0, camera.getFrame().length);
                            System.arraycopy(buffer, 0, newFrame, camera.getFrame().length, buffer.length);
                            camera.setFrame(newFrame);
                        }

                        if (isImageEnd(buffer)) {
                            sendImage(clientSocket, camera.getFrame());
                        }
                    }
                } else {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }

        private Camera getCameraByName(String name) {
            for (Camera camera : cameras) {
                if (camera.getName().equals(name)) {
                    return camera;
                }
            }
            return null;
        }
    }

    static class Camera {
        private String name;
        private String ip;
        private DatagramSocket socket;
        private byte[] frame;

        public Camera(String name, String ip, DatagramSocket socket) {
            this.name = name;
            this.ip = ip;
            this.socket = socket;
            this.frame = new byte[0];
        }

        public String getName() {
            return name;
        }

        public DatagramSocket getSocket() {
            return socket;
        }

        public byte[] getFrame() {
            return frame;
        }

        public void setFrame(byte[] frame) {
            this.frame = frame;
        }
    }
}
