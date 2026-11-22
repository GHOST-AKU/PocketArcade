package com.ghostaku.arcade;

import java.util.*;

/** Platform-independent arcade rules. The view owns input and presentation. */
public final class Games {
  public static final class Blocks {
    public int[][] board = new int[20][10];
    static final int[][][] SHAPES = {
      {{0, 1}, {1, 1}, {2, 1}, {3, 1}},
      {{1, 0}, {2, 0}, {1, 1}, {2, 1}},
      {{1, 0}, {0, 1}, {1, 1}, {2, 1}},
      {{1, 0}, {2, 0}, {0, 1}, {1, 1}},
      {{0, 0}, {1, 0}, {1, 1}, {2, 1}},
      {{0, 0}, {0, 1}, {1, 1}, {2, 1}},
      {{2, 0}, {0, 1}, {1, 1}, {2, 1}}
    };
    public final boolean free;
    public int[][] piece;
    public int x, y, type, next, score, lines;
    public boolean over, blocked;
    private int[][] undoBoard;
    private int undoType, undoX, undoY, undoScore, undoLines;
    private int[][] undoPiece;
    final Random rng = new Random();
    final ArrayList<Integer> bag = new ArrayList<>();

    int take() {
      if (bag.isEmpty()) {
        for (int i = 0; i < 7; i++) bag.add(i);
        Collections.shuffle(bag, rng);
      }
      return bag.remove(0);
    }

    public Blocks() {
      this(false);
    }

    public Blocks(boolean free) {
      this.free = free;
      next = free ? 2 : take();
      spawn();
    }

    private static int[][] copy(int[][] source) {
      int[][] result = new int[source.length][];
      for (int i = 0; i < source.length; i++) result[i] = source[i].clone();
      return result;
    }

    void spawn() {
      placeAtTop(next);
      if (!free) {
        next = take();
        over = blocked;
      }
    }

    private void placeAtTop(int t) {
      type = t;
      piece = copy(SHAPES[t]);
      x = 3;
      y = 0;
      blocked = !fits(piece, x, y);
    }

    public void choose(int t) {
      if (free && t >= 0 && t < 7) {
        next = t;
        placeAtTop(t);
      }
    }

    boolean fits(int[][] p, int xx, int yy) {
      for (int[] c : p) {
        int a = xx + c[0], b = yy + c[1];
        if (a < 0 || a >= 10 || b < 0 || b >= 20 || board[b][a] != 0) return false;
      }
      return true;
    }

    public boolean move(int dx, int dy) {
      if (over || !fits(piece, x + dx, y + dy)) return false;
      x += dx;
      y += dy;
      blocked = false;
      return true;
    }

    public boolean rotate() {
      if (over || type == 1) return false;
      int[][] p = new int[4][2];
      int size = type == 0 ? 4 : 3;
      for (int i = 0; i < 4; i++) {
        p[i][0] = size - 1 - piece[i][1];
        p[i][1] = piece[i][0];
      }
      for (int dy : new int[] {0, -1, -2})
        for (int dx : new int[] {0, -1, 1, -2, 2})
          if (fits(p, x + dx, y + dy)) {
            piece = p;
            x += dx;
            y += dy;
            blocked = false;
            return true;
          }
      return false;
    }

    public int ghost() {
      int yy = y;
      while (fits(piece, x, yy + 1)) yy++;
      return yy;
    }

    public boolean drop() {
      if (over || blocked) return false;
      if (free) {
        undoBoard = copy(board);
        undoPiece = copy(piece);
        undoType = type;
        undoScore = score;
        undoLines = lines;
        undoX = x;
        undoY = y;
      }
      int yy = ghost();
      score += (yy - y) * 2;
      y = yy;
      lock();
      return true;
    }

    public boolean undo() {
      if (!free || undoBoard == null) return false;
      board = undoBoard;
      undoBoard = null;
      piece = undoPiece;
      type = next = undoType;
      x = undoX;
      y = undoY;
      score = undoScore;
      lines = undoLines;
      blocked = false;
      return true;
    }

