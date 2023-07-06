package mycam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class MyCam {

    private static ArrayList<Camera> cameras = new ArrayList<>();
    private static Map<String, Boolean> desiredMacAddress = new HashMap<>();

    private static String StrimHexCad(String cad) {
        return cad.replaceAll(" ", "").trim();   
    }

    public static void main(String[] args) {
        desiredMacAddress.put("00:1e:b5:84:8f:01", Boolean.FALSE);
        desiredMacAddress.put("f8:da:c:7d:e9:2f", Boolean.FALSE); // Dirección MAC deseada Ever Sparkle Technologies Ltd
        /*String ip = "192.168.1.43";
        int[] ports = {/*8070,8080,*/12476, /*19122, 21664,24188,*/ 32108};
        //sendData(hexToAscii("5d782818"), "10.10.1.43", 32108);
        for (int port : ports) {
            System.out.println("\nProbing on port: " + port);
            sendData(hexToAscii("5d099800ed958ebe07b243c05d4fcb7bb76849e0a3898058"), ip, port);
            sendData(hexToAscii(StrimHexCad("5d 98 14 de 18 6a 52 36  fc fc f6 5f f6 5f b1 c339 9d 9b 85 b0 57 c0 38  89 eb 3f d6 27 64 e5 3baf c1 48 43 ac 31 b2 2a  fb e8 a1 29 27 60 6c afcf 91 71 63 fb fa 60 66a42642e20e902256f74ea5e1dfba1fa0afcbefbdf8c02af1c31fa5ff7e53bd")), ip, port);
            sendData(hexToAscii("5d99e6bbfdb5444738ea"), ip, port);
            sendData(hexToAscii("5d99e6bbfdb5444738eb"), ip, port);
            sendData(hexToAscii("5da80ef4"), ip, port);
            sendData(hexToAscii("5da96394"), ip, port);
            sendData("ࣉ�", ip, port);
            sendData(hexToAscii("5da96394"), ip, port);
            sendData(hexToAscii("5d9814f42a95cdf51a21ddeb0d2a95cdb0715b69b1afcf89e566a0b9f9ca91790feb09a756eda4225ea74c779412feb623c85b2c0cd5e8b7133f95fbbb4a043dc31b7289f49eba1339fefc99813a3fd62764aacbe202709406454b0139b635474b5b287c37fa6737e6ce5b3bf19f1d836b9766a43056a426558d79023f"), ip, port);
            sendData(hexToAscii("5d99e6b112cfff130cb4ec83021b00af"), ip, port);
            sendData(hexToAscii("5d099800ed958ebe07b243c05d4fcb7bb76849e0a3898058"), ip, port);
        }
        System.exit(0);*/

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
                    for (Camera cam : cameras) {
                        System.out.println("Camera: " + cam.getName() + " on: " + cam.ip);
                    }
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

    private static void sendData(String data, String hostname, int rport) {
        try {
            DatagramSocket sock = new DatagramSocket();
            sock.setSoTimeout(500);
            byte[] sdata = data.getBytes();
            sock.send(new DatagramPacket(sdata, sdata.length, new InetSocketAddress(hostname, rport)));

            byte[] buf = new byte[Byte.MAX_VALUE];
            DatagramPacket rdp = new DatagramPacket(buf, buf.length);
            sock.receive(rdp);
            String response = new String(rdp.getData(), 0, rdp.getLength());
            if (!response.isEmpty()) {
                System.out.println("Response data: " + response);
                System.out.println("Hex data: " + asciiToHex(response));
            }
        } catch (IOException ex) {
            //System.out.println(ex.getMessage());
        }
    }

    private static String checkIP(String ip) {
        return ip.replace('(', ' ').replace(')', ' ').trim();
    }

    private static String checkMac(String mac) {
        String[] parts = mac.split(":");
        String nMac = "";
        for (String part : parts) {
            if (part.length() == 2) {
                nMac += part + ":";
            } else {
                nMac += "0" + part + ":";
            }
        }
        nMac = nMac.substring(0, nMac.length() - 1);
        return nMac;
    }

    private static void discoverCameras() {

        int intervalSeconds = 10; // Intervalo de detección en segundos
        boolean running = true;

        while (running) {
            try {
                Process process = Runtime.getRuntime().exec("arp -a");

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                System.out.println("--- Tabla ARP ---\n");
                while ((line = reader.readLine()) != null) {
                    // Filtrar las líneas que contienen información de la tabla ARP

                    if (line.contains("dynamic") || line.contains("ifscope")) {
                        String[] parts = line.split("\\s+");
                        String ipAddress = checkIP(parts[1]);
                        String macAddress = checkMac(parts[3]);

                        for (Map.Entry<String, Boolean> entry : desiredMacAddress.entrySet()) {
                            String mac = entry.getKey();

                            if (macAddress.equalsIgnoreCase(mac)) {
                                System.out.println("¡Se ha detectado el equipo con la dirección MAC deseada!");
                                System.out.println("Dirección IP: " + ipAddress);
                                System.out.println("Dirección MAC: " + macAddress);
                                // Realiza las acciones necesarias cuando se detecta el equipo
                                desiredMacAddress.replace(mac, Boolean.TRUE);
                                Camera camera = new Camera(macAddress, ipAddress, new DatagramSocket());
                                cameras.add(camera);
                            }
                        }
                    }
                }

                reader.close();
                // Realiza las acciones necesarias cuando el equipo no está conectado
                if (desiredMacAddress.containsValue(Boolean.FALSE)) {
                    running = false;
                }
                /*for (Map.Entry<String, Boolean> e : desiredMacAddress.entrySet()) {
                    if (!e.getValue()) {
                        System.out.println("El equipo con la dirección MAC: " + e.getKey() + " no está conectado.");
                    }
                }*/

                Thread.sleep(intervalSeconds * 1000);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
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
                String cameraName = "";//"ACCQ495869RFVSV";//getCameraByName(cameraName);
                for (Camera camera : cameras) {

                    if (camera != null) {
                        InputStream inputStream = clientSocket.getInputStream();
                        OutputStream outputStream = clientSocket.getOutputStream();
                        System.out.println("Data streaming...");
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
                        System.out.println("Data streaming closed");
                    }
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
