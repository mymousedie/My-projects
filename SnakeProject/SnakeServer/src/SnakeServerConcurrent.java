import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class SnakeServerConcurrent {
    //game configs
    private static final int TILE_SIZE = 25;
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    //map to keep track of all players and their game state
    private final Map<ClientHandler, PlayerData> players = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private int appleX, appleY;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java SnakeServerConcurrent <port>");
            System.exit(1);
        }

        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number. Please provide a valid integer.");
            System.exit(1);
            return;
        }
        //start the server
        new SnakeServerConcurrent().startServer(port);
    }
    //start server on specified port
    private void startServer(int port) {
        spawnApple();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                players.put(clientHandler, new PlayerData());
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //generate an apple at a random location
    private synchronized void spawnApple() {
        appleX = random.nextInt(WIDTH / TILE_SIZE) * TILE_SIZE;
        appleY = random.nextInt(HEIGHT / TILE_SIZE) * TILE_SIZE;
    }
    //Check if the snake has eaten the apple
    private synchronized void checkApple(PlayerData playerData) {
        if (playerData.snakeX[0] == appleX && playerData.snakeY[0] == appleY) {
            playerData.snakeLength++;
            playerData.score++; // Increment score when food is eaten
            spawnApple();
        }
    }
    //Check if snake collided with itself or the walls
    private synchronized boolean checkCollision(PlayerData playerData) {
        // Check collision with the snake body
        for (int i = playerData.snakeLength - 1; i > 0; i--) {
            if (playerData.snakeX[0] == playerData.snakeX[i] &&
                playerData.snakeY[0] == playerData.snakeY[i]) {
                return true;
            }
        }
        // Check collision with walls
        return playerData.snakeX[0] < 0 || playerData.snakeX[0] >= WIDTH ||
               playerData.snakeY[0] < 0 || playerData.snakeY[0] >= HEIGHT;
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter output;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter output = new PrintWriter(socket.getOutputStream(), true)
            ) {
                this.output = output;
                System.out.println("Client connected: " + socket);
                //initialize player data
                PlayerData playerData = players.get(this);
                playerData.initSnake();

                // Thread to send game state updates
                new Thread(() -> {
                    try {
                        while (true) {
                            synchronized (playerData) {
                                sendGameState(playerData);
                            }
                            Thread.sleep(100); // Update game state every 100 ms
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();

                // Handle client commands
                String command;
                while ((command = input.readLine()) != null) {
                    synchronized (playerData) {
                        playerData.updateDirection(command);
                        playerData.move();
                        checkApple(playerData);

                        if (checkCollision(playerData)) {
                            output.println("GAME_OVER");
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Connection error: " + e.getMessage());
            } finally {
                players.remove(this);
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("Client disconnected: " + socket);
            }
        }
        //Send the current game state to the client
        private void sendGameState(PlayerData playerData) {
            StringBuilder state = new StringBuilder();
            state.append("snake,").append(playerData.snakeLength).append(",");
            for (int i = 0; i < playerData.snakeLength; i++) {
                state.append(playerData.snakeX[i]).append(",").append(playerData.snakeY[i]).append(",");
            }
            state.append("apple,").append(appleX).append(",").append(appleY).append(",");
            state.append("score,").append(playerData.score); // Send the updated score
            output.println(state.toString());
        }
    }
    //Stores player game specific data
    private static class PlayerData {
        private static final int ALL_TILES = (WIDTH * HEIGHT) / (TILE_SIZE * TILE_SIZE);

        private final int[] snakeX = new int[ALL_TILES];
        private final int[] snakeY = new int[ALL_TILES];
        private int snakeLength;
        private char direction = 'R'; // R = Right, L = Left, U = Up, D = Down
        private int score = 0; // Track the player's score

        //Initialize the snake's starting position
        public void initSnake() {
            snakeLength = 1; // Start with a single block
            snakeX[0] = WIDTH / 2;
            snakeY[0] = HEIGHT / 2;
        }
        //Update snake's direction base on the client's command
        public void updateDirection(String command) {
            switch (command) {
                case "UP" -> {
                    if (direction != 'D') direction = 'U';
                }
                case "DOWN" -> {
                    if (direction != 'U') direction = 'D';
                }
                case "LEFT" -> {
                    if (direction != 'R') direction = 'L';
                }
                case "RIGHT" -> {
                    if (direction != 'L') direction = 'R';
                }
            }
        }
        //Move the snake in the current direction
        public void move() {
            for (int i = snakeLength; i > 0; i--) {
                snakeX[i] = snakeX[i - 1];
                snakeY[i] = snakeY[i - 1];
            }
            switch (direction) {
                case 'U' -> snakeY[0] -= TILE_SIZE;
                case 'D' -> snakeY[0] += TILE_SIZE;
                case 'L' -> snakeX[0] -= TILE_SIZE;
                case 'R' -> snakeX[0] += TILE_SIZE;
            }
        }
    }
}