    public void tick() {
      if (!free && !over && !move(0, 1)) lock();
    }

    void lock() {
      for (int[] c : piece) board[y + c[1]][x + c[0]] = type + 1;
      int n = 0;
      for (int row = 19; row >= 0; row--) {
        boolean full = true;
        for (int a : board[row])
          if (a == 0) {
            full = false;
            break;
          }
        if (full) {
          for (int j = row; j > 0; j--) board[j] = board[j - 1];
          board[0] = new int[10];
          n++;
          row++;
        }
      }
      score += new int[] {0, 100, 300, 500, 800}[n] * (1 + lines / 10);
      lines += n;
      spawn();
    }
  }

  public static final class Match {
    public static final int STEPS = 0, TIMED = 1, ENDLESS = 2;
    public static final int HINT = 0, BOMB = 1, SHUFFLE = 2;
    public final int mode;
    public int[][] board = new int[8][8];
    public int score, moves = 30, lastChain;
    public boolean over;
    public double seconds = 90;
    public final int[] items;
    final Random rng = new Random();

    public Match() {
      this(STEPS, true);
    }

    public Match(int mode, boolean tools) {
      this.mode = mode;
      items = tools ? new int[] {3, 2, 2} : new int[3];
      shuffle();
    }

    public void tick(double dt) {
      if (mode == TIMED && !over) {
        seconds = Math.max(0, seconds - dt);
        over = seconds == 0;
      }
    }

    void shuffle() {
      do {
        for (int y = 0; y < 8; y++)
          for (int x = 0; x < 8; x++) {
            int v;
            do {
              v = 1 + rng.nextInt(6);
            } while ((x > 1 && board[y][x - 1] == v && board[y][x - 2] == v)
                || (y > 1 && board[y - 1][x] == v && board[y - 2][x] == v));
            board[y][x] = v;
          }
      } while (!hasMove());
    }

    void swap(int x, int y, int a, int b) {
      int v = board[y][x];
      board[y][x] = board[b][a];
      board[b][a] = v;
    }

    boolean[][] matches() {
      boolean[][] hit = new boolean[8][8];
      for (int y = 0; y < 8; y++)
        for (int x = 0; x < 8; x++) {
          int v = board[y][x];
          if (x < 6 && v == board[y][x + 1] && v == board[y][x + 2]) {
            hit[y][x] = hit[y][x + 1] = hit[y][x + 2] = true;
          }
          if (y < 6 && v == board[y + 1][x] && v == board[y + 2][x]) {
            hit[y][x] = hit[y + 1][x] = hit[y + 2][x] = true;
          }
        }
      return hit;
    }

    static boolean any(boolean[][] hit) {
      for (boolean[] row : hit) for (boolean v : row) if (v) return true;
      return false;
    }

    boolean any() {
      return any(matches());
    }

    int[] findMove() {
      for (int y = 0; y < 8; y++)
        for (int x = 0; x < 8; x++)
          for (int dir = 0; dir < 2; dir++) {
            int a = x + dir, b = y + 1 - dir;
            if (a > 7 || b > 7 || board[y][x] == board[b][a]) continue;
            swap(x, y, a, b);
            boolean ok = any();
            swap(x, y, a, b);
            if (ok) return new int[] {x, y, a, b};
          }
      return null;
    }

    boolean hasMove() {
      return findMove() != null;
    }

    void resolve(boolean[][] hit) {
      lastChain = 0;
      while (any(hit)) {
        lastChain++;
        for (int col = 0; col < 8; col++) {
          int w = 7;
          for (int row = 7; row >= 0; row--)
            if (!hit[row][col]) board[w--][col] = board[row][col];
            else score += 10 * lastChain;
          while (w >= 0) board[w--][col] = 1 + rng.nextInt(6);
        }
        hit = matches();
      }
      if (!over && !hasMove()) shuffle();
    }

