package com.nashss.se.chessplayerservice.activity;

import com.nashss.se.chessplayerservice.activity.request.GetNextMoveRequest;
import com.nashss.se.chessplayerservice.activity.response.GetNextMoveResponse;
import com.nashss.se.chessplayerservice.dynamodb.dao.GameDao;
import com.nashss.se.chessplayerservice.dynamodb.dao.UserDao;
import com.nashss.se.chessplayerservice.dynamodb.models.Game;
import com.nashss.se.chessplayerservice.dynamodb.models.User;
import com.nashss.se.chessplayerservice.engine.Stockfish;
import com.nashss.se.chessplayerservice.exceptions.InvalidRequestException;
import com.nashss.se.chessplayerservice.exceptions.StockfishException;
import com.nashss.se.chessplayerservice.utils.ChessUtils;

import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;

/**
 * Implementation of the GetNextMoveActivity for the ChessPlayerClient's GetNextMove API.
 *
 * This API allows the user to submit a move, updates the game in the database, and returns the updated game and move.
 * May also update the User in the database if the game ends.
 */
public class GetNextMoveActivity {

    private final GameDao gameDao;
    private final UserDao userDao;
    private final Stockfish stockfish;


    /**
     * Instantiates a new GetNextMoveActivity object.
     *
     * @param gameDao DAO to access the games table.
     * @param userDao DAO to access the users table.
     * @param stockfish Stockfish object to interface with the chess engine
     */
    @Inject
    public GetNextMoveActivity(GameDao gameDao, UserDao userDao, Stockfish stockfish) {
        this.gameDao = gameDao;
        this.userDao = userDao;
        this.stockfish = stockfish;
    }

    /**
     * This method handles the incoming request by initializing the engine, checking if the move is valid, and
     * checking if the game has ended.
     * <p>
     * It then returns the updated game object, and the engine move.
     * <p>
     * If the move is not valid, this should throw an InvalidRequestException.
     * <p>
     * If the game does not exist, this should throw an InvalidRequestException.
     *
     * @param request request object containing the gameId and the player's move
     * @return GetNextMoveResponse object containing the updated {@link Game} and the engine move
     */
    public GetNextMoveResponse handleRequest(GetNextMoveRequest request) {
        if (request.getMove() == null || request.getGameId() == null) {
            throw new InvalidRequestException(
                    String.format("Missing one or more required fields: move={%s}, gameId={%s}",
                    request.getMove(), request.getGameId()));
        }

        // Get the game from the database, then set the new move
        Game game = gameDao.load(request.getGameId());
        if (game == null) {
            throw new InvalidRequestException("There is no game with that ID");
        }
        if (game.getActive().equals("false")) {
            throw new InvalidRequestException("That game is inactive");
        }

        // Initialize stockfish
        if (!stockfish.startEngine()) {
            throw new StockfishException("Engine failed to start");
        }
        stockfish.getOutput(3);
        // Check if the submitted move is legal
        String[] legalMoves = game.getValidMoves().split(",");
        if (Arrays.stream(legalMoves).noneMatch(move -> move.equals(request.getMove()))) {
            throw new InvalidRequestException("That is not a legal move: " + Arrays.toString(legalMoves));
        }
        game.setNotation(game.getNotation() + " moves " + request.getMove());
        String moves = game.getMoves() == null ? request.getMove() : game.getMoves() + " " + request.getMove();
        game.setMoves(moves);

        // Check if the player move ends the game
        // This method also updates the game's moves to be fen notation
        gameOverChecker(game);

        stockfish.sendCommand("uci");
        stockfish.sendCommand("setoption name skill level value " + game.getBotDifficulty());
        stockfish.getOutput(10);
        String engineMove = null;
        List<String> validMoves = null;
        if (game.getWinner() == null) {
            // If the player move did not end the game, make an engine move
            engineMove = stockfish.getBestMove(String.format("fen %s", game.getNotation()), 500).trim();
            game.setNotation(game.getNotation() + " moves " + engineMove);
            game.setMoves(game.getMoves() == null ? engineMove : game.getMoves() + " " + engineMove);
            // Check if the engine move ends the game
            gameOverChecker(game);
            StringBuilder sb = new StringBuilder();
            for (String move : stockfish.getLegalMoves(game.getNotation())) {
                sb.append(move);
                sb.append(",");
            }
            game.setValidMoves(sb.toString());
        }

        stockfish.stopEngine();


        // If the game is over, edit the user(s) rating scores
        // If expected Score is above .5, you are expected to either win or draw
        if (game.getWinner() != null) {
            game.setValidMoves("");

            User white;
            User black;
            String winner = game.getWinner();
            // If multiplayer
            if (game.getBotDifficulty() == null) {
                white = userDao.load(game.getWhitePlayerUsername());
                black = userDao.load(game.getBlackPlayerUsername());
            } else if (game.getWhitePlayerUsername() != null) {
                // If white vs bot
                white = userDao.load(game.getWhitePlayerUsername());
                black = new User();
                black.setRating(ChessUtils.botDifficultyToRating(game.getBotDifficulty()));
            } else {
                // If black vs bot
                black = userDao.load(game.getBlackPlayerUsername());
                white = new User();
                white.setRating(ChessUtils.botDifficultyToRating(game.getBotDifficulty()));
            }

            white.setRating(white.getRating() +
                    (int) ChessUtils.calculateRatingForWhite(white.getRating(), black.getRating(), winner));
            black.setRating(black.getRating() +
                    (int) ChessUtils.calculateRatingForBlack(white.getRating(), black.getRating(), winner));
            if (white.getUsername() != null) {
                userDao.saveUser(white);
            }
            if (black.getUsername() != null) {
                userDao.saveUser(black);
            }
        }

        // Save the new notation to the database before returning
        gameDao.save(game);

        return GetNextMoveResponse.builder()
                .withGame(game)
                .withMove(engineMove)
                .build();
    }

