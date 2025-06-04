import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.*;
import java.net.Socket;
import javax.swing.*;

public class SnakeClient extends JFrame {
    private static final int TILE_SIZE = 25;
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    private String serverAddress;
    private int serverPort;
    private Socket socket;
    private PrintWriter output;

    private int[] x = new int[100];
    private int[] y = new int[100];
    private int snakeLength;
    private int appleX, appleY;
    private int score = 0;
    private boolean gameOver = false;

    private String currentDirection = null; // No movement initially
    private Timer movementTimer;

    private GamePanel gamePanel;

    public SnakeClient(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;

        setTitle("Snake Game - Client");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);

        // Create and set up the content pane
        gamePanel = new GamePanel();
        gamePanel.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        gamePanel.setBackground(Color.BLACK);
        gamePanel.setFocusable(true);
        gamePanel.requestFocusInWindow();
        add(gamePanel);
        pack();
        setLocationRelativeTo(null);

        initializeGameState();

        // Add a key listener to capture arrow key presses
        gamePanel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (output != null) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_UP -> {
                            if (!"DOWN".equals(currentDirection)) currentDirection = "UP";
                        }
                        case KeyEvent.VK_DOWN -> {
                            if (!"UP".equals(currentDirection)) currentDirection = "DOWN";
                        }
                        case KeyEvent.VK_LEFT -> {
                            if (!"RIGHT".equals(currentDirection)) currentDirection = "LEFT";
                        }
                        case KeyEvent.VK_RIGHT -> {
                            if (!"LEFT".equals(currentDirection)) currentDirection = "RIGHT";
                        }
                    }
                }
            }
        });

        setVisible(true);
        connectToServer();
        startMovementTimer();
    }

    private void initializeGameState() {
        snakeLength = 1; // Start with one part
        x[0] = WIDTH / 2;
        y[0] = HEIGHT / 2;
        appleX = TILE_SIZE * 3;
        appleY = TILE_SIZE * 3;
        score = 0;
        gameOver = false;
        currentDirection = null; // No movement initially
    }

    private void connectToServer() {
        try {
            socket = new Socket(serverAddress, serverPort);
            output = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            new Thread(() -> {
                try {
                    String gameState;
                    while ((gameState = input.readLine()) != null) {
                        if (gameState.equals("GAME_OVER")) {
                            gameOver = true;
                            gamePanel.repaint();
                            return;
                        }
                        parseGameState(gameState);
                        gamePanel.repaint();
                    }
                } catch (IOException e) {
                    System.err.println("Connection to the server was lost.");
                } finally {
                    cleanup();
                }
            }).start();

        } catch (IOException e) {
            System.err.println("Unable to connect to the server. Please ensure the server is running.");
            e.printStackTrace();
        }
    }

    private void cleanup() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //Updates game state
    private synchronized void parseGameState(String gameState) {
        try {
            String[] tokens = gameState.split(",");
            if (!tokens[0].equals("snake")) {
                throw new IllegalArgumentException("Invalid game state header.");
            }
    
            snakeLength = Integer.parseInt(tokens[1]);
            for (int i = 0; i < snakeLength; i++) {
                x[i] = Integer.parseInt(tokens[2 + i * 2]);
                y[i] = Integer.parseInt(tokens[3 + i * 2]);
            }
    
            int appleIndex = 2 + snakeLength * 2;
            if (!tokens[appleIndex].equals("apple")) {
                throw new IllegalArgumentException("Missing apple keyword.");
            }
            appleX = Integer.parseInt(tokens[appleIndex + 1]);
            appleY = Integer.parseInt(tokens[appleIndex + 2]);
    
            int scoreIndex = appleIndex + 3;
            if (tokens[scoreIndex].equals("score")) {
                score = Integer.parseInt(tokens[scoreIndex + 1]);
            }
        } catch (Exception e) {
            System.err.println("Error parsing game state: " + gameState);
            e.printStackTrace();
        }
    }
    

    private void startMovementTimer() {
        movementTimer = new Timer(100, e -> {
            if (output != null && currentDirection != null) {
                output.println(currentDirection);
            }
        });
        movementTimer.start();
    }

    private class GamePanel extends JPanel {
        public GamePanel() {
            setDoubleBuffered(true);
        }
    
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            // Draw the score or "GAME OVER" text in the score area
            g.setColor(Color.RED);
            g.setFont(new Font("Arial", Font.BOLD, 18));
            if (gameOver) {
               g.drawString("GAME OVER", 10, 20); //Display "GAME OVER" in the score area
            } else{
                g.drawString("Score: " + score, 10, 20); //Display score when game is running
            }
    
            // Draw the score
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.PLAIN, 18));
            g.drawString("Score: " + score, 10, 20);
    
            // Draw the apple
            g.setColor(Color.RED);
            g.fillRect(appleX, appleY, TILE_SIZE, TILE_SIZE);
    
            // Draw the snake
            g.setColor(Color.GREEN);
            for (int i = 0; i < snakeLength; i++) {
                g.fillRect(x[i], y[i], TILE_SIZE, TILE_SIZE);
            }
        }
    }
    
    
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java SnakeClient <server_address> <port>");
            System.exit(1);
        }

        String serverAddress = args[0];
        int serverPort;

        try {
            serverPort = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number. Please provide a valid integer.");
            System.exit(1);
            return;
        }

        SwingUtilities.invokeLater(() -> new SnakeClient(serverAddress, serverPort));
    }
}