    public boolean play(int x, int y, int a, int b) {
      if (over || !valid(x, y) || !valid(a, b) || Math.abs(x - a) + Math.abs(y - b) != 1)
        return false;
      swap(x, y, a, b);
      boolean[][] hit = matches();
      if (!any(hit)) {
        swap(x, y, a, b);
        return false;
      }
      if (mode == STEPS) {
        moves--;
        over = moves == 0;
      }
      resolve(hit);
      return true;
    }

    private boolean valid(int x, int y) {
      return x >= 0 && x < 8 && y >= 0 && y < 8;
    }

    public int[] hint() {
      if (over || items[HINT] == 0) return null;
      int[] move = findMove();
      if (move != null) items[HINT]--;
      return move;
    }

    public boolean bomb(int x, int y) {
      if (over || items[BOMB] == 0 || !valid(x, y)) return false;
      items[BOMB]--;
      boolean[][] hit = new boolean[8][8];
      for (int b = Math.max(0, y - 1); b <= Math.min(7, y + 1); b++)
        for (int a = Math.max(0, x - 1); a <= Math.min(7, x + 1); a++) hit[b][a] = true;
      resolve(hit);
      return true;
    }

    public boolean shuffleTool() {
      if (over || items[SHUFFLE] == 0) return false;
      items[SHUFFLE]--;
      shuffle();
      lastChain = 0;
      return true;
    }
  }

  public static final class Mines {
    public final int width, height, quantity;
    public final boolean[][] mines, open, flags;
    public boolean placed, over, won;
    public int revealed;
    public int explodedX = -1, explodedY = -1;
    final Random rng = new Random();

    public Mines() {
      this(9, 9, 10);
    }

    public Mines(int w, int h, int q) {
      if (w < 6 || h < 6 || w > 16 || h > 16 || q < 1 || q > w * h - 9)
        throw new IllegalArgumentException("Invalid mine field");
      width = w;
      height = h;
      quantity = q;
      mines = new boolean[h][w];
      open = new boolean[h][w];
      flags = new boolean[h][w];
    }

    public int safeCount() {
      return width * height - quantity;
    }

    boolean inside(int x, int y) {
      return x >= 0 && x < width && y >= 0 && y < height;
    }

    public int count(int x, int y) {
      int n = 0;
      for (int b = Math.max(0, y - 1); b <= Math.min(height - 1, y + 1); b++)
        for (int a = Math.max(0, x - 1); a <= Math.min(width - 1, x + 1); a++)
          if ((a != x || b != y) && mines[b][a]) n++;
      return n;
    }

    public int flagCount() {
      int n = 0;
      for (boolean[] row : flags) for (boolean v : row) if (v) n++;
      return n;
    }

    public boolean flag(int x, int y) {
      if (over || !inside(x, y) || open[y][x]) return false;
      flags[y][x] = !flags[y][x];
      return true;
    }

    private void place(int x, int y) {
      ArrayList<Integer> cells = new ArrayList<>();
      for (int b = 0; b < height; b++)
        for (int a = 0; a < width; a++)
          if (Math.abs(a - x) > 1 || Math.abs(b - y) > 1) cells.add(b * width + a);
      Collections.shuffle(cells, rng);
      for (int i = 0; i < quantity; i++) {
        int cell = cells.get(i);
        mines[cell / width][cell % width] = true;
      }
      placed = true;
    }

    public boolean reveal(int x, int y) {
      if (over || !inside(x, y) || flags[y][x]) return false;
      if (open[y][x]) return chord(x, y);
      if (!placed) place(x, y);
      uncover(x, y);
      checkWin();
      return true;
    }

    private boolean chord(int x, int y) {
      if (count(x, y) == 0) return false;
      int f = 0;
      for (int b = y - 1; b <= y + 1; b++)
        for (int a = x - 1; a <= x + 1; a++) if (inside(a, b) && flags[b][a]) f++;
      if (f != count(x, y)) return false;
      boolean changed = false;
      for (int b = y - 1; b <= y + 1 && !over; b++)
        for (int a = x - 1; a <= x + 1 && !over; a++)
          if (inside(a, b) && !flags[b][a] && !open[b][a]) {
            uncover(a, b);
            changed = true;
          }
      checkWin();
      return changed;
    }

