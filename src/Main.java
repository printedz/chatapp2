import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat; // Importar para timestamp local
import java.util.Date; // Importar para timestamp local

/**
 * Clase principal para la interfaz gráfica (GUI) del cliente de chat.
 * Utiliza Swing para la ventana y componentes.
 */
public class Main {
    // Componentes de la GUI
    private static JFrame chatFrame;
    private static JTextArea chatArea;
    private static JTextField messageField;
    private static JButton sendButton;
    private static JButton connectButton;

    // Información del cliente y conexión
    private static String userName;
    private static Networking networking; // Objeto para manejar la red
    private static NotificationSound notificationSound; // Objeto para reproducir sonidos

    // Valores predeterminados para la conexión
    private static final int DEFAULT_SERVER_PORT = 12345;
    private static final String DEFAULT_SERVER_ADDRESS = "localhost";
    private static final String SOUND_FILE_PATH = "resources/sounds/notif.wav"; // Ruta relativa al archivo de sonido

    // Formato para timestamp local (CORRECCIÓN)
    private static final SimpleDateFormat localFormatter = new SimpleDateFormat("HH:mm:ss");


    /**
     * Punto de entrada principal de la aplicación cliente.
     */
    public static void main(String[] args) {
        // Intentar establecer el Look and Feel del sistema operativo para una mejor integración visual
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("No se pudo establecer el Look and Feel del sistema: " + e.getMessage());
        }

        // Inicializar el objeto para reproducir sonidos de notificación
        notificationSound = new NotificationSound(SOUND_FILE_PATH);

