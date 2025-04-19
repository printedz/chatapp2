import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

public class Main {
    private static JFrame chatFrame;
    private static JTextArea chatArea;
    private static JTextField messageField;
    private static JButton sendButton;
    private static JButton connectButton;
    private static String userName;
    private static Networking networking;
    private static NotificationSound notificationSound;
    private static final int SERVER_PORT = 12345;
    private static final String SERVER_ADDRESS = "localhost";
    private static final String SOUND_FILE_PATH = "resources/sounds/notif.wav"; // Ruta al archivo de sonido

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Inicializar el objeto NotificationSound
        notificationSound = new NotificationSound(SOUND_FILE_PATH);

        SwingUtilities.invokeLater(() -> {
            solicitarNombreUsuario();
        });
    }

    private static void solicitarNombreUsuario() {
        JPanel panel = new JPanel(new GridLayout(4, 1, 10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JTextField nameField = new JTextField(20);
        JTextField hostField = new JTextField(SERVER_ADDRESS, 20);  // Pre-fill with default

        panel.add(new JLabel("Introduce tu nombre de usuario:"));
        panel.add(nameField);
        panel.add(new JLabel("Introduce la dirección del servidor:"));
        panel.add(hostField);

        int result = JOptionPane.showConfirmDialog(null, panel, "Chat - Inicio de sesión",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION && !nameField.getText().trim().isEmpty()) {
            userName = nameField.getText().trim();
            String serverHost = hostField.getText().trim().isEmpty() ?
                    SERVER_ADDRESS : hostField.getText().trim();

            crearVentanaChat();
            networking = new Networking(serverHost, SERVER_PORT);
            networking.setMessageHandler(Main::handleIncomingMessage);
            networking.setStatusHandler(Main::handleConnectionStatus);
        } else {
            System.exit(0);
        }
    }

    private static void crearVentanaChat() {
        chatFrame = new JFrame("Chat - " + userName);
        chatFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        chatFrame.setSize(600, 400);
        chatFrame.setMinimumSize(new Dimension(400, 300));

        // Panel principal
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Área de chat
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(chatArea);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Panel de entrada de mensaje
        JPanel messagePanel = new JPanel(new BorderLayout(5, 5));
        messageField = new JTextField();
        messageField.addActionListener(e -> enviarMensaje());

        sendButton = new JButton("Enviar");
        sendButton.addActionListener(e -> enviarMensaje());
        sendButton.setEnabled(false);

        messagePanel.add(messageField, BorderLayout.CENTER);
        messagePanel.add(sendButton, BorderLayout.EAST);

        // Panel de botones
        JPanel buttonPanel = new JPanel(new BorderLayout());
        connectButton = new JButton("Conectar");
        connectButton.addActionListener(e -> toggleConnection());
        buttonPanel.add(connectButton, BorderLayout.WEST);

        // Panel inferior
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(messagePanel, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        chatFrame.add(mainPanel);
        chatFrame.setLocationRelativeTo(null);
        chatFrame.setVisible(true);

        chatFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (networking != null) {
                    networking.disconnect();
                    networking.shutdown();
                }
                System.exit(0);
            }
        });

        agregarMensajeSistema("Bienvenido al chat, " + userName + ". Presiona 'Conectar' para comenzar.");
    }

    private static void toggleConnection() {
        if (networking.isConnected()) {
            networking.disconnect();
        } else {
            connectButton.setText("Conectando...");
            connectButton.setEnabled(false);
            new Thread(() -> {
                networking.connect();
                if (networking.isConnected()) {
                    networking.sendMessage("USER:" + userName);
                }
            }).start();
        }
    }

    private static void enviarMensaje() {
        String message = messageField.getText().trim();
        if (!message.isEmpty() && networking.isConnected()) {
            networking.sendMessage("MSG:" + message);
            messageField.setText("");

            // Reproducir el sonido de notificación al enviar el mensaje
            notificationSound.makeNoise();
        }
    }

    private static void handleIncomingMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(message + "\n");
            // Auto-scroll al final
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    private static void handleConnectionStatus(Networking.ConnectionStatus status) {
        SwingUtilities.invokeLater(() -> {
            switch (status) {
                case CONNECTED:
                    connectButton.setText("Desconectar");
                    connectButton.setEnabled(true);
                    sendButton.setEnabled(true);
                    agregarMensajeSistema("Conectado al servidor.");
                    break;
                case DISCONNECTED:
                    connectButton.setText("Conectar");
                    connectButton.setEnabled(true);
                    sendButton.setEnabled(false);
                    agregarMensajeSistema("Desconectado del servidor.");
                    break;
                case CONNECTION_FAILED:
                    connectButton.setText("Conectar");
                    connectButton.setEnabled(true);
                    sendButton.setEnabled(false);
                    agregarMensajeSistema("Error al conectar con el servidor. Verifica que el servidor esté en ejecución.");
                    break;
                case RECONNECTING:
                    connectButton.setText("Reconectando...");
                    connectButton.setEnabled(false);
                    sendButton.setEnabled(false);
                    agregarMensajeSistema("Intentando reconectar...");
                    break;
            }
        });
    }

    private static void agregarMensajeSistema(String mensaje) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append("[Sistema] " + mensaje + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }
}