    private void uncover(int x, int y) {
      if (mines[y][x]) {
        open[y][x] = true;
        explodedX = x;
        explodedY = y;
        over = true;
      } else flood(x, y);
    }

    private void checkWin() {
      if (!over && revealed == safeCount()) {
        over = true;
        won = true;
      }
    }

    void flood(int x, int y) {
      if (!inside(x, y) || open[y][x] || flags[y][x] || mines[y][x]) return;
      open[y][x] = true;
      revealed++;
      if (count(x, y) == 0)
        for (int b = y - 1; b <= y + 1; b++) for (int a = x - 1; a <= x + 1; a++) flood(a, b);
    }
  }

  public static final class Pong {
    public float x = 180, y = 280, vx = 95, vy = 190, player = 180, ai = 180, serve = 1;
    public float spin, angle;
    public int score, enemy, bounces;
    public boolean over;
    private float aim = 180;

    public void aim(float position) {
      aim = clamp(position, 48, 312);
    }

    public void tick(float dt) {
      if (over) return;
      for (float t = dt; t > 0 && !over; ) {
        float step = Math.min(t, .008f);
        t -= step;
        step(step);
      }
    }

    private static float clamp(float v, float lo, float hi) {
      return Math.max(lo, Math.min(hi, v));
    }

    private void step(float dt) {
      float playerV = clamp((aim - player) / dt, -720, 720);
      player += playerV * dt;
      if (serve > 0) {
        serve -= dt;
        return;
      }
      float target = vy < 0 ? x : 180, aiV = clamp((target - ai) / dt, -115, 115);
      ai = clamp(ai + aiV * dt, 48, 312);
      // A signed Magnus-style acceleration acts perpendicular to velocity.
      float oldVx = vx, oldY = y;
      vx = clamp(vx - spin * vy * .024f * dt, -450, 450);
      vy = Math.copySign(clamp(Math.abs(vy + spin * oldVx * .024f * dt), 135, 510), vy);
      spin *= Math.exp(-.65 * dt);
      angle = (angle + spin * dt) % (float) (2 * Math.PI);
      x += vx * dt;
      y += vy * dt;
      if (x < 12) {
        x = 12;
        vx = Math.abs(vx);
        spin *= .72f;
      }
      if (x > 348) {
        x = 348;
        vx = -Math.abs(vx);
        spin *= .72f;
      }
      if (vy > 0 && oldY <= 486 && y >= 486 && Math.abs(x - player) < 50) {
        y = 486;
        bounce(x - player, playerV, -1);
      }
      if (vy < 0 && oldY >= 74 && y <= 74 && Math.abs(x - ai) < 50) {
        y = 74;
        bounce(x - ai, aiV, 1);
      }
      if (y > 560 || y < 0) {
        boolean win = y < 0;
        if (win) score++;
        else enemy++;
        over = score >= 7 || enemy >= 7;
        x = 180;
        y = 280;
        vx = (score + enemy) % 2 == 0 ? 95 : -95;
        vy = win ? 190 : -190;
        spin = angle = 0;
        serve = 1;
      }
    }

    private void bounce(float offset, float paddleV, int dir) {
      float contact = clamp(offset / 48f, -1, 1);
      float speed = Math.min(490, (float) Math.hypot(vx, vy) * 1.045f + 10);
      float heading = clamp(contact * .85f + paddleV * .00043f, -1.02f, 1.02f);
      vx = (float) Math.sin(heading) * speed;
      vy = dir * (float) Math.cos(heading) * speed;
      spin = clamp(spin * .28f - dir * (contact * 13 + paddleV * .031f), -28, 28);
      bounces++;
    }
  }

  public static final class Snake {
    public static final int WIDTH = 16, HEIGHT = 18;
    public final ArrayDeque<Integer> body = new ArrayDeque<>();
    public int food, score;
    public boolean over, won;
    private int direction = 1, next = 1;
    final Random rng = new Random();

