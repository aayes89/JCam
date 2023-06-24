import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;

public class CameraServer {

    private static ArrayList<Camera> cameras;

    public static void main(String[] args) {
        try {
            cameras = new ArrayList<>();

            discoverCameras();

            if (cameras.isEmpty()) {
                System.out.println("No cameras detected\nExiting the program!");
                System.exit(0);
            }
            ServerSocket serverSocket = new ServerSocket(8081);
            System.out.println("Server started at http://127.0.0.1:8081/cam.html");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                Thread clientThread = new Thread(clientHandler);
                clientThread.start();
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void initiateCamera(String name, String ip, DatagramSocket s) throws IOException {
        DatagramSocket socket = s;
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

        cameras.clear(); // to avoid duplicating data
        Camera camera = new Camera(name, ip, socket);
        cameras.add(camera);
    }

    private static void discoverCameras() {
        // Scan the network for cameras with the name "rtthread"
        try {
            DatagramSocket socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setSoTimeout(1000);

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            InetAddress broadcastAddress = getBroadcastAddress();

            String discoveryMessage = "DISCOVER_RTTHREAD_CAMERAS";
            DatagramPacket discoveryPacket = new DatagramPacket(discoveryMessage.getBytes(), discoveryMessage.length(), broadcastAddress, 8081);
            socket.send(discoveryPacket);

            while (true) {
                try {
                    socket.receive(packet);
                    String response = new String(packet.getData(), 0, packet.getLength());
                    if (response.startsWith("rtthread:")) {
                        String name = response.substring("rtthread:".length());
                        String ip = packet.getAddress().getHostAddress();
                        System.out.println("Discovered Camera: " + name + " at IP: " + ip);
                        initiateCamera(name, ip, socket);
                    }
                } catch (SocketTimeoutException e) {
                    break;  // Timeout reached, stop receiving packets
                }
            }

            socket.close();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    private static InetAddress getBroadcastAddress() throws IOException {
        InetAddress myIp = null;
        Enumeration<NetworkInterface> ni = NetworkInterface.getNetworkInterfaces();
        while (ni.hasMoreElements()) {
            NetworkInterface mni = ni.nextElement();
            if (mni.isUp()) {
                Enumeration<InetAddress> ia = mni.getInetAddresses();
                while (ia.hasMoreElements()) {
                    InetAddress mia = ia.nextElement();
                    if (mia.isSiteLocalAddress()) {

                        myIp = mia;
                    }
                }
            }
        }
        return myIp;
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
                String cameraName = clientSocket.getInputStream().toString().split("\n")[0].split(" ")[1].replace("/", "");

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