        // Iniciar la GUI en el Event Dispatch Thread (EDT) de Swing
        SwingUtilities.invokeLater(() -> {
            solicitarNombreUsuario(); // Pedir nombre de usuario y datos del servidor
        });
    }

    /**
     * Muestra un diálogo para que el usuario introduzca su nombre,
     * la dirección y el puerto del servidor.
     */
    private static void solicitarNombreUsuario() {
        // Panel para organizar los campos de entrada
        JPanel panel = new JPanel(new GridLayout(6, 1, 5, 5)); // 6 filas, 1 columna, espaciado
        panel.setBorder(new EmptyBorder(15, 15, 15, 15)); // Margen interior

        // Campos de texto para los datos
        JTextField nameField = new JTextField(20);
        JTextField hostField = new JTextField(DEFAULT_SERVER_ADDRESS, 20); // Pre-rellenado
        JTextField portField = new JTextField(String.valueOf(DEFAULT_SERVER_PORT), 20); // Pre-rellenado

        // Etiquetas descriptivas
        panel.add(new JLabel("Introduce tu nombre de usuario:"));
        panel.add(nameField);
        panel.add(new JLabel("Introduce la dirección del servidor:"));
        panel.add(hostField);
        panel.add(new JLabel("Introduce el puerto del servidor:"));
        panel.add(portField);

        // Poner el foco inicial en el campo del nombre
        SwingUtilities.invokeLater(nameField::requestFocusInWindow);


        // Mostrar el diálogo modal
        int result = JOptionPane.showConfirmDialog(null, panel, "Chat - Inicio de sesión",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        // Procesar la respuesta del usuario
        if (result == JOptionPane.OK_OPTION) {
            String inputName = nameField.getText().trim();
            if (inputName.isEmpty()) {
                JOptionPane.showMessageDialog(null, "El nombre de usuario no puede estar vacío.", "Error", JOptionPane.ERROR_MESSAGE);
                solicitarNombreUsuario(); // Volver a pedir
                return;
            }
            // Validar nombre (simplificado, el servidor hará la validación principal)
            if (inputName.length() < 3 || inputName.length() > 15 || !inputName.matches("^[a-zA-Z0-9_-]+$")) {
                JOptionPane.showMessageDialog(null, "Nombre inválido (3-15 caracteres, letras, números, _, -).", "Error", JOptionPane.ERROR_MESSAGE);
                solicitarNombreUsuario(); // Volver a pedir
                return;
            }
            userName = inputName;


            // Obtener host y puerto, usando predeterminados si están vacíos
            String serverHost = hostField.getText().trim().isEmpty() ?
                    DEFAULT_SERVER_ADDRESS : hostField.getText().trim();

            int serverPort = DEFAULT_SERVER_PORT;
            try {
                String portText = portField.getText().trim();
                if (!portText.isEmpty()) {
                    int port = Integer.parseInt(portText);
                    // Validar rango de puertos TCP/IP
                    if (port > 0 && port <= 65535) {
                        serverPort = port;
                    } else {
                        JOptionPane.showMessageDialog(null,
                                "Puerto fuera de rango (1-65535). Usando puerto predeterminado: " + DEFAULT_SERVER_PORT,
                                "Advertencia de Puerto", JOptionPane.WARNING_MESSAGE);
                    }
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(null,
                        "Número de puerto inválido. Usando puerto predeterminado: " + DEFAULT_SERVER_PORT,
                        "Advertencia de Puerto", JOptionPane.WARNING_MESSAGE);
            }

            // Si todo es correcto, crear la ventana de chat e inicializar la red
            crearVentanaChat();
            networking = new Networking(serverHost, serverPort); // Crear objeto de red
            // Establecer los métodos que manejarán los mensajes entrantes y los cambios de estado
            networking.setMessageHandler(Main::handleIncomingMessage);
            networking.setStatusHandler(Main::handleConnectionStatus);

        } else {
            // Si el usuario cancela, salir de la aplicación
            System.exit(0);
        }
    }

    /**
     * Crea y muestra la ventana principal del chat.
     */
    private static void crearVentanaChat() {
        chatFrame = new JFrame("Chat - " + userName); // Título de la ventana
        chatFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Controlar cierre manualmente
        chatFrame.setSize(600, 400); // Tamaño inicial
        chatFrame.setMinimumSize(new Dimension(400, 300)); // Tamaño mínimo

        // Panel principal con BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5)); // Espaciado horizontal y vertical
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10)); // Margen exterior

        // Área de texto para mostrar los mensajes del chat
        chatArea = new JTextArea();
        chatArea.setEditable(false); // No se puede escribir directamente
        chatArea.setLineWrap(true); // Salto de línea automático
        chatArea.setWrapStyleWord(true); // Salto de línea respetando palabras
        chatArea.setFont(new Font("SansSerif", Font.PLAIN, 14)); // Fuente legible
        JScrollPane scrollPane = new JScrollPane(chatArea); // Añadir barra de desplazamiento
        mainPanel.add(scrollPane, BorderLayout.CENTER); // Añadir al centro del panel principal

        // Panel para la entrada de mensajes
        JPanel messagePanel = new JPanel(new BorderLayout(5, 0)); // Espaciado horizontal
        messageField = new JTextField(); // Campo para escribir el mensaje
        // Permitir enviar mensaje presionando Enter en el campo de texto
        messageField.addActionListener(e -> enviarMensaje());
        messageField.setEnabled(false); // Deshabilitado hasta conectar

        sendButton = new JButton("Enviar"); // Botón para enviar mensaje
        sendButton.addActionListener(e -> enviarMensaje()); // Acción al pulsar el botón
        sendButton.setEnabled(false); // Deshabilitado hasta conectar

        messagePanel.add(messageField, BorderLayout.CENTER); // Campo de texto al centro
        messagePanel.add(sendButton, BorderLayout.EAST); // Botón de enviar a la derecha

        // Panel para el botón de conexión/desconexión
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT)); // Alineación a la izquierda
        connectButton = new JButton("Conectar"); // Botón para conectar/desconectar
        connectButton.addActionListener(e -> toggleConnection()); // Acción al pulsar
        buttonPanel.add(connectButton);

        // Panel inferior que contiene el panel de mensaje y el panel de botones
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(messagePanel, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH); // Añadir panel inferior al sur del panel principal

        chatFrame.add(mainPanel); // Añadir panel principal a la ventana
        chatFrame.setLocationRelativeTo(null); // Centrar ventana en la pantalla

        // Manejar el evento de cierre de ventana
        chatFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Preguntar al usuario si realmente quiere salir
                int confirm = JOptionPane.showConfirmDialog(chatFrame,
                        "¿Seguro que quieres salir del chat?",
                        "Confirmar Salida", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    // Si confirma, desconectar limpiamente y cerrar
                    if (networking != null) {
                        networking.disconnect(); // Desconectar del servidor
                        networking.shutdown();   // Liberar recursos de red
                    }
                    // Cerrar también el clip de sonido si existe
                    if (notificationSound != null) {
                        notificationSound.closeClip();
                    }
                    chatFrame.dispose(); // Cerrar la ventana
                    System.exit(0);      // Terminar la aplicación
                }
                // Si dice NO, no hacer nada (la ventana permanece abierta)
            }
        });

        chatFrame.setVisible(true); // Hacer visible la ventana

        // Mensaje inicial en el área de chat
        agregarMensajeSistema("Bienvenido al chat, " + userName + ". Presiona 'Conectar' para comenzar.");
    }

    /**
     * Intenta conectar o desconectar del servidor al pulsar el botón correspondiente.
     */
    private static void toggleConnection() {
        if (networking.isConnected()) {
            // Si está conectado, desconectar
            agregarMensajeSistema("Desconectando...");
            networking.disconnect();
        } else {
            // Si no está conectado, intentar conectar en un hilo separado para no bloquear la GUI
            connectButton.setText("Conectando...");
            connectButton.setEnabled(false); // Deshabilitar botón mientras conecta
            messageField.setEnabled(false);
            sendButton.setEnabled(false);
            agregarMensajeSistema("Intentando conectar a " + networking.getServerAddress() + ":" + networking.getServerPort() + "...");

            new Thread(() -> {
                networking.connect(); // Intentar la conexión (bloqueante en este hilo)
                // Después de intentar conectar, si tuvo éxito, enviar el mensaje de identificación USER:
                // Es importante que esto ocurra *antes* de que la GUI se habilite completamente
                if (networking.isConnected()) {
                    networking.sendMessage("USER:" + userName);
                    // La habilitación de la GUI se hará en handleConnectionStatus cuando reciba CONNECTED
                }
                // Si connect() falla, handleConnectionStatus recibirá CONNECTION_FAILED y actualizará la GUI
            }, "ChatClient-ConnectThread").start(); // Dar nombre al hilo
        }
    }

    /**
     * Envía el mensaje escrito en el campo de texto al servidor.
     * Precede el mensaje con "MSG:" según el protocolo del servidor.
     * **CORRECCIÓN:** Añade el mensaje enviado al área de chat localmente.
     */
    private static void enviarMensaje() {
        String message = messageField.getText().trim(); // Obtener texto y quitar espacios extra
        // Solo enviar si hay mensaje, está conectado y el campo está habilitado
        if (!message.isEmpty() && networking.isConnected() && messageField.isEnabled()) {
            // Enviar al servidor
            boolean sent = networking.sendMessage("MSG:" + message);

            if (sent) {
                // --- INICIO CORRECCIÓN: Añadir mensaje localmente ---
                final String messageToShowLocally = getLocalTimestamp() + " [" + userName + "]: " + message;
                SwingUtilities.invokeLater(() -> {
                    chatArea.append(messageToShowLocally + "\n");
                    chatArea.setCaretPosition(chatArea.getDocument().getLength()); // Auto-scroll
                });
                // --- FIN CORRECCIÓN ---

                messageField.setText(""); // Limpiar campo de texto si se envió

                // Opcionalmente, reproducir sonido de notificación al enviar
                if (notificationSound != null) {
                    notificationSound.makeNoise();
                }
            } else {
                // Si falla el envío, informar al usuario
                agregarMensajeSistema("Error al enviar el mensaje. La conexión podría haberse perdido.");
                // Podríamos intentar deshabilitar el envío aquí o forzar una verificación de conexión
            }
            messageField.requestFocusInWindow(); // Devolver foco al campo de texto
        }
    }

    /**
     * Maneja un mensaje entrante del servidor.
     * Lo añade al área de chat. Se ejecuta en el EDT.
     * @param message El mensaje recibido del servidor.
     */
    private static void handleIncomingMessage(String message) {
        // Asegurarse de que la actualización de la GUI se haga en el Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            chatArea.append(message + "\n"); // Añadir mensaje al área de chat
            // Auto-scroll hacia el final para ver el último mensaje
            chatArea.setCaretPosition(chatArea.getDocument().getLength());

            // Reproducir sonido si es un mensaje de otro usuario (opcional)
            boolean isSystemMessage = message.contains("[Sistema]");
            boolean isOwnMessage = message.contains("[" + userName + "]"); // Comprobación simple
            if (notificationSound != null && !isSystemMessage && !isOwnMessage) {
                notificationSound.makeNoise();
            }
        });
    }

    /**
     * Maneja los cambios en el estado de la conexión.
     * Actualiza la GUI (botones, mensajes de sistema) según el estado. Se ejecuta en el EDT.
     * @param status El nuevo estado de la conexión (CONNECTED, DISCONNECTED, etc.).
     */
    private static void handleConnectionStatus(Networking.ConnectionStatus status) {
        // Asegurarse de que la actualización de la GUI se haga en el Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            switch (status) {
                case CONNECTED:
                    // Este estado se recibe DESPUÉS de que connect() tuvo éxito
                    // y DESPUÉS de que el hilo de conexión envió "USER:userName"
                    connectButton.setText("Desconectar");
                    connectButton.setEnabled(true);
                    sendButton.setEnabled(true); // Habilitar envío de mensajes
                    messageField.setEnabled(true); // Habilitar campo de texto
                    agregarMensajeSistema("Conectado al servidor como " + userName + ".");
                    messageField.requestFocusInWindow(); // Poner foco en el campo de mensaje
                    break;
                case DISCONNECTED:
                    connectButton.setText("Conectar");
                    connectButton.setEnabled(true);
                    sendButton.setEnabled(false); // Deshabilitar envío
                    messageField.setEnabled(false); // Deshabilitar campo
                    agregarMensajeSistema("Desconectado del servidor.");
                    break;
                case CONNECTION_FAILED:
                    connectButton.setText("Conectar"); // Permitir reintentar
                    connectButton.setEnabled(true);
                    sendButton.setEnabled(false);
                    messageField.setEnabled(false);
                    // El mensaje de error ya se muestra en connect(), aquí solo actualizamos botones
                    agregarMensajeSistema("Falló la conexión. Revisa la consola para detalles.");
                    // Mostrar diálogo de error si no se hizo ya en connect() (puede ser redundante)
                    /* JOptionPane.showMessageDialog(chatFrame,
                        "No se pudo conectar al servidor en " + networking.getServerAddress() + ":" + networking.getServerPort() +
                        ".\nPor favor, verifica los datos y asegúrate de que el servidor está en ejecución.",
                        "Error de Conexión", JOptionPane.ERROR_MESSAGE); */
                    break;
                case RECONNECTING: // No implementado actualmente
                    connectButton.setText("Reconectando...");
                    connectButton.setEnabled(false);
                    sendButton.setEnabled(false);
                    messageField.setEnabled(false);
                    agregarMensajeSistema("Intentando reconectar...");
                    break;
            }
        });
    }

    /**
     * Añade un mensaje con el prefijo "[Sistema]" al área de chat.
     * Útil para mostrar información sobre el estado de la conexión, etc.
     * Se ejecuta en el EDT.
     * @param mensaje El mensaje a mostrar.
     */
    private static void agregarMensajeSistema(String mensaje) {
        // Asegurarse de que la actualización de la GUI se haga en el Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            chatArea.append("[Sistema] " + mensaje + "\n"); // Añadir mensaje de sistema
            chatArea.setCaretPosition(chatArea.getDocument().getLength()); // Auto-scroll
        });
    }

    /**
     * Obtiene una marca de tiempo local formateada. (NUEVO MÉTODO)
     * @return String con la hora actual [HH:mm:ss]
     */
    private static String getLocalTimestamp() {
        return "[" + localFormatter.format(new Date()) + "]";
    }
}