    public Snake() {
      body.add(9 * WIDTH + 8);
      body.add(9 * WIDTH + 7);
      body.add(9 * WIDTH + 6);
      placeFood();
    }

    public void turn(int d) {
      if (d >= 0 && d < 4 && Math.abs(d - direction) != 2) next = d;
    }

    public float interval() {
      return Math.max(.075f, .23f - score * .008f);
    }

    private void placeFood() {
      ArrayList<Integer> empty = new ArrayList<>();
      for (int i = 0; i < WIDTH * HEIGHT; i++) if (!body.contains(i)) empty.add(i);
      if (empty.isEmpty()) {
        food = -1;
        over = won = true;
      } else food = empty.get(rng.nextInt(empty.size()));
    }

    public void tick() {
      if (over) return;
      direction = next;
      int head = body.peekFirst(), x = head % WIDTH, y = head / WIDTH;
      x += direction == 1 ? 1 : direction == 3 ? -1 : 0;
      y += direction == 2 ? 1 : direction == 0 ? -1 : 0;
      int to = y * WIDTH + x;
      boolean eating = to == food;
      if (x < 0
          || x >= WIDTH
          || y < 0
          || y >= HEIGHT
          || (body.contains(to) && (eating || to != body.peekLast()))) {
        over = true;
        return;
      }
      if (!eating) body.removeLast();
      body.addFirst(to);
      if (eating) {
        score++;
        placeFood();
      }
    }
  }

  public static final class Breaker {
    public static final int ROWS = 6, COLS = 8;
    public final boolean[][] bricks = new boolean[ROWS][COLS];
    public float x = 180, y = 453, vx = 135, vy = -260, paddle = 180;
    public int score, lives = 3, hits;
    public boolean ready = true, over, won;

    public Breaker() {
      for (boolean[] row : bricks) Arrays.fill(row, true);
    }

    public void aim(float n) {
      paddle = Math.max(42, Math.min(318, n));
      if (ready) x = paddle;
    }

    public boolean launch() {
      if (!ready || over) return false;
      ready = false;
      vx = lives % 2 == 0 ? -135 : 135;
      vy = -260;
      return true;
    }

    public void tick(float dt) {
      if (over || ready) return;
      for (float t = dt; t > 0 && !over && !ready; ) {
        float step = Math.min(t, .006f);
        t -= step;
        move(step);
      }
    }

    private void move(float dt) {
      float oldX = x, oldY = y;
      x += vx * dt;
      y += vy * dt;
      if (x < 7) {
        x = 7;
        vx = Math.abs(vx);
      }
      if (x > 353) {
        x = 353;
        vx = -Math.abs(vx);
      }
      if (y < 7) {
        y = 7;
        vy = Math.abs(vy);
      }
      if (vy > 0 && oldY <= 462 && y >= 462 && Math.abs(x - paddle) <= 44) {
        y = 462;
        float hit = (x - paddle) / 44;
        vx = Math.abs(hit) < .12f ? (vx > 0 ? 65 : -65) : hit * 245;
        vy = -Math.min(380, Math.abs(vy) + 12);
        hits++;
      }
      for (int r = 0; r < ROWS; r++)
        for (int c = 0; c < COLS; c++)
          if (bricks[r][c]) {
            float left = 8 + c * 43, right = left + 40, top = 32 + r * 27, bottom = top + 23;
            if (x + 6 >= left && x - 6 <= right && y + 6 >= top && y - 6 <= bottom) {
              bricks[r][c] = false;
              score += 10;
              hits++;
              if (oldX + 6 < left || oldX - 6 > right) {
                vx = -vx;
                x = oldX;
              } else {
                vy = -vy;
                y = oldY;
              }
              if (score == ROWS * COLS * 10) {
                over = won = true;
              }
              return;
            }
          }
      if (y > 510) {
        lives--;
        over = lives == 0;
        if (!over) {
          ready = true;
          x = paddle;
          y = 453;
        }
      }
    }
  }
}
