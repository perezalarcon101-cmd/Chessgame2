import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class QCUChess extends JFrame {

    // ─── Board & UI ───────────────────────────────────────────────
    private final JButton[][] squares   = new JButton[8][8];
    private final String[][]  board     = new String[8][8];

    // ─── Colors ───────────────────────────────────────────────────
    private static final Color LIGHT      = new Color(240, 217, 181);
    private static final Color DARK       = new Color(181, 136,  99);
    private static final Color SELECTED   = new Color( 20, 200,  80);
    private static final Color LEGAL_DOT  = new Color( 20, 200,  80, 160);
    private static final Color LAST_FROM  = new Color(205, 210,  60, 160);
    private static final Color LAST_TO    = new Color(205, 210,  60, 200);
    private static final Color CHECK_CLR  = new Color(220,  50,  50, 200);

    // ─── Game State ───────────────────────────────────────────────
    private boolean isWhiteTurn  = true;
    private int     selRow = -1, selCol = -1;
    private boolean pieceSelected = false;

    // Castling rights
    private boolean whiteKingMoved  = false, blackKingMoved  = false;
    private boolean whiteRookAMoved = false, blackRookAMoved = false; // a-file (col 0)
    private boolean whiteRookHMoved = false, blackRookHMoved = false; // h-file (col 7)

    // En passant target square (-1 if none)
    private int enPassantRow = -1, enPassantCol = -1;

    // Last move highlight
    private int lastFromRow = -1, lastFromCol = -1, lastToRow = -1, lastToCol = -1;

    // Legal moves for selected piece
    private List<int[]> legalMoves = new ArrayList<>();

    // Status
    private JLabel statusLabel;

    // ─── Constructor ──────────────────────────────────────────────
    public QCUChess() {
        setTitle("QCU Chess");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));

        // --- Status bar ---
        statusLabel = new JLabel("WHITE's Turn", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Serif", Font.BOLD, 22));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(50, 50, 50));
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(statusLabel, BorderLayout.NORTH);

        // --- Board panel ---
        JPanel boardPanel = new JPanel(new GridLayout(8, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
            }
        };
        boardPanel.setPreferredSize(new Dimension(640, 640));

        initializeBoard();
        buildUI(boardPanel);

        add(boardPanel, BorderLayout.CENTER);

        // --- Restart button ---
        JButton restart = new JButton("New Game");
        restart.setFont(new Font("Serif", Font.BOLD, 16));
        restart.addActionListener(e -> restartGame());
        JPanel south = new JPanel();
        south.setBackground(new Color(40, 40, 40));
        south.add(restart);
        add(south, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ─── Board Initialisation ─────────────────────────────────────
    private void initializeBoard() {
        // Black pieces (row 0 = rank 8)
        board[0] = new String[]{"♜","♞","♝","♛","♚","♝","♞","♜"};
        for (int c = 0; c < 8; c++) board[1][c] = "♟";
        for (int r = 2; r < 6; r++) for (int c = 0; c < 8; c++) board[r][c] = "";
        for (int c = 0; c < 8; c++) board[6][c] = "♙";
        board[7] = new String[]{"♖","♘","♗","♕","♔","♗","♘","♖"};
    }

    private void buildUI(JPanel panel) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                ChessSquare btn = new ChessSquare(r, c);
                squares[r][c] = btn;
                final int row = r, col = c;
                btn.addActionListener(e -> handleClick(row, col));
                panel.add(btn);
            }
        }
        refreshAllSquares();
    }

    // ─── Custom Square Button ─────────────────────────────────────
    class ChessSquare extends JButton {
        final int row, col;
        boolean isSelected = false;
        boolean isLegal    = false;
        boolean isLastFrom = false;
        boolean isLastTo   = false;
        boolean isInCheck  = false;

        ChessSquare(int r, int c) {
            this.row = r; this.col = c;
            setFont(new Font("Serif", Font.PLAIN, 52));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Base color
            Color base = ((row + col) % 2 == 0) ? LIGHT : DARK;
            g2.setColor(base);
            g2.fillRect(0, 0, getWidth(), getHeight());

            // Last move highlight
            if (isLastFrom || isLastTo) {
                g2.setColor(LAST_FROM);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }

            // Selected
            if (isSelected) {
                g2.setColor(SELECTED);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }

            // In check (king square)
            if (isInCheck) {
                g2.setColor(CHECK_CLR);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }

            // Legal move dot / capture ring
            if (isLegal) {
                String piece = board[row][col];
                if (piece == null || piece.isEmpty()) {
                    // Dot for empty square
                    g2.setColor(LEGAL_DOT);
                    int diam = getWidth() / 3;
                    g2.fillOval((getWidth() - diam) / 2, (getHeight() - diam) / 2, diam, diam);
                } else {
                    // Ring for capture square
                    g2.setColor(LEGAL_DOT);
                    int thick = 6;
                    g2.setStroke(new BasicStroke(thick));
                    g2.drawOval(thick / 2, thick / 2, getWidth() - thick, getHeight() - thick);
                }
            }

            // Draw piece text
            String piece = board[row][col];
            if (piece != null && !piece.isEmpty()) {
                FontMetrics fm = g2.getFontMetrics(getFont());
                int x = (getWidth()  - fm.stringWidth(piece)) / 2;
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                // Shadow
                g2.setColor(new Color(0, 0, 0, 60));
                g2.setFont(getFont());
                g2.drawString(piece, x + 2, y + 2);
                g2.setColor(Color.BLACK);
                g2.drawString(piece, x, y);
            }
            g2.dispose();
        }
    }

    // ─── Click Handler ────────────────────────────────────────────
    private void handleClick(int r, int c) {
        String clicked = board[r][c];

        if (!pieceSelected) {
            // --- SELECT a piece ---
            if (clicked == null || clicked.isEmpty()) return;
            boolean clickedIsWhite = isWhitePiece(clicked);
            if (isWhiteTurn != clickedIsWhite) {
                flash("Not your turn!");
                return;
            }
            selRow = r; selCol = c;
            pieceSelected = true;
            legalMoves = getLegalMoves(r, c);
            highlightSelection();

        } else {
            // --- MOVE or RE-SELECT ---

            // Re-select own piece
            if (!clicked.isEmpty() && (isWhitePiece(clicked) == isWhiteTurn)) {
                selRow = r; selCol = c;
                legalMoves = getLegalMoves(r, c);
                highlightSelection();
                return;
            }

            // Check if destination is legal
            boolean isLegal = false;
            for (int[] m : legalMoves) {
                if (m[0] == r && m[1] == c) { isLegal = true; break; }
            }

            if (!isLegal) {
                flash("Illegal move!");
                return;
            }

            executeMove(selRow, selCol, r, c);
        }
    }

    // ─── Execute a Move ───────────────────────────────────────────
    private void executeMove(int fr, int fc, int tr, int tc) {
        String piece = board[fr][fc];

        // --- En passant capture ---
        int epRow = enPassantRow, epCol = enPassantCol;
        enPassantRow = -1; enPassantCol = -1; // Reset each move

        if ((piece.equals("♙") || piece.equals("♟")) && tc == epCol && tr == epRow) {
            // Remove captured pawn
            int captureRow = isWhiteTurn ? epRow + 1 : epRow - 1;
            board[captureRow][epCol] = "";
        }

        // --- Set en passant target for double pawn push ---
        if (piece.equals("♙") && fr == 6 && tr == 4) {
            enPassantRow = 5; enPassantCol = fc;
        } else if (piece.equals("♟") && fr == 1 && tr == 3) {
            enPassantRow = 2; enPassantCol = fc;
        }

        // --- Castling ---
        if (piece.equals("♔") && Math.abs(tc - fc) == 2) {
            if (tc == 6) { board[7][5] = board[7][7]; board[7][7] = ""; } // Kingside
            else         { board[7][3] = board[7][0]; board[7][0] = ""; } // Queenside
        }
        if (piece.equals("♚") && Math.abs(tc - fc) == 2) {
            if (tc == 6) { board[0][5] = board[0][7]; board[0][7] = ""; } // Kingside
            else         { board[0][3] = board[0][0]; board[0][0] = ""; } // Queenside
        }

        // --- Update castling rights ---
        if (piece.equals("♔")) whiteKingMoved = true;
        if (piece.equals("♚")) blackKingMoved = true;
        if (fr == 7 && fc == 0) whiteRookAMoved = true;
        if (fr == 7 && fc == 7) whiteRookHMoved = true;
        if (fr == 0 && fc == 0) blackRookAMoved = true;
        if (fr == 0 && fc == 7) blackRookHMoved = true;

        // --- Move piece ---
        board[tr][tc] = piece;
        board[fr][fc] = "";

        // --- Pawn Promotion ---
        if (piece.equals("♙") && tr == 0) board[tr][tc] = promotePawn(true);
        if (piece.equals("♟") && tr == 7) board[tr][tc] = promotePawn(false);

        // --- Switch turn ---
        isWhiteTurn = !isWhiteTurn;
        pieceSelected = false;
        legalMoves.clear();
        lastFromRow = fr; lastFromCol = fc; lastToRow = tr; lastToCol = tc;

        refreshAllSquares();
        updateStatus();
    }

    // ─── Pawn Promotion Dialog ────────────────────────────────────
    private String promotePawn(boolean white) {
        String[] options = white
            ? new String[]{"♕ Queen","♖ Rook","♗ Bishop","♘ Knight"}
            : new String[]{"♛ Queen","♜ Rook","♝ Bishop","♞ Knight"};
        String[] values = white
            ? new String[]{"♕","♖","♗","♘"}
            : new String[]{"♛","♜","♝","♞"};
        int choice = JOptionPane.showOptionDialog(this,
            "Choose promotion piece:", "Pawn Promotion",
            JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
            null, options, options[0]);
        return values[Math.max(0, choice)];
    }

    // ─── Legal Move Generation ────────────────────────────────────
    /** Returns all legal moves for piece at (r,c) — filtered so king is never in check. */
    private List<int[]> getLegalMoves(int r, int c) {
        List<int[]> pseudo = getPseudoLegal(r, c);
        List<int[]> legal  = new ArrayList<>();
        String piece = board[r][c];
        for (int[] m : pseudo) {
            if (!moveLeavesKingInCheck(r, c, m[0], m[1])) {
                legal.add(m);
            }
        }
        return legal;
    }

    /** Check if making move (fr,fc)->(tr,tc) leaves own king in check. */
    private boolean moveLeavesKingInCheck(int fr, int fc, int tr, int tc) {
        // Simulate move
        String[][] backup  = copyBoard();
        String moved = board[fr][fc];

        // En passant simulation
        if ((moved.equals("♙") || moved.equals("♟")) && tc == enPassantCol && tr == enPassantRow) {
            int capRow = moved.equals("♙") ? tr + 1 : tr - 1;
            board[capRow][tc] = "";
        }
        // Castling simulation
        if (moved.equals("♔") && Math.abs(tc - fc) == 2) {
            if (tc == 6) { board[7][5] = board[7][7]; board[7][7] = ""; }
            else         { board[7][3] = board[7][0]; board[7][0] = ""; }
        }
        if (moved.equals("♚") && Math.abs(tc - fc) == 2) {
            if (tc == 6) { board[0][5] = board[0][7]; board[0][7] = ""; }
            else         { board[0][3] = board[0][0]; board[0][0] = ""; }
        }

        board[tr][tc] = moved;
        board[fr][fc] = "";

        boolean white = isWhitePiece(moved);
        boolean inCheck = isKingInCheck(white);

        // Restore board
        restoreBoard(backup);
        return inCheck;
    }

    /** Pseudo-legal moves (ignoring whether king ends up in check). */
    private List<int[]> getPseudoLegal(int r, int c) {
        String piece = board[r][c];
        List<int[]> moves = new ArrayList<>();
        if (piece == null || piece.isEmpty()) return moves;

        switch (piece) {
            case "♙": pawnMoves(r, c, true,  moves); break;
            case "♟": pawnMoves(r, c, false, moves); break;
            case "♖": case "♜": slidingMoves(r, c, new int[][]{{1,0},{-1,0},{0,1},{0,-1}}, moves); break;
            case "♗": case "♝": slidingMoves(r, c, new int[][]{{1,1},{1,-1},{-1,1},{-1,-1}}, moves); break;
            case "♕": case "♛": slidingMoves(r, c, new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}}, moves); break;
            case "♘": case "♞": knightMoves(r, c, moves); break;
            case "♔": kingMoves(r, c, true,  moves); break;
            case "♚": kingMoves(r, c, false, moves); break;
        }
        return moves;
    }

    private void pawnMoves(int r, int c, boolean white, List<int[]> moves) {
        int dir = white ? -1 : 1;
        int startRow = white ? 6 : 1;

        // Forward one
        int nr = r + dir;
        if (inBounds(nr, c) && board[nr][c].isEmpty()) {
            moves.add(new int[]{nr, c});
            // Forward two from start
            if (r == startRow && board[r + 2 * dir][c].isEmpty()) {
                moves.add(new int[]{r + 2 * dir, c});
            }
        }
        // Captures
        for (int dc : new int[]{-1, 1}) {
            int nc = c + dc;
            if (inBounds(nr, nc)) {
                String target = board[nr][nc];
                boolean isEnemy = !target.isEmpty() && (isWhitePiece(target) != white);
                boolean isEP    = nr == enPassantRow && nc == enPassantCol;
                if (isEnemy || isEP) moves.add(new int[]{nr, nc});
            }
        }
    }

    private void slidingMoves(int r, int c, int[][] dirs, List<int[]> moves) {
        boolean white = isWhitePiece(board[r][c]);
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            while (inBounds(nr, nc)) {
                String t = board[nr][nc];
                if (t.isEmpty()) {
                    moves.add(new int[]{nr, nc});
                } else {
                    if (isWhitePiece(t) != white) moves.add(new int[]{nr, nc}); // capture
                    break;
                }
                nr += d[0]; nc += d[1];
            }
        }
    }

    private void knightMoves(int r, int c, List<int[]> moves) {
        boolean white = isWhitePiece(board[r][c]);
        int[][] jumps = {{-2,-1},{-2,1},{2,-1},{2,1},{-1,-2},{-1,2},{1,-2},{1,2}};
        for (int[] j : jumps) {
            int nr = r + j[0], nc = c + j[1];
            if (inBounds(nr, nc)) {
                String t = board[nr][nc];
                if (t.isEmpty() || isWhitePiece(t) != white) moves.add(new int[]{nr, nc});
            }
        }
    }

    private void kingMoves(int r, int c, boolean white, List<int[]> moves) {
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = r + dr, nc = c + dc;
                if (inBounds(nr, nc)) {
                    String t = board[nr][nc];
                    if (t.isEmpty() || isWhitePiece(t) != white) moves.add(new int[]{nr, nc});
                }
            }
        }
        // Castling
        if (white && !whiteKingMoved && !isKingInCheck(true)) {
            // Kingside
            if (!whiteRookHMoved && board[7][5].isEmpty() && board[7][6].isEmpty()
                    && !squareAttacked(7, 5, false) && !squareAttacked(7, 6, false))
                moves.add(new int[]{7, 6});
            // Queenside
            if (!whiteRookAMoved && board[7][1].isEmpty() && board[7][2].isEmpty() && board[7][3].isEmpty()
                    && !squareAttacked(7, 3, false) && !squareAttacked(7, 2, false))
                moves.add(new int[]{7, 2});
        }
        if (!white && !blackKingMoved && !isKingInCheck(false)) {
            if (!blackRookHMoved && board[0][5].isEmpty() && board[0][6].isEmpty()
                    && !squareAttacked(0, 5, true) && !squareAttacked(0, 6, true))
                moves.add(new int[]{0, 6});
            if (!blackRookAMoved && board[0][1].isEmpty() && board[0][2].isEmpty() && board[0][3].isEmpty()
                    && !squareAttacked(0, 3, true) && !squareAttacked(0, 2, true))
                moves.add(new int[]{0, 2});
        }
    }

    // ─── Check / Attack Detection ─────────────────────────────────
    private boolean isKingInCheck(boolean white) {
        // Find king
        int kr = -1, kc = -1;
        String king = white ? "♔" : "♚";
        outer:
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                if (king.equals(board[r][c])) { kr = r; kc = c; break outer; }
        if (kr < 0) return false;
        return squareAttacked(kr, kc, !white);
    }

    /** Is square (r,c) attacked by any piece belonging to 'byWhite'? */
    private boolean squareAttacked(int r, int c, boolean byWhite) {
        // Check all enemy pieces
        for (int er = 0; er < 8; er++) {
            for (int ec = 0; ec < 8; ec++) {
                String p = board[er][ec];
                if (p.isEmpty()) continue;
                if (isWhitePiece(p) != byWhite) continue;
                if (canAttack(er, ec, r, c)) return true;
            }
        }
        return false;
    }

    /** Can piece at (fr,fc) attack square (tr,tc)? (pseudo, no recursion) */
    private boolean canAttack(int fr, int fc, int tr, int tc) {
        String piece = board[fr][fc];
        boolean white = isWhitePiece(piece);
        int dr = tr - fr, dc = tc - fc;

        switch (piece) {
            case "♙": return dr == -1 && Math.abs(dc) == 1;
            case "♟": return dr ==  1 && Math.abs(dc) == 1;
            case "♘": case "♞":
                return (Math.abs(dr) == 2 && Math.abs(dc) == 1) || (Math.abs(dr) == 1 && Math.abs(dc) == 2);
            case "♔": case "♚":
                return Math.abs(dr) <= 1 && Math.abs(dc) <= 1;
            case "♖": case "♜":
                if (dr != 0 && dc != 0) return false;
                return noBlockers(fr, fc, tr, tc);
            case "♗": case "♝":
                if (Math.abs(dr) != Math.abs(dc)) return false;
                return noBlockers(fr, fc, tr, tc);
            case "♕": case "♛":
                if (dr != 0 && dc != 0 && Math.abs(dr) != Math.abs(dc)) return false;
                return noBlockers(fr, fc, tr, tc);
        }
        return false;
    }

    private boolean noBlockers(int fr, int fc, int tr, int tc) {
        int sr = Integer.signum(tr - fr), sc = Integer.signum(tc - fc);
        int r = fr + sr, c = fc + sc;
        while (r != tr || c != tc) {
            if (!board[r][c].isEmpty()) return false;
            r += sr; c += sc;
        }
        return true;
    }

    // ─── Game State Checks ─────────────────────────────────────────
    private boolean hasAnyLegalMove(boolean white) {
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++) {
                String p = board[r][c];
                if (!p.isEmpty() && isWhitePiece(p) == white && !getLegalMoves(r, c).isEmpty())
                    return true;
            }
        return false;
    }

    private void updateStatus() {
        boolean inCheck = isKingInCheck(isWhiteTurn);
        boolean hasMove = hasAnyLegalMove(isWhiteTurn);
        String turn = isWhiteTurn ? "WHITE" : "BLACK";

        if (!hasMove) {
            if (inCheck) {
                String winner = isWhiteTurn ? "BLACK" : "WHITE";
                statusLabel.setText("CHECKMATE! " + winner + " wins! 🎉");
                statusLabel.setBackground(new Color(180, 30, 30));
                JOptionPane.showMessageDialog(this,
                    "Checkmate! " + winner + " wins!", "Game Over", JOptionPane.INFORMATION_MESSAGE);
            } else {
                statusLabel.setText("STALEMATE — Draw!");
                statusLabel.setBackground(new Color(80, 80, 120));
                JOptionPane.showMessageDialog(this,
                    "Stalemate! It's a draw.", "Game Over", JOptionPane.INFORMATION_MESSAGE);
            }
        } else if (inCheck) {
            statusLabel.setText(turn + "'s Turn — ⚠ CHECK!");
            statusLabel.setBackground(new Color(180, 80, 20));
        } else {
            statusLabel.setText(turn + "'s Turn");
            statusLabel.setBackground(new Color(50, 50, 50));
        }

        // Highlight king if in check
        highlightCheck(inCheck);
    }

    // ─── Visual Helpers ───────────────────────────────────────────
    private void highlightSelection() {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                ChessSquare sq = (ChessSquare) squares[r][c];
                sq.isSelected = (r == selRow && c == selCol);
                sq.isLegal    = false;
                sq.isLastFrom = (r == lastFromRow && c == lastFromCol);
                sq.isLastTo   = (r == lastToRow   && c == lastToCol);
                sq.isInCheck  = false;
                sq.repaint();
            }
        }
        for (int[] m : legalMoves) {
            ChessSquare sq = (ChessSquare) squares[m[0]][m[1]];
            sq.isLegal = true;
            sq.repaint();
        }
    }

    private void refreshAllSquares() {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                ChessSquare sq = (ChessSquare) squares[r][c];
                sq.isSelected = false;
                sq.isLegal    = false;
                sq.isLastFrom = (r == lastFromRow && c == lastFromCol);
                sq.isLastTo   = (r == lastToRow   && c == lastToCol);
                sq.isInCheck  = false;
                sq.repaint();
            }
        }
    }

    private void highlightCheck(boolean inCheck) {
        if (!inCheck) return;
        String king = isWhiteTurn ? "♔" : "♚";
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                if (king.equals(board[r][c])) {
                    ((ChessSquare) squares[r][c]).isInCheck = true;
                    squares[r][c].repaint();
                }
    }

    private void flash(String msg) {
        String prev = statusLabel.getText();
        Color prevBg = statusLabel.getBackground();
        statusLabel.setText(msg);
        statusLabel.setBackground(new Color(180, 30, 30));
        Timer t = new Timer(1200, e -> {
            statusLabel.setText(prev);
            statusLabel.setBackground(prevBg);
        });
        t.setRepeats(false);
        t.start();
    }

    // ─── Utilities ────────────────────────────────────────────────
    private boolean isWhitePiece(String p) {
        return p.equals("♙")||p.equals("♖")||p.equals("♘")||p.equals("♗")||p.equals("♕")||p.equals("♔");
    }

    private boolean inBounds(int r, int c) { return r >= 0 && r < 8 && c >= 0 && c < 8; }

    private String[][] copyBoard() {
        String[][] b = new String[8][8];
        for (int r = 0; r < 8; r++) b[r] = board[r].clone();
        return b;
    }

    private void restoreBoard(String[][] b) {
        for (int r = 0; r < 8; r++) board[r] = b[r].clone();
    }

    // ─── Restart ──────────────────────────────────────────────────
    private void restartGame() {
        isWhiteTurn = true; selRow = -1; selCol = -1; pieceSelected = false;
        whiteKingMoved = blackKingMoved = false;
        whiteRookAMoved = whiteRookHMoved = false;
        blackRookAMoved = blackRookHMoved = false;
        enPassantRow = enPassantCol = -1;
        lastFromRow = lastFromCol = lastToRow = lastToCol = -1;
        legalMoves.clear();
        initializeBoard();
        refreshAllSquares();
        statusLabel.setText("WHITE's Turn");
        statusLabel.setBackground(new Color(50, 50, 50));
    }

    // ─── Main ─────────────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(QCUChess::new);
    }
}