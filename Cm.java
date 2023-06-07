import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 *
 * @author Allan Ayes
 */
public class Cm {
    public static void main(String[] args) {
        try {
            Scanner scan = new Scanner(System.in);
            System.out.println("Type the IP of Mini Spy Cam\nDefault: 192.168.4.153");
            String ipCam = scan.nextLine();
            DatagramSocket socket = new DatagramSocket();
            socket.setReuseAddress(true);

            CameraHandler cameraHandler = new CameraHandler(socket, ipCam);
            Thread cameraThread = new Thread(cameraHandler);
            cameraThread.start();
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
    }

}

class CameraHandler implements Runnable {

    private DatagramSocket socket;
    private boolean cameraInitialized;
    private byte[] frame;
    private JFrame frameWindow;
    private JLabel imageLabel;
    private String ipCam = "192.168.4.153";

    public CameraHandler(DatagramSocket socket, String ipCam) {
        this.socket = socket;
        if (!ipCam.isBlank()) {
            this.ipCam = ipCam;
        }
        this.cameraInitialized = initCam(socket);
        this.frame = new byte[0];
        this.frameWindow = new JFrame("TT-Mini Spy Camera Stream");
        this.imageLabel = new JLabel();
        frameWindow.getContentPane().add(imageLabel);
        frameWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frameWindow.pack();
        frameWindow.setVisible(true);
    }

    private boolean initCam(DatagramSocket socket) {
        try {
            InetAddress address = InetAddress.getByName(ipCam);
            int port = 8070;

            byte[] buffer = ByteBuffer.allocate(2).put((byte) 0x30).put((byte) 0x67).array();
            socket.send(new DatagramPacket(buffer, buffer.length, address, port));

            buffer = ByteBuffer.allocate(2).put((byte) 0x30).put((byte) 0x66).array();
            socket.send(new DatagramPacket(buffer, buffer.length, address, port));

            port = 8080;
            buffer = ByteBuffer.allocate(2).put((byte) 0x42).put((byte) 0x76).array();
            socket.send(new DatagramPacket(buffer, buffer.length, address, port));

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean isImageStart(byte[] buffer) {
        for (int i = 0; i < buffer.length - 1; i++) {
            if (buffer[i] == (byte) 0xff && buffer[i + 1] == (byte) 0xd8) {
                return true;
            }
        }
        return false;
    }

    private boolean isImageEnd(byte[] buffer) {
        for (int i = 0; i < buffer.length - 1; i++) {
            if (buffer[i] == (byte) 0xff && buffer[i + 1] == (byte) 0xd9) {
                return true;
            }
        }
        return false;
    }

    private void sendImage(byte[] frame) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(frame);
        BufferedImage image = ImageIO.read(inputStream);
        if (image != null) {
            SwingUtilities.invokeLater(() -> {
                imageLabel.setIcon(new ImageIcon(image));
                frameWindow.pack();
            });
        }
    }

    public void run() {

        while (cameraInitialized) {
            try {
                byte[] buffer = new byte[4098];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                int port = packet.getPort();
                buffer = packet.getData();

                /*for (byte bb : buffer) {
                    System.out.print((char) bb);
                }*/
                if (port == 8080) {

                    if (isImageStart(buffer)) {
                        frame = new byte[buffer.length - 8];
                        System.arraycopy(buffer, 8, frame, 0, frame.length);
                    } else {
                        //ByteBuffer bbf = ByteBuffer.allocate(frame.length + buffer.length);
                        //bbf.put(frame);
                        //bbf.put(buffer);
                        //frame = bbf.array();

                        //byte[] newFrame = new byte[frame.length + buffer.length ];                        
                        //System.arraycopy(frame, 0, newFrame, 0, frame.length);
                        //System.arraycopy(buffer, frame.length, newFrame, frame.length, buffer.length );
                        //frame = newFrame;
                        byte[] nframe = new byte[frame.length + buffer.length];
                        int op = 0, bp = 0;
                        for (int i = 0; i < nframe.length - 1; i++) {
                            if (i > frame.length -1) {
                                nframe[i] = buffer[bp++];
                            } else {
                                nframe[i] = frame[op++];
                            }
                        }
                        frame = nframe;
                    }
                    if (isImageEnd(buffer)) {
                        sendImage(frame);
                    }
                } else if (port == 8070) {
                    // TODO
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
