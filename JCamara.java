package jcamara;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/*
* Made by Slam (2025)
*/
public class JCamara {

    private static ArrayList<Camera> cameras = new ArrayList<>();
    private static Map<String, Boolean> desiredMacAddress = new HashMap<>();

    private static String StrimHexCad(String cad) {
        return cad.replaceAll(" ", "").trim();
    }

    public static void main(String[] args) {
        desiredMacAddress.put("00:1e:b5:86:15:6d", Boolean.FALSE);
        desiredMacAddress.put("00-1e-b5-86-15-6d", Boolean.FALSE);
        desiredMacAddress.put("00:1e:b5:86:15:6c", Boolean.FALSE);
        desiredMacAddress.put("00-1e-b5-86-15-6c", Boolean.FALSE);
        desiredMacAddress.put("00:1e:b5:84:8f:01", Boolean.FALSE);
        desiredMacAddress.put("00-1e-b5-84-8f-01", Boolean.FALSE);
        desiredMacAddress.put("c8:47:8c:00:00:00", Boolean.FALSE);
        desiredMacAddress.put("c8-47-8c-00-00-00", Boolean.FALSE);
        desiredMacAddress.put("f8:da:0c:7d:e9:2f", Boolean.FALSE); // Dirección MAC deseada Ever Sparkle Technologies Ltd
        desiredMacAddress.put("f8-da-0c-7d-e9-2f", Boolean.FALSE);

        Scanner in = new Scanner(System.in);
        while (true) {
            System.out.println("--- MENU ---");
            System.out.println("1. Set Station Mode");
            System.out.println("2. Connect to Camera");
            System.out.println("3. Detectar cámaras UDP.");
            System.out.println("4. Detectar puertos en cámaras UDP.");
            System.out.println("0. Exit the program");
            int opc = in.nextInt();
            switch (opc) {
                case 0:
                    System.out.println("See you next!");
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
                        System.out.println("No cameras detected!");
                        System.out.println("Do you want exit? Y o N");

                        if (in.next().equalsIgnoreCase("y")) {
                            System.out.println("Exiting the program!");
                            System.exit(0);
                        } else {
                            System.out.println("If you know there is a camera online, enter the IP address:");
                            String ipCam = in.next();
                            if (isAnIP(ipCam)) {
                                System.out.println("Enter the name of the camera, please:");
                                String nCam = in.next();
                                System.out.println("Trying to connect to " + nCam + " on: " + ipCam);
                                try {
                                    initiateCameraSTMode(nCam, ipCam);
                                } catch (IOException ex) {
                                    System.out.println("IOE: " + ex.getMessage());
                                }
                            } else {
                                System.out.println("This app is not a chatbot, I'm not programmed to know what you think.\nThe program will exit now!");
                                System.exit(0);
                            }
                        }
                    } else {
                        System.out.println("Please select the camera to work with:");
                        for (Camera cam : cameras) {
                            System.out.println(cameras.indexOf(cam) + ". " + cam.toString());
                        }
                        String selectedCam = in.next();
                        try {
                            int option = Integer.parseInt(selectedCam);
                            Camera myCam = cameras.get(option);
                            if (myCam.ip.equals("192.168.4.153")) {
                                System.out.println("This camera is on AP mode");
                                initiateCameraAPMode(myCam.getName(), myCam.ip);
                            } else {
                                System.out.println("Trying to connect with " + myCam.getName());
                                initiateCameraSTMode(myCam.getName(), myCam.ip);
                                initServer(myCam);
                            }
                        } catch (NumberFormatException ex) {
                            System.out.println("NFE: " + ex.getMessage());
                        } finally {
                            continue;
                        }
                    }
                    break;
                case 3:
                    System.out.println("Ingrese la dirección IP de la cámara si la conoce:");
                    String ip = in.nextLine().concat(in.next());
                    detectCameraUDP(ip);
                    break;
                case 4:
                    System.out.println("Ingrese la dirección IP de la cámara si la conoce:");
                    String tip = in.nextLine().concat(in.next());
                    int port = detectAndDumpStream(tip);
                    if (port != -1) {
                        System.out.println("Puerto de transmisión activo: " + port);
                        // Puedes asignarlo a tu cámara:
                        // camera.setPort(port);
                    } else {
                        System.out.println("No se encontró stream UDP activo.");
                    }

                    break;
                default:
                    System.out.println("Command not available!");
            }
        }
    }

    private static void initiateCameraAPMode(String name, String ip) throws IOException {
        DatagramSocket socket = new DatagramSocket();
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

    private static void initiateCameraSTMode(String nCam, String ip) throws IOException {

        DatagramSocket socket = new DatagramSocket();
        byte[] buffer = new byte[2];
        InetAddress address = InetAddress.getByName(ip);
        int port = 12476;
        buffer[0] = (byte) 0x30;
        buffer[1] = (byte) 0x67;
        socket.send(new DatagramPacket(buffer, buffer.length, address, port));

        buffer[0] = (byte) 0x30;
        buffer[1] = (byte) 0x66;
        socket.send(new DatagramPacket(buffer, buffer.length, address, port));

        port = 32108;
        buffer[0] = (byte) 0x42;
        buffer[1] = (byte) 0x76;
        socket.send(new DatagramPacket(buffer, buffer.length, address, port));
        buffer = hexToAscii("5d782818").getBytes();
        socket.send(new DatagramPacket(buffer, buffer.length, address, port));

    }

    private static volatile boolean runningServer = true;

    private static void initServer(Camera cam) {
        System.out.println("Camera available!");

        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(8081)) {
                serverSocket.setReuseAddress(true);
                System.out.println("Server started at http://127.0.0.1:8081/");

                while (runningServer) {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setSoTimeout(15000);
                    new Thread(new ClientHandler(clientSocket, cam)).start();
                }
            } catch (IOException e) {
                System.err.println("Server error: " + e.getMessage());
            }
        }, "HTTPServerThread").start();
    }

    private static String readHttpRequest(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            sb.append(line).append("\r\n");
        }
        return sb.toString();
    }

    private static void sendHttpError(OutputStream out, int code, String msg) throws IOException {
        out.write(("HTTP/1.1 " + code + " " + msg + "\r\n"
                + "Content-Type: text/plain\r\n\r\n"
                + msg).getBytes(StandardCharsets.US_ASCII));
        out.flush();
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

    // validating if are correct
    private static boolean isAnIP(String ip) {

        if (ip.contains(".") && ip.split("\\.").length == 4) {
            String[] parts = ip.split("\\.");
            for (String part : parts) {
                try {
                    int value = Integer.parseInt(part);
                    if (value < 0 || value > 255) {
                        return false;
                    }
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static String checkIP(String ip) {
        return ip.replace('(', ' ').replace(')', ' ').trim();
    }

    private static String checkMac(String mac) {
        String[] parts = mac.split(":");
        String nMac = "";
        for (String part : parts) {
            if (part.length() >= 2) {
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

                    if (line.contains("-") || line.contains(":") || line.contains("dynamic") || line.contains("ifscope") || line.contains("dinámico") || line.contains("estático") || line.contains("est�tico") || line.contains("din�mico")) {
                        String[] parts = line.split("\\s+");
                        String ipAddress = checkIP(parts[1]);
                        String macAddress = checkMac(parts[2]);

                        for (Map.Entry<String, Boolean> entry : desiredMacAddress.entrySet()) {
                            String mac = entry.getKey();

                            if (macAddress.equalsIgnoreCase(mac)) {
                                System.out.println("¡Se ha detectado el equipo con la dirección MAC deseada!");
                                System.out.println("Dirección IP: " + ipAddress);
                                System.out.println("Dirección MAC: " + macAddress);
                                // Realiza las acciones necesarias cuando se detecta el equipo
                                desiredMacAddress.replace(mac, Boolean.TRUE);
                                Camera camera = new Camera(macAddress, ipAddress, new DatagramSocket());
                                camera.setOnline(true);
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

    private static boolean isImageStart(byte[] buffer, int len) {
        for (int i = 0; i < len - 1; i++) {
            if (buffer[i] == (byte) 0xFF && buffer[i + 1] == (byte) 0xD8) {
                return true;
            }
        }
        return false;
    }

    private static boolean isImageEnd(byte[] buffer, int len) {
        for (int i = 0; i < len - 1; i++) {
            if (buffer[i] == (byte) 0xFF && buffer[i + 1] == (byte) 0xD9) {
                return true;
            }
        }
        return false;
    }

    private static void sendImage(Socket clientSocket, byte[] frame) throws IOException {
        OutputStream out = clientSocket.getOutputStream();
        // NOTE: en el cuerpo se envía con DOS guiones prefijados
        out.write(("--jpgboundary\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(("Content-Type: image/jpeg\r\n"
                + "Content-Length: " + frame.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(frame);
        // DOS CRLF al final son importantes
        out.write("\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    static class ClientHandler implements Runnable {

        private final Socket clientSocket;
        private final Camera camera;

        public ClientHandler(Socket clientSocket, Camera cam) {
            this.clientSocket = clientSocket;
            this.camera = cam;
        }

        @Override
        public void run() {
            try {
                //String clientAddress = clientSocket.getInetAddress().getHostAddress();
                //String cameraName = "";//"ACCQ495869RFVSV";//getCameraByName(cameraName);

                String request = readHttpRequest(clientSocket.getInputStream());
                if (!request.startsWith("GET")) {
                    sendHttpError(clientSocket.getOutputStream(), 405, "Method Not Allowed");
                    clientSocket.close();
                    return;
                }

                // Rutas simples
                if (request.contains("GET / ") || request.contains("GET /cam.html")) {
                    sendHtml(clientSocket.getOutputStream());
                } else if (request.contains("GET /stream")) {
                    startStream(clientSocket, camera);
                } else {
                    sendHttpError(clientSocket.getOutputStream(), 404, "Not Found");
                }

                // old version
                /*while (true) {
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
            }else {
                    clientSocket.close();
                    System.out.println("Data streaming closed");
                }*/
            } catch (SocketException se) {
                System.out.println("Stream ended: " + se.getMessage());
            } catch (IOException e) {
                System.out.println("Client disconnected: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void sendHtml(OutputStream out) throws IOException {
        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>JCamara Stream</title>
                    <style>
                        body { background:#000; color:#0f0; text-align:center; font-family:monospace; }
                        img { width:90%%; max-width:640px; margin-top:30px; border:3px solid #0f0; border-radius:12px; }
                        h2 { color:#0f0; margin-top:20px; }
                    </style>
                </head>
                <body>
                    <h2>JCamara Live Stream</h2>
                    <img src="/stream" alt="Camera Stream">
                </body>
                </html>
                """;

        byte[] data = html.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 200 OK\r\n"
                + "Cache-Control: no-cache\r\n"
                + "Pragma: no-cache\r\n"
                + "Connection: close\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n"
                + "Content-Length: " + data.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(data);
        out.flush();
    }

    private static void startStream(Socket clientSocket, Camera camera) throws IOException {
        OutputStream raw = clientSocket.getOutputStream();
        BufferedOutputStream out = new BufferedOutputStream(raw, 8192);

        out.write(("HTTP/1.1 200 OK\r\n"
                + "Cache-Control: no-cache\r\n"
                + "Pragma: no-cache\r\n"
                + "Connection: close\r\n"
                + "Content-Type: multipart/x-mixed-replace; boundary=jpgboundary\r\n"
                + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();

        ByteArrayOutputStream frameBuilder = new ByteArrayOutputStream(262144); // 256KB
        DatagramSocket socket = camera.getSocket();

        // Asegurar que el socket esté vinculado
        if (socket == null || socket.isClosed()) {
            socket = new DatagramSocket(38752); // puerto típico UDP (32108 AP) (38752 STA)
            camera.socket = socket;
        }

        System.out.println("Streaming camera: " + camera.getName() + " @ " + camera.ip);

        byte[] recvBuf = new byte[8192];
        DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
        socket.receive(packet);
        int len = packet.getLength();
        byte[] data = packet.getData(); // contiene al menos len bytes válidos

        // comprobaciones sobre los bytes válidos únicamente
        if (isImageStart(data, len)) {
            frameBuilder.reset();
        }
        frameBuilder.write(data, 0, len);
        if (isImageEnd(data, len)) {
            byte[] frame = frameBuilder.toByteArray();
            sendImage(clientSocket, frame);
            frameBuilder.reset();
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

    public static void detectCameraUDP(String ip) {
        System.out.println("Escaneando puertos UDP en " + ip + "...");
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(100);
            byte[] probe = "PING".getBytes(StandardCharsets.US_ASCII);
            for (int port = 20000; port < 65535; port++) {
                try {
                    DatagramPacket packet = new DatagramPacket(probe, probe.length, InetAddress.getByName(ip), port);
                    socket.send(packet);
                    DatagramPacket response = new DatagramPacket(new byte[1024], 1024);
                    socket.receive(response);
                    System.out.println("Posible puerto UDP activo: " + port);
                } catch (SocketTimeoutException ignore) {
                } catch (IOException e) {
                    // ignorar errores de envío
                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public static int detectAndDumpStream(String camIp) {
        final int START = 20000;
        final int END = 40200;
        final int WIN_MS = 1600;      // ventana por puerto
        final int SOCKET_TIMEOUT = 300;
        final byte[] probe = "PING".getBytes(StandardCharsets.US_ASCII);
        byte[] recvBuf = new byte[8192];
        int foundPort = -1;

        try (DatagramSocket probeSocket = new DatagramSocket()) {
            probeSocket.setSoTimeout(SOCKET_TIMEOUT);
            InetAddress addr = InetAddress.getByName(camIp);

            // Pre-burst: enviar un pulso a todo el rango (acelera respuestas si la cámara necesita handshake)
            for (int p = START; p <= END; p += 20) { // saltos para no saturar la red
                try {
                    probeSocket.send(new DatagramPacket(probe, probe.length, addr, p));
                } catch (Exception ignored) {
                }
            }

            // Escaneo por puerto
            for (int port = START; port <= END; port++) {
                ByteArrayOutputStream accum = new ByteArrayOutputStream(262144);
                long windowStart = System.currentTimeMillis();
                int packets = 0;
                int totalBytes = 0;

                // Enviar un par de probes continuos al puerto para provocar respuesta
                for (int k = 0; k < 3; k++) {
                    try {
                        probeSocket.send(new DatagramPacket(probe, probe.length, addr, port));
                    } catch (Exception ignored) {
                    }
                }

                while (System.currentTimeMillis() - windowStart < WIN_MS) {
                    try {
                        DatagramPacket rcv = new DatagramPacket(recvBuf, recvBuf.length);
                        probeSocket.receive(rcv);
                        // Solo procesar paquetes provenientes de la cámara
                        if (!rcv.getAddress().equals(addr)) {
                            continue;
                        }
                        packets++;
                        int len = rcv.getLength();
                        totalBytes += len;
                        accum.write(rcv.getData(), 0, len);
                    } catch (SocketTimeoutException ste) {
                        // no recibido en este intervalo, volver a enviar probe si preciso
                        try {
                            probeSocket.send(new DatagramPacket(probe, probe.length, addr, port));
                        } catch (Exception ignored) {
                        }
                    }
                }

                // Heurística de validación
                byte[] data = accum.toByteArray();
                boolean mostlyBinary = isMostlyBinary(data);
                boolean containsJPEG = containsJPEGSignature(data);

                if (packets >= 6 && totalBytes > 15000 && mostlyBinary && containsJPEG) {
                    System.out.println("Puerto detectado: " + port + " (packets=" + packets + " bytes=" + totalBytes + ")");
                    foundPort = port;

                    // Reconstruir primer frame y guardarlo (buscar FFD8..FFD9)
                    int startIdx = findSequence(data, new byte[]{(byte) 0xFF, (byte) 0xD8});
                    int endIdx = findSequence(data, new byte[]{(byte) 0xFF, (byte) 0xD9});
                    if (startIdx >= 0 && endIdx > startIdx) {
                        byte[] frame = Arrays.copyOfRange(data, startIdx, endIdx + 2);
                        try (FileOutputStream fos = new FileOutputStream("frame_detected.jpg")) {
                            fos.write(frame);
                            System.out.println("Frame guardado: frame_detected.jpg (size=" + frame.length + ")");
                        } catch (IOException ioe) {
                            System.err.println("No se pudo guardar frame: " + ioe.getMessage());
                        }
                    } else {
                        // si no se encontró cierre, vuelca acumulado parcial por inspección
                        try (FileOutputStream fos = new FileOutputStream("stream_dump.bin")) {
                            fos.write(data);
                            System.out.println("Dump guardado: stream_dump.bin (inspección manual)");
                        } catch (IOException ioe) {
                            System.err.println("No se pudo guardar dump: " + ioe.getMessage());
                        }
                    }

                    break;
                }

                // optimización: si la cámara no responde a los primeros N puertos, seguir
            }
        } catch (SocketException se) {
            System.err.println("Socket error: " + se.getMessage());
        } catch (UnknownHostException uhe) {
            System.err.println("Unknown host: " + uhe.getMessage());
        } catch (IOException ioe) {
            System.err.println("IO error: " + ioe.getMessage());
        }

        if (foundPort == -1) {
            System.out.println("No se detectó puerto en rango 40000-40200.");
        }
        return foundPort;
    }

// util
    private static boolean isMostlyBinary(byte[] data) {
        if (data == null || data.length == 0) {
            return false;
        }
        int nonAscii = 0;
        int len = Math.min(data.length, 20000); // limitar inspección
        for (int i = 0; i < len; i++) {
            int b = data[i] & 0xFF;
            if (b < 0x20 || b > 0x7E) {
                nonAscii++;
            }
        }
        return nonAscii > (len * 0.65);
    }

    private static boolean containsJPEGSignature(byte[] data) {
        if (data == null || data.length < 4) {
            return false;
        }
        for (int i = 0; i < data.length - 1; i++) {
            int b1 = data[i] & 0xFF;
            int b2 = data[i + 1] & 0xFF;
            if (b1 == 0xFF && (b2 == 0xD8 || b2 == 0xD9)) {
                return true;
            }
        }
        return false;
    }

    private static int findSequence(byte[] data, byte[] seq) {
        if (data == null || seq == null || seq.length == 0) {
            return -1;
        }
        outer:
        for (int i = 0; i <= data.length - seq.length; i++) {
            for (int j = 0; j < seq.length; j++) {
                if (data[i + j] != seq[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    static class Camera {

        private String name;
        private String ip;
        DatagramSocket socket;
        private byte[] frame;
        private boolean isOnline;

        public Camera(String name, String ip, DatagramSocket socket) {
            this.name = name;
            this.ip = ip;
            this.socket = socket;
            this.frame = new byte[0];
            this.isOnline = false;
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

        public void setOnline(boolean state) {
            this.isOnline = state;
        }

        public boolean isOnline() {
            return isOnline;
        }

        @Override
        public String toString() {
            return "Camera{" + "name=" + name + ", IP=" + ip + ", IsOnline=" + isOnline + '}';
        }

    }
}
