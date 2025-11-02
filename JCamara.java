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

    private static final ArrayList<Camera> cameras = new ArrayList<>();
    private static final Map<String, Boolean> desiredMacAddress = new HashMap<>();
    // Socket UDP global, igual que "s" en tu script Python
    private static DatagramSocket udpSocket = null;
    // Bandera que indica si se inicializó la cámara con éxito (igual que camera_initialized)
    private static volatile boolean cameraInitialized = false;
    // Puerto remoto desde el que la cámara envía imágenes (normalmente 8080)
    private static final int CAMERA_SRC_PORT = 8080;
    private static volatile boolean runningServer = true;
    private static final boolean DEBUG = false;

    public static void main(String[] args) {
        // Dirección MAC deseada Ever Sparkle Technologies Ltd & Estatus
        desiredMacAddress.put("00-1e-b5-84-8f-00", Boolean.FALSE);
        desiredMacAddress.put("00:1e:b5:86:15:6d", Boolean.FALSE);
        desiredMacAddress.put("00-1e-b5-86-15-6d", Boolean.FALSE);
        desiredMacAddress.put("00:1e:b5:86:15:6c", Boolean.FALSE);
        desiredMacAddress.put("00-1e-b5-86-15-6c", Boolean.FALSE);
        desiredMacAddress.put("00:1e:b5:84:8f:01", Boolean.FALSE);
        desiredMacAddress.put("00-1e-b5-84-8f-01", Boolean.FALSE);
        desiredMacAddress.put("c8:47:8c:00:00:00", Boolean.FALSE);
        desiredMacAddress.put("c8-47-8c-00-00-00", Boolean.FALSE);
        desiredMacAddress.put("f8:da:0c:7d:e9:2f", Boolean.FALSE);
        desiredMacAddress.put("f8-da-0c-7d-e9-2f", Boolean.FALSE);

        Scanner in = new Scanner(System.in);
        while (true) {
            System.out.println("--- MENU ---");
            System.out.println("1. Modo Estación (STA)");
            System.out.println("2. Conectar a Cámara.");
            System.out.println("3. Detectar cámaras UDP.");
            System.out.println("4. Detectar puertos en cámaras UDP.");
            System.out.println("0. Salir del programa.");
            int opc = in.nextInt();
            switch (opc) {
                case 0:
                    System.out.println("Hasta la próxima!");
                    System.exit(0);
                    break;
                case 1:
                    System.out.println("Ingrese el ESSID:");
                    String essid = in.next();
                    System.out.println("Ingrese la contraseña:");
                    String pass = in.next();

                    System.out.println("Esperando por respuesta...");

                    if (setStationMode(essid, pass)) {
                        System.out.println("Modo STA activado");
                    } else {
                        System.out.println("Espere...");
                    }
                    break;
                case 2:
                    discoverCameras();
                    if (cameras.isEmpty()) {
                        System.out.println("No se encontraron cámaras!");
                        System.out.println("Desea salir? Y o N");

                        if (in.next().equalsIgnoreCase("y")) {
                            System.out.println("Saliendo del programa!");
                            System.exit(0);
                        } else {
                            System.out.println("Si sabe que su cámara está activa, ingrese su IP:");
                            String ipCam = in.next();
                            if (isAnIP(ipCam)) {
                                System.out.println("Íngrese un nombre para la cámara:");
                                String nCam = in.next();
                                System.out.println("Conectando con " + nCam + " en: " + ipCam);
                                try {
                                    initiateCameraSTMode(nCam, ipCam);
                                } catch (IOException ex) {
                                    System.out.println("IOE: " + ex.getMessage());
                                }
                            } else {
                                System.out.println("Esta aplicación no es un chabot, No fuí programado para saber lo que piensas.\nEl programa finalizará ahora!");
                                System.exit(0);
                            }
                        }
                    } else {
                        System.out.println("Elija la cámara: ");
                        for (Camera cam : cameras) {
                            System.out.println(cameras.indexOf(cam) + ". " + cam.toString());
                        }
                        String selectedCam = in.next();
                        try {
                            int option = Integer.parseInt(selectedCam);
                            Camera myCam = cameras.get(option);
                            if (myCam.ip.equals("192.168.4.153")) {
                                System.out.println("Ésta cámara está en modo AP!");
                                initiateCameraAPMode(myCam.getName(), myCam.ip);
                                initServer(myCam);
                            } else {
                                System.out.println("Conectando con " + myCam.getName());
                                initiateCameraSTMode(myCam.getName(), myCam.ip);
                                initServer(myCam);
                            }
                        } catch (NumberFormatException ex) {
                            System.out.println("NumberFormaEx: " + ex.getMessage());
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
                    System.out.println("Comando no disponible!");
            }
        }
    }

    private static boolean initiateCameraAPMode(String name, String ip) throws IOException {
        // crear y bindear el socket global si no existe
        if (udpSocket == null || udpSocket.isClosed()) {
            udpSocket = new DatagramSocket(null);
            udpSocket.setReuseAddress(true);
            udpSocket.bind(new InetSocketAddress(0)); // bind a ephemeral port en 0.0.0.0
            udpSocket.setSoTimeout(1000);
        }

        InetAddress address = InetAddress.getByName(ip);
        byte[] b1 = new byte[]{(byte) 0x30, (byte) 0x67};
        byte[] b2 = new byte[]{(byte) 0x30, (byte) 0x66};
        byte[] b3 = new byte[]{(byte) 0x42, (byte) 0x76};

        udpSocket.send(new DatagramPacket(b1, b1.length, address, 8070));
        udpSocket.send(new DatagramPacket(b2, b2.length, address, 8070));
        udpSocket.send(new DatagramPacket(b3, b3.length, address, 8080));

        // guardamos la cámara pero NO crear otro socket
        Camera camera = new Camera(name, ip, udpSocket);
        camera.setOnline(true);
        cameras.add(camera);

        cameraInitialized = true;
        System.out.println("Handshake enviado. UDP local port = " + udpSocket.getLocalPort());
        return true;
    }

    private static boolean initiateCameraSTMode(String nCam, String ip) throws IOException {
        if (udpSocket == null || udpSocket.isClosed()) {
            udpSocket = new DatagramSocket(null);
            udpSocket.setReuseAddress(true);
            udpSocket.bind(new InetSocketAddress(0));
            udpSocket.setSoTimeout(1000);
        }

        InetAddress address = InetAddress.getByName(ip);

        udpSocket.send(new DatagramPacket(new byte[]{(byte) 0x30, (byte) 0x67}, 2, address, 12476));
        udpSocket.send(new DatagramPacket(new byte[]{(byte) 0x30, (byte) 0x66}, 2, address, 12476));
        udpSocket.send(new DatagramPacket(new byte[]{(byte) 0x42, (byte) 0x76}, 2, address, 32108));
        udpSocket.send(new DatagramPacket(new byte[]{(byte) 0x5D, (byte) 0x78, (byte) 0x28, (byte) 0x18}, 4, address, 32108));

        Camera camera = new Camera(nCam, ip, udpSocket);
        camera.setOnline(true);
        cameras.add(camera);

        cameraInitialized = true;
        System.out.println("Handshake enviado. UDP local port = " + udpSocket.getLocalPort());
        return true;
    }

    private static void initServer(Camera cam) {
        System.out.println("Cámara disponible!");

        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(8081)) {
                serverSocket.setReuseAddress(true);
                System.out.println("Servidor iniciado en http://127.0.0.1:8081/");

                while (runningServer) {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setSoTimeout(15000);
                    new Thread(new ClientHandler(clientSocket, cam)).start();
                }
            } catch (IOException e) {
                System.err.println("Error de servidor: " + e.getMessage());
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

            System.out.println("Enviando datos y esperando respuesta...");
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName("192.168.4.153"), port);
            socket.send(packet);

            byte[] responseBuffer = new byte[Byte.MAX_VALUE];
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
            socket.receive(responsePacket);

            String response = new String(responsePacket.getData(), 0, responsePacket.getLength());
            if (!response.isEmpty()) {
                System.out.println("Datos adquiridos: ");
                System.out.println("Respuesta: " + response);
                System.out.println("ASCII: " + asciiToHex(response));
                if (response.equals("f�����")) {
                    return true;
                }
            }
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }
        return false;
    }

    // Descubrir si hay cámaras en la red por ARP
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
                System.err.println(e.getMessage());
            }
        }
    }

    // Clase de control
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
                System.err.println("Stream ended: " + se.getMessage());
            } catch (IOException e) {
                System.err.println("Client disconnected: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    // Publica la página html estática
    private static void sendHtml(OutputStream out) throws IOException {
        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>JCamara A9</title>
                    <style>
                        body { background:#000; color:#0f0; text-align:center; font-family:monospace; }
                        img { width:90%%; max-width:640px; margin-top:30px; border:3px solid #0f0; border-radius:12px; }
                        h2 { color:#0f0; margin-top:20px; }
                    </style>
                </head>
                <body>
                    <h2>JCamara A9 Live</h2>
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

    // Envía el flujo de imagen mjpg al navegador
    private static void startStream(Socket clientSocket, Camera camera) throws IOException {
        OutputStream raw = clientSocket.getOutputStream();
        BufferedOutputStream out = new BufferedOutputStream(raw, 16384);

        String boundary = "jpgboundary";
        out.write(("HTTP/1.1 200 OK\r\n"
                + "Cache-Control: no-cache\r\n"
                + "Pragma: no-cache\r\n"
                + "Connection: keep-alive\r\n"
                + "Content-Type: multipart/x-mixed-replace; boundary=--" + boundary + "\r\n"
                + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();

        // No emitimos boundary antes — emulamos Python: el primer frame incluirá headers en la parte.
        ByteArrayOutputStream frameBuilder = new ByteArrayOutputStream(512 * 1024);

        if (udpSocket == null) {
            throw new IOException("Socket UDP no inicializado. Invoque primero a initiateCamera...");
        }
        udpSocket.setSoTimeout(500);

        System.out.println("Transmitiendo en cámara: " + camera.getName() + " @ " + camera.ip + " (puerto UDP=" + udpSocket.getLocalPort() + ")");

        byte[] recvBuf = new byte[8192];
        DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
        int packetCounter = 0;
        boolean firstFrameSaved = false;

        try {
            while (!clientSocket.isClosed() && clientSocket.isConnected() && cameraInitialized) {
                try {
                    udpSocket.receive(packet);
                    packetCounter++;
                    InetAddress src = packet.getAddress();
                    int srcPort = packet.getPort();
                    int len = packet.getLength();
                    if (DEBUG) {
                        System.out.println("UDP recv #" + packetCounter + " from " + src.getHostAddress() + ":" + srcPort + " len=" + len);
                    }

                    // Solo procesar paquetes enviados desde el puerto fuente esperado (igual que python: if port == 8080)
                    if (srcPort != CAMERA_SRC_PORT) {
                        // puedes loggear si quieres; algunas cámaras usan otro puerto en STA/AP.
                        continue;
                    }

                    if (len <= 8) {
                        continue; // sin payload útil
                    }
                    int payloadOffset = 8;
                    int payloadLen = len - payloadOffset;
                    byte[] buf = packet.getData();

                    // Dump hex de los primeros bytes del primer paquete para comparar con Python
                    if (!firstFrameSaved) {
                        System.out.println("Carga inicial hex (first 64 bytes): " + hexDump(buf, payloadOffset, Math.min(payloadLen, 64)));
                        firstFrameSaved = true;
                    }

                    boolean startHere = isImageStartInBuffer(buf, payloadOffset, payloadLen);
                    boolean endHere = isImageEndInBuffer(buf, payloadOffset, payloadLen);

                    if (startHere) {
                        if (DEBUG) {
                            System.out.println("  -> Inicio de JPEG en paquete.");
                        }
                        frameBuilder.reset();
                    }

                    frameBuilder.write(buf, payloadOffset, payloadLen);

                    if (endHere) {
                        if (DEBUG) {
                            System.out.println("  -> Fin del paquete JPEG; Tamaño de ventana=" + frameBuilder.size());
                        }
                        byte[] frame = frameBuilder.toByteArray();

                        // Enviar boundaries y headers exactamente como python
                        // boundary antes de los headers de la parte: "\r\n--jpgboundary\r\n"
                        out.write(("\r\n--" + boundary + "\r\n").getBytes(StandardCharsets.US_ASCII));
                        out.write(("Content-Type: image/jpeg\r\n").getBytes(StandardCharsets.US_ASCII));
                        out.write(("Content-Length: " + frame.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                        out.write(frame);
                        out.flush();
                        if (DEBUG) {
                            System.out.println("Frames enviados, bytes=" + frame.length);
                        }
                        frameBuilder.reset();
                    }
                } catch (SocketTimeoutException ste) {
                    if (clientSocket.isClosed() || !clientSocket.isConnected()) {
                        System.err.println("Conexión del cliente al socket cerrada, deteniendo flujo.");
                        break;
                    }
                    // else continuar
                }
            }
        } finally {
            try {
                out.flush();
            } catch (IOException ignored) {
            }
            System.out.println("Flujo detenido.");
        }
    }

    // Auxiliar para detectar cámara por medio de puerto UDP abierto.
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

    // Auxiliar para detectar cámara por medio de puerto UDP abierto.
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
                } catch (IOException ignored) {
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
                    } catch (IOException ignored) {
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
                        } catch (IOException ignored) {
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

    // ---- Utilidades auxiliares para los métodos ----
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

    private static boolean isImageStartInBuffer(byte[] buffer, int offset, int len) {
        if (buffer == null || len < 2) {
            return false;
        }
        int end = offset + len - 1;
        for (int i = offset; i < end; i++) {
            if ((buffer[i] & 0xFF) == 0xFF && (buffer[i + 1] & 0xFF) == 0xD8) {
                return true;
            }
        }
        return false;
    }

    private static boolean isImageEndInBuffer(byte[] buffer, int offset, int len) {
        if (buffer == null || len < 2) {
            return false;
        }
        int end = offset + len - 1;
        for (int i = offset; i < end; i++) {
            if ((buffer[i] & 0xFF) == 0xFF && (buffer[i + 1] & 0xFF) == 0xD9) {
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

    // Convertir Hex a ASCII
    public static String hexToAscii(String hexString) {
        StringBuilder output = new StringBuilder();

        for (int i = 0; i < hexString.length(); i += 2) {
            String hex = hexString.substring(i, i + 2);
            int decimal = Integer.parseInt(hex, 16);
            output.append((char) decimal);
        }

        return output.toString();
    }

    // Convertir ASCII a Hex
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

    // validar si el formato de IP es correcto
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

    // Sanitizar IP
    private static String checkIP(String ip) {
        return ip.replace('(', ' ').replace(')', ' ').trim();
    }

    // Validar formato de MAC address
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

    // Devuelve en Hex una cadena de bytes
    private static String hexDump(byte[] buf, int offset, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = offset; i < offset + len; i++) {
            sb.append(String.format("%02X ", buf[i]));
        }
        return sb.toString().trim();
    }

    // Obtener nombre de la cámara (opcional)
    /*private Camera getCameraByName(String name) {
        for (Camera camera : cameras) {
            if (camera.getName().equals(name)) {
                return camera;
            }
        }
        return null;
    }*/
    static class Camera {

        private final String name;
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
