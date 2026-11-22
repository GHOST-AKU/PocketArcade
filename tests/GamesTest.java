package com.ghostaku.arcade;

import java.util.*;

public final class GamesTest {
  static int checks;

  static void check(boolean value, String name) {
    checks++;
    if (!value) throw new AssertionError(name);
  }

  static int filled(int[][] board) {
    int n = 0;
    for (int[] row : board) for (int v : row) if (v > 0) n++;
    return n;
  }

  public static void main(String[] args) {
    Games.Blocks b = new Games.Blocks();
    Set<Integer> types = new HashSet<>();
    for (int i = 0; i < 7; i++) {
      types.add(b.type);
      b.spawn();
    }
    check(types.size() == 7, "seven bag");
    b.type = 0;
    b.piece = new int[][] {{0, 0}, {1, 0}, {2, 0}, {3, 0}};
    b.x = 0;
    b.y = 19;
    Arrays.fill(b.board[19], 1);
    Arrays.fill(b.board[19], 0, 4, 0);
    b.lock();
    check(b.lines == 1 && b.score == 100 && filled(b.board) == 0, "line and score");
    Games.Blocks free = new Games.Blocks(true);
    for (int i = 0; i < 7; i++) {
      free.choose(i);
      check(free.type == i, "selected piece");
    }
    for (int i = 0; i < 100; i++) free.tick();
    check(free.y == 0, "no sandbox gravity");
    free.choose(1);
    free.move(-1, 0);
    check(free.drop() && filled(free.board) == 4 && free.type == 1, "manual drop and sticky piece");
    int placed = free.score;
    check(
        placed > 0 && free.undo() && filled(free.board) == 0 && free.score == 0 && free.x == 2,
        "sandbox undo restores board, score and piece");
    free.board[0][5] = 1;
    free.choose(1);
    check(free.blocked && !free.over && !free.drop(), "sandbox blocked spawn is recoverable");
    check(free.move(-1, 0) && !free.blocked, "sandbox can move around blockage");
    for (int mode = 0; mode < 3; mode++) {
      Games.Match m = new Games.Match(mode, true);
      check(!m.any() && m.hasMove(), "stable legal starting board");
      int[] h = m.hint();
      check(h != null && m.items[0] == 2 && m.moves == 30, "hint cost");
      check(
          m.play(h[0], h[1], h[2], h[3]) && m.score >= 30 && !m.any(),
          "hint identifies legal swap");
      check(m.moves == (mode == 0 ? 29 : 30), "mode-specific move consumption");
      int before = m.score, moves = m.moves;
      check(
          m.bomb(0, 0) && m.score >= before + 40 && m.items[1] == 1 && m.moves == moves,
          "edge bomb and tool consumption");
      check(
          m.shuffleTool() && m.items[2] == 1 && m.hasMove() && !m.any() && m.moves == moves,
          "shuffle stable board");
      m.tick(90.1);
      check(m.over == (mode == 1), "only timer mode expires");
    }
    Games.Match steps = new Games.Match(0, false);
    check(
        steps.hint() == null && !steps.bomb(3, 4) && !steps.shuffleTool(), "tools can be disabled");
    steps.moves = 1;
    int[] legal = steps.findMove();
    check(
        steps.play(legal[0], legal[1], legal[2], legal[3]) && steps.over && steps.moves == 0,
        "last valid move resolves before end");
    for (int[] config : new int[][] {{9, 9, 10}, {16, 6, 87}, {6, 16, 32}, {16, 16, 247}}) {
      Games.Mines m = new Games.Mines(config[0], config[1], config[2]);
      m.flag(0, 0);
      check(!m.reveal(0, 0) && !m.placed, "flag protects untouched board");
      m.reveal(2, 3);
      check(m.count(2, 3) == 0 && m.open[3][2] && (!m.over || m.won), "protected center halo");
      int n = 0;
      for (boolean[] row : m.mines) for (boolean mine : row) if (mine) n++;
      check(n == config[2], "exact custom mine count");
      m.flag(0, 0);
      for (int y = 0; y < m.height; y++)
        for (int x = 0; x < m.width; x++) if (!m.mines[y][x]) m.reveal(x, y);
      check(m.won && m.revealed == m.safeCount(), "custom board win");
    }
    Games.Mines m = new Games.Mines(6, 6, 1);
    m.placed = true;
    m.mines[1][1] = true;
    m.open[2][2] = true;
    m.revealed = 1;
    m.flag(1, 1);
    check(
        m.reveal(2, 2) && m.open[3][3] && m.open[1][2] && (!m.over || m.won),
        "chord reveals correct neighbors");
    Games.Mines wrong = new Games.Mines(6, 6, 1);
    wrong.placed = true;
    wrong.mines[1][1] = true;
    wrong.open[2][2] = true;
    wrong.revealed = 1;
    wrong.flag(2, 1);
    wrong.reveal(2, 2);
    check(wrong.over && !wrong.won, "incorrect chord can hit a mine");
    Games.Pong p = new Games.Pong();
    p.serve = 0;
    p.x = 208;
    p.y = 485;
    p.vx = 0;
    p.vy = 200;
    p.tick(.02f);
    check(p.bounces == 1 && p.vy < 0 && p.spin > 1, "edge impact generates spin");
    float oldSpin = p.spin, oldAngle = p.angle, oldVx = p.vx;
    p.tick(.15f);
    check(
        p.spin < oldSpin && p.angle != oldAngle && p.vx > oldVx,
        "spin decays, animates and curves trajectory");
    Games.Pong left = new Games.Pong();
    left.serve = 0;
    left.x = 152;
    left.y = 485;
    left.vx = 0;
    left.vy = 200;
    left.tick(.02f);
    check(left.spin < 0, "opposite edge produces opposite spin");
    Games.Pong moving = new Games.Pong();
    moving.serve = 0;
    moving.aim(260);
    moving.y = 485;
    moving.vx = 0;
    moving.vy = 200;
    moving.tick(.008f);
    check(moving.spin > 12, "moving paddle imparts side spin");
    p.score = 6;
    p.y = -2;
    p.vy = -200;
    p.tick(.01f);
    check(p.over && p.score == 7 && p.spin == 0, "seven-point scoring resets spin");
    Games.Snake s = new Games.Snake();
    int head = s.body.peekFirst();
    s.turn(3);
    s.food = head + 1;
    s.tick();
    check(
        s.body.peekFirst() == head + 1 && s.body.size() == 4 && s.score == 1,
        "snake eats and rejects reversal");
    s.food = 0;
    for (int i = 0; i < 20; i++) s.tick();
    check(s.over && !s.won, "wall ends snake game");
    Games.Breaker k = new Games.Breaker();
    k.aim(110);
    check(k.x == 110 && k.launch() && !k.ready, "aim and launch");
    k.x = 18;
    k.y = 198;
    k.vx = 0;
    k.vy = -260;
    k.tick(.03f);
    check(!k.bricks[5][0] && k.score == 10 && k.vy > 0, "brick collision and score");
    k.x = 200;
    k.y = 511;
    k.vx = 0;
    k.vy = 200;
    k.tick(.01f);
    check(k.lives == 2 && k.ready && !k.over, "miss loses life and resets serve");
    for (boolean[] row : k.bricks) Arrays.fill(row, false);
    k.bricks[0][0] = true;
    k.score = 470;
    k.launch();
    k.x = 18;
    k.y = 63;
    k.vx = 0;
    k.vy = -260;
    k.tick(.03f);
    check(k.over && k.won && k.score == 480, "last brick wins");
    System.out.println("PASS: " + checks + " checks");
  }
}
