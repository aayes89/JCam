import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Scanner;
/**
 *
 * @author Allan Ayes
 */
public class CameraServer {

    private static ArrayList<Camera> cameras;

    public static void main(String[] args) {
        boolean keepRun = true;
        int opc = -1;
        Scanner in = new Scanner(System.in);
        while (keepRun) {
            System.out.println("--- MENU ---");
            System.out.println("1. set Station Mode");
            System.out.println("2. connect to camera");
            System.out.println("0. exit the program");
            opc = in.nextInt();
            switch (opc) {
                case 0:
                    System.exit(opc);
                    break;
                case 1:
                    System.out.println("Type the ESSID");
                    String essid = in.next();
                    System.out.println("Type the Pass");
                    String pass = in.next();

                    System.out.println("Waiting for device");

                    if (setStationMode(essid, pass) == true) {
                        System.out.println("Station Mode Activated");
                    } else {
                        System.out.println("Waiting...");
                    }
                    break;
                case 2:
                    try {
                    discoverCameras();

                    if (cameras.isEmpty()) {
                        System.out.println("No cameras detected\nExiting the program!");
                        System.exit(0);
                    }
                    System.out.println("Camera available!");
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

        //cameras.clear(); // to avoid duplicating data
        Camera camera = new Camera(name, ip, socket);
        cameras.add(camera);
    }

    private static boolean setStationMode(String essid, String pass) {
        try {
            // envia: f[ESSID]&&&[ESSID]###[Pass][RndNumber] - puerto 8090 (envia en modo AP los datos del router y contraseña al que conectar, para que se guarden y cambie al modo estacion)
            //                                     - enviar sin []
            // responde: 66 00 01 00 00 00 01 99 or f    �  - ( respuesta OK)
            DatagramSocket socket = new DatagramSocket();
            socket.setReuseAddress(true);
            int port = 8090;

            byte[] data = ("f" + essid + "&&&" + essid + "###" + pass + "").getBytes();
            while (true) {
                System.out.println("Sending data and waiting for response...");
                socket.send(new DatagramPacket(data, data.length, new InetSocketAddress("192.168.4.153", port)));
                byte[] buf = new byte[Byte.MAX_VALUE];
                DatagramPacket dprec = new DatagramPacket(buf, buf.length);
                socket.receive(dprec);
                String response = new String(dprec.getData(), 0, dprec.getLength());
                if (!response.isEmpty()) {
                    System.out.println("Data acquired: ");
                    System.out.println("Response: " + response);
                    System.out.println("ASCII response: " + asciiToHex(response));
                    if (response.equals("f    �")) {
                        return true;
                    }
                }
            }
        } catch (SocketException ex) {
            System.out.println(ex.getMessage());
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
        // Scan the network for cameras with the name "rtthread"
        try {
            DatagramSocket socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setSoTimeout(1000);
            initiateCamera("ACCQ495869RFVSV", "192.168.4.153", socket);

            /*byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            InetAddress broadcastAddress = getBroadcastAddress();

            String discoveryMessage = "rtthread";//"DISCOVER_RTTHREAD_CAMERAS";
            DatagramPacket discoveryPacket = new DatagramPacket(discoveryMessage.getBytes(), discoveryMessage.length(), broadcastAddress, 8081);
            socket.send(discoveryPacket);
            
            while (true) {
                try {
                    socket.receive(packet);

                    String response = new String(packet.getData(), 0, packet.getLength());
                    if (response.startsWith("rtthread")) {
                        String name = response.substring("rtthread:".length());
                        String ip = packet.getAddress().getHostAddress();
                        System.out.println("Discovered Camera: " + name + " at IP: " + ip);
                        initiateCamera(name, ip, socket);
                    }

                } catch (SocketTimeoutException e) {
                    break;  // Timeout reached, stop receiving packets
                }
            }

            socket.close();*/
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

                //String cameraName = clientSocket.getInputStream().toString().split("\n")[0].split(" ")[1].replace("/", "");
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