    private void gameOverChecker(Game game) {
        List<String> legalMoves = stockfish.getLegalMoves("fen " + game.getNotation());

        // Initialize inCheck to false
        boolean inCheck = false;
        int fiftyMoveRule = 0;
        String pieces = "";
        stockfish.sendCommand("d");
        String[] dump = stockfish.getOutput(10).split("\n");
        for (String line : dump) {
            // Get new simplified notation
            // 5th field of fen string is 50 move rule
            if (line.startsWith("Fen: ")) {
                String fen = line.split("Fen: ")[1];
                game.setNotation(fen);
                pieces = fen.split(" ")[0];
                fiftyMoveRule = Integer.parseInt(fen.split(" ")[4]);
            }
            if (line.startsWith("Checkers: ")) {
                // See if position is in check
                String tmp = line.replace("Checkers: ", "").trim();
                if (!tmp.isBlank()) {
                    inCheck = true;
                }
            }
        }

        // Logic to determine if enough material is present
        List<Character> pieceList = List.of('p', 'P', 'q', 'Q', 'r', 'R');
        StringBuilder whitePieces = new StringBuilder();
        StringBuilder blackPieces = new StringBuilder();
        boolean enoughMaterial = false;
        // loop to collect all the relevant pieces
        for (Character c : pieces.toCharArray()) {
            // ignore fen notation breaks for rows
            if (c.equals('/') || c.toString().equalsIgnoreCase("k")) {
                continue;
            }
            // if a pawn, queen, or rook is found, there is enough material
            if (pieceList.contains(c)) {
                enoughMaterial = true;
                break;
            }
            if (Character.isLowerCase(c)) {
                blackPieces.append(c);
                if (blackPieces.length() > 1) {
                    enoughMaterial = true;
                    break;
                }
            } else {
                whitePieces.append(c);
                if (whitePieces.length() > 1) {
                    enoughMaterial = true;
                    break;
                }
            }
        }

        // if in check and no valid moves, game is over by checkmate
        if (inCheck && legalMoves.isEmpty()) {
            game.setWinner(game.getNotation().split(" ")[1].equals("w") ? "black" : "white");
            game.setActive("false");
        } else if (legalMoves.isEmpty()) {
            // if no valid moves, but you are not in check, game is over by stalemate
            game.setWinner("draw");
            game.setActive("false");
        } else if (fiftyMoveRule >= 100) {
            // fifty move rule is a draw condition;
            // if there have been no pawn captures or advances in 50 moves, the game ends in a draw
            // fen string tracks this for us
            game.setWinner("draw");
            game.setActive("false");
        } else if (!enoughMaterial) {
            // draw by not enough material
            game.setWinner("draw");
            game.setActive("false");
        }
    }
}
