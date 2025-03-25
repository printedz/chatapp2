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
    private static final int SERVER_PORT = 12345;
    private static final String SERVER_ADDRESS = "localhost";

    public static void main(String[] args) {
        // Configurar el look and feel para una mejor apariencia
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Inicializar networking
        networking = new Networking(SERVER_ADDRESS, SERVER_PORT);

        // Configurar manejadores de mensajes y estados
        networking.setMessageHandler(Main::handleIncomingMessage);
        networking.setStatusHandler(Main::handleConnectionStatus);

        // Mostrar diálogo de inicio para solicitar nombre de usuario
        solicitarNombreUsuario();
    }

    private static void solicitarNombreUsuario() {
        userName = JOptionPane.showInputDialog(null,
                "Por favor ingrese su nombre de usuario:",
                "Inicio de Chat",
                JOptionPane.QUESTION_MESSAGE);

        // Verificar si el usuario canceló el diálogo o no ingresó nombre
        if (userName == null || userName.trim().isEmpty()) {
            int respuesta = JOptionPane.showConfirmDialog(null,
                    "Debe ingresar un nombre para continuar.\n¿Desea intentarlo de nuevo?",
                    "Nombre requerido",
                    JOptionPane.YES_NO_OPTION);
            if (respuesta == JOptionPane.YES_OPTION) {
                solicitarNombreUsuario();
            } else {
                System.exit(0);
            }
        } else {
            // Una vez tenemos el nombre, creamos la ventana de chat
            crearVentanaChat();
        }
    }

    private static void crearVentanaChat() {
        // Crear la ventana principal
        chatFrame = new JFrame("Chat - " + userName);
        chatFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        chatFrame.setSize(500, 600);
        chatFrame.setLayout(new BorderLayout(10, 10)); // Añadir espacio entre componentes

        // Crear panel principal con padding
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15)); // Padding de 15px en todos los lados

        // Área de chat (historial de mensajes)
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        chatArea.setBorder(new EmptyBorder(10, 10, 10, 10)); // Padding interno al área de texto

        // Agregar mensaje de bienvenida
        chatArea.append("Bienvenido al chat, " + userName + "!\n");
        chatArea.append("Presiona 'Conectar' para unirte al chat.\n\n");

        JScrollPane scrollPane = new JScrollPane(chatArea);
        // Padding para el ScrollPane
        scrollPane.setBorder(new CompoundBorder(
                new EmptyBorder(0, 0, 10, 0),    // Margen exterior
                scrollPane.getBorder()));        // Borde original

        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Panel para el botón de conexión
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        connectButton = new JButton("Conectar");
        connectButton.setMargin(new Insets(5, 10, 5, 10));
        connectButton.addActionListener(e -> toggleConnection());
        topPanel.add(connectButton);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Panel inferior para escribir mensajes
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BorderLayout(8, 0)); // Espacio horizontal de 8px entre campo y botón
        bottomPanel.setBorder(new EmptyBorder(5, 0, 5, 0)); // Padding vertical

        // Campo para escribir mensajes
        messageField = new JTextField();
        messageField.addActionListener(e -> enviarMensaje());
        messageField.setEnabled(false); // Deshabilitado hasta establecer conexión
        // Agregar padding al campo de texto
        messageField.setBorder(new CompoundBorder(
                messageField.getBorder(),               // Mantener borde original
                new EmptyBorder(8, 8, 8, 8)));         // Agregar padding interno

        // Botón para enviar con padding
        sendButton = new JButton("Enviar");
        sendButton.addActionListener(e -> enviarMensaje());
        sendButton.setMargin(new Insets(8, 15, 8, 15)); // Padding interno del botón
        sendButton.setEnabled(false); // Deshabilitado hasta establecer conexión

        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        // Agregar panel inferior al panel principal
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // Agregar panel principal a la ventana
        chatFrame.add(mainPanel, BorderLayout.CENTER);

        // Configurar cierre de ventana para liberar recursos
        chatFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (networking != null) {
                    networking.shutdown();
                }
            }
        });

        // Centrar la ventana y hacerla visible
        chatFrame.setLocationRelativeTo(null);
        chatFrame.setVisible(true);
    }

    private static void toggleConnection() {
        if (networking.isConnected()) {
            // Desconectar del servidor
            networking.disconnect();
            connectButton.setText("Conectar");
            messageField.setEnabled(false);
            sendButton.setEnabled(false);
            agregarMensajeSistema("Desconectado del servidor.");
        } else {
            // Conectar al servidor
            agregarMensajeSistema("Conectando al servidor...");
            networking.connect();
        }
    }

    private static void enviarMensaje() {
        String mensaje = messageField.getText().trim();
        if (!mensaje.isEmpty() && networking.isConnected()) {
            // Formato del mensaje: "NOMBRE: mensaje"
            String formattedMessage = userName + ": " + mensaje;

            // Enviar mensaje al servidor
            if (networking.sendMessage(formattedMessage)) {
                // Agregar mensaje a la interfaz
                chatArea.append("Tú: " + mensaje + "\n");

                // Limpiar el campo de mensaje
                messageField.setText("");

                // Desplazar automáticamente hacia abajo
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
            } else {
                agregarMensajeSistema("Error al enviar el mensaje. Verifica la conexión.");
            }
        }
    }

    private static void handleIncomingMessage(String message) {
        // Añadir mensaje recibido al área de chat
        chatArea.append(message + "\n");

        // Desplazar automáticamente hacia abajo
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private static void handleConnectionStatus(Networking.ConnectionStatus status) {
        switch (status) {
            case CONNECTED:
                connectButton.setText("Desconectar");
                messageField.setEnabled(true);
                sendButton.setEnabled(true);
                agregarMensajeSistema("Conectado al servidor!");
                // Enviar mensaje de presentación
                networking.sendMessage(userName + " se ha unido al chat!");
                break;

            case DISCONNECTED:
                connectButton.setText("Conectar");
                messageField.setEnabled(false);
                sendButton.setEnabled(false);
                agregarMensajeSistema("Desconectado del servidor.");
                break;

            case CONNECTION_FAILED:
                connectButton.setText("Conectar");
                messageField.setEnabled(false);
                sendButton.setEnabled(false);
                agregarMensajeSistema("Error al conectar con el servidor. Por favor, inténtalo de nuevo.");
                break;

            case RECONNECTING:
                agregarMensajeSistema("Intentando reconectar...");
                break;
        }
    }

    private static void agregarMensajeSistema(String mensaje) {
        chatArea.append("[Sistema] " + mensaje + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }
}